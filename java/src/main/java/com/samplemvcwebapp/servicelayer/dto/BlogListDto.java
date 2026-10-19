package com.samplemvcwebapp.servicelayer.dto;

/** Mirrors ServiceLayer.BlogServices.BlogListDto (Blogs list row). */
public record BlogListDto(int blogId, String name, String emailAddress, int postsCount) {
}
