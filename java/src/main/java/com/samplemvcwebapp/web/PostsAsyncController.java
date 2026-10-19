package com.samplemvcwebapp.web;

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.services.PostAsyncService;
import com.samplemvcwebapp.web.infrastructure.ResourceNotFoundException;
import com.samplemvcwebapp.web.infrastructure.ValidationHelper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Port of SampleWebApp/Controllers/PostsAsyncController: same behaviour as Posts, handlers complete asynchronously. */
@Controller
@RequestMapping("/PostsAsync")
public class PostsAsyncController {

    private final PostAsyncService service;
    private final DataLayerInitialiser dataLayerInitialiser;

    public PostsAsyncController(PostAsyncService service, DataLayerInitialiser dataLayerInitialiser) {
        this.service = service;
        this.dataLayerInitialiser = dataLayerInitialiser;
    }

    @InitBinder("dto")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("bloggers.keyValueList*", "userChosenTags.keyValueList*");
    }

    /** Legacy async Index has no blog filter; the optional id (menu links use /Index/0) is ignored. */
    @GetMapping({"", "/Index", "/Index/{id}"})
    public CompletableFuture<String> index(@PathVariable(required = false) Integer id, Model model) {
        return service.listPostsAsync().thenApply(posts -> {
            model.addAttribute("posts", posts);
            return "postsAsync/index";
        });
    }

    @GetMapping("/Details/{id}")
    public CompletableFuture<String> details(@PathVariable int id, Model model) {
        return service.getDetailAsync(id).thenApply(dto -> {
            model.addAttribute("dto", dto.orElseThrow(() -> new ResourceNotFoundException("Post", id)));
            return "postsAsync/details";
        });
    }

    @GetMapping("/Edit/{id}")
    public CompletableFuture<String> edit(@PathVariable int id, Model model) {
        return service.getUpdateSetupAsync(id).thenApply(dto -> {
            model.addAttribute("dto", dto.orElseThrow(() -> new ResourceNotFoundException("Post", id)));
            return "postsAsync/edit";
        });
    }

    @PostMapping("/Edit/{id}")
    public CompletableFuture<String> edit(@PathVariable int id, @ModelAttribute("dto") DetailPostDto dto,
                                          BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (dto.getPostId() == 0) dto.setPostId(id);
        if (bindingResult.hasErrors())
            return redisplay("postsAsync/edit", dto, model);
        return service.updateAsync(dto).thenCompose(response -> handle(response, "postsAsync/edit", dto, bindingResult, model, redirect));
    }

    @GetMapping("/Create")
    public CompletableFuture<String> create(Model model) {
        return service.getCreateSetupAsync().thenApply(dto -> {
            model.addAttribute("dto", dto);
            return "postsAsync/create";
        });
    }

    @PostMapping("/Create")
    public CompletableFuture<String> create(@ModelAttribute("dto") DetailPostDto dto, BindingResult bindingResult,
                                            Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors())
            return redisplay("postsAsync/create", dto, model);
        return service.createAsync(dto).thenCompose(response -> handle(response, "postsAsync/create", dto, bindingResult, model, redirect));
    }

    @GetMapping("/Delete/{id}")
    public CompletableFuture<String> delete(@PathVariable int id, RedirectAttributes redirect) {
        return service.deleteAsync(id).thenApply(response -> {
            PostsController.addDeleteFlash(response, redirect);
            return "redirect:/PostsAsync";
        });
    }

    @GetMapping("/NumPosts")
    public CompletableFuture<String> numPosts(Model model) {
        return service.getNumPostsMessageAsync().thenApply(message -> {
            model.addAttribute("message", message);
            return "postsAsync/numPosts";
        });
    }

    @GetMapping("/CodeView")
    public String codeView() {
        return "postsAsync/codeView";
    }

    @GetMapping("/Delay")
    public CompletableFuture<String> delay(Model model) {
        return CompletableFuture.supplyAsync(() -> {
            model.addAttribute("delayMs", PostsController.DELAY_MS);
            return "postsAsync/delay";
        }, CompletableFuture.delayedExecutor(PostsController.DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @GetMapping("/Reset")
    public String reset(RedirectAttributes redirect) {
        dataLayerInitialiser.resetBlogs(TestDataSelection.MEDIUM);
        redirect.addFlashAttribute("message", "Successfully reset the blogs data");
        return "redirect:/PostsAsync";
    }

    private CompletableFuture<String> handle(SuccessOrErrors response, String view, DetailPostDto dto,
                                             BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (response.isValid()) {
            redirect.addFlashAttribute("message", response.getSuccessMessage());
            return CompletableFuture.completedFuture("redirect:/PostsAsync");
        }
        ValidationHelper.copyErrorsToBindingResult(response, bindingResult);
        return redisplay(view, dto, model);
    }

    private CompletableFuture<String> redisplay(String view, DetailPostDto dto, Model model) {
        return service.resetDtoAsync(dto).thenApply(reset -> {
            model.addAttribute("dto", reset != null ? reset : dto);
            return view;
        });
    }
}
