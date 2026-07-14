using System;

namespace GenericLibsBase
{
    /// <summary>
    /// Logging abstraction used across the layers. Implementations bridge to a
    /// concrete logging framework (originally log4net; now Microsoft.Extensions.Logging).
    /// </summary>
    public interface IGenericLogger
    {
        void Verbose(object message);
        void VerboseFormat(string format, params object[] args);

        void Info(object message);
        void InfoFormat(string format, params object[] args);

        void Warn(object message);
        void WarnFormat(string format, params object[] args);

        void Error(object message);
        void ErrorFormat(string format, params object[] args);

        void Critical(object message);
        void Critical(object message, Exception ex);
        void CriticalFormat(string format, params object[] args);
    }
}
