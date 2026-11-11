using System;

namespace AssessmentDelivery.Services.Scheduling
{
    /// <summary>
    /// A 4xx answer from scheduling-service. Message is the service's legacy proc text
    /// (contract section 4), so callers can surface it unchanged.
    /// </summary>
    [Serializable]
    public class SchedulingApiException : Exception
    {
        public SchedulingApiException(int httpStatus, string error, string message, int? legacyState)
            : base(message)
        {
            HttpStatus = httpStatus;
            Error = error;
            LegacyState = legacyState;
        }

        public int HttpStatus { get; private set; }
        public string Error { get; private set; }
        public int? LegacyState { get; private set; }
        public bool IsNotFound { get { return HttpStatus == 404; } }
    }
}
