package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import java.util.List;
import java.util.Optional;

/** Synchronous post operations (legacy IListService / IDetailService / ICreateService / IUpdateService / IDeleteService). */
public interface PostService {

    /** All posts in PostId order; when blogId is non-null and non-zero only that blogger's posts. */
    List<SimplePostDto> listPosts(Integer blogId);

    Optional<DetailPostDto> getDetail(int postId);

    /** DTO for Create GET: empty fields, bloggers and tags option lists populated, nothing selected. */
    DetailPostDto getCreateSetup();

    /** DTO for Edit GET: fields populated, current blogger and tags pre-selected. */
    Optional<DetailPostDto> getUpdateSetup(int postId);

    /** Repopulates option lists on a posted DTO (legacy ResetDto) so the form can be redisplayed after errors. */
    DetailPostDto resetDto(DetailPostDto dto);

    /** Success message: Successfully created Post. */
    SuccessOrErrors create(DetailPostDto dto);

    /** Success message: Successfully updated Post. */
    SuccessOrErrors update(DetailPostDto dto);

    /** Success message: Successfully deleted Post. */
    SuccessOrErrors delete(int postId);

    /** Returns the text: The total number of Posts is N */
    String getNumPostsMessage();
}
