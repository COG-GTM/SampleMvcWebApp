using System;
using GenericLibsBase;
using Microsoft.Extensions.Logging;

namespace SampleWebApp.Infrastructure
{
    public enum HostTypes { NotSet, LocalHost, WebWiz, Azure };

    /// <summary>
    /// Holds the host type shown in the UI and wires the GenericServices logger
    /// abstraction onto Microsoft.Extensions.Logging.
    /// </summary>
    public static class WebUiInitialise
    {
        public const string DatabaseConnectionStringName = "SampleWebAppDb";

        /// <summary>
        /// The host the app believes it is running on (shown in the page footer).
        /// </summary>
        public static HostTypes HostType { get; private set; }

        /// <summary>
        /// Called at startup. Decodes the configured host type and routes the
        /// GenericServices logger through the supplied logger factory.
        /// </summary>
        public static void InitialiseThis(string hostTypeString, ILoggerFactory loggerFactory)
        {
            HostType = DecodeHostType(hostTypeString);

            GenericLibsBaseConfig.SetLoggerMethod = name => new MelGenericLogger(loggerFactory.CreateLogger(name));
            GenericLibsBaseConfig.GetLogger("LoggerSetup").Info("We have just assigned a logger.");
        }

        private static HostTypes DecodeHostType(string hostTypeString)
        {
            HostTypes hostType;
            Enum.TryParse(hostTypeString, true, out hostType);
            return hostType;
        }
    }
}
