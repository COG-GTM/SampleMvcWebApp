package com.samplemvcwebapp.web.infrastructure;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import java.beans.Introspector;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/** Port of SampleWebApp/Infrastructure/ValidationHelper.CopyErrorsToModelState. */
public final class ValidationHelper {

    private ValidationHelper() {
    }

    /**
     * Copies service errors into the BindingResult: errors whose property exists on the form object become field
     * errors (legacy PascalCase names such as "Title" or "UserChosenTags" are mapped to bean paths), everything else
     * becomes a model-level (global) error shown in the validation summary.
     */
    public static void copyErrorsToBindingResult(SuccessOrErrors status, BindingResult bindingResult) {
        if (status == null || status.isValid()) return;
        Object target = bindingResult.getTarget();
        BeanWrapper wrapper = target == null ? null : PropertyAccessorFactory.forBeanPropertyAccess(target);
        for (ValidationError error : status.getErrors()) {
            String field = toBeanPath(error.propertyName());
            if (field != null && wrapper != null && wrapper.isReadableProperty(field)) {
                bindingResult.addError(new FieldError(bindingResult.getObjectName(), field,
                        wrapper.getPropertyValue(field), false, null, null, error.message()));
            } else {
                bindingResult.addError(new ObjectError(bindingResult.getObjectName(), null, null, error.message()));
            }
        }
    }

    static String toBeanPath(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) return null;
        return Arrays.stream(propertyName.trim().split(Pattern.quote(".")))
                .map(Introspector::decapitalize)
                .collect(Collectors.joining("."));
    }
}
