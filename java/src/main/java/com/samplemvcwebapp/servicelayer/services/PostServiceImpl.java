package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.entities.Post;
import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.datalayer.repositories.PostRepository;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import com.samplemvcwebapp.servicelayer.BeanValidation;
import com.samplemvcwebapp.servicelayer.ServiceMessages;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import com.samplemvcwebapp.servicelayer.ui.KeyValue;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy PostsController services: SimplePostDto list + DetailPostDto (SetupSecondaryData, SetupRestOfDto) and
 * the Post entity validation (DataAnnotations then IValidatableObject) that EF ran on SaveChanges.
 */
@Service
@Transactional
public class PostServiceImpl implements PostService {

    private static final String ITEM = "Post";

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final TagRepository tagRepository;
    private final BeanValidation beanValidation;

    public PostServiceImpl(PostRepository postRepository, BlogRepository blogRepository,
                           TagRepository tagRepository, BeanValidation beanValidation) {
        this.postRepository = postRepository;
        this.blogRepository = blogRepository;
        this.tagRepository = tagRepository;
        this.beanValidation = beanValidation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimplePostDto> listPosts(Integer blogId) {
        List<Post> posts = (blogId == null || blogId == 0)
                ? postRepository.findAllByOrderByPostIdAsc()
                : postRepository.findByBlogger_BlogIdOrderByPostIdAsc(blogId);
        return posts.stream()
                .map(p -> new SimplePostDto(p.getPostId(), p.getBlogger().getBlogId(), p.getBlogger().getName(),
                        p.getTitle(), p.getTagNames(), toLocal(p.getLastUpdated())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetailPostDto> getDetail(int postId) {
        return postRepository.findWithBloggerAndTagsByPostId(postId).map(PostServiceImpl::toDetailDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DetailPostDto getCreateSetup() {
        return resetDto(new DetailPostDto());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetailPostDto> getUpdateSetup(int postId) {
        return getDetail(postId).map(this::resetDto);
    }

    /** Legacy SetupSecondaryData: option lists in id order; the DTO's current selections are kept. */
    @Override
    @Transactional(readOnly = true)
    public DetailPostDto resetDto(DetailPostDto dto) {
        dto.getBloggers().setKeyValueList(blogRepository.findAllByOrderByBlogIdAsc().stream()
                .map(b -> new KeyValue(b.getName(), String.valueOf(b.getBlogId())))
                .collect(Collectors.toCollection(ArrayList::new)));
        dto.getBloggers().setPromptString(ServiceMessages.CHOOSE_BLOGGER_PROMPT);
        dto.getUserChosenTags().setKeyValueList(tagRepository.findAllByOrderByTagIdAsc().stream()
                .map(t -> new KeyValue(t.getName(), String.valueOf(t.getTagId())))
                .collect(Collectors.toCollection(ArrayList::new)));
        if (dto.getUserChosenTags().getFinalSelection() == null)
            dto.getUserChosenTags().setFinalSelection(new ArrayList<>());
        return dto;
    }

    @Override
    public SuccessOrErrors create(DetailPostDto dto) {
        return save(dto, null);
    }

    @Override
    public SuccessOrErrors update(DetailPostDto dto) {
        Optional<Post> found = postRepository.findWithBloggerAndTagsByPostId(dto.getPostId());
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        return save(dto, found.get());
    }

    @Override
    public SuccessOrErrors delete(int postId) {
        Optional<Post> found = postRepository.findById(postId);
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        Post post = found.get();
        post.getBlogger().getPosts().remove(post);
        postRepository.delete(post);
        return SuccessOrErrors.success(ServiceMessages.deleted(ITEM));
    }

    @Override
    @Transactional(readOnly = true)
    public String getNumPostsMessage() {
        return "The total number of Posts is " + postRepository.count();
    }

    private SuccessOrErrors save(DetailPostDto dto, Post existing) {
        List<ValidationError> errors = beanValidation.validate(dto);
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);

        // DetailPostDto.SetupRestOfDto: blogger and tags
        errors = new ArrayList<>();
        Blog blogger = null;
        Integer blogId = dto.getBloggers().getSelectedValueAsInt();
        if (blogId == null)
            errors.add(new ValidationError("Bloggers", ServiceMessages.BLOGGER_NOT_SELECTED));
        else if ((blogger = blogRepository.findById(blogId).orElse(null)) == null)
            errors.add(new ValidationError("Bloggers", ServiceMessages.BLOGGER_NOT_FOUND));
        List<Integer> tagIds = dto.getUserChosenTags().getFinalSelection() == null ? List.of()
                : dto.getUserChosenTags().getFinalSelection().stream().filter(Objects::nonNull).distinct().toList();
        List<Tag> tags = List.of();
        if (tagIds.isEmpty())
            errors.add(new ValidationError("UserChosenTags", ServiceMessages.NO_TAG_SELECTED));
        else if ((tags = tagRepository.findByTagIdIn(tagIds)).size() != tagIds.size())
            errors.add(new ValidationError("UserChosenTags", ServiceMessages.TAG_NOT_FOUND));
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);

        // Entity validation EF ran on SaveChanges, checked on an unmanaged candidate so nothing is flushed on failure
        Post candidate = new Post(blogger, dto.getTitle(), dto.getContent());
        candidate.setTags(new LinkedHashSet<>(tags));
        errors = beanValidation.validate(candidate);
        if (errors.isEmpty()) errors = BeanValidation.toErrors(candidate.validate());
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);

        if (existing == null) {
            blogger.getPosts().add(candidate);
            postRepository.save(candidate);
            dto.setPostId(candidate.getPostId());
            return SuccessOrErrors.success(ServiceMessages.created(ITEM));
        }
        if (!blogger.equals(existing.getBlogger())) {
            existing.getBlogger().getPosts().remove(existing);
            blogger.getPosts().add(existing);
        }
        existing.setBlogger(blogger);
        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        existing.getTags().clear();
        existing.getTags().addAll(tags);
        existing.updateTrackingInfo();
        return SuccessOrErrors.success(ServiceMessages.updated(ITEM));
    }

    private static DetailPostDto toDetailDto(Post post) {
        DetailPostDto dto = new DetailPostDto();
        dto.setPostId(post.getPostId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setBloggerName(post.getBlogger().getName());
        dto.setTagNames(post.getTagNames());
        dto.setLastUpdated(toLocal(post.getLastUpdated()));
        dto.getBloggers().setSelectedValue(String.valueOf(post.getBlogger().getBlogId()));
        dto.getUserChosenTags().setFinalSelection(
                new ArrayList<>(post.getTagsInIdOrder().stream().map(Tag::getTagId).toList()));
        return dto;
    }

    /** LastUpdated is stored in UTC; DTOs expose local time like the legacy display. */
    static LocalDateTime toLocal(LocalDateTime utc) {
        return utc == null ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
