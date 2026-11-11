using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    /// <summary>
    /// MBA-3828: the portal may run raw SQL only to read the capacity audit. Every Database.SqlQuery( call in
    /// portal source must EXEC one of the two read-only procs and nothing else.
    /// </summary>
    [TestFixture]
    public class PortalRawSqlGuardTests
    {
        private const string Call = "Database.SqlQuery(";

        private static readonly Regex AllowedSql = new Regex(
            @"^""EXEC dbo\.(usp_GetCapacityAudit|usp_GetCapacitySyncRuns)( @\w+(, @\w+)*)?""$", RegexOptions.CultureInvariant);

        private static readonly Regex StringLiteral = new Regex(@"@?""(?:[^""\\]|\\.|"""")*""", RegexOptions.CultureInvariant);

        private static string RepoRoot()
        {
            var dir = new DirectoryInfo(TestContext.CurrentContext.TestDirectory);
            while (dir != null && !File.Exists(Path.Combine(dir.FullName, "AssessmentDelivery.sln")))
                dir = dir.Parent;
            if (dir == null) Assert.Inconclusive("AssessmentDelivery.sln not found above the test directory");
            return dir.FullName;
        }

        /// <summary>One entry per Database.SqlQuery( statement in the source that does not EXEC an allowed proc.</summary>
        internal static IList<string> Violations(string source)
        {
            var problems = new List<string>();
            var at = source.IndexOf(Call, StringComparison.Ordinal);
            while (at >= 0)
            {
                var end = source.IndexOf(';', at);
                var statement = end < 0 ? source.Substring(at) : source.Substring(at, end - at);
                var sql = StringLiteral.Matches(statement).Cast<Match>().Select(m => m.Value)
                    .Where(l => !l.StartsWith("\"@", StringComparison.Ordinal)).ToList();
                if (sql.Count != 1 || !AllowedSql.IsMatch(sql[0]) || statement.Contains("\" +") || statement.Contains("+ \""))
                    problems.Add(statement.Trim());
                at = source.IndexOf(Call, at + Call.Length, StringComparison.Ordinal);
            }
            return problems;
        }

        private static IEnumerable<string> PortalFiles(string root)
        {
            foreach (var project in Directory.GetDirectories(root, "AssessmentDelivery.*"))
            {
                if (Path.GetFileName(project) == "AssessmentDelivery.Tests") continue;
                foreach (var f in Directory.GetFiles(project, "*.cs", SearchOption.AllDirectories)
                             .Concat(Directory.GetFiles(project, "*.cshtml", SearchOption.AllDirectories)))
                {
                    if (f.Contains(Path.DirectorySeparatorChar + "obj" + Path.DirectorySeparatorChar)
                        || f.Contains(Path.DirectorySeparatorChar + "bin" + Path.DirectorySeparatorChar)) continue;
                    yield return f;
                }
            }
        }

        [Test]
        public void AllowsTheTwoReadOnlyAuditProcs()
        {
            Assert.IsEmpty(Violations("var a = _db.Database.SqlQuery(typeof(Row),\n"
                + " \"EXEC dbo.usp_GetCapacityAudit @TestCenterId, @From, @To, @SessionId, @Page, @PageSize\",\n"
                + " IntParam(\"@TestCenterId\", 1), DateParam(\"@From\", f));"));
            Assert.IsEmpty(Violations("_db.Database.SqlQuery(typeof(Row), \"EXEC dbo.usp_GetCapacitySyncRuns @From, @To\", p1, p2);"));
        }

        [TestCase("_db.Database.SqlQuery(typeof(Row), \"UPDATE dbo.ExamSession SET Capacity = 1\");")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), \"EXEC dbo.usp_GetCapacityAudit @A; DELETE dbo.AuditLog\");")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), \"EXEC dbo.usp_GetEligibleCandidates @A\");")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), \"SELECT * FROM dbo.AuditLog\");")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), @\"INSERT dbo.AuditLog DEFAULT VALUES\");")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), sql, p1);")]
        [TestCase("_db.Database.SqlQuery(typeof(Row), \"EXEC dbo.usp_GetCapacityAudit\" + suffix);")]
        public void FlagsAnythingElse(string source)
        {
            Assert.IsNotEmpty(Violations(source), source);
        }

        [Test]
        public void PortalRawSqlOnlyReadsTheCapacityAudit()
        {
            var root = RepoRoot();
            var problems = new List<string>();
            var calls = 0;
            foreach (var file in PortalFiles(root))
            {
                var source = File.ReadAllText(file);
                calls += Regex.Matches(source, Regex.Escape(Call)).Count;
                problems.AddRange(Violations(source).Select(v => file.Substring(root.Length + 1) + ": " + v));
            }
            Assert.IsEmpty(problems, "Portal raw SQL must only EXEC dbo.usp_GetCapacityAudit / dbo.usp_GetCapacitySyncRuns:"
                + Environment.NewLine + string.Join(Environment.NewLine, problems));
            Assert.AreEqual(2, calls, "expected exactly the two capacity audit reads in AdminService");
        }
    }
}
