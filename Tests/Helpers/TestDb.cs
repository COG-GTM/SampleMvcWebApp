using System;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices;
using GenericServices.Configuration;
using GenericServices.PublicButHidden;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.Startup;
using StatusGeneric;

namespace Tests.Helpers
{
    public static class TestDb
    {
        public const string ConnectionStringEnvVar = "ConnectionStrings__SampleWebAppDbTest";

        private const string DefaultConnectionString =
            "Server=localhost,1433;Database=SampleWebAppDb-Test;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True";

        private static readonly Lazy<string> TestConnectionString = new Lazy<string>(() =>
        {
            var connectionString = Environment.GetEnvironmentVariable(ConnectionStringEnvVar);
            if (string.IsNullOrWhiteSpace(connectionString))
                connectionString = DefaultConnectionString;

            var databaseName = new SqlConnectionStringBuilder(connectionString).InitialCatalog;
            if (databaseName.IndexOf("Test", StringComparison.OrdinalIgnoreCase) < 0)
                throw new InvalidOperationException(
                    $"The tests wipe the database, so {ConnectionStringEnvVar} must point at a test database (name containing 'Test'), not '{databaseName}'.");

            return connectionString;
        });

        private static readonly Lazy<IGenericServicesConfig> Config = new Lazy<IGenericServicesConfig>(() =>
        {
            using var services = CreateServiceProvider();
            return services.GetRequiredService<IWrappedConfigAndMapper>().Config;
        });

        public static string ConnectionString => TestConnectionString.Value;

        public static string DatabaseName => new SqlConnectionStringBuilder(ConnectionString).InitialCatalog;

        public static SampleWebAppDb CreateDb()
        {
            return new SampleWebAppDb(new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(ConnectionString).Options);
        }

        public static void ResetBlogs(TestDataSelection selection, bool canCreateDatabase = true)
        {
            using var db = CreateDb();
            DataLayerInitialise.InitialiseThis(db, canCreateDatabase);
            DataLayerInitialise.ResetBlogs(db, selection);
        }

        public static ServiceProvider CreateServiceProvider()
        {
            return new ServiceCollection()
                .AddLogging()
                .AddServiceLayer(ConnectionString, false)
                .BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true });
        }

        public static IStatusGeneric SaveChangesWithChecking(this SampleWebAppDb db)
        {
            return db.SaveChangesWithValidation(Config.Value);
        }
    }
}
