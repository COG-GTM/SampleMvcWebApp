#!/usr/bin/env python3
"""Agentless AWS dependency discovery ("Wiz-style", but for the *application* dependency graph).

Walks every resource tagged Project=<project> (ELBv2, target groups, EC2, ECS, security groups,
Route53, Secrets Manager, RDS, ECR) via boto3 and emits a typed graph:

  nodes: {id, type, arn, name, attrs{...}}
  edges: {source, target, type}   type in routes_to | health_checks | allows_ingress | resolves_to |
                                          reads_secret | connects_to_db | runs_image

Outputs demo/dependency-graph.json, .md (Mermaid + tables) and .png (Graphviz).
"""
import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from urllib.parse import urlparse

import boto3

EDGE_TYPES = ("routes_to", "health_checks", "allows_ingress", "resolves_to", "reads_secret", "connects_to_db", "runs_image")


class Graph:
    def __init__(self):
        self.nodes = {}
        self.edges = []

    def node(self, nid, ntype, name, arn=None, **attrs):
        n = self.nodes.setdefault(nid, {"id": nid, "type": ntype, "name": name, "arn": arn, "attrs": {}})
        n["attrs"].update({k: v for k, v in attrs.items() if v not in (None, "", [])})
        if arn:
            n["arn"] = arn
        return n

    def edge(self, src, dst, etype, **attrs):
        assert etype in EDGE_TYPES, etype
        e = {"source": src, "target": dst, "type": etype}
        if attrs:
            e["attrs"] = attrs
        if e not in self.edges:
            self.edges.append(e)


def tagv(tags, key, default=None):
    for t in tags or []:
        if t.get("Key") == key:
            return t.get("Value")
    return default


def parse_conn(conn):
    """SQL Server style: Server=host,port;Database=x;..."""
    out = {}
    for part in conn.split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k.strip().lower()] = v.strip()
    server = out.get("server") or out.get("data source") or ""
    host, _, port = server.partition(",")
    return {"host": host, "port": int(port) if port.isdigit() else 1433, "database": out.get("database") or out.get("initial catalog")}


