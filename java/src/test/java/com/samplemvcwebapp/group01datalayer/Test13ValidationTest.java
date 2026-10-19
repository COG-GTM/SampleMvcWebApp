package com.samplemvcwebapp.group01datalayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.services.PostService;
import com.samplemvcwebapp.servicelayer.services.TagService;
import com.samplemvcwebapp.testsupport.DbSnapShot;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of Tests/UnitTests/Group01DataLayer/Test13Validation.cs (7 tests).
 * Legacy validated entities in SampleWebAppDb.SaveChangesWithChecking (DataAnnotations, Post.IValidatableObject and
 * ValidateEntity slug uniqueness). In Java those rules run in the services (JAVA_MIGRATION_NOTES §4/§6), which return
 * SuccessOrErrors instead of throwing, so the same inputs are driven through TagService/PostService.create.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Test13ValidationTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired TagService tagService;
    @Autowired PostService postService;
    @Autowired EntityManager em;

    @BeforeEach
    void setUpFixture() {
        initialiser.resetBlogs(TestDataSelection.SMALL);
    }

    @Test
    void check01ValidateTagOk() {
        DbSnapShot snap = new DbSnapShot(em);

        SuccessOrErrors status = tagService.create(tag("non-duplicate slug", UUID.randomUUID().toString().replace("-", "")));

        assertThat(status.isValid()).as("%s", status.getErrors()).isTrue();
        snap.checkSnapShot(em, 0, 0, 0, 1);
    }

    @Test
    void check02ValidateTagError() {
        Tag existingTag = firstTag();
        DbSnapShot snap = new DbSnapShot(em);

        SuccessOrErrors status = tagService.create(tag("duplicate slug", existingTag.getSlug()));

        assertThat(status.isValid()).isFalse();
        assertThat(status.getErrors()).extracting(ValidationError::message)
                .containsExactly("The Slug on tag 'duplicate slug' must be unique and is already being used.");
        snap.checkSnapShot(em);
    }

    @Test
    void check10ValidatePostOk() {
        DbSnapShot snap = new DbSnapShot(em);

        SuccessOrErrors status = postService.create(post("Test post", "Nothing special"));

        assertThat(status.isValid()).as("%s", status.getErrors()).isTrue();
        snap.checkSnapShot(em, 1, 1);
    }

    @Test
    void check15ValidatePostTitleError() {
        SuccessOrErrors status = postService.create(post("Test post!", "Nothing special"));

        assertThat(status.isValid()).isFalse();
        assertThat(status.getErrors()).extracting(ValidationError::message)
                .containsExactly("Sorry, but you can't get too excited and include a ! in the title.");
    }

    @Test
    void check16ValidatePostTitleError() {
        SuccessOrErrors status = postService.create(post("Test post?", "Nothing special"));

        assertThat(status.isValid()).isFalse();
        assertThat(status.getErrors()).extracting(ValidationError::message)
                .containsExactly("Sorry, but you can't ask a question, i.e. the title can't end with '?'.");
    }

    @Test
    void check20ValidatePostContentOneError() {
        SuccessOrErrors status = postService.create(post("Test post", "Should not end sentence with sheep."));

        assertThat(status.isValid()).isFalse();
        assertThat(status.getErrors()).extracting(ValidationError::message)
                .containsExactly("Sorry. Not allowed to end a sentance with 'sheep'.");
        assertThat(status.getErrors().get(0).propertyName()).as("model-level error").isNull();
    }

    @Test
    void check21ValidatePostContentTwoErrors() {
        SuccessOrErrors status = postService.create(
                post("Test post", "Should not end sentence with sheep. Nor end sentence with lamb."));

        assertThat(status.isValid()).isFalse();
        assertThat(status.getErrors()).extracting(ValidationError::message).containsExactly(
                "Sorry. Not allowed to end a sentance with 'sheep'.",
                "Sorry. Not allowed to end a sentance with 'lamb'.");
    }

    // ------------------------------------------------------------------ helpers

    private Tag firstTag() {
        return em.createQuery("select t from Tag t order by t.tagId", Tag.class).setMaxResults(1).getSingleResult();
    }

    private Blog firstBlog() {
        return em.createQuery("select b from Blog b order by b.blogId", Blog.class).setMaxResults(1).getSingleResult();
    }

    private static TagDto tag(String name, String slug) {
        TagDto dto = new TagDto();
        dto.setName(name);
        dto.setSlug(slug);
        return dto;
    }

    private DetailPostDto post(String title, String content) {
        DetailPostDto dto = postService.getCreateSetup();
        dto.setTitle(title);
        dto.setContent(content);
        dto.getBloggers().setSelectedValue(String.valueOf(firstBlog().getBlogId()));
        dto.getUserChosenTags().setFinalSelection(List.of(firstTag().getTagId()));
        return dto;
    }
}
