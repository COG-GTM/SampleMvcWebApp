import { api } from '../api';
import { formatDateTime } from '../format';
import { useLoad } from '../hooks';
import { Empty, ErrorBox, Spinner } from './ui';

export default function WaitlistTable({ sessionId, refreshKey = 0 }: { sessionId: number; refreshKey?: number }) {
  const { data, error, loading } = useLoad(() => api.waitlist(sessionId), [sessionId, refreshKey]);
  if (loading && !data) return <Spinner label="Loading waitlist" />;
  if (error) return <ErrorBox error={error} />;
  if (!data || data.length === 0) return <Empty>Nobody is waiting for this session.</Empty>;
  return (
    <ol className="divide-y divide-slate-100" data-testid="waitlist">
      {data.map((w) => (
        <li key={w.bookingId} className="flex items-center gap-3 py-3" data-testid="waitlist-row">
          <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-amber-100 text-sm font-semibold text-amber-800">
            {w.position}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-slate-900">{w.candidateName ?? 'Unknown candidate'}</p>
            <p className="text-xs text-slate-500">{w.candidateNumber} - booking #{w.bookingId}</p>
          </div>
          <span className="text-right text-xs text-slate-500">added<br />{formatDateTime(w.addedAt)}</span>
        </li>
      ))}
    </ol>
  );
}
