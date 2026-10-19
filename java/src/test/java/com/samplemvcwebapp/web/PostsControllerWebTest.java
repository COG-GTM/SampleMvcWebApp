package com.samplemvcwebapp.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.services.PostService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostsController.class)
class PostsControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean PostService service;
    @MockitoBean DataLayerInitialiser initialiser;

    @BeforeEach
    void setUp() {
        when(service.listPosts(any())).thenReturn(WebTestData.posts());
        when(service.getCreateSetup()).thenReturn(WebTestData.setupDto());
        when(service.resetDto(any())).thenAnswer(inv -> {
            DetailPostDto posted = inv.getArgument(0);
            DetailPostDto lists = WebTestData.setupDto();
            posted.setBloggers(copySelection(lists, posted));
            posted.getUserChosenTags().setKeyValueList(lists.getUserChosenTags().getKeyValueList());
            return posted;
        });
    }

    private static com.samplemvcwebapp.servicelayer.ui.DropDownListType copySelection(DetailPostDto lists, DetailPostDto posted) {
        lists.getBloggers().setSelectedValue(posted.getBloggers().getSelectedValue());
        return lists.getBloggers();
    }

    @Test
    void indexListsPostsWithLegacyColumnsAndShortDate() throws Exception {
        mvc.perform(get("/Posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/index"))
                .andExpect(model().attribute("posts", WebTestData.posts()))
                .andExpect(content().string(containsString("<title>Posts - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h2>Posts</h2>")))
                .andExpect(content().string(containsString("BloggerName")))
                .andExpect(content().string(containsString("TagNames")))
                .andExpect(content().string(containsString("Programming, My Opinion")))
                .andExpect(content().string(containsString("10/19/2026")))
                .andExpect(content().string(containsString("href=\"/Posts/Edit/7\"")))
                .andExpect(content().string(containsString("href=\"/Posts/Details/7\"")))
                .andExpect(content().string(containsString("href=\"/Posts/Delete/7\"")))
                .andExpect(content().string(containsString("Create New Post")))
                .andExpect(content().string(containsString("Reset Blogs data")))
                .andExpect(content().string(containsString("Delay for 500ms (Sync)")))
                .andExpect(content().string(not(containsString("Filtered list"))));
        verify(service).listPosts(null);
    }

    @Test
    void indexWithZeroIdIsUnfiltered() throws Exception {
        mvc.perform(get("/Posts/Index/0")).andExpect(status().isOk()).andExpect(view().name("posts/index"))
                .andExpect(content().string(not(containsString("Filtered list"))));
        mvc.perform(get("/Posts/Index")).andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).listPosts(null);
    }

    @Test
    void indexWithBlogIdFiltersAndShowsMessage() throws Exception {
        mvc.perform(get("/Posts/Index/6"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", "Filtered list"))
                .andExpect(content().string(containsString("<div class=\"text-success\">Filtered list</div>")));
        verify(service).listPosts(6);
    }

    @Test
    void indexRendersFlashMessage() throws Exception {
        mvc.perform(get("/Posts").flashAttr("message", "Successfully reset the blogs data"))
                .andExpect(content().string(containsString("<div class=\"text-success\">Successfully reset the blogs data</div>")));
    }

    @Test
    void detailsShowsPost() throws Exception {
        when(service.getDetail(1)).thenReturn(Optional.of(WebTestData.detailDto()));
        mvc.perform(get("/Posts/Details/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/details"))
                .andExpect(content().string(containsString("<title>Details - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h4>DetailPostDto</h4>")))
                .andExpect(content().string(containsString("About Ada Lovelace")))
                .andExpect(content().string(containsString("10/19/2026")))
                .andExpect(content().string(containsString("href=\"/Posts/Edit/1\"")));
    }

    @Test
    void unknownIdShowsLegacyErrorPage() throws Exception {
        when(service.getDetail(999)).thenReturn(Optional.empty());
        when(service.getUpdateSetup(999)).thenReturn(Optional.empty());
        for (String url : List.of("/Posts/Details/999", "/Posts/Edit/999", "/Posts/Details/abc")) {
            mvc.perform(get(url))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name("error"))
                    .andExpect(content().string(containsString("<title>Error - SampleMvcWebApp</title>")))
                    .andExpect(content().string(containsString("An error occurred while processing your request.")));
        }
    }

    @Test
    void createGetShowsEmptyFormWithOptionLists() throws Exception {
        mvc.perform(get("/Posts/Create"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/create"))
                .andExpect(content().string(containsString("<title>Create - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h4>Post</h4>")))
                .andExpect(content().string(containsString(">Bloggers</label>")))
                .andExpect(content().string(containsString(">Tags</label>")))
                .andExpect(content().string(containsString("<option value=\"\">--- choose blogger ---</option>")))
                .andExpect(content().string(containsString("multiple=\"multiple\"")))
                .andExpect(content().string(containsString("data-val-required=\"The Title field is required.\"")))
                .andExpect(content().string(containsString("Post Validation rules")))
                .andExpect(content().string(containsString("/Scripts/jquery.validate.unobtrusive.js")));
    }


    @Test
    void createPostSuccessRedirectsWithFlash() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.success("Successfully created Post."));
        mvc.perform(post("/Posts/Create").param("title", "Hello").param("content", "World")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/Posts"))
                .andExpect(flash().attribute("message", "Successfully created Post."));
        verify(service).create(argThat(dto -> "Hello".equals(dto.getTitle())
                && Integer.valueOf(1).equals(dto.getBloggers().getSelectedValueAsInt())
                && dto.getUserChosenTags().getFinalSelection().equals(List.of(1, 3))));
    }

    @Test
    void createPostEmptyTitleRedisplaysWithFieldError() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.error("Title", "The Title field is required."));
        mvc.perform(post("/Posts/Create").param("title", "").param("content", "Some content.")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/create"))
                .andExpect(model().attributeHasFieldErrors("dto", "title"))
                .andExpect(content().string(containsString(
                        "<span class=\"field-validation-error text-danger\" data-valmsg-for=\"title\" data-valmsg-replace=\"true\"><span>The Title field is required.</span></span>")))
                .andExpect(content().string(containsString(">Some content.</textarea>")))
                .andExpect(content().string(containsString("<option value=\"1\" selected=\"selected\">Ada Lovelace</option>")))
                .andExpect(content().string(containsString("<option value=\"2\" selected=\"selected\">Programming</option>")));
        verify(service).resetDto(any());
    }

    @Test
    void createPostBangTitleShowsBusinessRuleMessage() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.error("Title",
                "Sorry, but you can't get too excited and include a ! in the title."));
        mvc.perform(post("/Posts/Create").param("title", "Hello!").param("content", "c")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sorry, but you can&#39;t get too excited and include a ! in the title.")))
                .andExpect(content().string(containsString("value=\"Hello!\"")));
    }

    @Test
    void createPostNoTagsAndNoBloggerShowFieldErrors() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.errors(List.of(
                new ValidationError("Bloggers", "The blogger was not selected. You must do that before the post can be saved."),
                new ValidationError("UserChosenTags", "You must select at least one tag for the post."))));
        mvc.perform(post("/Posts/Create").param("title", "Hello").param("content", "c").param("bloggers.selectedValue", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("dto", "bloggers", "userChosenTags"))
                .andExpect(content().string(containsString("data-valmsg-for=\"userChosenTags\" data-valmsg-replace=\"true\"><span>You must select at least one tag for the post.</span>")))
                .andExpect(content().string(containsString("<span>The blogger was not selected. You must do that before the post can be saved.</span>")));
    }

    @Test
    void createPostModelLevelErrorGoesToValidationSummary() throws Exception {
        when(service.create(any())).thenReturn(SuccessOrErrors.error(null, "Sorry. Not allowed to end a sentance with 'sheep'."));
        mvc.perform(post("/Posts/Create").param("title", "Farm").param("content", "I like sheep.")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("dto"))
                .andExpect(content().string(containsString(
                        "<div class=\"validation-summary-errors text-danger\"><ul><li>Sorry. Not allowed to end a sentance with &#39;sheep&#39;.</li></ul></div>")));
    }

    @Test
    void createPostBindingErrorRedisplaysWithoutCallingCreate() throws Exception {
        mvc.perform(post("/Posts/Create").param("title", "x").param("userChosenTags.finalSelection", "notAnInt"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/create"));
        verify(service, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void editGetShowsPreselectedForm() throws Exception {
        when(service.getUpdateSetup(1)).thenReturn(Optional.of(WebTestData.detailDto()));
        mvc.perform(get("/Posts/Edit/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/edit"))
                .andExpect(content().string(containsString("<title>Edit - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<h4>DetailPostDto</h4>")))
                .andExpect(content().string(containsString("name=\"postId\" value=\"1\"")))
                .andExpect(content().string(containsString("value=\"About Ada Lovelace\"")))
                .andExpect(content().string(containsString("<option value=\"1\" selected=\"selected\">Ada Lovelace</option>")))
                .andExpect(content().string(containsString("<option value=\"1\" selected=\"selected\">About Me</option>")))
                .andExpect(content().string(containsString("value=\"Save\"")));
    }

    @Test
    void editPostSuccessRedirectsWithFlash() throws Exception {
        when(service.update(any())).thenReturn(SuccessOrErrors.success("Successfully updated Post."));
        mvc.perform(post("/Posts/Edit/1").param("postId", "1").param("title", "New").param("content", "c")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(redirectedUrl("/Posts"))
                .andExpect(flash().attribute("message", "Successfully updated Post."));
        verify(service).update(argThat(dto -> dto.getPostId() == 1 && "New".equals(dto.getTitle())));
    }

    @Test
    void editPostErrorRedisplaysEditView() throws Exception {
        when(service.update(any())).thenReturn(SuccessOrErrors.error("Title",
                "Sorry, but you can't ask a question, i.e. the title can't end with '?'."));
        mvc.perform(post("/Posts/Edit/1").param("title", "Why?").param("content", "c")
                        .param("bloggers.selectedValue", "1").param("userChosenTags.finalSelection", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/edit"))
                .andExpect(content().string(containsString("the title can&#39;t end with &#39;?&#39;.")))
                .andExpect(content().string(containsString("name=\"postId\" value=\"1\"")));
    }

    @Test
    void deleteRedirectsWithSuccessOrErrorFlash() throws Exception {
        when(service.delete(3)).thenReturn(SuccessOrErrors.success("Successfully deleted Post."));
        mvc.perform(get("/Posts/Delete/3"))
                .andExpect(redirectedUrl("/Posts"))
                .andExpect(flash().attribute("message", "Successfully deleted Post."));
        when(service.delete(4)).thenReturn(SuccessOrErrors.error(null, "Could not find the post."));
        mvc.perform(get("/Posts/Delete/4"))
                .andExpect(redirectedUrl("/Posts"))
                .andExpect(flash().attribute("errorMessage", List.of("Could not find the post.")));
        mvc.perform(get("/Posts").flashAttr("errorMessage", List.of("Could not find the post.")))
                .andExpect(content().string(containsString("<div class=\"text-danger\"><strong><span>Could not find the post.</span>")));
    }

    @Test
    void resetSeedsMediumAndRedirects() throws Exception {
        mvc.perform(get("/Posts/Reset"))
                .andExpect(redirectedUrl("/Posts"))
                .andExpect(flash().attribute("message", "Successfully reset the blogs data"));
        verify(initialiser).resetBlogs(TestDataSelection.MEDIUM);
    }

    @Test
    void numPostsDelayAndCodeView() throws Exception {
        when(service.getNumPostsMessage()).thenReturn("The total number of Posts is 17");
        mvc.perform(get("/Posts/NumPosts"))
                .andExpect(view().name("posts/numPosts"))
                .andExpect(content().string(containsString("<title> - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("<p>The total number of Posts is 17</p>")));
        long start = System.nanoTime();
        mvc.perform(get("/Posts/Delay"))
                .andExpect(view().name("posts/delay"))
                .andExpect(content().string(containsString("<title>Posts Delay - SampleMvcWebApp</title>")))
                .andExpect(content().string(containsString("I delayed by 500 ms.")));
        org.assertj.core.api.Assertions.assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(500_000_000L);
        mvc.perform(get("/Posts/CodeView"))
                .andExpect(view().name("posts/codeView"))
                .andExpect(content().string(containsString("Posts: An explanation of Post code")));
    }
}
