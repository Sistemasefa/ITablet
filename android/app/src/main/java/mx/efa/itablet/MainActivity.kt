package mx.efa.itablet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import mx.efa.itablet.integration.EntraConfig
import mx.efa.itablet.integration.SapServiceLayerClient
import javax.net.ssl.SSLHandshakeException

class MainActivity : ComponentActivity() {
    private var msalApp: IMultipleAccountPublicClientApplication? = null
    private var msalStatus by mutableStateOf("Inicializando Microsoft 365...")
    private var signedInAccount by mutableStateOf<String?>(null)
    private var msalBusy by mutableStateOf(false)
    private var sapBusy by mutableStateOf(false)
    private var sapStatus by mutableStateOf("Sin conectar a SAP")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicClientApplication.createMultipleAccountPublicClientApplication(
            applicationContext,
            R.raw.msal_config,
            object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                    msalApp = application
                    msalStatus = "Microsoft 365 listo para iniciar sesión"
                }

                override fun onError(exception: MsalException) {
                    msalStatus = "No se pudo inicializar Microsoft 365: ${exception.errorCode}"
                }
            }
        )
        setContent {
            MaterialTheme {
                var user by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var allowSapCertificate by remember { mutableStateOf(false) }
                val debugBuild = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ITablet", style = MaterialTheme.typography.headlineMedium)
                    Text("Acceso SAP Business One")
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuario SAP") })
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation())
                    if (debugBuild) {
                        Text("Advertencia: el certificado SAP está vencido. Autorizarlo solo para esta prueba permite enviar credenciales a un destino cuya identidad se comprueba por huella SHA-256, pero sin validar vigencia ni emisor. No usar en producción.")
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = allowSapCertificate, onCheckedChange = { allowSapCertificate = it })
                            Text("Confío en este certificado SAP para la prueba")
                        }
                    }
                    Button(onClick = { loginSap(user, password, allowSapCertificate) }, enabled = !sapBusy && user.isNotBlank() && password.isNotBlank()) {
                        Text(if (sapBusy) "Conectando..." else "Ingresar SAP")
                    }
                    Text(sapStatus)
                    HorizontalDivider()
                    Text("Microsoft 365", style = MaterialTheme.typography.titleMedium)
                    Text(msalStatus)
                    if (signedInAccount != null) Text("Cuenta: ${signedInAccount}")
                    Button(
                        enabled = msalApp != null && !msalBusy,
                        onClick = { signInMicrosoft() }
                    ) {
                        Text(if (msalBusy) "Iniciando sesión..." else "Iniciar sesión Microsoft 365")
                    }
                    Text("La prueba solo autentica. No envía correos ni guarda tokens en el código.")
                }
            }
        }
    }

    private fun loginSap(user: String, password: String, allowPinnedExpiredCertificate: Boolean) {
        if (sapBusy) return
        sapBusy = true
        sapStatus = "Conectando con SAP..."
        Thread {
            try {
                val client = SapServiceLayerClient(context = applicationContext, allowPinnedExpiredCertificate = allowPinnedExpiredCertificate)
                client.login("AlimentosEFA", user, password)
                val warehouses = client.getWarehouses()
                val count = org.json.JSONObject(warehouses).optJSONArray("value")?.length() ?: 0
                runOnUiThread { sapStatus = "Conexión SAP correcta. Almacenes disponibles: $count" }
                client.logout()
            } catch (e: Exception) {
                val message = if (e is SSLHandshakeException ||
                    e.cause is java.security.cert.CertificateException ||
                    e.message?.contains("certificate", ignoreCase = true) == true) {
                    "No se pudo validar el certificado HTTPS de SAP."
                } else {
                    "Error de conexión SAP: ${e.javaClass.simpleName}: ${e.message?.take(120) ?: "sin detalle"}"
                }
                runOnUiThread { sapStatus = message }
            } finally {
                runOnUiThread { sapBusy = false }
            }
        }.start()
    }

    private fun signInMicrosoft() {
        val app = msalApp ?: return
        msalBusy = true
        msalStatus = "Abriendo inicio de sesión..."
        app.acquireToken(
            this,
            arrayOf(EntraConfig.MAIL_SEND_SCOPE, "User.Read"),
            object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    signedInAccount = authenticationResult.account.username
                    msalStatus = "Autenticación Microsoft 365 correcta"
                    msalBusy = false
                }

                override fun onError(exception: MsalException) {
                    msalStatus = "Error de autenticación: ${exception.errorCode}"
                    msalBusy = false
                }

                override fun onCancel() {
                    msalStatus = "Inicio de sesión cancelado"
                    msalBusy = false
                }
            }
        )
    }
}
