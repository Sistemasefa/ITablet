package mx.efa.itablet

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import mx.efa.itablet.data.*
import mx.efa.itablet.integration.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SapWarehouse(val code: String, val description: String)

class MainActivity : ComponentActivity() {
    private lateinit var store: InventoryStore
    private var active by mutableStateOf<InventoryHeader?>(null)
    private var screen by mutableStateOf("home")
    private var msalApp: IMultipleAccountPublicClientApplication? = null
    private var msalStatus by mutableStateOf("Inicializando Microsoft 365...")
    private var sapStatus by mutableStateOf("SAP sin conectar")
    private var busy by mutableStateOf(false)
    private var warehouses by mutableStateOf<List<SapWarehouse>>(emptyList())
    private var sapClient: SapServiceLayerClient? = null
    private var rows by mutableStateOf<List<JSONObject>>(emptyList())
    private var history by mutableStateOf<List<InventoryHeader>>(emptyList())
    private var selectedHistory by mutableStateOf<InventoryHeader?>(null)
    private var status by mutableStateOf("")
    private fun refresh() {
        active = store.active()
        rows = active?.let { store.rows(it.id) } ?: emptyList()
        history = store.history()
    }
    private fun date(value: Long?): String = value?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "—"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = InventoryStore(this)
        refresh()
        screen = if (active != null) "inventory" else "home"
        PublicClientApplication.createMultipleAccountPublicClientApplication(
            applicationContext, R.raw.msal_config,
            object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                    msalApp = application
                    msalStatus = "Microsoft 365 listo"
                }
                override fun onError(exception: MsalException) {
                    msalStatus = "Microsoft 365: ${exception.errorCode}"
                }
            })
        setContent {
            MaterialTheme {
                var responsible by remember { mutableStateOf("") }
                var photoPath by remember { mutableStateOf("") }
                var sapUser by remember { mutableStateOf("") }
                var sapPassword by remember { mutableStateOf("") }
                var trustCertificate by remember { mutableStateOf(false) }
                var warehouse by remember { mutableStateOf("") }
                var selectedRow by remember { mutableStateOf<JSONObject?>(null) }
                var qty by remember { mutableStateOf("") }
                var pieces by remember { mutableStateOf("") }
                val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
                    if (bitmap != null) {
                        val file = File(filesDir, "responsible_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        photoPath = file.absolutePath
                    }
                }
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ITablet · AlimentosEFA", style = MaterialTheme.typography.headlineSmall)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (status.isNotBlank()) Text(status)
                    when (screen) {
                        "home" -> {
                            if (active != null) {
                                Text("Inventario pendiente", style = MaterialTheme.typography.titleLarge)
                                Text("Responsable: ${active!!.responsible}")
                                Text("Almacén: ${active!!.warehouse}")
                                Text("Inicio: ${date(active!!.startedAt)}")
                                Button(onClick = { refresh(); screen = "inventory" }) { Text("Continuar inventario") }
                            } else {
                                Text("¿Quién realizará el inventario?", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(responsible, { responsible = it }, label = { Text("Nombre completo") })
                                Button(onClick = { camera.launch(null) }) { Text("Tomar fotografía obligatoria") }
                                Text(if (photoPath.isNotBlank()) "Fotografía capturada" else "Fotografía pendiente")
                                Button(enabled = responsible.isNotBlank() && photoPath.isNotBlank(), onClick = { screen = "setup" }) {
                                    Text("Continuar")
                                }
                            }
                            OutlinedButton(onClick = { refresh(); screen = "history" }) { Text("Consulta de inventarios anteriores") }
                        }
                        "setup" -> {
                            Text("Conexiones del dispositivo", style = MaterialTheme.typography.titleLarge)
                            Text(msalStatus)
                            Button(enabled = msalApp != null, onClick = { signInMicrosoft() }) { Text("Iniciar sesión Microsoft 365") }
                            OutlinedTextField(sapUser, { sapUser = it }, label = { Text("Usuario SAP") })
                            OutlinedTextField(sapPassword, { sapPassword = it }, label = { Text("Contraseña SAP") }, visualTransformation = PasswordVisualTransformation())
                            if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                                Row { Checkbox(trustCertificate, { trustCertificate = it }); Text("Aceptar certificado SAP vencido (solo pruebas)") }
                            }
                            Button(enabled = !busy && sapUser.isNotBlank() && sapPassword.isNotBlank(),
                                onClick = { loginSap(sapUser, sapPassword, trustCertificate) { sapPassword = ""; if (active != null) { screen = "inventory"; download(active!!.warehouse) } else screen = "warehouse" } }) {
                                Text("Conectar y consultar almacenes")
                            }
                            Text(sapStatus)
                        }
                        "warehouse" -> {
                            Text("Seleccionar almacén", style = MaterialTheme.typography.titleLarge)
                            LazyColumn(Modifier.weight(1f)) {
                                items(warehouses, key = { it.code }) { w ->
                                    Row(Modifier.fillMaxWidth().clickable { warehouse = w.code }.padding(8.dp)) {
                                        RadioButton(warehouse == w.code, onClick = { warehouse = w.code })
                                        Text("${w.code} · ${w.description}")
                                    }
                                }
                            }
                            Button(enabled = warehouse.isNotBlank() && !busy, onClick = {
                                try {
                                    val w = warehouses.first { it.code == warehouse }
                                    store.start(responsible, photoPath, w.code, w.description)
                                    refresh(); screen = "inventory"
                                    download(warehouse)
                                } catch (e: Exception) { status = "No se pudo iniciar: ${e.message}" }
                            }) { Text("Iniciar y descargar inventario actual") }
                        }
                        "inventory" -> {
                            val inv = active
                            if (inv != null) {
                                Text("Responsable: ${inv.responsible} · Almacén: ${inv.warehouse}")
                                Text("Inicio: ${date(inv.startedAt)}")
                                Text("Consulta SAP: ${date(inv.queryStartedAt)} — ${date(inv.queryFinishedAt)}")
                                Text("Lotes: ${rows.size} · Contados: ${rows.count { it.has("CountedQty") }}")
                                if (rows.isEmpty()) {
                                    Text("Aún no hay existencias guardadas.")
                                    Button(enabled = !busy, onClick = { screen = "setup"; status = "Reconecta SAP para descargar el inventario pendiente." }) { Text("Conectar SAP") }
                                }
                                if (selectedRow == null) {
                                    LazyColumn(Modifier.weight(1f)) {
                                        items(rows, key = { it.getString("ItemCode") + "|" + it.getString("BatchNumber") }) { row ->
                                            Column(Modifier.fillMaxWidth().clickable {
                                                selectedRow = row
                                                qty = if (row.has("CountedQty")) row.getDouble("CountedQty").toString() else ""
                                                pieces = if (row.has("Pieces")) row.getDouble("Pieces").toString() else ""
                                            }.padding(8.dp)) {
                                                Text("${row.optString("ItemCode")} · ${row.optString("ItemName")}")
                                                Text("Lote: ${row.optString("BatchNumber")} · SAP: ${row.optDouble("InStock")}")
                                                Text(if (row.has("CountedQty")) "Contado: ${row.optDouble("CountedQty")} · Piezas: ${row.optDouble("Pieces")}" else "Recontar")
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                    Button(enabled = rows.isNotEmpty() && rows.all { it.has("CountedQty") } && !busy,
                                        onClick = {
                                            try { store.finish(inv.id); refresh(); screen = "home"; responsible = ""; photoPath = ""; status = "Inventario finalizado y guardado en historial. Envío de correo pendiente." }
                                            catch (e: Exception) { status = "No se pudo finalizar: ${e.message}" }
                                        }) { Text("Finalizar inventario") }
                                    OutlinedButton(onClick = { screen = "home" }) { Text("Guardar y salir") }
                                } else {
                                    val row = selectedRow!!
                                    Text("Recontar ${row.optString("ItemCode")} · Lote ${row.optString("BatchNumber")}")
                                    OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad (kg)") })
                                    OutlinedTextField(pieces, { pieces = it }, label = { Text("Piezas") })
                                    Button(enabled = qty.toDoubleOrNull() != null && pieces.toDoubleOrNull() != null,
                                        onClick = {
                                            try {
                                                store.count(inv.id, row.getString("ItemCode"), row.getString("BatchNumber"), qty.toDouble(), pieces.toDouble())
                                                selectedRow = null; refresh()
                                            } catch (e: Exception) { status = "Error al guardar: ${e.message}" }
                                        }) { Text("Contado") }
                                    OutlinedButton(onClick = { selectedRow = null }) { Text("Cancelar") }
                                }
                            }
                        }
                        "history" -> {
                            Text("Inventarios anteriores · Solo consulta", style = MaterialTheme.typography.titleLarge)
                            LazyColumn(Modifier.weight(1f)) {
                                items(history, key = { it.id }) { inv ->
                                    Column(Modifier.fillMaxWidth().clickable { selectedHistory = inv; screen = "detail" }.padding(10.dp)) {
                                        Text("${date(inv.finishedAt)} · ${inv.responsible}")
                                        Text("${inv.warehouse} · ${inv.warehouseName}")
                                        HorizontalDivider()
                                    }
                                }
                            }
                            OutlinedButton(onClick = { screen = "home" }) { Text("Volver") }
                        }
                        "detail" -> {
                            selectedHistory?.let { inv ->
                                Text("Resumen · Solo lectura", style = MaterialTheme.typography.titleLarge)
                                Text("Responsable: ${inv.responsible}")
                                val photo = remember(inv.photoPath) { if (File(inv.photoPath).exists()) BitmapFactory.decodeFile(inv.photoPath)?.asImageBitmap() else null }
                                if (photo != null) Image(bitmap = photo, contentDescription = "Fotografía del responsable", modifier = Modifier.size(120.dp))
                                else Text("Fotografía no disponible")
                                Text("Almacén: ${inv.warehouse} · ${inv.warehouseName}")
                                Text("Inicio: ${date(inv.startedAt)}")
                                Text("Fin: ${date(inv.finishedAt)}")
                                val saved = remember(inv.id) { store.rows(inv.id) }
                                Text("Lotes: ${saved.size} · Diferencias: ${saved.count { it.has("CountedQty") && it.optDouble("CountedQty") != it.optDouble("InStock") }}")
                                LazyColumn(Modifier.weight(1f)) {
                                    items(saved) { row ->
                                        Text("${row.optString("ItemCode")} · ${row.optString("BatchNumber")} · SAP ${row.optDouble("InStock")} · Conteo ${row.optDouble("CountedQty")}")
                                        HorizontalDivider()
                                    }
                                }
                            }
                            OutlinedButton(onClick = { screen = "history" }) { Text("Volver al historial") }
                        }
                    }
                }
            }
        }
    }
    private fun signInMicrosoft() {
        val app = msalApp ?: return
        app.acquireToken(this, arrayOf(EntraConfig.MAIL_SEND_SCOPE, "User.Read"),
            object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) { msalStatus = "Microsoft 365: ${result.account.username}" }
                override fun onError(exception: MsalException) { msalStatus = "Microsoft 365: ${exception.errorCode}" }
                override fun onCancel() { msalStatus = "Autenticación cancelada" }
            })
    }
    private fun loginSap(user: String, password: String, pinned: Boolean, onSuccess: () -> Unit) {
        if (busy) return
        busy = true
        Thread {
            try {
                val client = SapServiceLayerClient(context = applicationContext, allowPinnedExpiredCertificate = pinned)
                client.login("AlimentosEFA", user, password)
                val array = JSONObject(client.getWarehouses()).getJSONArray("value")
                val loaded = (0 until array.length()).map {
                    val w = array.getJSONObject(it)
                    SapWarehouse(w.getString("WarehouseCode"), w.optString("WarehouseName"))
                }
                sapClient = client
                runOnUiThread {
                    warehouses = loaded
                    sapStatus = "Conectado: ${loaded.size} almacenes"
                    onSuccess()
                }
            } catch (e: Exception) {
                runOnUiThread { sapStatus = "Error SAP: ${e.message?.take(160)}" }
            } finally { runOnUiThread { busy = false } }
        }.start()
    }
    private fun download(warehouse: String) {
        val client = sapClient ?: run { status = "Conecta SAP para descargar"; return }
        val id = active?.id ?: return
        busy = true
        val started = System.currentTimeMillis()
        status = "Consultando existencias actuales SAP..."
        Thread {
            try {
                val stock = client.downloadWarehouseStock(warehouse) { n ->
                    runOnUiThread { status = "Artículos PT con existencia: $n" }
                }
                val names = stock.associate { it.getString("ItemCode") to it.optString("ItemName") }
                val allowed = names.keys
                val batches = client.downloadBatchStock(warehouse) { n ->
                    runOnUiThread { status = "Lotes consultados: $n" }
                }.filter { it.optString("ItemCode") in allowed }
                    .map { JSONObject(it.toString()).put("ItemName", names[it.getString("ItemCode")] ?: "") }
                store.saveSnapshot(id, batches, started, System.currentTimeMillis())
                runOnUiThread { refresh(); status = "Existencias guardadas localmente: ${batches.size} lotes" }
            } catch (e: Exception) {
                runOnUiThread { status = "Error SAP: ${e.message?.take(160)}. Inventario pendiente; reintentar conexión." }
            } finally {
                try { client.logout() } catch (_: Exception) {}
                sapClient = null
                runOnUiThread { busy = false }
            }
        }.start()
    }
}
