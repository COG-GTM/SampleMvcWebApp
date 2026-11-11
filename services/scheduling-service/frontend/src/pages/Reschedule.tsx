import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { api, Session } from '../api';
import { Empty, ErrorBox, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { formatDateTime } from '../format';
import { todayIso, toastError, useLoad } from '../hooks';

/** Reschedule = cancel + book, only to another session of the same exam (usp_RescheduleBooking). */
export default function Reschedule() {
  const { bookingId } = useParams();
  const id = Number(bookingId);
  const navigate = useNavigate();
  const booking = useLoad(() => api.booking(id), [id]);
  const current = useLoad(() => (booking.data ? api.session(booking.data.sessionId) : Promise.resolve(null)), [booking.data]);
  const centers = useLoad(() => api.centers(), []);
  const [centerCode, setCenterCode] = useState<string>('');
  const center = centerCode || current.data?.centerCode || '';
  const options = useLoad<Session[] | null>(
    () => (center && booking.data ? api.sessions(center, booking.data.examCode, todayIso()) : Promise.resolve(null)),
    [center, booking.data]);
  const [target, setTarget] = useState<Session | null>(null);
  const [busy, setBusy] = useState(false);

  if (booking.loading && !booking.data) return <Spinner />;
  if (booking.error || !booking.data) return <ErrorBox error={booking.error ?? new Error('Booking not found')} />;
  const b = booking.data;
  const movable = b.status === 'BOOKED' || b.status === 'WAITLISTED';
  const choices = (options.data ?? []).filter((s) => s.sessionId !== b.sessionId && s.status === 'OPEN');

  const confirm = async () => {
    if (!target) return;
    setBusy(true);
    try {
      const r = await api.reschedule(b.bookingId, target.sessionId);
      toast.success(`Rescheduled to session ${r.sessionId}`, {
        description: r.status === 'BOOKED' ? `New booking #${r.bookingId}` : `Session full: waitlist #${r.waitlistPosition} (booking #${r.bookingId})`,
      });
      navigate(`/my-bookings?candidateNumber=${b.candidateNumber}`);
    } catch (e) {
      toastError(e);
      setBusy(false);
    }
  };

  return (
    <div>
      <PageHeader title={`Reschedule booking #${b.bookingId}`}
        subtitle={<Link className="text-brand-600 hover:underline" to={`/my-bookings?candidateNumber=${b.candidateNumber}`}>Back to my bookings</Link>} />
      <div className="card mb-6 flex flex-wrap items-center gap-4 p-4">
        <div className="flex-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Current</p>
          <p className="font-semibold">{b.examCode} - {formatDateTime(b.sessionStartsAt)}</p>
          <p className="text-sm text-slate-500">{current.data ? `${current.data.centerName}, ${current.data.roomName}` : `session ${b.sessionId}`}</p>
        </div>
        <StatusBadge status={b.status} />
      </div>
      {!movable ? <Empty>This booking is {b.status.toLowerCase()} and can no longer be rescheduled.</Empty> : (
        <>
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <p className="text-sm text-slate-600">Pick another <b>{b.examCode}</b> session. Rescheduling is only possible within the same exam.</p>
            <label className="w-72">
              <span className="label">Test center</span>
              <select className="input" value={center} onChange={(e) => setCenterCode(e.target.value)} data-testid="reschedule-center">
                {centers.data?.map((c) => <option key={c.centerCode} value={c.centerCode}>{c.centerCode} - {c.name}</option>)}
              </select>
            </label>
          </div>
          {options.loading && !options.data && <Spinner label="Loading sessions" />}
          {options.data && choices.length === 0 && <Empty>No other open {b.examCode} sessions at this center.</Empty>}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {choices.map((s) => (
              <button key={s.sessionId} onClick={() => setTarget(s)} data-testid="reschedule-option" data-session-id={s.sessionId}
                className="card p-4 text-left transition hover:border-brand-500 hover:shadow-md">
                <p className="font-semibold">{formatDateTime(s.startsAt)}</p>
                <p className="text-sm text-slate-500">{s.roomName} - session {s.sessionId}</p>
                <p className={`mt-2 text-sm font-medium ${s.seatsAvailable > 0 ? 'text-emerald-700' : 'text-amber-600'}`}>
                  {s.seatsAvailable > 0 ? `${s.seatsAvailable} seats left` : 'Full - you would be waitlisted'}
                </p>
              </button>
            ))}
          </div>
        </>
      )}
      <Modal open={!!target} onClose={() => setTarget(null)} title="Confirm reschedule"
        footer={<>
          <button className="btn-secondary" onClick={() => setTarget(null)}>Back</button>
          <button className="btn-primary" onClick={confirm} disabled={busy} data-testid="reschedule-confirm">{busy ? 'Moving...' : 'Reschedule'}</button>
        </>}>
        {target && <p>Booking #{b.bookingId} will be cancelled and a new booking made for {formatDateTime(target.startsAt)} (session {target.sessionId}). If that fails, nothing changes.</p>}
      </Modal>
    </div>
  );
}
