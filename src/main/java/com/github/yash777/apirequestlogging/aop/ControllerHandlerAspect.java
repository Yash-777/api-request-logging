package com.github.yash777.apirequestlogging.aop;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.collector.RequestLogCollectorApi;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties.AopProperties;

/**
 * AOP aspect that automatically logs controller invocations as a single
 * structured line per request, and populates the {@code controllerHandler}
 * field of the per-request {@code INCOMING} block in {@link RequestLogCollector}.
 *
 * <h3>Output shape</h3>
 *
 * <p>The dedicated AOP log line always contains a {@code handler=[...]} field
 * and a {@code timeTaken=[N ms]} field. Optional fields are appended only when
 * their corresponding toggle is enabled or when an exception is thrown:</p>
 *
 * <pre>
 * [CONTROLLER-AOP] handler=[OrderController#createOrder] timeTaken=[12 ms]
 * [CONTROLLER-AOP] handler=[OrderController#createOrder] args=[request=OrderRequest{...}] timeTaken=[12 ms]
 * [CONTROLLER-AOP] handler=[OrderController#createOrder] timeTaken=[648 ms] exception=[org.springframework.web.client.ResourceAccessException: I/O error ...]
 * </pre>
 *
 * <h3>{@code controllerHandler} in the INCOMING block</h3>
 *
 * <p>The same {@link HandlerFormat}-aware string is also written into the
 * {@code INCOMING} log block under {@link RequestLogCollector#LOG_CONTROLLER_HANDLER},
 * so the consolidated request log shows the handler at the verbosity the
 * caller chose:</p>
 *
 * <pre>
 *   controllerHandler: OrderController#createOrder                                      // SIMPLE
 *   controllerHandler: OrderController#createOrder(OrderRequest)                        // WITH_PARAMS
 *   controllerHandler: com.github.yash777...OrderController#createOrder                 // QUALIFIED
 *   controllerHandler: com.github.yash777...OrderController#createOrder(...OrderRequest)// FULL
 * </pre>
 *
 * <h3>Verbosity is one knob</h3>
 *
 * <p>Rather than independent flags for class-package and param-package, the
 * aspect uses a single {@link HandlerFormat} enum, since the realistic
 * progression {@code SIMPLE → WITH_PARAMS → QUALIFIED → FULL} is what users
 * actually want to choose between. See {@link HandlerFormat} for details.</p>
 *
 * <h3>Slow-call escalation</h3>
 *
 * <p>If {@code api.request.logging.aop.slow-threshold-ms} is set to a
 * non-negative value, successful calls exceeding that duration are logged
 * at {@code WARN} instead of {@code INFO}. Failed calls are always logged
 * at {@code ERROR}.</p>
 *
 * <h3>Activation conditions</h3>
 * <ol>
 *   <li>{@code api.request.logging.enabled=true}</li>
 *   <li>{@code api.request.logging.aop.controller-handler-enabled=true} (default)</li>
 *   <li>{@code spring-boot-starter-aop} on the classpath</li>
 * </ol>
 *
 * <p>If {@code spring-aop} is absent the aspect is silently skipped — no error,
 * no warning, just no AOP log line and no {@code controllerHandler} field.</p>
 *
 * <h3>Required consumer dependency</h3>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-aop</artifactId>
 * </dependency>
 * }</pre>
 *
 * <h3>Note on parameter names</h3>
 *
 * <p>{@code args=[...]} uses {@link Parameter#getName()}, which only returns
 * meaningful names if the consuming application is compiled with the
 * {@code -parameters} flag (Maven: {@code <parameters>true</parameters>} on
 * the compiler plugin). Otherwise names appear as {@code arg0}, {@code arg1},
 * …</p>
 *
 * @author Yash
 * @since 1.1.0
 * @see HandlerFormat
 * @see RequestLogCollector#LOG_CONTROLLER_HANDLER
 */
