package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.datalayer.entities.Tag;
import com.samplemvcwebapp.datalayer.repositories.TagRepository;
import com.samplemvcwebapp.datalayer.validation.ValidationMessages;
import com.samplemvcwebapp.servicelayer.BeanValidation;
import com.samplemvcwebapp.servicelayer.ServiceMessages;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Legacy TagsController via GenericServices List/Detail/Create/Update/Delete on the Tag entity. */
@Service
@Transactional
public class TagServiceImpl implements TagService {

    private static final String ITEM = "Tag";

    private final TagRepository tagRepository;
    private final BeanValidation beanValidation;

    public TagServiceImpl(TagRepository tagRepository, BeanValidation beanValidation) {
        this.tagRepository = tagRepository;
        this.beanValidation = beanValidation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagListDto> listTags() {
        return tagRepository.findAllWithPostsCount().stream()
                .map(t -> new TagListDto(t.getTagId(), t.getName(), t.getSlug(), t.getPostsCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TagDto> getDetail(int tagId) {
        return tagRepository.findById(tagId).map(TagServiceImpl::toDto);
    }

    @Override
    public SuccessOrErrors create(TagDto dto) {
        List<ValidationError> errors = beanValidation.validate(dto);
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);
        if (tagRepository.existsBySlug(dto.getSlug()))
            return slugNotUnique(dto);
        Tag tag = tagRepository.save(new Tag(dto.getName(), dto.getSlug()));
        dto.setTagId(tag.getTagId());
        return SuccessOrErrors.success(ServiceMessages.created(ITEM));
    }

    @Override
    public SuccessOrErrors update(TagDto dto) {
        Optional<Tag> found = tagRepository.findById(dto.getTagId());
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        List<ValidationError> errors = beanValidation.validate(dto);
        if (!errors.isEmpty()) return SuccessOrErrors.errors(errors);
        if (tagRepository.existsBySlugAndTagIdNot(dto.getSlug(), dto.getTagId()))
            return slugNotUnique(dto);
        Tag tag = found.get();
        tag.setName(dto.getName());
        tag.setSlug(dto.getSlug());
        return SuccessOrErrors.success(ServiceMessages.updated(ITEM));
    }

    @Override
    public SuccessOrErrors delete(int tagId) {
        Optional<Tag> found = tagRepository.findById(tagId);
        if (found.isEmpty()) return SuccessOrErrors.error(null, ServiceMessages.NOT_FOUND);
        tagRepository.delete(found.get());
        return SuccessOrErrors.success(ServiceMessages.deleted(ITEM));
    }

    private static SuccessOrErrors slugNotUnique(TagDto dto) {
        return SuccessOrErrors.error("Slug", ValidationMessages.slugNotUnique(dto.getName()));
    }

    private static TagDto toDto(Tag tag) {
        TagDto dto = new TagDto();
        dto.setTagId(tag.getTagId());
        dto.setName(tag.getName());
        dto.setSlug(tag.getSlug());
        return dto;
    }
}
