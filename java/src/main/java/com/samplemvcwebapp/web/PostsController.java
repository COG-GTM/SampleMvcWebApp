package com.samplemvcwebapp.web;

import com.samplemvcwebapp.datalayer.startup.DataLayerInitialiser;
import com.samplemvcwebapp.datalayer.startup.TestDataSelection;
import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import com.samplemvcwebapp.servicelayer.dto.DetailPostDto;
import com.samplemvcwebapp.servicelayer.services.PostService;
import com.samplemvcwebapp.web.infrastructure.ResourceNotFoundException;
import com.samplemvcwebapp.web.infrastructure.ValidationHelper;
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

/** Port of SampleWebApp/Controllers/PostsController (synchronous DTO-based CRUD on posts). */
@Controller
@RequestMapping("/Posts")
public class PostsController {

    static final int DELAY_MS = 500;

    private final PostService service;
    private final DataLayerInitialiser dataLayerInitialiser;

    public PostsController(PostService service, DataLayerInitialiser dataLayerInitialiser) {
        this.service = service;
        this.dataLayerInitialiser = dataLayerInitialiser;
    }

    @InitBinder("dto")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("bloggers.keyValueList*", "userChosenTags.keyValueList*");
    }

    @GetMapping({"", "/Index", "/Index/{id}"})
    public String index(@PathVariable(required = false) Integer id, Model model) {
        boolean filtered = id != null && id != 0;
        if (filtered) model.addAttribute("message", "Filtered list");
        model.addAttribute("posts", service.listPosts(filtered ? id : null));
        return "posts/index";
    }

    @GetMapping("/Details/{id}")
    public String details(@PathVariable int id, Model model) {
        model.addAttribute("dto", service.getDetail(id).orElseThrow(() -> new ResourceNotFoundException("Post", id)));
        return "posts/details";
    }

    @GetMapping("/Edit/{id}")
    public String edit(@PathVariable int id, Model model) {
        model.addAttribute("dto", service.getUpdateSetup(id).orElseThrow(() -> new ResourceNotFoundException("Post", id)));
        return "posts/edit";
    }

    @PostMapping("/Edit/{id}")
    public String edit(@PathVariable int id, @ModelAttribute("dto") DetailPostDto dto, BindingResult bindingResult,
                       Model model, RedirectAttributes redirect) {
        if (dto.getPostId() == 0) dto.setPostId(id);
        if (bindingResult.hasErrors())
            return redisplay("posts/edit", dto, model);
        SuccessOrErrors response = service.update(dto);
        if (response.isValid()) {
            redirect.addFlashAttribute("message", response.getSuccessMessage());
            return "redirect:/Posts";
        }
        ValidationHelper.copyErrorsToBindingResult(response, bindingResult);
        return redisplay("posts/edit", dto, model);
    }

    @GetMapping("/Create")
    public String create(Model model) {
        model.addAttribute("dto", service.getCreateSetup());
        return "posts/create";
    }

    @PostMapping("/Create")
    public String create(@ModelAttribute("dto") DetailPostDto dto, BindingResult bindingResult, Model model,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors())
            return redisplay("posts/create", dto, model);
        SuccessOrErrors response = service.create(dto);
        if (response.isValid()) {
            redirect.addFlashAttribute("message", response.getSuccessMessage());
            return "redirect:/Posts";
        }
        ValidationHelper.copyErrorsToBindingResult(response, bindingResult);
        return redisplay("posts/create", dto, model);
    }

    @GetMapping("/Delete/{id}")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        addDeleteFlash(service.delete(id), redirect);
        return "redirect:/Posts";
    }

    @GetMapping("/NumPosts")
    public String numPosts(Model model) {
        model.addAttribute("message", service.getNumPostsMessage());
        return "posts/numPosts";
    }

    @GetMapping("/CodeView")
    public String codeView() {
        return "posts/codeView";
    }

    @GetMapping("/Delay")
    public String delay(Model model) throws InterruptedException {
        Thread.sleep(DELAY_MS);
        model.addAttribute("delayMs", DELAY_MS);
        return "posts/delay";
    }

    @GetMapping("/Reset")
    public String reset(RedirectAttributes redirect) {
        dataLayerInitialiser.resetBlogs(TestDataSelection.MEDIUM);
        redirect.addFlashAttribute("message", "Successfully reset the blogs data");
        return "redirect:/Posts";
    }

    private String redisplay(String view, DetailPostDto dto, Model model) {
        DetailPostDto reset = service.resetDto(dto);
        model.addAttribute("dto", reset != null ? reset : dto);
        return view;
    }

    /** Legacy: TempData["message"] on success, TempData["errorMessage"] = response.ErrorsAsHtml() on failure. */
    static void addDeleteFlash(SuccessOrErrors response, RedirectAttributes redirect) {
        if (response.isValid())
            redirect.addFlashAttribute("message", response.getSuccessMessage());
        else
            redirect.addFlashAttribute("errorMessage", response.getErrors().stream().map(ValidationError::message).toList());
    }
}
