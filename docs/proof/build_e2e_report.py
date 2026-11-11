#!/usr/bin/env python3
"""Builds docs/proof/e2e-side-by-side.html: one self-contained file (inline CSS/JS, base64 images).

Usage: python3 docs/proof/build_e2e_report.py <data-dir> [out.html]
<data-dir>/report-data.json holds the assembled worker results; screenshot paths in it are relative to <data-dir>.
"""
import base64
import html
import io
import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    Image = None

CLASSES = ["MATCH", "APPROVED DIFFERENCE", "KNOWN DEFECT", "MISMATCH"]
CLASS_CSS = {"MATCH": "match", "APPROVED DIFFERENCE": "adr", "KNOWN DEFECT": "known", "MISMATCH": "mismatch"}
E = html.escape


def img_data_uri(path: Path, max_w: int = 1100) -> str:
    raw = path.read_bytes()
    if Image is None:
        return "data:image/png;base64," + base64.b64encode(raw).decode()
    im = Image.open(io.BytesIO(raw)).convert("RGB")
    if im.width > max_w:
        im = im.resize((max_w, int(im.height * max_w / im.width)), Image.LANCZOS)
    if im.height > 2400:
        im = im.crop((0, 0, im.width, 2400))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=72, optimize=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def badge(cls: str) -> str:
    return f'<span class="badge {CLASS_CSS.get(cls, "mismatch")}">{E(cls)}</span>'


def bar_svg(items, width=520, bar_h=26, max_val=None):
    """items: [(label, value, css_class)] -> horizontal bar chart."""
    max_val = max_val or max(v for _, v, _ in items) or 1
    h = len(items) * (bar_h + 10) + 6
    out = [f'<svg viewBox="0 0 {width} {h}" class="chart" role="img">']
    for i, (label, val, css) in enumerate(items):
        y = 4 + i * (bar_h + 10)
        w = max(2, (width - 230) * val / max_val) if val else 2
        out.append(f'<text x="0" y="{y + bar_h * 0.68}" class="lbl">{E(label)}</text>')
        out.append(f'<rect x="170" y="{y}" width="{w:.1f}" height="{bar_h}" rx="4" class="{css}"/>')
        out.append(f'<text x="{176 + w:.1f}" y="{y + bar_h * 0.68}" class="val">{val}</text>')
    out.append("</svg>")
    return "".join(out)


def donut_svg(parts, size=170, label=""):
    """parts: [(value, css_class)]"""
    total = sum(v for v, _ in parts) or 1
    r, c = 62, 85
    circ = 2 * 3.14159265 * r
    off = 0.0
    out = [f'<svg viewBox="0 0 {size} {size}" class="donut" role="img">',
           f'<circle cx="{c}" cy="{c}" r="{r}" class="ring"/>']
    for v, css in parts:
        if not v:
            continue
        seg = circ * v / total
        out.append(f'<circle cx="{c}" cy="{c}" r="{r}" class="seg {css}" stroke-dasharray="{seg:.2f} {circ - seg:.2f}" '
                   f'stroke-dashoffset="{-off:.2f}" transform="rotate(-90 {c} {c})"/>')
        off += seg
    out.append(f'<text x="{c}" y="{c + 4}" class="dlabel">{E(label)}</text></svg>')
    return "".join(out)


def kv_table(d):
    if not d:
        return '<p class="muted">-</p>'
    if isinstance(d, str):
        return f"<pre>{E(d)}</pre>"
    return "<pre>" + E(json.dumps(d, indent=1, ensure_ascii=False)) + "</pre>"


