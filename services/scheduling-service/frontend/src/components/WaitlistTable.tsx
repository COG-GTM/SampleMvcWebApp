import { fmtDateTime, type WaitlistEntry } from '../api';

export default function WaitlistTable({ entries }: { entries: WaitlistEntry[] }) {
  if (entries.length === 0) return <p className="text-sm text-slate-500">Nobody is waitlisted for this session.</p>;
  return (
    <table className="w-full text-left text-sm" data-testid="waitlist-table">
      <thead className="text-xs uppercase text-slate-500">
        <tr><th className="py-2 pr-2">#</th><th className="py-2 pr-2">Candidate</th><th className="py-2">Added</th></tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {entries.map((w) => (
          <tr key={w.bookingId} data-testid="waitlist-row">
            <td className="py-2 pr-2 font-semibold">{w.position}</td>
            <td className="py-2 pr-2">
              <span className="block font-medium">{w.candidateNumber}</span>
              <span className="text-xs text-slate-500">{w.candidateName}{w.accommodationCode ? ` · ${w.accommodationCode}` : ''} · booking #{w.bookingId}</span>
            </td>
            <td className="py-2 text-slate-600">{fmtDateTime(w.addedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