@Aspect
@Component
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
@ConditionalOnProperty(
    prefix      = "api.request.logging",
    name        = "enabled",
    havingValue = "true"
)
public class ControllerHandlerAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerHandlerAspect.class);

    private final RequestLogCollectorApi      collector;
    private final ApiRequestLoggingProperties properties;

    public ControllerHandlerAspect(RequestLogCollectorApi      collector,
                                   ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Pointcut
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pointcut matching all public methods on classes annotated with
     * {@code @RestController} or {@code @Controller}.
     */
    @Pointcut("(@within(org.springframework.web.bind.annotation.RestController) || "
            + " @within(org.springframework.stereotype.Controller))")
    public void controllerMethods() {}

    // ─────────────────────────────────────────────────────────────────────
    //  Around advice
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Times the controller method, captures handler metadata, populates the
     * {@code INCOMING} block with the formatted handler name, and emits a
     * single AOP log line. Any thrown exception is logged and re-thrown so
     * Spring MVC error handling and {@code @ExceptionHandler} methods are
     * unaffected.
     */
    @Around("controllerMethods()")
    public Object captureHandler(ProceedingJoinPoint pjp) throws Throwable {

        AopProperties aop = properties.getAop();
        if (!aop.isControllerHandlerEnabled()) {
            return pjp.proceed();
        }

        // Resolve method/class metadata once, up front. MethodSignature is
        // available before pjp.proceed(), so we can use the same formatted
        // handler string for both the collector and the SLF4J log line.
        Class<?>        targetClass     = pjp.getTarget().getClass();
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method          method          = methodSignature.getMethod();
        HandlerFormat   format          = aop.getHandlerFormat();

        String handler = formatHandler(targetClass, method, format);

        // Populate the per-request INCOMING block at the configured verbosity.
        try {
            collector.addLog(RequestLogCollector.INCOMING_KEY,
                             RequestLogCollector.LOG_CONTROLLER_HANDLER,
                             handler);
        } catch (Exception ignored) {
            // Collector failure must never break the request.
        }

        StopWatch sw = new StopWatch();
        sw.start();
        try {
            Object result = pjp.proceed();
            sw.stop();
            emitLog(handler, method, pjp.getArgs(), sw.getTotalTimeMillis(), null, aop);
            return result;
        } catch (Throwable ex) {
            sw.stop();
            emitLog(handler, method, pjp.getArgs(), sw.getTotalTimeMillis(), ex, aop);
            throw ex; // always re-throw
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Log construction
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the AOP log line dynamically so optional fields never appear
     * empty, then dispatches to the appropriate SLF4J level
     * (INFO / WARN / ERROR).
     *
     * @param handler   pre-formatted handler string (already at the configured verbosity)
     * @param method    the controller method (for return-type and arg names)
     * @param args      runtime argument values (for {@code args=[...]})
     * @param elapsedMs elapsed wall-clock time in milliseconds
     * @param thrownEx  thrown exception, or {@code null} on success
     * @param aop       AOP configuration
     */
    private void emitLog(String        handler,
                         Method        method,
                         Object[]      args,
                         long          elapsedMs,
                         Throwable     thrownEx,
                         AopProperties aop) {

        StringBuilder pattern = new StringBuilder("[CONTROLLER-AOP] handler=[{}]");
        java.util.List<Object> values = new java.util.ArrayList<>(5);
        values.add(handler);

        // -- args=[...] (optional) ------------------------------------------
        if (aop.isIncludeArgs()) {
            pattern.append(" args=[{}]");
            values.add(buildArgList(method.getParameters(), args));
        }

        // -- returnType=[...] (optional) ------------------------------------
        if (aop.isIncludeReturnType()) {
            pattern.append(" returnType=[{}]");
            values.add(resolveTypeName(method.getReturnType(), useFqn(aop.getHandlerFormat())));
        }

        // -- timeTaken=[...] (always) ---------------------------------------
        pattern.append(" timeTaken=[{} ms]");
        values.add(elapsedMs);

        // -- exception=[...] (only on failure) ------------------------------
        if (thrownEx != null) {
            pattern.append(" exception=[{}]");
            values.add(formatException(thrownEx));
        }

        // -- Dispatch -------------------------------------------------------
        if (thrownEx != null) {
            log.error(pattern.toString(), values.toArray());
        } else if (aop.getSlowThresholdMs() >= 0 && elapsedMs > aop.getSlowThresholdMs()) {
            log.warn(pattern.toString(), values.toArray());
        } else {
            log.info(pattern.toString(), values.toArray());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Handler formatting
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Produces the handler string at the requested verbosity using the
     * canonical {@code Class#method} notation. Used both for the SLF4J log
     * line ({@code handler=[...]}) and for the {@code controllerHandler}
     * field in the INCOMING block.
     */
    private String formatHandler(Class<?> targetClass, Method method, HandlerFormat format) {
        boolean fqnClass   = format == HandlerFormat.QUALIFIED || format == HandlerFormat.FULL;
        boolean withParams = format == HandlerFormat.WITH_PARAMS || format == HandlerFormat.FULL;
        boolean fqnParams  = format == HandlerFormat.FULL;

        String className = fqnClass ? targetClass.getName() : targetClass.getSimpleName();

        StringBuilder sb = new StringBuilder(className).append('#').append(method.getName());
        if (withParams) {
            sb.append('(')
              .append(buildParameterTypeList(method.getParameters(), fqnParams))
              .append(')');
        }
        return sb.toString();
    }

    /** Whether the chosen format implies fully-qualified type names. */
    private boolean useFqn(HandlerFormat format) {
        return format == HandlerFormat.QUALIFIED || format == HandlerFormat.FULL;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Parameter helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds a comma-separated list of parameter <em>types</em> only:
     * {@code OrderRequest, String, int} or, with package names enabled,
     * {@code com.github.yash777.dto.OrderRequest, java.lang.String, int}.
     */
    private String buildParameterTypeList(Parameter[] parameters, boolean includePackage) {
        if (parameters == null || parameters.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(resolveTypeName(parameters[i].getType(), includePackage));
        }
        return sb.toString();
    }

    /**
     * Builds the {@code args=[...]} body: comma-separated {@code name=value}
     * pairs with a safe {@code toString()} so the aspect never fails the
     * request.
     */
    private String buildArgList(Parameter[] parameters, Object[] args) {
        if (parameters == null || parameters.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) sb.append(", ");
            String name = parameters[i].isNamePresent()
                    ? parameters[i].getName()
                    : "arg" + i;
            Object value = (args != null && i < args.length) ? args[i] : "<unavailable>";
            sb.append(name).append('=').append(safeToString(value));
        }
        return sb.toString();
    }

    /**
     * Resolves a {@link Class} to its FQN or simple name. Primitives and
     * arrays use their canonical name regardless of the flag (no package).
     */
    private String resolveTypeName(Class<?> type, boolean includePackage) {
        if (type.isPrimitive() || type.isArray()) {
            return type.getCanonicalName() != null ? type.getCanonicalName() : type.getName();
        }
        return includePackage ? type.getName() : type.getSimpleName();
    }

    /**
     * Catches any {@code toString()} failure (lazy JPA proxies, streams) so
     * the aspect itself never breaks a request.
     */
    private String safeToString(Object value) {
        if (value == null) return "null";
        try {
            return value.toString();
        } catch (Exception ex) {
            return "<toString-failed: " + value.getClass().getName() + ">";
        }
    }

    /** Format a thrown exception as {@code FQCN: message}. */
    private String formatException(Throwable ex) {
        return ex.getClass().getName() + ": " + ex.getMessage();
    }
}