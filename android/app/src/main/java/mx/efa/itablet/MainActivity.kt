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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        Text("Ingresar (próximamente)")
                    }
                    Text("Prototipo inicial. No envía credenciales todavía.")
                }
            }
        }
    }
}
