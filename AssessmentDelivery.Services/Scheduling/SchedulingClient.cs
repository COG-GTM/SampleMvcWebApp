using System;
using System.Collections.Generic;
using System.Configuration;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using Newtonsoft.Json;

namespace AssessmentDelivery.Services.Scheduling
{
    /// <summary>
    /// Synchronous HTTP client for scheduling-service. Base URL comes from the Scheduling.BaseUrl appSetting
    /// (Web.config). Synchronous HttpWebRequest keeps the MVC controllers unchanged and avoids
    /// sync-over-async deadlocks on the ASP.NET request context.
    /// </summary>
    public class SchedulingClient : ISchedulingClient
    {
        public const string BaseUrlSetting = "Scheduling.BaseUrl";
        public const string TimeoutSetting = "Scheduling.TimeoutSeconds";
        public const string DefaultBaseUrl = "http://localhost:8084/api/v1";

        private static readonly JsonSerializerSettings Json = new JsonSerializerSettings
        {
            DateParseHandling = DateParseHandling.DateTime,
            DateTimeZoneHandling = DateTimeZoneHandling.Unspecified,
            NullValueHandling = NullValueHandling.Include
        };

        private readonly string _baseUrl;
        private readonly int _timeoutMs;

        public SchedulingClient()
            : this(ConfigurationManager.AppSettings[BaseUrlSetting], ParseTimeout(ConfigurationManager.AppSettings[TimeoutSetting]))
        {
        }

        public SchedulingClient(string baseUrl, int timeoutSeconds = 30)
        {
            _baseUrl = (string.IsNullOrWhiteSpace(baseUrl) ? DefaultBaseUrl : baseUrl.Trim()).TrimEnd('/');
            _timeoutMs = timeoutSeconds * 1000;
        }

        public string BaseUrl { get { return _baseUrl; } }

        //------------------------------------------------- sessions

        public IList<ApiSession> SearchSessions(SessionQuery q)
        {
            var qs = new QueryString()
                .Add("status", q.Status)
                .Add("examCode", q.ExamCode)
                .Add("examId", q.ExamId)
                .Add("centerCode", q.CenterCode)
                .Add("testCenterId", q.TestCenterId)
                .Add("from", q.From.HasValue ? q.From.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : null)
                .Add("to", q.To.HasValue ? q.To.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) : null)
                .Add("excludeSessionId", q.ExcludeSessionId)
                .Add("include", q.IncludeCounts ? "counts" : null)
                .Add("sort", q.Sort);
            return Send<List<ApiSession>>("GET", "/sessions" + qs, null, null, null);
        }

        public ApiSession GetSession(int sessionId)
        {
            return GetOrNull<ApiSession>("/sessions/" + sessionId);
        }

        public IList<ApiSessionSummary> SessionSummaryByCenter()
        {
            return Send<List<ApiSessionSummary>>("GET", "/sessions/summary?groupBy=testCenter", null, null, null);
        }

        public ApiCapacityChangeResult ChangeCapacity(int sessionId, int newCapacity, string actor)
        {
            return Send<ApiCapacityChangeResult>("POST", "/sessions/" + sessionId + "/capacity",
                new { newCapacity }, actor, NewKey());
        }

        public ApiCapacityAuditPage GetCapacityAudit(int testCenterId, DateTime from, DateTime to, int? sessionId, int page, int pageSize)
        {
            var qs = new QueryString()
                .Add("testCenterId", testCenterId)
                .Add("sessionId", sessionId)
                .Add("from", from.ToString("yyyy-MM-ddTHH:mm:ss", CultureInfo.InvariantCulture))
                .Add("to", to.ToString("yyyy-MM-ddTHH:mm:ss", CultureInfo.InvariantCulture))
                .Add("page", page)
                .Add("pageSize", pageSize);
            return Send<ApiCapacityAuditPage>("GET", "/sessions/capacity-audit" + qs, null, null, null);
        }

        public ApiRoster GetRoster(int sessionId)
        {
            return GetOrNull<ApiRoster>("/sessions/" + sessionId + "/roster?format=json");
        }

        //------------------------------------------------- bookings

        public IList<ApiBooking> SearchBookings(int candidateId, string sort)
        {
            var qs = new QueryString().Add("candidateId", candidateId).Add("sort", sort);
            return Send<List<ApiBooking>>("GET", "/bookings" + qs, null, null, null);
        }

