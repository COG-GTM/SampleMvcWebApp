using SampleWebApp.Models;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllersWithViews();
builder.Services.AddSingleton<IInternalsInfoProvider, InternalsInfoProvider>();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
}

app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();

/// <summary>
/// Exposed so <c>WebApplicationFactory&lt;Program&gt;</c> can bootstrap the app
/// from integration tests. Top-level statements produce an implicit
/// <c>Program</c> class but it is <c>internal</c>, so we declare a public
/// partial here to give the test project a handle.
/// </summary>
public partial class Program { }
