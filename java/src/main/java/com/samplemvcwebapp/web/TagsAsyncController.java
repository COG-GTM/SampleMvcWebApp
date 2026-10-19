package com.samplemvcwebapp.web;

import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.services.TagAsyncService;
import com.samplemvcwebapp.web.infrastructure.ResourceNotFoundException;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Port of SampleWebApp/Controllers/TagsAsyncController. */
@Controller
@RequestMapping("/TagsAsync")
public class TagsAsyncController {

    private static final String INDEX = "redirect:/TagsAsync";

    private final TagAsyncService service;

    public TagsAsyncController(TagAsyncService service) {
        this.service = service;
    }

    @GetMapping({"", "/Index"})
    public CompletableFuture<String> index(Model model) {
        return service.listTagsAsync().thenApply(tags -> {
            model.addAttribute("tags", tags);
            return "tagsAsync/index";
        });
    }

    @GetMapping("/Details/{id}")
    public CompletableFuture<String> details(@PathVariable int id, Model model) {
        return service.getDetailAsync(id).thenApply(tag -> {
            model.addAttribute("tag", tag.orElseThrow(() -> new ResourceNotFoundException("Tag", id)));
            return "tagsAsync/details";
        });
    }

    @GetMapping("/Edit/{id}")
    public CompletableFuture<String> edit(@PathVariable int id, Model model) {
        return service.getDetailAsync(id).thenApply(tag -> {
            model.addAttribute("tag", tag.orElseThrow(() -> new ResourceNotFoundException("Tag", id)));
            return "tagsAsync/edit";
        });
    }

    @PostMapping("/Edit/{id}")
    public CompletableFuture<String> edit(@PathVariable int id, @ModelAttribute("tag") TagDto tag,
                                          BindingResult bindingResult, RedirectAttributes redirect) {
        if (tag.getTagId() == 0) tag.setTagId(id);
        if (bindingResult.hasErrors()) return CompletableFuture.completedFuture("tagsAsync/edit");
        return service.updateAsync(tag)
                .thenApply(response -> TagsController.handle(response, "tagsAsync/edit", INDEX, bindingResult, redirect));
    }

    @GetMapping("/Create")
    public String create(Model model) {
        model.addAttribute("tag", new TagDto());
        return "tagsAsync/create";
    }

    @PostMapping("/Create")
    public CompletableFuture<String> create(@ModelAttribute("tag") TagDto tag, BindingResult bindingResult,
                                            RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) return CompletableFuture.completedFuture("tagsAsync/create");
        return service.createAsync(tag)
                .thenApply(response -> TagsController.handle(response, "tagsAsync/create", INDEX, bindingResult, redirect));
    }

    @GetMapping("/Delete/{id}")
    public CompletableFuture<String> delete(@PathVariable int id, RedirectAttributes redirect) {
        return service.deleteAsync(id).thenApply(response -> {
            PostsController.addDeleteFlash(response, redirect);
            return INDEX;
        });
    }

    @GetMapping("/CodeView")
    public String codeView() {
        return "tagsAsync/codeView";
    }
}
