# ITabletLotApi

API interno de solo lectura para existencias ITablet. Se conecta al SQL Server
local con autenticación integrada de Windows; la cuenta del servicio debe tener
`db_datareader` en las sociedades SAP que se quieran consultar.

Endpoints protegidos con `X-ITablet-Api-Key`:

- `GET /api/bases`
- `GET /api/almacenes?baseDatos=AlimentosEFA`
- `GET /api/lotes?baseDatos=AlimentosEFA&almacen=ALMPTTUL`

`GET /health` no requiere clave.

No guardar la clave en Git. En Windows, publicar como servicio con una cuenta
que use la identidad `NT AUTHORITY\\Servicio de red`, configurar `ApiKey` fuera
del repositorio y limitar el puerto 5080 al segmento de tablets mediante el
firewall. Instalar un certificado válido para HTTPS antes de producción.
