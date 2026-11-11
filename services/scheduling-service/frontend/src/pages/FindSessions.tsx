import { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, Session } from '../api';
import { Empty, ErrorBox, PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import { formatDate, formatTime, seatTone } from '../format';
import { plusDays, todayIso, useLoad } from '../hooks';

export default function FindSessions() {
  const [params, setParams] = useSearchParams();
  const centerCode = params.get('centerCode') ?? '';
  const examCode = params.get('examCode') ?? '';
  const date = params.get('date') ?? '';
  const centers = useLoad(() => api.centers(), []);
  const exams = useLoad(() => api.exams(), []);
  const sessions = useLoad<Session[] | null>(
    () => (centerCode ? api.sessions(centerCode, examCode || undefined, date || todayIso(), date ? plusDays(date, 1) : undefined) : Promise.resolve(null)),
    [centerCode, examCode, date],
  );

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
  };

  const byDay = useMemo(() => {
    const groups = new Map<string, Session[]>();
    (sessions.data ?? []).forEach((s) => {
      const k = s.startsAt.slice(0, 10);
      groups.set(k, [...(groups.get(k) ?? []), s]);
    });
    return [...groups.entries()];
  }, [sessions.data]);
  const center = centers.data?.find((c) => c.centerCode === centerCode);

  return (
    <div>
      <PageHeader title="Find an exam session" subtitle="Pick a test center, then filter by exam and date. Seat counts are live." />
      <div className="card mb-6 grid gap-4 p-4 sm:grid-cols-3">
        <label>
          <span className="label">Test center</span>
          <select className="input" value={centerCode} onChange={(e) => set('centerCode', e.target.value)} data-testid="center-select">
            <option value="">Choose a center...</option>
            {centers.data?.map((c) => <option key={c.centerCode} value={c.centerCode}>{c.centerCode} - {c.name} ({c.city})</option>)}
          </select>
        </label>
        <label>
          <span className="label">Exam</span>
          <select className="input" value={examCode} onChange={(e) => set('examCode', e.target.value)} data-testid="exam-select">
            <option value="">All exams</option>
            {exams.data?.map((x) => <option key={x.examCode} value={x.examCode}>{x.examCode} - {x.title}</option>)}
          </select>
        </label>
        <label>
          <span className="label">Date</span>
          <input type="date" className="input" value={date} onChange={(e) => set('date', e.target.value)} data-testid="date-input" />
        </label>
      </div>

      {!centerCode && <Empty>Choose a test center to see its upcoming sessions.</Empty>}
      {centerCode && sessions.loading && !sessions.data && <Spinner label="Loading sessions" />}
      {sessions.error && <ErrorBox error={sessions.error} />}
      {centerCode && sessions.data && sessions.data.length === 0 && <Empty>No sessions match these filters.</Empty>}

      <div className="space-y-8">
        {byDay.map(([day, list]) => (
          <section key={day}>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{formatDate(`${day}T00:00:00`)}</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {list.map((s) => <SessionCard key={s.sessionId} s={s} timeZone={center?.timeZone} />)}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

function SessionCard({ s, timeZone }: { s: Session; timeZone?: string }) {
  const tone = seatTone(s.seatsAvailable, s.capacity);
  const accLeft = Math.max(0, s.accommodationCapacity - s.accommodationUsed);
  const bookable = s.status === 'OPEN';
  return (
    <article className="card flex flex-col p-5 transition hover:-translate-y-0.5 hover:shadow-md" data-testid="session-card" data-session-id={s.sessionId}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-brand-600">{s.examCode}</p>
          <h3 className="font-semibold text-slate-900">{s.examTitle}</h3>
          <p className="mt-0.5 text-sm text-slate-500">{formatTime(s.startsAt)} - {s.roomName}{timeZone ? ` - ${timeZone}` : ''}</p>
        </div>
        <StatusBadge status={s.status} />
      </div>
      <div className="mt-4">
        <div className="mb-1.5 flex items-baseline justify-between text-sm">
          <span data-testid="seats-left" className={`font-semibold ${tone === 'full' ? 'text-rose-600' : tone === 'low' ? 'text-amber-600' : 'text-emerald-700'}`}>
            {s.seatsAvailable > 0 ? `${s.seatsAvailable} seats left` : 'Full - waitlist open'}
          </span>
          <span className="text-xs text-slate-500">{s.bookedSeats}/{s.capacity} booked</span>
        </div>
        <SeatMeter available={s.seatsAvailable} capacity={s.capacity} booked={s.bookedSeats} />
        <p className="mt-2 text-xs text-slate-500">
          {accLeft} of {s.accommodationCapacity} extra-time seats free
          {s.waitlistCount > 0 && <> - {s.waitlistCount} on waitlist</>}
        </p>
      </div>
      <div className="mt-5 flex justify-end">
        {bookable ? (
          <Link to={`/book?sessionId=${s.sessionId}`} className={s.seatsAvailable > 0 ? 'btn-primary' : 'btn-secondary'} data-testid="book-link">
            {s.seatsAvailable > 0 ? 'Book a seat' : 'Join waitlist'}
          </Link>
        ) : (
          <span className="text-xs text-slate-400">Not open for booking</span>
        )}
      </div>
    </article>
  );
}
