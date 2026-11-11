import type { ReactNode } from 'react';
import type { Session } from '../api';

export function StatusBadge({ status }: { status: string }) {
  const c: Record<string, string> = {
    OPEN: 'bg-emerald-100 text-emerald-800', BOOKED: 'bg-emerald-100 text-emerald-800',
    WAITLISTED: 'bg-amber-100 text-amber-800', FULL: 'bg-amber-100 text-amber-800',
    CANCELLED: 'bg-slate-200 text-slate-600', CLOSED: 'bg-slate-200 text-slate-600',
    ATTENDED: 'bg-sky-100 text-sky-800', DELIVERED: 'bg-sky-100 text-sky-800',
  };
  return <span className={`badge ${c[status] ?? 'bg-slate-100 text-slate-700'}`}>{status}</span>;
}

export function SeatMeter({ s }: { s: Session }) {
  const pct = s.capacity === 0 ? 100 : Math.min(100, Math.round((s.bookedSeats / s.capacity) * 100));
  return (
    <div className="w-full">
      <div className="h-2 overflow-hidden rounded-full bg-slate-200">
        <div className={`h-full ${s.seatsAvailable === 0 ? 'bg-amber-500' : 'bg-indigo-500'}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="card p-8 text-center text-sm text-slate-500">{children}</div>;
}

export function Spinner() {
  return <div className="flex justify-center p-8"><div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" /></div>;
}

export function Drawer({ open, title, onClose, children }: { open: boolean; title: string; onClose: () => void; children: ReactNode }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex justify-end" role="dialog" aria-modal="true" aria-label={title}>
      <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
      <aside className="relative flex h-full w-full max-w-md flex-col bg-white shadow-xl" data-testid="drawer">
        <header className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </header>
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
      </aside>
    </div>
  );
}

export function Modal({ open, title, onClose, children }: { open: boolean; title: string; onClose: () => void; children: ReactNode }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
      <div className="card relative w-full max-w-md p-6 shadow-xl">
        <h2 className="mb-3 text-lg font-semibold">{title}</h2>
        {children}
      </div>
    </div>
  );
}
