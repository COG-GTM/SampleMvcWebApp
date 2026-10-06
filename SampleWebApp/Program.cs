using DataLayer.DataClasses;
using SampleWebApp.Infrastructure;
using ServiceLayer.Startup;

var builder = WebApplication.CreateBuilder(args);

var hostType = WebUiInitialise.DecodeHostType(builder.Configuration[WebUiInitialise.HostTypeConfigName]);
var connectionString = builder.Configuration.GetConnectionString(SampleWebAppDb.NameOfConnectionString);

builder.Services.AddControllersWithViews();
builder.Services.AddServiceLayer(connectionString, hostType == HostTypes.Azure);

var app = builder.Build();

if (!app.Environment.IsDevelopment())
    app.UseExceptionHandler("/Home/Error");

app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

WebUiInitialise.InitialiseThis(app.Services, hostType);

app.Run();

public partial class Program { }
