package com.northwind.assessment.scheduling.domain;

/** {@code seat_offer.status} (V3__seat_offer.sql). Only PENDING holds a seat. */
public enum SeatOfferStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED, WITHDRAWN
}
