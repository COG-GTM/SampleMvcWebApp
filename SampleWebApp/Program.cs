#region licence
// The MIT License (MIT)
//
// Filename: Program.cs
//
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System;
using System.Linq;
using BizLayer.Startup;
using DataLayer.DataClasses;
using DataLayer.Startup;
using Microsoft.AspNetCore.Builder;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using ServiceLayer.Startup;

namespace SampleWebApp
{
    public class Program
    {
        public static void Main(string[] args)
        {
            var builder = WebApplication.CreateBuilder(args);

            builder.Services.AddControllersWithViews();

            //Register the EF Core context. The connection string comes from configuration
            //(user secrets / environment variables / appsettings.{Environment}.json). The
            //checked-in appsettings files leave it empty so no credentials live in source control.
            var connectionString = builder.Configuration.GetConnectionString("SampleWebAppDb");
            if (string.IsNullOrWhiteSpace(connectionString))
                throw new InvalidOperationException(
                    "No connection string named 'SampleWebAppDb' was found. Set it via " +
                    "`dotnet user-secrets set \"ConnectionStrings:SampleWebAppDb\" \"...\"` or the " +
                    "ConnectionStrings__SampleWebAppDb environment variable. See README.md.");

            builder.Services.AddDbContext<SampleWebAppDb>(options =>
                options.UseSqlServer(connectionString, sql => sql.EnableRetryOnFailure()));

            //Registers the service layer (ICrudServices/ICrudServicesAsync + DTO mappings + IPostCrudHelper),
            //which internally also registers the data layer.
            builder.Services.AddServiceLayer();
            builder.Services.AddBizLayer();

            var app = builder.Build();

            if (app.Environment.IsDevelopment())
            {
                app.UseDeveloperExceptionPage();
            }
            else
            {
                app.UseExceptionHandler("/Home/Error");
            }

            app.UseStaticFiles();
            app.UseRouting();

            app.MapControllerRoute(
                name: "default",
                pattern: "{controller=Home}/{action=Index}/{id?}");

            MigrateAndSeed(app);

            app.Run();
        }

        /// <summary>
        /// Applies any pending EF Core migrations (falling back to EnsureCreated when no migration exists yet)
        /// and seeds the blogs data on first run when the database is empty. This replaces the EF6 database
        /// initializer + <c>Application_Start</c> seeding of the classic app.
        /// </summary>
        private static void MigrateAndSeed(WebApplication app)
        {
            using var scope = app.Services.CreateScope();
            var services = scope.ServiceProvider;
            var logger = services.GetRequiredService<ILogger<Program>>();
            var context = services.GetRequiredService<SampleWebAppDb>();

            if (context.Database.GetMigrations().Any())
                context.Database.Migrate();
            else
                context.Database.EnsureCreated();

            if (!context.Blogs.Any())
                DataLayerInitialise.ResetBlogs(context, TestDataSelection.Medium, logger);
        }
    }
}
