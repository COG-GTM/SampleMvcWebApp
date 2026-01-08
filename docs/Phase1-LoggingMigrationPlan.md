# Logging Migration Plan: log4net to Microsoft.Extensions.Logging

## Overview

This document outlines the strategy for migrating from log4net to Microsoft.Extensions.Logging as part of the .NET 8 migration preparation. The migration will be implemented in phases to ensure a smooth transition while maintaining compatibility with both .NET Framework 4.5.1 and future .NET 8.

## Current Logging Implementation

### log4net Usage Analysis

**Current Version:** log4net 2.0.3

**Configuration Location:** `SampleWebApp/Log4Net.xml`

**Logger Implementations:**
1. `Log4NetGenericLogger` - Located at `SampleWebApp/Infrastructure/Log4NetGenericLogger.cs`
2. `TraceGenericLogger` - Located at `SampleWebApp/Infrastructure/TraceGenericLogger.cs` (used for Azure)

**Logging Interface:** `IGenericLogger` from GenericLibsBase

**Current Usage Pattern:**
```csharp
// In WebUiInitialise.cs
GenericLibsBaseConfig.SetLoggerMethod = name => new Log4NetGenericLogger(name);
// or for Azure:
GenericLibsBaseConfig.SetLoggerMethod = name => new TraceGenericLogger(name);

// Usage throughout the application:
GenericLibsBaseConfig.GetLogger("LoggerName").Info("Message");
```

### Current Logger Interface (IGenericLogger)

The existing `IGenericLogger` interface from GenericLibsBase provides:
- `Verbose(object message)`
- `VerboseFormat(string format, params object[] args)`
- `Info(object message)`
- `InfoFormat(string format, params object[] args)`
- `Warn(object message)`
- `WarnFormat(string format, params object[] args)`
- `Error(object message)`
- `ErrorFormat(string format, params object[] args)`
- `Critical(object message)`
- `Critical(object message, Exception ex)`
- `CriticalFormat(string format, params object[] args)`

---

## Migration Strategy

### Phase 1: Create Abstraction Layer (Current Phase)

**Objective:** Create a logging abstraction that can work with both log4net and Microsoft.Extensions.Logging.

**Steps:**

1. **Create a new logging abstraction interface** that mirrors Microsoft.Extensions.Logging patterns while remaining compatible with the existing IGenericLogger interface.

2. **Implement adapter pattern** to wrap both log4net and Microsoft.Extensions.Logging behind the same interface.

3. **Document all logging call sites** in the codebase for future migration.

### Phase 2: Implement Microsoft.Extensions.Logging Adapter

**Objective:** Create an adapter that implements IGenericLogger using Microsoft.Extensions.Logging.

**Implementation:**

```csharp
// New file: SampleWebApp/Infrastructure/MicrosoftExtensionsLogger.cs
using System;
using GenericLibsBase;
using Microsoft.Extensions.Logging;

namespace SampleWebApp.Infrastructure
{
    public class MicrosoftExtensionsLogger : IGenericLogger
    {
        private readonly ILogger _logger;

        public MicrosoftExtensionsLogger(ILogger logger)
        {
            _logger = logger;
        }

        public void Verbose(object message)
        {
            _logger.LogDebug("{Message}", message);
        }

        public void VerboseFormat(string format, params object[] args)
        {
            _logger.LogDebug(format, args);
        }

        public void Info(object message)
        {
            _logger.LogInformation("{Message}", message);
        }

        public void InfoFormat(string format, params object[] args)
        {
            _logger.LogInformation(format, args);
        }

        public void Warn(object message)
        {
            _logger.LogWarning("{Message}", message);
        }

        public void WarnFormat(string format, params object[] args)
        {
            _logger.LogWarning(format, args);
        }

        public void Error(object message)
        {
            _logger.LogError("{Message}", message);
        }

        public void ErrorFormat(string format, params object[] args)
        {
            _logger.LogError(format, args);
        }

        public void Critical(object message)
        {
            _logger.LogCritical("{Message}", message);
        }

        public void Critical(object message, Exception ex)
        {
            _logger.LogCritical(ex, "{Message}", message);
        }

        public void CriticalFormat(string format, params object[] args)
        {
            _logger.LogCritical(format, args);
        }
    }
}
```

### Phase 3: Full Migration to Microsoft.Extensions.Logging (.NET 8)

**Objective:** Replace all log4net usage with Microsoft.Extensions.Logging when migrating to .NET 8.

**Steps:**

1. Remove log4net NuGet package
2. Add Microsoft.Extensions.Logging packages
3. Configure logging in Program.cs/Startup.cs
4. Update DI container to register ILogger<T>
5. Replace GenericLibsBase.IGenericLogger with Microsoft.Extensions.Logging.ILogger<T>

