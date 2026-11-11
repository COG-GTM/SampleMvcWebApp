import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { api, ApiError, BookingResult, Eligibility } from '../api';
import { ErrorBox, PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import {
  ACCOMMODATION_OPTIONS, accommodationParam, accommodationSeatsLeft, effectiveAccommodation, formatDateTime, hasSeatFor,
  needsAccommodationSeat, validateCandidateNumber,
} from '../format';
import { rememberCandidate, rememberedCandidate, toastError, useLoad } from '../hooks';

export default function Book() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const sessionId = Number(params.get('sessionId'));
  const session = useLoad(() => api.session(sessionId), [sessionId]);
  const [candidate, setCandidate] = useState((params.get('candidateNumber') ?? '').toUpperCase() || rememberedCandidate());
  const [touched, setTouched] = useState(false);
  const explicitAcc = params.get('acc');
  const [accommodation, setAccommodation] = useState(() => effectiveAccommodation(explicitAcc, null));
  const prefilledAccommodation = useRef<string | null>(null);
  // An explicit choice (carried over from the search, or picked here) is never replaced by the profile default.
  const explicitChoice = useRef(!!explicitAcc);
  const [eligibility, setEligibility] = useState<Eligibility | null>(null);
  const [candidateName, setCandidateName] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<BookingResult | null>(null);
  const candidateError = validateCandidateNumber(candidate);
  const s = session.data;

  // Look the candidate up as they type: prefill their default accommodation and show retake eligibility.
  useEffect(() => {
    setEligibility(null);
    setCandidateName(null);
    const previous = prefilledAccommodation.current;
    prefilledAccommodation.current = null;
    if (previous && !explicitChoice.current) setAccommodation((a) => (a === previous ? '' : a));
    if (candidateError || !s) return;
    const n = candidate.trim().toUpperCase();
    let live = true;
    api.candidate(n).then((c) => {
      if (!live) return;
      setCandidateName(`${c.firstName} ${c.lastName}`);
      const preset = c.defaultAccommodationCode;
      if (preset && !explicitChoice.current) {
        setAccommodation((a) => {
          if (a) return a;
          prefilledAccommodation.current = preset;
          return preset;
        });
      }
      return api.eligibility(n, s.examCode, s.startsAt.slice(0, 10)).then((e) => live && setEligibility(e));
    }).catch(() => live && setCandidateName(''));
    return () => { live = false; };
  }, [candidate, candidateError, s]);

  if (!sessionId) return <ErrorBox error={new Error('No session selected.')} />;
  if (session.loading && !s) return <Spinner />;
  if (session.error || !s) return <ErrorBox error={session.error ?? new Error('Session not found')} />;

  const accLeft = accommodationSeatsLeft(s);
  const accError = !hasSeatFor(accommodation, s)
    ? `No accommodation seat left for ${accommodation} on this session - pick another session or a different accommodation` : null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (candidateError || accError) return;
    setSubmitting(true);
    try {
      const n = candidate.trim().toUpperCase();
      const r = await api.book(s.sessionId, n, accommodation || null);
      rememberCandidate(n);
      setResult(r);
      const usedAcc = r.status === 'BOOKED' && needsAccommodationSeat(r.accommodationCode);
      session.setData({ ...s, seatsAvailable: r.seatsAvailable, bookedSeats: r.status === 'BOOKED' ? s.bookedSeats + 1 : s.bookedSeats,
        accommodationUsed: usedAcc ? s.accommodationUsed + 1 : s.accommodationUsed,
        accommodationSeatsAvailable: usedAcc ? Math.max(0, accLeft - 1) : accLeft,
        waitlistCount: r.status === 'WAITLISTED' ? s.waitlistCount + 1 : s.waitlistCount });
      if (r.status === 'BOOKED') toast.success(`Seat booked - booking #${r.bookingId}`, { description: `${r.seatsAvailable} seats left on this session` });
      else toast.warning(`Session is full - you are #${r.waitlistPosition} on the waitlist`, { description: `Booking #${r.bookingId}` });
    } catch (err) {
      toastError(err);
      // Someone else may have taken the last (accommodation) seat: show the live counts again.
      if (err instanceof ApiError && err.status === 409) void session.reload();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-5">
      <div className="lg:col-span-3">
        <PageHeader title="Book a seat" subtitle={<Link to={`/?${backQuery(s.centerCode, candidate, accommodation, explicitChoice.current)}`} className="text-brand-600 hover:underline">Back to {s.centerName} sessions</Link>} />
        {result ? (
          <div className="card p-6" data-testid="booking-confirmation">
            <div className="flex items-center gap-3">
              <StatusBadge status={result.status} />
              <h2 className="text-lg font-semibold">Booking #{result.bookingId}</h2>
            </div>
            <p className="mt-2 text-sm text-slate-600">
              {result.status === 'BOOKED'
                ? `You're booked for ${s.examTitle} on ${formatDateTime(s.startsAt)}.`
                : `The session is full. You are number ${result.waitlistPosition} on the waitlist.`}
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <button className="btn-primary" onClick={() => navigate(`/my-bookings?candidateNumber=${result.candidateNumber}`)} data-testid="go-my-bookings">View my bookings</button>
              <button className="btn-secondary" onClick={() => setResult(null)}>Book another candidate</button>
            </div>
          </div>
        ) : (
          <form className="card space-y-5 p-6" onSubmit={submit} noValidate data-testid="booking-form">
            <label className="block">
              <span className="label">Candidate number</span>
              <input className={`input ${touched && candidateError ? 'input-error' : ''}`} placeholder="NW00000901" value={candidate}
                onChange={(e) => setCandidate(e.target.value)} onBlur={() => setTouched(true)} data-testid="candidate-input" />
              {touched && candidateError && <p className="field-error">{candidateError}</p>}
              {candidateName && <p className="mt-1 text-xs text-slate-500">Booking for <b>{candidateName}</b></p>}
              {candidateName === '' && !candidateError && <p className="field-error">Candidate not found</p>}
            </label>
            {eligibility && (
              <div className={`rounded-lg px-3 py-2 text-sm ${eligibility.eligible ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-700'}`} data-testid="eligibility">
                {eligibility.eligible ? 'Eligible to sit this exam' : 'Not eligible yet'} ({eligibility.reason.replaceAll('_', ' ').toLowerCase()})
                {!eligibility.eligible && eligibility.earliestRetakeDate && <> - earliest retake {eligibility.earliestRetakeDate}</>}
              </div>
            )}
            <label className="block">
              <span className="label">Accommodation (optional)</span>
              <select className={`input ${accError ? 'input-error' : ''}`} value={accommodation}
                onChange={(e) => { explicitChoice.current = true; setAccommodation(e.target.value); }} data-testid="accommodation-select">
                {ACCOMMODATION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
              {accError && <p className="field-error">{accError}</p>}
            </label>
            <div className="flex items-center justify-between gap-4 border-t border-slate-100 pt-5">
              <p className="text-xs text-slate-500">{s.seatsAvailable > 0 ? 'A seat is held immediately.' : 'This session is full; you will join the waitlist.'}</p>
              <button type="submit" className="btn-primary" disabled={submitting || (touched && !!candidateError) || !!accError} data-testid="book-submit">
                {submitting ? 'Booking...' : s.seatsAvailable > 0 ? 'Confirm booking' : 'Join waitlist'}
              </button>
            </div>
          </form>
        )}
      </div>
      <aside className="lg:col-span-2">
        <div className="card p-6 lg:sticky lg:top-6" data-testid="session-summary">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-semibold text-brand-600">{s.examCode} - session {s.sessionId}</p>
              <h2 className="text-lg font-semibold">{s.examTitle}</h2>
            </div>
            <StatusBadge status={s.status} />
          </div>
          <dl className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-slate-500">When</dt><dd>{formatDateTime(s.startsAt)}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Where</dt><dd>{s.centerName}, {s.roomName}</dd></div>
          </dl>
          <div className="mt-5">
            <div className="mb-1.5 flex items-baseline justify-between">
              <span className="text-3xl font-semibold" data-testid="summary-seats-left">{s.seatsAvailable}</span>
              <span className="text-xs text-slate-500">seats left of {s.capacity}</span>
            </div>
            <SeatMeter available={s.seatsAvailable} capacity={s.capacity} booked={s.bookedSeats} />
            <p className="mt-2 text-xs text-slate-500"><span data-testid="summary-accommodation-left">{accLeft} accommodation {accLeft === 1 ? 'seat' : 'seats'} left</span> of {s.accommodationCapacity} (ET25/ET50) - {s.waitlistCount} waiting</p>
          </div>
        </div>
      </aside>
    </div>
  );
}

function backQuery(centerCode: string, candidate: string, accommodation: string, explicit: boolean): string {
  const q = new URLSearchParams({ centerCode });
  const n = candidate.trim().toUpperCase();
  if (n && !validateCandidateNumber(n)) q.set('candidateNumber', n);
  if (explicit) q.set('acc', accommodationParam(accommodation));
  return q.toString();
}
