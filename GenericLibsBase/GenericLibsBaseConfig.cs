using System;

namespace GenericLibsBase
{
    /// <summary>
    /// Global configuration point for the logging abstraction. The host assigns
    /// <see cref="SetLoggerMethod"/> at startup; callers obtain loggers via <see cref="GetLogger"/>.
    /// </summary>
    public static class GenericLibsBaseConfig
    {
        private static Func<string, IGenericLogger> _getLoggerMethod = name => new NoLoggingGenericLogger();

        /// <summary>
        /// Set this at startup to control how loggers are created.
        /// </summary>
        public static Func<string, IGenericLogger> SetLoggerMethod
        {
            private get { return _getLoggerMethod; }
            set { _getLoggerMethod = value ?? (name => new NoLoggingGenericLogger()); }
        }

        public static IGenericLogger GetLogger(string name)
        {
            return _getLoggerMethod(name);
        }

        public static IGenericLogger GetLogger(Type typeForName)
        {
            return _getLoggerMethod(typeForName.Name);
        }
    }
}
