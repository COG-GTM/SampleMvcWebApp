using System;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.RegularExpressions;
using AssessmentDelivery.Tests.Helpers;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.IntegrationTests
{
    /// <summary>
    /// MBA-3826 over HTTP against the running portal (AD_PORTAL_URL, default http://localhost:8080).
    /// Ignored when the portal or database is not reachable.
    /// </summary>
    [TestFixture, Category("Integration")]
    public class RosterPortalTests
    {
        private RosterFixture _fixture;
        private HttpClient _http;

        [OneTimeSetUp]
        public void Start()
        {
            string reason;
            if (!RosterFixture.DatabaseAvailable(out reason)) Assert.Ignore(reason);

            var baseUrl = Environment.GetEnvironmentVariable("AD_PORTAL_URL") ?? "http://localhost:8080";
            _http = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false })
            {
                BaseAddress = new Uri(baseUrl),
                Timeout = TimeSpan.FromSeconds(60)
            };
            try
            {
                _http.GetAsync("/Admin/Centers").Result.Dispose();
            }
            catch (Exception ex)
            {
                Assert.Ignore("Portal not reachable at " + baseUrl + " (" + ex.GetBaseException().Message + "); run scripts/run-portal.ps1");
            }

            _fixture = new RosterFixture();
            _fixture.Create();
        }

        [OneTimeTearDown]
        public void Stop()
        {
            if (_fixture != null) RosterFixture.Remove();
            if (_http != null) _http.Dispose();
        }

        [Test]
        public void SessionsPageLinksEachRowToItsRoster()
        {
            var html = _http.GetStringAsync("/Admin/Sessions?centerId=" + _fixture.TestCenterId).Result;

            StringAssert.Contains("href=\"/Admin/Sessions/Roster/" + _fixture.SessionId + "?format=csv\"", html);
            StringAssert.Contains("Roster (CSV)", html);
            var rows = Regex.Matches(html, "<td class=\"nw-muted\">#\\d+</td>").Count;
            Assert.That(rows, Is.GreaterThan(0));
            Assert.AreEqual(rows, Regex.Matches(html, @"/Admin/Sessions/Roster/\d+\?format=csv").Count, "one link per session row");
        }

        [Test]
        public void DownloadIsAUtf8BomCsvAttachmentWithTheAgreedName()
        {
            var before = RosterExportIntegrationTests.WriteSignature(_fixture.SessionId);
            using (var response = _http.GetAsync("/Admin/Sessions/Roster/" + _fixture.SessionId + "?format=csv").Result)
            {
                Assert.AreEqual(HttpStatusCode.OK, response.StatusCode);
                Assert.AreEqual("text/csv", response.Content.Headers.ContentType.MediaType);
                Assert.AreEqual("attachment", response.Content.Headers.ContentDisposition.DispositionType);
                var fileName = response.Content.Headers.ContentDisposition.FileName.Trim('"');
                StringAssert.IsMatch("^roster-" + Regex.Escape(_fixture.CenterCode) + "-" + Regex.Escape(_fixture.ExamCode) + @"-\d{8}-\d{4}\.csv$", fileName);

                var bytes = response.Content.ReadAsByteArrayAsync().Result;
                CollectionAssert.AreEqual(new byte[] { 0xEF, 0xBB, 0xBF }, bytes.Take(3).ToArray(), "UTF-8 BOM");
                var rows = CsvReader.Parse(Encoding.UTF8.GetString(bytes, 3, bytes.Length - 3));

                Assert.AreEqual("CandidateNumber,LastName,FirstName,Email,BookingStatus,AccommodationCode,Channel,BookedAt,WaitlistPosition",
                    string.Join(",", rows[0]));
                Assert.AreEqual(RosterFixture.Bookings.Count(b => (string)b[3] != "CANCELLED"), rows.Count - 1);
                Assert.IsFalse(rows.Any(r => r[4] == "CANCELLED"));
                var oneil = rows.Single(r => r[0] == RosterFixture.NumberPrefix + "0001");
                Assert.AreEqual("O'Neil", oneil[1]);
                Assert.AreEqual("\"Jo\"", oneil[2]);
                Assert.AreEqual("3", rows.Single(r => r[0] == RosterFixture.NumberPrefix + "0006")[8]);
            }
            Assert.AreEqual(before, RosterExportIntegrationTests.WriteSignature(_fixture.SessionId), "download wrote to the database");
        }

        [Test]
        public void UnknownSessionIs404WithNoFile()
        {
            using (var response = _http.GetAsync("/Admin/Sessions/Roster/999999?format=csv").Result)
            {
                Assert.AreEqual(HttpStatusCode.NotFound, response.StatusCode);
                Assert.IsNull(response.Content.Headers.ContentDisposition);
                Assert.AreNotEqual("text/csv", response.Content.Headers.ContentType == null ? null : response.Content.Headers.ContentType.MediaType);
            }
        }

        [Test]
        public void UnsupportedFormatIs400()
        {
            using (var response = _http.GetAsync("/Admin/Sessions/Roster/" + _fixture.SessionId + "?format=xlsx").Result)
            {
                Assert.AreEqual(HttpStatusCode.BadRequest, response.StatusCode);
                Assert.IsNull(response.Content.Headers.ContentDisposition);
            }
        }
    }
}
