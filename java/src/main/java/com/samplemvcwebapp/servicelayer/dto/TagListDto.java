package com.samplemvcwebapp.servicelayer.dto;

/** Mirrors ServiceLayer.TagServices.TagListDto (Tags list row). */
public record TagListDto(int tagId, String name, String slug, int postsCount) {
}
