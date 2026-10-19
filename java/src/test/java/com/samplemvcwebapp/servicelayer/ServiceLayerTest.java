package com.samplemvcwebapp.servicelayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.dto.BlogDto;
import com.samplemvcwebapp.servicelayer.dto.BlogListDto;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import com.samplemvcwebapp.servicelayer.services.BlogService;
import com.samplemvcwebapp.servicelayer.services.PostAsyncService;
import com.samplemvcwebapp.servicelayer.services.PostService;
import com.samplemvcwebapp.servicelayer.services.TagAsyncService;
import com.samplemvcwebapp.servicelayer.services.TagService;
import com.samplemvcwebapp.servicelayer.ui.KeyValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import com.samplemvcwebapp.datalayer.repositories.BlogRepository;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:serviceLayerTest;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE")
class ServiceLayerTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired PostService postService;
    @Autowired PostAsyncService postAsyncService;
    @Autowired TagService tagService;
    @Autowired TagAsyncService tagAsyncService;
    @Autowired BlogService blogService;
    @Autowired BlogRepository blogRepository;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void seed() {
        initialiser.resetBlogs(TestDataSelection.SMALL);
    }

    @Test
    void listsAndCountMessage() {
        List<SimplePostDto> posts = postService.listPosts(null);
        assertThat(posts).extracting(SimplePostDto::bloggerName).containsExactly("Jon Smith", "Jon Smith", "Fred Bloggs");
        assertThat(posts.get(0).tagNames()).isEqualTo("Good post, Ugly post");
        assertThat(postService.listPosts(posts.get(2).blogId())).hasSize(1);
        assertThat(postService.getNumPostsMessage()).isEqualTo("The total number of Posts is 3");
        assertThat(tagService.listTags()).extracting(TagListDto::postsCount).containsExactly(1, 2, 2);
        assertThat(blogService.listBlogs()).extracting(BlogListDto::postsCount).containsExactly(2, 1);
    }

    @Test
    void createSetupAndCreatePost() {
        DetailPostDto dto = postService.getCreateSetup();
        assertThat(dto.getBloggers().getKeyValueList()).extracting(KeyValue::text).containsExactly("Jon Smith", "Fred Bloggs");
        assertThat(dto.getUserChosenTags().getKeyValueList()).extracting(KeyValue::text)
                .containsExactly("Good post", "Ugly post", "Bad post");

        SuccessOrErrors none = postService.create(dto);
        assertThat(none.getErrors()).containsExactly(
                new ValidationError("Content", "The Content field is required."),
                new ValidationError("Title", "The Title field is required."));

        dto.setTitle("New post");
        dto.setContent("Some words.");
        assertThat(postService.create(dto).getErrors()).extracting(ValidationError::message).containsExactly(
                "The blogger was not selected. You must do that before the post can be saved.",
                "You must select at least one tag for the post.");

        dto.getBloggers().setSelectedValue(dto.getBloggers().getKeyValueList().get(1).value());
        dto.getUserChosenTags().setFinalSelection(new ArrayList<>(List.of(
                Integer.valueOf(dto.getUserChosenTags().getKeyValueList().get(2).value()),
                Integer.valueOf(dto.getUserChosenTags().getKeyValueList().get(0).value()))));
        SuccessOrErrors ok = postService.create(dto);
        assertThat(ok.isValid()).isTrue();
        assertThat(ok.getSuccessMessage()).isEqualTo("Successfully created Post.");
        DetailPostDto created = postService.getDetail(dto.getPostId()).orElseThrow();
        assertThat(created.getBloggerName()).isEqualTo("Fred Bloggs");
        assertThat(created.getTagNames()).isEqualTo("Good post, Bad post");
    }

    @Test
    void updatePostEnforcesBusinessRulesAndPreselects() {
        int postId = postService.listPosts(null).get(0).postId();
        DetailPostDto dto = postService.getUpdateSetup(postId).orElseThrow();
        assertThat(dto.getBloggers().getSelectedValue()).isEqualTo(dto.getBloggers().getKeyValueList().get(0).value());
        assertThat(dto.getUserChosenTags().getFinalSelection()).hasSize(2);

        dto.setTitle("Great!");
        SuccessOrErrors bad = postService.update(dto);
        assertThat(bad.getErrors()).containsExactly(
                new ValidationError("Title", "Sorry, but you can't get too excited and include a ! in the title."));
        assertThat(postService.getDetail(postId).orElseThrow().getTitle()).isEqualTo("First great post");

        dto.setTitle("Edited");
        dto.getBloggers().setSelectedValue("99999");
        assertThat(postService.update(dto).getErrors()).extracting(ValidationError::message)
                .containsExactly("Could not find the blogger you selected. Did another user delete it?");

        dto.getBloggers().setSelectedValue(dto.getBloggers().getKeyValueList().get(1).value());
        assertThat(postService.update(dto).getSuccessMessage()).isEqualTo("Successfully updated Post.");
        assertThat(postService.getDetail(postId).orElseThrow().getBloggerName()).isEqualTo("Fred Bloggs");
        assertThat(blogService.listBlogs()).extracting(BlogListDto::postsCount).containsExactly(1, 2);
        assertThat(postService.getNumPostsMessage()).isEqualTo("The total number of Posts is 3");

        assertThat(postService.delete(postId).getSuccessMessage()).isEqualTo("Successfully deleted Post.");
        assertThat(postService.getNumPostsMessage()).isEqualTo("The total number of Posts is 2");
    }

    @Test
    void tagCrudWithSlugUniqueness() throws Exception {
        TagDto dto = new TagDto();
        dto.setName("Duplicate");
        dto.setSlug("good");
        assertThat(tagService.create(dto).getErrors()).containsExactly(
                new ValidationError("Slug", "The Slug on tag 'Duplicate' must be unique and is already being used."));
        dto.setSlug("bad slug");
        assertThat(tagService.create(dto).getErrors()).containsExactly(
                new ValidationError("Slug", "The slug must not contain spaces or non-alphanumeric characters."));

        dto.setSlug("caf\u00e9_2");
        assertThat(tagService.create(dto).isValid()).isTrue();
        dto.setSlug("ab\u200Dcd");
        assertThat(tagService.create(dto).getErrors()).extracting(ValidationError::message)
                .containsExactly("The slug must not contain spaces or non-alphanumeric characters.");
        dto.setName(" ");
        dto.setSlug("dup");
        assertThat(tagService.create(dto).getErrors()).extracting(ValidationError::message)
                .containsExactly("The Name field is required.");
        dto.setName("Duplicate");
        assertThat(tagAsyncService.createAsync(dto).get().getSuccessMessage()).isEqualTo("Successfully created Tag.");
        TagDto existing = tagService.getDetail(dto.getTagId()).orElseThrow();
        existing.setName("Renamed");
        assertThat(tagService.update(existing).isValid()).isTrue();
        existing.setSlug("bad");
        assertThat(tagService.update(existing).getErrors()).extracting(ValidationError::propertyName).containsExactly("Slug");
        assertThat(tagAsyncService.deleteAsync(existing.getTagId()).get().getSuccessMessage())
                .isEqualTo("Successfully deleted Tag.");
        assertThat(tagService.delete(existing.getTagId()).getErrors()).hasSize(1);
    }

    @Test
    void blogCrudAndAsyncPosts() throws Exception {
        BlogDto blog = new BlogDto();
        blog.setName("A");
        blog.setEmailAddress("not-an-email");
        assertThat(blogService.create(blog).getErrors()).extracting(ValidationError::propertyName)
                .containsExactlyInAnyOrder("EmailAddress", "Name");
        blog.setName("Ann Other");
        blog.setEmailAddress("ann@nospam.com");
        assertThat(blogService.create(blog).getSuccessMessage()).isEqualTo("Successfully created Blog.");

        int jon = blogService.listBlogs().get(0).blogId();
        assertThat(blogService.delete(jon).getSuccessMessage()).isEqualTo("Successfully deleted Blog.");
        assertThat(postAsyncService.listPostsAsync().get()).hasSize(1);
        assertThat(postAsyncService.getNumPostsMessageAsync().get()).isEqualTo("The total number of Posts is 1");
    }

    @Test
    void movedPostSurvivesDeletingOldBlogInSameTransaction() {
        tx.executeWithoutResult(status -> {
            var blogs = blogRepository.findAllByOrderByBlogIdAsc();
            assertThat(blogs.get(0).getPosts()).hasSize(2);
            assertThat(blogs.get(1).getPosts()).hasSize(1);
            DetailPostDto dto = postService.getUpdateSetup(postService.listPosts(null).get(0).postId()).orElseThrow();
            dto.getBloggers().setSelectedValue(String.valueOf(blogs.get(1).getBlogId()));
            assertThat(postService.update(dto).isValid()).isTrue();
            assertThat(blogService.delete(blogs.get(0).getBlogId()).isValid()).isTrue();
        });
        assertThat(postService.listPosts(null)).extracting(SimplePostDto::bloggerName)
                .containsExactly("Fred Bloggs", "Fred Bloggs");
    }
}
