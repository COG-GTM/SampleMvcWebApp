package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.BlogDto;
import com.samplemvcwebapp.servicelayer.dto.BlogListDto;
import java.util.List;
import java.util.Optional;

/** Blog operations (legacy BlogsController uses sync GenericServices only). */
public interface BlogService {

    /** All blogs in BlogId order with post counts. */
    List<BlogListDto> listBlogs();

    Optional<BlogDto> getDetail(int blogId);

    /** Success message: Successfully created Blog. */
    SuccessOrErrors create(BlogDto dto);

    /** Success message: Successfully updated Blog. */
    SuccessOrErrors update(BlogDto dto);

    /** Success message: Successfully deleted Blog. Deleting a blog deletes its posts (legacy cascade). */
    SuccessOrErrors delete(int blogId);
}
