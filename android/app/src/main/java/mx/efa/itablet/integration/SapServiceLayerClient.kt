package mx.efa.itablet.integration

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * Direct SAP B1 Service Layer client. Call on background dispatcher.
 * TLS uses Android system trust and hostname verification, never trust-all.
 */
class SapServiceLayerClient(
    private val baseUrl: String = "https://192.168.1.99:50000/b1s/v1/"
) {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val http = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()

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
        val request = Request.Builder()
            .url(baseUrl + "Warehouses?\$select=WarehouseCode,WarehouseName")
            .get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("SAP warehouses failed: HTTP ${response.code}")
            return response.body?.string() ?: throw IllegalStateException("Empty SAP response")
        }
    }

    fun logout() {
        val request = Request.Builder().url(baseUrl + "Logout")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(request).execute().close()
        cookieManager.cookieStore.removeAll()
    }
}
