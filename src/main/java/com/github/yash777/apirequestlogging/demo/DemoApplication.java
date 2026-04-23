package com.github.yash777.apirequestlogging.demo;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.github.yash777.apirequestlogging.demo.diagnostic.StartupDiagnosticUtil;

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
	 * Guards {@link StartupDiagnosticUtil#print(String[])} against double execution.
	 *
	 * <p><b>Problem:</b> DevTools calls {@code main()} twice on first launch —
	 * once from the base JVM classloader, then immediately from
	 * {@code RestartClassLoader} — producing two identical banners:
	 * <pre>
	 * ╔══ STARTUP DIAGNOSTIC REPORT ══╗  ← base classloader (unwanted)
	 * ╔══ STARTUP DIAGNOSTIC REPORT ══╗  ← RestartClassLoader (wanted)
	 * </pre>
	 *
	 * <p><b>Solution:</b> {@link java.util.concurrent.atomic.AtomicBoolean#compareAndSet}
	 * allows only the first {@code main()} invocation to print; all subsequent
	 * calls (DevTools restarts) are silently skipped:
	 * <pre>
	 * ╔══ STARTUP DIAGNOSTIC REPORT ══╗  ← printed once ✅
	 * </pre>
	 */
	private static final AtomicBoolean DIAGNOSTIC_PRINTED = new AtomicBoolean(false);
    
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
     * beans ({@code OrderController}, {@code OrderService}, etc.) should be created.</p>
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
     *     <strong>Set {@code nonConsumer = true}</strong> — done <em>before</em>
     *     {@code SpringApplication.run()} so that
     *     {@link com.github.yash777.apirequestlogging.demo.condition.DemoEnvironmentCondition#matches}
     *     returns {@code true} during the very first context-refresh pass.
     *     If set after {@code run()}, Spring would have already evaluated all
     *     {@code @Conditional} annotations and the demo beans would be absent.
     *   </li>
     *   <li>
     *     <strong>{@code SpringApplication.run()}</strong> — starts the full
     *     Spring Boot context: component scan, auto-configuration, filter
     *     registration, and embedded Tomcat.
     *   </li>
     * </ol>
     *
     * <h4>Why not {@code SpringApplicationBuilder.properties()}?</h4>
     * <p>{@code SpringApplicationBuilder.properties()} injects values into the Spring
     * {@link org.springframework.core.env.Environment} after the
     * {@code ApplicationContext} starts building — by that point some
     * {@code @Conditional} annotations may have already been evaluated.
     * Setting the static {@code nonConsumer} flag directly before {@code run()}
     * guarantees it is visible to {@link DemoEnvironmentCondition#matches} on every
     * evaluation without relying on property-source ordering.</p>
     *
     * @param args command-line arguments forwarded verbatim to
     *             {@link SpringApplication#run(Class, String...)}
     */
    public static void main(String[] args) {
        // Set BEFORE SpringApplication.run() — DemoEnvironmentCondition.matches()
        // reads this flag as its definitive answer. Only DemoApplication.main()
        // can set it to true, so a consumer application can never accidentally
        // activate demo beans regardless of their properties configuration.
        nonConsumer = true;

        /*
         * Print StartupDiagnosticUtil banner only once on DevTools startup
         * 
         * DevTools calls main() twice on first launch — base JVM classloader then
         * RestartClassLoader — causing the diagnostic banner to appear twice.
         * Subsequent restarts (live-reload) correctly print it once.
         * 
         * Added AtomicBoolean guard: compareAndSet(false, true) allows only the
         * first main() invocation to print; all subsequent calls are skipped.
         */
        if (DIAGNOSTIC_PRINTED.compareAndSet(false, true)) {
            StartupDiagnosticUtil.print(args);
        }
        
        SpringApplication.run(DemoApplication.class, args);
    }
}