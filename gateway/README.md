# ITablet Gateway — esqueleto V1

Gateway ASP.NET Core 8. Por ahora expone solo `GET /api/health`. No conecta ni autentica aún con SAP; no enviar credenciales ni publicar el puerto a la red.

## Prueba local (servidor de desarrollo)
Instalar SDK .NET 8 compatible y ejecutar:
```powershell
cd gateway
dotnet restore
dotnet run --urls http://127.0.0.1:5080
Invoke-RestMethod http://127.0.0.1:5080/api/health
```
HTTP se permite exclusivamente en loopback para la prueba local. Para producción: certificado HTTPS válido, binding en 8443, cuenta de servicio de permisos mínimos, firewall restringido a tablets/VPN y configuración de secretos fuera del repositorio.

## Próximas tareas
- Autenticación de usuarios y autorización de almacenes en gateway.
- Cliente SAP con verificación TLS (certificado autofirmado instalado y nombre SAN coincidente); gestión de B1SESSION y ROUTEID.
- Consultas de almacenes y stock por lote contrastadas con SAP.
- Servicio de envío de reportes con identidad y destinatario derivados del usuario autenticado, validación del XLSX y límites de tamaño.
- Pruebas de integración antes de instalar como servicio Windows.
