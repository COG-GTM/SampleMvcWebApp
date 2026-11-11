import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, fmtDateTime, type Session, type WaitlistEntry } from '../api';
import CapacityEditor from '../components/CapacityEditor';
import WaitlistTable from '../components/WaitlistTable';
import { PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';

export default function AdminSession() {
  const sessionId = Number(useParams().sessionId);
  const [session, setSession] = useState<Session | null>(null);
  const [waitlist, setWaitlist] = useState<WaitlistEntry[] | null>(null);
  const load = useCallback(() => {
    api.session(sessionId).then(setSession).catch(() => {});
    api.waitlist(sessionId).then(setWaitlist).catch(() => {});
  }, [sessionId]);
  useEffect(load, [load]);
  if (!session) return <Spinner />;
  const stat = (k: string, v: React.ReactNode) => <div className="card p-3"><p className="text-xs text-slate-500">{k}</p><div className="text-xl font-bold">{v}</div></div>;
  return (
    <>
      <PageHeader title={`Session #${session.sessionId}`}
        subtitle={<><Link to={`/admin/centers/${session.centerCode}`} className="text-indigo-600">{session.centerName}</Link> · {session.examCode} – {session.examTitle} · {fmtDateTime(session.startsAt)}</>}
        actions={<StatusBadge status={session.status} />} />
      <div className="mb-4"><SeatMeter s={session} /></div>
      <div className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-5">
        {stat('Capacity', <CapacityEditor key={session.capacity} session={session} onChanged={load} />)}
        {stat('Booked', session.bookedSeats)}
        {stat('Available', session.seatsAvailable)}
        {stat('Accommodation used / avail.', `${session.accommodationUsed} / ${session.accommodationAvailable}`)}
        {stat('Waitlist', session.heldSeats ? `${session.waitlistCount} (${session.heldSeats} seat${session.heldSeats === 1 ? '' : 's'} offered)` : session.waitlistCount)}
      </div>
      <section className="card p-4">
        <h2 className="mb-3 font-semibold">Waitlist</h2>
        {waitlist ? <WaitlistTable entries={waitlist} /> : <Spinner />}
      </section>
    </>
  );
}
