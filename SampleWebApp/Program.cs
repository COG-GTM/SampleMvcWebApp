using System;
using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using SampleWebApp.Infrastructure;
using ServiceLayer.Startup;

var builder = WebApplication.CreateBuilder(args);

WebUiInitialise.HostType = WebUiInitialise.DecodeHostType(builder.Configuration["HostTypeString"]);

builder.Services.AddControllersWithViews();
builder.Services.AddServiceLayer(
    builder.Configuration.GetConnectionString(WebUiInitialise.DatabaseConnectionStringName)
        ?? throw new InvalidOperationException("Connection string 'SampleWebAppDb' not found."),
    WebUiInitialise.HostType == HostTypes.Azure);

var app = builder.Build();

ServiceLayerInitialise.InitialiseThis(app.Services, WebUiInitialise.HostType != HostTypes.WebWiz);

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
    app.UseHttpsRedirection();
}

app.UseStaticFiles();
app.UseRouting();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();

public partial class Program { }
