using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using SampleWebApp.Infrastructure;
using ServiceLayer.PostServices;
using ServiceLayer.Startup;

var builder = WebApplication.CreateBuilder(args);

var connectionString = builder.Configuration.GetConnectionString(WebUiInitialise.DatabaseConnectionStringName)
                       ?? "Data Source=SampleWebAppDb.db";

// EF Core context (replaces EF6 + web.config connection string)
builder.Services.AddDbContext<SampleWebAppDb>(options => options.UseSqlite(connectionString));
builder.Services.AddScoped<IGenericServicesDbContext>(sp => sp.GetRequiredService<SampleWebAppDb>());

// GenericServices compatibility layer + AutoMapper (replaces Autofac registrations)
builder.Services.AddGenericServices(typeof(SimplePostDto).Assembly);

builder.Services.AddControllersWithViews();

// Preserve the MVC5 pattern of supplying services as action-method parameters
builder.Services.AddSingleton<IConfigureOptions<MvcOptions>, ConfigureServiceParameterBinding>();

var app = builder.Build();

// Route the GenericServices logger through Microsoft.Extensions.Logging (replaces log4net)
var hostTypeString = app.Configuration["HostTypeString"] ?? nameof(HostTypes.LocalHost);
WebUiInitialise.InitialiseThis(hostTypeString, app.Services.GetRequiredService<ILoggerFactory>());

// Initialise / migrate / seed the database (replaces the EF6 database initializer)
using (var scope = app.Services.CreateScope())
{
    var context = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
    ServiceLayerInitialise.InitialiseThis(context, canCreateDatabase: true);
}

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
}

app.UseStaticFiles();

app.UseRouting();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();
