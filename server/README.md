# API de lotes de ITablet

## Propósito

`ITabletLotApi` es un servicio interno para consultar existencias de ITablet
cuando Service Layer no puede ejecutar `SQLQueries`. Corre en el mismo Windows
Server que SQL Server (`SVERP`) y usa autenticación integrada de Windows.

La API no usa las credenciales SAP de la tablet, `sa`, `manager`, tokens de
Service Layer ni secretos de Microsoft. La identidad del proceso debe ser
`NT AUTHORITY\\Servicio de red`, con `db_datareader` en `SBO-COMMON` y en las
sociedades SAP que se quieran consultar.

## Endpoints

Todos, excepto `/health`, requieren el encabezado `X-ITablet-Api-Key`.

```text
GET /health
GET /api/bases
GET /api/almacenes?baseDatos=AlimentosEFA
GET /api/lotes?baseDatos=AlimentosEFA&almacen=ALMPTTUL
```

`/api/bases` detecta bases en estado `ONLINE` que contienen las tablas SAP
necesarias. Los almacenes se limitan a claves `ALM%` activas. Los lotes se leen
de `OBTQ/OBTN`; los artículos sin gestión de lotes se leen de `OITW`. En ambos
casos se excluyen cantidades cero, artículos distintos de `PT`, artículos
inactivos/congelados y almacenes inactivos.

## Publicación en Windows Server

Requisitos: .NET SDK 8 x64 y ASP.NET Core Hosting Bundle 8 x64.

Desde la carpeta del proyecto:

```powershell
dotnet restore
dotnet publish -c Release -r win-x64 --self-contained false -o C:\ITablet\LotApi
```

Copia `appsettings.json.example` como `C:\ITablet\LotApi\appsettings.json` y
define una clave larga generada localmente; nunca la guardes en Git.

```json
{
  "SqlServer": "SVERP",
  "ApiKey": "CLAVE_GENERADA_LOCALMENTE",
  "Urls": "https://0.0.0.0:5080"
}
```

Configura un certificado válido para HTTPS. Para la primera prueba controlada
puede usarse HTTP únicamente en localhost; no expongas el puerto sin firewall.

## Servicio de Windows

Instala el ejecutable publicado como servicio con una cuenta que use la
identidad `NT AUTHORITY\\Servicio de red`. Limita el firewall al segmento de
las tablets y conserva el puerto de SQL Server cerrado a las tablets.

Pruebas iniciales desde el servidor:

```powershell
Invoke-WebRequest https://localhost:5080/health
Invoke-RestMethod -Headers @{"X-ITablet-Api-Key"="CLAVE_LOCAL"} `
  "https://localhost:5080/api/bases"
Invoke-RestMethod -Headers @{"X-ITablet-Api-Key"="CLAVE_LOCAL"} `
  "https://localhost:5080/api/lotes?baseDatos=AlimentosEFA&almacen=ALMPTTUL"
```

No conectes la tablet directamente a SQL Server. La aplicación se integrará
con este API después de validar sus respuestas desde el servidor.
