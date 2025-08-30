package dev.controlplane.auditkit.annotations;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditRedact {
    Strategy strategy() default Strategy.MASK;
    enum Strategy { MASK, HASH, OMIT }
}
