using System;

namespace AssessmentDelivery.Services.Common
{
    /// <summary>
    /// Business error that is safe to show to the user (stored proc RAISERROR text, rule failures).
    /// </summary>
    [Serializable]
    public class PortalException : Exception
    {
        public PortalException(string message) : base(message) { }
        public PortalException(string message, Exception inner) : base(message, inner) { }
    }
}
