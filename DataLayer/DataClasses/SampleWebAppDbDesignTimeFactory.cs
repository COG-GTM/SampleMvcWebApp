using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DataLayer.DataClasses
{
    /// <summary>
    /// Used by the dotnet-ef tools so that migrations can be created without the web host.
    /// Reads the ConnectionStrings__SampleWebAppDb environment variable, falling back to the local docker SQL Server.
    /// </summary>
    public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        private const string LocalDevConnectionString =
            "Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True";

        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__" + SampleWebAppDb.NameOfConnectionString);
            if (string.IsNullOrEmpty(connectionString))
                connectionString = LocalDevConnectionString;

            var optionsBuilder = new DbContextOptionsBuilder<SampleWebAppDb>();
            optionsBuilder.UseSqlServer(connectionString);
            return new SampleWebAppDb(optionsBuilder.Options);
        }
    }
}
