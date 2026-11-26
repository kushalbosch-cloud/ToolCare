const { S3Client, PutObjectCommand, GetObjectCommand } = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");

const MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB limit
const MAX_DOWNLOAD_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB limit

function getS3Client() {
  const endpoint = process.env.B2_ENDPOINT || "https://s3.us-west-004.backblazeb2.com";
  const region = process.env.B2_REGION || "us-west-004";
  const accessKeyId = process.env.B2_KEY_ID;
  const secretAccessKey = process.env.B2_APPLICATION_KEY;

  if (!accessKeyId || !secretAccessKey) {
    throw new Error("Backblaze B2 credentials (B2_KEY_ID / B2_APPLICATION_KEY) are missing in environment configuration.");
  }

  return new S3Client({
    endpoint: endpoint,
    region: region,
    credentials: {
      accessKeyId: accessKeyId,
      secretAccessKey: secretAccessKey,
    },
  });
}

/**
 * Generates a short-lived presigned PUT URL for file upload (Expires in 15 minutes).
 */
async function generatePresignedUploadUrl({ companyId, blockId, feature, recordId, filename, contentType, sizeBytes }) {
  if (sizeBytes > MAX_UPLOAD_SIZE_BYTES) {
    throw new Error(`File size (${(sizeBytes / (1024 * 1024)).toFixed(1)} MB) exceeds maximum 50 MB upload limit.`);
  }

  const s3 = getS3Client();
  const bucket = process.env.B2_BUCKET_NAME || "industrial-app-files";
  const fileId = `FILE_${Date.now()}`;
  const cleanFile = filename.replace(/[^a-zA-Z0-9_.-]/g, "_");
  const objectKey = `Companies/${companyId}/Blocks/${blockId}/${feature}/${recordId}/${fileId}_${cleanFile}`;

  const command = new PutObjectCommand({
    Bucket: bucket,
    Key: objectKey,
    ContentType: contentType || "application/octet-stream",
  });

  const presignedUrl = await getSignedUrl(s3, command, { expiresIn: 900 });

  return {
    presignedUrl: presignedUrl,
    objectKey: objectKey,
    expiresInSeconds: 900,
  };
}

/**
 * Generates a short-lived presigned GET URL for file download (Expires in 15 minutes).
 */
async function generatePresignedDownloadUrl({ objectKey, sizeBytes }) {
  if (sizeBytes && sizeBytes > MAX_DOWNLOAD_SIZE_BYTES) {
    throw new Error(`File size (${(sizeBytes / (1024 * 1024)).toFixed(1)} MB) exceeds maximum 20 MB download limit.`);
  }

  const s3 = getS3Client();
  const bucket = process.env.B2_BUCKET_NAME || "industrial-app-files";

  const command = new GetObjectCommand({
    Bucket: bucket,
    Key: objectKey,
  });

  const presignedUrl = await getSignedUrl(s3, command, { expiresIn: 900 });

  return {
    presignedUrl: presignedUrl,
    expiresInSeconds: 900,
  };
}

module.exports = {
  generatePresignedUploadUrl,
  generatePresignedDownloadUrl,
  MAX_UPLOAD_SIZE_BYTES,
  MAX_DOWNLOAD_SIZE_BYTES,
};
