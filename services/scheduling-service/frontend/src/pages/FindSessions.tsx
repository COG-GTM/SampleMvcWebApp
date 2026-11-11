import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ACCOMMODATIONS, CANDIDATE_RE, api, fmtDateTime, noAccommodationSeatFor, type Center, type Exam, type Session } from '../api';
import { Empty, PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';
import { useCandidateNumber } from '../candidate';

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
  const [candidate, setCandidate] = useCandidateNumber();
  const [number, setNumber] = useState(candidate);
  const [profileDefault, setProfileDefault] = useState<string | null>(null);
  // ?acc= (even empty = "None") is an explicit choice; otherwise the candidate's profile default is pre-filled (ADR 0002 ODD-07)
  const accParam = params.get('acc');
  const acc = accParam ?? profileDefault ?? '';

  useEffect(() => {
    if (!CANDIDATE_RE.test(number)) {
      setProfileDefault(null);
      if (!number) setCandidate('');
      return;
    }
    setCandidate(number);
    let live = true;
    api.candidate(number)
      .then((c) => { if (live) setProfileDefault(c.defaultAccommodationCode); })
      .catch(() => { if (live) setProfileDefault(null); });
    return () => { live = false; };
  }, [number, setCandidate]);

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
  // a different candidate starts again from their own profile default
  const changeCandidate = (v: string) => {
    setNumber(v);
    if (params.has('acc')) {
      const p = new URLSearchParams(params);
      p.delete('acc');
      setParams(p, { replace: true });
    }
  };
  const setAcc = (v: string) => {
    const p = new URLSearchParams(params);
    p.set('acc', v);
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
      <div className="card mb-6 grid gap-4 p-4 sm:grid-cols-2" data-testid="candidate-context">
        <div>
          <label className="label" htmlFor="find-cand">Candidate number (optional)</label>
          <input id="find-cand" data-testid="find-candidate-input" className="input" value={number} placeholder="NW00000901"
            onChange={(e) => changeCandidate(e.target.value.trim().toUpperCase())} />
        </div>
        <div>
          <label className="label" htmlFor="find-acc">Accommodation</label>
          <select id="find-acc" data-testid="find-accommodation-select" className="input" value={acc} onChange={(e) => setAcc(e.target.value)}>
            {ACCOMMODATIONS.map((a) => <option key={a.code} value={a.code}>{a.label}</option>)}
          </select>
          {profileDefault && <p className="mt-1 text-xs text-slate-500" data-testid="find-accommodation-default">Profile default: {profileDefault}</p>}
        </div>
      </div>

      {!center ? <Empty>Pick a test centre to see its sessions.</Empty>
        : loading ? <Spinner />
        : sessions && sessions.length === 0 ? <Empty>No sessions match these filters at {centerObj?.name ?? center}.</Empty>
        : sessions && (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3" data-testid="session-list">
            {sessions.map((s) => {
              const noAccSeat = noAccommodationSeatFor(s, acc);
              const bookable = s.status === 'OPEN' && !noAccSeat;
              return (
              <article key={s.sessionId} className={`card flex flex-col gap-3 p-4 ${noAccSeat ? 'opacity-75' : ''}`} data-testid={`session-card-${s.sessionId}`}
                data-unavailable={noAccSeat ? 'true' : undefined}>
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
                  <div><dt className="text-slate-500">Accommodation seats left</dt><dd className="text-lg font-bold" data-testid="acc-left">{s.accommodationAvailable}<span className="text-sm font-normal text-slate-400"> / {s.accommodationCapacity}</span></dd></div>
                </dl>
                {s.waitlistCount > 0 && <p className="text-xs text-amber-700">{s.waitlistCount} on the waitlist</p>}
                {noAccSeat && <p className="rounded-lg bg-rose-50 p-2 text-xs text-rose-700" data-testid={`acc-unavailable-${s.sessionId}`}>
                  No accommodation seat left for {acc} – unavailable for you.</p>}
                <Link to={`/book?sessionId=${s.sessionId}&acc=${encodeURIComponent(acc)}`} data-testid={`book-${s.sessionId}`}
                  aria-disabled={!bookable} tabIndex={bookable ? undefined : -1}
                  className={`mt-auto ${bookable ? 'btn-primary' : 'btn-secondary pointer-events-none opacity-50'}`}>
                  {s.status !== 'OPEN' ? 'Not bookable' : noAccSeat ? 'Unavailable' : s.seatsAvailable > 0 ? 'Book a seat' : 'Join waitlist'}
                </Link>
              </article>
              );
            })}
          </div>
        )}
    </>
  );
}
