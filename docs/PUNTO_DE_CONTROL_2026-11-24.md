# ITablet — Punto de control (24 noviembre 2026)

## Estado y referencia

Proyecto `Sistemasefa/ITablet`, rama `codex/itablet`.

- Último commit publicado: `9ec6bf9` — `Preparar base SAP y API de existencias por lote`.
- Punto funcional anterior que debe conservarse: `22a691c5e146cef1b0593707ed5374b7c804fd48`.
- No integrar cambios en `feature/android-direct-sap` sin revisión y aprobación.
- No se han subido credenciales, cookies, tokens, contraseñas ni datos reales de inventario.

## Funcionalidad Android conservada

El flujo confirmado antes de este trabajo continúa siendo la referencia: inicio
de sesión SAP, selección de almacén, descarga de existencias PT, inventario
local, captura de kg y piezas, cierre completo/incompleto, historial y correo
Graph con fotografía y Excel.

La configuración inicial ahora solicita:

- Base de datos SAP editable, con `AlimentosEFA` como valor inicial.
- Usuario y contraseña SAP.
- Aceptación explícita del certificado vencido solo en debug.
- Inicio de sesión Microsoft 365 mediante MSAL.

La base seleccionada se guarda junto con las credenciales SAP cifradas mediante
Android Keystore y se usa en `POST /Login`. Los almacenes se filtran en el
cliente a claves `ALM%` activas.

La compilación Android debug fue verificada con `BUILD SUCCESSFUL` usando Java
17, Gradle 8.9 y Android SDK 35.

## Lotes y existencias

La consulta SQL fue validada en SQL Server sobre `AlimentosEFA` usando `OBTQ`,
`OBTN` y `OITM`. El almacén de prueba `ALMPTTUL` devolvió artículos PT con
lotes y cantidades.

Se decidió soportar ambos tipos de artículo:

- Gestionado por lote: `OBTQ` + `OBTN`, una fila por lote.
- Sin gestión por lote: `OITW`, una fila por artículo y almacén.

En ambos casos solo se devuelven existencias distintas de cero, artículos PT,
artículos activos/no congelados y almacenes `ALM%` activos.

El método `downloadBatchStockQuery` está preparado para una consulta
`SQLQueries`, usando el código `itablet_lotes` y el parámetro `ParamList`.
Service Layer declara `SQLQueries` en `$metadata`, pero las operaciones
`GET` y `POST` devuelven HTTP 500 con código SAP 299 (`Internal server error`),
por lo que la aplicación no debe depender de ese recurso.

## API interno en Windows Server

Se añadió `server/ITabletLotApi`, un API ASP.NET Core 8 para ejecutarse en el
mismo servidor Windows que SQL Server (`SVERP`). Sus endpoints son:

```text
GET /health
GET /api/bases
GET /api/almacenes?baseDatos=AlimentosEFA
GET /api/lotes?baseDatos=AlimentosEFA&almacen=ALMPTTUL
```

Excepto `/health`, requieren `X-ITablet-Api-Key`. El API usa autenticación
integrada de Windows y no usa `sa`, `manager` ni credenciales SAP de la tablet.

En el servidor se creó correctamente el usuario de solo lectura:
`NT AUTHORITY\\Servicio de red`, con `db_datareader` en `AlimentosEFA`.
Debe conservarse esa restricción y otorgarse el mismo acceso únicamente a las
sociedades SAP que se autoricen.

En `SVERP` se instalaron ASP.NET Core Hosting Bundle 8.0.33 y .NET SDK 8.0.427.
El repositorio fue clonado en `C:\ITablet` y `dotnet restore` terminó
correctamente. También se preparó `C:\ITablet\LotApi\appsettings.json` con el
servidor y una clave local; la clave no debe entrar en Git ni compartirse.

## Próximo paso exacto

Desde `C:\ITablet\server\ITabletLotApi` publicar y arrancar el API:

```powershell
dotnet publish -c Release -r win-x64 --self-contained false -o C:\ITablet\LotApi
cd C:\ITablet\LotApi
.\ITabletLotApi.exe
```

En otra PowerShell comprobar:

```powershell
Invoke-WebRequest http://localhost:5080/health
$headers = @{ "X-ITablet-Api-Key" = "CLAVE_LOCAL" }
Invoke-RestMethod -Headers $headers http://localhost:5080/api/bases
Invoke-RestMethod -Headers $headers "http://localhost:5080/api/lotes?baseDatos=AlimentosEFA&almacen=ALMPTTUL"
```

Primero validar localmente. Después configurar HTTPS con certificado válido,
firewall restringido a la red de tablets y ejecutar el proceso como servicio
de Windows con la identidad `NT AUTHORITY\\Servicio de red`.

## Reglas de continuidad

- No usar `sa` desde el API ni conectar la tablet directamente a SQL Server.
- No activar la descarga por lotes en la pantalla hasta validar las respuestas
  del API en el servidor.
- No publicar secretos en `appsettings.json`, GitHub, logs ni chat.
- Mantener migraciones SQLite no destructivas; conservar inventarios históricos.
- Probar primero `/health`, luego `/api/bases`, después almacenes y lotes.
- No afirmar que el API está operativo hasta obtener respuestas reales de esas
  tres pruebas.
