const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();
const messaging = getMessaging();

/**
 * Formats company and block strings into a clean FCM topic name.
 * e.g. "Mahindra", "V-Block" -> "mahindra_v_block"
 */
function getShopTopic(company, block) {
  const comp = (company || "Mahindra").toLowerCase().replace(/[\s-]+/g, "_");
  const blk = (block || "V-Block").toLowerCase().replace(/[\s-]+/g, "_");
  return `${comp}_${blk}`;
}

/**
 * 1. Real-time Breakdown Report Created Notification
 */
exports.onBreakdownCreated = onDocumentCreated("breakdown_entries/{entryId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const data = snapshot.data();
  const toolId = data.originalToolId || "Tool";
  const line = data.line || "Line";
  const station = data.station || "Station";
  const problem = data.problem || "Breakdown Reported";
  const company = data.company || "Mahindra";
  const block = data.block || "V-Block";
  const topic = getShopTopic(company, block);

  const title = "Breakdown";
  const body = `${toolId} - ${line}/${station}: ${problem}`;

  // Data-Only FCM Payload (High Priority)
  const message = {
    topic: topic,
    data: {
      title: title,
      body: body,
      screen: "Breakdown",
      toolId: toolId,
      alertType: "breakdown",
      timestamp: String(Date.now())
    },
    android: {
      priority: "high"
    }
  };

  try {
    await messaging.send(message);
    console.log(`FCM Breakdown push notification sent to topic: ${topic}`);
  } catch (error) {
    console.error("Error sending FCM Breakdown notification:", error);
  }

  // Write alert document to Firestore notifications collection
  try {
    await db.collection("notifications").add({
      title: title,
      message: body,
      type: "breakdown",
      route: "Breakdown",
      entityId: toolId,
      company: company,
      block: block,
      isRead: false,
      timestamp: FieldValue.serverTimestamp()
    });
  } catch (error) {
    console.error("Error writing notification to Firestore:", error);
  }
});

/**
 * 2. Breakdown Repaired (Pending Handover) Status Update Notification
 */
exports.onBreakdownUpdated = onDocumentUpdated("breakdown_entries/{entryId}", async (event) => {
  const beforeData = event.data.before ? event.data.before.data() : null;
  const afterData = event.data.after ? event.data.after.data() : null;

  if (!afterData) return;

  const oldStatus = beforeData ? beforeData.status : "";
  const newStatus = afterData.status || "";

  if (newStatus === "Repaired (Pending Handover)" && oldStatus !== newStatus) {
    const toolId = afterData.originalToolId || "Tool";
    const line = afterData.line || "Line";
    const station = afterData.station || "Station";
    const company = afterData.company || "Mahindra";
    const block = afterData.block || "V-Block";
    const topic = getShopTopic(company, block);

    const title = "Repaired (Pending Handover)";
    const body = `Tool ${toolId} repaired on ${line}/${station}. Ready for handover.`;

    const message = {
      topic: topic,
      data: {
        title: title,
        body: body,
        screen: "Repair",
        toolId: toolId,
        alertType: "repair",
        timestamp: String(Date.now())
      },
      android: {
        priority: "high"
      }
    };

    try {
      await messaging.send(message);
      console.log(`FCM Repair push notification sent to topic: ${topic}`);
    } catch (error) {
      console.error("Error sending FCM Repair notification:", error);
    }

    try {
      await db.collection("notifications").add({
        title: title,
        message: body,
        type: "repair",
        route: "Repair",
        entityId: toolId,
        company: company,
        block: block,
        isRead: false,
        timestamp: FieldValue.serverTimestamp()
      });
    } catch (error) {
      console.error("Error writing repair notification to Firestore:", error);
    }
  }
});

/**
 * 3. Daily Scheduled Maintenance & Stock Alerts (08:00 AM Asia/Kolkata)
 */
