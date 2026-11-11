"""Characterization tooling for the Northwind Assessment Network legacy estate (MBA-3820)."""
import datetime

LEGACY_BASELINE_TAG = "legacy-baseline"
LEGACY_BASELINE_SHA = "e458238da39c991b51d8f670f6d0c463b51b372f"
TRANSCRIPT_FORMAT = 1

# The deterministic seed's clock (db/seed/042 @AsOf): sessions before it are DELIVERED, later ones OPEN; no seed
# timestamp falls between the booking cut-off (2026-11-10 18:00) and the first OPEN session (2026-11-11 09:00).
# Replays and captures compare against this instant instead of the wall clock, so a transcript replays the same
# on any day (otherwise OPEN seed sessions drift into the past as the calendar moves on).
SEED_AS_OF = datetime.datetime(2026, 11, 11)
