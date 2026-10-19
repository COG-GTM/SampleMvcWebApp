package com.samplemvcwebapp.datalayer.entities;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

/** Contract shape of DataLayer.DataClasses.Concrete.Tag. Child A owns validation annotations and behaviour. */
@Entity
@Table(name = "Tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TagId")
    private Integer tagId;

    @Column(name = "Slug", length = 64, nullable = false)
    private String slug;

    @Column(name = "Name", length = 128, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Post> posts = new LinkedHashSet<>();

    public Integer getTagId() { return tagId; }
    public void setTagId(Integer tagId) { this.tagId = tagId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Post> getPosts() { return posts; }
    public void setPosts(Set<Post> posts) { this.posts = posts; }
}