def discover(project, region, read_secret_values, track=None):
    sess = boto3.Session(region_name=region)
    tagging = sess.client("resourcegroupstaggingapi")
    ec2 = sess.client("ec2")
    elbv2 = sess.client("elbv2")
    r53 = sess.client("route53")
    sm = sess.client("secretsmanager")
    rds = sess.client("rds")
    ecr = sess.client("ecr")
    ecs = sess.client("ecs")
    iam = sess.client("iam")
    g = Graph()

    arns = []
    filters = [{"Key": "Project", "Values": [project]}] + ([{"Key": "Track", "Values": [track]}] if track else [])
    pages = tagging.get_paginator("get_resources").paginate(TagFilters=filters)
    for p in pages:
        arns += [m["ResourceARN"] for m in p["ResourceTagMappingList"]]
    by_service = {}
    for a in arns:
        by_service.setdefault(a.split(":")[2], []).append(a)
    print(f"[discover] {len(arns)} resources tagged Project={project}{' Track=' + track if track else ''}: " + ", ".join(f"{k}={len(v)}" for k, v in sorted(by_service.items())), file=sys.stderr)

    # ---- ELBv2: load balancers, listeners, target groups, targets ----------------------------------
    lb_arns = [a for a in by_service.get("elasticloadbalancing", []) if ":loadbalancer/" in a]
    tg_arns = [a for a in by_service.get("elasticloadbalancing", []) if ":targetgroup/" in a]
    lbs = elbv2.describe_load_balancers(LoadBalancerArns=lb_arns)["LoadBalancers"] if lb_arns else []
    for lb in lbs:
        g.node(lb["LoadBalancerArn"], "alb", lb["LoadBalancerName"], lb["LoadBalancerArn"],
               dns_name=lb["DNSName"], scheme=lb["Scheme"], vpc=lb["VpcId"], subnets=[z["SubnetId"] for z in lb["AvailabilityZones"]],
               security_groups=lb.get("SecurityGroups", []))
        for sg in lb.get("SecurityGroups", []):
            g.edge(sg, lb["LoadBalancerArn"], "allows_ingress", scope="attached")
        for lst in elbv2.describe_listeners(LoadBalancerArn=lb["LoadBalancerArn"])["Listeners"]:
            lid = lst["ListenerArn"]
            g.node(lid, "listener", f"{lst['Protocol']}:{lst['Port']}", lid, port=lst["Port"], protocol=lst["Protocol"])
            g.edge(lb["LoadBalancerArn"], lid, "routes_to", port=lst["Port"])
            for act in lst.get("DefaultActions", []):
                if act.get("TargetGroupArn"):
                    g.edge(lid, act["TargetGroupArn"], "routes_to")
    tgs = elbv2.describe_target_groups(TargetGroupArns=tg_arns)["TargetGroups"] if tg_arns else []
    for tg in tgs:
        tid = tg["TargetGroupArn"]
        tg_tags = elbv2.describe_tags(ResourceArns=[tid])["TagDescriptions"][0]["Tags"]
        g.node(tid, "target_group", tg["TargetGroupName"], tid, port=tg["Port"], protocol=tg["Protocol"],
               target_type=tg["TargetType"], health_check_path=tg.get("HealthCheckPath"), health_check_port=tg.get("HealthCheckPort"),
               health_check_matcher=tg.get("Matcher", {}).get("HttpCode"),
               runtime_family=tagv(tg_tags, "runtime.family"), runtime_image=tagv(tg_tags, "runtime.image"))
        for th in elbv2.describe_target_health(TargetGroupArn=tid)["TargetHealthDescriptions"]:
            target = th["Target"]["Id"]
            port = th["Target"].get("Port", tg["Port"])
            g.edge(tid, target, "routes_to", port=port)
            g.edge(tid, target, "health_checks", path=tg.get("HealthCheckPath"), port=port, state=th["TargetHealth"]["State"],
                   reason=th["TargetHealth"].get("Reason"))

    # ---- EC2 instances -----------------------------------------------------------------------------
    inst_ids = [a.rsplit("/", 1)[1] for a in by_service.get("ec2", []) if ":instance/" in a]
    if inst_ids:
        for res in ec2.describe_instances(InstanceIds=inst_ids)["Reservations"]:
            for i in res["Instances"]:
                iid = i["InstanceId"]
                tags = i.get("Tags", [])
                image = tagv(tags, "app.image")
                g.node(iid, "ec2_instance", tagv(tags, "Name", iid), f"arn:aws:ec2:{region}:{i.get('OwnerId', '')}:instance/{iid}",
                       instance_type=i["InstanceType"], state=i["State"]["Name"], private_ip=i.get("PrivateIpAddress"),
                       subnet=i.get("SubnetId"), security_groups=[s["GroupId"] for s in i.get("SecurityGroups", [])],
                       iam_instance_profile=(i.get("IamInstanceProfile") or {}).get("Arn"),
                       runtime_family=tagv(tags, "runtime.family"), runtime_image=tagv(tags, "runtime.image"),
                       app_port=tagv(tags, "app.port"), app_health=tagv(tags, "app.health"), app_secret_key=tagv(tags, "app.secret_key"))
                for s in i.get("SecurityGroups", []):
                    g.edge(s["GroupId"], iid, "allows_ingress", scope="attached")
                if image:
                    g.node(image, "container_image", image.rsplit("/", 1)[-1], image, registry="ecr" if ".dkr.ecr." in image else "external")
                    g.edge(iid, image, "runs_image")
                    repo = image.split("/", 1)[1].rsplit(":", 1)[0] if "/" in image else None
                    for a in by_service.get("ecr", []):
                        if repo and a.endswith(f":repository/{repo}"):
                            g.edge(image, a, "runs_image", relation="stored_in")
                # reads_secret via the instance role's inline policies
                prof = (i.get("IamInstanceProfile") or {}).get("Arn")
                if prof:
                    pname = prof.rsplit("/", 1)[1]
                    for role in iam.get_instance_profile(InstanceProfileName=pname)["InstanceProfile"]["Roles"]:
                        for pol in iam.list_role_policies(RoleName=role["RoleName"])["PolicyNames"]:
                            doc = iam.get_role_policy(RoleName=role["RoleName"], PolicyName=pol)["PolicyDocument"]
                            for st in doc.get("Statement", []):
                                acts = st["Action"] if isinstance(st["Action"], list) else [st["Action"]]
                                ress = st["Resource"] if isinstance(st["Resource"], list) else [st["Resource"]]
                                if any(a.startswith("secretsmanager:GetSecretValue") for a in acts):
                                    for r in ress:
                                        if r.startswith("arn:aws:secretsmanager"):
                                            g.edge(iid, r, "reads_secret", via=f"{role['RoleName']}/{pol}")

    # ---- ECS (services / tasks) ----------------------------------------------------------------------
    for a in by_service.get("ecs", []):
        if ":service/" in a:
            cluster, svc = a.split(":service/")[1].split("/", 1)
            for s in ecs.describe_services(cluster=cluster, services=[svc])["services"]:
                g.node(a, "ecs_service", s["serviceName"], a, cluster=cluster, launch_type=s.get("launchType"), desired=s.get("desiredCount"))
                td = ecs.describe_task_definition(taskDefinition=s["taskDefinition"])["taskDefinition"]
                for c in td["containerDefinitions"]:
                    g.node(c["image"], "container_image", c["image"].rsplit("/", 1)[-1], c["image"])
                    g.edge(a, c["image"], "runs_image", container=c["name"], ports=[p.get("containerPort") for p in c.get("portMappings", [])])
                    for sec in c.get("secrets", []):
                        g.edge(a, sec["valueFrom"], "reads_secret", env=sec["name"])
                for sg in ((s.get("networkConfiguration") or {}).get("awsvpcConfiguration") or {}).get("securityGroups", []):
                    g.edge(sg, a, "allows_ingress", scope="attached")
                for lbc in s.get("loadBalancers", []):
                    if lbc.get("targetGroupArn"):
                        g.edge(lbc["targetGroupArn"], a, "routes_to", port=lbc.get("containerPort"))

    # ---- security groups: rules -> allows_ingress edges ----------------------------------------------
    sg_ids = [a.rsplit("/", 1)[1] for a in by_service.get("ec2", []) if ":security-group/" in a]
    if sg_ids:
        for sg in ec2.describe_security_groups(GroupIds=sg_ids)["SecurityGroups"]:
            sid = sg["GroupId"]
            rules = []
            for p in sg["IpPermissions"]:
                fr, to = p.get("FromPort"), p.get("ToPort")
                port = "all" if p["IpProtocol"] == "-1" else (str(fr) if fr == to else f"{fr}-{to}")
                for r in p.get("IpRanges", []):
                    rules.append({"from": r["CidrIp"], "port": port, "proto": p["IpProtocol"], "desc": r.get("Description")})
                for r in p.get("UserIdGroupPairs", []):
                    rules.append({"from": r["GroupId"], "port": port, "proto": p["IpProtocol"], "desc": r.get("Description")})
                    g.edge(r["GroupId"], sid, "allows_ingress", port=port, protocol=p["IpProtocol"], description=r.get("Description"))
            g.node(sid, "security_group", tagv(sg.get("Tags"), "Name", sg["GroupName"]), f"arn:aws:ec2:{region}:{sg['OwnerId']}:security-group/{sid}",
                   group_name=sg["GroupName"], vpc=sg["VpcId"], ingress=rules, ingress_ports=sorted({r["port"] for r in rules}))
            for r in rules:
                if r["from"].endswith("/0"):
                    g.node("internet", "internet", "0.0.0.0/0", None)
                    g.edge("internet", sid, "allows_ingress", port=r["port"], protocol=r["proto"])

    # ---- Route53 -------------------------------------------------------------------------------------
    dns_names = {n["attrs"].get("dns_name", "").lower(): nid for nid, n in g.nodes.items() if n["type"] == "alb"}
    private_ips = {n["attrs"].get("private_ip"): nid for nid, n in g.nodes.items() if n["type"] == "ec2_instance"}
    for a in by_service.get("route53", []):
        if ":hostedzone/" not in a:
            continue
        zid = a.rsplit("/", 1)[1]
        z = r53.get_hosted_zone(Id=zid)["HostedZone"]
        g.node(a, "route53_zone", z["Name"].rstrip("."), a, private=z["Config"].get("PrivateZone"), vpcs=[v["VPCId"] for v in r53.get_hosted_zone(Id=zid).get("VPCs", [])])
        for rr in r53.list_resource_record_sets(HostedZoneId=zid)["ResourceRecordSets"]:
            if rr["Type"] not in ("A", "AAAA", "CNAME"):
                continue
            name = rr["Name"].rstrip(".")
            rid = f"dns:{name}"
            alias = rr.get("AliasTarget", {}).get("DNSName", "").rstrip(".").lower()
            values = [v["Value"] for v in rr.get("ResourceRecords", [])]
            g.node(rid, "dns_record", name, None, type=rr["Type"], zone=z["Name"].rstrip("."), alias_target=alias or None, values=values or None)
            g.edge(a, rid, "resolves_to", relation="contains")
            target = dns_names.get(alias.removeprefix("dualstack."))
            if target:
                g.edge(rid, target, "resolves_to")
            for v in values:
                if v in private_ips:
                    g.edge(rid, private_ips[v], "resolves_to")

    # ---- Secrets Manager -----------------------------------------------------------------------------
    for a in by_service.get("secretsmanager", []):
        d = sm.describe_secret(SecretId=a)
        attrs = dict(consumer_env=tagv(d.get("Tags"), "ConsumerEnv"), db_host=tagv(d.get("Tags"), "DbHost"), db_engine=tagv(d.get("Tags"), "DbEngine"),
                     last_changed=str(d.get("LastChangedDate", "")))
        g.node(a, "secret", d["Name"], a, key_name=d["Name"].rsplit("/", 1)[-1], **attrs)
        db_host, db_port, db_name = attrs.get("db_host"), 1433, None
        if read_secret_values:
            val = sm.get_secret_value(SecretId=a)["SecretString"]
            c = parse_conn(val)
            db_host, db_port, db_name = c["host"] or db_host, c["port"], c["database"]
        if db_host:
            did = f"db:{db_host}:{db_port}"
            g.node(did, "database", f"{db_host}:{db_port}", None, engine=attrs.get("db_engine"), host=db_host, port=db_port, database=db_name,
                   hosting="host-local container" if f"dns:{db_host}" in g.nodes and any(e for e in g.edges if e["source"] == f"dns:{db_host}" and e["type"] == "resolves_to" and e["target"] in private_ips.values()) else None)
            g.edge(a, did, "connects_to_db", via="connection string")
            if f"dns:{db_host}" in g.nodes:
                g.edge(did, f"dns:{db_host}", "resolves_to")
            for e in list(g.edges):
                if e["type"] == "reads_secret" and e["target"] == a:
                    g.edge(e["source"], did, "connects_to_db", port=db_port, via=g.nodes[a]["attrs"]["key_name"])

    # ---- RDS / ECR -----------------------------------------------------------------------------------
    for a in by_service.get("rds", []):
        if ":db:" in a:
            for db in rds.describe_db_instances(DBInstanceIdentifier=a.rsplit(":", 1)[1])["DBInstances"]:
                did = a
                g.node(did, "rds_instance", db["DBInstanceIdentifier"], a, engine=db["Engine"], engine_version=db["EngineVersion"],
                       endpoint=db.get("Endpoint", {}).get("Address"), port=db.get("Endpoint", {}).get("Port"), instance_class=db["DBInstanceClass"])
                for sg in db.get("VpcSecurityGroups", []):
                    g.edge(sg["VpcSecurityGroupId"], did, "allows_ingress", scope="attached")
    for a in by_service.get("ecr", []):
        name = a.split(":repository/")[1]
        r = ecr.describe_repositories(repositoryNames=[name])["repositories"][0]
        imgs = ecr.describe_images(repositoryName=name).get("imageDetails", [])
        g.node(a, "ecr_repository", name, a, uri=r["repositoryUri"], tags=sorted({t for i in imgs for t in i.get("imageTags", [])}),
               image_count=len(imgs))

    return g


