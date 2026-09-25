# escape=`
# Production image for SampleWebApp (ASP.NET MVC 5 on .NET Framework 4.x, IIS).
# Runtime pin is mirrored in infra/terraform/variables.tf (app_runtime_image) and must be kept in sync.
# Linux hosts cannot run Windows containers: infra/legacy/Dockerfile.mono is the Linux stand-in (see infra/README.md).
ARG RUNTIME_IMAGE=mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022
ARG SDK_IMAGE=mcr.microsoft.com/dotnet/framework/sdk:4.8-windowsservercore-ltsc2022

FROM ${SDK_IMAGE} AS build
WORKDIR C:\src
COPY . .
RUN nuget restore SampleWebApp.sln
RUN msbuild SampleWebApp\SampleWebApp.csproj /p:Configuration=Release /p:DeployOnBuild=true `
    /p:WebPublishMethod=FileSystem /p:PublishUrl=C:\publish /p:DeleteExistingFiles=true

FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR C:\inetpub\wwwroot
COPY --from=build C:\publish .
# IIS listens on 80; the load balancer (infra/nginx) upstreams to app:80 and probes /Home/Health.
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD powershell -command `
    try { (Invoke-WebRequest -UseBasicParsing http://localhost/Home/Health).StatusCode -eq 200 } catch { exit 1 }
