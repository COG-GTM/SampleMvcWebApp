export const ACCOMMODATION_OPTIONS = [
  { value: '', label: 'None' },
  { value: 'ET25', label: 'ET25 - 25% extra time (accommodation seat)' },
  { value: 'ET50', label: 'ET50 - 50% extra time (accommodation seat)' },
  { value: 'SR', label: 'SR - separate room' },
  { value: 'RB', label: 'RB - rest breaks' },
];

export const CANDIDATE_NUMBER = /^NW\d{8}$/;

export function validateCandidateNumber(value: string): string | null {
  const v = value.trim().toUpperCase();
  if (!v) return 'Candidate number is required';
  if (!CANDIDATE_NUMBER.test(v)) return 'Use the format NW followed by 8 digits, e.g. NW00000901';
  return null;
}

export function validateCapacity(value: string, accommodationCapacity: number): string | null {
  if (value.trim() === '') return 'Enter a capacity';
  if (!/^\d+$/.test(value.trim())) return 'Capacity must be a whole number';
  const n = Number(value);
  if (n > 500) return 'Capacity cannot exceed 500';
  if (n < accommodationCapacity) return `Cannot go below the ${accommodationCapacity} accommodation seats`;
  return null;
}

export const needsAccommodationSeat = (code: string | null | undefined) => code === 'ET25' || code === 'ET50';

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return d.toLocaleString('en-US', { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

export function seatTone(available: number, capacity: number): 'full' | 'low' | 'ok' {
  if (available <= 0) return 'full';
  if (available <= Math.max(2, Math.floor(capacity * 0.2))) return 'low';
  return 'ok';
}

/** Admin waitlist drawer label for a position's offer state (ADR 0003). */
export function waitlistOfferLabel(w: { offerState: string; offerExpiresAt: string | null; accommodationCode: string | null }): string | null {
  if (w.offerState === 'OFFERED') return `Offered, expires ${formatDateTime(w.offerExpiresAt)}`;
  if (w.offerState === 'SKIPPED') return `Skipped: needs ${w.accommodationCode ?? 'accommodation'}`;
  return null;
}
