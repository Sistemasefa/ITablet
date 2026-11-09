# Arquitectura directa ITablet → SAP Service Layer

## Decisiones
- Cada inventarista cuenta con credenciales SAP propias.
- No se instalará gateway para la consulta SAP.
- Login mediante POST /b1s/v1/Login con CompanyDB, UserName y Password.
- Mantener B1SESSION y ROUTEID (cuando aplique) en un cookie jar de memoria; nunca registrar contraseñas ni cookies.
- La contraseña no se persiste; volver a solicitarla al expirar la sesión.
- Certificado autofirmado: instalar confianza y comprobar SAN para el host usado; nunca usar trust-all ni deshabilitar hostname verification.
- Permisos SAP de solo lectura, y limitar acceso de red a LAN/VPN.
- No asumir que ItemWarehouseInfo.OnHand ofrece desglose por lote; validar consulta de lotes y cantidades contra SAP.
- Cantidad y piezas de recuento son campos independientes.
- Envío automático del XLSX: pendiente de definir proveedor y autenticación OAuth/delegada. No incluir secretos SMTP ni tokens de servicio en APK.
- Mapeo de usuario SAP a destinatario de correo: evitar que un usuario pueda modificar arbitrariamente el destino sin política autorizada.

## Estado
La pantalla Android inicial es un prototipo sin autenticación operativa. Falta integrar Room, consultas y exportación XLSX.

## Consulta de lotes validada en SQL Server

La consulta de existencias por lote fue validada contra la sociedad `AlimentosEFA`
con las tablas `OBTQ`, `OBTN` y `OITM`. El almacén de prueba fue `ALMPTTUL` y
devolvió, entre otros, el artículo `PTAHPU/E032` con lotes `122-26`, `131-26`,
`281-26` y `362-26`, cada uno con su cantidad.

La app incorpora `downloadBatchStockQuery(queryCode, warehouseCode, ...)` para
consumir una consulta registrada como `SQLQueries('codigo')/List`. El parámetro
del almacén se envía mediante `ParamList`, que es el parámetro definido por el
metadata de Service Layer. El código
predeterminado configurado en la app es `itablet_lotes`. La consulta
publicada debe devolver exactamente `ItemCode`, `ItemName`, `WarehouseCode`,
`BatchNumber` e `InStock`. El código de la consulta aún debe confirmarse en
Service Layer antes de activar la descarga por lotes en la pantalla principal.
