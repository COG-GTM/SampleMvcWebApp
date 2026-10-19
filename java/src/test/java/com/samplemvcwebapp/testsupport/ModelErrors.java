package com.samplemvcwebapp.testsupport;

import com.samplemvcwebapp.servicelayer.SuccessOrErrors;
import com.samplemvcwebapp.servicelayer.ValidationError;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

/**
 * Java port of the legacy ModelStateDictionary view used by SampleWebApp/Infrastructure/ValidationHelper.cs.
 * Keys are PascalCase property names ("MyInt", "Title") in declaration order; model-level errors use the "" key and
 * come last, as in ReturnModelErrorsAsJson.
 */
public final class ModelErrors {

    private ModelErrors() { }

    /** ModelState equivalent of a validated binding result. */
    public static Map<String, List<String>> fromBindingResult(BindingResult result) {
        Map<String, List<String>> byField = new LinkedHashMap<>();
        List<String> declared = declaredFieldOrder(result.getTarget() == null ? Object.class : result.getTarget().getClass());
        result.getFieldErrors().stream()
                .sorted((a, b) -> Integer.compare(indexOf(declared, a.getField()), indexOf(declared, b.getField())))
                .forEach(e -> byField.computeIfAbsent(StringUtils.capitalize(e.getField()), k -> new ArrayList<>())
                        .add(e.getDefaultMessage()));
        List<String> modelLevel = result.getGlobalErrors().stream().map(ObjectError::getDefaultMessage).toList();
        if (!modelLevel.isEmpty()) {
            byField.put("", new ArrayList<>(modelLevel));
        }
        return byField;
    }

    /**
     * Port of ReturnErrorsAsJson/CopyErrorsToModelState: errors whose property is shown by the dto keep their key,
     * everything else (including property-less errors) is reported at model level.
     */
    public static Map<String, List<String>> fromStatus(SuccessOrErrors status, Class<?> displayDtoType) {
        if (status.isValid()) {
            throw new IllegalArgumentException("You should only call this if there are errors to return.");
        }
        List<String> dtoProperties = beanPropertyNames(displayDtoType);
        Map<String, List<String>> byField = new LinkedHashMap<>();
        List<String> modelLevel = new ArrayList<>();
        for (ValidationError error : status.getErrors()) {
            String match = error.propertyName() == null ? null : dtoProperties.stream()
                    .filter(p -> p.equalsIgnoreCase(error.propertyName())).findFirst().orElse(null);
            if (match == null) {
                modelLevel.add(error.message());
            } else {
                byField.computeIfAbsent(StringUtils.capitalize(match), k -> new ArrayList<>()).add(error.message());
            }
        }
        if (!modelLevel.isEmpty()) {
            byField.put("", modelLevel);
        }
        return byField;
    }

    /** The ReturnModelErrorsAsJson payload shape: {"errorsDict":{"Key":{"errors":[...]}}}. */
    public static Map<String, Object> toErrorsDict(Map<String, List<String>> modelState) {
        if (modelState.isEmpty()) {
            throw new IllegalArgumentException("You should only call this if there are model errors to return.");
        }
        Map<String, Object> dict = new LinkedHashMap<>();
        modelState.forEach((key, errors) -> dict.put(key, Map.of("errors", errors)));
        return Map.of("errorsDict", dict);
    }

    private static List<String> declaredFieldOrder(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private static int indexOf(List<String> declared, String field) {
        int i = declared.indexOf(field);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static List<String> beanPropertyNames(Class<?> type) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        }
        try {
            return Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
                    .map(PropertyDescriptor::getName).toList();
        } catch (java.beans.IntrospectionException e) {
            throw new IllegalStateException(e);
        }
    }
}
