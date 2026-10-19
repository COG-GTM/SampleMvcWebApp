package com.samplemvcwebapp.datalayer.startup;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataLayer.Startup.DataLayerInitialise.ResetBlogs. Inserts in legacy EF graph order: each blog, then its posts in
 * document order, creating a tag the first time a post references its slug (unreferenced tags are not persisted).
 */
@Component
public class JpaDataLayerInitialiser implements DataLayerInitialiser {

    private static final Logger log = LoggerFactory.getLogger(JpaDataLayerInitialiser.class);

    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final EntityManager entityManager;

    public JpaDataLayerInitialiser(BlogRepository blogRepository, PostRepository postRepository,
                                   TagRepository tagRepository, EntityManager entityManager) {
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void resetBlogs(TestDataSelection selection) {
        LoadDbDataFromXml.SeedData seed = LoadDbDataFromXml.load(selection.getClasspathResource());

        // Entity-level post deletes also remove their TagPosts rows; tags and blogs are then orphan-free.
        postRepository.deleteAll(postRepository.findAll());
        entityManager.flush();
        tagRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();

        Map<String, Tag> createdTags = new HashMap<>();
        int posts = 0;
        for (LoadDbDataFromXml.BlogData blogData : seed.blogs()) {
            Blog blog = new Blog(blogData.name(), blogData.email());
            entityManager.persist(blog);
            for (LoadDbDataFromXml.PostData postData : blogData.posts()) {
                Post post = new Post(blog, postData.title(), postData.content());
                for (String slug : postData.tagSlugs()) {
                    post.getTags().add(createdTags.computeIfAbsent(slug, s -> createTag(seed, s)));
                }
                blog.getPosts().add(post);
                entityManager.persist(post);
                posts++;
            }
        }
        entityManager.flush();
        log.info("Reset blogs data from {}: {} blogs, {} posts, {} tags",
                selection.getClasspathResource(), seed.blogs().size(), posts, createdTags.size());
    }

    private Tag createTag(LoadDbDataFromXml.SeedData seed, String slug) {
        LoadDbDataFromXml.TagData data = seed.tagsBySlug().get(slug);
        if (data == null)
            throw new IllegalStateException("Seed XML references unknown tag slug '" + slug + "'");
        Tag tag = new Tag(data.name(), data.slug());
        entityManager.persist(tag);
        return tag;
    }
}
