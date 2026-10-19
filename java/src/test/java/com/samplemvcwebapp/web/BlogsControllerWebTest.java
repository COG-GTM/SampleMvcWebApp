package com.samplemvcwebapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.BlogDto;
import com.samplemvcwebapp.servicelayer.services.BlogService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BlogsController.class)
class BlogsControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean BlogService service;

    @Test
    void indexListsBlogsWithoutTitle() throws Exception {
        when(service.listBlogs()).thenReturn(WebTestData.blogs());
        mvc.perform(get("/Blogs"))
                .andExpect(status().isOk())
                .andExpect(view().name("blogs/index"))
                .andExpect(content().string(containsString("<title> - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h2>Blogs</h2>")))
                .andExpect(content().string(containsString("Num Posts this Author")))
                .andExpect(content().string(containsString("<td>sherlock@nospam.com</td>")))
                .andExpect(content().string(containsString("Create New Blogger")))
                .andExpect(content().string(containsString("href=\"/Posts/Index/2\"")));
        mvc.perform(get("/Blogs/Index")).andExpect(view().name("blogs/index"));
    }

    @Test
    void createGetHasEmailInput() throws Exception {
        mvc.perform(get("/Blogs/Create"))
                .andExpect(view().name("blogs/create"))
                .andExpect(content().string(containsString("<h4>Blog</h4>")))
                .andExpect(content().string(containsString(">EmailAddress</label>")))
                .andExpect(content().string(containsString("type=\"email\"")));
    }

    @Test
    void createPostSuccessAndErrors() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.success("Successfully created Blog."));
        mvc.perform(post("/Blogs/Create").param("name", "New").param("emailAddress", "new@nospam.com"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully created Blog."));
        when(service.create(any())).thenReturn(SuccessOrErrors.errors(List.of(
                new ValidationError("Name", "The field Name must be a string or array type with a minimum length of '2'."),
                new ValidationError("EmailAddress", "The EmailAddress field is not a valid e-mail address."))));
        mvc.perform(post("/Blogs/Create").param("name", "X").param("emailAddress", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("blogs/create"))
                .andExpect(model().attributeHasFieldErrors("blog", "name", "emailAddress"))
                .andExpect(content().string(containsString("minimum length of &#39;2&#39;.")))
                .andExpect(content().string(containsString("<span>The EmailAddress field is not a valid e-mail address.</span>")));
    }

    @Test
    void editGetPostAndUnknownId() throws Exception {
        BlogDto blog = new BlogDto();
        blog.setBlogId(1);
        blog.setName("Ada Lovelace");
        blog.setEmailAddress("ada@nospam.com");
        when(service.getDetail(1)).thenReturn(Optional.of(blog));
        when(service.getDetail(9)).thenReturn(Optional.empty());
        mvc.perform(get("/Blogs/Edit/1"))
                .andExpect(view().name("blogs/edit"))
                .andExpect(content().string(containsString("name=\"blogId\" value=\"1\"")))
                .andExpect(content().string(containsString("value=\"ada@nospam.com\"")));
        mvc.perform(get("/Blogs/Edit/9")).andExpect(status().isInternalServerError()).andExpect(view().name("error"));
        when(service.update(any())).thenReturn(SuccessOrErrors.success("Successfully updated Blog."));
        mvc.perform(post("/Blogs/Edit/1").param("blogId", "1").param("name", "Ada").param("emailAddress", "ada@nospam.com"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully updated Blog."));
        when(service.update(any())).thenReturn(SuccessOrErrors.error("EmailAddress", "The EmailAddress field is required."));
        mvc.perform(post("/Blogs/Edit/1").param("name", "Ada").param("emailAddress", ""))
                .andExpect(view().name("blogs/edit"))
                .andExpect(content().string(containsString("<span>The EmailAddress field is required.</span>")));
    }

    @Test
    void deleteFlashesOnlyOnSuccess() throws Exception {
        when(service.delete(1)).thenReturn(SuccessOrErrors.success("Successfully deleted Blog."));
        mvc.perform(get("/Blogs/Delete/1"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attribute("message", "Successfully deleted Blog."));
        when(service.delete(2)).thenReturn(SuccessOrErrors.error(null, "nope"));
        mvc.perform(get("/Blogs/Delete/2"))
                .andExpect(redirectedUrl("/Blogs"))
                .andExpect(flash().attributeCount(0));
    }
}
