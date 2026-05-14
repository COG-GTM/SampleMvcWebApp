# Phase 1 Troubleshooting

> Companion to [Phase1-Migration-Guide.md](Phase1-Migration-Guide.md).
> If you hit a problem not listed here, add it. Pull requests improving this
> doc count toward Phase 1 acceptance criteria.

A catalogue of issues encountered while migrating `HomeController` to
ASP.NET Core 6, with the resolutions actually used. Symptoms are quoted
verbatim where possible so error-message searches land here.

---

## Build / restore

### "error NETSDK1045: The current .NET SDK does not support targeting .NET 6.0."

You don't have the .NET 6 SDK installed.

```bash
# Linux / WSL
sudo apt-get update && sudo apt-get install -y dotnet-sdk-6.0

# macOS
brew install --cask dotnet-sdk

# Windows: install from https://dotnet.microsoft.com/download/dotnet/6.0
dotnet --list-sdks   # confirm 6.0.x appears
```

Phase 1 pins the SDK in `global.json` at the repo root. If you have multiple
SDKs installed, that file is what `dotnet` resolves against — verify with
`dotnet --version` from inside the repo.

### "error CS0234: The type or namespace name 'Mvc' does not exist in the namespace 'System.Web'"

You are compiling the legacy MVC5 project with the .NET 6 SDK. The legacy
`SampleWebApp.csproj` (root-level) requires .NET Framework / msbuild on
Windows. Build the legacy solution from Visual Studio with the
.NET Framework 4.5.1 targeting pack installed, or build only the new
solution from the command line:

```bash
dotnet build netcore/SampleWebApp.NetCore.sln
```

### "warning MSB3277: Found conflicts between different versions of …"

Common in mixed solutions during migration. As long as it's a warning, not
an error, the new app is fine. Pin the version in `Directory.Packages.props`
(central package management) if it bleeds across projects.

### `dotnet restore` hangs / times out behind a proxy

Set:

```bash
export DOTNET_CLI_TELEMETRY_OPTOUT=1
export NUGET_XMLDOC_MODE=skip
dotnet nuget locals all --clear
```

If you are on an enterprise network with a NuGet proxy, copy the corporate
`NuGet.Config` into the repo root or into `%AppData%/NuGet/NuGet.Config`.

---

## Runtime

### Static files (CSS, JS, images) return 404

ASP.NET Core only serves files from `wwwroot/`. Two common causes:

