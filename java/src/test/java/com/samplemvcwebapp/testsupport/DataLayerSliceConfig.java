package com.samplemvcwebapp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Adds the data layer's own beans (e.g. the DataLayerInitialiser implementation) to a {@code @DataJpaTest} slice,
 * the Java equivalent of the legacy DataLayerModule, without loading the service or web layers.
 */
@TestConfiguration
@ComponentScan("com.samplemvcwebapp.datalayer")
public class DataLayerSliceConfig {
}
