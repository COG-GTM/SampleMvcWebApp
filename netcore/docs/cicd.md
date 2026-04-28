# CI/CD Pipeline Documentation

## Overview

This document describes the CI/CD pipeline and DevOps setup for the .NET Core 6 migration of SampleMvcWebApp.

## Pipeline Architecture

```
Push/PR ──► CI (Build + Test) ──► Code Quality ──► CD (Deploy)
                │                       │               │
                ▼                       ▼               ▼
          Test Results          Format Check      Staging → Production
          Coverage Report       Security Scan
```

## Workflows

### 1. CI Pipeline (`.github/workflows/ci.yml`)

Triggered on push/PR to `master`, `main`, or `release/**` branches when `netcore/` files change.

**Jobs:**
- **Build**: Restores, builds, and uploads build artifacts
- **Test**: Runs unit and integration tests with code coverage
  - Unit tests with Coverlet code coverage collection
  - Integration tests via `WebApplicationFactory`
  - Coverage threshold enforcement (default: 80%)
  - HTML coverage report generation via ReportGenerator

### 2. Code Quality (`.github/workflows/code-quality.yml`)

Runs formatting, security, and build analysis checks.

**Jobs:**
- **Format Check**: Verifies code formatting with `dotnet format --verify-no-changes`
- **Security Scan**: Audits NuGet packages for known vulnerabilities
- **Build Warnings Analysis**: Builds with `/warnaserror` to catch warnings

### 3. CD Pipeline (`.github/workflows/cd.yml`)

Triggered on push to `main`/`master` or via manual dispatch.

**Jobs:**
- **Build & Test**: Full build and test verification before deployment
- **Build Docker Image**: Builds and smoke-tests the Docker image
- **Deploy to Staging**: Automatic deployment on merge to main
- **Deploy to Production**: Manual trigger only, requires staging success

## Docker Support

### Build locally

```bash
cd netcore
docker-compose up webapp
```

### Environments

| Service | Port | Environment |
|---------|------|-------------|
| `webapp` | 5000 | Development |
| `webapp-staging` | 5001 | Staging |

### Docker commands

```bash
# Build image
docker build -f netcore/Dockerfile -t sample-mvc-webapp .

# Run container
docker run -p 5000:80 -e ASPNETCORE_ENVIRONMENT=Development sample-mvc-webapp
```

## Health Checks

The application exposes three health check endpoints:

| Endpoint | Purpose |
|----------|---------|
| `/health` | Readiness probe — reports if the app is ready to serve traffic |
| `/health/startup` | Startup probe — reports if initialization is complete |
| `/health/live` | Liveness probe — always healthy if the process is running |

Response format:
```json
{
  "status": "Healthy",
  "totalDuration": 1.23,
  "entries": [
    {
      "name": "self",
      "status": "Healthy",
      "duration": 0.5,
      "description": "Application is running."
    }
  ]
}
```

## Monitoring & Logging

### Structured Logging

The application uses structured logging via the built-in .NET logging framework:

- **Development**: Console output with debug-level detail
- **Staging**: JSON-formatted console output at Information level
- **Production**: JSON-formatted console output at Warning level (application logs at Information)

### Request Logging

All HTTP requests are logged with:
- HTTP method
- Request path
- Response status code
- Elapsed time in milliseconds

### Environment-Specific Configuration

| Setting | Development | Staging | Production |
|---------|------------|---------|------------|
| Default log level | Debug | Information | Warning |
| ASP.NET Core log level | Information | Warning | Error |
| EF Core log level | Information | Warning | Error |
| App log level | Debug | Information | Information |

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ASPNETCORE_ENVIRONMENT` | Runtime environment | `Production` |
| `ASPNETCORE_URLS` | Listening URLs | `http://+:80` |

### GitHub Environments

Configure these GitHub environments for deployment:

1. **staging**: Set `STAGING_URL` variable
2. **production**: Set `PRODUCTION_URL` variable, enable required reviewers

### Secrets (Future)

When deploying to a specific platform, add these secrets:
- Container registry credentials
- Database connection strings
- Application Insights key (if using Azure)

## Adding New Quality Gates

To add additional code quality checks:

1. Add a new job to `.github/workflows/code-quality.yml`
2. Use the same `.NET` setup pattern
3. Upload any reports as artifacts

## Extending Deployments

To configure deployments for your platform:

1. Update the deploy steps in `.github/workflows/cd.yml`
2. Add required secrets to the GitHub environment
3. Update health check URLs in smoke tests
