package com.example.excelparser.utils.validators;

import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;

import java.util.regex.Pattern;

public class VisaryValidators {

    /**
     * Валидатор строки диапазонов целых значений (например, "1-6, 8-10, 12- 15").
     */
    public static final Validator INT_RANGE_VALIDATOR = new Validator() {
        // Допустимо любое количество пробелов между числами и символами "-" и ","
        private static final String REGEX = "(\\s*\\d+\\s*-\\s*\\d+\\s*)+(,\\s*\\d+\\s*-\\s*\\d+\\s*)*";

        @Override
        public ValidationResult validate(String subject, String input, ValidationContext context) {
            if (context.isExpressionLanguageSupported(subject) && context.isExpressionLanguagePresent(input)) {
                return new ValidationResult.Builder()
                        .subject(subject)
                        .input(input)
                        .explanation("Expression Language Present")
                        .valid(true)
                        .build();
            }

            final boolean valid = Pattern.matches(REGEX, input);
            return new ValidationResult.Builder()
                    .subject(subject)
                    .input(input)
                    .valid(valid)
                    .explanation(valid ? null : "Некорректная строка диапазонов целых чисел")
                    .build();
        }
    };
}