        public ApiBooking GetBooking(int bookingId)
        {
            return GetOrNull<ApiBooking>("/bookings/" + bookingId);
        }

        public ApiBookingResult Book(ApiBookRequest request, string actor)
        {
            return Send<ApiBookingResult>("POST", "/bookings", request, actor, NewKey());
        }

        public ApiBooking Cancel(int bookingId, string reason, string actor)
        {
            return Send<ApiBooking>("POST", "/bookings/" + bookingId + "/cancel",
                new { reason = string.IsNullOrWhiteSpace(reason) ? null : reason }, actor, NewKey());
        }

        public ApiBookingResult Reschedule(int bookingId, int newSessionId, string channel, string actor)
        {
            return Send<ApiBookingResult>("POST", "/bookings/" + bookingId + "/reschedule",
                new { newSessionId, channel }, actor, NewKey());
        }

        //------------------------------------------------- transport

        private T GetOrNull<T>(string path) where T : class
        {
            try
            {
                return Send<T>("GET", path, null, null, null);
            }
            catch (SchedulingApiException ex)
            {
                if (ex.IsNotFound) return null;
                throw;
            }
        }

        private T Send<T>(string method, string path, object body, string actor, string idempotencyKey)
        {
            var request = (HttpWebRequest)WebRequest.Create(_baseUrl + path);
            request.Method = method;
            request.Accept = "application/json";
            request.Timeout = _timeoutMs;
            request.ReadWriteTimeout = _timeoutMs;
            if (!string.IsNullOrEmpty(actor)) request.Headers["X-Actor"] = actor;
            if (!string.IsNullOrEmpty(idempotencyKey)) request.Headers["Idempotency-Key"] = idempotencyKey;
            if (body != null)
            {
                var bytes = Encoding.UTF8.GetBytes(JsonConvert.SerializeObject(body, Json));
                request.ContentType = "application/json; charset=utf-8";
                request.ContentLength = bytes.Length;
                using (var s = request.GetRequestStream())
                    s.Write(bytes, 0, bytes.Length);
            }

            HttpWebResponse response;
            try
            {
                response = (HttpWebResponse)request.GetResponse();
            }
            catch (WebException ex)
            {
                var errorResponse = ex.Response as HttpWebResponse;
                if (errorResponse == null)
                    throw new SchedulingUnavailableException(method + " " + path + " failed: " + ex.Message, ex);
                using (errorResponse)
                    throw ToException(errorResponse, method, path);
            }
            using (response)
                return JsonConvert.DeserializeObject<T>(ReadBody(response), Json);
        }

        internal static Exception ToException(HttpWebResponse response, string method, string path)
        {
            var status = (int)response.StatusCode;
            var text = ReadBody(response);
            ApiError error = null;
            try { error = JsonConvert.DeserializeObject<ApiError>(text, Json); }
            catch (JsonException) { }
            if (status >= 400 && status < 500 && error != null && !string.IsNullOrEmpty(error.Message))
                return new SchedulingApiException(status, error.Error, error.Message, error.LegacyState);
            return new SchedulingUnavailableException(
                method + " " + path + " returned HTTP " + status + ": " + (text.Length > 300 ? text.Substring(0, 300) : text), null);
        }

        private static string ReadBody(HttpWebResponse response)
        {
            using (var stream = response.GetResponseStream())
            {
                if (stream == null) return string.Empty;
                using (var reader = new StreamReader(stream, Encoding.UTF8))
                    return reader.ReadToEnd();
            }
        }

        private static string NewKey()
        {
            return Guid.NewGuid().ToString();
        }

        private static int ParseTimeout(string value)
        {
            int seconds;
            return int.TryParse(value, out seconds) && seconds > 0 ? seconds : 30;
        }

        private sealed class QueryString
        {
            private readonly List<string> _parts = new List<string>();

            public QueryString Add(string name, object value)
            {
                if (value == null) return this;
                var text = Convert.ToString(value, CultureInfo.InvariantCulture);
                if (string.IsNullOrEmpty(text)) return this;
                _parts.Add(name + "=" + Uri.EscapeDataString(text));
                return this;
            }

            public override string ToString()
            {
                return _parts.Count == 0 ? string.Empty : "?" + string.Join("&", _parts);
            }
        }
    }
}
