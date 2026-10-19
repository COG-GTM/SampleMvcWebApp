package com.samplemvcwebapp.web;

import com.samplemvcwebapp.web.models.InternalsInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Port of SampleWebApp/Controllers/HomeController. */
@Controller
public class HomeController {

    @GetMapping({"/", "/Home", "/Home/Index"})
    public String index() {
        return "home/index";
    }

    @GetMapping("/Home/About")
    public String about() {
        return "home/about";
    }

    @GetMapping("/Home/Contact")
    public String contact() {
        return "home/contact";
    }

    @GetMapping("/Home/Internals")
    public String internals(Model model) {
        model.addAttribute("info", InternalsInfo.capture());
        return "home/internals";
    }

    /** Views/Shared/Error as a named page (notes section 5: GET /Error). */
    @GetMapping("/Error")
    public String error() {
        return "error";
    }

    @GetMapping("/Home/CodeView")
    public String codeView() {
        return "home/codeView";
    }
}
