#!/usr/bin/env python3
"""Impact matrix: live infrastructure (demo/dependency-graph.json) vs what the code in <repo> declares.

Code contract is read from the working tree (run it on `master` -> all rows MATCH; on `plugin/migration` ->
the planted gaps light up):  runtime family/base image, listen port, health path, DB secret key name,
DB driver/dialect, and the ports/services the modern app needs from firewall + DNS.

  python3 tools/discover/diff.py --graph demo/dependency-graph.json --repo . [--markdown out.md] [--fail-on-drift]
"""
import argparse
import json
import re
import sys
from pathlib import Path


def rd(p):
    return p.read_text(errors="ignore") if p.exists() else ""


def code_contract(repo: Path):
    c = {}
    csproj = rd(repo / "SampleWebApp/SampleWebApp.csproj")
    tfm = re.search(r"<TargetFramework(?:Version)?>([^<]+)<", csproj)
    tfm = tfm.group(1) if tfm else "?"
    c["target_framework"] = tfm
    modern = tfm.startswith("net") and not tfm.startswith("net4")
    c["runtime_family"] = f"dotnet-{tfm[3:]}" if modern else "netframework-4.x"
    c["runtime_os"] = "linux" if modern else "windows"
    c["expected_base_image"] = f"mcr.microsoft.com/dotnet/aspnet:{tfm[3:]}" if modern else "mcr.microsoft.com/dotnet/framework/aspnet:4.8-*"

    program = rd(repo / "SampleWebApp/Program.cs")
    appsettings = rd(repo / "SampleWebApp/appsettings.json")
    launch = rd(repo / "SampleWebApp/Properties/launchSettings.json")
    webconfig = rd(repo / "SampleWebApp/Web.config")
    dockerfile = rd(repo / "Dockerfile")
    if modern:
        port = re.search(r"ASPNETCORE_HTTP_PORTS[\"']?\s*[:=]\s*[\"']?(\d+)", dockerfile + appsettings + program) or \
               re.search(r"http://\+:(\d+)|ListenAnyIP\((\d+)\)|http://\*:(\d+)", program + appsettings + dockerfile)
        c["port"] = int(next(g for g in port.groups() if g)) if port else 8080
        hc = re.search(r'MapHealthChecks\(\s*"([^"]+)"', program)
        c["health_path"] = hc.group(1) if hc else None
        env_keys = set(re.findall(r'Environment\.GetEnvironmentVariable\(\s*"([A-Z0-9_]+)"', program))
        cs_key = re.search(r'GetConnectionString\(\s*"([^"]+)"', program)
        cs = re.search(r'"ConnectionStrings"\s*:\s*\{\s*"([^"]+)"', appsettings)
        key = cs_key.group(1) if cs_key else (cs.group(1) if cs else "DefaultConnection")
        c["db_config_key"] = sorted(env_keys)[0] if env_keys else f"ConnectionStrings__{key}"
        c["db_config_key_source"] = "Program.cs env var" if env_keys else "appsettings.json ConnectionStrings (env ConnectionStrings__<name>)"
        c["db_driver"] = "Microsoft.EntityFrameworkCore.SqlServer (EF Core) / Microsoft.Data.SqlClient"
    else:
        c["port"] = 80
        hc = re.search(r"public ActionResult (Health)\(", rd(repo / "SampleWebApp/Controllers/HomeController.cs"))
        c["health_path"] = "/Home/Health" if hc else None
        c["db_config_key"] = "SAMPLEWEBAPP_DB_CONNECTION"
        c["db_config_key_source"] = "Web.config connectionStrings (injected by infra/legacy/entrypoint.sh)"
        c["db_driver"] = "EntityFramework 6 / System.Data.SqlClient"

    # DB feature the reporting consumer / stored proc depend on
    ctx = "\n".join(rd(p) for p in (repo / "DataLayer").rglob("*.cs")) if (repo / "DataLayer").exists() else ""
    c["db_stored_proc_call"] = "usp_PostSummaryByBlog" in ctx or "usp_PostSummaryByBlog" in rd(repo / "reporting/report.sh")
    c["ef_pool"] = bool(re.search(r"AddDbContextPool|Max Pool Size|MaxPoolSize", program + appsettings))
    # what the code's own infra declarations say (these are what the plugin should have updated)
    tfvars = rd(repo / "infra/terraform/variables.tf") + rd(repo / "infra/aws/variables.tf")
    def tf_default(name):
        m = re.search(r'variable\s+"' + name + r'"\s*\{[^}]*?default\s*=\s*"?([^"\n]+?)"?\s*\n', tfvars, re.S)
        return m.group(1).strip() if m else None
    c["declared_runtime_image"] = tf_default("app_runtime_image")
    c["declared_port"] = tf_default("app_port")
    c["declared_health_path"] = tf_default("app_health_path")
    c["declared_secret_key"] = tf_default("app_db_connection_env")
    c["dockerfile_base"] = (re.search(r"^ARG RUNTIME_IMAGE=(\S+)", dockerfile, re.M) or re.search(r"^FROM\s+(\S+)", dockerfile, re.M) or [None, None])[1] if dockerfile else None
    fw = rd(repo / "infra/firewall/allowlist.rules")
    c["firewall_app_ports"] = sorted({int(p) for p in re.findall(r"->\s+app\s+tcp/(\d+)", fw)})
    c["dns_names"] = sorted(set(re.findall(r"^\S+\s+(.*)$", rd(repo / "infra/dns/hosts"), re.M)))
    return c


