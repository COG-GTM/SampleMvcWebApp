using System;
using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;

namespace Tests.Helpers
{
    /// <summary>
    /// Creates SampleWebAppDb instances pointing at the test database (never the app database).
    /// Override the connection string with the ConnectionStrings__SampleWebAppDbTest environment variable.
    /// </summary>
    public static class TestDb
    {
        private const string DefaultConnection =
            "Server=localhost,1433;Database=SampleWebAppDb-Test;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=true";

        private static readonly Lazy<bool> Migrated = new Lazy<bool>(() =>
        {
            using var db = new SampleWebAppDb(Options);
            db.Database.Migrate();
            return true;
        });

        public static string ConnectionString =>
            Environment.GetEnvironmentVariable("ConnectionStrings__SampleWebAppDbTest") ?? DefaultConnection;

        private static DbContextOptions<SampleWebAppDb> Options =>
            new DbContextOptionsBuilder<SampleWebAppDb>().UseSqlServer(ConnectionString).Options;

        public static SampleWebAppDb Create()
        {
            _ = Migrated.Value;
            return new SampleWebAppDb(Options);
        }
    }
}
