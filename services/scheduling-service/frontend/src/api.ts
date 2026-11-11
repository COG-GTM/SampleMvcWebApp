// Relative URLs only: the SPA also runs behind the routing gateway on :8000.
export interface Center { centerId: number; centerCode: string; name: string; city: string; region: string; timeZone: string }
export interface Exam { examCode: string; title: string; durationMinutes: number; retakeWaitDays: number }
export interface Candidate { candidateNumber: string; firstName: string; lastName: string; defaultAccommodationCode: string | null }
export interface Session {
  sessionId: number; examCode: string; examTitle: string; centerCode: string; centerName: string; roomName: string;
  startsAt: string; capacity: number; accommodationCapacity: number; seatsAvailable: number; status: string;
  bookedSeats: number; accommodationUsed: number; waitlistCount: number;
}
export interface Booking {
  bookingId: number; sessionId: number; candidateNumber: string; examCode: string; sessionStartsAt: string; status: string;
  accommodationCode: string | null; channel: string; externalRef: string | null; bookedAt: string; cancelledAt: string | null;
  cancelReason: string | null; origin: 'MODERN' | 'LEGACY_IMPORT'; legacyBookingId: number | null; waitlistPosition: number | null;
}
export interface BookingResult {
  bookingId: number; status: 'BOOKED' | 'WAITLISTED'; sessionId: number; candidateNumber: string;
  accommodationCode: string | null; channel: string; waitlistPosition: number | null; seatsAvailable: number;
}
export interface WaitlistEntry { position: number; bookingId: number; candidateNumber: string; candidateName: string | null; addedAt: string }
export interface Eligibility { eligible: boolean; reason: string; earliestRetakeDate: string | null }
export interface CapacityResult { session: Session; bumpedBookingIds: number[] }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public legacyCode: number | null) {
    super(message);
  }
}

async function call<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? { Accept: 'application/json' } : { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, json?.error ?? 'ERROR', json?.message ?? res.statusText, json?.legacyCode ?? null);
  }
  return json as T;
}

const q = (params: Record<string, string | undefined | null>) => {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v && s.set(k, v));
  const str = s.toString();
  return str ? `?${str}` : '';
};

export const api = {
  centers: () => call<Center[]>('GET', '/api/v2/centers'),
  exams: () => call<Exam[]>('GET', '/api/v2/exams'),
  sessions: (centerCode: string, examCode?: string, from?: string, to?: string) =>
    call<Session[]>('GET', `/api/v2/centers/${encodeURIComponent(centerCode)}/sessions${q({ examCode, from, to })}`),
  session: (id: number) => call<Session>('GET', `/api/v2/sessions/${id}`),
  waitlist: (id: number) => call<WaitlistEntry[]>('GET', `/api/v2/sessions/${id}/waitlist`),
  changeCapacity: (id: number, newCapacity: number) =>
    call<CapacityResult>('PUT', `/api/v2/sessions/${id}/capacity`, { newCapacity, actor: 'admin-ui' }),
  candidate: (n: string) => call<Candidate>('GET', `/api/v2/candidates/${encodeURIComponent(n)}`),
  candidateBookings: (n: string) => call<Booking[]>('GET', `/api/v2/candidates/${encodeURIComponent(n)}/bookings`),
  eligibility: (n: string, examCode: string, date: string) =>
    call<Eligibility>('GET', `/api/v2/candidates/${encodeURIComponent(n)}/eligibility${q({ examCode, date })}`),
  booking: (id: number) => call<Booking>('GET', `/api/v2/bookings/${id}`),
  book: (sessionId: number, candidateNumber: string, accommodationCode: string | null) =>
    call<BookingResult>('POST', '/api/v2/bookings', { sessionId, candidateNumber, accommodationCode, externalRef: null, channel: 'PORTAL' }),
  cancel: (id: number, reason?: string) => call<Booking>('DELETE', `/api/v2/bookings/${id}${q({ reason })}`),
  reschedule: (id: number, newSessionId: number) => call<BookingResult>('POST', `/api/v2/bookings/${id}/reschedule`, { newSessionId }),
};
