using DataLayer.DataClasses;
using SampleWebApp.Infrastructure;
using ServiceLayer.Startup;

var builder = WebApplication.CreateBuilder(args);

var connectionString = builder.Configuration.GetConnectionString(SampleWebAppDb.NameOfConnectionString);
if (string.IsNullOrWhiteSpace(connectionString))
    throw new InvalidOperationException(
        $"The connection string 'ConnectionStrings:{SampleWebAppDb.NameOfConnectionString}' is empty. " +
        "Set it in appsettings.{Environment}.json (e.g. run with ASPNETCORE_ENVIRONMENT=Development) " +
        $"or via the environment variable ConnectionStrings__{SampleWebAppDb.NameOfConnectionString}.");

var hostType = builder.Configuration.GetHostType();

builder.Services.AddControllersWithViews();
builder.Services.AddServiceLayer(connectionString, hostType == HostTypes.Azure);

var app = builder.Build();

//WebWiz does not allow the app to create/migrate the database
ServiceLayerInitialise.InitialiseThis(app.Services, canCreateDatabase: hostType != HostTypes.WebWiz);

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
}

app.UseRouting();

app.MapStaticAssets();
app.MapControllerRoute(
        name: "default",
        pattern: "{controller=Home}/{action=Index}/{id?}")
    .WithStaticAssets();

app.Run();
