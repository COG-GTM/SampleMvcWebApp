import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, fmtDateTime, type Center, type Exam, type Session, type WaitlistEntry } from '../api';
import CapacityEditor from '../components/CapacityEditor';
import WaitlistTable from '../components/WaitlistTable';
import { Drawer, Empty, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { useToast } from '../components/Toast';

/** Admin: session grid for one centre with inline capacity edit and a waitlist drawer. */
export default function AdminCenter() {
  const toast = useToast();
  const centerCode = useParams().centerCode!;
  const [center, setCenter] = useState<Center | null>(null);
  const [exams, setExams] = useState<Exam[]>([]);
  const [examCode, setExamCode] = useState('');
  const [sessions, setSessions] = useState<Session[] | null>(null);
  const [drawer, setDrawer] = useState<{ session: Session; entries: WaitlistEntry[] | null } | null>(null);

  const load = useCallback(() => {
    api.sessions(centerCode, examCode || undefined).then(setSessions).catch((e) => toast('error', 'Could not load sessions', e.message));
  }, [centerCode, examCode, toast]);

  useEffect(() => {
    api.centers().then((cs) => setCenter(cs.find((c) => c.centerCode === centerCode) ?? null)).catch(() => {});
    api.exams().then(setExams).catch(() => {});
  }, [centerCode]);
  useEffect(load, [load]);

  async function openWaitlist(s: Session) {
    setDrawer({ session: s, entries: null });
    try {
      setDrawer({ session: s, entries: await api.waitlist(s.sessionId) });
    } catch (e) {
      toast('error', 'Could not load waitlist', (e as Error).message);
    }
  }

  const replace = (s: Session) => setSessions((all) => all?.map((x) => (x.sessionId === s.sessionId ? s : x)) ?? null);
  const totals = sessions?.reduce((a, s) => ({ cap: a.cap + s.capacity, booked: a.booked + s.bookedSeats, wl: a.wl + s.waitlistCount }), { cap: 0, booked: 0, wl: 0 });

  return (
    <>
      <PageHeader title={center ? `${center.name}` : centerCode}
        subtitle={<><Link to="/admin" className="text-indigo-600">All centres</Link> · {centerCode}{center ? ` · ${center.city}` : ''}</>}
        actions={
          <select className="input sm:w-72" value={examCode} onChange={(e) => setExamCode(e.target.value)} aria-label="Filter by exam" data-testid="admin-exam-filter">
            <option value="">All exams</option>
            {exams.map((x) => <option key={x.examCode} value={x.examCode}>{x.examCode} – {x.title}</option>)}
          </select>
        } />
      {totals && (
        <div className="mb-4 grid grid-cols-3 gap-3">
          {[['Sessions', sessions!.length], ['Seats booked', `${totals.booked} / ${totals.cap}`], ['Waitlisted', totals.wl]].map(([k, v]) => (
            <div key={k} className="card p-3"><p className="text-xs text-slate-500">{k}</p><p className="text-xl font-bold">{v}</p></div>
          ))}
        </div>
      )}
      {!sessions ? <Spinner /> : sessions.length === 0 ? <Empty>No sessions.</Empty> : (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[860px] text-left text-sm" data-testid="admin-session-grid">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Session</th><th className="px-3 py-2">Starts</th><th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Capacity</th><th className="px-3 py-2">Booked</th><th className="px-3 py-2">Available</th>
                <th className="px-3 py-2">Accommodation used / avail.</th><th className="px-3 py-2">Waitlist</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.map((s) => (
                <tr key={s.sessionId} className="hover:bg-slate-50" data-testid={`admin-row-${s.sessionId}`}>
                  <td className="px-3 py-2"><Link className="font-semibold text-indigo-600" to={`/admin/sessions/${s.sessionId}`}>#{s.sessionId}</Link><span className="block text-xs text-slate-500">{s.examCode} · {s.roomName}</span></td>
                  <td className="px-3 py-2 whitespace-nowrap">{fmtDateTime(s.startsAt)}</td>
                  <td className="px-3 py-2"><StatusBadge status={s.status} /></td>
                  <td className="px-3 py-2"><CapacityEditor key={s.capacity} session={s} onChanged={(r) => replace(r.session)} /></td>
                  <td className="px-3 py-2" data-testid={`booked-${s.sessionId}`}>{s.bookedSeats}</td>
                  <td className={`px-3 py-2 font-semibold ${s.seatsAvailable === 0 ? 'text-amber-700' : 'text-emerald-700'}`} data-testid={`available-${s.sessionId}`}>{s.seatsAvailable}</td>
                  <td className="px-3 py-2">{s.accommodationUsed} / {s.accommodationAvailable} <span className="text-xs text-slate-400">(of {s.accommodationCapacity})</span></td>
                  <td className="px-3 py-2">
                    <button className={`badge ${s.waitlistCount ? 'bg-amber-100 text-amber-800 hover:bg-amber-200' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                      onClick={() => openWaitlist(s)} data-testid={`waitlist-${s.sessionId}`}>{s.waitlistCount} waiting</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Drawer open={!!drawer} title={drawer ? `Waitlist · session #${drawer.session.sessionId}` : ''} onClose={() => setDrawer(null)}>
        {drawer && (
          <>
            <p className="mb-4 text-sm text-slate-500">{drawer.session.examCode} · {fmtDateTime(drawer.session.startsAt)} · {drawer.session.bookedSeats}/{drawer.session.capacity} booked</p>
            {drawer.entries ? <WaitlistTable entries={drawer.entries} /> : <Spinner />}
          </>
        )}
      </Drawer>
    </>
  );
}
