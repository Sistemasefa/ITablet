# ITablet — Toma de inventarios Android

Aplicación nativa Android para consultar existencias de SAP Business One y efectuar recuentos físicos sin conexión.

## Requisitos acordados
- Una partida por combinación de artículo, lote y almacén.
- Login local de usuarios preconfigurados; nunca almacenar contraseñas en texto plano.
- Consulta de almacenes y existencias mediante SAP Business One Service Layer (SAP B1 10.0 FP 2208 / SQL Server 2019).
- Persistencia local Room/SQLite, con sesiones de inventario separadas.
- Captura de lote, cantidad y piezas como campos independientes; cancelar no guarda, contado persiste y marca la partida.
- Exportación XLSX y envío automático al correo asignado al usuario.
- Recuento offline; descarga y envío requieren red.

## Arquitectura prevista
Android: Kotlin, Jetpack Compose, Room, Retrofit/OkHttp, WorkManager.
Integración: Service Layer vía gateway HTTPS con credenciales SAP almacenadas solo en servidor.
Correo: servicio de envío backend autenticado; no colocar secretos SMTP en APK.
Estado: definición técnica inicial; todavía no existe una compilación funcional.

## Entidades locales previstas
- UserProfile(id, username, displayName, destinationEmail)
- InventorySession(id, warehouseCode, userId, downloadedAt, status)
- SapStockLine(id, sessionId, itemCode, description, batchNumber, sapQuantity, unit)
- CountEntry(id, stockLineId, enteredBatch, quantity, pieces, countedAt)

Conservar números decimales con precisión definida y permitir múltiples capturas por partida. No modificar inventarios SAP.

## Aspectos por validar
1. Disponibilidad real y autenticación de Service Layer en instalación SQL Server 2019.
2. Endpoint o consulta compatible para cantidades por almacén y lote; no asumir que OnHand por artículo equivale a existencia por lote.
3. Unidades y precisión de cantidad/piezas, así como manejo de lotes sin existencia.
4. Alcance de recuentos múltiples y correcciones.
5. Plataforma de correo corporativo y servicio de envío automático.
6. Versión mínima de Android y condiciones de acceso a red/VPN.

## Seguridad
No versionar claves, contraseñas, certificados privados, tokens, datos reales de inventario ni archivos de configuración con secretos.
