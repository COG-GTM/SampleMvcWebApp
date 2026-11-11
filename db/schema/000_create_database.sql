/*
    AssessmentDelivery - database + application login
    Run as sysadmin (sa) against the target instance.  Safe to re-run.

    NOTE: Azure SQL does not support CREATE DATABASE ... from here or server
    logins in master the same way; on Azure the database and contained user are
    created by the platform team (see ops wiki "AD Azure cutover 2016").
*/
IF DB_ID('AssessmentDelivery') IS NULL
    CREATE DATABASE AssessmentDelivery;
GO

-- MBA-3823: the shared ad_app login (docs/as-is HB-28, MR-07) is retired. Each component now has its
-- own login, created after the tables exist by db/security/036_component_logins.sql; legacy write denial
-- for the single-writer coexistence is db/security/040_single_writer.sql.
