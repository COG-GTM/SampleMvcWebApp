package com.samplemvcwebapp.group06mvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.HtmlUtils;

/**
 * Full-stack route coverage for JAVA_MIGRATION_NOTES.md section 5: real controllers + real services + H2 seeded with
 * MEDIUM data (child B's *ControllerWebTest classes cover the same routes as web slices with mocked services).
 * Every GET route is rendered against real data and every write route is round-tripped through the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Test10RoutesEndToEndTest {

    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired DataLayerInitialiser initialiser;
    @Autowired EntityManagerFactory emf;

    @BeforeEach
    void seedMedium() {
        initialiser.resetBlogs(TestDataSelection.MEDIUM);
    }

    // ---- GET routes with no id ---------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "/Posts|posts/index|Posts - SampleMvcWebApp|Posts",
            "/Posts/Index|posts/index|Posts - SampleMvcWebApp|Posts",
            "/Posts/Index/0|posts/index|Posts - SampleMvcWebApp|Posts",
            "/Posts/Create|posts/create|Create - SampleMvcWebApp|Create",
            "/Posts/CodeView|posts/codeView|CodeView - SampleMvcWebApp|Posts: An explanation of Post code",
            "/Posts/NumPosts|posts/numPosts|' - SampleMvcWebApp'|The total number of Posts is 17",
            "/Posts/Delay|posts/delay|Posts Delay - SampleMvcWebApp|I delayed by 500 ms.",
            "/PostsAsync|postsAsync/index|PostsAsync - SampleMvcWebApp|Posts (async)",
            "/PostsAsync/Index|postsAsync/index|PostsAsync - SampleMvcWebApp|Posts (async)",
            "/PostsAsync/Index/0|postsAsync/index|PostsAsync - SampleMvcWebApp|Posts (async)",
            "/PostsAsync/Create|postsAsync/create|Create - SampleMvcWebApp|Create (Async)",
            "/PostsAsync/CodeView|postsAsync/codeView|CodeView - SampleMvcWebApp|PostsAsync: A explanation of the PostAsync code",
            "/PostsAsync/NumPosts|postsAsync/numPosts|' - SampleMvcWebApp'|The total number of Posts is 17",
            "/PostsAsync/Delay|postsAsync/delay|PostsAsync Delay - SampleMvcWebApp|I delayed by 500 ms.",
            "/Tags|tags/index|Tags - SampleMvcWebApp|Tags",
            "/Tags/Index|tags/index|Tags - SampleMvcWebApp|Tags",
            "/Tags/Create|tags/create|Create - SampleMvcWebApp|Create",
            "/Tags/CodeView|tags/codeView|CodeView - SampleMvcWebApp|Tags: A explanation of Tags code",
            "/TagsAsync|tagsAsync/index|TagsAsync - SampleMvcWebApp|Tags (Async)",
            "/TagsAsync/Index|tagsAsync/index|TagsAsync - SampleMvcWebApp|Tags (Async)",
            "/TagsAsync/Create|tagsAsync/create|Create - SampleMvcWebApp|Create (Async)",
            "/TagsAsync/CodeView|tagsAsync/codeView|CodeView - SampleMvcWebApp|Tags",
            "/Blogs|blogs/index|' - SampleMvcWebApp'|Blogs",
            "/Blogs/Index|blogs/index|' - SampleMvcWebApp'|Blogs",
            "/Blogs/Create|blogs/create|' - SampleMvcWebApp'|Blog"})
    void getRouteRendersAgainstRealData(String url, String viewName, String title, String text) throws Exception {
        perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(containsString("<title>" + title + "</title>")))
                .andExpect(content().string(containsString(text)));
    }

    @Test
    void postsIndexListsEverySeededPost() throws Exception {
        for (String prefix : List.of("/Posts", "/PostsAsync")) {
            ResultActions page = perform(get(prefix)).andExpect(status().isOk());
            for (Post p : allPosts()) {
                page.andExpect(content().string(containsString(escape(p.getTitle()))));
            }
        }
    }

    @Test
    void tagsAndBlogsIndexListEverySeededRow() throws Exception {
        for (String prefix : List.of("/Tags", "/TagsAsync")) {
            ResultActions page = perform(get(prefix)).andExpect(status().isOk());
            for (Tag t : query("select t from Tag t", Tag.class)) {
                page.andExpect(content().string(containsString(escape(t.getName()))));
            }
        }
        ResultActions blogs = perform(get("/Blogs")).andExpect(status().isOk());
        for (Blog b : query("select b from Blog b", Blog.class)) {
            blogs.andExpect(content().string(containsString(escape(b.getName()))));
        }
    }

    @Test
    void postsIndexWithBlogIdFiltersToThatBlog() throws Exception {
        List<Blog> blogs = query("select distinct b from Blog b left join fetch b.posts order by b.blogId", Blog.class);
        Blog filtered = blogs.get(0);
        Blog other = blogs.get(1);
        ResultActions page = perform(get("/Posts/Index/" + filtered.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/index"))
                .andExpect(content().string(containsString("Filtered list")));
        for (Post p : filtered.getPosts()) {
            page.andExpect(content().string(containsString(escape(p.getTitle()))));
        }
        for (Post p : other.getPosts()) {
            page.andExpect(content().string(not(containsString(escape(p.getTitle())))));
        }
    }

    // ---- GET routes with an id ---------------------------------------------------------------------------------

    @Test
    void detailsAndEditPagesRenderTheRealRow() throws Exception {
        Post post = allPosts().get(0);
        Tag tag = query("select t from Tag t order by t.tagId", Tag.class).get(0);
        Blog blog = query("select b from Blog b order by b.blogId", Blog.class).get(0);

        for (String prefix : List.of("/Posts", "/PostsAsync")) {
            String async = prefix.endsWith("Async") ? " (Async)" : "";
            String views = prefix.endsWith("Async") ? "postsAsync" : "posts";
            perform(get(prefix + "/Details/" + post.getPostId()))
                    .andExpect(status().isOk()).andExpect(view().name(views + "/details"))
                    .andExpect(content().string(containsString("<title>Details - SampleMvcWebApp</title>")))
                    .andExpect(content().string(containsString("Details" + async)))
                    .andExpect(content().string(containsString(escape(post.getTitle()))));
            perform(get(prefix + "/Edit/" + post.getPostId()))
                    .andExpect(status().isOk()).andExpect(view().name(views + "/edit"))
                    .andExpect(content().string(containsString("<title>Edit - SampleMvcWebApp</title>")))
                    .andExpect(content().string(containsString("Edit" + async)))
                    .andExpect(content().string(containsString(escape(post.getTitle()))));
        }
        for (String prefix : List.of("/Tags", "/TagsAsync")) {
            String views = prefix.endsWith("Async") ? "tagsAsync" : "tags";
            perform(get(prefix + "/Details/" + tag.getTagId()))
                    .andExpect(status().isOk()).andExpect(view().name(views + "/details"))
                    .andExpect(content().string(containsString(escape(tag.getName()))));
            perform(get(prefix + "/Edit/" + tag.getTagId()))
                    .andExpect(status().isOk()).andExpect(view().name(views + "/edit"))
                    .andExpect(content().string(containsString(escape(tag.getSlug()))));
        }
        perform(get("/Blogs/Edit/" + blog.getBlogId()))
                .andExpect(status().isOk()).andExpect(view().name("blogs/edit"))
                .andExpect(content().string(containsString("<h4>Blog</h4>")))
                .andExpect(content().string(containsString(escape(blog.getEmailAddress()))));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"/Posts/Details/999999", "/Posts/Edit/999999", "/PostsAsync/Details/999999", "/PostsAsync/Edit/999999",
            "/Tags/Details/999999", "/Tags/Edit/999999", "/TagsAsync/Details/999999", "/TagsAsync/Edit/999999",
            "/Blogs/Edit/999999"})
    void unknownIdRendersSharedErrorView(String url) throws Exception {
        perform(get(url))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("An error occurred while processing your request.")));
    }

    @Test
    void errorRouteOverRealHttpRendersSharedErrorView() {
        HttpHeaders browser = new HttpHeaders();
        browser.setAccept(List.of(MediaType.TEXT_HTML));
        ResponseEntity<String> response = http.exchange("/Error", HttpMethod.GET, new HttpEntity<>(browser), String.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        assertThat(response.getBody())
                .contains("<title>Error - SampleMvcWebApp</title>")
                .contains("An error occurred while processing your request.");
    }

    @Test
    void homePagesOverRealHttpUseTheLegacyLayout() {
        for (String url : List.of("/", "/Home", "/Home/Index", "/Home/About", "/Home/Contact", "/Home/CodeView",
                "/Home/Internals")) {
            ResponseEntity<String> response = http.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(url)
                    .contains("An open source project under the <a href=\"http://opensource.org/licenses/MIT\" target=\"_blank\">MIT licence</a>")
                    .contains("Hosted on LocalHost");
        }
    }

    // ---- write routes round-tripped through the database -------------------------------------------------------

    @Test
    void postsCreateEditDeleteRoundTrip() throws Exception {
        postsCrud("/Posts");
    }

    @Test
    void postsAsyncCreateEditDeleteRoundTrip() throws Exception {
        postsCrud("/PostsAsync");
    }

    @Test
    void postsCreateInvalidRedisplaysFormAndSavesNothing() throws Exception {
        for (String prefix : List.of("/Posts", "/PostsAsync")) {
            perform(postForm(prefix + "/Create", "", "Some content", firstBlogId(), firstTagId()))
                    .andExpect(status().isOk())
                    .andExpect(view().name(prefix.endsWith("Async") ? "postsAsync/create" : "posts/create"))
                    .andExpect(content().string(containsString("The Title field is required.")));
            assertThat(count("Post")).isEqualTo(17);
        }
    }

    @Test
    void postsResetRestoresMediumData() throws Exception {
        for (String prefix : List.of("/Posts", "/PostsAsync")) {
            perform(get(prefix + "/Delete/" + allPosts().get(0).getPostId())).andExpect(redirectedUrl(prefix));
            assertThat(count("Post")).isEqualTo(16);
            perform(get(prefix + "/Reset"))
                    .andExpect(redirectedUrl(prefix))
                    .andExpect(flash().attribute("message", "Successfully reset the blogs data"));
            assertThat(count("Post")).isEqualTo(17);
            assertThat(count("Blog")).isEqualTo(4);
            assertThat(count("Tag")).isEqualTo(8);
        }
    }

    @Test
    void tagsCreateEditDeleteRoundTrip() throws Exception {
        tagsCrud("/Tags");
    }

    @Test
    void tagsAsyncCreateEditDeleteRoundTrip() throws Exception {
        tagsCrud("/TagsAsync");
    }

    @Test
    void tagsCreateDuplicateSlugIsRejected() throws Exception {
        String existing = query("select t from Tag t order by t.tagId", Tag.class).get(0).getSlug();
        for (String prefix : List.of("/Tags", "/TagsAsync")) {
            perform(post(prefix + "/Create").param("name", "Dup").param("slug", existing))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("must be unique and is already being used.")));
            assertThat(count("Tag")).isEqualTo(8);
        }
    }

    @Test
    void blogsCreateEditDeleteRoundTrip() throws Exception {
        perform(post("/Blogs/Create").param("name", "Grace").param("emailAddress", "grace@nospam.com"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully created Blog."));
        Blog created = single("select b from Blog b where b.name = 'Grace'", Blog.class);
        assertThat(created.getEmailAddress()).isEqualTo("grace@nospam.com");

        perform(post("/Blogs/Edit/" + created.getBlogId()).param("blogId", String.valueOf(created.getBlogId()))
                .param("name", "Grace Hopper").param("emailAddress", "grace@nospam.com"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully updated Blog."));
        assertThat(find(Blog.class, created.getBlogId()).getName()).isEqualTo("Grace Hopper");

        perform(get("/Blogs/Delete/" + created.getBlogId()))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully deleted Blog."));
        assertThat(count("Blog")).isEqualTo(4);
    }

    @Test
    void blogsCreateInvalidRedisplaysForm() throws Exception {
        perform(post("/Blogs/Create").param("name", "").param("emailAddress", "nobody@nospam.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("blogs/create"));
        assertThat(count("Blog")).isEqualTo(4);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private void postsCrud(String prefix) throws Exception {
        perform(postForm(prefix + "/Create", "End to end post", "Created over HTTP", firstBlogId(), firstTagId()))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully created Post."));
        assertThat(count("Post")).isEqualTo(18);
        Post created = single("select p from Post p where p.title = 'End to end post'", Post.class);

        perform(postForm(prefix + "/Edit/" + created.getPostId(), "End to end post edited", "Updated over HTTP",
                firstBlogId(), firstTagId()).param("postId", String.valueOf(created.getPostId())))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully updated Post."));
        Post updated = find(Post.class, created.getPostId());
        assertThat(updated.getTitle()).isEqualTo("End to end post edited");
        assertThat(updated.getContent()).isEqualTo("Updated over HTTP");

        perform(get(prefix + "/Delete/" + created.getPostId()))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully deleted Post."));
        assertThat(count("Post")).isEqualTo(17);
    }

    private void tagsCrud(String prefix) throws Exception {
        perform(post(prefix + "/Create").param("name", "End To End").param("slug", "endtoend"))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully created Tag."));
        Tag created = single("select t from Tag t where t.slug = 'endtoend'", Tag.class);

        perform(post(prefix + "/Edit/" + created.getTagId()).param("tagId", String.valueOf(created.getTagId()))
                .param("name", "End To End Edited").param("slug", "endtoend"))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully updated Tag."));
        assertThat(find(Tag.class, created.getTagId()).getName()).isEqualTo("End To End Edited");

        perform(get(prefix + "/Delete/" + created.getTagId()))
                .andExpect(redirectedUrl(prefix))
                .andExpect(flash().attribute("message", "Successfully deleted Tag."));
        assertThat(count("Tag")).isEqualTo(8);
    }

    private static MockHttpServletRequestBuilder postForm(String url, String title, String content, int blogId,
            int tagId) {
        return post(url).param("title", title).param("content", content)
                .param("bloggers.selectedValue", String.valueOf(blogId))
                .param("userChosenTags.finalSelection", String.valueOf(tagId));
    }

    /** Performs the request and, for the *Async controllers' CompletableFuture handlers, the async dispatch. */
    private ResultActions perform(RequestBuilder request) throws Exception {
        ResultActions result = mvc.perform(request);
        if (result.andReturn().getRequest().isAsyncStarted()) {
            result = mvc.perform(asyncDispatch(result.andReturn()));
        }
        return result;
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }

    private int firstBlogId() {
        return query("select b from Blog b order by b.blogId", Blog.class).get(0).getBlogId();
    }

    private int firstTagId() {
        return query("select t from Tag t order by t.tagId", Tag.class).get(0).getTagId();
    }

    private List<Post> allPosts() {
        return query("select p from Post p order by p.postId", Post.class);
    }

    private long count(String entity) {
        return inEm(em -> em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
    }

    private <T> List<T> query(String jpql, Class<T> type) {
        return inEm(em -> em.createQuery(jpql, type).getResultList());
    }

    private <T> T single(String jpql, Class<T> type) {
        return inEm(em -> em.createQuery(jpql, type).getSingleResult());
    }

    private <T> T find(Class<T> type, int id) {
        return inEm(em -> em.find(type, id));
    }

    private <R> R inEm(Function<EntityManager, R> work) {
        try (EntityManager em = emf.createEntityManager()) {
            return work.apply(em);
        }
    }
}
