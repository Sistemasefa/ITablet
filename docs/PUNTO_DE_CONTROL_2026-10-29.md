# ITablet — Punto de control funcional (29 octubre 2026)

## Propósito y estado
Proyecto **ITablet**, toma de inventarios en tablet Android para AlimentosEFA. **El usuario confirmó en dispositivo que el flujo principal y el envío de correo funcionan correctamente** el 29/10/2026. No confundir con el proyecto VMobile.

- Repositorio: `Sistemasefa/ITablet`
- Rama de trabajo: `feature/android-direct-sap`
- **Commit funcional confirmado ANTES de este documento:** `22a691c5e146cef1b0593707ed5374b7c804fd48`.
- Android Studio en Windows; código Android en `android/`; paquete `mx.efa.itablet`.
- IMPORTANTE: no modificar ni sustituir el flujo funcional sin respaldo y pruebas. Recuperar el código usando el commit anterior; este documento no es copia de todos los archivos fuente.

## Arquitectura y servicios
- App Android nativa Kotlin + Jetpack Compose, compilación Android SDK 35.
- SAP Business One 10 con SQL Server 2019, Service Layer directo `https://192.168.1.99:50000/b1s/v1/`, sociedad `AlimentosEFA`.
- Inicio de sesión SAP compartido por tablet, credenciales cifradas localmente con Android Keystore en `DeviceCredentials.kt`. Cada inventario identifica al responsable mediante nombre y foto; no requiere iniciar SAP con su usuario individual.
- Certificado SAP autofirmado y vencido: existe una **excepción TLS por huella exacta, opt-in, solo compilación debug**. No hay permiso para cambiar certificado/servidor; nunca introducir trust-all genérico ni habilitar excepción en producción.
- Microsoft 365 vía MSAL (delegated Mail.Send y User.Read), Microsoft Graph `/me/sendMail`. No almacenar secretos ni tokens fijos en código.
- Base local SQLite mediante `InventoryStore.kt`, no Room. Un único inventario ACTIVE por dispositivo; FINISHED en historial solo lectura.

## Funcionalidad confirmada
1. Configuración inicial SAP + Microsoft 365; alta de responsable con nombre y fotografía.
2. Selección de almacén SAP; descarga de artículos prefijo `PT` con existencias distintas de cero por artículo/almacén; paginación; snapshot persistente local. El desglose por lote está **aplazado**.
3. Reconteo por artículo: cantidad en kg (decimal) y piezas (entero), independientes. Guardado inmediato local; permite retomar inventario.
4. Búsqueda parcial de clave en lista (ej. `M001` encuentra `PTJAPZ/M001/050`), insensible a mayúsculas/minúsculas.
5. Finalizar inventario: si todos contados, finaliza; si hay pendientes, confirma «Sí, finalizar» o «No, seguir contando». Pendientes permanecen **sin contar**, no cero.
6. Historial read-only; botón «Enviar correo a Francisco» a `francisco.alvirde@alimentosefa.com.mx`.
7. Email Microsoft 365: cuerpo HTML con resumen, responsable, foto incrustada mediante CID, almacén, fechas, totales y diferencias. Excel XLSX adjunto **solo detalle** de artículos, descripción, almacén, SAP kg, contado kg, piezas, diferencia y estado. Pendientes como «Sin contar»; corregido texto `NaN` en vista histórica.
8. El usuario confirmó que todo salió bien tras la prueba del correo.

## Código relevante
- `android/app/src/main/java/mx/efa/itablet/MainActivity.kt`: Compose, navegación, SAP, reconteo, búsqueda, cierre, historial, envío.
- `android/app/src/main/java/mx/efa/itablet/data/InventoryStore.kt`: tablas `inventories` y `stock`, transacciones, `finish(id, allowIncomplete)`.
- `android/app/src/main/java/mx/efa/itablet/data/DeviceCredentials.kt`: configuración y credenciales cifradas.
- `android/app/src/main/java/mx/efa/itablet/integration/SapServiceLayerClient.kt`: SAP login, almacenes, stock PT, paginación, TLS debug.
- `android/app/src/main/java/mx/efa/itablet/integration/GraphMailSender.kt`: Graph, HTML, fotografía inline y XLSX adjunto.
- `android/app/src/main/java/mx/efa/itablet/integration/InventoryExcel.kt`: creación de XLSX sin librerías adicionales.
- `android/app/build.gradle.kts`: configuración Android.

## Pendientes acordados
1. **Existencias por lote y almacén:** la consulta `BatchNumberDetails?$filter=WarehouseCode ...` devolvió HTTP 400 porque ese recurso no expone la propiedad esperada. La app actualmente **no ejecuta esa consulta**. No asumir que `BatchNumberDetails.Quantity` equivale a stock por almacén. Investigar mecanismo SAP válido (p.ej. SQLQueries Service Layer con OBTQ/OBTN/OITM si disponible y autorizado); verificar en la instalación antes de implementarlo. Evitar romper descarga actual. Diseñar migración de datos si cambia esquema local; preservar inventarios históricos sin lote.
2. **Estética / UX:** modernizar diseño Compose, legibilidad, distribución en tablet, búsqueda, botones y resumen. Conservar comportamiento funcional.
3. Pruebas de regresión y eventual manejo de producción para certificado válido.

## Reglas de continuidad
- En nueva conversación: leer **este documento** y consultar **el commit funcional** antes de editar. No reconstruir el proyecto desde cero ni cambiar de repositorio.
- Mantener `feature/android-direct-sap` salvo indicación del usuario.
- Cambios pequeños y verificables; informar commit, archivos y cómo probar en Android Studio.
- No afirmar compilación/ejecución local si solo se modificó GitHub.
- Antes de lotes o estética, preservar este checkpoint como punto de retorno.
- No incluir contraseñas, cookies, tokens ni datos personales de autenticación en commits.
