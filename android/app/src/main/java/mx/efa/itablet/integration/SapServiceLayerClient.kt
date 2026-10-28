package mx.efa.itablet.integration

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Direct SAP B1 Service Layer client. Call on background dispatcher.
 * TLS uses Android system trust and hostname verification, never trust-all.
 */
class SapServiceLayerClient(
    private val baseUrl: String = "https://192.168.1.99:50000/b1s/v1/",
    private val context: Context? = null,
    private val allowPinnedExpiredCertificate: Boolean = false
) {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .apply {
            if (allowPinnedExpiredCertificate) {
                // Explicit user opt-in, debug build only. Not a general trust-all.
                require(context != null && (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    "La excepción TLS solo está disponible en compilaciones de depuración"
                }
                require(baseUrl == "https://192.168.1.99:50000/b1s/v1/") {
                    "La excepción TLS está limitada al servidor SAP autorizado"
                }
                val trustManager = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                        throw java.security.cert.CertificateException("No se aceptan certificados de cliente")
                    }
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        val leaf = chain.firstOrNull()
                            ?: throw java.security.cert.CertificateException("Sin certificado de servidor")
                        val actual = MessageDigest.getInstance("SHA-256")
                            .digest(leaf.encoded).joinToString(":") { "%02X".format(it) }
                        if (actual != PINNED_CERT_SHA256) {
                            throw java.security.cert.CertificateException("Certificado SAP distinto del autorizado")
                        }
                        // OkHttp still checks the hostname/IP against certificate SAN.
                        // This exact-certificate exception does not check expiration or CA trust.
                    }
                }
                val ssl = SSLContext.getInstance("TLS")
                ssl.init(null, arrayOf(trustManager), SecureRandom())
                sslSocketFactory(ssl.socketFactory, trustManager)
            }
        }
        .build()

    companion object {
        private const val PINNED_CERT_SHA256 =
            "76:C5:76:E0:40:87:B3:A5:97:98:0B:94:13:1E:8D:BB:B6:1F:44:A9:C0:61:27:DC:CF:C7:D4:B1:DC:2E:98:C2"
    }

    fun login(companyDb: String, username: String, password: String) {
        require(companyDb.isNotBlank() && username.isNotBlank() && password.isNotBlank())
        val json = JSONObject()
            .put("CompanyDB", companyDb)
            .put("UserName", username)
            .put("Password", password)
        val request = Request.Builder().url(baseUrl + "Login")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("SAP login failed: HTTP ${response.code}")
        }
    }

    fun getWarehouses(): String {
        val all = org.json.JSONArray()
        var next: String? = "Warehouses?\$select=WarehouseCode,WarehouseName"
        val visited = mutableSetOf<String>()
        while (next != null) {
            val current = next
            check(visited.add(current)) { "SAP devolvió una página repetida de almacenes" }
            val url = if (current.startsWith("https://")) current else baseUrl + current.removePrefix("/")
            require(url.startsWith(baseUrl)) { "Paginación fuera del servidor SAP autorizado" }
            val request = Request.Builder().url(url).get().build()
            val page = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("SAP warehouses failed: HTTP ${response.code}")
                JSONObject(response.body?.string() ?: error("Respuesta vacía de almacenes SAP"))
            }
            val values = page.getJSONArray("value")
            for (i in 0 until values.length()) all.put(values.getJSONObject(i))
            val nextLink = page.optString("odata.nextLink")
                .ifBlank { page.optString("@odata.nextLink") }
            next = if (nextLink.isBlank()) null else nextLink.removePrefix(baseUrl).removePrefix("/")
        }
        return JSONObject().put("value", all).toString()
    }

    /**
     * Consulta paginada de existencias por artículo/almacén.
     * No sustituye la consulta posterior de lotes.
     */
    fun downloadWarehouseStock(warehouseCode: String, onProgress: (Int) -> Unit): List<JSONObject> {
        require(warehouseCode.isNotBlank())
        val result = mutableListOf<JSONObject>()
        var next: String? = "Items?\$select=ItemCode,ItemName,ItemWarehouseInfoCollection"
        val visited = mutableSetOf<String>()
        while (next != null) {
            val current = next
            check(visited.add(current)) { "SAP devolvió una página repetida" }
            val url = if (current.startsWith("https://")) current else baseUrl + current.removePrefix("/")
            require(url.startsWith(baseUrl)) { "Paginación fuera del servidor SAP autorizado" }
            val request = Request.Builder().url(url).get().build()
            val page = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("SAP Items: HTTP ${response.code}")
                JSONObject(response.body?.string() ?: error("Respuesta vacía de SAP"))
            }
            val values = page.getJSONArray("value")
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                val warehouses = item.optJSONArray("ItemWarehouseInfoCollection") ?: continue
                for (j in 0 until warehouses.length()) {
                    val stock = warehouses.getJSONObject(j)
                    if (stock.optString("WarehouseCode") == warehouseCode) {
                        val quantity = stock.optDouble("InStock", 0.0)
                        if (quantity != 0.0) {
                            result.add(JSONObject()
                                .put("ItemCode", item.optString("ItemCode"))
                                .put("ItemName", item.optString("ItemName"))
                                .put("WarehouseCode", warehouseCode)
                                .put("InStock", quantity))
                        }
                    }
                }
            }
            onProgress(result.size)
            val nextLink = page.optString("odata.nextLink")
                .ifBlank { page.optString("@odata.nextLink") }
            next = if (nextLink.isBlank()) null else nextLink.removePrefix(baseUrl).removePrefix("/")
        }
        return result
    }

    fun logout() {
        val request = Request.Builder().url(baseUrl + "Logout")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(request).execute().close()
        cookieManager.cookieStore.removeAll()
    }
}
