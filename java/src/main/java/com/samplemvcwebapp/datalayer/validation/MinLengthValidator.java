package com.samplemvcwebapp.datalayer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MinLengthValidator implements ConstraintValidator<MinLength, CharSequence> {

    private int min;

    @Override
    public void initialize(MinLength annotation) {
        this.min = annotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.isEmpty() || value.length() >= min;
    }
}
