import type { BookRequest, CapacityRequest, RescheduleRequest } from './api.contract';

export interface Center { centerCode: string; name: string; city: string; region: string; timeZone: string }
export interface Exam { examCode: string; title: string; durationMinutes: number; retakeWaitDays: number }
export interface Session {
  sessionId: number; examCode: string; examTitle: string; centerCode: string; centerName: string; roomName: string;
  startsAt: string; status: string; capacity: number; accommodationCapacity: number; bookedSeats: number;
  seatsAvailable: number; accommodationUsed: number; accommodationAvailable: number; waitlistCount: number;
  /** seats held by pending waitlist offers (MBA-3829) */
  heldSeats: number;
}
export interface Booking {
  bookingId: number; sessionId: number; candidateNumber: string; candidateName: string; examCode: string;
  examTitle: string; centerCode: string; centerName: string; sessionStartsAt: string; status: string;
  accommodationCode: string | null; channel: string; externalRef: string | null; bookedAt: string;
  cancelledAt: string | null; cancelReason: string | null; waitlistPosition: number | null; rescheduledFrom: number | null;
  /** latest waitlist seat offer of this booking (MBA-3829) */
  offerId: number | null; offerStatus: string | null; offerExpiresAt: string | null;
}
export interface BookingResult { bookingId: number; status: string; waitlistPosition: number | null; booking: Booking; session: Session }
export interface WaitlistEntry {
  position: number; bookingId: number; candidateNumber: string; candidateName: string; accommodationCode: string | null; addedAt: string;
  /** pending seat offer (MBA-3829) */
  offerId: number | null; offerStatus: string | null; offerExpiresAt: string | null;
}
export interface Offer {
  offerId: number; sessionId: number; bookingId: number; candidateNumber: string; position: number; status: string;
  offeredAt: string; expiresAt: string; resolvedAt: string | null;
}
export interface OfferResult { offer: Offer; booking: Booking; session: Session }
export interface Candidate { candidateNumber: string; firstName: string; lastName: string; email: string; defaultAccommodationCode: string | null }
export interface Eligibility { eligible: boolean; reason: string; message: string; earliestRetakeDate: string | null; dayCounting: string }
export interface CapacityResult { session: Session; oldCapacity: number; newCapacity: number; demotedBookingIds: number[]; notifiedCandidates: number }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public legacyCode?: number | null) {
    super(message);
  }
}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`/api/v2${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, data?.error ?? 'ERROR', data?.message ?? res.statusText, data?.legacyCode);
  }
  return data as T;
}

const q = (params: Record<string, string | undefined>) => {
  const s = new URLSearchParams(Object.entries(params).filter(([, v]) => v) as [string, string][]).toString();
  return s ? `?${s}` : '';
};

export const api = {
  centers: () => call<Center[]>('GET', '/centers'),
  exams: () => call<Exam[]>('GET', '/exams'),
  sessions: (center: string, examCode?: string, from?: string, to?: string) =>
    call<Session[]>('GET', `/centers/${encodeURIComponent(center)}/sessions${q({ examCode, from, to })}`),
  session: (id: number) => call<Session>('GET', `/sessions/${id}`),
  waitlist: (id: number) => call<WaitlistEntry[]>('GET', `/sessions/${id}/waitlist`),
  changeCapacity: (id: number, capacity: number, reason?: string) =>
    call<CapacityResult>('PUT', `/sessions/${id}/capacity`, { capacity, reason } satisfies CapacityRequest),
  candidate: (n: string) => call<Candidate>('GET', `/candidates/${encodeURIComponent(n)}`),
  bookings: (n: string) => call<Booking[]>('GET', `/candidates/${encodeURIComponent(n)}/bookings`),
  eligibility: (n: string, examCode: string, date?: string) =>
    call<Eligibility>('GET', `/candidates/${encodeURIComponent(n)}/eligibility${q({ examCode, date })}`),
  book: (sessionId: number, candidateNumber: string, accommodationCode?: string) =>
    call<BookingResult>('POST', '/bookings', { sessionId, candidateNumber, accommodationCode: accommodationCode || null } satisfies BookRequest),
  booking: (id: number) => call<Booking>('GET', `/bookings/${id}`),
  cancel: (id: number) => call<Booking>('DELETE', `/bookings/${id}`),
  acceptOffer: (offerId: number) => call<OfferResult>('POST', `/offers/${offerId}/accept`),
  declineOffer: (offerId: number) => call<OfferResult>('POST', `/offers/${offerId}/decline`),
  reschedule: (id: number, newSessionId: number) => call<BookingResult>('POST', `/bookings/${id}/reschedule`, { newSessionId } satisfies RescheduleRequest),
};

export const ACCOMMODATIONS = [
  { code: '', label: 'None' },
  { code: 'ET25', label: 'ET25 – 25% extra time (uses an accommodation seat)' },
  { code: 'ET50', label: 'ET50 – 50% extra time (uses an accommodation seat)' },
  { code: 'SR', label: 'SR – separate room' },
  { code: 'RB', label: 'RB – rest breaks' },
];

export const isExtraTime = (code?: string | null) => code === 'ET25' || code === 'ET50';

/** ET25/ET50 need a free accommodation seat (usp_BookSeat state 3 = NO_ACCOMMODATION_SEAT); SR/RB/none never do. */
export const noAccommodationSeatFor = (s: Session, code?: string | null) => isExtraTime(code) && s.accommodationAvailable === 0;

export const CANDIDATE_RE = /^NW\d{8}$/;

export function fmtDateTime(iso: string) {
  return new Date(iso).toLocaleString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
