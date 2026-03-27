package com.github.yash777.apirequestlogging.demo.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;

/**
 * <h2>@ConditionalOnDemoEnvironment</h2>
 *
 * <p>A <strong>composed meta-annotation</strong> that gates any bean or
 * configuration class so it is only created when the application is running
 * as the <em>built-in live demo</em> of this starter — never inside a
 * consumer's application that merely adds the starter as a Maven dependency.</p>
 *
 * <h3>How to use it</h3>
 * <p>Replace {@code @ConditionalOnProperty} on every demo-internal bean with
 * this single annotation:</p>
 * <pre>
 * // BEFORE (wrong — activates in every consumer app):
 * {@literal @}ConditionalOnProperty(prefix="api.request.logging", name="enabled", havingValue="true")
 * {@literal @}Service
 * public class OrderService { ... }
 *
 * // AFTER (correct — activates ONLY during mvn spring-boot:run):
 * {@literal @}ConditionalOnDemoEnvironment
 * {@literal @}Service
 * public class OrderService { ... }
 * </pre>
 *
 * <h3>Three-guard activation model</h3>
 * <p>A bean annotated with {@code @ConditionalOnDemoEnvironment} is created
 * only when <strong>all three guards pass simultaneously</strong>:</p>
 *
 * <table border="1" cellpadding="6">
 *   <tr>
 *     <th>#</th><th>Guard</th><th>Mechanism</th><th>Who controls it</th>
 *   </tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@code DemoApplication} class is on the classpath</td>
 *     <td>{@link ConditionalOnClass}</td>
 *     <td>Always true — class is inside the starter JAR itself</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>{@code api.request.logging.live-demo=true}</td>
 *     <td>{@link DemoEnvironmentCondition} reads Spring {@code Environment}</td>
 *     <td>Set exclusively via {@code pom.xml} {@code <jvmArguments>}</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>{@code DemoApplication.getNonConsumer() == true}</td>
 *     <td>{@link DemoEnvironmentCondition} reads static flag</td>
 *     <td>Set in {@link DemoApplication#main(String[])} before Spring starts</td>
 *   </tr>
 * </table>
 *
 * <h3>Why three guards instead of one?</h3>
 *
 * <h4>Guard 1 — {@code @ConditionalOnClass}</h4>
 * <p>{@code DemoApplication.class} lives inside the starter JAR, so it is
 * technically present on the classpath of any consumer project that
 * depends on this starter.  Alone, this guard does not isolate anything.
 * It is included for documentation value and as a fast-fail hint in the
 * Auto-Configuration report.</p>
 *
 * <h4>Guard 2 — {@code api.request.logging.live-demo} property</h4>
 * <p>A consumer adding this starter to their {@code pom.xml} would never set
 * {@code api.request.logging.live-demo=true} — they have no reason to.
 * Only the {@code spring-boot-maven-plugin}'s {@code <jvmArguments>} block
 * in this project's own {@code pom.xml} sets it, and only when
 * {@code mvn spring-boot:run} is executed.</p>
 *
 * <h4>Guard 3 — {@code DemoApplication.getNonConsumer()} static flag</h4>
 * <p>Properties can theoretically be copied, guessed, or set by accident.
 * The static boolean field in {@link DemoApplication} is set
 * <em>programmatically</em> in the {@code main} method, which only executes
 * when Java's JVM directly launches {@code DemoApplication} as the entry point.
 * A consumer's own {@code @SpringBootApplication} main class never calls
 * {@code DemoApplication.main()}, so the flag stays {@code false}.</p>
 *
 * <pre>
 *   Consumer app starts → their own main() runs → nonConsumer stays false
 *                                                   → Guard 3 fails
 *                                                   → Demo beans SKIPPED ✅
 *
 *   mvn spring-boot:run → DemoApplication.main() runs → nonConsumer = true
 *                                                         → all 3 guards pass
 *                                                         → Demo beans CREATED ✅
 * </pre>
 *
 * <h3>Why not {@code @ConditionalOnProperty} alone?</h3>
 * <p>Using only the starter's master switch property would cause demo beans
 * to activate in consumer applications:</p>
 * <pre>
 * // WRONG — demo beans leak into consumer's context:
 * {@literal @}ConditionalOnProperty(prefix="api.request.logging", name="enabled", havingValue="true")
 * </pre>
 * <p>The consumer sets {@code api.request.logging.enabled=true} to enable the
 * starter's filters and collector. If demo beans share that condition, they
 * register {@code /api/orders} and {@code /api/payments} endpoints in the
 * consumer's application — completely unacceptable for a library.</p>
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
 * <h3>Annotation composition</h3>
 * <p>This annotation is itself meta-annotated with:</p>
 * <ul>
 *   <li>{@link ConditionalOnClass} — fast classpath guard (Guard 1)</li>
 *   <li>{@link Conditional}{@code (DemoEnvironmentCondition.class)} — evaluates
 *       Guards 2 and 3 at context-refresh time</li>
 *   <li>{@link AutoConfigureAfter}{@code (DemoApplication.class)} — ensures Spring
 *       processes {@code DemoApplication}'s own bean definitions before evaluating
 *       this condition, so the static flag is guaranteed to be set</li>
 * </ul>
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
// Guard 1: DemoApplication class must be on the classpath.
// Always true when running this project; present but not sufficient alone —
// the class lives inside the starter JAR so consumers also have it on their
// classpath. Guards 2 and 3 (inside DemoEnvironmentCondition) provide the
// actual isolation.
@ConditionalOnClass(name = "com.github.yash777.apirequestlogging.demo.DemoApplication")
// Guards 2 + 3: delegate to DemoEnvironmentCondition which checks BOTH
// the 'api.request.logging.live-demo' property AND the static nonConsumer flag
// set in DemoApplication.main() before Spring starts.
@Conditional(DemoEnvironmentCondition.class)
// Ensures DemoApplication's own bean definitions are fully processed before
// this condition is evaluated — guarantees the static nonConsumer flag is set.
@AutoConfigureAfter(com.github.yash777.apirequestlogging.demo.DemoApplication.class)
public @interface ConditionalOnDemoEnvironment {
}