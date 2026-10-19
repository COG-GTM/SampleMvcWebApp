package com.samplemvcwebapp.servicelayer.services;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Async post operations used by PostsAsyncController (legacy *ServiceAsync). Same semantics and messages as PostService. */
public interface PostAsyncService {

    CompletableFuture<List<SimplePostDto>> listPostsAsync();

    CompletableFuture<Optional<DetailPostDto>> getDetailAsync(int postId);

    CompletableFuture<DetailPostDto> getCreateSetupAsync();

    CompletableFuture<Optional<DetailPostDto>> getUpdateSetupAsync(int postId);

    CompletableFuture<DetailPostDto> resetDtoAsync(DetailPostDto dto);

    CompletableFuture<SuccessOrErrors> createAsync(DetailPostDto dto);

    CompletableFuture<SuccessOrErrors> updateAsync(DetailPostDto dto);

    CompletableFuture<SuccessOrErrors> deleteAsync(int postId);

    CompletableFuture<String> getNumPostsMessageAsync();
}
