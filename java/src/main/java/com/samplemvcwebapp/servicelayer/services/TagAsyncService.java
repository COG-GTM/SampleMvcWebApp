package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Async tag operations used by TagsAsyncController. Same semantics and messages as TagService. */
public interface TagAsyncService {

    CompletableFuture<List<TagListDto>> listTagsAsync();

    CompletableFuture<Optional<TagDto>> getDetailAsync(int tagId);

    CompletableFuture<SuccessOrErrors> createAsync(TagDto dto);

    CompletableFuture<SuccessOrErrors> updateAsync(TagDto dto);

    CompletableFuture<SuccessOrErrors> deleteAsync(int tagId);
}