def infra_contract(graph):
    nodes = {n["id"]: n for n in graph["nodes"]}
    tg = next((n for n in graph["nodes"] if n["type"] == "target_group"), None)
    inst = next((n for n in graph["nodes"] if n["type"] in ("ec2_instance", "ecs_service")), None)
    secret = next((n for n in graph["nodes"] if n["type"] == "secret"), None)
    db = next((n for n in graph["nodes"] if n["type"] in ("database", "rds_instance")), None)
    app_sgs = [nodes[s] for s in (inst or {}).get("attrs", {}).get("security_groups", []) if s in nodes] if inst else []
    sg_ports = sorted({p for sg in app_sgs for p in sg["attrs"].get("ingress_ports", []) if p != "all"})
    dns = sorted(n["name"] for n in graph["nodes"] if n["type"] == "dns_record")
    return {
        "target_group": tg, "instance": inst, "secret": secret, "db": db, "app_sgs": app_sgs,
        "health_path": (tg or {}).get("attrs", {}).get("health_check_path"),
        "port": (tg or {}).get("attrs", {}).get("port"),
        "runtime_image": (tg or {}).get("attrs", {}).get("runtime_image") or (inst or {}).get("attrs", {}).get("runtime_image"),
        "runtime_family": (inst or {}).get("attrs", {}).get("runtime_family"),
        "secret_key": (secret or {}).get("attrs", {}).get("key_name"),
        "db_engine": (db or {}).get("attrs", {}).get("engine"),
        "sg_ports": sg_ports, "dns": dns,
    }


