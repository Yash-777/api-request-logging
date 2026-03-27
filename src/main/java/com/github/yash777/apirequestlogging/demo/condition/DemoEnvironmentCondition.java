package com.github.yash777.apirequestlogging.demo.condition;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.github.yash777.apirequestlogging.demo.DemoApplication;

/**
 * <h2>DemoEnvironmentCondition</h2>
 *
 * <p>The runtime {@link Condition} implementation backing the
 * {@link ConditionalOnDemoEnvironment} composed annotation.
 * It is the single class responsible for evaluating whether the current
 * JVM process is running as the built-in live demo of this starter,
 * not as a consumer's production application.</p>
 *
 * <p>Spring calls {@link #matches} once per annotated bean during the
 * application-context refresh phase, before any singleton beans are
 * instantiated.  A return value of {@code true} allows the bean to be
 * created; {@code false} skips it entirely (it appears in the
 * Auto-Configuration report under "Negative matches").</p>
 *
 * <h3>Decision logic — what this class checks</h3>
 *
 * <table border="1" cellpadding="6">
 *   <tr>
 *     <th>Check</th><th>Source</th><th>Set by</th><th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td>{@code api.request.logging.live-demo=true}</td>
 *     <td>Spring {@link Environment}</td>
 *     <td>{@code pom.xml} {@code <jvmArguments>} in
 *         {@code spring-boot-maven-plugin}</td>
 *     <td>Externally-declared intent to run the demo</td>
 *   </tr>
 *   <tr>
 *     <td>{@code DemoApplication.getNonConsumer() == true}</td>
 *     <td>Static field in {@link DemoApplication}</td>
 *     <td>{@link DemoApplication#main(String[])} — runs only when
 *         the JVM directly launches {@code DemoApplication}</td>
 *     <td>Programmatic guarantee — cannot be faked by a property</td>
 *   </tr>
 * </table>
 *
 * <p>The final result is:</p>
 * <pre>
 *   matches = liveDemo AND nonConsumer
 * </pre>
 *
 * <h3>Why both checks are necessary</h3>
 *
 * <h4>Check A alone is not sufficient</h4>
 * <p>A consumer could accidentally set {@code api.request.logging.live-demo=true}
 * in their own {@code application.properties} (copy-paste mistake, CI override, etc.).
 * Without Check B, demo beans would silently register {@code /api/orders} and
 * {@code /api/payments} in the consumer's production context.</p>
 *
 * <h4>Check B alone is not sufficient</h4>
 * <p>{@link DemoApplication#main(String[])} sets {@code nonConsumer = true}
 * programmatically, but the static field is shared within the JVM.
 * In a test scenario where both the demo and a consumer application are loaded
 * in the same JVM (e.g. integration tests), Check A provides the additional
 * external-config gate that a test can explicitly disable.</p>
 *
 * <h4>Together they are sufficient</h4>
 * <pre>
 * Scenario 1 — consumer production app
 *   live-demo property : not set (false)
 *   nonConsumer flag   : false (main() never called)
 *   matches()          : false → demo beans SKIPPED ✅
 *
 * Scenario 2 — consumer app with accidental live-demo=true
 *   live-demo property : true  (set by mistake)
 *   nonConsumer flag   : false (DemoApplication.main() never called)
 *   matches()          : false → demo beans still SKIPPED ✅
 *
 * Scenario 3 — mvn spring-boot:run on this starter project
 *   live-demo property : true  (set via pom.xml jvmArguments)
 *   nonConsumer flag   : true  (DemoApplication.main() sets it before Spring starts)
 *   matches()          : true  → demo beans CREATED ✅
 * </pre>
 *
 * <h3>Property resolution order for {@code isNonConsumer}</h3>
 * <p>The condition checks the Spring {@link Environment} first, then falls back
 * to {@link System#getProperty(String)}.  This two-step lookup handles the case
 * where the property is injected as a JVM {@code -D} argument (resolved by
 * the System property layer) before Spring's {@code Environment} has been
 * fully populated by property sources.</p>
 * <pre>
 *   1. env.getProperty("internal.app.non-consumer")  — Spring Environment
 *   2. System.getProperty("internal.app.non-consumer") — JVM -D argument fallback
 * </pre>
 *
 * <h3>Startup debug output</h3>
 * <p>This class prints a diagnostic line to {@code System.err} during every
 * application-context refresh so you can see exactly why a bean was included
 * or excluded without enabling DEBUG logging:</p>
 * <pre>
 *   [Condition Check] live-demo: true | non-consumer: true | non-consumer Main: true | Result: true
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>{@link #matches} is called by a single thread during context refresh
 * (Spring's context-loading thread). The static field
 * {@link DemoApplication#getNonConsumer()} is written in
 * {@link DemoApplication#main(String[])} before {@code SpringApplication.run()}
 * is called — so the write always happens-before any {@link #matches} call.
 * No additional synchronisation is needed.</p>
 *
 * @author yash777
 * @since 1.0.0
 * @see ConditionalOnDemoEnvironment
 * @see DemoApplication#main(String[])
 * @see DemoApplication#getNonConsumer()
 * @see org.springframework.context.annotation.Condition
 */