# ---- rendering ----------------------------------------------------------------------------------------
SHAPES = {"internet": ("ellipse", "#eeeeee"), "alb": ("box3d", "#cfe2ff"), "listener": ("component", "#e2ecff"), "target_group": ("folder", "#d9f2e6"),
          "ec2_instance": ("box", "#fff3cd"), "ecs_service": ("box", "#fff3cd"), "security_group": ("hexagon", "#f8d7da"),
          "route53_zone": ("tab", "#e2d9f3"), "dns_record": ("note", "#efe8fb"), "secret": ("septagon", "#ffe5d0"),
          "database": ("cylinder", "#d1ecf1"), "rds_instance": ("cylinder", "#d1ecf1"), "container_image": ("cds", "#e9ecef"), "ecr_repository": ("folder", "#e9ecef")}
EDGE_STYLE = {"routes_to": ("solid", "#0d6efd"), "health_checks": ("dashed", "#198754"), "allows_ingress": ("dotted", "#dc3545"),
              "resolves_to": ("solid", "#6f42c1"), "reads_secret": ("dashed", "#fd7e14"), "connects_to_db": ("bold", "#0dcaf0"), "runs_image": ("solid", "#6c757d")}
KEY_ATTRS = {"alb": ["dns_name"], "target_group": ["port", "health_check_path", "runtime_image"], "listener": ["port"],
             "ec2_instance": ["instance_type", "app_port", "app_health", "app_secret_key"], "security_group": ["ingress_ports"],
             "dns_record": ["type"], "secret": ["key_name"], "database": ["engine", "port"], "container_image": ["registry"], "ecr_repository": ["tags"]}


