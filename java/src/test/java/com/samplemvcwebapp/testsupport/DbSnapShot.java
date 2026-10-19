package com.samplemvcwebapp.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

/** Port of Tests/Helpers/DbSnapShot.cs: row counts of Posts, TagPosts links, Blogs and Tags. */
public final class DbSnapShot {

    private final long numPosts;
    private final long numPostTagLinks;
    private final long numBlogs;
    private final long numTags;

    public DbSnapShot(EntityManager em) {
        numPosts = count(em, "select count(p) from Post p");
        numPostTagLinks = count(em, "select count(t) from Post p join p.tags t");
        numBlogs = count(em, "select count(b) from Blog b");
        numTags = count(em, "select count(t) from Tag t");
    }

    private static long count(EntityManager em, String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    public void checkSnapShot(EntityManager em) {
        checkSnapShot(em, 0, 0, 0, 0);
    }

    public void checkSnapShot(EntityManager em, int postsChange, int postTagLinkChange) {
        checkSnapShot(em, postsChange, postTagLinkChange, 0, 0);
    }

    public void checkSnapShot(EntityManager em, int postsChange, int postTagLinkChange, int blogsChange, int tagsChange) {
        DbSnapShot now = new DbSnapShot(em);
        assertThat(now.numPosts).as("posts wrong").isEqualTo(numPosts + postsChange);
        assertThat(now.numPostTagLinks).as("posttaglinks wrong").isEqualTo(numPostTagLinks + postTagLinkChange);
        assertThat(now.numBlogs).as("blogs wrong").isEqualTo(numBlogs + blogsChange);
        assertThat(now.numTags).as("tags wrong").isEqualTo(numTags + tagsChange);
    }

    public long getNumPosts() { return numPosts; }
    public long getNumPostTagLinks() { return numPostTagLinks; }
    public long getNumBlogs() { return numBlogs; }
    public long getNumTags() { return numTags; }
}
