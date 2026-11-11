// MBA-3833: compile-time check of api.ts against the committed spec (../api/openapi.yaml). `npm run gen:api`
// (run by build and typecheck) regenerates src/generated/openapi.ts; a spec change that the UI types no longer
// accept fails `tsc`.
import type { components } from './generated/openapi';
import type {
  Booking, BookingResult, Candidate, CapacityResult, Center, Eligibility, Exam, Offer, OfferResult, Session, WaitlistEntry,
} from './api';

export type Schemas = components['schemas'];

// Every response the server documents must be readable through the UI's type, and the UI may not
// expect a field the spec does not guarantee.
type Accepts<Ui, Spec extends Ui> = Spec;
export type ResponseContract = [
  Accepts<Center, Schemas['CenterDto']>,
  Accepts<Exam, Schemas['ExamDto']>,
  Accepts<Session, Schemas['SessionDto']>,
  Accepts<Booking, Schemas['BookingDto']>,
  Accepts<BookingResult, Schemas['BookingResultDto']>,
  Accepts<WaitlistEntry, Schemas['WaitlistEntryDto']>,
  Accepts<Candidate, Schemas['CandidateDto']>,
  Accepts<Eligibility, Schemas['EligibilityDto']>,
  Accepts<CapacityResult, Schemas['CapacityResultDto']>,
  Accepts<Offer, Schemas['OfferDto']>,
  Accepts<OfferResult, Schemas['OfferResultDto']>,
  Accepts<{ error: string; message: string; legacyCode?: number | null }, Schemas['ApiError']>,
  Accepts<{ error: string; message: string; legacyCode?: number | null }, Schemas['OfferError']>,
];

export type BookRequest = Schemas['BookRequest'];
export type RescheduleRequest = Schemas['RescheduleRequest'];
export type CapacityRequest = Schemas['CapacityRequest'];
