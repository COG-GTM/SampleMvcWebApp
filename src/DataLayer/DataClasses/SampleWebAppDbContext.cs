using Microsoft.EntityFrameworkCore;

namespace DataLayer.DataClasses;

/// <summary>
/// EF Core replacement for the legacy EF6 <c>SampleWebAppDb</c>.
/// Entity sets (Blogs, Posts, Tags) will be added in subsequent migration phases.
/// </summary>
public class SampleWebAppDbContext : DbContext
{
    public const string ConnectionStringName = "SampleWebAppDb";

    public SampleWebAppDbContext(DbContextOptions<SampleWebAppDbContext> options)
        : base(options)
    {
    }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);
    }
}
