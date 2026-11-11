import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, Session } from '../api';
import CapacityEditor from '../components/CapacityEditor';
import WaitlistTable from '../components/WaitlistTable';
import { Drawer, Empty, ErrorBox, PageHeader, Spinner, StatusBadge } from '../components/ui';
import { formatDateTime } from '../format';
import { todayIso, useLoad } from '../hooks';

export default function AdminCenter() {
  const { centerCode = '' } = useParams();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const examCode = params.get('examCode') ?? '';
  const from = params.get('from') ?? todayIso();
  const centers = useLoad(() => api.centers(), []);
  const exams = useLoad(() => api.exams(), []);
  const sessions = useLoad(() => api.sessions(centerCode, examCode || undefined, from), [centerCode, examCode, from]);
  const [drawer, setDrawer] = useState<Session | null>(null);
  const center = centers.data?.find((c) => c.centerCode === centerCode);

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
  };
  const replace = (s: Session) => sessions.setData((list) => list?.map((x) => (x.sessionId === s.sessionId ? s : x)) ?? null);
  const list = sessions.data ?? [];
  const totals = list.reduce((t, s) => ({ cap: t.cap + s.capacity, booked: t.booked + s.bookedSeats, wait: t.wait + s.waitlistCount }), { cap: 0, booked: 0, wait: 0 });

  return (
    <div>
      <PageHeader title={center ? center.name : centerCode} subtitle={center ? `${center.centerCode} - ${center.city}, ${center.region} - ${center.timeZone}` : 'Test center'}
        actions={
          <select className="input w-64" value={centerCode} onChange={(e) => navigate(`/admin/centers/${e.target.value}`)} data-testid="admin-center-select">
            {centers.data?.map((c) => <option key={c.centerCode} value={c.centerCode}>{c.centerCode} - {c.name}</option>)}
          </select>
        } />
      <div className="mb-6 grid gap-4 sm:grid-cols-4">
        <Stat label="Sessions" value={list.length} />
        <Stat label="Seats" value={totals.cap} />
        <Stat label="Booked" value={totals.booked} />
        <Stat label="Waiting" value={totals.wait} />
      </div>
      <div className="card mb-4 flex flex-wrap gap-4 p-4">
        <label className="w-64"><span className="label">Exam</span>
          <select className="input" value={examCode} onChange={(e) => set('examCode', e.target.value)}>
            <option value="">All exams</option>
            {exams.data?.map((x) => <option key={x.examCode} value={x.examCode}>{x.examCode}</option>)}
          </select>
        </label>
        <label className="w-48"><span className="label">From</span>
          <input type="date" className="input" value={from} onChange={(e) => set('from', e.target.value)} />
        </label>
      </div>
      {sessions.loading && !sessions.data && <Spinner label="Loading sessions" />}
      {sessions.error && <ErrorBox error={sessions.error} />}
      {sessions.data && list.length === 0 && <Empty>No sessions from {from}.</Empty>}
      {list.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm" data-testid="session-grid">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Session</th><th className="px-4 py-3">Exam</th><th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Capacity</th><th className="px-4 py-3 text-right">Booked</th><th className="px-4 py-3 text-right">Available</th>
                <th className="px-4 py-3 text-right">Accommodation</th><th className="px-4 py-3 text-right">Waitlist</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {list.map((s) => (
                <tr key={s.sessionId} className="hover:bg-slate-50/60" data-testid="session-row" data-session-id={s.sessionId}>
                  <td className="px-4 py-3">
                    <Link to={`/admin/sessions/${s.sessionId}`} className="font-medium text-brand-600 hover:underline">#{s.sessionId}</Link>
                    <div className="text-xs text-slate-500">{formatDateTime(s.startsAt)} - {s.roomName}</div>
                  </td>
                  <td className="px-4 py-3"><span className="font-medium">{s.examCode}</span><div className="text-xs text-slate-500">{s.examTitle}</div></td>
                  <td className="px-4 py-3"><StatusBadge status={s.status} /></td>
                  <td className="px-4 py-3"><CapacityEditor session={s} onChanged={replace} compact /></td>
                  <td className="px-4 py-3 text-right tabular-nums" data-testid="booked">{s.bookedSeats}</td>
                  <td className={`px-4 py-3 text-right font-semibold tabular-nums ${s.seatsAvailable === 0 ? 'text-rose-600' : 'text-emerald-700'}`} data-testid="available">{s.seatsAvailable}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{s.accommodationUsed} / {s.accommodationCapacity}<div className="text-xs text-slate-500">{Math.max(0, s.accommodationCapacity - s.accommodationUsed)} free</div></td>
                  <td className="px-4 py-3 text-right">
                    <button className={`badge ${s.waitlistCount ? 'bg-amber-100 text-amber-800 hover:bg-amber-200' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                      onClick={() => setDrawer(s)} data-testid="waitlist-button">{s.waitlistCount} waiting</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer ? `Waitlist - session #${drawer.sessionId}` : 'Waitlist'}
        subtitle={drawer && `${drawer.examCode} - ${formatDateTime(drawer.startsAt)} - ${drawer.seatsAvailable} seats free`}>
        {drawer && <WaitlistTable sessionId={drawer.sessionId} refreshKey={drawer.waitlistCount} />}
      </Drawer>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="card p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}
