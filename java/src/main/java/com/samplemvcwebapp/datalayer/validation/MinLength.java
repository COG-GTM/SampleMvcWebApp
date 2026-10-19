package com.samplemvcwebapp.datalayer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mirrors System.ComponentModel.DataAnnotations.MinLengthAttribute as seen through ASP.NET MVC: a null or empty value
 * passes (MVC binds "" as null and [Required] reports it), so a blank field yields only the "required" message.
 */
@Documented
@Constraint(validatedBy = MinLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinLength {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
