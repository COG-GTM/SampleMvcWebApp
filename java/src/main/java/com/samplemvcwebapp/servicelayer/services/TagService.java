package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import java.util.List;
import java.util.Optional;

/** Synchronous tag operations. */
public interface TagService {

    /** All tags in TagId order with post counts. */
    List<TagListDto> listTags();

    Optional<TagDto> getDetail(int tagId);

    /** Success message: Successfully created Tag. Duplicate-slug error is reported on property Slug. */
    SuccessOrErrors create(TagDto dto);

    /** Success message: Successfully updated Tag. */
    SuccessOrErrors update(TagDto dto);

    /** Success message: Successfully deleted Tag. */
    SuccessOrErrors delete(int tagId);
}
