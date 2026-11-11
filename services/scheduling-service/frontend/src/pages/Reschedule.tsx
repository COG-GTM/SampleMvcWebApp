import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, api, fmtDateTime, type Booking, type Session } from '../api';
import { Empty, PageHeader, Spinner } from '../components/ui';
import { useToast } from '../components/Toast';

/** Candidate: move a booking to another session of the same exam (any centre). */
export default function Reschedule() {
  const toast = useToast();
  const navigate = useNavigate();
  const bookingId = Number(useParams().bookingId);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [center, setCenter] = useState('');
  const [centers, setCenters] = useState<{ centerCode: string; name: string }[]>([]);
  const [options, setOptions] = useState<Session[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.booking(bookingId).then((b) => { setBooking(b); setCenter(b.centerCode); })
      .catch((e) => toast('error', 'Booking not found', e.message));
    api.centers().then(setCenters).catch(() => {});
  }, [bookingId, toast]);

  useEffect(() => {
    if (!booking || !center) return;
    setOptions(null);
    api.sessions(center, booking.examCode)
      .then((s) => setOptions(s.filter((x) => x.sessionId !== booking.sessionId && x.status === 'OPEN')))
      .catch((e) => toast('error', 'Could not load sessions', e.message));
  }, [booking, center, toast]);

  async function confirm() {
    if (!selected) return;
    setBusy(true);
    try {
      const r = await api.reschedule(bookingId, selected);
      toast(r.status === 'BOOKED' ? 'success' : 'info', r.status === 'BOOKED' ? `Moved to session #${selected}` : `Moved – waitlisted at position ${r.waitlistPosition}`,
        `New booking #${r.bookingId}; booking #${bookingId} was cancelled.`);
      navigate('/my-bookings');
    } catch (e) {
      const ae = e as ApiError;
      toast('error', 'Reschedule failed', `${ae.message}${ae.legacyCode ? ` (code ${ae.legacyCode})` : ''}`);
    } finally {
      setBusy(false);
    }
  }

  if (!booking) return <Spinner />;
  return (
    <>
      <PageHeader title={`Reschedule booking #${booking.bookingId}`}
        subtitle={<>Currently {booking.examCode} on {fmtDateTime(booking.sessionStartsAt)} at {booking.centerName}. Only sessions of the same exam are offered. <Link to="/my-bookings" className="text-indigo-600">Back</Link></>} />
      <div className="card mb-4 p-4">
        <label className="label" htmlFor="rs-center">Test centre</label>
        <select id="rs-center" className="input max-w-md" value={center} onChange={(e) => { setCenter(e.target.value); setSelected(null); }}>
          {centers.map((c) => <option key={c.centerCode} value={c.centerCode}>{c.centerCode} – {c.name}</option>)}
        </select>
      </div>
      {!options ? <Spinner /> : options.length === 0 ? <Empty>No other open {booking.examCode} sessions at this centre.</Empty> : (
        <fieldset className="grid gap-3 md:grid-cols-2" data-testid="reschedule-options">
          {options.map((s) => (
            <label key={s.sessionId} className={`card flex cursor-pointer items-center justify-between gap-3 p-4 ${selected === s.sessionId ? 'ring-2 ring-indigo-500' : ''}`}>
              <span className="flex items-center gap-3">
                <input type="radio" name="target" checked={selected === s.sessionId} onChange={() => setSelected(s.sessionId)} data-testid={`target-${s.sessionId}`} />
                <span><span className="block font-medium">{fmtDateTime(s.startsAt)}</span><span className="text-sm text-slate-500">Session #{s.sessionId} · {s.roomName}</span></span>
              </span>
              <span className={`text-sm font-semibold ${s.seatsAvailable ? 'text-emerald-700' : 'text-amber-700'}`}>{s.seatsAvailable ? `${s.seatsAvailable} seats left` : 'Full – waitlist'}</span>
            </label>
          ))}
        </fieldset>
      )}
      <div className="mt-6 flex justify-end">
        <button className="btn-primary" disabled={!selected || busy} onClick={confirm} data-testid="confirm-reschedule">{busy ? 'Moving…' : 'Confirm reschedule'}</button>
      </div>
    </>
  );
}
