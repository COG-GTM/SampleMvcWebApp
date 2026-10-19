package com.samplemvcwebapp.web;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.services.TagAsyncService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(TagsAsyncController.class)
class TagsAsyncControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TagAsyncService service;

    private ResultActions async(RequestBuilder builder) throws Exception {
        MvcResult started = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started));
    }

    @Test
    void indexListsTags() throws Exception {
        when(service.listTagsAsync()).thenReturn(completedFuture(WebTestData.tags()));
        async(get("/TagsAsync"))
                .andExpect(status().isOk())
                .andExpect(view().name("tagsAsync/index"))
                .andExpect(content().string(containsString("<title>TagsAsync - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h2>Tags (Async)</h2>")))
                .andExpect(content().string(containsString("Return To PostsAsync")))
                .andExpect(content().string(containsString("href=\"/TagsAsync/Edit/2\"")));
        async(get("/TagsAsync/Index")).andExpect(view().name("tagsAsync/index"));
    }

    @Test
    void detailsEditAndUnknownId() throws Exception {
        when(service.getDetailAsync(1)).thenReturn(completedFuture(Optional.of(WebTestData.tag())));
        when(service.getDetailAsync(9)).thenReturn(completedFuture(Optional.empty()));
        async(get("/TagsAsync/Details/1"))
                .andExpect(view().name("tagsAsync/details"))
                .andExpect(content().string(containsString("<h2>Details (Async)</h2>")));
        async(get("/TagsAsync/Edit/1"))
                .andExpect(view().name("tagsAsync/edit"))
                .andExpect(content().string(containsString("<h2>Edit (Async)</h2>")))
                .andExpect(content().string(containsString("name=\"tagId\" value=\"1\"")));
        async(get("/TagsAsync/Details/9")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
    }

    @Test
    void createRoundTrips() throws Exception {
        mvc.perform(get("/TagsAsync/Create"))
                .andExpect(view().name("tagsAsync/create"))
                .andExpect(content().string(containsString("<h2>Create (Async)</h2>")));
        when(service.createAsync(any())).thenReturn(completedFuture(SuccessOrErrors.error("Slug",
                "The Slug on tag 'Duplicate' must be unique and is already being used.")));
        async(post("/TagsAsync/Create").param("name", "Duplicate").param("slug", "programming"))
                .andExpect(status().isOk())
                .andExpect(view().name("tagsAsync/create"))
                .andExpect(model().attributeHasFieldErrors("tag", "slug"))
                .andExpect(content().string(containsString("The Slug on tag &#39;Duplicate&#39; must be unique and is already being used.")));
        when(service.createAsync(any())).thenReturn(completedFuture(SuccessOrErrors.success("Successfully created Tag.")));
        async(post("/TagsAsync/Create").param("name", "Ok").param("slug", "ok"))
                .andExpect(redirectedUrl("/TagsAsync"))
                .andExpect(flash().attribute("message", "Successfully created Tag."));
    }

    @Test
    void editPostDeleteAndCodeView() throws Exception {
        when(service.updateAsync(any())).thenReturn(completedFuture(SuccessOrErrors.success("Successfully updated Tag.")));
        async(post("/TagsAsync/Edit/1").param("tagId", "1").param("name", "A").param("slug", "a"))
                .andExpect(redirectedUrl("/TagsAsync"))
                .andExpect(flash().attribute("message", "Successfully updated Tag."));
        when(service.deleteAsync(1)).thenReturn(completedFuture(SuccessOrErrors.success("Successfully deleted Tag.")));
        async(get("/TagsAsync/Delete/1"))
                .andExpect(redirectedUrl("/TagsAsync"))
                .andExpect(flash().attribute("message", "Successfully deleted Tag."));
        mvc.perform(get("/TagsAsync/CodeView"))
                .andExpect(view().name("tagsAsync/codeView"))
                .andExpect(content().string(containsString("Tags: A explanation of Tags code")));
    }
}
