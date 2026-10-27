# Microsoft Entra ID — configuración ITablet

## Registro de aplicación
- Nombre: ITablet - Inventarios SAP
- Tipo de cuenta: single tenant (solo este directorio)
- Plataforma: Android (configurar al disponer del hash de firma)
- Package name: `mx.efa.itablet`
- Redirect URI: usar exactamente la generada por Entra para Android y el hash de firma
- Permisos Microsoft Graph: `Mail.Send` **delegado** (y permisos básicos de inicio de sesión según MSAL)
- Sin secretos de cliente ni permisos de aplicación `Mail.Send`

## Identificadores pendientes
- Tenant ID: obtener de Overview de Entra.
- Application (client) ID: obtener de Overview.
- Firma SHA-1: calcular sobre el keystore real de desarrollo y distribución, no inventarla.
- Cuenta Microsoft 365 de prueba: utilizar buzón autorizado y destinatario controlado.

Los IDs de tenant y cliente no son secretos, pero no publicar tokens, claves privadas, contraseñas o datos reales.

## Integración Android
1. Agregar dependencia MSAL Android y archivo de configuración correspondiente.
2. Registrar redirect URI y hash de firma; habilitar configuración de cliente público cuando sea necesario.
3. Solicitar token delegado con `Mail.Send` mediante MSAL, preferentemente silenciosamente después del consentimiento.
4. Pasar el token en memoria a `GraphMailSender`; no almacenarlo manualmente en Room.
5. Generar XLSX localmente, mantener cola persistente de reportes, registrar aceptación HTTP 202 y controlar reintentos para evitar duplicados.
6. Si MSAL solicita interacción (MFA, sesión expirada, Conditional Access), mostrar inicio de sesión; no prometer envío desatendido en todos los casos.

## Restricciones
- `/me/sendMail` envía como el usuario autenticado de Microsoft 365, no necesariamente el usuario SAP. Definir vínculo y política entre identidades.
- No usar `Mail.Send` application permission.
- Una restricción de destinatario aplicada solo en el APK puede eludirse por un cliente manipulado. Para cumplimiento fuerte se necesitan controles del lado de Microsoft 365/Exchange.
- No confundir respuesta 202 con entrega final.