---

## Log Level Mapping

| IGenericLogger | log4net | Microsoft.Extensions.Logging |
|----------------|---------|------------------------------|
| Verbose | Debug | LogDebug |
| Info | Info | LogInformation |
| Warn | Warn | LogWarning |
| Error | Error | LogError |
| Critical | Fatal | LogCritical |

---

## Configuration Migration

### Current log4net Configuration (Log4Net.xml)

The current configuration likely includes:
- File appenders for log files
- Console appenders for development
- Log level filters
- Rolling file policies

### Microsoft.Extensions.Logging Configuration (appsettings.json)

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft": "Warning",
      "Microsoft.Hosting.Lifetime": "Information",
      "SampleWebApp": "Debug"
    },
    "Console": {
      "IncludeScopes": true,
      "TimestampFormat": "yyyy-MM-dd HH:mm:ss "
    },
    "File": {
      "Path": "logs/app.log",
      "Append": true,
      "MinLevel": "Information"
    }
  }
}
```

---

## Files Requiring Updates

### Phase 1 (Current)
- No code changes required, only documentation

### Phase 2 (Intermediate)
1. `SampleWebApp/Infrastructure/MicrosoftExtensionsLogger.cs` - New adapter class
2. `SampleWebApp/Infrastructure/WebUiInitialise.cs` - Add conditional logging setup
3. `SampleWebApp/packages.config` - Add Microsoft.Extensions.Logging.Abstractions

### Phase 3 (.NET 8 Migration)
1. `SampleWebApp/Program.cs` - Configure logging in host builder
2. `SampleWebApp/Startup.cs` - Register logging services
3. All files using `GenericLibsBaseConfig.GetLogger()` - Replace with ILogger<T> injection
4. Remove `Log4NetGenericLogger.cs` and `TraceGenericLogger.cs`
5. Remove `Log4Net.xml` configuration file

---

## Logging Call Sites Inventory

### SampleWebApp Project
| File | Line | Current Usage |
|------|------|---------------|
| Infrastructure/WebUiInitialise.cs | 107 | `GenericLibsBaseConfig.GetLogger("LoggerSetup").Info(...)` |

### ServiceLayer Project
- No direct logging calls found (uses GenericServices internal logging)

### DataLayer Project
- No direct logging calls found (uses GenericServices internal logging)

---

## Testing Strategy

### Unit Tests for Logging Adapter

```csharp
[TestFixture]
public class LoggingAdapterTests
{
    [Test]
    public void MicrosoftExtensionsLogger_Info_LogsCorrectLevel()
    {
        // Arrange
        var mockLogger = new Mock<ILogger>();
        var adapter = new MicrosoftExtensionsLogger(mockLogger.Object);
        
        // Act
        adapter.Info("Test message");
        
        // Assert
        mockLogger.Verify(
            x => x.Log(
                LogLevel.Information,
                It.IsAny<EventId>(),
                It.IsAny<It.IsAnyType>(),
                It.IsAny<Exception>(),
                It.IsAny<Func<It.IsAnyType, Exception, string>>()),
            Times.Once);
    }
}
```

---

## Rollback Plan

If issues are encountered during migration:

1. **Phase 2 Rollback:** Simply revert to using Log4NetGenericLogger in WebUiInitialise.cs
2. **Phase 3 Rollback:** Restore log4net package and configuration files from version control

---

## Timeline

| Phase | Description | Target |
|-------|-------------|--------|
| Phase 1 | Documentation and planning | Current (Phase 1 of .NET 8 prep) |
| Phase 2 | Create adapter, maintain dual support | Before .NET 8 migration |
| Phase 3 | Full migration to Microsoft.Extensions.Logging | During .NET 8 migration |

---

## Benefits of Microsoft.Extensions.Logging

1. **Built-in to .NET Core/.NET 8** - No additional dependencies required
2. **Structured Logging** - Native support for structured log data
3. **Provider Agnostic** - Easy to switch between logging providers (Console, File, Azure, etc.)
4. **Dependency Injection** - First-class support for DI with ILogger<T>
5. **Performance** - Optimized for high-performance scenarios with LoggerMessage.Define
6. **Configuration** - Easy configuration through appsettings.json
7. **Scopes** - Built-in support for logging scopes

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing logging | Adapter pattern maintains backward compatibility |
| Missing log entries during migration | Comprehensive testing before deployment |
| Performance degradation | Benchmark logging performance before and after |
| Configuration complexity | Document all configuration changes |

---

*Document created: Phase 1 - Foundation and Preparation for .NET 8 Migration*
*Last updated: January 2026*
