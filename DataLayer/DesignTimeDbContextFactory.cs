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
    /// </summary>
    public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString =
                Environment.GetEnvironmentVariable("SAMPLEWEBAPPDB_CONNECTION")
                ?? "Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Design_Time_Only!;TrustServerCertificate=True;MultipleActiveResultSets=True";

            var optionsBuilder = new DbContextOptionsBuilder<SampleWebAppDb>();
            optionsBuilder.UseSqlServer(connectionString);
            return new SampleWebAppDb(optionsBuilder.Options);
        }
    }
}
