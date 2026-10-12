using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.Startup;
using ServiceLayer.TagServices;

namespace Tests.Helpers
{
    /// <summary>
    /// Provides access to the SQL Server test database.
    /// The connection string comes from the env var ConnectionStrings__SampleWebAppDb, else the test appsettings.json,
    /// else the local docker SQL Server (database SampleWebAppDb-Test).
    /// </summary>
    internal static class TestDbHelper
    {
        internal const string DefaultConnectionString =
            "Server=localhost,1433;Database=SampleWebAppDb-Test;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True";

        private static readonly Lazy<string> LazyConnectionString = new Lazy<string>(ReadConnectionString);

        internal static string ConnectionString => LazyConnectionString.Value;

        internal static SampleWebAppDb CreateDb()
        {
            var options = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(ConnectionString)
                .Options;
            return new SampleWebAppDb(options);
        }

        /// <summary>
        /// Migrates the test database (if needed) and resets the blogs to the selected test data
        /// </summary>
        internal static void MigrateAndResetBlogs(TestDataSelection selection = TestDataSelection.Small)
        {
            using (var db = CreateDb())
            {
                DataLayerInitialise.InitialiseThis(db, true);
                DataLayerInitialise.ResetBlogs(db, selection);
            }
        }

        /// <summary>
        /// Builds the same service registrations the web app uses (ServiceLayer and below) against the test database
        /// </summary>
        internal static ServiceProvider CreateServiceLayerProvider()
        {
            var services = new ServiceCollection();
            services.AddServiceLayer(ConnectionString);
            return services.BuildServiceProvider(validateScopes: true);
        }

        /// <summary>
        /// Registers GenericServices for the ServiceLayer and Tests DTOs against the test database
        /// </summary>
        internal static ServiceProvider CreateProviderWithTestDtos()
        {
            var services = new ServiceCollection();
            services.AddDataLayer(ConnectionString);
            var config = new GenericServicesConfig
            {
                DtoAccessValidateOnSave = true,
                DirectAccessValidateOnSave = true,
                BeforeSaveChanges = context => ((SampleWebAppDb)context).CheckTagSlugsUnique()
            };
            services.GenericServicesSimpleSetup<SampleWebAppDb>(config,
                typeof(TagListDto).Assembly, typeof(TestDbHelper).Assembly);
            return services.BuildServiceProvider(validateScopes: true);
        }

        private static string ReadConnectionString()
        {
            var config = new ConfigurationBuilder()
                .SetBasePath(AppContext.BaseDirectory)
                .AddJsonFile("appsettings.json", optional: true)
                .AddEnvironmentVariables()
                .Build();
            var connectionString = config.GetConnectionString(SampleWebAppDb.NameOfConnectionString);
            return string.IsNullOrWhiteSpace(connectionString) ? DefaultConnectionString : connectionString;
        }
    }
}
