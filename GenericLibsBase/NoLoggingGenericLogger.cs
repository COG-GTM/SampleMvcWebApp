using System;

namespace GenericLibsBase
{
    /// <summary>
    /// Default no-op logger used until the host assigns a real logger factory.
    /// </summary>
    public class NoLoggingGenericLogger : IGenericLogger
    {
        public void Verbose(object message) { }
        public void VerboseFormat(string format, params object[] args) { }
        public void Info(object message) { }
        public void InfoFormat(string format, params object[] args) { }
        public void Warn(object message) { }
        public void WarnFormat(string format, params object[] args) { }
        public void Error(object message) { }
        public void ErrorFormat(string format, params object[] args) { }
        public void Critical(object message) { }
        public void Critical(object message, Exception ex) { }
        public void CriticalFormat(string format, params object[] args) { }
    }
}
