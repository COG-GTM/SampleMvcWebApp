package com.samplemvcwebapp.group01datalayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.testsupport.DataLayerSliceConfig;
import com.samplemvcwebapp.testsupport.DbSnapShot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Port of Tests/UnitTests/Group01DataLayer/Test14ReadWriteBlogs.cs (13 tests).
 * EF "Include(...)" eager loading maps to JPQL "join fetch"; "no Include" maps to the association not being loaded
 * (checked with PersistenceUnitUtil.isLoaded). SaveChanges maps to flush + clear so re-reads hit the database.
 */
@DataJpaTest
@Import(DataLayerSliceConfig.class)
class Test14ReadWriteBlogsTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired EntityManager em;

    private PersistenceUnitUtil util;

    @BeforeEach
    void setUpFixture() {
        initialiser.resetBlogs(TestDataSelection.SMALL);
        em.flush();
        em.clear();
        util = em.getEntityManagerFactory().getPersistenceUnitUtil();
    }

    @Test
    void check01ReadBlogsNoPostsOk() {
        List<Blog> allBlogs = em.createQuery("select b from Blog b order by b.blogId", Blog.class).getResultList();

        assertThat(allBlogs).hasSize(2);
        assertThat(allBlogs.get(0).getName()).isEqualTo("Jon Smith");
        assertThat(util.isLoaded(allBlogs.get(0), "posts")).isFalse();
    }

    @Test
    void check02ReadBlogsWithPostsOk() {
        List<Blog> allBlogs = em.createQuery(
                "select distinct b from Blog b left join fetch b.posts order by b.blogId", Blog.class).getResultList();

        assertThat(allBlogs).hasSize(2);
        assertThat(util.isLoaded(allBlogs.get(0), "posts")).isTrue();
        assertThat(allBlogs.get(0).getPosts()).hasSize(2);
        assertThat(allBlogs.get(1).getPosts()).hasSize(1);
        assertThat(util.isLoaded(allBlogs.get(0).getPosts().get(0), "tags")).isFalse();
    }

    @Test
    void check03ReadBlogsWithPostTagsOk() {
        // Include(b => b.Posts.Select(p => p.Tags)): fetching a bag and its children in one join duplicates the bag rows,
        // so the second level is fetched into the same persistence context by a second query.
        List<Blog> allBlogs = em.createQuery(
                "select distinct b from Blog b left join fetch b.posts order by b.blogId", Blog.class).getResultList();
        em.createQuery("select distinct p from Post p left join fetch p.tags", Post.class).getResultList();

        assertThat(allBlogs).hasSize(2);
        assertThat(allBlogs.get(0).getPosts()).hasSize(2);
        assertThat(util.isLoaded(allBlogs.get(0).getPosts().get(0), "tags")).isTrue();
        assertThat(allBlogs.get(0).getPosts().get(0).getTags()).hasSize(2);
    }

    @Test
    void check05ReadPostsOk() {
        List<Post> allPosts = em.createQuery("select p from Post p order by p.postId", Post.class).getResultList();

        assertThat(allPosts).hasSize(3);
        assertThat(allPosts.get(0).getBlogger()).isNotNull();
        assertThat(util.isLoaded(allPosts.get(0), "tags")).isFalse();
    }

    @Test
    void check06ReadPostsWithTagsOk() {
        List<Post> allPosts = em.createQuery(
                "select distinct p from Post p left join fetch p.tags order by p.postId", Post.class).getResultList();

        assertThat(allPosts).hasSize(3);
        assertThat(util.isLoaded(allPosts.get(0), "tags")).isTrue();
        assertThat(allPosts.get(0).getTags()).extracting(Tag::getSlug).containsExactlyInAnyOrder("good", "ugly");
    }

    @Test
    void check10ReadTAllocatedTagsWithUglySlugOk() {
        List<Post> uglyPosts = em.createQuery(
                "select p from Post p join p.tags t where t.slug = 'ugly'", Post.class).getResultList();
        Tag uglyTag = em.createQuery(
                "select t from Tag t left join fetch t.posts where t.slug = 'ugly'", Tag.class).getSingleResult();

        assertThat(uglyPosts).hasSize(2);
        assertThat(uglyTag.getPosts()).hasSize(2);
    }

    @Test
    void check20AddPostOk() {
        DbSnapShot snap = new DbSnapShot(em);
        Blog jon = firstBlog();
        Tag uglyTag = tagBySlug("ugly");

        Post newPost = new Post();
        newPost.setBlogger(jon);
        newPost.setTitle("Test post");
        newPost.setContent("Nothing special");
        newPost.getTags().add(uglyTag);
        em.persist(newPost);
        saveChanges();

        snap.checkSnapShot(em, 1, 1);
        Tag reread = em.createQuery("select t from Tag t left join fetch t.posts where t.slug = 'ugly'", Tag.class)
                .getSingleResult();
        assertThat(reread.getPosts()).hasSize(3);
    }

    @Test
    void check21CheckUpdateSimpleOk() {
        DbSnapShot snap = new DbSnapShot(em);
        Post firstPost = firstPost();
        String newTitle = UUID.randomUUID().toString();

        firstPost.setTitle(newTitle);
        saveChanges();

        snap.checkSnapShot(em);
        assertThat(firstPost().getTitle()).isEqualTo(newTitle);
    }

    @Test
    void check22CheckUpdateLastUpdatedOk() throws InterruptedException {
        Post firstPost = firstPost();
        var originalLastUpdated = firstPost.getLastUpdated();
        Thread.sleep(400);

        firstPost.setTitle(UUID.randomUUID().toString());
        saveChanges();

        var newLastUpdated = firstPost().getLastUpdated();
        assertThat(Duration.between(originalLastUpdated, newLastUpdated).toMillis()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void check25UpdatePostToAddTagOk() {
        DbSnapShot snap = new DbSnapShot(em);
        Post firstPost = firstPostWithTags();
        Tag badTag = tagBySlug("bad");

        firstPost.getTags().add(badTag);
        saveChanges();

        snap.checkSnapShot(em, 0, 1);
        assertThat(firstPostWithTags().getTags()).hasSize(3);
    }

    @Test
    void check26ReplaceTagsOk() {
        DbSnapShot snap = new DbSnapShot(em);
        Post firstPost = firstPostWithTags();
        Set<Integer> existingIds = firstPost.getTags().stream().map(Tag::getTagId).collect(Collectors.toSet());
        List<Tag> tagsNotInFirstPost = em.createQuery("select t from Tag t order by t.tagId", Tag.class)
                .getResultList().stream().filter(t -> !existingIds.contains(t.getTagId())).toList();

        firstPost.setTags(new HashSet<>(tagsNotInFirstPost));
        saveChanges();

        snap.checkSnapShot(em, 0, -1);
        assertThat(firstPostWithTags().getTags()).extracting(Tag::getSlug).containsExactly("bad");
    }

    @Test
    void check30CheckCreateLastUpdatedOk() throws InterruptedException {
        DbSnapShot snap = new DbSnapShot(em);
        Post firstPost = firstPostWithTags();
        var originalLastUpdated = firstPost.getLastUpdated();
        Thread.sleep(400);

        Post copy = copyOf(firstPost);
        em.persist(copy);
        saveChanges();

        snap.checkSnapShot(em, 1, 2);
        Post reread = em.find(Post.class, copy.getPostId());
        assertThat(Duration.between(originalLastUpdated, reread.getLastUpdated()).toMillis()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void check31CheckCreateDataOk() {
        DbSnapShot snap = new DbSnapShot(em);
        Post firstPost = firstPostWithTags();
        int bloggerId = firstPost.getBlogger().getBlogId();
        Set<Integer> tagIds = firstPost.getTags().stream().map(Tag::getTagId).collect(Collectors.toSet());

        Post copy = copyOf(firstPost);
        em.persist(copy);
        saveChanges();

        snap.checkSnapShot(em, 1, 2);
        Post reread = em.createQuery("select p from Post p left join fetch p.tags join fetch p.blogger where p.postId = :id",
                Post.class).setParameter("id", copy.getPostId()).getSingleResult();
        assertThat(reread.getTitle()).isEqualTo(copy.getTitle());
        assertThat(reread.getContent()).isEqualTo(firstPost.getContent());
        assertThat(reread.getBlogger().getBlogId()).isEqualTo(bloggerId);
        assertThat(reread.getTags()).extracting(Tag::getTagId).containsExactlyInAnyOrderElementsOf(tagIds);
    }

    // ------------------------------------------------------------------ helpers

    private void saveChanges() {
        em.flush();
        em.clear();
    }

    private Blog firstBlog() {
        return em.createQuery("select b from Blog b order by b.blogId", Blog.class).setMaxResults(1).getSingleResult();
    }

    private Post firstPost() {
        return em.createQuery("select p from Post p order by p.postId", Post.class).setMaxResults(1).getSingleResult();
    }

    private Post firstPostWithTags() {
        int id = firstPost().getPostId();
        return em.createQuery("select p from Post p left join fetch p.tags where p.postId = :id", Post.class)
                .setParameter("id", id).getSingleResult();
    }

    private Tag tagBySlug(String slug) {
        return em.createQuery("select t from Tag t where t.slug = :slug", Tag.class).setParameter("slug", slug)
                .getSingleResult();
    }

    private static Post copyOf(Post source) {
        Post copy = new Post();
        copy.setTitle(UUID.randomUUID().toString());
        copy.setContent(source.getContent());
        copy.setBlogger(source.getBlogger());
        copy.getTags().addAll(source.getTags());
        return copy;
    }
}
