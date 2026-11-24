using System;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DataLayer.DataClasses
{
    /// <summary>
    /// Used by the dotnet-ef tools to create migrations
    /// </summary>
    public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__SampleWebAppDb")
                ?? throw new InvalidOperationException(
                    "Set the ConnectionStrings__SampleWebAppDb environment variable to the database the EF Core tools should use.");
            var options = new DbContextOptionsBuilder<SampleWebAppDb>().UseSqlServer(connectionString).Options;
            return new SampleWebAppDb(options);
        }
    }
}
