using SampleWebApp.Extensions;
using SampleWebApp.Models;

var builder = WebApplication.CreateBuilder(args);

builder.Host.UseStructuredLogging();

builder.Services.AddControllersWithViews();
builder.Services.AddSingleton<IInternalsInfoProvider, InternalsInfoProvider>();
builder.Services.AddApplicationHealthChecks();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
}

app.UseApplicationHealthChecks();
app.UseRequestLogging();
app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

// Mark the application as ready for health checks.
var startupCheck = app.Services
    .GetRequiredService<IEnumerable<Microsoft.Extensions.Diagnostics.HealthChecks.IHealthCheck>>()
    .OfType<StartupHealthCheck>()
    .FirstOrDefault();
if (startupCheck is not null)
{
    startupCheck.IsReady = true;
}

app.Run();

/// <summary>
/// Exposed so <c>WebApplicationFactory&lt;Program&gt;</c> can bootstrap the app
/// from integration tests.
/// </summary>
public partial class Program { }
