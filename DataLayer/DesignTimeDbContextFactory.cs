using System;
using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DataLayer
{
    /// <summary>
    /// Used by the EF Core tools (dotnet ef) at design time so that creating/applying
    /// migrations does not need to run the web application's host and startup seeding.
    /// The connection string here is only used by the tooling; the running application
    /// supplies its own connection string via configuration.
    /// Set the SAMPLEWEBAPPDB_CONNECTION environment variable to point the tooling at a real
    /// database (required for `dotnet ef database update`). The credential-free fallback below
    /// is only enough for offline commands such as `dotnet ef migrations add`, which build the
    /// model without opening a connection.
    /// </summary>
    public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString =
                Environment.GetEnvironmentVariable("SAMPLEWEBAPPDB_CONNECTION")
                ?? "Server=localhost,1433;Database=SampleWebAppDb;Trusted_Connection=True;TrustServerCertificate=True;MultipleActiveResultSets=True";

            var optionsBuilder = new DbContextOptionsBuilder<SampleWebAppDb>();
            optionsBuilder.UseSqlServer(connectionString);
            return new SampleWebAppDb(optionsBuilder.Options);
        }
    }
}
