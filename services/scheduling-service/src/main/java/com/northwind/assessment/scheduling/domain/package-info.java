/**
 * Seat-inventory rules of the Northwind Assessment Network, ported from the legacy stored
 * procedures, triggers, booking-api (Java 8) and the portal (C#) into plain Java.
 *
 * <p>No Spring, no JDBC: everything here is unit-tested in isolation. Each class cites the legacy
 * source it replaces; the decisions where the legacy channels disagree are recorded in
 * {@code docs/adr/0002-ported-rule-decisions.md}.
 */
package com.northwind.assessment.scheduling.domain;
