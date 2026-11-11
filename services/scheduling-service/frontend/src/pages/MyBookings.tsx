import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError, CANDIDATE_RE, api, fmtDateTime, type Booking } from '../api';
import { Empty, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';
import { useCandidateNumber } from '../candidate';

/** Candidate: list bookings, cancel (with confirmation), reschedule. */
export default function MyBookings() {
  const toast = useToast();
  const [candidate, setCandidate] = useCandidateNumber();
  const [params] = useSearchParams();
  // legacy /Booking/MyBookings?candidateNumber= bookmarks arrive here via the routing gateway (MBA-3824)
  const linked = (params.get('candidateNumber') ?? '').trim().toUpperCase();
  const [input, setInput] = useState(CANDIDATE_RE.test(linked) ? linked : candidate);
  const [error, setError] = useState('');
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [toCancel, setToCancel] = useState<Booking | null>(null);

  const load = useCallback((n: string) => {
    if (!n) return;
    setLoading(true);
    api.bookings(n).then(setBookings)
      .catch((e: ApiError) => { setBookings(null); toast('error', 'Could not load bookings', e.message); })
      .finally(() => setLoading(false));
  }, [toast]);

  useEffect(() => { if (CANDIDATE_RE.test(linked)) setCandidate(linked); }, [linked, setCandidate]);
  useEffect(() => { load(candidate); }, [candidate, load]);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!CANDIDATE_RE.test(input)) { setError('Format is NW followed by 8 digits'); return; }
    setError('');
    setCandidate(input);
  }

  async function cancel(b: Booking) {
    try {
      await api.cancel(b.bookingId);
      toast('success', `Booking #${b.bookingId} cancelled`, 'The seat has been released.');
      load(candidate);
    } catch (e) {
      toast('error', 'Cancellation failed', (e as ApiError).message);
    } finally {
      setToCancel(null);
    }
  }

  const active = (b: Booking) => b.status === 'BOOKED' || b.status === 'WAITLISTED';
  return (
    <>
      <PageHeader title="My bookings" subtitle="Bookings for your candidate number, newest first." />
      <form onSubmit={submit} className="card mb-6 flex flex-col gap-3 p-4 sm:flex-row sm:items-end" noValidate>
        <div className="flex-1">
          <label className="label" htmlFor="mb-cand">Candidate number</label>
          <input id="mb-cand" data-testid="mybookings-candidate" className={`input ${error ? 'input-error' : ''}`} value={input}
            placeholder="NW00000901" onChange={(e) => setInput(e.target.value.trim().toUpperCase())} />
          {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
        </div>
        <button className="btn-primary" type="submit">Show bookings</button>
      </form>
      {!candidate ? <Empty>Enter your candidate number to see your bookings.</Empty>
        : loading && !bookings ? <Spinner />
        : bookings && bookings.length === 0 ? <Empty>No bookings yet. <Link className="text-indigo-600" to="/">Find a session</Link></Empty>
        : bookings && (
          <ul className="space-y-3" data-testid="booking-list">
            {bookings.map((b) => (
              <li key={b.bookingId} className="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between" data-testid={`booking-${b.bookingId}`}>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{b.examCode}</span>
                    <StatusBadge status={b.status} />
                    {b.waitlistPosition != null && b.status === 'WAITLISTED' && <span className="badge bg-amber-50 text-amber-700">Position {b.waitlistPosition}</span>}
                    {b.accommodationCode && <span className="badge bg-violet-100 text-violet-800">{b.accommodationCode}</span>}
                  </div>
                  <p className="text-sm text-slate-600">{b.examTitle}</p>
                  <p className="text-sm text-slate-500">{fmtDateTime(b.sessionStartsAt)} · {b.centerName} · Session #{b.sessionId} · Booking #{b.bookingId}</p>
                  {b.cancelReason && <p className="text-xs text-slate-400">{b.cancelReason}</p>}
                </div>
                {active(b) && (
                  <div className="flex gap-2">
                    <Link to={`/reschedule/${b.bookingId}`} className="btn-secondary" data-testid={`reschedule-${b.bookingId}`}>Reschedule</Link>
                    <button className="btn-danger" onClick={() => setToCancel(b)} data-testid={`cancel-${b.bookingId}`}>Cancel</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      <Modal open={!!toCancel} title="Cancel this booking?" onClose={() => setToCancel(null)}>
        {toCancel && (
          <>
            <p className="text-sm text-slate-600">Booking #{toCancel.bookingId} for {toCancel.examCode} on {fmtDateTime(toCancel.sessionStartsAt)} will be cancelled and the seat released. Waitlisted candidates are not promoted automatically.</p>
            <div className="mt-5 flex justify-end gap-2">
              <button className="btn-secondary" onClick={() => setToCancel(null)}>Keep booking</button>
              <button className="btn-danger" onClick={() => cancel(toCancel)} data-testid="confirm-cancel">Yes, cancel</button>
            </div>
          </>
        )}
      </Modal>
    </>
  );
}