CSS = """
:root{--bg:#0f1720;--panel:#16212d;--panel2:#1c2a39;--ink:#e6edf3;--muted:#8aa0b4;--line:#2a3b4d;
--match:#2ea043;--adr:#3b82f6;--known:#d29922;--mismatch:#f85149;--legacy:#a371f7;--modern:#2dd4bf}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.5 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif}
header{padding:28px 36px;background:linear-gradient(120deg,#13293d,#1b3a4b 55%,#16302b);border-bottom:1px solid var(--line)}
header h1{margin:0 0 4px;font-size:26px}header .sub{color:var(--muted)}
main{padding:24px 36px;max-width:1500px;margin:auto}section{margin-bottom:34px}h2{font-size:19px;border-bottom:1px solid var(--line);padding-bottom:6px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:14px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px}
.card .big{font-size:34px;font-weight:700}.card .cap{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.05em}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:1000px){.grid2{grid-template-columns:1fr}}
table{border-collapse:collapse;width:100%;background:var(--panel);border-radius:8px;overflow:hidden}
th,td{padding:7px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}th{background:var(--panel2);color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase}
.badge{display:inline-block;padding:2px 9px;border-radius:999px;font-size:11px;font-weight:700;letter-spacing:.03em;color:#fff;white-space:nowrap}
.badge.match{background:var(--match)}.badge.adr{background:var(--adr)}.badge.known{background:var(--known);color:#111}.badge.mismatch{background:var(--mismatch)}
rect.match,.seg.match{fill:var(--match);stroke:var(--match)}rect.adr,.seg.adr{fill:var(--adr);stroke:var(--adr)}rect.known,.seg.known{fill:var(--known);stroke:var(--known)}
rect.mismatch,.seg.mismatch{fill:var(--mismatch);stroke:var(--mismatch)}rect.na,.seg.na{fill:#6e7681;stroke:#6e7681}rect.legacy{fill:var(--legacy)}rect.modern{fill:var(--modern)}
.seg{fill:none!important;stroke-width:20}.ring{fill:none;stroke:var(--panel2);stroke-width:20}.dlabel{fill:var(--ink);font-size:20px;font-weight:700;text-anchor:middle}
.chart{width:100%;max-width:560px}
.donut{width:170px;height:170px;flex:0 0 170px}
.grid2>*{min-width:0}.chart .lbl{fill:var(--muted);font-size:13px}.chart .val{fill:var(--ink);font-size:13px;font-weight:700}
.legend span{margin-right:14px;font-size:12px;color:var(--muted)}.legend i{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:5px;vertical-align:middle}
.scn{background:var(--panel);border:1px solid var(--line);border-radius:12px;margin-bottom:22px;overflow:hidden}
.scn>.hd{display:flex;gap:12px;align-items:center;padding:12px 16px;background:var(--panel2);cursor:pointer}.scn>.hd h3{margin:0;font-size:16px;flex:1}
.scn .cite{color:var(--muted);font-size:12px}.scn .bd{padding:14px 16px}.scn.collapsed .bd{display:none}
.side{border-radius:8px;padding:10px;background:#111a24}.side h4{margin:0 0 8px;font-size:13px}.side.legacy h4{color:var(--legacy)}.side.modern h4{color:var(--modern)}
.shots{display:flex;gap:8px;overflow-x:auto;padding-bottom:6px}.shot{flex:0 0 auto;width:300px}.shot img{width:300px;border:1px solid var(--line);border-radius:6px;cursor:zoom-in;display:block}
.shot .cap{font-size:11px;color:var(--muted);margin-top:3px}
pre{background:#0b121a;border:1px solid var(--line);border-radius:6px;padding:8px;font-size:11.5px;max-height:260px;overflow:auto;white-space:pre-wrap;margin:6px 0}
.muted{color:var(--muted)}a{color:#58a6ff}.diff td.l{color:#d2a8ff}.diff td.m{color:#5eead4}
#lb{position:fixed;inset:0;background:rgba(0,0,0,.88);display:none;align-items:center;justify-content:center;z-index:9;cursor:zoom-out}#lb img{max-width:95vw;max-height:94vh}
.toolbar{margin:8px 0 14px}.toolbar button{background:var(--panel2);color:var(--ink);border:1px solid var(--line);border-radius:6px;padding:5px 10px;margin-right:6px;cursor:pointer}
.toolbar button.on{border-color:#58a6ff}
footer{color:var(--muted);padding:20px 36px;border-top:1px solid var(--line)}
"""

JS = """
document.querySelectorAll('.shot img').forEach(function(i){i.onclick=function(){var lb=document.getElementById('lb');lb.querySelector('img').src=i.src;lb.style.display='flex';};});
document.getElementById('lb').onclick=function(){this.style.display='none';};
document.querySelectorAll('.scn>.hd').forEach(function(h){h.onclick=function(){h.parentNode.classList.toggle('collapsed');};});
document.querySelectorAll('.toolbar button').forEach(function(b){b.onclick=function(){
 document.querySelectorAll('.toolbar button').forEach(function(x){x.classList.remove('on');});b.classList.add('on');var f=b.dataset.f;
 document.querySelectorAll('.scn').forEach(function(s){s.style.display=(f==='ALL'||s.dataset.cls===f)?'':'none';});};});
"""


