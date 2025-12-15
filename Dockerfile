# Dockerfile for SampleMvcWebApp.Core
# Multi-stage build for optimized production image

# Build stage
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Copy solution and project files first for better layer caching
COPY ["SampleMvcWebApp.Core.sln", "./"]
COPY ["SampleWebApp.Core/SampleWebApp.Core.csproj", "SampleWebApp.Core/"]
COPY ["ServiceLayer.Core/ServiceLayer.Core.csproj", "ServiceLayer.Core/"]
COPY ["DataLayer.Core/DataLayer.Core.csproj", "DataLayer.Core/"]
COPY ["Tests.Core/Tests.Core.csproj", "Tests.Core/"]

# Restore dependencies
RUN dotnet restore "SampleMvcWebApp.Core.sln"

# Copy all source code
COPY ["SampleWebApp.Core/", "SampleWebApp.Core/"]
COPY ["ServiceLayer.Core/", "ServiceLayer.Core/"]
COPY ["DataLayer.Core/", "DataLayer.Core/"]
COPY ["Tests.Core/", "Tests.Core/"]

# Build the application
WORKDIR "/src/SampleWebApp.Core"
RUN dotnet build "SampleWebApp.Core.csproj" -c Release -o /app/build

# Publish stage
FROM build AS publish
RUN dotnet publish "SampleWebApp.Core.csproj" -c Release -o /app/publish /p:UseAppHost=false

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app

# Create non-root user for security
RUN adduser --disabled-password --gecos '' appuser && chown -R appuser /app
USER appuser

# Copy published application
COPY --from=publish /app/publish .

# Expose port 80 (standard HTTP port for containers)
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:80/health || exit 1

# Set environment variables
ENV ASPNETCORE_URLS=http://+:80
ENV ASPNETCORE_ENVIRONMENT=Production

# Entry point
ENTRYPOINT ["dotnet", "SampleWebApp.Core.dll"]