def esc(s):
    return str(s).replace('"', '\\"')


def to_dot(g):
    lines = ["digraph deps {", '  rankdir=TB; ranksep=0.9; nodesep=0.5; splines=true; fontname="Helvetica"; node [fontname="Helvetica", fontsize=11, style="filled", margin="0.2,0.1"]; edge [fontname="Helvetica", fontsize=9];',
             '  label="SampleWebApp — live AWS application dependency graph (tools/discover)"; labelloc=t;']
    for n in g.nodes.values():
        shape, color = SHAPES.get(n["type"], ("box", "#ffffff"))
        attrs = [f"{k}={n['attrs'][k]}" for k in KEY_ATTRS.get(n["type"], []) if k in n["attrs"]]
        label = f"{n['type']}\\n{n['name']}" + ("\\n" + "\\n".join(esc(a) for a in attrs) if attrs else "")
        lines.append(f'  "{esc(n["id"])}" [label="{label}", shape={shape}, fillcolor="{color}"];')
    for e in g.edges:
        style, color = EDGE_STYLE[e["type"]]
        a = e.get("attrs", {})
        detail = " ".join(f"{k}={v}" for k, v in a.items() if k in ("port", "path", "state") and v is not None)
        lines.append(f'  "{esc(e["source"])}" -> "{esc(e["target"])}" [label="{e["type"]}{(" " + esc(detail)) if detail else ""}", style={style}, color="{color}"];')
    lines.append("}")
    return "\n".join(lines)


