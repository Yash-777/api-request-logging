package com.github.yash777.apirequestlogging.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h2>DemoApplication — Live Demo entry point</h2>
 *
 * <p>The {@code main} class for the built-in live demo of the
 * <strong>api-request-logging-spring-boot-starter</strong>.
 * Running this application shows the full HTTP request/response log
 * lifecycle in the console for every API call.</p>
 *
 * <h3>Run the demo</h3>
 * <pre>
 * mvn spring-boot:run
 * </pre>
 *
 * <h3>Demo URLs (context-path set by pom.xml jvmArguments)</h3>
 * <pre>
 *   Swagger UI  → http://localhost:8080/api-request-logging-demo/swagger-ui/index.html
 *   Actuator    → http://localhost:8080/api-request-logging-demo/actuator/health
 *   POST order  → http://localhost:8080/api-request-logging-demo/api/orders
 *   POST charge → http://localhost:8080/api-request-logging-demo/api/payments/charge
 * </pre>
 *
 * <h3>Sample cURL commands</h3>
 * <pre>
 * # Full order chain — 3 log sections: INCOMING + InventoryService + PaymentGateway
 * curl -X POST http://localhost:8080/api-request-logging-demo/api/orders \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: my-trace-001" \
 *      -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'
 *
 * # GET order — 2 log sections: INCOMING + OrderDB
 * curl http://localhost:8080/api-request-logging-demo/api/orders/ORD-ABC12345 \
 *      -H "X-Request-ID: my-trace-002"
 *
 * # Direct payment — 2 log sections: INCOMING + PaymentGateway/charge
 * curl -X POST http://localhost:8080/api-request-logging-demo/api/payments/charge \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: my-trace-003" \
 *      -d '{"orderId":"ORD-99","amount":250.00}'
 * </pre>
 *
 * <h3>The nonConsumer flag — how demo beans are isolated from consumer apps</h3>
 *
 * <p>All demo-internal beans ({@code OrderController}, {@code OrderService},
 * {@code PaymentController}, {@code PaymentService}) carry
 * {@code @ConditionalOnDemoEnvironment}.  That annotation delegates to
 * {@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition},
 * which ultimately returns {@link #getNonConsumer()}.</p>
 *
 * <p>The flag is {@code false} by default (class-loaded value).
 * It is set to {@code true} only inside {@link #main(String[])} — meaning
 * it is {@code true} only when the JVM launched <em>this</em> class as its
 * entry point.  A consumer application's own {@code main()} method never
 * calls {@code DemoApplication.main()}, so the flag stays {@code false}
 * and all demo beans are skipped in the consumer's context.</p>
 *
 * <pre>
 *   Consumer's app starts  →  their main() runs  →  nonConsumer = false
 *                                                     → DemoEnvironmentCondition = false
 *                                                     → OrderController SKIPPED ✅
 *
 *   mvn spring-boot:run    →  DemoApplication.main() runs  →  nonConsumer = true
 *                                                              → DemoEnvironmentCondition = true
 *                                                              → OrderController CREATED ✅
 * </pre>
 *
 * @author Yash
 * @since 1.0.0
 * @see com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment
 * @see com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition
 */
@SpringBootApplication
public class DemoApplication {

    /**
     * Static flag that distinguishes the demo JVM process from any consumer
     * application that has this starter on its classpath.
     *
     * <ul>
     *   <li>{@code false} (default) — the class was loaded by a consumer application;
     *       {@link #main(String[])} was never invoked as the JVM entry point.</li>
     *   <li>{@code true} — {@link #main(String[])} ran, proving this JVM process
     *       was started specifically to run the built-in demo.</li>
     * </ul>
     *
     * <p>Read by
     * {@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition#matches}
     * during Spring's application-context refresh phase to decide whether demo
     * beans ({@code OrderController}, {@code OrderService}, …) should be created.</p>
     *
     * <p><strong>Thread safety:</strong> written once in {@link #main(String[])}
     * (single-threaded JVM startup) <em>before</em> {@code SpringApplication.run()}
     * is called, so it is always visible to the context-refresh thread that reads
     * it via the Java memory model's program-order rule.
     * No synchronisation is required.</p>
     */
    private static boolean nonConsumer = false;

    /**
     * Returns {@code true} when this JVM process was started by launching
     * {@link #main(String[])} directly — i.e., this is the built-in demo run,
     * not a consumer application.
     *
     * <p>This method is the final authority used by
     * {@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition}
     * to decide whether to register demo-only beans.  No external property,
     * environment variable, or annotation can set this flag to {@code true} —
     * only calling {@link #main(String[])} can do so.</p>
     *
     * @return {@code true} if {@link #main(String[])} was the JVM entry point;
     *         {@code false} in all other cases (consumer application, test context, etc.)
     */
    public static boolean getNonConsumer() {
        return nonConsumer;
    }

    /**
     * Application entry point for the built-in live demo.
     *
     * <h4>Startup sequence — why order matters</h4>
     * <ol>
     *   <li>
     *     <strong>Set {@link System#setProperty} flags</strong> — done
     *     <em>before</em> {@code SpringApplication.run()} so that
     *     {@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition}
     *     can read them from the Spring {@link org.springframework.core.env.Environment}
     *     during the very first context-refresh pass.  If set after {@code run()},
     *     Spring would have already evaluated all {@code @Conditional} annotations
     *     and the demo beans would be absent.
     *   </li>
     *   <li>
     *     <strong>Set {@code nonConsumer = true}</strong> — the static boolean
     *     that {@code DemoEnvironmentCondition.matches()} uses as its definitive
     *     answer.  Setting it before {@code run()} provides the same
     *     happens-before guarantee as the system properties.
     *   </li>
     *   <li>
     *     <strong>{@code SpringApplication.run()}</strong> — starts the full
     *     Spring Boot context: component scan, auto-configuration, filter
     *     registration, and embedded Tomcat.
     *   </li>
     * </ol>
     *
     * <h4>System properties set in this method</h4>
     * <table border="1" cellpadding="6">
     *   <tr><th>Key</th><th>Value</th><th>Read by</th></tr>
     *   <tr>
     *     <td>{@code internal.demo.bean.active}</td>
     *     <td>{@code "true"}</td>
     *     <td>Reserved for future conditional use; currently informational</td>
     *   </tr>
     *   <tr>
     *     <td>{@code internal.app.non-consumer}</td>
     *     <td>{@code "true"}</td>
     *     <td>{@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition}
     *         — checked alongside the {@code api.request.logging.live-demo} property</td>
     *   </tr>
     * </table>
     *
     * <h4>Why {@code System.setProperty} and not {@code SpringApplicationBuilder.properties}?</h4>
     * <p>{@code SpringApplicationBuilder.properties()} injects values into the Spring
     * {@link org.springframework.core.env.Environment} after the
     * {@code ApplicationContext} starts building — by that point some
     * {@code @Conditional} annotations may have already been evaluated.
     * {@link System#setProperty} writes to the JVM system-property layer, which
     * Spring reads at the very beginning of context refresh via
     * {@code SystemEnvironmentPropertySource}, ensuring the flags are visible
     * to all {@code Condition} evaluations.</p>
     *
     * <h4>Alternative (works equally well)</h4>
     * <pre>
     * new SpringApplicationBuilder(DemoApplication.class)
     *     .properties("internal.app.non-consumer=true")
     *     .run(args);
     * </pre>
     * <p>This is the idiomatic Spring Boot way and also sets the property before
     * condition evaluation.  Both approaches are equivalent.</p>
     *
     * @param args command-line arguments forwarded verbatim to
     *             {@link SpringApplication#run(Class, String...)}
     */
    public static void main(String[] args) {
        // ── Step 1: Set JVM system properties BEFORE Spring starts ────────
        // DemoEnvironmentCondition reads these during @Conditional evaluation
        // inside SpringApplication.run(). They must be set before run() is
        // called, otherwise Spring evaluates conditions before seeing the flags.
        System.setProperty("internal.demo.bean.active", "true");
        System.setProperty("internal.app.non-consumer", "true");

        // ── Step 2: Set the static flag ───────────────────────────────────
        // DemoEnvironmentCondition.matches() returns this flag as its final
        // answer. It is the strongest isolation guard: only DemoApplication.main()
        // can set it to true, so a consumer application can never accidentally
        // activate demo beans, regardless of their properties configuration.
        nonConsumer = true;

        // ── Step 3: Start the Spring Boot application ─────────────────────
        SpringApplication.run(DemoApplication.class, args);
    }
}