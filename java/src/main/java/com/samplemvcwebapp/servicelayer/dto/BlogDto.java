package com.samplemvcwebapp.servicelayer.dto;

import com.samplemvcwebapp.datalayer.validation.MinLength;
import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Blog create/edit form-backing object (legacy used the Blog entity directly via GenericServices).
 * Form field names: BlogId, Name, EmailAddress. Child A adds Bean Validation annotations / messages.
 */
public class BlogDto {

    private int blogId;

    @NotBlank(message = ValidationMessages.BLOG_NAME_REQUIRED)
    @MinLength(value = 2, message = ValidationMessages.BLOG_NAME_MIN)
    @Size(max = 64, message = ValidationMessages.BLOG_NAME_MAX)
    private String name;

    @NotBlank(message = ValidationMessages.BLOG_EMAIL_REQUIRED)
    @Size(max = 256, message = ValidationMessages.BLOG_EMAIL_MAX)
    @Email(message = ValidationMessages.BLOG_EMAIL_FORMAT)
    private String emailAddress;

    public int getBlogId() { return blogId; }
    public void setBlogId(int blogId) { this.blogId = blogId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
}
