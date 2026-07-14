using System;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.PostServices;

namespace Tests.Helpers
{
    /// <summary>
    /// Builds an isolated, seeded application stack (EF Core SQLite in-memory + GenericServices
    /// via the built-in DI container) that mirrors how Program.cs wires the running web app.
    /// Each instance owns its own database, so tests do not interfere with each other.
    /// </summary>
    public sealed class TestServices : IDisposable
    {
        private readonly SqliteConnection _connection;
        private readonly ServiceProvider _provider;

        public TestServices(TestDataSelection selection = TestDataSelection.Small)
        {
            _connection = new SqliteConnection("DataSource=:memory:");
            _connection.Open();

            var services = new ServiceCollection();
            services.AddDbContext<SampleWebAppDb>(options => options.UseSqlite(_connection));
            services.AddScoped<IGenericServicesDbContext>(sp => sp.GetRequiredService<SampleWebAppDb>());
            services.AddGenericServices(typeof(SimplePostDto).Assembly);
            _provider = services.BuildServiceProvider();

            using var scope = _provider.CreateScope();
            var context = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            context.Database.EnsureCreated();
            DataLayerInitialise.ResetBlogs(context, selection);
        }

        /// <summary>Creates a new DI scope. Dispose it to release the scoped DbContext.</summary>
        public IServiceScope CreateScope() => _provider.CreateScope();

        public void Dispose()
        {
            _provider.Dispose();
            _connection.Dispose();
        }
    }
}
