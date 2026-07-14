using System;
using GenericLibsBase;
using Microsoft.Extensions.Logging;

namespace SampleWebApp.Infrastructure
{
    /// <summary>
    /// Bridges the GenericServices <see cref="IGenericLogger"/> abstraction onto
    /// Microsoft.Extensions.Logging, replacing the original log4net logger.
    /// </summary>
    public class MelGenericLogger : IGenericLogger
    {
        private readonly ILogger _logger;

        public MelGenericLogger(ILogger logger)
        {
            _logger = logger;
        }

        public void Verbose(object message) => _logger.LogDebug("{Message}", message);
        public void VerboseFormat(string format, params object[] args) => _logger.LogDebug(format, args);

        public void Info(object message) => _logger.LogInformation("{Message}", message);
        public void InfoFormat(string format, params object[] args) => _logger.LogInformation(format, args);

        public void Warn(object message) => _logger.LogWarning("{Message}", message);
        public void WarnFormat(string format, params object[] args) => _logger.LogWarning(format, args);

        public void Error(object message) => _logger.LogError("{Message}", message);
        public void ErrorFormat(string format, params object[] args) => _logger.LogError(format, args);

        public void Critical(object message) => _logger.LogCritical("{Message}", message);
        public void Critical(object message, Exception ex) => _logger.LogCritical(ex, "{Message}", message);
        public void CriticalFormat(string format, params object[] args) => _logger.LogCritical(format, args);
    }
}
