"""Entry point: `python -m harness capture|replay ...` (wrapped by characterization/capture and characterization/replay)."""
import argparse
import fnmatch
import subprocess
import sys
import time
import traceback

from .compare import compare_transcripts
from .config import REPORT_DIR, REPO_DIR, SEED_AS_OF, Target
from .db import log
from .runner import Runner, load_catalogue, read_transcript, transcript_path, validate, write_transcript


def build_target(args):
    common = dict(sql_server=args.sql_server, database=args.database, reseed_cmd=args.reseed_cmd, seed_as_of=args.seed_as_of,
                  settle_url=args.settle_url)
    urls = dict(portal_url=args.portal_url, api_url=args.api_url, sync_url=args.sync_url)
    if args.target == "modern":
        if not args.base_url:
            sys.exit("--target modern needs --base-url")
        t = Target.modern(args.base_url, **urls, **common)
    else:
        t = Target.legacy(**urls, **common)
    if args.reseed:
        t.reseed = args.reseed
    if args.compare:
        t.compare = args.compare
    return t


def select(scenarios, patterns):
    if not patterns:
        return scenarios
    return [s for s in scenarios if any(fnmatch.fnmatch(s["id"], p) or fnmatch.fnmatch(s["_file"], p) for p in patterns)]


def git_sha():
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=str(REPO_DIR), text=True).strip()
    except Exception:
        return "nogit"


def main(argv=None):
    p = argparse.ArgumentParser(prog="characterization")
    p.add_argument("command", choices=["capture", "replay", "list"])
    p.add_argument("--target", choices=["legacy", "modern"], default="legacy")
    p.add_argument("--base-url", help="modern target: gateway URL serving the portal routes and /api/v1")
    p.add_argument("--portal-url")
    p.add_argument("--api-url")
    p.add_argument("--sync-url")
    p.add_argument("--sql-server")
    p.add_argument("--database")
    p.add_argument("--reseed", choices=["restore", "apply", "command", "none"],
                   help="restore (default for legacy): db/apply once, then RESTORE that seed before every scenario; "
                        "apply: run db/apply before every scenario; command: run --reseed-cmd before every scenario")
    p.add_argument("--reseed-cmd")
    p.add_argument("--compare", choices=["all", "http"], help="all (default) compares HTTP and DB snapshots; http skips DB")
    p.add_argument("--seed-as-of", default=SEED_AS_OF)
    p.add_argument("--settle-url", help="JSON endpoint polled until its 'pending' count is 0 after the reseed and every "
                                        "step, before DB snapshots (modern: http://localhost:8090/internal/relay)")
    p.add_argument("--report", help="report path (default reports/replay-<target>-<sha>.md)")
    p.add_argument("scenarios", nargs="*", help="scenario id or file globs (default: all)")
    args = p.parse_args(argv)

    catalogue = load_catalogue()
    bad = {s.get("id", s["_file"]): validate(s) for s in catalogue if validate(s)}
    if bad:
        for sid, problems in bad.items():
            print(f"{sid}: {problems}", file=sys.stderr)
        return 2
    chosen = select(catalogue, args.scenarios)
    if args.command == "list":
        for s in chosen:
            print(f"{s['id']:<48} {s['channel']:<8} {'ODDITY ' if s.get('oddity') else ''}{s['title']}")
        print(f"{len(chosen)} scenarios")
        return 0

    target = build_target(args)
    runner = Runner(target)
    runner.prepare()
    results = []
    for s in chosen:
        started = time.time()
        log(f"{args.command} {s['id']}")
        try:
            actual = runner.run(s)
            if args.command == "capture":
                write_transcript(transcript_path(s["id"]), actual)
                results.append((s, "CAPTURED", []))
            else:
                write_transcript(REPORT_DIR / "actual" / target.name / f"{s['id']}.json", actual)
                golden = read_transcript(s["id"])
                diffs = compare_transcripts(golden, actual, include_db=target.compare == "all")
                results.append((s, "PASS" if not diffs else "FAIL", diffs))
        except Exception as exc:
            results.append((s, "ERROR", [f"{type(exc).__name__}: {exc}"] + traceback.format_exc().splitlines()[-6:]))
        log(f"  -> {results[-1][1]} ({time.time() - started:.1f}s)")

    failed = [r for r in results if r[1] in ("FAIL", "ERROR")]
    if args.command == "replay":
        path = args.report or str(REPORT_DIR / f"replay-{target.name}-{git_sha()}.md")
        write_report(path, target, results)
        log(f"report: {path}")
    print(f"{args.command}: {len(results) - len(failed)}/{len(results)} ok")
    return 1 if failed else 0


def write_report(path, target, results):
    from pathlib import Path
    passed = sum(1 for r in results if r[1] == "PASS")
    lines = [f"# Characterization replay: target `{target.name}`", "",
             f"- commit: `{git_sha()}`", f"- portal: {target.portal_url}  api: {target.api_url}  sync: {target.sync_url}",
             f"- reseed: `{target.reseed}`  compare: `{target.compare}`  SEED_AS_OF: `{target.seed_as_of}`",
             f"- result: **{passed}/{len(results)} transcripts match**", "",
             "| Scenario | Channel | Oddity | Result |", "|---|---|---|---|"]
    for s, status, _ in results:
        odd = ", ".join(s.get("oddity_ids") or []) if s.get("oddity") else ""
        lines.append(f"| `{s['id']}` | {s['channel']} | {odd} | {status} |")
    for s, status, diffs in results:
        if diffs:
            lines += ["", f"## {s['id']} ({status})", "", "```"] + diffs[:50] + ["```"]
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    sys.exit(main())
