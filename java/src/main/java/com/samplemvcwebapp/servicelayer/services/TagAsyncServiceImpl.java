package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Legacy *ServiceAsync: runs the transactional {@link TagService} operations on the async executor. */
@Service
@Async
public class TagAsyncServiceImpl implements TagAsyncService {

    private final TagService tagService;

    public TagAsyncServiceImpl(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    public CompletableFuture<List<TagListDto>> listTagsAsync() {
        return CompletableFuture.completedFuture(tagService.listTags());
    }

    @Override
    public CompletableFuture<Optional<TagDto>> getDetailAsync(int tagId) {
        return CompletableFuture.completedFuture(tagService.getDetail(tagId));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> createAsync(TagDto dto) {
        return CompletableFuture.completedFuture(tagService.create(dto));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> updateAsync(TagDto dto) {
        return CompletableFuture.completedFuture(tagService.update(dto));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> deleteAsync(int tagId) {
        return CompletableFuture.completedFuture(tagService.delete(tagId));
    }
}
