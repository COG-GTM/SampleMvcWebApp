using System;

namespace GenericLibsBase
{
    /// <summary>
    /// Minimal logging abstraction kept for compatibility with the original GenericLibsBase package.
    /// </summary>
    public interface IGenericLogger
    {
        void Verbose(string message);
        void Info(string message);
        void InfoFormat(string format, params object[] args);
        void Warn(string message);
        void WarnFormat(string format, params object[] args);
        void Error(string message);
        void Error(string message, Exception ex);
        void Critical(string message);
        void Critical(string message, Exception ex);
        void CriticalFormat(string format, params object[] args);
    }
}
