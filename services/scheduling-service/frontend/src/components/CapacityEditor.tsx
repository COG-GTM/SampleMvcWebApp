import { useState } from 'react';
import { ApiError, api, type CapacityResult, type Session } from '../api';
import { Modal } from './ui';
import { useToast } from './Toast';

/**
 * Inline capacity edit with a confirmation step. Lowering below the booked count demotes the newest
 * bookings to the waitlist (ADR 0002, ODD-02); the floor is the accommodation allocation (ODD-08). Affected
 * candidates get a CandidateCapacityImpactNotified event (MBA-3831, docs/events.md).
 */
export default function CapacityEditor({ session, onChanged }: { session: Session; onChanged: (r: CapacityResult) => void }) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(String(session.capacity));
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const n = Number(value);
  const error = value.trim() === '' || !Number.isInteger(n) ? 'Whole number required'
    : n < 0 ? 'Cannot be negative'
    : n < session.accommodationCapacity ? `Must be ≥ accommodation allocation (${session.accommodationCapacity})`
    : n === session.capacity ? 'Unchanged' : '';
  const demotions = Math.max(0, session.bookedSeats - n);

  async function apply() {
    setBusy(true);
    try {
      const r = await api.changeCapacity(session.sessionId, n, 'Changed in admin UI');
      onChanged(r);
      const detail = [
        r.demotedBookingIds.length ? `${r.demotedBookingIds.length} booking(s) moved to the waitlist` : '',
        r.notifiedCandidates ? `${r.notifiedCandidates} candidate notification(s) queued (stub, no email/SMS)` : '',
      ].filter(Boolean).join('; ');
      toast('success', `Session #${session.sessionId}: capacity ${r.oldCapacity} → ${r.newCapacity}`, detail || undefined);
      setEditing(false);
    } catch (e) {
      const ae = e as ApiError;
      toast('error', 'Capacity change rejected', `${ae.message}${ae.legacyCode ? ` (code ${ae.legacyCode})` : ''}`);
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  }

  if (!editing) {
    return (
      <button className="group inline-flex items-center gap-1 rounded px-1 font-semibold hover:bg-indigo-50" title="Change capacity"
        onClick={() => { setValue(String(session.capacity)); setEditing(true); }} data-testid={`capacity-edit-${session.sessionId}`}>
        <span data-testid={`capacity-${session.sessionId}`}>{session.capacity}</span>
        <span className="text-xs text-slate-400 group-hover:text-indigo-600">✎</span>
      </button>
    );
  }
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-1">
        <input type="number" min={0} className={`input w-20 py-1 ${error && error !== 'Unchanged' ? 'input-error' : ''}`} value={value} autoFocus
          onChange={(e) => setValue(e.target.value)} onKeyDown={(e) => { if (e.key === 'Escape') setEditing(false); if (e.key === 'Enter' && !error) setConfirming(true); }}
          aria-label="New capacity" data-testid={`capacity-input-${session.sessionId}`} />
        <button className="btn-primary px-2 py-1" disabled={!!error} onClick={() => setConfirming(true)} data-testid={`capacity-save-${session.sessionId}`}>Save</button>
        <button className="btn-secondary px-2 py-1" onClick={() => setEditing(false)}>✕</button>
      </div>
      {error && error !== 'Unchanged' && <p className="text-xs text-rose-600">{error}</p>}
      <Modal open={confirming} title="Confirm capacity change" onClose={() => setConfirming(false)}>
        <p className="text-sm text-slate-600">Change session #{session.sessionId} from <b>{session.capacity}</b> to <b>{n}</b> seats?</p>
        {demotions > 0 && <p className="mt-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{demotions} active booking(s) exceed the new capacity; the newest will be moved to the waitlist.</p>}
        {n < session.capacity && session.bookedSeats > 0 && <p className="mt-2 text-xs text-slate-500" data-testid="capacity-notify-hint">A notification event is queued for each affected candidate (MBA-3831; stub, no email/SMS yet).</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button className="btn-secondary" onClick={() => setConfirming(false)}>Back</button>
          <button className="btn-primary" disabled={busy} onClick={apply} data-testid="confirm-capacity">{busy ? 'Saving…' : 'Confirm'}</button>
        </div>
      </Modal>
    </div>
  );
}
