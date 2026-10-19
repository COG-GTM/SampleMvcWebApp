package com.samplemvcwebapp.servicelayer.dto;

import java.time.LocalDateTime;

/**
 * Mirrors ServiceLayer.PostServices.SimplePostDto (Posts list row).
 * tagNames = tag names ordered by TagId, joined with ", ". lastUpdated is local time (legacy LastUpdatedUtc.ToLocalTime()).
 */
public record SimplePostDto(int postId, int blogId, String bloggerName, String title, String tagNames,
                            LocalDateTime lastUpdated) {
}
