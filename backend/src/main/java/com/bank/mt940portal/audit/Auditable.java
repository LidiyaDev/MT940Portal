package com.bank.mt940portal.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an operation that must be written to the audit trail. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** One of the constants in {@link AuditAction}. */
    String action();

    String entityType() default "";

    String description() default "";
}
