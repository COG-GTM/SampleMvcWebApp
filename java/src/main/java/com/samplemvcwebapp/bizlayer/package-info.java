/**
 * Legacy BizLayer port. The .NET BizLayer contained only BizLayerInitialise (an empty hook) and an Autofac
 * BizLayerModule that registered the ServiceLayer assembly's types; it had no business logic and no UI consumer.
 * Spring component scanning of {@code com.samplemvcwebapp} replaces that registration, so no types live here.
 */
package com.samplemvcwebapp.bizlayer;
