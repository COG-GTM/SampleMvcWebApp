using System.Data.Entity;
using System.Data.Entity.ModelConfiguration.Conventions;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Data.DataClasses
{
    /// <summary>
    /// EF6 context over the shared AssessmentDelivery database.
    /// The schema is owned by the db scripts (db/schema), NOT by EF - never add migrations here.
    /// </summary>
    public class AssessmentDeliveryDb : DbContext
    {
        public const string ConnectionName = "AssessmentDelivery";

        static AssessmentDeliveryDb()
        {
            //schema is maintained by the DBA scripts, EF must not try to create/alter it
            Database.SetInitializer<AssessmentDeliveryDb>(null);
        }

        public AssessmentDeliveryDb() : base("name=" + ConnectionName)
        {
        }

        public AssessmentDeliveryDb(string nameOrConnectionString) : base(nameOrConnectionString)
        {
        }

        public DbSet<TestCenter> TestCenters { get; set; }
        public DbSet<Room> Rooms { get; set; }
        public DbSet<Exam> Exams { get; set; }
        public DbSet<ExamSession> ExamSessions { get; set; }
        public DbSet<Candidate> Candidates { get; set; }
        public DbSet<Booking> Bookings { get; set; }
        public DbSet<WaitlistEntry> Waitlist { get; set; }
        public DbSet<Result> Results { get; set; }
        public DbSet<AuditLog> AuditLogs { get; set; }

        protected override void OnModelCreating(DbModelBuilder modelBuilder)
        {
            modelBuilder.Conventions.Remove<PluralizingTableNameConvention>();
            modelBuilder.Conventions.Remove<OneToManyCascadeDeleteConvention>();

            modelBuilder.Entity<ExamSession>().HasRequired(s => s.Exam).WithMany().HasForeignKey(s => s.ExamId);
            modelBuilder.Entity<ExamSession>().HasRequired(s => s.TestCenter).WithMany(c => c.Sessions).HasForeignKey(s => s.TestCenterId);
            modelBuilder.Entity<ExamSession>().HasRequired(s => s.Room).WithMany().HasForeignKey(s => s.RoomId);
            modelBuilder.Entity<Room>().HasRequired(r => r.TestCenter).WithMany(c => c.Rooms).HasForeignKey(r => r.TestCenterId);
            modelBuilder.Entity<Booking>().HasRequired(b => b.Session).WithMany(s => s.Bookings).HasForeignKey(b => b.SessionId);
            modelBuilder.Entity<Booking>().HasRequired(b => b.Candidate).WithMany(c => c.Bookings).HasForeignKey(b => b.CandidateId);
            modelBuilder.Entity<Result>().HasRequired(r => r.Booking).WithMany().HasForeignKey(r => r.BookingId);
            modelBuilder.Entity<Result>().HasRequired(r => r.Candidate).WithMany().HasForeignKey(r => r.CandidateId);
            modelBuilder.Entity<Result>().HasRequired(r => r.Exam).WithMany().HasForeignKey(r => r.ExamId);
            modelBuilder.Entity<Result>().HasRequired(r => r.Session).WithMany().HasForeignKey(r => r.SessionId);
            modelBuilder.Entity<WaitlistEntry>().HasRequired(w => w.Session).WithMany(s => s.Waitlist).HasForeignKey(w => w.SessionId);

            base.OnModelCreating(modelBuilder);
        }
    }
}
