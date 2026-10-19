package com.samplemvcwebapp.datalayer.startup;

/** Test-only bridge to the package-private {@link LoadDbDataFromXml} so seed-file tests use the real loader. */
public final class SeedFiles {

    public record Counts(int blogs, int posts, long distinctTagSlugs) { }

    private SeedFiles() { }

    public static Counts load(String classpathResource) {
        LoadDbDataFromXml.SeedData data = LoadDbDataFromXml.load(classpathResource);
        return new Counts(data.blogs().size(),
                data.blogs().stream().mapToInt(b -> b.posts().size()).sum(),
                data.blogs().stream().flatMap(b -> b.posts().stream()).flatMap(p -> p.tagSlugs().stream()).distinct().count());
    }
}
