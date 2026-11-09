# AGENTS.md — ITablet / Codex

## Proyecto y alcance
Este repositorio es **ITablet** (toma de inventarios en tablets Android), NO VMobile. Idioma de trabajo: español. Código Android en `android/`, paquete `mx.efa.itablet`.

**Antes de cualquier modificación**, leer `docs/PUNTO_DE_CONTROL_2026-10-29.md` y revisar la implementación actual. El usuario confirmó como funcional el commit `22a691c5e146cef1b0593707ed5374b7c804fd48`. Nunca perder esa referencia.

## Reglas de protección
- Rama de integración actual: `feature/android-direct-sap`. Trabajar inicialmente en `codex/itablet` o en ramas de tarea. No fusionar cambios ni modificar la rama funcional sin revisión y aprobación del usuario.
- Preservar: SAP login, descarga de artículos PT con existencias por almacén, inventario local, captura de kg y piezas, búsqueda parcial por clave, cierre completo/incompleto con confirmación, historial read-only y correo Graph con fotografía inline y Excel adjunto.
- Antes de cambiar lógica de inventarios, inspeccionar `MainActivity.kt`, `InventoryStore.kt`, `SapServiceLayerClient.kt`, `InventoryExcel.kt` y `GraphMailSender.kt`.
- Nunca borrar ni migrar destructivamente la base SQLite de inventarios. Si se cambia esquema, proponer migración que conserve historial y conteos.
- No introducir contraseñas, tokens, cookies, fotos ni datos reales de inventario en commits, logs o tests.
- No afirmar que compiló o pasó pruebas si no se ejecutaron. Informar comandos y resultados reales.

## Arquitectura
- Kotlin + Jetpack Compose, Android SDK 35, Gradle wrapper y versiones según repositorio.
- SAP Business One Service Layer directamente, sin gateway. Servidor interno `192.168.1.99:50000`, sociedad `AlimentosEFA`.
- El certificado del SAP es autofirmado/vencido. La excepción por huella exacta es solo debug y con consentimiento. **Nunca implementar trust-all ni alterar el certificado/servidor SAP**.
- SAP credenciales locales protegidas con Android Keystore; Microsoft 365 mediante MSAL y Graph delegated Mail.Send.
- SQLite local con un único inventario activo por dispositivo.

## Pendientes — NO iniciar sin instrucción del usuario
1. **Lotes**: el usuario pausó explícitamente el tema. No retomar automáticamente. El recurso `BatchNumberDetails` con filtro `WarehouseCode` generó HTTP 400; no usarlo como saldo por almacén. Validar la alternativa con SAP antes de implementarla.
2. **Diseño estético**: pendiente de definición con el usuario. No alterar funcionalidad durante rediseño.

## Flujo de trabajo recomendado
1. Leer punto de control y archivos relevantes; formular preguntas concretas ante requisitos ambiguos.
2. Describir alcance, riesgos y archivos a modificar.
3. Cambios mínimos y reversibles; pruebas automatizadas y compilación Gradle cuando el entorno disponga de Android SDK.
4. Mostrar diff y resultados; no integrar en rama funcional sin aprobación.
5. Para pruebas con SAP real, usar dispositivo/red corporativa del usuario; el entorno remoto de Codex puede no alcanzar la IP privada.
6. Registrar cambios significativos en documentación y conservar el punto de retorno.

## Comandos orientativos
- Desde `android/`, revisar wrapper y ejecutar `./gradlew assembleDebug` en Linux/macOS o `gradlew.bat assembleDebug` en Windows, solo si SDK/JDK/Gradle están disponibles.
- No sustituir herramientas ni versiones de compilación por conjeturas.
