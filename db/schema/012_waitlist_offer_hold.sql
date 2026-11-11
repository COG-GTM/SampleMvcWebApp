/*
    012 - waitlist seat-offer hold (MBA-3829, A4, ADR 0003).

    scheduling-service offers a freed seat to the next waitlisted candidate and holds it until the offer is
    accepted, declined or expires.  Waitlist.OfferExpiresAt is set by the outbox relay (ad_sched_relay) while the
    entry holds a PENDING offer and cleared otherwise, so legacy readers can see why SeatsAvailable is lower
    than Capacity - BOOKED - ATTENDED, and db/checks/seat-drift.sql counts the held seats.
    Nullable, no default: existing rows, procs and the EF6 portal model are unaffected.
*/
USE AssessmentDelivery;
GO
IF COL_LENGTH('dbo.Waitlist', 'OfferExpiresAt') IS NULL
    ALTER TABLE dbo.Waitlist ADD OfferExpiresAt DATETIME NULL;
GO
