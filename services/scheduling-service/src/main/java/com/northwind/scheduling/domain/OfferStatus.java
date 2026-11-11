package com.northwind.scheduling.domain;

/** seat_offer.status (V3__waitlist_offers.sql, ADR 0004). Only PENDING holds a seat. */
public enum OfferStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED, WITHDRAWN
}
