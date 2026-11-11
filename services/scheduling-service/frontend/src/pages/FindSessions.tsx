import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, fmtDateTime, type Center, type Exam, type Session } from '../api';
import { Empty, PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';

/** Candidate step 1: pick a centre, filter by exam and date, see live seats. */
export default function FindSessions() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const [centers, setCenters] = useState<Center[]>([]);
  const [exams, setExams] = useState<Exam[]>([]);
  const [sessions, setSessions] = useState<Session[] | null>(null);
  const [loading, setLoading] = useState(false);
  const center = params.get('center') ?? '';
  const examCode = params.get('exam') ?? '';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const dateError = from && to && to < from ? '"To" must be on or after "From"' : '';

  useEffect(() => {
    Promise.all([api.centers(), api.exams()])
      .then(([c, e]) => { setCenters(c); setExams(e); })
      .catch((e) => toast('error', 'Could not load centres', e.message));
  }, [toast]);

  useEffect(() => {
    if (!center || dateError) { setSessions(null); return; }
    setLoading(true);
    api.sessions(center, examCode || undefined, from || undefined, to || undefined)
      .then(setSessions)
      .catch((e) => toast('error', 'Could not load sessions', e.message))
      .finally(() => setLoading(false));
  }, [center, examCode, from, to, dateError, toast]);

  const set = (k: string, v: string) => {
    const p = new URLSearchParams(params);
    if (v) p.set(k, v); else p.delete(k);
    setParams(p, { replace: true });
  };
  const centerObj = useMemo(() => centers.find((c) => c.centerCode === center), [centers, center]);

  return (
    <>
      <PageHeader title="Find an exam session" subtitle="Choose a test centre, then filter by exam and date. Seat counts are live." />
      <div className="card mb-6 grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <label className="label" htmlFor="center">Test centre</label>
          <select id="center" data-testid="center-select" className="input" value={center} onChange={(e) => set('center', e.target.value)}>
            <option value="">Select a centre…</option>
            {centers.map((c) => <option key={c.centerCode} value={c.centerCode}>{c.centerCode} – {c.name} ({c.city})</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="exam">Exam</label>
          <select id="exam" data-testid="exam-select" className="input" value={examCode} onChange={(e) => set('exam', e.target.value)}>
            <option value="">All exams</option>
            {exams.map((x) => <option key={x.examCode} value={x.examCode}>{x.examCode} – {x.title}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="from">From</label>
          <input id="from" data-testid="from-date" type="date" className="input" value={from} onChange={(e) => set('from', e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="to">To</label>
          <input id="to" data-testid="to-date" type="date" className={`input ${dateError ? 'input-error' : ''}`} value={to}
            onChange={(e) => set('to', e.target.value)} aria-invalid={!!dateError} />
          {dateError && <p className="mt-1 text-xs text-rose-600">{dateError}</p>}
        </div>
      </div>

      {!center ? <Empty>Pick a test centre to see its sessions.</Empty>
        : loading ? <Spinner />
        : sessions && sessions.length === 0 ? <Empty>No sessions match these filters at {centerObj?.name ?? center}.</Empty>
        : sessions && (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3" data-testid="session-list">
            {sessions.map((s) => (
              <article key={s.sessionId} className="card flex flex-col gap-3 p-4" data-testid={`session-card-${s.sessionId}`}>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-xs font-semibold text-indigo-600">{s.examCode} · Session #{s.sessionId}</p>
                    <h3 className="font-semibold leading-tight">{s.examTitle}</h3>
                  </div>
                  <StatusBadge status={s.status} />
                </div>
                <p className="text-sm text-slate-600">{fmtDateTime(s.startsAt)} · {s.roomName}</p>
                <SeatMeter s={s} />
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <div><dt className="text-slate-500">Seats left</dt><dd className="text-lg font-bold" data-testid="seats-left">{s.seatsAvailable}<span className="text-sm font-normal text-slate-400"> / {s.capacity}</span></dd></div>
                  <div><dt className="text-slate-500">Extra-time seats</dt><dd className="text-lg font-bold" data-testid="acc-left">{s.accommodationAvailable}<span className="text-sm font-normal text-slate-400"> / {s.accommodationCapacity}</span></dd></div>
                </dl>
                {s.waitlistCount > 0 && <p className="text-xs text-amber-700">{s.waitlistCount} on the waitlist</p>}
                <Link to={`/book?sessionId=${s.sessionId}`} data-testid={`book-${s.sessionId}`}
                  className={`mt-auto ${s.status === 'OPEN' ? 'btn-primary' : 'btn-secondary pointer-events-none opacity-50'}`}>
                  {s.status !== 'OPEN' ? 'Not bookable' : s.seatsAvailable > 0 ? 'Book a seat' : 'Join waitlist'}
                </Link>
              </article>
            ))}
          </div>
        )}
    </>
  );
}