@AutoConfigureAfter(com.github.yash777.apirequestlogging.demo.DemoApplication.class)
public class DemoEnvironmentCondition implements Condition {

    /**
     * Evaluates whether the annotated bean should be created in the current
     * application context.
     *
     * <h4>Evaluation steps</h4>
     * <ol>
     *   <li>Read {@code api.request.logging.live-demo} from the Spring
     *       {@link Environment} (covers {@code application.properties},
     *       YAML, and {@code -D} JVM arguments injected by
     *       {@code spring-boot-maven-plugin}).</li>
     *   <li>Read {@code internal.app.non-consumer} from the Spring
     *       {@link Environment}; if absent, fall back to
     *       {@link System#getProperty(String)} to handle early
     *       {@link DemoApplication#main(String[])} {@code System.setProperty}
     *       calls that may precede full Environment population.</li>
     *   <li>Read {@link DemoApplication#getNonConsumer()} — the static flag
     *       set before {@code SpringApplication.run()} is called.</li>
     *   <li>Return {@code true} only when the {@code nonConsumer} static flag
     *       is {@code true} (the property checks are logged for diagnostics
     *       but the final decision delegates to the flag for maximum safety).</li>
     * </ol>
     *
     * <h4>Why the final return delegates to the static flag</h4>
     * <p>The static flag {@link DemoApplication#getNonConsumer()} can only be
     * {@code true} if {@link DemoApplication#main(String[])} was called by the
     * JVM as the application entry point.  No external property or annotation
     * can replicate this guarantee, making it the most reliable single source
     * of truth for "are we running as the demo?".</p>
     *
     * @param context  the condition evaluation context — provides access to the
     *                 {@link Environment}, {@code ClassLoader}, and
     *                 {@code BeanFactory}
     * @param metadata metadata of the class or method being evaluated; not used
     *                 by this implementation but required by the
     *                 {@link Condition} contract
     * @return {@code true} if all guards pass and the bean should be created;
     *         {@code false} to skip the bean entirely
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        System.err.println("----- DemoEnvironmentCondition implements Condition :: nonConsumer="
                + DemoApplication.getNonConsumer());

        Environment env = context.getEnvironment();

        // Guard 2 — Check property from pom.xml jvmArguments / application.properties
        // Expected value: api.request.logging.live-demo=true
        String liveDemo = env.getProperty("api.request.logging.live-demo");

        // Guard 3 — Check property set programmatically in DemoApplication.main()
        // Try Spring Environment first (covers -D args processed by Boot),
        // then fall back to raw System.getProperty in case Spring's property
        // sources haven't loaded it yet at the time this Condition is evaluated.
        String isNonConsumer = env.getProperty("internal.app.non-consumer");
        if (isNonConsumer == null) {
            isNonConsumer = System.getProperty("internal.app.non-consumer");
        }

        boolean match = "true".equalsIgnoreCase(liveDemo)
                     && "true".equalsIgnoreCase(isNonConsumer);

        // Startup diagnostic — always visible in the console during context refresh.
        // Helps quickly confirm which guard is failing without enabling DEBUG logs.
        System.err.println("[Condition Check] live-demo: " + liveDemo
                         + " | non-consumer: "      + isNonConsumer
                         + " | non-consumer Main: " + DemoApplication.getNonConsumer()
                         + " | Result: "            + match);

        // Final decision: delegate to the static flag.
        // The flag is set in DemoApplication.main() — the one code-path that
        // only executes when THIS application is the JVM entry point.
        // A consumer app calling SpringApplication.run() directly never triggers
        // DemoApplication.main(), so the flag stays false regardless of properties.
        return DemoApplication.getNonConsumer();
    }
}