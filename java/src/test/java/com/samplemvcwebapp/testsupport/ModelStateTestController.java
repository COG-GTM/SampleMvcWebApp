package com.samplemvcwebapp.testsupport;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Test-only controller replacing ModelStateTester.ReturnModelState (which ran the MVC model binder by hand).
 * Mirrors MVC5's DefaultModelBinder: IValidatableObject.Validate only runs when no attribute errors were found.
 */
@Controller
public class ModelStateTestController {

    public static final String VIEW = "modelStateTest";

    /** Simple dto shown by the view in Check20/Check21 (legacy: {@code new { MyInt = 1 }}). */
    public record MyIntDto(int myInt) { }

    @PostMapping("/test/modelstate")
    public String modelState(@Valid @ModelAttribute("testModel") TestModel model, BindingResult result) {
        runModelLevelValidation(model, result);
        return VIEW;
    }

    @PostMapping("/test/modelstate/json")
    @ResponseBody
    public Map<String, Object> modelStateJson(@Valid @ModelAttribute("testModel") TestModel model, BindingResult result) {
        runModelLevelValidation(model, result);
        return ModelErrors.toErrorsDict(ModelErrors.fromBindingResult(result));
    }

    @PostMapping("/test/status/json")
    @ResponseBody
    public Map<String, Object> statusJson(@RequestParam(required = false) String propertyName,
                                          @RequestParam String message) {
        SuccessOrErrors status = SuccessOrErrors.error(propertyName, message);
        return ModelErrors.toErrorsDict(ModelErrors.fromStatus(status, MyIntDto.class));
    }

    private static void runModelLevelValidation(TestModel model, BindingResult result) {
        if (!result.hasFieldErrors()) {
            List<String> errors = model.validate();
            errors.forEach(message -> result.reject("modelLevel", message));
        }
    }
}
