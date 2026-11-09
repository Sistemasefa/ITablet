using Microsoft.Data.SqlClient;
using System.Security.Cryptography;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton<LotRepository>();
var app = builder.Build();

app.Use(async (context, next) =>
{
    if (context.Request.Path == "/health")
    {
        await next();
        return;
    }

    var expected = app.Configuration["ApiKey"];
    if (string.IsNullOrWhiteSpace(expected) ||
        !context.Request.Headers.TryGetValue("X-ITablet-Api-Key", out var received) ||
        !CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.UTF8.GetBytes(expected),
            System.Text.Encoding.UTF8.GetBytes(received.ToString())))
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await context.Response.WriteAsJsonAsync(new { error = "API key inválida" });
        return;
    }
    await next();
});

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

app.MapGet("/api/bases", async (LotRepository repository, CancellationToken cancellationToken) =>
    Results.Ok(await repository.GetSapDatabasesAsync(cancellationToken)));

app.MapGet("/api/almacenes", async (string baseDatos, LotRepository repository, CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await repository.GetWarehousesAsync(baseDatos, cancellationToken));
    }
    catch (ArgumentException ex) { return Results.BadRequest(new { error = ex.Message }); }
});

app.MapGet("/api/lotes", async (string baseDatos, string almacen, LotRepository repository, CancellationToken cancellationToken) =>
{
    try
    {
        return Results.Ok(await repository.GetStockAsync(baseDatos, almacen, cancellationToken));
    }
    catch (ArgumentException ex) { return Results.BadRequest(new { error = ex.Message }); }
});

app.Run();

sealed class LotRepository(IConfiguration configuration)
{
    private readonly string server = configuration["SqlServer"] ?? "SVERP";

    private SqlConnection Connection(string database) => new(
        $"Server={server};Database={database};Integrated Security=True;Encrypt=True;TrustServerCertificate=False;Application Name=ITabletLotApi;Connect Timeout=15");

    private async Task<bool> IsSapDatabaseAsync(string database, CancellationToken cancellationToken)
    {
        await using var connection = Connection(database);
        await connection.OpenAsync(cancellationToken);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT CASE WHEN OBJECT_ID(N'dbo.OWHS') IS NOT NULL
                           AND OBJECT_ID(N'dbo.OITM') IS NOT NULL
                           AND OBJECT_ID(N'dbo.OITW') IS NOT NULL
                        THEN 1 ELSE 0 END
            """;
        return Convert.ToInt32(await command.ExecuteScalarAsync(cancellationToken)) == 1;
    }

    private async Task EnsureSapDatabaseAsync(string database, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(database) || database.Any(ch => !(char.IsLetterOrDigit(ch) || ch == '_' || ch == '-')))
            throw new ArgumentException("La base SAP no es válida.");
        await using var master = Connection("master");
        await master.OpenAsync(cancellationToken);
        await using var exists = master.CreateCommand();
        exists.CommandText = "SELECT state FROM sys.databases WHERE name = @database";
        exists.Parameters.Add(new SqlParameter("@database", database));
        var state = await exists.ExecuteScalarAsync(cancellationToken);
        if (state is null || Convert.ToInt32(state) != 0 || !await IsSapDatabaseAsync(database, cancellationToken))
            throw new ArgumentException("La base no existe, no está activa o no es una sociedad SAP.");
    }

    public async Task<IReadOnlyList<object>> GetSapDatabasesAsync(CancellationToken cancellationToken)
    {
        var result = new List<object>();
        await using var master = Connection("master");
        await master.OpenAsync(cancellationToken);
        await using var command = master.CreateCommand();
        command.CommandText = "SELECT name FROM sys.databases WHERE state = 0 AND name NOT IN ('master','model','msdb','tempdb','SBO-COMMON') ORDER BY name";
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var candidates = new List<string>();
        while (await reader.ReadAsync(cancellationToken)) candidates.Add(reader.GetString(0));
        foreach (var database in candidates)
            if (await IsSapDatabaseAsync(database, cancellationToken)) result.Add(new { database });
        return result;
    }

    public async Task<IReadOnlyList<object>> GetWarehousesAsync(string database, CancellationToken cancellationToken)
    {
        await EnsureSapDatabaseAsync(database, cancellationToken);
        await using var connection = Connection(database);
        await connection.OpenAsync(cancellationToken);
        await using var command = connection.CreateCommand();
        command.CommandText = "SELECT WhsCode, WhsName FROM dbo.OWHS WHERE WhsCode LIKE 'ALM%' AND Inactive = 'N' ORDER BY WhsCode";
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var result = new List<object>();
        while (await reader.ReadAsync(cancellationToken))
            result.Add(new { code = reader.GetString(0), name = reader.GetString(1) });
        return result;
    }

    public async Task<IReadOnlyList<object>> GetStockAsync(string database, string warehouse, CancellationToken cancellationToken)
    {
        await EnsureSapDatabaseAsync(database, cancellationToken);
        if (string.IsNullOrWhiteSpace(warehouse) || !warehouse.StartsWith("ALM", StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("El almacén debe comenzar con ALM.");
        await using var connection = Connection(database);
        await connection.OpenAsync(cancellationToken);
        await using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT Q.ItemCode, I.ItemName, Q.WhsCode AS WarehouseCode,
                   B.DistNumber AS BatchNumber, Q.Quantity AS InStock,
                   CAST(1 AS bit) AS BatchManaged
            FROM dbo.OBTQ Q
            INNER JOIN dbo.OBTN B ON B.ItemCode = Q.ItemCode AND B.SysNumber = Q.SysNumber
            INNER JOIN dbo.OITM I ON I.ItemCode = Q.ItemCode
            INNER JOIN dbo.OWHS H ON H.WhsCode = Q.WhsCode
            WHERE Q.WhsCode = @warehouse AND H.Inactive = 'N'
              AND Q.Quantity <> 0 AND Q.ItemCode LIKE 'PT%'
              AND I.ValidFor = 'Y' AND I.FrozenFor = 'N' AND I.ManBtchNum = 'Y'
            UNION ALL
            SELECT W.ItemCode, I.ItemName, W.WhsCode AS WarehouseCode,
                   '' AS BatchNumber, W.OnHand AS InStock,
                   CAST(0 AS bit) AS BatchManaged
            FROM dbo.OITW W
            INNER JOIN dbo.OITM I ON I.ItemCode = W.ItemCode
            INNER JOIN dbo.OWHS H ON H.WhsCode = W.WhsCode
            WHERE W.WhsCode = @warehouse AND H.Inactive = 'N'
              AND W.OnHand <> 0 AND W.ItemCode LIKE 'PT%'
              AND I.ValidFor = 'Y' AND I.FrozenFor = 'N' AND I.ManBtchNum = 'N'
            ORDER BY ItemCode, BatchNumber;
            """;
        command.Parameters.Add(new SqlParameter("@warehouse", warehouse));
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var result = new List<object>();
        while (await reader.ReadAsync(cancellationToken))
            result.Add(new { itemCode = reader.GetString(0), itemName = reader.GetString(1), warehouseCode = reader.GetString(2), batchNumber = reader.GetString(3), inStock = reader.GetDecimal(4), batchManaged = reader.GetBoolean(5) });
        return result;
    }
}
