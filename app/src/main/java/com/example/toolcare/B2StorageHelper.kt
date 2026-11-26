package com.example.toolcare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class B2UploadValidationResult {
    data class Valid(
        val filename: String,
        val sizeBytes: Long,
        val contentType: String,
        val formattedSize: String
    ) : B2UploadValidationResult()

    data class Error(val message: String) : B2UploadValidationResult()
}

class B2StorageHelper(private val context: Context) {

    fun validatePreflightUpload(uri: Uri): B2UploadValidationResult {
        try {
            val contentResolver = context.contentResolver
            val contentType = contentResolver.getType(uri) ?: "application/octet-stream"

            var filename = "uploaded_file"
            var sizeBytes = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) filename = cursor.getString(nameIdx) ?: filename
                    if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            if (sizeBytes > B2StorageConfig.MAX_UPLOAD_SIZE_BYTES) {
                return B2UploadValidationResult.Error(
                    "File size (${B2StorageConfig.formatFileSize(sizeBytes)}) exceeds maximum allowed upload limit of 50 MB."
                )
            }

            return B2UploadValidationResult.Valid(
                filename = filename,
                sizeBytes = sizeBytes,
                contentType = contentType,
                formattedSize = B2StorageConfig.formatFileSize(sizeBytes)
            )
        } catch (e: Exception) {
            Log.e("B2StorageHelper", "Preflight validation error", e)
            return B2UploadValidationResult.Error("Failed to inspect file: ${e.localizedMessage}")
        }
    }

    suspend fun requestPresignedUploadUrl(
        idToken: String,
        serverBaseUrl: String,
        companyId: String,
        blockId: String,
        feature: String,
        recordId: String,
        filename: String,
        contentType: String,
        sizeBytes: Long
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = if (serverBaseUrl.endsWith("/")) "${serverBaseUrl}getB2UploadUrl" else "$serverBaseUrl/getB2UploadUrl"
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val jsonBody = JSONObject().apply {
                put("companyId", companyId)
                put("blockId", blockId)
                put("feature", feature)
                put("recordId", recordId)
                put("filename", filename)
                put("contentType", contentType)
                put("sizeBytes", sizeBytes)
            }.toString()

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val presignedUrl = json.getString("presignedUrl")
                val objectKey = json.getString("objectKey")
                Pair(presignedUrl, objectKey)
            } else {
                Log.e("B2StorageHelper", "Server error requesting upload URL: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("B2StorageHelper", "requestPresignedUploadUrl exception", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun requestPresignedDownloadUrl(
        idToken: String,
        serverBaseUrl: String,
        objectKey: String,
        sizeBytes: Long = 0L
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = if (serverBaseUrl.endsWith("/")) "${serverBaseUrl}getB2DownloadUrl" else "$serverBaseUrl/getB2DownloadUrl"
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val jsonBody = JSONObject().apply {
                put("objectKey", objectKey)
                put("sizeBytes", sizeBytes)
            }.toString()

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                json.getString("presignedUrl")
            } else {
                Log.e("B2StorageHelper", "Server error requesting download URL: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("B2StorageHelper", "requestPresignedDownloadUrl exception", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun uploadFileToPresignedUrl(
        uri: Uri,
        presignedPutUrl: String,
        contentType: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            val contentResolver = context.contentResolver
            inputStream = contentResolver.openInputStream(uri) ?: return@withContext false

            val totalBytes = inputStream.available().toLong()

            val url = URL(presignedPutUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                connectTimeout = 30000
                readTimeout = 60000
            }

            val outputStream = connection.outputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var uploadedBytes = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                uploadedBytes += bytesRead
                if (totalBytes > 0) {
                    val pct = ((uploadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
                    onProgress(pct)
                }
            }
            outputStream.flush()

            val responseCode = connection.responseCode
            val isSuccess = responseCode in 200..299
            Log.d("B2StorageHelper", "B2 Presigned Upload HTTP Response: $responseCode")
            isSuccess
        } catch (e: Exception) {
            Log.e("B2StorageHelper", "uploadFileToPresignedUrl exception", e)
            false
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }

    suspend fun downloadFileFromPresignedUrl(
        presignedGetUrl: String,
        onProgress: (Int) -> Unit = {}
    ): ByteArray? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(presignedGetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 60000
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > B2StorageConfig.MAX_DOWNLOAD_SIZE_BYTES) {
                Log.e("B2StorageHelper", "Requested download size $contentLength exceeds 20 MB limit.")
                return@withContext null
            }

            val inputStream = connection.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (contentLength > 0) {
                    val pct = ((totalRead.toDouble() / contentLength.toDouble()) * 100).toInt()
                    onProgress(pct)
                }
            }

            byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("B2StorageHelper", "downloadFileFromPresignedUrl exception", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
