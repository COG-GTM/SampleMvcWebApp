package com.samplemvcwebapp.web;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.TagDto;
import com.samplemvcwebapp.servicelayer.services.TagService;
import com.samplemvcwebapp.web.infrastructure.ResourceNotFoundException;
import com.samplemvcwebapp.web.infrastructure.ValidationHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Port of SampleWebApp/Controllers/TagsController (synchronous CRUD directly on the Tag data class). */
@Controller
@RequestMapping("/Tags")
public class TagsController {

    private final TagService service;

    public TagsController(TagService service) {
        this.service = service;
    }

    @GetMapping({"", "/Index"})
    public String index(Model model) {
        model.addAttribute("tags", service.listTags());
        return "tags/index";
    }

    @GetMapping("/Details/{id}")
    public String details(@PathVariable int id, Model model) {
        model.addAttribute("tag", service.getDetail(id).orElseThrow(() -> new ResourceNotFoundException("Tag", id)));
        return "tags/details";
    }

    @GetMapping("/Edit/{id}")
    public String edit(@PathVariable int id, Model model) {
        model.addAttribute("tag", service.getDetail(id).orElseThrow(() -> new ResourceNotFoundException("Tag", id)));
        return "tags/edit";
    }

    @PostMapping("/Edit/{id}")
    public String edit(@PathVariable int id, @ModelAttribute("tag") TagDto tag, BindingResult bindingResult,
                       RedirectAttributes redirect) {
        if (tag.getTagId() == 0) tag.setTagId(id);
        if (bindingResult.hasErrors()) return "tags/edit";
        return handle(service.update(tag), "tags/edit", "redirect:/Tags", bindingResult, redirect);
    }

    @GetMapping("/Create")
    public String create(Model model) {
        model.addAttribute("tag", new TagDto());
        return "tags/create";
    }

    @PostMapping("/Create")
    public String create(@ModelAttribute("tag") TagDto tag, BindingResult bindingResult, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) return "tags/create";
        return handle(service.create(tag), "tags/create", "redirect:/Tags", bindingResult, redirect);
    }

    @GetMapping("/Delete/{id}")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        PostsController.addDeleteFlash(service.delete(id), redirect);
        return "redirect:/Tags";
    }

    @GetMapping("/CodeView")
    public String codeView() {
        return "tags/codeView";
    }

    /** Success: flash the message and redirect to Index; failure: copy errors into the form and redisplay. */
    static String handle(SuccessOrErrors response, String view, String redirectToIndex, BindingResult bindingResult,
                         RedirectAttributes redirect) {
        if (response.isValid()) {
            redirect.addFlashAttribute("message", response.getSuccessMessage());
            return redirectToIndex;
        }
        ValidationHelper.copyErrorsToBindingResult(response, bindingResult);
        return view;
    }
}
