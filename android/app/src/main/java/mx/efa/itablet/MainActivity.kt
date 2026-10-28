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
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import mx.efa.itablet.integration.EntraConfig

class MainActivity : ComponentActivity() {
    private var msalApp: IMultipleAccountPublicClientApplication? = null
    private var msalStatus by mutableStateOf("Inicializando Microsoft 365...")
    private var signedInAccount by mutableStateOf<String?>(null)
    private var msalBusy by mutableStateOf(false)

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
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ITablet", style = MaterialTheme.typography.headlineMedium)
                    Text("Acceso SAP Business One")
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Usuario SAP") })
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { /* Pendiente: autenticar contra Service Layer */ }, enabled = false) {
                        Text("Ingresar SAP (próximamente)")
                    }
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