def to_mermaid(g):
    ids = {nid: f"n{i}" for i, nid in enumerate(g.nodes)}
    out = ["flowchart LR"]
    for nid, n in g.nodes.items():
        out.append(f'  {ids[nid]}["{n["type"]}<br/>{n["name"]}"]')
    for e in g.edges:
        out.append(f"  {ids[e['source']]} -->|{e['type']}| {ids[e['target']]}")
    return "\n".join(out)


def to_markdown(g, project, region, png_name, track=None):
    md = [f"# Live dependency graph — `Project={project}`{f' `Track={track}`' if track else ''} ({region})", "",
          f"Generated {datetime.now(timezone.utc).isoformat(timespec='seconds')} by `tools/discover/discover.py` from AWS APIs (no agents, read-only).", "",
          f"![dependency graph]({png_name})", "", "## Nodes", "", "| type | name | key attributes |", "|---|---|---|"]
    for n in sorted(g.nodes.values(), key=lambda n: (n["type"], n["name"])):
        attrs = ", ".join(f"`{k}={n['attrs'][k]}`" for k in KEY_ATTRS.get(n["type"], []) if k in n["attrs"])
        md.append(f"| {n['type']} | {n['name']} | {attrs} |")
    md += ["", "## Edges", "", "| source | edge | target | detail |", "|---|---|---|---|"]
    for e in g.edges:
        a = e.get("attrs", {})
        md.append(f"| {g.nodes[e['source']]['name']} | `{e['type']}` | {g.nodes[e['target']]['name']} | {', '.join(f'{k}={v}' for k, v in a.items() if v is not None)} |")
    md += ["", "## Mermaid", "", "```mermaid", to_mermaid(g), "```", ""]
    return "\n".join(md)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="modernization-demo")
    ap.add_argument("--track", default=None, help="also filter on Track=<java|dotnet> (the account may host several tracks)")
    ap.add_argument("--region", default="us-east-1")
    ap.add_argument("--out", default="demo")
    ap.add_argument("--read-secret-values", action="store_true", help="GetSecretValue to resolve DB host/port/name (never written to output)")
    args = ap.parse_args()

    g = discover(args.project, args.region, args.read_secret_values, args.track)
    doc = {"schema": "app-dependency-graph/v1", "project": args.project, "track": args.track, "region": args.region,
           "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
           "nodes": list(g.nodes.values()), "edges": g.edges,
           "summary": {"nodes": len(g.nodes), "edges": len(g.edges), "edge_types": {t: sum(1 for e in g.edges if e["type"] == t) for t in EDGE_TYPES}}}
    base = f"{args.out}/dependency-graph"
    json.dump(doc, open(f"{base}.json", "w"), indent=2, default=str)
    open(f"{base}.dot", "w").write(to_dot(g))
    try:
        subprocess.run(["dot", "-Tpng", f"{base}.dot", "-o", f"{base}.png"], check=True)
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"[discover] WARN: graphviz render failed ({e}); .dot written", file=sys.stderr)
    open(f"{base}.md", "w").write(to_markdown(g, args.project, args.region, "dependency-graph.png", args.track))
    print(f"[discover] wrote {base}.json/.md/.png — {doc['summary']}")


if __name__ == "__main__":
    main()
