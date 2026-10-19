package com.samplemvcwebapp.datalayer.entities;

import com.samplemvcwebapp.datalayer.validation.MinLength;
import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** DataLayer.DataClasses.Concrete.Post, including the IValidatableObject business rules (see {@link #validate()}). */
@Entity
@Table(name = "Posts")
public class Post extends TrackUpdate {

    private static final String[] BANNED_SENTENCE_ENDINGS = {"sheep", "lamb", "cow", "calf"};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PostId")
    private Integer postId;

    @NotBlank(message = ValidationMessages.TITLE_REQUIRED)
    @MinLength(value = 2, message = ValidationMessages.TITLE_MIN)
    @Size(max = 128, message = ValidationMessages.TITLE_MAX)
    @Column(name = "Title", length = 128, nullable = false)
    private String title;

    @NotBlank(message = ValidationMessages.CONTENT_REQUIRED)
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

    public Post() {
    }

    public Post(Blog blogger, String title, String content) {
        this.blogger = blogger;
        this.title = title;
        this.content = content;
    }

    /**
     * Legacy Post.Validate(ValidationContext). EF only calls it once the DataAnnotations (here Bean Validation)
     * pass, so callers should check those first.
     */
    public List<RuleViolation> validate() {
        List<RuleViolation> result = new ArrayList<>();
        if (tags != null && tags.isEmpty())
            result.add(new RuleViolation("AllocatedTags", ValidationMessages.POST_NEEDS_TAG));
        String t = title == null ? "" : title;
        if (t.contains("!"))
            result.add(new RuleViolation("Title", ValidationMessages.TITLE_NO_BANG));
        if (t.endsWith("?"))
            result.add(new RuleViolation("Title", ValidationMessages.TITLE_NO_QUESTION));
        String c = content == null ? "" : content;
        for (String word : BANNED_SENTENCE_ENDINGS) {
            if (c.contains(" " + word + "."))
                result.add(new RuleViolation(null, ValidationMessages.sentenceEnding(word)));
        }
        return result;
    }

    /** Tags in TagId order. */
    public List<Tag> getTagsInIdOrder() {
        return tags.stream()
                .sorted(Comparator.comparing(Tag::getTagId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Legacy DTO TagNames: tag names in TagId order joined with ", ". */
    public String getTagNames() {
        return getTagsInIdOrder().stream().map(Tag::getName).collect(Collectors.joining(", "));
    }

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

    @Override
    public String toString() {
        return "PostId: " + postId + ", Title: " + title;
    }
}
