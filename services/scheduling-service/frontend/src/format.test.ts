import { describe, expect, it } from 'vitest';
import { needsAccommodationSeat, offerExpired, seatTone, validateCandidateNumber, validateCapacity, waitlistOfferLabel } from './format';

describe('validateCandidateNumber', () => {
  it('requires a value', () => expect(validateCandidateNumber('  ')).toMatch(/required/));
  it('rejects a bad format', () => expect(validateCandidateNumber('12345')).toMatch(/format/));
  it('accepts NW + 8 digits, case-insensitive', () => expect(validateCandidateNumber('nw00000901')).toBeNull());
});

describe('validateCapacity', () => {
  it('requires a whole number', () => {
    expect(validateCapacity('', 0)).toMatch(/Enter/);
    expect(validateCapacity('3.5', 0)).toMatch(/whole/);
    expect(validateCapacity('-1', 0)).toMatch(/whole/);
  });
  it('mirrors the usp_ChangeCapacity accommodation floor', () => {
    expect(validateCapacity('1', 2)).toMatch(/accommodation/);
    expect(validateCapacity('2', 2)).toBeNull();
  });
  it('caps absurd values', () => expect(validateCapacity('900', 0)).toMatch(/exceed/));
});

describe('seat helpers', () => {
  it('only ET25/ET50 need an accommodation seat', () => {
    expect(needsAccommodationSeat('ET25')).toBe(true);
    expect(needsAccommodationSeat('ET50')).toBe(true);
    expect(needsAccommodationSeat('SR')).toBe(false);
    expect(needsAccommodationSeat(null)).toBe(false);
  });
  it('classifies availability', () => {
    expect(seatTone(0, 20)).toBe('full');
    expect(seatTone(3, 20)).toBe('low');
    expect(seatTone(10, 20)).toBe('ok');
  });
});

describe('waitlistOfferLabel', () => {
  it('shows the expiry of an offered position', () =>
    expect(waitlistOfferLabel({ offerState: 'OFFERED', offerExpiresAt: '2026-11-12T09:30:00', accommodationCode: null }))
      .toMatch(/^Offered, expires .*2026/));
  it('names the accommodation a skipped position needs', () =>
    expect(waitlistOfferLabel({ offerState: 'SKIPPED', offerExpiresAt: null, accommodationCode: 'ET25' })).toBe('Skipped: needs ET25'));
  it('shows nothing for a waiting position', () =>
    expect(waitlistOfferLabel({ offerState: 'WAITING', offerExpiresAt: null, accommodationCode: 'ET50' })).toBeNull());
});

describe('offerExpired', () => {
  const now = new Date('2026-11-11T12:00:00');
  it('is false before expiresAt', () => expect(offerExpired('2026-11-11T12:00:01', now)).toBe(false));
  it('is true from expiresAt on', () => {
    expect(offerExpired('2026-11-11T12:00:00', now)).toBe(true);
    expect(offerExpired('2026-11-10T12:00:00', now)).toBe(true);
  });
});
