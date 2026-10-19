package com.samplemvcwebapp.datalayer.entities;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** Contract shape of DataLayer.DataClasses.Concrete.Blog. Child A owns validation annotations and behaviour. */
@Entity
@Table(name = "Blogs")
public class Blog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BlogId")
    private Integer blogId;

    @Column(name = "Name", length = 64, nullable = false)
    private String name;

    @Column(name = "EmailAddress", length = 256, nullable = false)
    private String emailAddress;

    @OneToMany(mappedBy = "blogger", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("postId ASC")
    private List<Post> posts = new ArrayList<>();

    public Integer getBlogId() { return blogId; }
    public void setBlogId(Integer blogId) { this.blogId = blogId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    public List<Post> getPosts() { return posts; }
    public void setPosts(List<Post> posts) { this.posts = posts; }
}
