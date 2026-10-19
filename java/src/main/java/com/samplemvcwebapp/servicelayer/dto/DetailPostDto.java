package com.samplemvcwebapp.servicelayer.dto;

import com.samplemvcwebapp.datalayer.validation.MinLength;
import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import com.samplemvcwebapp.servicelayer.ui.DropDownListType;
import com.samplemvcwebapp.servicelayer.ui.MultiSelectListType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Mirrors ServiceLayer.PostServices.DetailPostDto (Posts details/create/edit form-backing object).
 * Form field names (binding contract with the web layer): PostId, Title, Content, Bloggers.SelectedValue,
 * UserChosenTags.FinalSelection. Child A adds the Bean Validation annotations / messages listed in JAVA_MIGRATION_NOTES.md.
 */
public class DetailPostDto {

    private int postId;

    @NotBlank(message = ValidationMessages.TITLE_REQUIRED)
    @MinLength(value = 2, message = ValidationMessages.TITLE_MIN)
    @Size(max = 128, message = ValidationMessages.TITLE_MAX)
    private String title;

    @NotBlank(message = ValidationMessages.CONTENT_REQUIRED)
    private String content;

    private String bloggerName;
    private String tagNames;
    private LocalDateTime lastUpdated;
    private DropDownListType bloggers = new DropDownListType();
    private MultiSelectListType userChosenTags = new MultiSelectListType();

    public int getPostId() { return postId; }
    public void setPostId(int postId) { this.postId = postId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBloggerName() { return bloggerName; }
    public void setBloggerName(String bloggerName) { this.bloggerName = bloggerName; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public DropDownListType getBloggers() { return bloggers; }
    public void setBloggers(DropDownListType bloggers) { this.bloggers = bloggers; }
    public MultiSelectListType getUserChosenTags() { return userChosenTags; }
    public void setUserChosenTags(MultiSelectListType userChosenTags) { this.userChosenTags = userChosenTags; }
}
