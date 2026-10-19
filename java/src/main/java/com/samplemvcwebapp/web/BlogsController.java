package com.samplemvcwebapp.web;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.dto.BlogDto;
import com.samplemvcwebapp.servicelayer.services.BlogService;
import com.samplemvcwebapp.web.infrastructure.ResourceNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Port of SampleWebApp/Controllers/BlogsController (no Details page in legacy). */
@Controller
@RequestMapping("/Blogs")
public class BlogsController {

    private static final String INDEX = "redirect:/Blogs";

    private final BlogService service;

    public BlogsController(BlogService service) {
        this.service = service;
    }

    @GetMapping({"", "/Index"})
    public String index(Model model) {
        model.addAttribute("blogs", service.listBlogs());
        return "blogs/index";
    }

    @GetMapping("/Edit/{id}")
    public String edit(@PathVariable int id, Model model) {
        model.addAttribute("blog", service.getDetail(id).orElseThrow(() -> new ResourceNotFoundException("Blog", id)));
        return "blogs/edit";
    }

    @PostMapping("/Edit/{id}")
    public String edit(@PathVariable int id, @ModelAttribute("blog") BlogDto blog, BindingResult bindingResult,
                       RedirectAttributes redirect) {
        if (blog.getBlogId() == 0) blog.setBlogId(id);
        if (bindingResult.hasErrors()) return "blogs/edit";
        return TagsController.handle(service.update(blog), "blogs/edit", INDEX, bindingResult, redirect);
    }

    @GetMapping("/Create")
    public String create(Model model) {
        model.addAttribute("blog", new BlogDto());
        return "blogs/create";
    }

    @PostMapping("/Create")
    public String create(@ModelAttribute("blog") BlogDto blog, BindingResult bindingResult, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) return "blogs/create";
        return TagsController.handle(service.create(blog), "blogs/create", INDEX, bindingResult, redirect);
    }

    /** Legacy BlogsController.Delete only reports success (no errorMessage on failure). */
    @GetMapping("/Delete/{id}")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        SuccessOrErrors response = service.delete(id);
        if (response.isValid()) redirect.addFlashAttribute("message", response.getSuccessMessage());
        return INDEX;
    }
}
