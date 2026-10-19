package com.samplemvcwebapp.servicelayer;

import com.samplemvcwebapp.datalayer.entities.RuleViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

/**
 * Runs Jakarta Bean Validation and converts violations into {@link ValidationError}s named like the legacy
 * DataAnnotations member names (field path "title" becomes "Title", "bloggers.selectedValue" becomes
 * "Bloggers.SelectedValue"; class-level violations have a null property, i.e. model-level).
 */
@Component
public class BeanValidation {

    private static final Comparator<ValidationError> ORDER = Comparator
            .comparing(ValidationError::propertyName, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ValidationError::message);

    private final Validator validator;

    public BeanValidation(Validator validator) {
        this.validator = validator;
    }

    public List<ValidationError> validate(Object target) {
        return validator.validate(target).stream()
                .map(v -> new ValidationError(propertyName(v), v.getMessage()))
                .distinct()
                .sorted(ORDER)
                .toList();
    }

    public static List<ValidationError> toErrors(List<RuleViolation> violations) {
        return violations.stream().map(v -> new ValidationError(v.propertyName(), v.message())).toList();
    }

    private static String propertyName(ConstraintViolation<?> violation) {
        Path path = violation.getPropertyPath();
        String name = StreamSupport.stream(path.spliterator(), false)
                .map(Path.Node::getName)
                .filter(n -> n != null && !n.isEmpty())
                .map(n -> Character.toUpperCase(n.charAt(0)) + n.substring(1))
                .collect(Collectors.joining("."));
        return name.isEmpty() ? null : name;
    }
}
