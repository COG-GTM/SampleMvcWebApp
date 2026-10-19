package com.samplemvcwebapp.datalayer.startup;

/** Mirrors DataLayer.Startup.TestDataSelection. Small = seed/BlogsContentSimple.xml, Medium = seed/BlogsContextMedium.xml. */
public enum TestDataSelection {
    SMALL("seed/BlogsContentSimple.xml"),
    MEDIUM("seed/BlogsContextMedium.xml");

    private final String classpathResource;

    TestDataSelection(String classpathResource) { this.classpathResource = classpathResource; }

    public String getClasspathResource() { return classpathResource; }
}
