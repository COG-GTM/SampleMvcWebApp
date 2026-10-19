package com.samplemvcwebapp.datalayer;

import static org.assertj.core.api.Assertions.assertThat;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.entities.RuleViolation;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dataLayerTest;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE")
class DataLayerTest {

    @Autowired DataLayerInitialiser initialiser;
    @Autowired BlogRepository blogRepository;
    @Autowired PostRepository postRepository;
    @Autowired TagRepository tagRepository;
    @Autowired TransactionTemplate tx;
    @Autowired Validator validator;

    @Test
    void mediumSeedHasContractCountsAndTagOrder() {
        initialiser.resetBlogs(TestDataSelection.MEDIUM);
        assertThat(blogRepository.count()).isEqualTo(4);
        assertThat(postRepository.count()).isEqualTo(17);
        assertThat(tagRepository.findAllByOrderByTagIdAsc()).extracting(Tag::getName).containsExactly(
                "About Me", "Programming", "My Opinion", "Education", "Vices and Foibles", "Personal",
                "Entertainment", "Business");
        assertThat(postRepository.findAllByOrderByPostIdAsc().get(0).getTitle()).isEqualTo("About Ada Lovelace");
    }

    @Test
    void smallSeedReplacesExistingDataInDocumentOrder() {
        initialiser.resetBlogs(TestDataSelection.MEDIUM);
        initialiser.resetBlogs(TestDataSelection.SMALL);
        assertThat(blogRepository.findAllByOrderByBlogIdAsc()).extracting(Blog::getName)
                .containsExactly("Jon Smith", "Fred Bloggs");
        List<Post> posts = postRepository.findAllByOrderByPostIdAsc();
        assertThat(posts).extracting(Post::getTitle)
                .containsExactly("First great post", "Second post, which isn't bad", "Freds good post");
        assertThat(posts).extracting(Post::getTagNames).containsExactly("Good post, Ugly post", "Bad post", "Ugly post, Bad post");
        assertThat(tagRepository.findAllByOrderByTagIdAsc()).extracting(Tag::getSlug).containsExactly("good", "ugly", "bad");
        assertThat(posts.get(0).getLastUpdated())
                .isBetween(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
    }

    @Test
    void deletingBlogCascadesToPostsAndDeletingTagRemovesJoinRows() {
        initialiser.resetBlogs(TestDataSelection.SMALL);
        tx.executeWithoutResult(s -> blogRepository.delete(blogRepository.findAllByOrderByBlogIdAsc().get(1)));
        assertThat(postRepository.count()).isEqualTo(2);

        tx.executeWithoutResult(s -> tagRepository.delete(tagRepository.findAllByOrderByTagIdAsc().get(0)));
        assertThat(tagRepository.count()).isEqualTo(2);
        assertThat(postRepository.findAllByOrderByPostIdAsc()).extracting(Post::getTagNames)
                .containsExactly("Ugly post", "Bad post");
        assertThat(tagRepository.findAllWithPostsCount()).extracting(t -> t.getPostsCount()).containsExactly(1, 1);
    }

    @Test
    void entityValidationUsesLegacyMessages() {
        Post post = new Post(new Blog("Jon", "jon@nospam.com"), "x", "Bad sheep. And a cow.");
        post.getTags().add(new Tag("Good", "good"));
        assertThat(messages(validator.validate(post)))
                .containsExactly("The field Title must be a string or array type with a minimum length of '2'.");

        post.setTitle("Wow!?");
        assertThat(post.validate()).extracting(RuleViolation::message).containsExactly(
                "Sorry, but you can't get too excited and include a ! in the title.",
                "Sorry, but you can't ask a question, i.e. the title can't end with '?'.",
                "Sorry. Not allowed to end a sentance with 'sheep'.",
                "Sorry. Not allowed to end a sentance with 'cow'.");

        assertThat(messages(validator.validate(new Tag("Name", "has space"))))
                .containsExactly("The slug must not contain spaces or non-alphanumeric characters.");
        assertThat(messages(validator.validate(new Blog("", "x@y.com"))))
                .containsExactly("The Name field is required.");
        assertThat(messages(validator.validate(new Blog(" ", "x@y.com"))))
                .containsExactly("The Name field is required.");
    }

    private static List<String> messages(Set<? extends jakarta.validation.ConstraintViolation<?>> violations) {
        return violations.stream().map(jakarta.validation.ConstraintViolation::getMessage).sorted().toList();
    }
}
