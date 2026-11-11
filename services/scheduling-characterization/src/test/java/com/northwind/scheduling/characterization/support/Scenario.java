package com.northwind.scheduling.characterization.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Catalogue entry for a scenario; every id must appear in README.md (checked by CatalogueConsistencyTest). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Scenario {
    /** Scenario id, e.g. {@code BS-01}. */
    String id();

    /** Proc/trigger branch covered, e.g. {@code 020 seats > 0}. */
    String covers();

    /** HB-xx / MR-xx references, comma separated. */
    String refs() default "";
}
