package com.samplemvcwebapp.servicelayer.dto;

/**
 * Tag details/create/edit form-backing object (legacy used the Tag entity directly via GenericServices).
 * Form field names: TagId, Name, Slug. Child A adds Bean Validation annotations / messages.
 */
public class TagDto {

    private int tagId;
    private String name;
    private String slug;

    public int getTagId() { return tagId; }
    public void setTagId(int tagId) { this.tagId = tagId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
