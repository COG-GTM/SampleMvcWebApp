package com.samplemvcwebapp.servicelayer.dto;

/**
 * Blog create/edit form-backing object (legacy used the Blog entity directly via GenericServices).
 * Form field names: BlogId, Name, EmailAddress. Child A adds Bean Validation annotations / messages.
 */
public class BlogDto {

    private int blogId;
    private String name;
    private String emailAddress;

    public int getBlogId() { return blogId; }
    public void setBlogId(int blogId) { this.blogId = blogId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
}
