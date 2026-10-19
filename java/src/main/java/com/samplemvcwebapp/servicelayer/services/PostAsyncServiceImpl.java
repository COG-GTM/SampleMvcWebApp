package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Legacy *ServiceAsync: runs the transactional {@link PostService} operations on the async executor. */
@Service
@Async
public class PostAsyncServiceImpl implements PostAsyncService {

    private final PostService postService;

    public PostAsyncServiceImpl(PostService postService) {
        this.postService = postService;
    }

    @Override
    public CompletableFuture<List<SimplePostDto>> listPostsAsync() {
        return CompletableFuture.completedFuture(postService.listPosts(null));
    }

    @Override
    public CompletableFuture<Optional<DetailPostDto>> getDetailAsync(int postId) {
        return CompletableFuture.completedFuture(postService.getDetail(postId));
    }

    @Override
    public CompletableFuture<DetailPostDto> getCreateSetupAsync() {
        return CompletableFuture.completedFuture(postService.getCreateSetup());
    }

    @Override
    public CompletableFuture<Optional<DetailPostDto>> getUpdateSetupAsync(int postId) {
        return CompletableFuture.completedFuture(postService.getUpdateSetup(postId));
    }

    @Override
    public CompletableFuture<DetailPostDto> resetDtoAsync(DetailPostDto dto) {
        return CompletableFuture.completedFuture(postService.resetDto(dto));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> createAsync(DetailPostDto dto) {
        return CompletableFuture.completedFuture(postService.create(dto));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> updateAsync(DetailPostDto dto) {
        return CompletableFuture.completedFuture(postService.update(dto));
    }

    @Override
    public CompletableFuture<SuccessOrErrors> deleteAsync(int postId) {
        return CompletableFuture.completedFuture(postService.delete(postId));
    }

    @Override
    public CompletableFuture<String> getNumPostsMessageAsync() {
        return CompletableFuture.completedFuture(postService.getNumPostsMessage());
    }
}
