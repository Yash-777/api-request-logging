package com.github.yash777.apirequestlogging.demo.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;

/**
 * <h2>{@literal @}ConditionalOnDemoEnvironment</h2>
 *
 * <p>A <strong>composed meta-annotation</strong> that gates any bean or
 * configuration class so it is only created when the application is running
 * as the <em>built-in live demo</em> of this starter — never inside a
 * consumer's application that merely adds the starter as a Maven dependency.</p>
 *
 * <h3>How to use it</h3>
 * <p>Place this annotation on every demo-internal bean instead of
 * {@code @ConditionalOnProperty}:</p>
 * <pre>
 * // BEFORE (wrong — activates in every consumer app that sets enabled=true):
 * {@literal @}ConditionalOnProperty(prefix="api.request.logging", name="enabled", havingValue="true")
 * {@literal @}Service
 * public class OrderService { ... }
 *
 * // AFTER (correct — activates ONLY when DemoApplication.main() is the JVM entry point):
 * {@literal @}ConditionalOnDemoEnvironment
 * {@literal @}Service
 * public class OrderService { ... }
 * </pre>
 *
 * <h3>Two-guard activation model</h3>
 * <p>A bean annotated with {@code @ConditionalOnDemoEnvironment} is created
 * only when <strong>both guards pass simultaneously</strong>:</p>
 *
 * <table border="1" cellpadding="6">
 *   <tr>
 *     <th>#</th><th>Guard</th><th>Mechanism</th><th>Who controls it</th>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@code DemoApplication} class is on the classpath</td>
 *     <td>{@link ConditionalOnClass} — evaluated at context-refresh by Spring</td>
 *     <td>Always true when running this project; present in the starter JAR</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>{@code DemoApplication.getNonConsumer() == true}</td>
 *     <td>{@link DemoEnvironmentCondition} reads the static flag</td>
 *     <td>Set only in {@link DemoApplication#main(String[])} before Spring starts</td>
 *   </tr>
 * </table>
 *
 * <h3>Guard 1 — {@code @ConditionalOnClass}</h3>
 * <p>{@code DemoApplication.class} lives inside the starter JAR, so it is
 * technically present on the classpath of any consumer project that depends
 * on this starter.  Alone this guard does not isolate anything — it is
 * included as a fast-fail hint visible in the Auto-Configuration report and
 * to document the classpath dependency.</p>
 *
 * <h3>Guard 2 — static flag in {@code DemoApplication}</h3>
 * <p>The static field {@link DemoApplication#getNonConsumer()} is {@code false}
 * by default and is set to {@code true} only inside
 * {@link DemoApplication#main(String[])}.  A consumer's own
 * {@code @SpringBootApplication} main class never calls
 * {@code DemoApplication.main()}, so the flag stays {@code false}
 * and all demo beans are skipped.</p>
 *
 * <pre>
 *   Consumer app starts → their main() runs → nonConsumer = false
 *                                               → Guard 2 fails
 *                                               → Demo beans SKIPPED
 *
 *   mvn spring-boot:run → DemoApplication.main() runs → nonConsumer = true
 *                                                          → Both guards pass
 *                                                          → Demo beans CREATED
 * </pre>
 *
 * <h3>Why not {@code @ConditionalOnProperty} alone?</h3>
 * <p>Using the starter's master switch property on demo beans would cause them
 * to activate in every consumer application that sets
 * {@code api.request.logging.enabled=true} — registering unwanted
 * {@code /api/orders} and {@code /api/payments} endpoints in the consumer's
 * context.  The static flag cannot be replicated by any property.</p>
 *
 * <h3>Auto-Configuration report — consumer application (expected)</h3>
 * <pre>
 * Positive matches:
 *   ApiLoggingFilter            matched: {@literal @}ConditionalOnProperty (enabled=true) ✅
 *   RequestBodyCachingFilter    matched: {@literal @}ConditionalOnProperty (enabled=true) ✅
 *   RequestLogCollector         matched: {@literal @}ConditionalOnProperty (enabled=true) ✅
 *
 * Negative matches:
 *   OrderController             did not match: DemoEnvironmentCondition returned false ✅
 *   OrderService                did not match: DemoEnvironmentCondition returned false ✅
 *   PaymentController           did not match: DemoEnvironmentCondition returned false ✅
 *   PaymentService              did not match: DemoEnvironmentCondition returned false ✅
 * </pre>
 *
 * <h3>Auto-Configuration report — mvn spring-boot:run (expected)</h3>
 * <pre>
 * Positive matches:
 *   ApiLoggingFilter            matched ✅
 *   RequestBodyCachingFilter    matched ✅
 *   RequestLogCollector         matched ✅
 *   OrderController             matched: DemoEnvironmentCondition returned true ✅
 *   OrderService                matched: DemoEnvironmentCondition returned true ✅
 *   PaymentController           matched: DemoEnvironmentCondition returned true ✅
 *   PaymentService              matched: DemoEnvironmentCondition returned true ✅
 * </pre>
 *
 * @author yash777
 * @since 1.0.0
 * @see DemoEnvironmentCondition
 * @see DemoApplication#main(String[])
 * @see DemoApplication#getNonConsumer()
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
// Guard 1: fast classpath check — documents the DemoApplication dependency.
// DemoApplication.class is always present in the starter JAR, so this alone
// does not isolate demo beans; Guard 2 (DemoEnvironmentCondition) provides
// the actual runtime isolation.
@ConditionalOnClass(name = "com.github.yash777.apirequestlogging.demo.DemoApplication")
// Guard 2: delegates to DemoEnvironmentCondition, which returns
// DemoApplication.getNonConsumer() — true only when DemoApplication.main()
// was the JVM entry point.
@Conditional(DemoEnvironmentCondition.class)
public @interface ConditionalOnDemoEnvironment {
}