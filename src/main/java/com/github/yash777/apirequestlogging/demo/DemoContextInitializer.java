package com.github.yash777.apirequestlogging.demo;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * <h2>DemoContextInitializer</h2>
 *
 * <p>A Spring {@link ApplicationContextInitializer} that activates the demo
 * environment flag <strong>before</strong> the Spring container evaluates any
 * {@code @Conditional} annotation.</p>
 *
 * <h3>Why this is the correct hook</h3>
 *
 * <p>{@code DemoEnvironmentCondition.matches()} returns
 * {@code DemoApplication.getNonConsumer()} as its final answer. That private
 * static boolean is only set to {@code true} inside
 * {@code DemoApplication.main()}, which is never called during
 * {@code mvn test}. Any hook that runs <em>after</em> class loading but
 * <em>before</em> Spring's condition evaluation solves the problem.</p>
 *
 * <p>{@link ApplicationContextInitializer#initialize} is called by
 * {@code AbstractApplicationContext.prepareContext()} — before
 * {@code invokeBeanFactoryPostProcessors}, which is where Spring resolves all
 * {@code @Conditional} annotations. This is the earliest possible point inside
 * the Spring context lifecycle after the context is created.</p>
 *
 * <h3>Lifecycle order</h3>
 * <pre>
 *   JVM loads test class
 *       ↓
 *   @SpringBootTest creates SpringApplication
 *       ↓
 *   ApplicationContext is instantiated (empty)
 *       ↓
 *   ★ DemoContextInitializer.initialize() ← we run HERE
 *       ↓
 *   Bean definitions are scanned (@ComponentScan)
 *       ↓
 *   @Conditional annotations are evaluated  ← DemoEnvironmentCondition runs here
 *       ↓
 *   Beans are created (controllers, services, filters)
 *       ↓
 *   @Autowired fields are injected into test class
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 * {@literal @}SpringBootTest(classes = DemoApplication.class)
 * {@literal @}ContextConfiguration(initializers = DemoContextInitializer.class)
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * @author Yash
 * @since 1.0.0
 */
public class DemoContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * Called by Spring immediately after the {@link ConfigurableApplicationContext}
     * is created but before any bean definitions are loaded or conditions evaluated.
     *
     * <p>Replicates the two pre-{@code SpringApplication.run()} steps that
     * {@link DemoApplication#main(String[])} performs:</p>
     * <ol>
     *   <li>Set System properties so {@code DemoEnvironmentCondition} can read
     *       them via the Spring {@link org.springframework.core.env.Environment}
     *       and as a raw {@link System#getProperty} fallback.</li>
     *   <li>Set the private static boolean {@code DemoApplication.nonConsumer}
     *       to {@code true} — the definitive gate that
     *       {@code DemoEnvironmentCondition.matches()} returns.</li>
     * </ol>
     *
     * @param applicationContext the context to initialize
     */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {

        // ── Step 1: System properties ────────────────────────────────────────
        // DemoEnvironmentCondition reads these from the Spring Environment.
        // Setting them here ensures they are visible to the Environment before
        // any @Conditional is evaluated.
        System.setProperty("internal.demo.bean.active", "true");
        System.setProperty("internal.app.non-consumer",  "true");

        // ── Step 2: Set DemoApplication.nonConsumer = true via reflection ────
        // This is the field whose value DemoEnvironmentCondition.matches()
        // returns as its final answer:
        //
        //   return DemoApplication.getNonConsumer();   // private static boolean
        //
        // Since the field is private (not package-private), reflection is the
        // only way to set it from outside DemoApplication. On Java 8–15 this
        // works without additional JVM flags. On Java 16+ it requires
        // --add-opens, but Spring Boot 2.0.x targets Java 8 so it is safe here.
        try {
            java.lang.reflect.Field field =
                    DemoApplication.class.getDeclaredField("nonConsumer");
            field.setAccessible(true);
            field.set(null, Boolean.TRUE);
        } catch (Exception ex) {
            // Wrap as a runtime error so test context loading fails loudly
            // rather than silently running with demo beans excluded.
            throw new IllegalStateException(
                "DemoContextInitializer: could not set " +
                "DemoApplication.nonConsumer = true. " +
                "Demo beans (OrderController, PaymentController, ...) " +
                "will be excluded from the test context, causing NPE on mockMvc. " +
                "Root cause: " + ex, ex);
        }
    }
}