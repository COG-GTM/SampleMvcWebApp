#region licence
// The MIT License (MIT)
// 
// Filename: Program.cs
// Date Created: 2014/05/20
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
using DataLayer.DataClasses;
using DataLayer.Startup;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using SampleWebApp.Infrastructure;
using ServiceLayer.Startup;

namespace SampleWebApp
{
    /// <summary>
    /// This replaces Global.asax.cs, App_Start/* and Infrastructure/WebUiInitialise.cs
    /// </summary>
    public class Program
    {
        public static void Main(string[] args)
        {
            var builder = WebApplication.CreateBuilder(args);

            var appSettingsSection = builder.Configuration.GetSection(AppSettings.SectionName);
            builder.Services.Configure<AppSettings>(appSettingsSection);
            var appSettings = appSettingsSection.Get<AppSettings>() ?? new AppSettings();

            builder.Services.AddControllersWithViews();

            //This registers the service layer, which registers every layer below it
            builder.Services.AddServiceLayer(
                builder.Configuration.GetConnectionString("SampleWebAppDb"),
                appSettings.HostType == HostTypes.Azure);

            var app = builder.Build();

            if (app.Environment.IsDevelopment())
            {
                app.UseDeveloperExceptionPage();
            }
            else
            {
                //this replaces the FilterConfig HandleErrorAttribute
                app.UseExceptionHandler("/Home/Error");
                app.UseStatusCodePagesWithReExecute("/Home/Error", "?code={0}");
            }

            app.UseStaticFiles();
            app.UseRouting();
            app.MapControllerRoute("default", "{controller=Home}/{action=Index}/{id?}");

            MigrateAndSeedDatabase(app);

            app.Run();
        }

        /// <summary>
        /// EF Core has no database initialisers, so the migrate-and-seed that DataLayerInitialise
        /// used to do via CreateDatabaseIfNotExists happens here instead.
        /// </summary>
        private static void MigrateAndSeedDatabase(WebApplication app)
        {
            var logger = app.Services.GetRequiredService<ILoggerFactory>().CreateLogger(typeof(Program).FullName);
            try
            {
                using (var scope = app.Services.CreateScope())
                {
                    var context = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
                    DataLayerInitialise.MigrateAndSeed(context, TestDataSelection.Small, logger);
                }
                logger.LogInformation("The database has been migrated and seeded.");
            }
            catch (Exception ex)
            {
                //a database outage must not silently produce an app that half works, so shout about it
                logger.LogCritical(ex,
                    "Could not migrate/seed the database. The web app will start but every page that " +
                    "touches the database will fail. Check the SampleWebAppDb connection string.");
            }
        }
    }
}
