package mx.efa.itablet.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only encrypted SAP credentials; Microsoft tokens are owned by MSAL. */
class DeviceCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("device_setup", Context.MODE_PRIVATE)
    private val alias = "itablet_sap_aes"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun saveSap(user: String, password: String, pinnedDebugCertificate: Boolean) {
        require(user.isNotBlank() && password.isNotBlank())
        prefs.edit().putString("sap_user", encrypt(user))
            .putString("sap_password", encrypt(password))
            .putBoolean("sap_pinned_debug", pinnedDebugCertificate).apply()
    }
    fun sap(): Triple<String, String, Boolean>? = try {
        val u = prefs.getString("sap_user", null) ?: return null
        val p = prefs.getString("sap_password", null) ?: return null
        Triple(decrypt(u), decrypt(p), prefs.getBoolean("sap_pinned_debug", false))
    } catch (_: Exception) { null }
    var microsoftConfigured: Boolean
        get() = prefs.getBoolean("microsoft_configured", false)
        set(value) { prefs.edit().putBoolean("microsoft_configured", value).apply() }
}
