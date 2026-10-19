package com.samplemvcwebapp.web;

import com.samplemvcwebapp.servicelayer.dto.BlogListDto;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.dto.SimplePostDto;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.dto.TagListDto;
import com.samplemvcwebapp.servicelayer.ui.DropDownListType;
import com.samplemvcwebapp.servicelayer.ui.KeyValue;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Canned DTOs shaped like the MEDIUM seed for the web-slice tests. */
final class WebTestData {

    static final LocalDateTime UPDATED = LocalDateTime.of(2026, 10, 19, 15, 4, 5);

    private WebTestData() {
    }

    /** Child A exposes the legacy prompt as DropDownListType.promptString; mimic that shape here. */
    static class PromptedDropDown extends DropDownListType {
        private final String promptString;

        PromptedDropDown(String promptString) {
            this.promptString = promptString;
        }

        public String getPromptString() {
            return promptString;
        }
    }

    static List<SimplePostDto> posts() {
        return List.of(
                new SimplePostDto(1, 1, "Ada Lovelace", "About Ada Lovelace", "About Me", UPDATED),
                new SimplePostDto(2, 1, "Ada Lovelace", "Thoughts on programming with a spanner", "Programming, My Opinion", UPDATED),
                new SimplePostDto(7, 2, "Sherlock Holmes", "Sound qualities of 221b Baker Street", "Personal, Entertainment", UPDATED));
    }

    static DetailPostDto setupDto() {
        DetailPostDto dto = new DetailPostDto();
        dto.setBloggers(new PromptedDropDown("--- choose blogger ---"));
        dto.getBloggers().setKeyValueList(new ArrayList<>(List.of(
                new KeyValue("Ada Lovelace", "1"),
                new KeyValue("Sherlock Holmes", "2"))));
        dto.getUserChosenTags().setKeyValueList(new ArrayList<>(List.of(
                new KeyValue("About Me", "1"),
                new KeyValue("Programming", "2"),
                new KeyValue("My Opinion", "3"))));
        return dto;
    }

    static DetailPostDto detailDto() {
        DetailPostDto dto = setupDto();
        dto.setPostId(1);
        dto.setTitle("About Ada Lovelace");
        dto.setContent("The following was taken from Wikipedia");
        dto.setBloggerName("Ada Lovelace");
        dto.setTagNames("About Me");
        dto.setLastUpdated(UPDATED);
        dto.getBloggers().setSelectedValue("1");
        dto.getUserChosenTags().setFinalSelection(new ArrayList<>(List.of(1)));
        return dto;
    }

    static List<TagListDto> tags() {
        return List.of(new TagListDto(1, "About Me", "about", 4), new TagListDto(2, "Programming", "programming", 4));
    }

    static TagDto tag() {
        TagDto tag = new TagDto();
        tag.setTagId(1);
        tag.setName("About Me");
        tag.setSlug("about");
        return tag;
    }

    static List<BlogListDto> blogs() {
        return List.of(new BlogListDto(1, "Ada Lovelace", "ada@nospam.com", 5),
                new BlogListDto(2, "Sherlock Holmes", "sherlock@nospam.com", 5));
    }
}
