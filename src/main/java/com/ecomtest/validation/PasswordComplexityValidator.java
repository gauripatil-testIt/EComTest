package com.ecomtest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordComplexityValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() < 8) {
            return false;
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        boolean hasUpper = value.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = value.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = value.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
