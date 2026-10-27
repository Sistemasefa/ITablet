using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddHttpClient("sap", client =>
{
    var baseUrl = builder.Configuration["Sap:BaseUrl"]
        ?? throw new InvalidOperationException("Sap:BaseUrl is required");
    client.BaseAddress = new Uri(baseUrl);
    client.Timeout = TimeSpan.FromSeconds(30);
});
var app = builder.Build();
app.UseHttpsRedirection();
app.MapGet("/api/health", () => Results.Ok(new { status = "ok", service = "ITablet.Gateway" }));

// No public SAP proxy endpoints until gateway authentication, authorization,
// trusted TLS configuration and Service Layer batch-stock query are implemented.
app.Run();
