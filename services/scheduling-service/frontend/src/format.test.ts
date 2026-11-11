import { describe, expect, it } from 'vitest';
import {
  ACC_NONE, accommodationParam, accommodationSeatsLeft, effectiveAccommodation, hasSeatFor, needsAccommodationSeat, seatTone,
  validateCandidateNumber, validateCapacity,
} from './format';

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

describe('accommodation seats (MBA-3830)', () => {
  const session = (accommodationCapacity: number, accommodationUsed: number, accommodationSeatsAvailable?: number) =>
    ({ accommodationCapacity, accommodationUsed, accommodationSeatsAvailable });

  it('prefers the server figure, else derives capacity - used floored at 0', () => {
    expect(accommodationSeatsLeft(session(2, 1, 1))).toBe(1);
    expect(accommodationSeatsLeft(session(2, 1))).toBe(1);
    expect(accommodationSeatsLeft(session(1, 3))).toBe(0);
    expect(accommodationSeatsLeft(session(5, 0, 0))).toBe(0);
  });

  it('only ET25/ET50 need a free accommodation seat', () => {
    const full = session(1, 1, 0);
    expect(hasSeatFor('ET25', full)).toBe(false);
    expect(hasSeatFor('ET50', full)).toBe(false);
    expect(hasSeatFor('SR', full)).toBe(true);
    expect(hasSeatFor('RB', full)).toBe(true);
    expect(hasSeatFor('', full)).toBe(true);
    expect(hasSeatFor(null, full)).toBe(true);
    expect(hasSeatFor('ET25', session(2, 1, 1))).toBe(true);
  });

  it('pre-fills the candidate default unless an explicit choice (including None) was made', () => {
    expect(effectiveAccommodation(null, 'ET25')).toBe('ET25');
    expect(effectiveAccommodation('', 'ET25')).toBe('ET25');
    expect(effectiveAccommodation('SR', 'ET25')).toBe('SR');
    expect(effectiveAccommodation(ACC_NONE, 'ET25')).toBe('');
    expect(effectiveAccommodation(null, null)).toBe('');
  });

  it('encodes None explicitly for the URL', () => {
    expect(accommodationParam('')).toBe(ACC_NONE);
    expect(accommodationParam('ET50')).toBe('ET50');
  });
});
