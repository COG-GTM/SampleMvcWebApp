FROM mcr.microsoft.com/dotnet/aspnet:6.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build
WORKDIR /src

COPY netcore/SampleWebApp.NetCore.sln ./netcore/
COPY netcore/src/SampleWebApp/SampleWebApp.csproj ./netcore/src/SampleWebApp/

RUN dotnet restore netcore/SampleWebApp.NetCore.sln || dotnet restore netcore/src/SampleWebApp/SampleWebApp.csproj

COPY netcore/src/ ./netcore/src/

WORKDIR /src/netcore/src/SampleWebApp
RUN dotnet build -c Release -o /app/build

FROM build AS publish
RUN dotnet publish -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app

ENV ASPNETCORE_URLS=http://+:80
ENV ASPNETCORE_ENVIRONMENT=Production
ENV Logging__LogLevel__Default=Information
ENV Logging__LogLevel__Microsoft.AspNetCore=Warning

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:80/health || exit 1

COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "SampleWebApp.dll"]