exports.dailyScheduledAlerts = onSchedule(
  {
    schedule: "0 8 * * *",
    timeZone: "Asia/Kolkata"
  },
  async (event) => {
    console.log("Running Daily Scheduled Alerts...");

    const todayStr = new Date().toISOString().split("T")[0]; // YYYY-MM-DD

    // Fetch pending PM entries
    const pmSnap = await db.collection("pm_entries")
      .where("status", "==", "Pending")
      .get();

    const pmAlertsByShop = {};

    pmSnap.forEach((doc) => {
      const pm = doc.data();
      const comp = pm.company || "Mahindra";
      const blk = pm.block || "V-Block";
      const key = `${comp}_${blk}`;

      if (!pmAlertsByShop[key]) {
        pmAlertsByShop[key] = { dueToday: 0, overdue: 0, company: comp, block: blk };
      }

      if (pm.scheduledDate === todayStr) {
        pmAlertsByShop[key].dueToday++;
      } else if (pm.scheduledDate < todayStr && pm.scheduledDate) {
        pmAlertsByShop[key].overdue++;
      }
    });

    // Send PM summary alerts per shop
    for (const key of Object.keys(pmAlertsByShop)) {
      const shopInfo = pmAlertsByShop[key];
      if (shopInfo.dueToday > 0 || shopInfo.overdue > 0) {
        const topic = getShopTopic(shopInfo.company, shopInfo.block);
        const title = "Daily PM Schedule Alert";
        const body = `PM Summary for ${shopInfo.company} ${shopInfo.block}: ${shopInfo.dueToday} Tasks Due Today, ${shopInfo.overdue} Tasks Overdue.`;

        const message = {
          topic: topic,
          data: {
            title: title,
            body: body,
            screen: "PM Management",
            alertType: "pm_summary",
            timestamp: String(Date.now())
          },
          android: {
            priority: "high"
          }
        };

        try {
          await messaging.send(message);
          await db.collection("notifications").add({
            title: title,
            message: body,
            type: "pm",
            route: "PM Management",
            company: shopInfo.company,
            block: shopInfo.block,
            isRead: false,
            timestamp: FieldValue.serverTimestamp()
          });
        } catch (err) {
          console.error(`Failed to send daily PM alert for ${topic}:`, err);
        }
      }
    }

    // Fetch low stock items
    const productsSnap = await db.collection("products").get();
    const stockAlertsByShop = {};

    productsSnap.forEach((doc) => {
      const prod = doc.data();
      if (prod.currentStock <= prod.minStockLevel) {
        const comp = prod.company || "Mahindra";
        const blk = prod.block || "V-Block";
        const key = `${comp}_${blk}`;

        if (!stockAlertsByShop[key]) {
          stockAlertsByShop[key] = { count: 0, company: comp, block: blk };
        }
        stockAlertsByShop[key].count++;
      }
    });

    // Send Low Stock summary alerts per shop
    for (const key of Object.keys(stockAlertsByShop)) {
      const shopInfo = stockAlertsByShop[key];
      if (shopInfo.count > 0) {
        const topic = getShopTopic(shopInfo.company, shopInfo.block);
        const title = "Spare Parts Low Stock Alert";
        const body = `${shopInfo.count} spare parts are at or below minimum stock level in ${shopInfo.company} ${shopInfo.block}.`;

        const message = {
          topic: topic,
          data: {
            title: title,
            body: body,
            screen: "Spare Parts",
            alertType: "stock_summary",
            timestamp: String(Date.now())
          },
          android: {
            priority: "high"
          }
        };

        try {
          await messaging.send(message);
          await db.collection("notifications").add({
            title: title,
            message: body,
            type: "stock",
            route: "Spare Parts",
            company: shopInfo.company,
            block: shopInfo.block,
            isRead: false,
            timestamp: FieldValue.serverTimestamp()
          });
        } catch (err) {
          console.error(`Failed to send daily stock alert for ${topic}:`, err);
        }
      }
    }
  }
);

/**
 * 4. Backblaze B2 Presigned Upload & Download HTTP Endpoints
 */
const { onRequest } = require("firebase-functions/v2/https");
const { getAuth } = require("firebase-admin/auth");
const { generatePresignedUploadUrl, generatePresignedDownloadUrl } = require("./b2Service");

exports.getB2UploadUrl = onRequest({ cors: true }, async (req, res) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Unauthorized: Missing Firebase Auth ID Token" });
    }

    const token = authHeader.split("Bearer ")[1];
    await getAuth().verifyIdToken(token);

    const { companyId, blockId, feature, recordId, filename, contentType, sizeBytes } = req.body || {};

    if (!companyId || !blockId || !filename) {
      return res.status(400).json({ error: "Bad Request: Missing companyId, blockId, or filename" });
    }

    const result = await generatePresignedUploadUrl({
      companyId,
      blockId,
      feature: feature || "documents",
      recordId: recordId || "general",
      filename,
      contentType: contentType || "application/pdf",
      sizeBytes: Number(sizeBytes) || 0,
    });

    return res.status(200).json(result);
  } catch (error) {
    console.error("Error in getB2UploadUrl:", error);
    return res.status(500).json({ error: error.message || "Failed to generate presigned upload URL" });
  }
});

exports.getB2DownloadUrl = onRequest({ cors: true }, async (req, res) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Unauthorized: Missing Firebase Auth ID Token" });
    }

    const token = authHeader.split("Bearer ")[1];
    await getAuth().verifyIdToken(token);

    const { objectKey, sizeBytes } = req.body || {};

    if (!objectKey) {
      return res.status(400).json({ error: "Bad Request: Missing objectKey" });
    }

    const result = await generatePresignedDownloadUrl({
      objectKey,
      sizeBytes: Number(sizeBytes) || 0,
    });

    return res.status(200).json(result);
  } catch (error) {
    console.error("Error in getB2DownloadUrl:", error);
    return res.status(500).json({ error: error.message || "Failed to generate presigned download URL" });
  }
});

