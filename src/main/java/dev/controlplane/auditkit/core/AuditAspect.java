package dev.controlplane.auditkit.core;

import dev.controlplane.auditkit.annotations.Audited;
import dev.controlplane.auditkit.context.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Aspect
public class AuditAspect {

    private final AuditProperties props;
    private final AuditSinkClient client;
    private final AuditRedactor redactor;
    private final ExpressionParser parser = new SpelExpressionParser();

    public AuditAspect(AuditProperties props, AuditSinkClient client) {
        this.props = props;
        this.client = client;
        this.redactor = new AuditRedactor(props.getRedactKeys(), props.getMaxJsonBytes());
    }

    @Around("@annotation(dev.controlplane.auditkit.annotations.Audited)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("AUDIT ASPECT: Method intercepted - " + pjp.getSignature());
        if (!props.isEnabled()) {
            System.out.println("AUDIT ASPECT: Audit disabled, proceeding without audit");
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Audited ann = method.getAnnotation(Audited.class);

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        Object[] args = pjp.getArgs();
        String[] paramNames = ((MethodSignature) pjp.getSignature()).getParameterNames();
        
        // Add parameters by position (p0, p1, etc.)
        for (int i = 0; i < args.length; i++) ctx.setVariable("p"+i, args[i]);
        
        // Add parameters by name if available
        if (paramNames != null && paramNames.length == args.length) {
            for (int i = 0; i < args.length; i++) {
                if (paramNames[i] != null) ctx.setVariable(paramNames[i], args[i]);
            }
        }
        
        // Special case for single argument methods
        if (args.length == 1) ctx.setVariable("req", args[0]);
        
        ctx.setVariable("actor", ActorContextHolder.get());
        ctx.setVariable("corr", CorrelationContextHolder.get());
        ctx.setVariable("policy", PolicyDecisionContextHolder.get());

        if (ann.emitBefore()) {
            SinkEnvelope pre = baseEnvelope("INTENT", ann, ctx, null, args);
            try { client.send(pre); } catch (Exception ignored) {}
        }

        Object result = null;
        try {
            result = pjp.proceed();
            ctx.setVariable("result", result);
            SinkEnvelope env = baseEnvelope("SUCCESS", ann, ctx, result, args);
            client.send(env);
            return result;
        } catch (Throwable ex) {
            SinkEnvelope env = baseEnvelope("FAILURE", ann, ctx, result, args);
            env = withError(env, ex);
            if (ann.onException()) {
                try { client.send(env); } catch (Exception ignored) {}
            }
            throw ex;
        }
    }

    private SinkEnvelope baseEnvelope(String outcome, Audited ann, StandardEvaluationContext ctx, Object result, Object[] args) {
        String subjectId = evalString(ann.subject(), ctx, "UNKNOWN");
        Map<String,String> kv = parseContextPairs(ann.context(), ctx);

        ActorContext ac = ActorContextHolder.get();
        CorrelationContext cc = CorrelationContextHolder.get();
        PolicyDecisionContext pc = PolicyDecisionContextHolder.get();

        Map<String,Object> argsSnap = ann.includeArgs() ? redactor.snapshot(args.length == 1 ? args[0] : Map.of("args", args)) : null;
        Map<String,Object> resultSnap = ann.includeResult() ? redactor.snapshot(result) : null;

        String keySeed = String.join("|",
                "p:"+props.getProducerId(),
                "c:"+(cc != null ? nullSafe(cc.correlationId) : ""),
                "a:"+ann.action(),
                "st:"+ann.subjectType(),
                "si:"+subjectId);
        String idem = HashUtil.sha256Base64(keySeed);

        return new SinkEnvelope(
                1,
                props.getProducerId(),
                OffsetDateTime.now(),
                ann.action(),
                outcome,
                new SinkEnvelope.Subject(ann.subjectType(), subjectId),
                new SinkEnvelope.Actor(ac != null ? ac.actorId : "unknown", ac != null ? ac.actorType : "SERVICE", ac != null ? ac.roles : null, ac != null ? ac.tenantId : null),
                new SinkEnvelope.Context(kv.get("appId"), kv.get("trackId"), kv.get("releaseId"), kv.get("jiraKey"), kv.get("snowSysId")),
                cc != null ? cc.channel : null,
                cc != null ? cc.correlationId : null,
                cc != null ? cc.traceId : null,
                pc != null ? new SinkEnvelope.Policy(pc.decisionId, pc.rulePath) : null,
                new SinkEnvelope.Payload(argsSnap, resultSnap, null),
                null,
                idem
        );
    }

    private SinkEnvelope withError(SinkEnvelope base, Throwable ex) {
        return new SinkEnvelope(
                base.schemaVersion(), base.producerId(), base.occurredAtUtc(), base.action(), base.outcome(),
                base.subject(), base.actor(), base.context(), base.channel(), base.correlationId(), base.traceId(),
                base.policy(),
                base.payload(),
                new SinkEnvelope.ErrorInfo(ex.getClass().getSimpleName(), HashUtil.sha256Base64(String.valueOf(ex.getMessage()))),
                base.idempotencyKey()
        );
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private String evalString(String spel, StandardEvaluationContext ctx, String def) {
        try {
            Expression e = parser.parseExpression(spel);
            Object v = e.getValue(ctx);
            return v == null ? def : String.valueOf(v);
        } catch (Exception ex) {
            return def;
        }
    }

    private Map<String,String> parseContextPairs(String[] pairs, StandardEvaluationContext ctx) {
        Map<String,String> m = new HashMap<>();
        if (pairs == null) return m;
        for (String p : pairs) {
            if (p == null || p.isBlank()) continue;
            int i = p.indexOf('=');
            if (i < 0) continue;
            String k = p.substring(0, i).trim();
            String expr = p.substring(i+1).trim();
            m.put(k, evalString(expr, ctx, null));
        }
        return m;
    }
}
