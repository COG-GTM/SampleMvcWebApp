package com.samplemvcwebapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
class HomeControllerWebTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/|home/index|<title>Index - SampleMvcWebApp</title>|Welcome to the Sample MVC Web Application - basic version",
            "/Home|home/index|<title>Index - SampleMvcWebApp</title>|UPDATE for 2015",
            "/Home/Index|home/index|<title>Index - SampleMvcWebApp</title>|Where should I start?",
            "/Home/About|home/about|<title>About - SampleMvcWebApp</title>|About this site",
            "/Home/Contact|home/contact|<title>Contact - SampleMvcWebApp</title>|About the Author",
            "/Home/CodeView|home/codeView|<title>CodeView - SampleMvcWebApp</title>|Introduction to GenericServices"})
    void staticPages(String url, String viewName, String title, String text) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(containsString(text)));
    }

    @Test
    void layoutHasLegacyNavbarAndFooter() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Sync database<span class=\"caret\"></span>")))
                .andExpect(content().string(containsString("Async database<span class=\"caret\"></span>")))
                .andExpect(content().string(containsString("href=\"/Posts/Index/0\"")))
                .andExpect(content().string(containsString("href=\"/PostsAsync/Index/0\"")))
                .andExpect(content().string(containsString("href=\"/Content/bootstrap.css\"")))
                .andExpect(content().string(containsString("Hosted on LocalHost")));
    }

    @Test
    void internalsShowsJvmInfoWithoutTitle() throws Exception {
        mvc.perform(get("/Home/Internals"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/internals"))
                .andExpect(model().attributeExists("info"))
                .andExpect(content().string(containsString("<title> - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h4>InternalsInfo</h4>")))
                .andExpect(content().string(containsString("HeapMemoryUsedKbytes")));
    }

    @Test
    void errorPageIsDirectlyRoutable() throws Exception {
        mvc.perform(get("/Error"))
                .andExpect(status().isOk())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("<title>Error - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("An error occurred while processing your request.")));
    }

    @Test
    void internalLinksHonourContextPath() throws Exception {
        mvc.perform(get("/sample/Home/About").contextPath("/sample"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/sample/Home/CodeView\"")))
                .andExpect(content().string(containsString("href=\"/sample/Posts/Index/0\"")));
    }

    @Test
    void staticAssetsAreServed() throws Exception {
        mvc.perform(get("/Content/Site.css")).andExpect(status().isOk());
        mvc.perform(get("/Scripts/jquery-1.10.2.js")).andExpect(status().isOk());
        mvc.perform(get("/Scripts/jquery.validate.unobtrusive.js")).andExpect(status().isOk());
    }
}
