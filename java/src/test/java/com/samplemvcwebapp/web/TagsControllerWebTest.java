package com.samplemvcwebapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.services.TagService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TagsController.class)
class TagsControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean TagService service;

    @Test
    void indexListsTags() throws Exception {
        when(service.listTags()).thenReturn(WebTestData.tags());
        mvc.perform(get("/Tags"))
                .andExpect(status().isOk())
                .andExpect(view().name("tags/index"))
                .andExpect(content().string(containsString("<title>Tags - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h2>Tags</h2>")))
                .andExpect(content().string(containsString("Num Posts Used In")))
                .andExpect(content().string(containsString("<td>programming</td>")))
                .andExpect(content().string(containsString("Create New Tag")))
                .andExpect(content().string(containsString("href=\"/Tags/Delete/2\"")));
        mvc.perform(get("/Tags/Index")).andExpect(view().name("tags/index"));
    }

    @Test
    void detailsAndUnknownId() throws Exception {
        when(service.getDetail(1)).thenReturn(Optional.of(WebTestData.tag()));
        when(service.getDetail(99)).thenReturn(Optional.empty());
        mvc.perform(get("/Tags/Details/1"))
                .andExpect(view().name("tags/details"))
                .andExpect(content().string(containsString("<title>Details - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h4>Tag</h4>")))
                .andExpect(content().string(containsString("<dd>about</dd>")));
        mvc.perform(get("/Tags/Details/99")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
        mvc.perform(get("/Tags/Edit/99")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
    }

    @Test
    void createGetShowsEmptyForm() throws Exception {
        mvc.perform(get("/Tags/Create"))
                .andExpect(view().name("tags/create"))
                .andExpect(content().string(containsString("<title>Create - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString(">Name</label>")))
                .andExpect(content().string(containsString(">Slug</label>")))
                .andExpect(content().string(containsString("Tag Validation rules")));
    }

    @Test
    void createPostSuccess() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.success("Successfully created Tag."));
        mvc.perform(post("/Tags/Create").param("name", "New tag").param("slug", "newtag"))
                .andExpect(redirectedUrl("/Tags"))
                .andExpect(flash().attribute("message", "Successfully created Tag."));
        verify(service).create(argThat(t -> "newtag".equals(t.getSlug())));
        mvc.perform(get("/Tags").flashAttr("message", "Successfully created Tag."))
                .andExpect(content().string(containsString("<div class=\"text-success\">Successfully created Tag.</div>")));
    }

    @Test
    void createPostDuplicateSlug() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.error("Slug",
                "The Slug on tag 'Duplicate' must be unique and is already being used."));
        mvc.perform(post("/Tags/Create").param("name", "Duplicate").param("slug", "programming"))
                .andExpect(status().isOk())
                .andExpect(view().name("tags/create"))
                .andExpect(model().attributeHasFieldErrors("tag", "slug"))
                .andExpect(content().string(containsString("The Slug on tag &#39;Duplicate&#39; must be unique and is already being used.")))
                .andExpect(content().string(containsString("value=\"programming\"")));
    }

    @Test
    void createPostBadSlug() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.error("Slug",
                "The slug must not contain spaces or non-alphanumeric characters."));
        mvc.perform(post("/Tags/Create").param("name", "Bad").param("slug", "bad slug"))
                .andExpect(view().name("tags/create"))
                .andExpect(content().string(containsString("<span>The slug must not contain spaces or non-alphanumeric characters.</span>")));
    }

    @Test
    void editGetAndPost() throws Exception {
        when(service.getDetail(1)).thenReturn(Optional.of(WebTestData.tag()));
        mvc.perform(get("/Tags/Edit/1"))
                .andExpect(view().name("tags/edit"))
                .andExpect(content().string(containsString("<title>Edit - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("name=\"tagId\" value=\"1\"")))
                .andExpect(content().string(containsString("value=\"About Me\"")));
        when(service.update(any())).thenReturn(SuccessOrErrors.success("Successfully updated Tag."));
        mvc.perform(post("/Tags/Edit/1").param("tagId", "1").param("name", "About").param("slug", "about"))
                .andExpect(redirectedUrl("/Tags"))
                .andExpect(flash().attribute("message", "Successfully updated Tag."));
        when(service.update(any())).thenReturn(SuccessOrErrors.error("Name", "The Name field is required."));
        mvc.perform(post("/Tags/Edit/1").param("name", "").param("slug", "about"))
                .andExpect(view().name("tags/edit"))
                .andExpect(content().string(containsString("<span>The Name field is required.</span>")));
    }

    @Test
    void deleteAndCodeView() throws Exception {
        when(service.delete(1)).thenReturn(SuccessOrErrors.success("Successfully deleted Tag."));
        mvc.perform(get("/Tags/Delete/1"))
                .andExpect(redirectedUrl("/Tags"))
                .andExpect(flash().attribute("message", "Successfully deleted Tag."));
        when(service.delete(2)).thenReturn(SuccessOrErrors.error(null, "Not found"));
        mvc.perform(get("/Tags/Delete/2")).andExpect(flash().attribute("errorMessage", List.of("Not found")));
        mvc.perform(get("/Tags/CodeView"))
                .andExpect(view().name("tags/codeView"))
                .andExpect(content().string(containsString("Tags: A explanation of Tags code")));
    }
}
