using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using AssessmentDelivery.Services.Scheduling;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    /// <summary>SchedulingClient against a one-shot HttpListener standing in for scheduling-service.</summary>
    [TestFixture]
    public class SchedulingClientTests
    {
        private HttpListener _listener;
        private string _baseUrl;
        private string _method, _path, _body, _actor, _idempotencyKey;

        [SetUp]
        public void Start()
        {
            var probe = new TcpListener(IPAddress.Loopback, 0);
            probe.Start();
            var port = ((IPEndPoint)probe.LocalEndpoint).Port;
            probe.Stop();
            _baseUrl = "http://localhost:" + port + "/api/v1";
            _listener = new HttpListener();
            _listener.Prefixes.Add(_baseUrl + "/");
            _listener.Start();
        }

        [TearDown]
        public void Stop()
        {
            _listener.Close();
        }

        private void RespondOnce(int status, string json)
        {
            ThreadPool.QueueUserWorkItem(_ =>
            {
                var ctx = _listener.GetContext();
                _method = ctx.Request.HttpMethod;
                _path = ctx.Request.Url.PathAndQuery;
                _actor = ctx.Request.Headers["X-Actor"];
                _idempotencyKey = ctx.Request.Headers["Idempotency-Key"];
                using (var r = new StreamReader(ctx.Request.InputStream)) _body = r.ReadToEnd();
                var bytes = Encoding.UTF8.GetBytes(json ?? "");
                ctx.Response.StatusCode = status;
                ctx.Response.ContentType = "application/json";
                ctx.Response.OutputStream.Write(bytes, 0, bytes.Length);
                ctx.Response.Close();
            });
        }

        [Test]
        public void BookPostsPortalChannelWithActorAndIdempotencyKey()
        {
            RespondOnce(201, "{\"bookingId\":55,\"status\":\"BOOKED\"}");
            var client = new SchedulingClient(_baseUrl + "/");

            var result = client.Book(new ApiBookRequest
            {
                SessionId = 7, CandidateId = 9, AccommodationCode = "ET25", Channel = "PORTAL"
            }, "portal");

            Assert.AreEqual(55, result.BookingId);
            Assert.AreEqual("BOOKED", result.Status);
            Assert.AreEqual("POST", _method);
            Assert.AreEqual("/api/v1/bookings", _path);
            Assert.AreEqual("portal", _actor);
            Assert.IsNotEmpty(_idempotencyKey);
            StringAssert.Contains("\"channel\":\"PORTAL\"", _body);
            StringAssert.Contains("\"candidateId\":9", _body);
        }

        [Test]
        public void ConflictBecomesSchedulingApiExceptionWithLegacyMessage()
        {
            RespondOnce(409, "{\"error\":\"NOT_CANCELLABLE\",\"message\":\"Booking 3 cannot be cancelled.\",\"legacyState\":4}");
            var client = new SchedulingClient(_baseUrl);

            var ex = Assert.Throws<SchedulingApiException>(() => client.Cancel(3, "r", "portal"));

            Assert.AreEqual(409, ex.HttpStatus);
            Assert.AreEqual("NOT_CANCELLABLE", ex.Error);
            Assert.AreEqual("Booking 3 cannot be cancelled.", ex.Message);
            Assert.AreEqual(4, ex.LegacyState);
            Assert.AreEqual("/api/v1/bookings/3/cancel", _path);
        }

        [Test]
        public void MissingSessionReadsAsNull()
        {
            RespondOnce(404, "{\"error\":\"SESSION_NOT_FOUND\",\"message\":\"Session 404 was not found.\"}");
            Assert.IsNull(new SchedulingClient(_baseUrl).GetSession(404));
        }

        [Test]
        public void ServerErrorIsUnavailableNotAPortalMessage()
        {
            RespondOnce(500, "oops");
            Assert.Throws<SchedulingUnavailableException>(() => new SchedulingClient(_baseUrl).GetBooking(1));
        }

        [Test]
        public void UnreachableServiceOnAWriteIsUnavailable()
        {
            var closed = new TcpListener(IPAddress.Loopback, 0);
            closed.Start();
            var port = ((IPEndPoint)closed.LocalEndpoint).Port;
            closed.Stop();
            var client = new SchedulingClient("http://127.0.0.1:" + port + "/api/v1", 5);
            Assert.Throws<SchedulingUnavailableException>(() => client.Book(new ApiBookRequest
            {
                SessionId = 7, CandidateId = 9, AccommodationCode = "NONE", Channel = "PORTAL"
            }, "portal"));
        }
    }
}