def side_html(name, css, side, base: Path):
    shots = []
    for s in side.get("shots", []):
        p = (base / s["file"]).resolve()
        if p.is_relative_to(base.resolve()) and p.is_file():
            shots.append(f'<div class="shot"><img alt="{E(s.get("label", ""))}" src="{img_data_uri(p)}"/>'
                         f'<div class="cap">{E(s.get("label", ""))}</div></div>')
    notes = f'<p>{E(side["notes"])}</p>' if side.get("notes") else ""
    return (f'<div class="side {css}"><h4>{E(name)}</h4>{notes}<div class="shots">{"".join(shots) or "<span class=muted>no screenshot</span>"}</div>'
            f'<details><summary class="muted">API / DB state after the last step</summary>{kv_table(side.get("state"))}</details></div>')


def scenario_html(s, base: Path):
    cls = s["classification"]
    rows = "".join(f'<tr><td>{E(str(d.get("field", "")))}</td><td class="l">{E(str(d.get("legacy", "")))}</td>'
                   f'<td class="m">{E(str(d.get("modern", "")))}</td><td>{E(str(d.get("note", "")))}</td></tr>' for d in s.get("diff", []))
    diff = (f'<table class="diff"><tr><th>DB / API field</th><th>Legacy</th><th>Modern</th><th>Note</th></tr>{rows}</table>'
            if rows else "")
    inputs = f'<p class="muted">Inputs: {E(s["inputs"])}</p>' if s.get("inputs") else ""
    return (f'<div class="scn" data-cls="{E(cls)}" id="s{int(s["id"])}"><div class="hd"><h3>{int(s["id"])}. {E(s["title"])}</h3>'
            f'<span class="cite">{E(s.get("citation", ""))}</span>{badge(cls)}</div><div class="bd">'
            f'<p>{E(s.get("summary", ""))}</p>{inputs}<div class="grid2">'
            f'{side_html("LEGACY - .NET 4.8 portal + Java 8 services (tag legacy-baseline)", "legacy", s.get("legacy", {}), base)}'
            f'{side_html("MODERN - Java 21 scheduling-service via routing gateway :8000", "modern", s.get("modern", {}), base)}'
            f'</div><h4 style="margin:14px 0 6px">DB-state diff</h4>{diff or "<p class=muted>identical business state</p>"}</div></div>')


