package dev.controlplane.auditkit.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String action();
    String subjectType();
    String subject();                  // SpEL for subject id (e.g., "#req.id" or "#result.id")
    String[] context() default {};     // key=value SpEL pairs (e.g., "appId=#req.appId")
    boolean emitBefore() default false;
    boolean includeArgs() default true;
    boolean includeResult() default false;
    boolean onException() default true;
}