def build_matrix(code, infra):
    rows = []
    def row(attr, infra_v, code_v, ok, resources, gap):
        rows.append({"attribute": attr, "infra": infra_v, "code": code_v, "status": "MATCH" if ok else "DRIFT", "resources": resources, "planted_gap": gap,
                     "fix": None})
    tg = infra["target_group"] or {}
    inst = infra["instance"] or {}
    res_tg = [tg.get("name")] if tg else []
    res_inst = [inst.get("name")] if inst else []

    img = infra["runtime_image"] or code["declared_runtime_image"]
    ok = bool(img) and (("framework" in img) == (code["runtime_os"] == "windows"))
    row("runtime base image", img, code["expected_base_image"], ok, res_tg + res_inst + ["Dockerfile", "infra/terraform", "infra/aws"], 1)
    rows[-1]["fix"] = None if ok else f"pin {code['expected_base_image']} in Dockerfile + terraform app_runtime_image; redeploy target group runtime tags"

    ok = str(infra["health_path"]) == str(code["health_path"])
    row("health check path", infra["health_path"], code["health_path"], ok, res_tg + ["nginx upstream (infra/nginx)"], 2)
    rows[-1]["fix"] = None if ok else f"target group health_check.path -> {code['health_path']}; nginx location /healthz proxy_pass -> {code['health_path']}"

    ok = str(infra["port"]) == str(code["port"])
    row("app listen port", infra["port"], code["port"], ok, res_tg + [sg["name"] for sg in infra["app_sgs"]] + ["nginx upstream"], 2)
    rows[-1]["fix"] = None if ok else f"target group port + nginx upstream -> {code['port']}"

    ok = str(code["port"]) in infra["sg_ports"] or (not infra["sg_ports"] and str(code["port"]) in map(str, code["firewall_app_ports"]))
    row("firewall / SG ingress for app port", f"sg ingress ports {infra['sg_ports']}; compose allowlist app tcp/{code['firewall_app_ports']}", f"tcp/{code['port']}",
        ok and code["port"] in code["firewall_app_ports"], [sg["name"] for sg in infra["app_sgs"]] + ["infra/firewall/allowlist.rules"], 5)
    rows[-1]["fix"] = None if rows[-1]["status"] == "MATCH" else f"add ALB->app tcp/{code['port']} SG rule; add 'ALLOW 172.28.0.0/24 -> app tcp/{code['port']}' to allowlist.rules"

    ok = infra["secret_key"] == code["db_config_key"]
    row("DB connection secret key name", infra["secret_key"], code["db_config_key"], ok, [(infra["secret"] or {}).get("name"), "infra/secrets/.env.example", "docker-compose app.environment"], 3)
    rows[-1]["fix"] = None if ok else f"rename secret / env to {code['db_config_key']} (or map {infra['secret_key']} -> {code['db_config_key']} in the deploy)"

    legacy_driver = code["db_driver"].startswith("EntityFramework 6")
    ok = legacy_driver or not code["db_stored_proc_call"] or code.get("ef_pool")
    row("DB driver / dialect vs stored procedure consumer", f"{infra['db_engine'] or 'sqlserver'} + dbo.usp_PostSummaryByBlog (reporting/)", code["db_driver"],
        legacy_driver, [(infra["db"] or {}).get("name"), "reporting/report.sh", "infra/db/01-schema.sql"], 4)
    rows[-1]["fix"] = None if legacy_driver else "EF Core maps the Tag<->Post join table with its own column names (TagsTagId/PostsPostId): keep EF6 names via .UsingEntity(...) or update the proc; verify pool defaults (Max Pool Size, MARS)"

    svc = "samplewebapp" if code["runtime_os"] == "windows" else "samplewebapp-core"
    need = f"{svc}.demo.internal"
    ok = code["runtime_os"] == "windows" or any(need in d for d in infra["dns"])
    row("DNS record for (renamed) service", infra["dns"], need if code["runtime_os"] != "windows" else "samplewebapp.demo.internal", ok,
        [n for n in infra["dns"]] + ["infra/dns/hosts"], 5)
    rows[-1]["fix"] = None if ok else f"add {need} (Route53 private zone + infra/dns/hosts) or keep the legacy name in appsettings"

    ok = code["runtime_os"] == "windows"
    row("under-load NFR (System.Web cache/session removed; EF Core pooling)", "baseline demo/BASELINE.json p95/p99", "no output caching / DbContext pool declared" if not code.get("ef_pool") else "pooling declared",
        ok or code.get("ef_pool"), ["Grafana samplewebapp-nfr", "load/k6-main.js"], 6)
    rows[-1]["fix"] = None if rows[-1]["status"] == "MATCH" else "AddDbContextPool + response caching on /Posts; re-run make loadtest until within demo/BASELINE.json gates"
    return rows


def render(rows, code, markdown):
    hdr = ["attribute", "infra (live)", "code declares", "status", "affected resources", "gap#"]
    out = [f"## Impact matrix — live infra vs code ({code['target_framework']}, {code['runtime_family']})", "",
           "| " + " | ".join(hdr) + " |", "|" + "---|" * len(hdr)]
    for r in rows:
        out.append(f"| {r['attribute']} | `{r['infra']}` | `{r['code']}` | **{r['status']}** | {', '.join(str(x) for x in r['resources'] if x)} | {r['planted_gap']} |")
    drift = [r for r in rows if r["status"] == "DRIFT"]
    out += ["", f"**{len(drift)} DRIFT / {len(rows)} checks**", ""]
    if drift:
        out += ["Suggested fixes:", ""] + [f"- gap {r['planted_gap']} — {r['attribute']}: {r['fix']}" for r in drift]
    text = "\n".join(out)
    if markdown:
        Path(markdown).write_text(text + "\n")
    print(text)
    return len(drift)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--graph", default="demo/dependency-graph.json")
    ap.add_argument("--repo", default=".")
    ap.add_argument("--markdown", help="also write the matrix to this file")
    ap.add_argument("--json", help="write rows as JSON")
    ap.add_argument("--fail-on-drift", action="store_true")
    a = ap.parse_args()
    graph = json.load(open(a.graph))
    code = code_contract(Path(a.repo))
    infra = infra_contract(graph)
    rows = build_matrix(code, infra)
    if a.json:
        json.dump({"code_contract": code, "rows": rows}, open(a.json, "w"), indent=2, default=str)
    n = render(rows, code, a.markdown)
    sys.exit(1 if (a.fail_on_drift and n) else 0)


if __name__ == "__main__":
    main()