def build(data: dict, base: Path) -> str:
    m, sc = data["meta"], data["scorecard"]
    scen = data["scenarios"]
    counts = {c: sum(1 for s in scen if s["classification"] == c) for c in CLASSES}
    passed = counts["MATCH"] + counts["APPROVED DIFFERENCE"] + counts["KNOWN DEFECT"]
    rp = sc["replay"]["modern"]
    lg = sc["replay"]["legacy"]
    dr = sc["drift"]
    cc = sc["concurrency"]
    tests = sc["tests"]
    t_pass = sum(t.get("passed", 0) for t in tests)
    t_fail = sum(t.get("failed", 0) for t in tests)

    cards = f"""<div class="cards">
<div class="card"><div class="cap">Side-by-side scenarios</div><div class="big">{passed}/{len(scen)}</div>
<div>{counts['MATCH']} match &middot; {counts['APPROVED DIFFERENCE']} approved diff &middot; {counts['KNOWN DEFECT']} known defect &middot; <b style="color:var(--mismatch)">{counts['MISMATCH']} mismatch</b></div></div>
<div class="card"><div class="cap">Transcript replay - legacy</div><div class="big">{lg['match']}/{lg['total']}</div><div>{E(lg.get('note', ''))}</div></div>
<div class="card"><div class="cap">Transcript replay - modern via :8000</div><div class="big">{rp['match']}/{rp['total']}</div>
<div>+{rp['adr']} ADR-approved, {rp['known']} known defect, {rp['not_replayable']} retired, <b style="color:var(--mismatch)">{rp['mismatch']} mismatch</b></div></div>
<div class="card"><div class="cap">Seat drift after mixed traffic</div><div class="big">{dr['before']} &rarr; {dr['after']}</div><div>reconciliation mismatches: <b>{dr['recon']}</b></div></div>
<div class="card"><div class="cap">Last-seat race ({cc['parallel']} parallel)</div><div class="big">{cc['legacy']['success']} vs {cc['modern']['success']}</div>
<div>bookings accepted for the last seat: legacy (worst trial) vs modern (every trial)</div></div>
<div class="card"><div class="cap">Automated tests</div><div class="big">{t_pass}</div><div>passed across {len(tests)} suites, <b style="color:{'var(--mismatch)' if t_fail else 'var(--match)'}">{t_fail} failed</b></div></div>
</div>"""

    replay_chart = bar_svg([("MATCH", rp["match"], "match"), ("ADR-approved", rp["adr"], "adr"),
                            ("Known defect (#57)", rp["known"], "known"), ("Mismatch", rp["mismatch"], "mismatch"),
                            ("Retired / not replayable", rp["not_replayable"], "na"),
                            ("Skipped / error", rp.get("skipped", 0) + rp.get("error", 0), "na")], max_val=rp["total"])
    donut = donut_svg([(rp["match"], "match"), (rp["adr"], "adr"), (rp["known"], "known"), (rp["mismatch"], "mismatch"),
                       (rp["not_replayable"] + rp.get("skipped", 0) + rp.get("error", 0), "na")], label=f"{rp['total']}")
    groups = "".join(f"<tr><td>{E(g['group'])}</td><td>{g['match']}/{g['total']}</td><td>{g.get('adr', 0)}</td><td>{g.get('known', 0)}</td>"
                     f"<td>{g.get('mismatch', 0)}</td><td>{g.get('not_replayable', 0)}</td></tr>" for g in sc["replay"].get("groups", []))
    drift_chart = bar_svg([("Legacy: seat-drift rows", dr["before"], "legacy"), ("Modern: seat-drift rows", dr["after"], "modern"),
                           ("Modern: reconcile mismatch", dr["recon"], "modern")], max_val=max(dr["before"], 1))
    drift_runs = "".join(f"<tr><td>{E(r['where'])}</td><td>{r['before']}</td><td>{r['after']}</td><td>{r['recon']}</td><td>{E(r.get('note', ''))}</td></tr>"
                         for r in dr.get("runs", []))
    conc_rows = "".join(f"<tr><td>{E(side.upper())}</td><td>{E(str(v.get('target', '')))}</td><td>{v['success']}</td><td>{E(str(v.get('final_seats', '')))}</td>"
                        f"<td>{E(str(v.get('booked_vs_capacity', '')))}</td><td>{E(str(v.get('waitlist', '')))}</td><td>{E(str(v.get('note', '')))}</td></tr>"
                        for side, v in (("legacy", cc["legacy"]), ("modern", cc["modern"])))
    conc_chart = bar_svg([("Legacy accepted", cc["legacy"]["success"], "legacy"), ("Modern accepted", cc["modern"]["success"], "modern")],
                         max_val=max(cc["legacy"]["success"], cc["modern"]["success"], 1))
    test_rows = "".join(f"<tr><td>{E(t['suite'])}</td><td>{E(t.get('platform', ''))}</td><td>{t.get('passed', 0)}</td>"
                        f"<td>{t.get('failed', 0)}</td><td>{t.get('skipped', 0)}</td><td>{E(t.get('note', ''))}</td></tr>" for t in tests)
    matrix = "".join(f'<tr><td><a href="#s{int(s["id"])}">{int(s["id"])}. {E(s["title"])}</a></td><td>{badge(s["classification"])}</td>'
                     f'<td>{E(s.get("citation", ""))}</td><td>{E(s.get("summary", ""))}</td></tr>' for s in scen)
    env_rows = "".join(f"<tr><td>{E(e['worker'])}</td><td>{E(e.get('role', ''))}</td><td>{E(e.get('env', ''))}</td>"
                       f"<td>{'<a href=' + chr(34) + E(e['url']) + chr(34) + '>session</a>' if str(e.get('url', '')).startswith('https://') else ''}</td></tr>" for e in m["workers"])
    prs = "".join(f'<li><a href="https://github.com/COG-GTM/SampleMvcWebApp/pull/{p["n"]}">#{p["n"]}</a> {E(p["title"])}</li>' for p in m["prs"])
    mism = "".join(f"<li>{E(x)}</li>" for x in data.get("mismatches", [])) or (
        "<li>" + E(f"{counts['MISMATCH']} scenario(s) classified MISMATCH - see the matrix above.") + "</li>" if counts["MISMATCH"] else "") or "<li>None. Every difference is either ADR-approved, a retired legacy surface or the documented on-call demo defect.</li>"
    filt = "".join(f'<button data-f="{c}">{c} ({counts[c]})</button>' for c in CLASSES)
    legend = ('<div class="legend"><span><i style="background:var(--match)"></i>MATCH</span><span><i style="background:var(--adr)"></i>APPROVED DIFFERENCE</span>'
              '<span><i style="background:var(--known)"></i>KNOWN DEFECT</span><span><i style="background:var(--mismatch)"></i>MISMATCH</span>'
              '<span><i style="background:#6e7681"></i>retired / not replayable</span></div>')

    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Northwind Assessment Network - legacy vs modern side-by-side E2E verification</title><style>{CSS}</style></head><body>
