package com.samplemvcwebapp.datalayer.entities;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

/** Contract shape of DataLayer.DataClasses.Concrete.Post. Child A owns validation annotations and behaviour. */
@Entity
@Table(name = "Posts")
public class Post extends TrackUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PostId")
    private Integer postId;

    @Column(name = "Title", length = 128, nullable = false)
    private String title;

    @Lob
    @Column(name = "Content", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BlogId", nullable = false)
    private Blog blogger;

    @ManyToMany
    @JoinTable(name = "TagPosts",
            joinColumns = @JoinColumn(name = "Post_PostId"),
            inverseJoinColumns = @JoinColumn(name = "Tag_TagId"))
    @OrderBy("tagId ASC")
    private Set<Tag> tags = new LinkedHashSet<>();

    public Integer getPostId() { return postId; }
    public void setPostId(Integer postId) { this.postId = postId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Blog getBlogger() { return blogger; }
    public void setBlogger(Blog blogger) { this.blogger = blogger; }
    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }
}
