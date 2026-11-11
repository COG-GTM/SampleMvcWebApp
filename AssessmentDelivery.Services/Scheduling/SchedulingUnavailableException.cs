using System;

namespace AssessmentDelivery.Services.Scheduling
{
    /// <summary>scheduling-service could not be reached or answered with a 5xx / unparseable error.</summary>
    [Serializable]
    public class SchedulingUnavailableException : Exception
    {
        public SchedulingUnavailableException(string message, Exception inner) : base(message, inner) { }
    }
}
