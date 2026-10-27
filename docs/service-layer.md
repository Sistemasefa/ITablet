# Conexión SAP Business One Service Layer

## Endpoint de desarrollo (red privada)

- Base URL: `https://192.168.1.99:50000/b1s/v1/`
- Protocolo: HTTPS
- Servicio: SAP Business One Service Layer OData v1
- Estado: proporcionado por el usuario; conectividad y autenticación aún no verificadas.

## Prueba desde red corporativa

1. Comprobar conectividad TCP al puerto 50000.
2. Solicitar `GET /b1s/v1/$metadata` y registrar código HTTP (sin compartir tokens).
3. Verificar que el certificado TLS sea válido y confiable para Android; evitar desactivar validación de certificados.
4. Validar `POST /b1s/v1/Login` desde un componente servidor seguro con `CompanyDB`, `UserName` y `Password` guardados fuera del repositorio.
5. Validar permisos mínimos de lectura y consultas de almacenes y existencias por lote.

## Seguridad

Nunca publicar contraseñas, cookies B1SESSION/ROUTEID, datos de inventario reales o certificados privados en GitHub. No conectar la tablet directamente usando credenciales SAP embebidas: utilizar gateway HTTPS interno con control de acceso. La IP privada requiere LAN/VPN.

## Pendiente

Confirmar nombre exacto de CompanyDB, versión de Service Layer, certificado y disponibilidad de endpoints/consultas para existencias por lote.
