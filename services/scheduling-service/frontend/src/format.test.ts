import { describe, expect, it } from 'vitest';
import { needsAccommodationSeat, seatTone, validateCandidateNumber, validateCapacity } from './format';

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
  it('floors capacity at the accommodation seats already booked, not the allocation', () => {
    expect(validateCapacity('1', 2)).toMatch(/accommodation seats already booked/);
    expect(validateCapacity('2', 2)).toBeNull();
    expect(validateCapacity('0', 0)).toBeNull();
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
