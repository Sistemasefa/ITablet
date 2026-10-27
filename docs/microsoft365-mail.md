# ITablet — envío de XLSX con Microsoft 365

## Diseño
Android usa Microsoft Authentication Library (MSAL) para obtener un token delegado del usuario conectado, con permiso Microsoft Graph `Mail.Send`. El cliente Graph envía `POST https://graph.microsoft.com/v1.0/me/sendMail` y un adjunto XLSX pequeño. Respuesta 202 = aceptado para procesamiento, **no** entregado al destinatario.

## Configuración pendiente en Microsoft Entra ID
1. Registrar una aplicación pública nativa Android con package name y firma SHA-1 del APK.
2. Configurar redirect URI generado para MSAL Android, Tenant ID y Client ID (identificadores, no secretos).
3. Solicitar permiso delegado `Mail.Send` y conceder consentimiento según políticas de la organización.
4. Integrar MSAL y adquisición silenciosa/interactiva de token; no incluir client secret.
5. Definir destinatarios permitidos asociados a usuarios; evitar destinos arbitrarios.
6. Implementar cola persistente y reintentos de envío, con prevención de duplicados y estado `accepted` diferenciado de `delivered`.
7. Para archivos mayores al límite admitido por JSON fileAttachment, usar borrador + upload session.
8. Validar que las políticas de Conditional Access y MDM permiten el uso desde tablets.

## Estado del código
`GraphMailSender.kt` implementa la llamada HTTP de envío con token delegado proporcionado externamente, solo para XLSX de hasta 3 MiB. **No hay todavía integración MSAL, generación XLSX ni cola persistente.** No está listo para enviar correos reales.

## Seguridad
No registrar tokens, contraseñas ni contenido de inventario. Evitar secretos de aplicación embebidos en el APK. La identidad remitente es el usuario Microsoft autenticado; no se puede suplantar arbitrariamente a otro remitente.
