package com.popclub.testsigma;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestSigmaId {
    String value() default "";
    String folder() default "negative";
    String[] labels() default {};
    String preconditions() default "";
    String steps() default "";
    String expectedResults() default "";
}
