package com.ecomtest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordComplexityValidator.class)
public @interface ValidPassword {

    String message() default "Password must be at least 8 characters long and contain an uppercase letter, "
            + "a lowercase letter, a digit, a special character, and no spaces";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
