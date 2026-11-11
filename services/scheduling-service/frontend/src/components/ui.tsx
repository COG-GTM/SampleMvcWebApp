import { ReactNode, useEffect } from 'react';
import { seatTone } from '../format';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  const tone: Record<string, string> = {
    BOOKED: 'bg-emerald-100 text-emerald-800',
    ATTENDED: 'bg-sky-100 text-sky-800',
    WAITLISTED: 'bg-amber-100 text-amber-800',
    CANCELLED: 'bg-slate-200 text-slate-600',
    NO_SHOW: 'bg-rose-100 text-rose-700',
    OPEN: 'bg-emerald-100 text-emerald-800',
    CLOSED: 'bg-slate-200 text-slate-600',
    COMPLETED: 'bg-sky-100 text-sky-800',
  };
  return <span className={`badge ${tone[status] ?? 'bg-slate-100 text-slate-700'}`}>{status.replace('_', ' ')}</span>;
}

export function SeatMeter({ available, capacity, booked }: { available: number; capacity: number; booked: number }) {
  const pct = capacity > 0 ? Math.min(100, Math.round((booked / capacity) * 100)) : 100;
  const tone = seatTone(available, capacity);
  const bar = tone === 'full' ? 'bg-rose-500' : tone === 'low' ? 'bg-amber-500' : 'bg-emerald-500';
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden>
      <div className={`h-full ${bar} transition-all`} style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-10 text-sm text-slate-500" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
      {label}...
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="card px-6 py-12 text-center text-sm text-slate-500">{children}</div>;
}

export function ErrorBox({ error }: { error: Error }) {
  return <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error.message}</div>;
}

export function Modal({ open, onClose, title, children, footer }: {
  open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    if (open) window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 grid place-items-center bg-slate-900/40 p-4 backdrop-blur-sm" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label={title} className="card w-full max-w-md p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-semibold">{title}</h2>
        <div className="mt-3 text-sm text-slate-600">{children}</div>
        {footer && <div className="mt-6 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}

export function Drawer({ open, onClose, title, subtitle, children }: {
  open: boolean; onClose: () => void; title: string; subtitle?: ReactNode; children: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    if (open) window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  return (
    <div className={`fixed inset-0 z-30 ${open ? '' : 'pointer-events-none'}`}>
      <div className={`absolute inset-0 bg-slate-900/30 transition-opacity ${open ? 'opacity-100' : 'opacity-0'}`} onClick={onClose} />
      <aside role="dialog" aria-label={title}
        className={`absolute right-0 top-0 flex h-full w-full max-w-md flex-col bg-white shadow-2xl transition-transform duration-300 ${open ? 'translate-x-0' : 'translate-x-full'}`}>
        <div className="flex items-start justify-between border-b border-slate-200 px-5 py-4">
          <div>
            <h2 className="text-lg font-semibold">{title}</h2>
            {subtitle && <p className="text-sm text-slate-500">{subtitle}</p>}
          </div>
          <button className="btn-secondary px-2 py-1" onClick={onClose} aria-label="Close">x</button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{open && children}</div>
      </aside>
    </div>
  );
}