1. The file is under `Content/` or `Scripts/` (legacy path). Move it under
   `wwwroot/css/` or `wwwroot/js/`. See
   [Phase1-Configuration-Migration.md §7](Phase1-Configuration-Migration.md#7-static-files-and-bundling).
2. `app.UseStaticFiles()` is missing from `Program.cs`. It must appear
   **before** `app.UseRouting()`.

### Views render but Bootstrap / jQuery don't load

Two possibilities:

- The Razor layout still references legacy bundle paths like
  `Scripts/bootstrap.bundle.min.js` or `~/bundles/jquery`. Replace with
  `<script src="~/lib/bootstrap/dist/js/bootstrap.bundle.min.js"></script>`.
- The libraries haven't been copied under `wwwroot/lib/`. Use LibMan
  (`libman restore`) or copy them from the legacy `Scripts/` and `Content/`
  folders. The legacy `bootstrap` 3.3.2 ships under
  `SampleWebApp/Content/bootstrap.*` and `Scripts/bootstrap.*`.

### `/Home/Internals` throws `PlatformNotSupportedException`

Symptom:

```
System.PlatformNotSupportedException: System.Diagnostics.PerformanceCounter
is not supported on this platform.
```

You ported `InternalsInfo` literally instead of swapping the Windows-only
`PerformanceCounter` for the cross-platform GC API. Apply:

```csharp
var gcInfo = GC.GetGCMemoryInfo();
AvailableMbytes = (int)(gcInfo.TotalAvailableMemoryBytes / (1024L * 1024L));
```

See [Phase1-Migration-Guide.md §4.3](Phase1-Migration-Guide.md#43-models-with-framework-specific-apis).

### Antiforgery token errors after migrating a POST action

ASP.NET Core enforces antiforgery on every POST by default if you use
`<form asp-controller="..." asp-action="...">`. Either:

- Add `@Html.AntiForgeryToken()` (still works) or use the tag-helper form,
  and ensure the controller action is decorated with `[ValidateAntiForgeryToken]`.
- For genuinely cross-origin POSTs (e.g. webhooks), opt out with
  `[IgnoreAntiforgeryToken]` and document why.

### "InvalidOperationException: Endpoint Routing does not support IRouter-based actions."

You added `app.UseMvc(routes => routes.MapRoute(...))`. That API is removed.
Use `app.MapControllerRoute(...)` after `app.UseRouting()` instead.

---

## Tests

### Integration tests fail with `Could not find a part of the path '…/Views/Home/Index.cshtml'`

`WebApplicationFactory<Program>` runs the app from the test binary's output
directory, which doesn't contain the views by default. Add to the web app
`.csproj`:

```xml
<PropertyGroup>
  <PreserveCompilationContext>true</PreserveCompilationContext>
  <PreserveCompilationReferences>true</PreserveCompilationReferences>
  <CopyRefAssembliesToPublishDirectory>true</CopyRefAssembliesToPublishDirectory>
</PropertyGroup>
```

…and in the test project add a `ProjectReference` to the web app with
`<PrivateAssets>none</PrivateAssets>`.

`Microsoft.AspNetCore.Mvc.Testing` 6.0 typically handles this automatically
when the test project references the web project — confirm the reference is
not marked `Private="true"` or `IncludeAssets="all"` aggressively.

### `WebApplicationFactory<Program>` complains `Program is not accessible`

Top-level statements compile `Program` as `internal`. Either add
`public partial class Program { }` at the bottom of `Program.cs`, or add to
the test project:

```xml
<ItemGroup>
  <InternalsVisibleTo Include="SampleWebApp.IntegrationTests" />
</ItemGroup>
```

We prefer the `partial class Program {}` approach because it is local to the
file consumers need it.

### `xunit.runner.visualstudio` doesn't discover any tests

Make sure the test project has **all three** packages:

```xml
<PackageReference Include="xunit" Version="2.4.*" />
<PackageReference Include="xunit.runner.visualstudio" Version="2.4.*" />
<PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.*" />
```

Missing `Microsoft.NET.Test.Sdk` is the usual culprit — the runner exists
but VSTest can't find a host.

### Coverage report claims 0% for `HomeController`

Coverlet excludes anonymous types and async state machines by default but
otherwise needs `[assembly: ExcludeFromCodeCoverage]` markers respected.
Check the `coverlet.runsettings`:

```xml
<DataCollectionRunSettings>
  <DataCollectors>
    <DataCollector friendlyName="XPlat code coverage">
      <Configuration>
        <Format>cobertura</Format>
        <Include>[SampleWebApp]*</Include>
        <Exclude>[xunit*]*,[*Tests]*</Exclude>
        <ExcludeByFile>**/Program.cs,**/*.cshtml,**/obj/**/*.cs</ExcludeByFile>
      </Configuration>
    </DataCollector>
  </DataCollectors>
</DataCollectionRunSettings>
```

If `Include` is wrong (e.g. matches the test assembly), nothing in the
SUT contributes to coverage.

---

## CI

### CI builds twice — once for the legacy solution, once for `netcore`

That is intentional during the strangle. See
[`netcore/docs/cicd.md`](../netcore/docs/cicd.md). If only one of the two
build legs runs, your branch's path filter is too tight — `ci.yml` includes
both `netcore/**` and the legacy paths.

### CI on the legacy `.NET Framework 4.5.1` solution times out / fails on Linux

It will. The legacy solution **cannot** build on Linux runners. Use
`windows-latest` for the legacy build leg and `ubuntu-latest` for the
`netcore` leg.

### `dotnet format --verify-no-changes` fails in CI but the local build is clean

`dotnet format` honours `.editorconfig` plus the .NET 6 default formatter.
Run locally:

```bash
dotnet format netcore/SampleWebApp.NetCore.sln
git diff --stat                     # shows what would change
```

Commit the formatter's changes and push.

---

## Local environment

### Visual Studio opens but the new `netcore` solution targets are greyed out

Install the **ASP.NET and web development** workload from the VS Installer.
Verify .NET 6 SDK appears under Tools → Options → Environment → Preview
Features → "Use previews of the .NET SDK".

### `https://localhost:5001` shows a certificate warning in the browser

Trust the dev cert once per machine:

```bash
dotnet dev-certs https --trust
```

On Linux/WSL, this only writes the cert under your user profile. Browsers
in WSL2 still won't trust it unless launched from the same user.

### SQL LocalDB is unreachable in WSL / Linux

`(localdb)\mssqllocaldb` is Windows-only. For Phase 1, `HomeController`
does not open the database, so this is not blocking. For Phase 2 on
Linux dev boxes, run SQL Server in Docker:

```bash
docker run --name sql -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=YourStrong!Passw0rd" \
  -p 1433:1433 -d mcr.microsoft.com/mssql/server:2022-latest
```

Update the connection string in `appsettings.Development.json` to point
to `Server=localhost;...` accordingly.

---

## When this doc isn't enough

1. Re-read [Phase1-Migration-Guide.md](Phase1-Migration-Guide.md).
2. Search closed PRs touching `netcore/` — they will have hit the same
   issues.
3. Post in `#net-upgrade` Slack with the exact error message, your OS,
   `dotnet --info` output, and the failing command. Add the resolution
   back to this doc as a new section once solved.
