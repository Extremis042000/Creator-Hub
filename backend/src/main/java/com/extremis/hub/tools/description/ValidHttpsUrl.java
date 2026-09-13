package com.extremis.hub.tools.description;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parses via java.net.URI rather than regex — see
 * docs/06-prd.md §5.5. Rejects unless parsing succeeds, scheme is
 * exactly "https", and host is non-empty.
 */
@Documented
@Constraint(validatedBy = ValidHttpsUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidHttpsUrl {
    String message() default "must be a valid https:// URL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
