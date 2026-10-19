package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.datalayer.entities.Blog;
import com.samplemvcwebapp.datalayer.repositories.BlogRepository;
import com.samplemvcwebapp.servicelayer.BeanValidation;
import com.samplemvcwebapp.servicelayer.ServiceMessages;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.BlogDto;
import com.samplemvcwebapp.servicelayer.dto.BlogListDto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Legacy BlogsController via GenericServices on the Blog entity. Delete cascades to the blog's posts. */
@Service
@Transactional
public class BlogServiceImpl implements BlogService {

    private static final String ITEM = "Blog";

    private final BlogRepository blogRepository;
    private final BeanValidation beanValidation;

    public BlogServiceImpl(BlogRepository blogRepository, BeanValidation beanValidation) {
        this.blogRepository = blogRepository;
        this.beanValidation = beanValidation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlogListDto> listBlogs() {
        return blogRepository.findAllWithPostsCount().stream()
                .map(b -> new BlogListDto(b.getBlogId(), b.getName(), b.getEmailAddress(), b.getPostsCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BlogDto> getDetail(int blogId) {
        return blogRepository.findById(blogId).map(BlogServiceImpl::toDto);
    }

    @Override
    public SuccessOrErrors create(BlogDto dto) {
        List<ValidationError> errors = beanValidation.validate(dto);
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);
        Blog blog = blogRepository.save(new Blog(dto.getName(), dto.getEmailAddress()));
        dto.setBlogId(blog.getBlogId());
        return SuccessOrErrors.success(ServiceMessages.created(ITEM));
    }

    @Override
    public SuccessOrErrors update(BlogDto dto) {
        Optional<Blog> found = blogRepository.findById(dto.getBlogId());
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        List<ValidationError> errors = beanValidation.validate(dto);
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);
        found.get().setName(dto.getName());
        found.get().setEmailAddress(dto.getEmailAddress());
        return SuccessOrErrors.success(ServiceMessages.updated(ITEM));
    }

    @Override
    public SuccessOrErrors delete(int blogId) {
        Optional<Blog> found = blogRepository.findById(blogId);
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        blogRepository.delete(found.get());
        return SuccessOrErrors.success(ServiceMessages.deleted(ITEM));
    }

    private static BlogDto toDto(Blog blog) {
        BlogDto dto = new BlogDto();
        dto.setBlogId(blog.getBlogId());
        dto.setName(blog.getName());
        dto.setEmailAddress(blog.getEmailAddress());
        return dto;
    }
}
