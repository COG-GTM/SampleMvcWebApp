import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ACCOMMODATIONS, ApiError, CANDIDATE_RE, api, fmtDateTime, type Session } from '../api';
import { PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';
import { useCandidateNumber } from '../candidate';

/** Candidate step 2: confirm a session, optional accommodation code, book. */
export default function Book() {
  const toast = useToast();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const sessionId = Number(params.get('sessionId'));
  const [session, setSession] = useState<Session | null>(null);
  const [candidate, setCandidate] = useCandidateNumber();
  const [number, setNumber] = useState(candidate);
  const [acc, setAcc] = useState('');
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const numberError = !number ? 'Enter your candidate number' : !CANDIDATE_RE.test(number) ? 'Format is NW followed by 8 digits, e.g. NW00000901' : '';
  const accError = session && (acc === 'ET25' || acc === 'ET50') && session.accommodationAvailable === 0
    ? 'No extra-time seats left on this session' : '';

  useEffect(() => {
    if (!sessionId) return;
    api.session(sessionId).then(setSession).catch((e) => toast('error', 'Session not found', e.message));
  }, [sessionId, toast]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (numberError || accError || !session) return;
    setBusy(true);
    try {
      const r = await api.book(session.sessionId, number, acc);
      setCandidate(number);
      setSession(r.session);
      if (r.status === 'BOOKED') toast('success', `Booked – booking #${r.bookingId}`, `Seats left on session #${r.session.sessionId}: ${r.session.seatsAvailable}`);
      else toast('info', `Session full – you are #${r.waitlistPosition} on the waitlist`, `Booking #${r.bookingId}`);
      navigate('/my-bookings');
    } catch (err) {
      const ae = err as ApiError;
      toast('error', 'Booking failed', `${ae.message}${ae.legacyCode ? ` (code ${ae.legacyCode})` : ''}`);
    } finally {
      setBusy(false);
    }
  }

  if (!sessionId) return <p>No session selected. <Link className="text-indigo-600" to="/">Find a session</Link></p>;
  if (!session) return <Spinner />;
  return (
    <>
      <PageHeader title="Book a seat" subtitle={<Link to={`/?center=${session.centerCode}&exam=${session.examCode}`} className="text-indigo-600">← Back to sessions</Link>} />
      <div className="grid gap-6 lg:grid-cols-5">
        <section className="card p-5 lg:col-span-2" data-testid="book-session">
          <div className="mb-2 flex items-center justify-between"><p className="text-xs font-semibold text-indigo-600">{session.examCode} · Session #{session.sessionId}</p><StatusBadge status={session.status} /></div>
          <h2 className="text-lg font-semibold">{session.examTitle}</h2>
          <p className="mt-1 text-sm text-slate-600">{session.centerName} · {session.roomName}</p>
          <p className="text-sm text-slate-600">{fmtDateTime(session.startsAt)}</p>
          <div className="my-4"><SeatMeter s={session} /></div>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div><dt className="text-slate-500">Seats left</dt><dd className="text-2xl font-bold" data-testid="seats-left">{session.seatsAvailable}</dd></div>
            <div><dt className="text-slate-500">Extra-time seats left</dt><dd className="text-2xl font-bold">{session.accommodationAvailable}</dd></div>
          </dl>
          {session.seatsAvailable === 0 && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">This session is full – booking now puts you on the waitlist.</p>}
        </section>
        <form className="card space-y-4 p-5 lg:col-span-3" onSubmit={submit} noValidate>
          <div>
            <label className="label" htmlFor="cand">Candidate number</label>
            <input id="cand" data-testid="candidate-input" className={`input ${touched && numberError ? 'input-error' : ''}`} value={number}
              placeholder="NW00000901" onChange={(e) => setNumber(e.target.value.trim().toUpperCase())} onBlur={() => setTouched(true)} />
            {touched && numberError && <p className="mt-1 text-xs text-rose-600" data-testid="candidate-error">{numberError}</p>}
          </div>
          <div>
            <label className="label" htmlFor="acc">Accommodation (optional)</label>
            <select id="acc" data-testid="accommodation-select" className={`input ${accError ? 'input-error' : ''}`} value={acc} onChange={(e) => setAcc(e.target.value)}>
              {ACCOMMODATIONS.map((a) => <option key={a.code} value={a.code}>{a.label}</option>)}
            </select>
            {accError && <p className="mt-1 text-xs text-rose-600">{accError}</p>}
          </div>
          <button type="submit" className="btn-primary w-full sm:w-auto" disabled={busy || session.status !== 'OPEN'} data-testid="confirm-book">
            {busy ? 'Booking…' : session.seatsAvailable > 0 ? 'Confirm booking' : 'Join waitlist'}
          </button>
        </form>
      </div>
    </>
  );
}
