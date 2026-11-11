import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import CapacityEditor from '../components/CapacityEditor';
import WaitlistTable from '../components/WaitlistTable';
import { ErrorBox, PageHeader, SeatMeter, Spinner, StatusBadge } from '../components/ui';
import { formatDateTime } from '../format';
import { useLoad } from '../hooks';

export default function AdminSession() {
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const session = useLoad(() => api.session(id), [id]);
  const s = session.data;
  if (session.loading && !s) return <Spinner />;
  if (session.error || !s) return <ErrorBox error={session.error ?? new Error('Session not found')} />;
  return (
    <div>
      <PageHeader title={`Session #${s.sessionId}`} subtitle={<Link className="text-brand-600 hover:underline" to={`/admin/centers/${s.centerCode}`}>Back to {s.centerName}</Link>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="card p-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-semibold text-brand-600">{s.examCode}</p>
              <h2 className="font-semibold">{s.examTitle}</h2>
              <p className="text-sm text-slate-500">{formatDateTime(s.startsAt)} - {s.roomName}</p>
            </div>
            <StatusBadge status={s.status} />
          </div>
          <div className="mt-6">
            <p className="label">Capacity</p>
            <CapacityEditor session={s} onChanged={(n) => session.setData(n)} />
          </div>
          <div className="mt-4"><SeatMeter available={s.seatsAvailable} capacity={s.capacity} booked={s.bookedSeats} /></div>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <div><dt className="text-slate-500">Booked</dt><dd className="font-semibold" data-testid="booked">{s.bookedSeats}</dd></div>
            <div><dt className="text-slate-500">Available</dt><dd className="font-semibold" data-testid="available">{s.seatsAvailable}</dd></div>
            <div><dt className="text-slate-500">Accommodation</dt><dd className="font-semibold">{s.accommodationUsed} / {s.accommodationCapacity}</dd></div>
            <div><dt className="text-slate-500">Waitlist</dt><dd className="font-semibold">{s.waitlistCount}</dd></div>
          </dl>
        </div>
        <div className="card p-6 lg:col-span-2">
          <h2 className="mb-2 font-semibold">Waitlist</h2>
          <WaitlistTable sessionId={s.sessionId} refreshKey={s.waitlistCount} />
        </div>
      </div>
    </div>
  );
}
