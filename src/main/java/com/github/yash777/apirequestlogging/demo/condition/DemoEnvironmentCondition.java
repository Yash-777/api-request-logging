package com.github.yash777.apirequestlogging.demo.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
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
 *     <td>{@code DemoApplication.getNonConsumer() == true}</td>
 *     <td>Static field in {@link DemoApplication}</td>
 *     <td>{@link DemoApplication#main(String[])} — runs only when
 *         the JVM directly launches {@code DemoApplication}</td>
 *     <td>Programmatic guarantee — cannot be faked by any property</td>
 *   </tr>
 * </table>
 *
 * <h3>Why the static flag is the definitive guard</h3>
 * <p>The static field {@link DemoApplication#getNonConsumer()} can only be
 * {@code true} if {@link DemoApplication#main(String[])} was called by the
 * JVM as the application entry point.  No external property, environment
 * variable, or annotation can replicate this guarantee.</p>
 *
 * <pre>
 * Scenario 1 — consumer production app
 *   nonConsumer flag : false (main() never called)
 *   matches()        : false → demo beans SKIPPED
 *
 * Scenario 2 — consumer app with any accidental property set
 *   nonConsumer flag : false (DemoApplication.main() never called)
 *   matches()        : false → demo beans still SKIPPED
 *
 * Scenario 3 — mvn spring-boot:run on this starter project
 *   nonConsumer flag : true  (DemoApplication.main() sets it before Spring starts)
 *   matches()        : true  → demo beans CREATED
 * </pre>
 *
 * <h3>Startup debug output</h3>
 * <p>Enable {@code logging.level.org.springframework.boot.autoconfigure=DEBUG}
 * to see this condition evaluated in the Auto-Configuration report, or add a
 * {@code System.err.println} inside {@link #matches} for quick local tracing:</p>
 * <pre>
 *   [DemoEnvironmentCondition] nonConsumer=true → demo beans CREATED
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
public class DemoEnvironmentCondition implements Condition {

    /**
     * Evaluates whether the annotated bean should be created in the current
     * application context.
     *
     * <p>Delegates entirely to {@link DemoApplication#getNonConsumer()}, the static
     * boolean flag set in {@link DemoApplication#main(String[])} before
     * {@code SpringApplication.run()} is called.  This flag is the most reliable
     * single source of truth: only the JVM entry-point execution of
     * {@code DemoApplication.main()} can set it to {@code true} — no property,
     * annotation, or test fixture can replicate that guarantee.</p>
     *
     * @param context  the condition evaluation context — provides access to the
     *                 {@link org.springframework.core.env.Environment},
     *                 {@code ClassLoader}, and {@code BeanFactory}
     * @param metadata metadata of the class or method being evaluated;
     *                 not used by this implementation but required by the
     *                 {@link Condition} contract
     * @return {@code true} if {@link DemoApplication#main(String[])} was the JVM
     *         entry point and demo beans should be created;
     *         {@code false} to skip the bean entirely
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // The flag is set in DemoApplication.main() — the one code-path that
        // only executes when THIS application is the JVM entry point.
        // A consumer app calling SpringApplication.run() directly never triggers
        // DemoApplication.main(), so the flag stays false regardless of properties.
        return DemoApplication.getNonConsumer();
    }
}