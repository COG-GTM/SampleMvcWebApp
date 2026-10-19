package com.samplemvcwebapp.datalayer.entities;

import com.samplemvcwebapp.datalayer.validation.MinLength;
import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/** DataLayer.DataClasses.Concrete.Blog. Deleting a blog deletes its posts (EF cascade on the required BlogId FK). */
@Entity
@Table(name = "Blogs")
public class Blog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BlogId")
    private Integer blogId;

    @NotBlank(message = ValidationMessages.BLOG_NAME_REQUIRED)
    @MinLength(value = 2, message = ValidationMessages.BLOG_NAME_MIN)
    @Size(max = 64, message = ValidationMessages.BLOG_NAME_MAX)
    @Column(name = "Name", length = 64, nullable = false)
    private String name;

    @NotBlank(message = ValidationMessages.BLOG_EMAIL_REQUIRED)
    @Size(max = 256, message = ValidationMessages.BLOG_EMAIL_MAX)
    @Email(message = ValidationMessages.BLOG_EMAIL_FORMAT)
    @Column(name = "EmailAddress", length = 256, nullable = false)
    private String emailAddress;

    @OneToMany(mappedBy = "blogger", cascade = CascadeType.ALL)
    @OrderBy("postId ASC")
    private List<Post> posts = new ArrayList<>();

    public Blog() {
    }

    public Blog(String name, String emailAddress) {
        this.name = name;
        this.emailAddress = emailAddress;
    }

    public Integer getBlogId() { return blogId; }
    public void setBlogId(Integer blogId) { this.blogId = blogId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    public List<Post> getPosts() { return posts; }
    public void setPosts(List<Post> posts) { this.posts = posts; }

    @Override
    public String toString() {
        return "BlogId: " + blogId + ", Name: " + name + ", EmailAddress: " + emailAddress;
    }
}
