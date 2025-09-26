using Microsoft.EntityFrameworkCore;
using DataLayer.DataClasses;
using ServiceLayer.Startup;
using DataLayer.Startup;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllersWithViews();

builder.Services.AddDbContext<SampleWebAppDb>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("SampleWebAppDb")));

builder.Services.AddAutoMapper(typeof(Program));


var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseStaticFiles();

app.UseRouting();

app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();
