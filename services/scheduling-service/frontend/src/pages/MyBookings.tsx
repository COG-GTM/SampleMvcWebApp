import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { api, Booking } from '../api';
import { Empty, ErrorBox, Modal, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { formatDateTime, validateCandidateNumber } from '../format';
import { rememberCandidate, rememberedCandidate, toastError, useLoad } from '../hooks';

export default function MyBookings() {
  const [params, setParams] = useSearchParams();
  const candidateNumber = (params.get('candidateNumber') ?? '').toUpperCase();
  const [input, setInput] = useState(candidateNumber || rememberedCandidate());
  const [touched, setTouched] = useState(false);
  const [cancelling, setCancelling] = useState<Booking | null>(null);
  const [busy, setBusy] = useState(false);
  const inputError = validateCandidateNumber(input);
  const bookings = useLoad<Booking[] | null>(
    () => (candidateNumber ? api.candidateBookings(candidateNumber) : Promise.resolve(null)), [candidateNumber]);

  const search = (e: React.FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (inputError) return;
    const n = input.trim().toUpperCase();
    rememberCandidate(n);
    setParams({ candidateNumber: n });
  };

  const cancel = async () => {
    if (!cancelling) return;
    setBusy(true);
    try {
      await api.cancel(cancelling.bookingId, 'Cancelled by candidate');
      toast.success(`Booking #${cancelling.bookingId} cancelled`, { description: 'The seat has been released.' });
      setCancelling(null);
      await bookings.reload();
    } catch (e) {
      toastError(e);
    } finally {
      setBusy(false);
    }
  };

  const active = (bookings.data ?? []).filter((b) => b.status === 'BOOKED' || b.status === 'WAITLISTED');
  const past = (bookings.data ?? []).filter((b) => b.status !== 'BOOKED' && b.status !== 'WAITLISTED');

  return (
    <div>
      <PageHeader title="My bookings" subtitle="See, cancel or reschedule your exam bookings." />
      <form className="card mb-6 flex flex-wrap items-start gap-3 p-4" onSubmit={search} noValidate>
        <label className="min-w-[16rem] flex-1">
          <span className="label">Candidate number</span>
          <input className={`input ${touched && inputError ? 'input-error' : ''}`} value={input} placeholder="NW00000901"
            onChange={(e) => setInput(e.target.value)} data-testid="mybookings-candidate" />
          {touched && inputError && <p className="field-error">{inputError}</p>}
        </label>
        <button className="btn-primary mt-5" type="submit" data-testid="mybookings-search">Show bookings</button>
      </form>

      {!candidateNumber && <Empty>Enter your candidate number to see your bookings.</Empty>}
      {candidateNumber && bookings.loading && !bookings.data && <Spinner label="Loading bookings" />}
      {bookings.error && <ErrorBox error={bookings.error} />}
      {bookings.data && (
        <div className="space-y-8">
          <section>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Upcoming ({active.length})</h2>
            {active.length === 0 ? <Empty>No active bookings. <Link className="text-brand-600 hover:underline" to="/">Find a session</Link></Empty> : (
              <div className="grid gap-3">
                {active.map((b) => <BookingRow key={b.bookingId} b={b} onCancel={() => setCancelling(b)} />)}
              </div>
            )}
          </section>
          {past.length > 0 && (
            <section>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">History ({past.length})</h2>
              <div className="grid gap-3">{past.map((b) => <BookingRow key={b.bookingId} b={b} />)}</div>
            </section>
          )}
        </div>
      )}

      <Modal open={!!cancelling} onClose={() => setCancelling(null)} title="Cancel this booking?"
        footer={<>
          <button className="btn-secondary" onClick={() => setCancelling(null)}>Keep it</button>
          <button className="btn-danger" onClick={cancel} disabled={busy} data-testid="cancel-confirm">{busy ? 'Cancelling...' : 'Cancel booking'}</button>
        </>}>
        {cancelling && <p>Booking #{cancelling.bookingId} for {cancelling.examCode} on {formatDateTime(cancelling.sessionStartsAt)} will be cancelled and the seat released.</p>}
      </Modal>
    </div>
  );
}

function BookingRow({ b, onCancel }: { b: Booking; onCancel?: () => void }) {
  return (
    <article className="card flex flex-wrap items-center gap-4 p-4" data-testid="booking-row" data-booking-id={b.bookingId} data-status={b.status}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold">{b.examCode}</span>
          <StatusBadge status={b.status} />
          {b.waitlistPosition && <span className="badge bg-amber-50 text-amber-700">waitlist #{b.waitlistPosition}</span>}
          {b.accommodationCode && <span className="badge bg-indigo-50 text-indigo-700">{b.accommodationCode}</span>}
        </div>
        <p className="mt-1 text-sm text-slate-500">
          {formatDateTime(b.sessionStartsAt)} - session {b.sessionId} - booking #{b.bookingId}
          {b.origin === 'LEGACY_IMPORT' && ' - from legacy system'}
        </p>
        {b.cancelReason && <p className="text-xs text-slate-400">{b.cancelReason} {b.cancelledAt && `on ${formatDateTime(b.cancelledAt)}`}</p>}
      </div>
      {onCancel && (
        <div className="flex gap-2">
          <Link to={`/reschedule/${b.bookingId}`} className="btn-secondary" data-testid="reschedule-link">Reschedule</Link>
          <button className="btn-secondary text-rose-600 hover:bg-rose-50" onClick={onCancel} data-testid="cancel-button">Cancel</button>
        </div>
      )}
    </article>
  );
}
