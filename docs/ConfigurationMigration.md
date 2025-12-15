# Configuration Migration Plan

This document outlines the migration strategy from `Web.config` to `appsettings.json` for the .NET Core application.

## Overview

The .NET Framework application uses `Web.config` for configuration, while .NET Core uses `appsettings.json` and environment-based configuration. This document maps the current configuration to the new format.

## Current Configuration (Web.config)

### Connection Strings

**Source: `SampleWebApp/Web.config`**
```xml
<connectionStrings>
  <add name="SampleWebAppDb" 
       connectionString="Data Source=(localdb)\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True" 
       providerName="System.Data.SqlClient" />
</connectionStrings>
```

### App Settings

**Source: `SampleWebApp/Web.config`**
```xml
<appSettings>
  <add key="webpages:Version" value="3.0.0.0" />
  <add key="webpages:Enabled" value="false" />
  <add key="ClientValidationEnabled" value="true" />
  <add key="UnobtrusiveJavaScriptEnabled" value="true" />
  <add key="owin:AutomaticAppStartup" value="false" />
</appSettings>
```

### Application Settings

**Source: `SampleWebApp/Web.config`**
```xml
<applicationSettings>
  <SampleWebApp.Properties.Settings>
    <setting name="HostTypeString" serializeAs="String">
      <value>LocalHost</value>
    </setting>
    <setting name="DatabaseLoginPrefix" serializeAs="String">
      <value>jonsmith_</value>
    </setting>
  </SampleWebApp.Properties.Settings>
</applicationSettings>
```

### Entity Framework Configuration

**Source: `SampleWebApp/Web.config`**
```xml
<entityFramework>
  <defaultConnectionFactory type="System.Data.Entity.Infrastructure.LocalDbConnectionFactory, EntityFramework">
    <parameters>
      <parameter value="v12.0" />
    </parameters>
  </defaultConnectionFactory>
  <providers>
    <provider invariantName="System.Data.SqlClient" 
              type="System.Data.Entity.SqlServer.SqlProviderServices, EntityFramework.SqlServer" />
  </providers>
</entityFramework>
```

## Target Configuration (appsettings.json)

### Base Configuration

**Target: `SampleWebApp.Core/appsettings.json`**
```json
{
  "ConnectionStrings": {
    "SampleWebAppDb": "Data Source=(localdb)\\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True"
  },
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning",
      "Microsoft.EntityFrameworkCore": "Warning"
    }
  },
  "AllowedHosts": "*",
  "Application": {
    "HostType": "LocalHost",
    "DatabaseLoginPrefix": "jonsmith_"
  }
}
```

### Development Configuration

**Target: `SampleWebApp.Core/appsettings.Development.json`**
```json
{
  "ConnectionStrings": {
    "SampleWebAppDb": "Data Source=(localdb)\\mssqllocaldb;Initial Catalog=SampleWebAppDb-Dev;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True"
  },
  "Logging": {
    "LogLevel": {
      "Default": "Debug",
      "Microsoft.AspNetCore": "Information",
      "Microsoft.EntityFrameworkCore": "Information"
    }
  },
  "Application": {
    "HostType": "LocalHost"
  }
}
```

### Production Configuration

**Target: `SampleWebApp.Core/appsettings.Production.json`**
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Warning",
      "Microsoft.AspNetCore": "Warning",
      "Microsoft.EntityFrameworkCore": "Warning"
    }
  },
  "Application": {
    "HostType": "Production"
  }
}
```

### Azure Configuration

**Target: `SampleWebApp.Core/appsettings.Azure.json`**
```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    },
    "ApplicationInsights": {
      "LogLevel": {
        "Default": "Information"
      }
    }
  },
  "Application": {
    "HostType": "Azure"
  }
}
```

## Configuration Mapping Table

| Web.config Section | Web.config Key | appsettings.json Path | Notes |
|--------------------|----------------|----------------------|-------|
| connectionStrings | SampleWebAppDb | ConnectionStrings:SampleWebAppDb | Direct mapping |
| appSettings | webpages:Version | N/A | Not needed in .NET Core |
| appSettings | webpages:Enabled | N/A | Not needed in .NET Core |
| appSettings | ClientValidationEnabled | N/A | Handled differently |
| appSettings | UnobtrusiveJavaScriptEnabled | N/A | Handled differently |
| appSettings | owin:AutomaticAppStartup | N/A | OWIN not used |
| applicationSettings | HostTypeString | Application:HostType | Custom setting |
| applicationSettings | DatabaseLoginPrefix | Application:DatabaseLoginPrefix | Custom setting |
| entityFramework | defaultConnectionFactory | N/A | Configured in code |
| system.web | compilation debug | ASPNETCORE_ENVIRONMENT | Environment-based |
| system.web | targetFramework | N/A | Project file |

## Environment Variables

For production deployments, sensitive configuration should use environment variables:

```bash
# Connection string (override appsettings.json)
export ConnectionStrings__SampleWebAppDb="Server=prod-server;Database=SampleWebAppDb;User Id=app;Password=secret;"

# Application settings
export Application__HostType="Production"

# ASP.NET Core environment
export ASPNETCORE_ENVIRONMENT="Production"
```

## Configuration Classes

### ApplicationSettings.cs

```csharp
namespace SampleWebApp.Core.Configuration
{
    public class ApplicationSettings
    {
        public string HostType { get; set; } = "LocalHost";
        public string DatabaseLoginPrefix { get; set; } = string.Empty;
    }
}
```

### Program.cs Configuration

```csharp
var builder = WebApplication.CreateBuilder(args);

// Bind configuration section to strongly-typed class
builder.Services.Configure<ApplicationSettings>(
    builder.Configuration.GetSection("Application"));

// Add DbContext with connection string
builder.Services.AddDbContext<SampleWebAppDb>(options =>
    options.UseSqlServer(
        builder.Configuration.GetConnectionString("SampleWebAppDb")));
```

## Secrets Management

### Development (User Secrets)

```bash
# Initialize user secrets
dotnet user-secrets init --project SampleWebApp.Core

# Set connection string secret
dotnet user-secrets set "ConnectionStrings:SampleWebAppDb" "Data Source=..." --project SampleWebApp.Core
```

### Production (Azure Key Vault)

```csharp
// Program.cs
builder.Configuration.AddAzureKeyVault(
    new Uri($"https://{keyVaultName}.vault.azure.net/"),
    new DefaultAzureCredential());
```

## Transform Files Migration

### Web.Debug.config -> appsettings.Development.json

The debug transforms are replaced by environment-specific JSON files.

### Web.Release.config -> appsettings.Production.json

Release transforms become production configuration.

### Web.AzureRelease.config -> appsettings.Azure.json

Azure-specific configuration in dedicated file.

## Validation Checklist

- [ ] Connection strings migrated correctly
- [ ] Custom application settings preserved
- [ ] Environment-specific overrides working
- [ ] Secrets not committed to source control
- [ ] Configuration binding tested
- [ ] Environment variables override JSON settings
- [ ] Logging configuration appropriate per environment

## References

- [Configuration in ASP.NET Core](https://docs.microsoft.com/en-us/aspnet/core/fundamentals/configuration/)
- [Options pattern in ASP.NET Core](https://docs.microsoft.com/en-us/aspnet/core/fundamentals/configuration/options)
- [Safe storage of app secrets](https://docs.microsoft.com/en-us/aspnet/core/security/app-secrets)
