package com.samplemvcwebapp.datalayer.entities;

import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * DataLayer.DataClasses.Concrete.Tag. Slug uniqueness is a service-level check (legacy SampleWebAppDb.ValidateEntity).
 * Deleting a tag removes its TagPosts join rows (EF many-to-many behaviour).
 */
@Entity
@Table(name = "Tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TagId")
    private Integer tagId;

    @NotBlank(message = ValidationMessages.TAG_SLUG_REQUIRED)
    @Size(max = 64, message = ValidationMessages.TAG_SLUG_MAX)
    @Pattern(regexp = "[\\p{L}\\p{Mn}\\p{Nd}\\p{Pc}]*", message = ValidationMessages.TAG_SLUG_PATTERN)
    @Column(name = "Slug", length = 64, nullable = false)
    private String slug;

    @NotBlank(message = ValidationMessages.TAG_NAME_REQUIRED)
    @Size(max = 128, message = ValidationMessages.TAG_NAME_MAX)
    @Column(name = "Name", length = 128, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Post> posts = new LinkedHashSet<>();

    public Tag() {
    }

    public Tag(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    @PreRemove
    void removeFromPosts() {
        for (Post post : new ArrayList<>(posts)) {
            post.getTags().remove(this);
        }
        posts.clear();
    }

    public Integer getTagId() { return tagId; }
    public void setTagId(Integer tagId) { this.tagId = tagId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Post> getPosts() { return posts; }
    public void setPosts(Set<Post> posts) { this.posts = posts; }

    @Override
    public String toString() {
        return "TagId: " + tagId + ", Name: " + name + ", Slug: " + slug;
    }
}
