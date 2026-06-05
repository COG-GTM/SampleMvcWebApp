using System;

namespace GenericLibsBase
{
    /// <summary>
    /// Compatibility replacement for the original GenericLibsBaseConfig static logger factory.
    /// By default it writes to the .NET trace/console output. The host can replace the factory
    /// via <see cref="SetLoggerMethod"/>.
    /// </summary>
    public static class GenericLibsBaseConfig
    {
        private static Func<string, IGenericLogger> _loggerFactory = name => new ConsoleGenericLogger(name);

        public static Func<string, IGenericLogger> SetLoggerMethod
        {
            set { _loggerFactory = value; }
        }

        public static IGenericLogger GetLogger(string name)
        {
            return _loggerFactory(name);
        }
    }

    /// <summary>
    /// Simple default logger that writes to the console.
    /// </summary>
    public class ConsoleGenericLogger : IGenericLogger
    {
        private readonly string _name;

        public ConsoleGenericLogger(string name)
        {
            _name = name;
        }

        private void Write(string level, string message)
        {
            Console.WriteLine("{0:u} [{1}] {2}: {3}", DateTime.UtcNow, level, _name, message);
        }

        public void Verbose(string message) => Write("VERBOSE", message);
        public void Info(string message) => Write("INFO", message);
        public void InfoFormat(string format, params object[] args) => Write("INFO", string.Format(format, args));
        public void Warn(string message) => Write("WARN", message);
        public void WarnFormat(string format, params object[] args) => Write("WARN", string.Format(format, args));
        public void Error(string message) => Write("ERROR", message);
        public void Error(string message, Exception ex) => Write("ERROR", message + " " + ex);
        public void Critical(string message) => Write("CRITICAL", message);
        public void Critical(string message, Exception ex) => Write("CRITICAL", message + " " + ex);
        public void CriticalFormat(string format, params object[] args) => Write("CRITICAL", string.Format(format, args));
    }
}
