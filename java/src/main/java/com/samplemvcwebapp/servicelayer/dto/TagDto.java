package com.samplemvcwebapp.servicelayer.dto;

import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tag details/create/edit form-backing object (legacy used the Tag entity directly via GenericServices).
 * Form field names: TagId, Name, Slug. Child A adds Bean Validation annotations / messages.
 */
public class TagDto {

    private int tagId;

    @NotBlank(message = ValidationMessages.TAG_NAME_REQUIRED)
    @Size(max = 128, message = ValidationMessages.TAG_NAME_MAX)
    private String name;

    @NotBlank(message = ValidationMessages.TAG_SLUG_REQUIRED)
    @Size(max = 64, message = ValidationMessages.TAG_SLUG_MAX)
    @Pattern(regexp = "[\\p{L}\\p{Mn}\\p{Nd}\\p{Pc}]*", message = ValidationMessages.TAG_SLUG_PATTERN)
    private String slug;

    public int getTagId() { return tagId; }
    public void setTagId(int tagId) { this.tagId = tagId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
