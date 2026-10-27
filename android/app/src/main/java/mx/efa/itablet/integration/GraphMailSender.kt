package mx.efa.itablet.integration

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Graph delegated Mail.Send client. The bearer token MUST come from MSAL,
 * never from a hardcoded secret. Call on a background dispatcher.
 */
class GraphMailSender(private val http: OkHttpClient = OkHttpClient()) {
    fun sendXlsx(
        accessToken: String,
        recipient: String,
        subject: String,
        fileName: String,
        xlsx: ByteArray
    ) {
        require(accessToken.isNotBlank())
        require(recipient.isNotBlank() && !recipient.contains('\n') && !recipient.contains('\r'))
        require(fileName.endsWith(".xlsx", ignoreCase = true))
        require(xlsx.isNotEmpty())
        // Graph JSON fileAttachment is suitable for small attachments.
        require(xlsx.size <= 3 * 1024 * 1024) { "Use an upload session for larger attachments" }
        val attachment = JSONObject()
            .put("@odata.type", "#microsoft.graph.fileAttachment")
            .put("name", fileName)
            .put("contentType", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .put("contentBytes", Base64.getEncoder().encodeToString(xlsx))
        val message = JSONObject()
            .put("subject", subject)
            .put("body", JSONObject().put("contentType", "Text").put("content", "Reporte de inventario ITablet adjunto."))
            .put("toRecipients", JSONArray().put(JSONObject().put("emailAddress", JSONObject().put("address", recipient))))
            .put("attachments", JSONArray().put(attachment))
        val payload = JSONObject().put("message", message).put("saveToSentItems", true)
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/sendMail")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code != 202) throw IllegalStateException("Graph sendMail failed: HTTP ${response.code}")
        }
    }
}
