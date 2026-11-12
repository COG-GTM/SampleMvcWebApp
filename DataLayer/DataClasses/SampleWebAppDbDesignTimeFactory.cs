using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DataLayer.DataClasses
{
    public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        private const string LocalDevConnectionString =
            "Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True";

        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__" + SampleWebAppDb.NameOfConnectionString);
            var optionsBuilder = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(string.IsNullOrEmpty(connectionString) ? LocalDevConnectionString : connectionString);
            return new SampleWebAppDb(optionsBuilder.Options);
        }
    }
}
