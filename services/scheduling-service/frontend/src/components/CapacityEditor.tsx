import { useState } from 'react';
import { toast } from 'sonner';
import { api, Session } from '../api';
import { validateCapacity } from '../format';
import { toastError } from '../hooks';
import { Modal } from './ui';

/** Inline capacity change with validation and a confirmation step (usp_ChangeCapacity + trg_ExamSession_Update). */
export default function CapacityEditor({ session, onChanged, compact = false }: {
  session: Session; onChanged: (s: Session) => void; compact?: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(String(session.capacity));
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);
  const error = editing ? validateCapacity(value, session.accommodationCapacity) : null;
  const next = Number(value);
  const willBump = Math.max(0, session.bookedSeats - next);

  const start = () => { setValue(String(session.capacity)); setEditing(true); };
  const submit = () => {
    if (error) return;
    if (next === session.capacity) { setEditing(false); return; }
    setConfirming(true);
  };
  const apply = async () => {
    setSaving(true);
    try {
      const r = await api.changeCapacity(session.sessionId, next);
      onChanged(r.session);
      toast.success(`Capacity of session ${session.sessionId} is now ${r.session.capacity}`, {
        description: r.bumpedBookingIds.length
          ? `${r.bumpedBookingIds.length} booking(s) moved to the waitlist: ${r.bumpedBookingIds.join(', ')}`
          : 'No bookings were moved.',
      });
      setEditing(false);
      setConfirming(false);
    } catch (e) {
      toastError(e);
    } finally {
      setSaving(false);
    }
  };

  if (!editing) {
    return (
      <button type="button" onClick={start} data-testid="capacity-edit"
        className={`group inline-flex items-center gap-1.5 rounded-lg border border-transparent px-2 py-1 font-semibold text-slate-900 hover:border-slate-300 hover:bg-white ${compact ? 'text-sm' : 'text-2xl'}`}
        title="Change capacity">
        {session.capacity}
        <span className="text-xs font-normal text-slate-400 group-hover:text-brand-600">edit</span>
      </button>
    );
  }
  return (
    <form className="inline-flex flex-col" onSubmit={(e) => { e.preventDefault(); submit(); }} noValidate>
      <div className="flex items-center gap-1.5">
        <input autoFocus aria-label="New capacity" data-testid="capacity-input" inputMode="numeric"
          className={`input w-20 py-1 ${error ? 'input-error' : ''}`} value={value} onChange={(e) => setValue(e.target.value)} />
        <button type="submit" className="btn-primary px-2.5 py-1" disabled={!!error} data-testid="capacity-save">Save</button>
        <button type="button" className="btn-secondary px-2.5 py-1" onClick={() => setEditing(false)}>Cancel</button>
      </div>
      {error && <span className="field-error" data-testid="capacity-error">{error}</span>}
      <Modal open={confirming} onClose={() => setConfirming(false)} title="Change capacity?"
        footer={<>
          <button className="btn-secondary" onClick={() => setConfirming(false)}>Back</button>
          <button className={willBump > 0 ? 'btn-danger' : 'btn-primary'} onClick={apply} disabled={saving} data-testid="capacity-confirm">
            {saving ? 'Saving...' : `Set capacity to ${next}`}
          </button>
        </>}>
        <p>Session {session.sessionId} ({session.examCode}) goes from <b>{session.capacity}</b> to <b>{next}</b> seats.</p>
        {willBump > 0 ? (
          <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-800">
            {willBump} of the {session.bookedSeats} seated booking(s) will be moved to the waitlist - the most recently
            booked first.
          </p>
        ) : (
          <p className="mt-3 text-slate-500">No booked candidate is affected.</p>
        )}
      </Modal>
    </form>
  );
}
