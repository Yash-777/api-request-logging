package com.github.yash777.apirequestlogging.aop;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically captures the controller handler name for every
 * matched {@code @RestController} or {@code @Controller} method invocation.
 *
 * <h3>Output</h3>
 * <p>Adds the following field to the {@code INCOMING} log block:</p>
 * <pre>
 *   controllerHandler: "UserController#listUsers"
 * </pre>
 *
 * <h3>Activation conditions</h3>
 * <ol>
 *   <li>{@code api.request.logging.enabled=true}</li>
 *   <li>{@code api.request.logging.aop.controller-handler-enabled=true} (default)</li>
 *   <li>{@code spring-boot-starter-aop} must be on the classpath
 *       ({@code @ConditionalOnClass(ProceedingJoinPoint.class)})</li>
 * </ol>
 *
 * <p>If {@code spring-aop} is absent the aspect is silently skipped — no error,
 * no warning, just no {@code controllerHandler} field in the log.</p>
 *
 * <h3>Required consumer dependency</h3>
 * <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-aop</artifactId>
 * </dependency>
 * }</pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see RequestLogCollector#LOG_CONTROLLER_HANDLER
 */
@Aspect
@Component
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class ControllerHandlerAspect {

    private final RequestLogCollector collector;
    private final ApiRequestLoggingProperties properties;

    public ControllerHandlerAspect(RequestLogCollector collector,
                                   ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    /**
     * Pointcut matching all public methods on classes annotated with
     * {@code @RestController} or {@code @Controller}.
     */
    @Pointcut("(@within(org.springframework.web.bind.annotation.RestController) || "
            + " @within(org.springframework.stereotype.Controller))")
    public void controllerMethods() {}

    /**
     * Around advice that captures the handler name before delegating,
     * then re-throws any exception unchanged so Spring MVC error handling
     * and {@code @ExceptionHandler} methods work as normal.
     *
     * @param pjp the proceeding join point
     * @return the value returned by the controller method
     * @throws Throwable re-thrown from the controller method unchanged
     */
    @Around("controllerMethods()")
    public Object captureHandler(ProceedingJoinPoint pjp) throws Throwable {
        if (!properties.getAop().isControllerHandlerEnabled()) {
            return pjp.proceed();
        }

        // "UserController#listUsers"
        String handler = pjp.getTarget().getClass().getSimpleName()
                       + "#" + pjp.getSignature().getName();

        collector.addLog(RequestLogCollector.INCOMING_KEY,
                         RequestLogCollector.LOG_CONTROLLER_HANDLER,
                         handler);
        return pjp.proceed();
    }
}