<header><h1>Northwind Assessment Network &middot; legacy vs modern side-by-side E2E verification</h1>
<div class="sub">Test-center scheduling slice &middot; legacy <code>{E(m['legacy_ref'])}</code> ({E(m['legacy_sha'][:7])}) vs modern <code>{E(m['modern_ref'])}</code> ({E(m['modern_sha'][:7])}) through the routing gateway :8000 &middot; generated {E(m['generated'])}</div></header>
<main>
<section><h2>Headline scorecard</h2>{cards}</section>
<section class="grid2"><div><h2>Transcript replay (modern via :8000, {rp['total']} transcripts)</h2>{legend}<div style="display:flex;gap:18px;align-items:center">{donut}<div style="flex:1">{replay_chart}</div></div>
<table><tr><th>Group</th><th>Match</th><th>ADR</th><th>Known</th><th>Mismatch</th><th>Retired</th></tr>{groups}</table></div>
<div><h2>Seat drift after mixed traffic</h2>{drift_chart}<table><tr><th>Run</th><th>Before (legacy)</th><th>After (modern)</th><th>Reconcile</th><th>Note</th></tr>{drift_runs}</table>
<h2 style="margin-top:24px">Last-seat concurrency</h2>{conc_chart}<table><tr><th>Side</th><th>Target</th><th>Accepted</th><th>Final SeatsAvailable</th><th>Booked / capacity</th><th>Waitlist</th><th>Note</th></tr>{conc_rows}</table></div></section>
<section><h2>Test suites</h2><table><tr><th>Suite</th><th>Where</th><th>Passed</th><th>Failed</th><th>Skipped</th><th>Note</th></tr>{test_rows}</table></section>
<section><h2>Scenario matrix</h2><table><tr><th>Scenario</th><th>Classification</th><th>Citation</th><th>Result</th></tr>{matrix}</table></section>
<section><h2>Side by side, per scenario</h2><div class="toolbar"><button data-f="ALL" class="on">All ({len(scen)})</button>{filt}</div>
{''.join(scenario_html(s, base) for s in scen)}</section>
<section><h2>Mismatches found</h2><ul>{mism}</ul></section>
<section><h2>Environment, SHAs and sessions</h2><p>Legacy: tag <code>{E(m['legacy_ref'])}</code> = <code>{E(m['legacy_sha'])}</code>. Modern: <code>{E(m['modern_ref'])}</code> = <code>{E(m['modern_sha'])}</code>.</p>
<table><tr><th>Worker</th><th>Role</th><th>Environment</th><th>Session</th></tr>{env_rows}</table>
{'<p>Video: ' + E(m['video']) + '</p>' if m.get('video') else ''}<h3>Pull requests</h3><ul>{prs}</ul>
<h3>Classification rules</h3><p class="muted">MATCH = same business fields and same Booking / Waitlist / ExamSession state. APPROVED DIFFERENCE = a legacy defect the modern side fixes on purpose (ADR 0002 section and/or docs/as-is HB id), or a retired legacy surface. KNOWN DEFECT = the documented on-call demo defect (#57, capacity reductions answered 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR). MISMATCH = an unexplained difference that would be a real finding.</p></section>
</main><footer>Northwind Assessment Network &middot; generated by docs/proof/build_e2e_report.py from worker evidence; every image is embedded, no external requests.</footer>
<div id="lb"><img alt=""/></div><script>{JS}</script></body></html>"""


def main():
    base = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).with_name("e2e-side-by-side.html")
    data = json.loads((base / "report-data.json").read_text(encoding="utf-8"))
    out.write_text(build(data, base), encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
