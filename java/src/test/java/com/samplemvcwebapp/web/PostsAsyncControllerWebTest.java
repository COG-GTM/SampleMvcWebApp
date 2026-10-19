package com.samplemvcwebapp.web;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.services.PostAsyncService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(PostsAsyncController.class)
class PostsAsyncControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean PostAsyncService service;
    @MockitoBean DataLayerInitialiser initialiser;

    @BeforeEach
    void setUp() {
        when(service.listPostsAsync()).thenReturn(completedFuture(WebTestData.posts()));
        when(service.getCreateSetupAsync()).thenReturn(completedFuture(WebTestData.setupDto()));
        when(service.resetDtoAsync(any())).thenAnswer(inv -> {
            DetailPostDto posted = inv.getArgument(0);
            DetailPostDto lists = WebTestData.setupDto();
            posted.getBloggers().setKeyValueList(lists.getBloggers().getKeyValueList());
            posted.getUserChosenTags().setKeyValueList(lists.getUserChosenTags().getKeyValueList());
            return completedFuture(posted);
        });
    }

    private ResultActions async(RequestBuilder builder) throws Exception {
        MvcResult started = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started));
    }

    @Test
    void indexIsUnfilteredAndRendersAsyncHeading() throws Exception {
        async(get("/PostsAsync/Index/0"))
                .andExpect(status().isOk())
                .andExpect(view().name("postsAsync/index"))
                .andExpect(content().string(containsString("<title>PostsAsync - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h2>Posts (async)</h2>")))
                .andExpect(content().string(containsString("Go to TagsAsync Page")))
                .andExpect(content().string(containsString("href=\"/PostsAsync/Details/7\"")))
                .andExpect(content().string(containsString("Delay for 500ms (async)")));
        async(get("/PostsAsync")).andExpect(view().name("postsAsync/index"));
    }

    @Test
    void detailsAndUnknownId() throws Exception {
        when(service.getDetailAsync(1)).thenReturn(completedFuture(Optional.of(WebTestData.detailDto())));
        when(service.getDetailAsync(9)).thenReturn(completedFuture(Optional.empty()));
        when(service.getUpdateSetupAsync(9)).thenReturn(completedFuture(Optional.empty()));
        async(get("/PostsAsync/Details/1"))
                .andExpect(view().name("postsAsync/details"))
                .andExpect(content().string(containsString("<h2>Details (Async)</h2>")))
                .andExpect(content().string(containsString("href=\"/PostsAsync/Edit/1\"")));
        async(get("/PostsAsync/Details/9")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
        async(get("/PostsAsync/Edit/9")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
    }

    @Test
    void createGetAndValidationRoundTrip() throws Exception {
        async(get("/PostsAsync/Create"))
                .andExpect(view().name("postsAsync/create"))
                .andExpect(content().string(containsString("<h2>Create (Async)</h2>")))
                .andExpect(content().string(containsString("--- choose blogger ---")));
        when(service.createAsync(any())).thenReturn(completedFuture(
                SuccessOrErrors.error("UserChosenTags", "You must select at least one tag for the post.")));
        async(post("/PostsAsync/Create").param("title", "Hello").param("content", "c").param("bloggers.selectedValue", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("postsAsync/create"))
                .andExpect(model().attributeHasFieldErrors("dto", "userChosenTags"))
                .andExpect(content().string(containsString("<span>You must select at least one tag for the post.</span>")))
                .andExpect(content().string(containsString("<option value=\"1\" selected=\"selected\">Ada Lovelace</option>")));
    }

    @Test
    void createPostSuccessRedirectsWithFlash() throws Exception {
        when(service.createAsync(any())).thenReturn(completedFuture(SuccessOrErrors.success("Successfully created Post.")));
        async(post("/PostsAsync/Create").param("title", "Hello").param("content", "c")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(redirectedUrl("/PostsAsync"))
                .andExpect(flash().attribute("message", "Successfully created Post."));
    }

    @Test
    void editGetAndPost() throws Exception {
        when(service.getUpdateSetupAsync(1)).thenReturn(completedFuture(Optional.of(WebTestData.detailDto())));
        async(get("/PostsAsync/Edit/1"))
                .andExpect(view().name("postsAsync/edit"))
                .andExpect(content().string(containsString("<h2>Edit (Async)</h2>")))
                .andExpect(content().string(containsString("name=\"postId\" value=\"1\"")));
        when(service.updateAsync(any())).thenReturn(completedFuture(SuccessOrErrors.success("Successfully updated Post.")));
        async(post("/PostsAsync/Edit/1").param("postId", "1").param("title", "T2").param("content", "c")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(redirectedUrl("/PostsAsync"))
                .andExpect(flash().attribute("message", "Successfully updated Post."));
        when(service.updateAsync(any())).thenReturn(completedFuture(SuccessOrErrors.error("Title", "The Title field is required.")));
        async(post("/PostsAsync/Edit/1").param("title", "").param("content", "c"))
                .andExpect(view().name("postsAsync/edit"))
                .andExpect(content().string(containsString("<span>The Title field is required.</span>")));
    }

    @Test
    void deleteResetNumPostsDelayCodeView() throws Exception {
        when(service.deleteAsync(3)).thenReturn(completedFuture(SuccessOrErrors.success("Successfully deleted Post.")));
        async(get("/PostsAsync/Delete/3"))
                .andExpect(redirectedUrl("/PostsAsync"))
                .andExpect(flash().attribute("message", "Successfully deleted Post."));
        when(service.deleteAsync(4)).thenReturn(completedFuture(SuccessOrErrors.error(null, "gone")));
        async(get("/PostsAsync/Delete/4")).andExpect(flash().attribute("errorMessage", List.of("gone")));

        mvc.perform(get("/PostsAsync/Reset"))
                .andExpect(redirectedUrl("/PostsAsync"))
                .andExpect(flash().attribute("message", "Successfully reset the blogs data"));
        verify(initialiser).resetBlogs(TestDataSelection.MEDIUM);

        when(service.getNumPostsMessageAsync()).thenReturn(completedFuture("The total number of Posts is 17"));
        async(get("/PostsAsync/NumPosts"))
                .andExpect(view().name("postsAsync/numPosts"))
                .andExpect(content().string(containsString("The total number of Posts is 17")));
        async(get("/PostsAsync/Delay"))
                .andExpect(view().name("postsAsync/delay"))
                .andExpect(content().string(containsString("<title>PostsAsync Delay - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("I delayed by 500 ms.")));
        mvc.perform(get("/PostsAsync/CodeView"))
                .andExpect(view().name("postsAsync/codeView"))
                .andExpect(content().string(containsString("PostsAsync: A explanation of the PostAsync code")));
    }
}
