package com.github.yash777.apirequestlogging.demo.diagnostic;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

/**
 * <h2>BeanProxyDiagnosticPostProcessor</h2>
 *
 * <p>A <strong>demo-only</strong> {@link BeanPostProcessor} that inspects every
 * bean in the Spring application context and logs, at startup, exactly which
 * proxy strategy was chosen for it — and why.</p>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      BUG FIX — why the previous version logged NO-PROXY incorrectly
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Bug fixed — {@code @Configuration} CGLIB enhancements reported as NO-PROXY</h3>
 *
 * <p>The previous version used only {@link AopUtils#isCglibProxy(Object)} to detect
 * CGLIB proxies.  This missed an entire category: <strong>Spring {@code @Configuration}
 * class CGLIB enhancements</strong>.  These two CGLIB mechanisms are completely
 * separate:</p>
 *
 * <table border="1" cellpadding="6">
 *   <caption>Two distinct CGLIB mechanisms in Spring</caption>
 *   <tr>
 *     <th>CGLIB mechanism</th>
 *     <th>Created by</th>
 *     <th>Implements {@code SpringProxy}?</th>
 *     <th>Detected by {@code AopUtils.isCglibProxy()}?</th>
 *     <th>Class name marker</th>
 *     <th>Purpose</th>
 *   </tr>
 *   <tr>
 *     <td><strong>AOP CGLIB proxy</strong></td>
 *     <td>{@code CglibAopProxy}</td>
 *     <td>✅ YES</td>
 *     <td>✅ YES</td>
 *     <td>{@code $$EnhancerBySpringCGLIB$$}</td>
 *     <td>Intercepts method calls for AOP advice (scoped proxy,
 *         {@code @Transactional}, etc.)</td>
 *   </tr>
 *   <tr>
 *     <td><strong>{@code @Configuration} enhancement</strong></td>
 *     <td>{@code ConfigurationClassEnhancer}</td>
 *     <td>❌ NO</td>
 *     <td>❌ NO — this was the bug</td>
 *     <td>{@code $$EnhancerBySpringCGLIB$$}</td>
 *     <td>Ensures {@code @Bean} methods return the singleton from the context
 *         even when called directly from another {@code @Bean} method.</td>
 *   </tr>
 * </table>
 *
 * <p>Because {@code @Configuration} enhanced classes do not implement
 * {@code SpringProxy}, {@link AopUtils#isCglibProxy(Object)} returns {@code false}
 * for them, causing the old code to fall through to the {@code [NO-PROXY]} branch —
 * even though the class name clearly contains {@code $$EnhancerBySpringCGLIB$$}.</p>
 *
 * <p><strong>Fix:</strong> a third detection step checks
 * {@code bean.getClass().getName().contains("$$EnhancerBySpringCGLIB$$")} after
 * the AOP checks, catching all {@code @Configuration} enhancements that slipped
 * through.</p>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      FOUR PROXY STATES — complete reference
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Four proxy states detected by this class</h3>
 *
 * <table border="1" cellpadding="6">
 *   <caption>All four Spring bean proxy states</caption>
 *   <tr>
 *     <th>Label</th>
 *     <th>Detection</th>
 *     <th>Class name example</th>
 *     <th>Trigger</th>
 *     <th>DevTools + Java 17</th>
 *   </tr>
 *   <tr>
 *     <td><strong>[CGLIB-AOP]</strong></td>
 *     <td>{@code AopUtils.isCglibProxy(bean) == true}</td>
 *     <td>{@code RequestLogCollector$$EnhancerBySpringCGLIB$$ae03bd32}</td>
 *     <td>{@code @Scope(proxyMode=TARGET_CLASS)}, {@code @Transactional},
 *         or other AOP advice on a class with no interface</td>
 *     <td>⚠ {@code LinkageError} risk on restart</td>
 *   </tr>
 *   <tr>
 *     <td><strong>[CGLIB-CFG]</strong></td>
 *     <td>class name contains {@code "$$EnhancerBySpringCGLIB$$"}
 *         AND NOT a {@code SpringProxy}</td>
 *     <td>{@code ApiLoggingFilter$$EnhancerBySpringCGLIB$$276d5491}</td>
 *     <td>Class is annotated {@code @Configuration} (or {@code @SpringBootApplication});
 *         Spring subclasses it to intercept {@code @Bean} method calls</td>
 *     <td>⚠ Same classloader risk — but MUCH less likely because
 *         {@code @Configuration} beans are usually loaded from the base classloader
 *         JAR and DevTools does not restart them</td>
 *   </tr>
 *   <tr>
 *     <td><strong>[JDK-PROXY]</strong></td>
 *     <td>{@code AopUtils.isJdkDynamicProxy(bean) == true}</td>
 *     <td>{@code com.sun.proxy.$Proxy42} / {@code jdk.proxy.$Proxy42}</td>
 *     <td>{@code @Scope(proxyMode=INTERFACES)}, bean has ≥1 interface and
 *         {@code proxyTargetClass=false}</td>
 *     <td>✅ Safe — no CGLIB subclass generated</td>
 *   </tr>
 *   <tr>
 *     <td><strong>[NO-PROXY ]</strong></td>
 *     <td>none of the above</td>
 *     <td>{@code OrderController}</td>
 *     <td>No AOP advice; no scope mismatch with a longer-lived bean</td>
 *     <td>✅ Safe — plain singleton</td>
 *   </tr>
 * </table>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      DETECTION FLOWCHART — complete
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Detection flowchart in {@code postProcessAfterInitialization}</h3>
 * <pre>
 *  Bean fully initialised (constructor + @PostConstruct + all other BPPs done)
 *      │
 *      ▼
 *  Resolve real class:  AopUtils.getTargetClass(bean)
 *    └─ For AOP proxies  → unwraps to the original target class
 *    └─ For CGLIB-CFG    → returns the $$Enhanced class itself (not unwrapped)
 *    └─ For NO-PROXY     → returns bean.getClass() as-is
 *      │
 *      ▼
 *  matchesPackageFilter(realClass)?
 *    └─ compares realClass.getName().startsWith(prefix)
 *    └─ For CGLIB-CFG: enhanced class name still starts with original package ✅
 *    NO  ──────────────────────────────────────────────► skip (return bean silently)
 *    YES
 *      │
 *      ▼
 *  Step 1: AopUtils.isCglibProxy(bean)?
 *    Checks: bean instanceof SpringProxy
 *            AND bean.getClass().getName().contains("$$")
 *    YES ──────────────────────────────────────────────► [CGLIB-AOP]
 *      │                                                  Spring AOP proxy
 *      │                                                  Implements SpringProxy + Advised
 *      │                                                  Has interceptor advisors
 *      │                                                  ⚠ LinkageError risk Java 17 + DevTools
 *    NO
 *      │
 *      ▼
 *  Step 2: AopUtils.isJdkDynamicProxy(bean)?
 *    Checks: bean instanceof SpringProxy
 *            AND Proxy.isProxyClass(bean.getClass())
 *    YES ──────────────────────────────────────────────► [JDK-PROXY]
 *      │                                                  java.lang.reflect.Proxy
 *      │                                                  Implements all target interfaces
 *      │                                                  ✅ Safe with DevTools + Java 17
 *    NO
 *      │
 *      ▼
 *  Step 3: bean.getClass().getName().contains("$$EnhancerBySpringCGLIB$$")?
 *    Checks raw class name for the CGLIB marker string
 *    This catches @Configuration enhancements missed by Steps 1 and 2
 *    because ConfigurationClassEnhancer does NOT add SpringProxy to the class
 *    YES ──────────────────────────────────────────────► [CGLIB-CFG]
 *      │                                                  @Configuration class enhancement
 *      │                                                  Does NOT implement SpringProxy
 *      │                                                  AopUtils.isCglibProxy() = FALSE (BUG FIX)
 *      │                                                  Superclass = original @Configuration class
 *      │                                                  Purpose: intercept @Bean method calls
 *      │                                                           to enforce singleton semantics
 *    NO
 *      │
 *      ▼
 *  [NO-PROXY ]
 *    Plain singleton — no AOP advice, no scope mismatch, no @Configuration
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      WHY EnvironmentAware — not @Value
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Why {@link EnvironmentAware} instead of {@code @Value}</h3>
 *
 * <p>{@link BeanPostProcessor} beans are instantiated in a dedicated early phase
 * before regular singleton beans.  At that point
 * {@code PropertySourcesPlaceholderConfigurer} — which resolves {@code ${...}}
 * placeholders — may not yet have run, so {@code @Value} fields receive the
 * literal placeholder string instead of the actual value, and Spring logs:</p>
 * <pre>
 *   "BeanPostProcessor … is not eligible for getting processed by all BeanPostProcessors"
 * </pre>
 * <p>{@link EnvironmentAware} is injected before any BPP callback fires and reads
 * property sources directly without depending on the placeholder configurer.
 * {@link PostConstruct} then resolves prefixes once the environment is fully
 * populated — safely after {@code application.properties} is loaded.</p>
 *
 * <pre>
 * Safe injection sequence for a BeanPostProcessor:
 *
 *   ① Spring instantiates this BPP bean
 *   ② setEnvironment(env)                  ← EnvironmentAware — safe
 *   ③ @PostConstruct init()                ← resolves prefixes + prints banner
 *   ④ postProcessBeforeInitialization()    ← other beans start initialising
 *   ⑤ postProcessAfterInitialization()     ← detect + log proxy type per bean
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      PACKAGE FILTER
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Package filter property</h3>
 *
 * <pre>
 * # ══════════════════════════════════════════════════════════════════════
 * # Property : api.request.logging.demo.diagnostic.packages
 * # Default  : * (log ALL beans — framework + application)
 * #
 * # Patterns (comma-separated):
 * #   *                                           → all beans (wildcard default)
 * #   com.github.yash777.*                        → all sub-packages
 * #   com.github.yash777.apirequestlogging.demo.* → specific sub-package
 * #   com.github                                  → partial prefix match
 * #
 * # Normalisation:
 * #   "com.github.yash777.*"  →  prefix "com.github.yash777."  (strips "*", keeps ".")
 * #   "com.github.yash777"    →  prefix "com.github.yash777"   (kept as-is)
 * # ══════════════════════════════════════════════════════════════════════
 * api.request.logging.demo.diagnostic.packages=com.github.yash777.apirequestlogging.*
 *
 * # Enable per-bean DEBUG lines:
 * logging.level.com.github.yash777.apirequestlogging.demo.diagnostic=DEBUG
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════════
 *      SAMPLE OUTPUT
 *      ═══════════════════════════════════════════════════════════════════ -->
 *
 * <h3>Sample startup output (packages=com.github.yash777.apirequestlogging.*)</h3>
 * <pre>
 * ══ BEAN PROXY DIAGNOSTIC ════════════════════════════════════════════
 *
 * [CGLIB-AOP] requestLogCollector
 *               realClass  : …collector.RequestLogCollector
 *               proxyClass : …RequestLogCollector$$EnhancerBySpringCGLIB$$ae03bd32
 *               superClass : …RequestLogCollector
 *               interfaces : [none]
 *               mechanism  : Spring AOP (CglibAopProxy) — implements SpringProxy + Advised
 *               advisors   : 1 applied (ScopedProxyFactoryBean interceptor)
 *               when       : postProcessAfterInitialization() — after @PostConstruct + all BPPs
 *               ⚠ No interface — switch to ScopedProxyMode.INTERFACES to fix
 *                 LinkageError on Java 17 + DevTools restart
 *
 * [CGLIB-CFG] apiLoggingFilter
 *               realClass  : …filter.ApiLoggingFilter$$EnhancerBySpringCGLIB$$276d5491
 *               superClass : …filter.ApiLoggingFilter  ← original @Configuration class
 *               mechanism  : Spring @Configuration class enhancement (ConfigurationClassEnhancer)
 *                            NOT an AOP proxy — AopUtils.isCglibProxy() = false
 *                            Purpose: intercepts @Bean method calls to enforce singleton semantics
 *               when       : postProcessAfterInitialization()
 *
 * [JDK-PROXY] someTransactionalService
 *               realClass  : …SomeServiceImpl
 *               proxyClass : com.sun.proxy.$Proxy42
 *               interfaces : [SomeService, Serializable]
 *               mechanism  : java.lang.reflect.Proxy — implements SpringProxy + Advised
 *               when       : postProcessAfterInitialization()
 *               ✅ Safe with DevTools + Java 17
 *
 * [NO-PROXY ] orderController
 *               class      : …controller.OrderController
 *               when       : plain singleton — no AOP advice, no scope mismatch
 * </pre>
 *
 * @author Yash
 * @since 1.2.0
 * @see org.springframework.aop.support.AopUtils
 * @see org.springframework.aop.framework.Advised
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.EnvironmentAware
 * @see java.lang.reflect.Proxy
 */
@Component
@ConditionalOnDemoEnvironment
public class BeanProxyDiagnosticPostProcessor
        implements BeanPostProcessor, PriorityOrdered, EnvironmentAware {

    private static final Logger log =
            LoggerFactory.getLogger(BeanProxyDiagnosticPostProcessor.class);

    // ════════════════════════════════════════════════════════════════════════
    //  CGLIB CLASS-NAME MARKERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Class-name substring present in both Spring AOP CGLIB proxies
     * ({@code CglibAopProxy}) AND Spring {@code @Configuration} class
     * enhancements ({@code ConfigurationClassEnhancer}).
     *
     * <p>This is used in Step 3 of the detection flowchart to catch
     * {@code @Configuration} enhancements that are NOT detected by
     * {@link AopUtils#isCglibProxy(Object)} because they do not implement
     * {@code SpringProxy}.</p>
     *
     * <p>Value: {@value}</p>
     */
    public static final String CGLIB_CLASS_MARKER = "$$EnhancerBySpringCGLIB$$";

    // ════════════════════════════════════════════════════════════════════════
    //  PROPERTY CONSTANTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The {@code application.properties} key for the package filter.
     *
     * <p>Value: {@value}</p>
     */
    public static final String PROPERTY_KEY =
            "api.request.logging.demo.diagnostic.packages";

    /**
     * Default value — logs ALL beans when the property is absent.
     *
     * <p>Value: {@value}</p>
     */
    public static final String DEFAULT_PACKAGES = "*";

    private static final String WILDCARD = "*";
    private static final String SEP      =
            "══════════════════════════════════════════════════════════════";
    private static final String THIN_SEP =
            "──────────────────────────────────────────────────────────────";

    // ════════════════════════════════════════════════════════════════════════
    //  STATE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Spring {@link Environment} — injected by {@link #setEnvironment(Environment)}
     * before any {@link BeanPostProcessor} callback fires.  Used to read
     * {@link #PROPERTY_KEY} safely from all property sources.
     */
    private volatile Environment environment;

    /**
     * Normalised package prefixes resolved from {@link #PROPERTY_KEY} in
     * {@link #init()}.
     *
     * <ul>
     *   <li>Empty list → wildcard (match all)</li>
     *   <li>Non-empty → each entry is a prefix string for
     *       {@link String#startsWith(String)} matching on FQCNs</li>
     * </ul>
     */
    /** {@code null} until {@link #setEnvironment(Environment)} resolves it. */
    private volatile List<String> packagePrefixes = null;

    // ════════════════════════════════════════════════════════════════════════
    //  EnvironmentAware
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Injected by Spring before any {@link BeanPostProcessor} callback.
     *
     * <p>This is the only safe way to read properties in a
     * {@link BeanPostProcessor}.  Using {@code @Value} is unsafe because
     * {@code PropertySourcesPlaceholderConfigurer} may not have run yet when
     * the BPP is instantiated (Phase 3 of Spring's startup), causing the
     * literal {@code "${api...}"} string to be injected instead of the value.</p>
     *
     * @param environment the fully populated Spring environment
     */
    /**
     * Injected by Spring before any {@link BeanPostProcessor} callback — and
     * crucially, before {@code @PostConstruct} would fire.
     *
     * <h4>Why @PostConstruct was removed and initialization moved here</h4>
     * <p>{@code @PostConstruct} is processed by {@code CommonAnnotationBeanPostProcessor},
     * which is itself a {@code PriorityOrdered} BPP with order {@code LOWEST_PRECEDENCE}.
     * This class also uses {@code LOWEST_PRECEDENCE}.  When both land in the same
     * sort bucket, Spring may instantiate <em>this</em> BPP first — before
     * {@code CommonAnnotationBeanPostProcessor} is registered — so
     * {@code @PostConstruct} is silently skipped.  The result is that
     * {@code packagePrefixes} stays {@code null} (its default), which causes
     * {@code matchesPackageFilter} to treat it as a wildcard and log all beans,
     * ignoring the configured property completely.</p>
     *
     * <p>{@code setEnvironment()} is called by Spring's internal
     * {@code ApplicationContextAwareProcessor} unconditionally before any other
     * BPP is given a chance to process this bean, so it is guaranteed to run
     * regardless of BPP registration order.</p>
     *
     * <h4>Corrected safe startup sequence</h4>
     * <pre>
     *   ① Spring instantiates this BPP (constructor)
     *   ② setEnvironment(env)  ← resolves prefixes + prints banner  ★ HERE
     *   ③ postProcessAfterInitialization() called for each bean
     * </pre>
     *
     * @param environment the fully populated Spring {@link Environment};
     *                    all property sources including {@code application.properties}
     *                    are available at this point
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.packagePrefixes = resolvePackagePrefixes(); // was @PostConstruct — moved here
        printBanner();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PriorityOrdered
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@link Ordered#LOWEST_PRECEDENCE} so this post-processor runs
     * <em>after</em> all other {@link BeanPostProcessor} implementations,
     * including Spring's own AOP and scoped-proxy wrappers.
     *
     * <p>This guarantees that the proxy type observed here is the final state
     * that will be stored in the singleton registry — not an intermediate.</p>
     *
     * @return {@link Ordered#LOWEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BeanPostProcessor
    // ════════════════════════════════════════════════════════════════════════

    /**
     * No-op — the proxy has not yet been applied at this point.
     *
     * @param bean     bean before initialisation (not yet proxied)
     * @param beanName Spring bean name
     * @return the bean unchanged
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        return bean;
    }

    /**
     * Detects and logs the proxy type of each fully initialised bean.
     *
     * <h4>Execution timeline for each bean</h4>
     * <pre>
     *   new BeanClass()                  ← constructor injection
     *       ↓
     *   @Autowired fields set
     *       ↓
     *   @PostConstruct method
     *       ↓
     *   AOP / scoped-proxy wrapping      ← other BeanPostProcessors run
     *       ↓
     *   ★ THIS METHOD — bean is in its final, fully-proxied state
     *       ↓
     *   bean stored in singleton registry
     *       ↓
     *   injected into dependent beans
     * </pre>
     *
     * <h4>Four-step proxy detection</h4>
     * <pre>
     *   Step 1: AopUtils.isCglibProxy(bean)
     *           → checks: bean instanceof SpringProxy
     *                     AND class name contains "$$"
     *           → YES: [CGLIB-AOP]  Spring AOP proxy
     *
     *   Step 2: AopUtils.isJdkDynamicProxy(bean)
     *           → checks: bean instanceof SpringProxy
     *                     AND Proxy.isProxyClass(bean.getClass())
     *           → YES: [JDK-PROXY]  java.lang.reflect.Proxy
     *
     *   Step 3: bean.getClass().getName().contains("$$EnhancerBySpringCGLIB$$")
     *           → catches @Configuration enhancements missed by Steps 1 + 2
     *             because ConfigurationClassEnhancer does NOT add SpringProxy
     *           → YES: [CGLIB-CFG]  @Configuration class enhancement
     *
     *   Step 4: (none of the above)
     *           → [NO-PROXY]  plain singleton
     * </pre>
     *
     * <h4>Package filter — always matched against the REAL class</h4>
     * <p>For AOP proxies, {@link AopUtils#getTargetClass(Object)} unwraps the proxy
     * to the original class.  For {@code @Configuration} enhancements,
     * {@code getTargetClass} returns the enhanced class itself (it is not an
     * {@code Advised} object), but its FQCN still starts with the original
     * package — so prefix matching works correctly in both cases.</p>
     *
     * @param bean     the fully initialised bean (may be a proxy)
     * @param beanName the Spring bean name
     * @return the bean unchanged — observational only
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {

        // Unwrap AOP proxy → real class.
        // For @Configuration CGLIB enhancements, this returns the enhanced class
        // itself (e.g. ApiLoggingFilter$$EnhancerBySpringCGLIB$$…) because they
        // are not Advised objects — but their FQCN still starts with the original
        // package, so prefix matching is correct.
        Class<?> realClass = AopUtils.getTargetClass(bean);

        if (!matchesPackageFilter(realClass)) {
            return bean; // not in configured packages — skip silently
        }

        // ── Four-step detection ───────────────────────────────────────────

        if (AopUtils.isCglibProxy(bean)) {
            // Step 1: Spring AOP CGLIB proxy — implements SpringProxy + Advised
            logCglibAopProxy(bean, beanName, realClass);

        } else if (AopUtils.isJdkDynamicProxy(bean)) {
            // Step 2: JDK dynamic proxy — implements SpringProxy, Proxy.isProxyClass
            logJdkProxy(bean, beanName, realClass);

        } else if (bean.getClass().getName().contains(CGLIB_CLASS_MARKER)) {
            // Step 3: @Configuration class CGLIB enhancement.
            // NOT a SpringProxy → AopUtils.isCglibProxy() returned false above.
            // Detected by raw class-name substring check.
            // Superclass = the original @Configuration class.
            logCglibCfgProxy(bean, beanName);

        } else {
            // Step 4: plain singleton — no proxy of any kind
            logNoProxy(beanName, bean.getClass());
        }

        return bean;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PER-TYPE LOG METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Logs a {@code [CGLIB-AOP]} entry — a Spring AOP proxy created by
     * {@code CglibAopProxy}.
     *
     * <h4>What makes this an AOP proxy</h4>
     * <ul>
     *   <li>Implements {@code SpringProxy} — detectable by
     *       {@link AopUtils#isCglibProxy(Object)}</li>
     *   <li>Implements {@link Advised} — holds a list of
     *       {@link org.springframework.aop.Advisor} instances</li>
     *   <li>Generated subclass name: {@code OriginalClass$$EnhancerBySpringCGLIB$$hex}</li>
     * </ul>
     *
     * <h4>Common triggers</h4>
     * <ul>
     *   <li>{@code @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)} — request/session
     *       scoped beans injected into singletons</li>
     *   <li>{@code @Transactional} on a class with no interface and
     *       {@code proxyTargetClass=true}</li>
     *   <li>Any AOP {@code @Aspect} advice matching the bean</li>
     * </ul>
     *
     * <h4>DevTools + Java 17 risk</h4>
     * <p>The CGLIB subclass is defined in the base {@code app} classloader (permanent).
     * On DevTools restart the child {@code RestartClassLoader} is discarded and
     * recreated, but Spring tries to redefine the same CGLIB class in the same
     * base classloader → {@code LinkageError: duplicate class definition}.</p>
     *
     * <p>Fix: add a {@code RequestLogCollectorApi} interface and switch to
     * {@code ScopedProxyMode.INTERFACES} to use a JDK proxy instead.</p>
     *
     * @param bean      the CGLIB AOP proxy
     * @param beanName  the Spring bean name
     * @param realClass the un-proxied target class (from {@link AopUtils#getTargetClass})
     */
    private void logCglibAopProxy(Object bean, String beanName, Class<?> realClass) {
        Class<?>[] ifaces   = realClass.getInterfaces();
        Class<?> superClass = bean.getClass().getSuperclass();

        StringBuilder sb = new StringBuilder();
        sb.append("\n[CGLIB-AOP] ").append(beanName);
        sb.append("\n  realClass  : ").append(realClass.getName());
        sb.append("\n  proxyClass : ").append(bean.getClass().getName());
        sb.append("\n  superClass : ").append(superClass != null ? superClass.getName() : "N/A");
        sb.append("\n  interfaces : ").append(formatInterfaces(ifaces));
        sb.append("\n  mechanism  : Spring AOP — CglibAopProxy");
        sb.append("\n               Implements SpringProxy + Advised");
        sb.append("\n               AopUtils.isCglibProxy() = TRUE  ← detected here");
        sb.append("\n  when       : postProcessAfterInitialization()");
        sb.append("\n               after constructor + @PostConstruct + all BPPs");

        if (bean instanceof Advised) {
            int advisorCount = ((Advised) bean).getAdvisors().length;
            sb.append("\n  advisors   : ").append(advisorCount).append(" applied");
        }

        if (ifaces.length == 0) {
            sb.append("\n  ⚠  No interface found on target class.");
            sb.append("\n     Cannot switch to JDK proxy without adding an interface.");
            sb.append("\n     To fix LinkageError on Java 17 + DevTools:");
            sb.append("\n       1. Create interface  RequestLogCollectorApi");
            sb.append("\n       2. Implement it in   RequestLogCollector");
            sb.append("\n       3. Change annotation @Scope(proxyMode=ScopedProxyMode.INTERFACES)");
            sb.append("\n       4. Inject the interface type instead of the concrete class");
        } else {
            sb.append("\n  ⚠  Has interface(s) — eligible for ScopedProxyMode.INTERFACES.");
            sb.append("\n     Switch from TARGET_CLASS to INTERFACES to eliminate");
            sb.append("\n     LinkageError on Java 17 + DevTools restart.");
        }

        log.debug(sb.toString());
    }

    /**
     * Logs a {@code [CGLIB-CFG]} entry — a Spring {@code @Configuration} class
     * CGLIB enhancement created by {@code ConfigurationClassEnhancer}.
     *
     * <h4>Why this is NOT detected by {@link AopUtils#isCglibProxy(Object)}</h4>
     * <p>{@code AopUtils.isCglibProxy()} checks {@code bean instanceof SpringProxy}.
     * {@code ConfigurationClassEnhancer} does NOT add {@code SpringProxy} to the
     * generated subclass — it is not an AOP proxy.  The enhanced class is only
     * identifiable by its name containing {@code "$$EnhancerBySpringCGLIB$$"}.</p>
     *
     * <h4>What this enhancement does</h4>
     * <p>When a {@code @Configuration} class has two {@code @Bean} methods where
     * one calls the other:</p>
     * <pre>
     *   {@literal @}Configuration
     *   public class MyConfig {
     *       {@literal @}Bean
     *       public ServiceA serviceA() { return new ServiceA(serviceB()); }
     *
     *       {@literal @}Bean
     *       public ServiceB serviceB() { return new ServiceB(); }
     *   }
     * </pre>
     * <p>Without CGLIB enhancement, {@code serviceA()} would create a new
     * {@code ServiceB()} directly — breaking the singleton contract.
     * The CGLIB subclass intercepts the {@code serviceB()} call inside
     * {@code serviceA()} and returns the singleton instance from the
     * {@code ApplicationContext} instead.</p>
     *
     * <h4>Common examples in this project</h4>
     * <ul>
     *   <li>{@code ApiLoggingFilter$$EnhancerBySpringCGLIB$$} — annotated
     *       {@code @Configuration} (contains {@code @Bean} methods)</li>
     *   <li>{@code DemoApplication$$EnhancerBySpringCGLIB$$} — annotated
     *       {@code @SpringBootApplication} which meta-annotates {@code @Configuration}</li>
     *   <li>{@code ApiRequestLoggingAutoConfiguration$$EnhancerBySpringCGLIB$$} — same</li>
     * </ul>
     *
     * @param bean     the {@code @Configuration} CGLIB enhanced bean
     * @param beanName the Spring bean name
     */
    private void logCglibCfgProxy(Object bean, String beanName) {
        Class<?> enhancedClass  = bean.getClass();
        Class<?> originalClass  = enhancedClass.getSuperclass(); // unwrap one level

        StringBuilder sb = new StringBuilder();
        sb.append("\n[CGLIB-CFG] ").append(beanName);
        sb.append("\n  enhancedClass : ").append(enhancedClass.getName());
        sb.append("\n  originalClass : ").append(
                originalClass != null ? originalClass.getName() : "N/A");
        sb.append("\n  interfaces    : ").append(formatInterfaces(
                originalClass != null ? originalClass.getInterfaces() : new Class<?>[0]));
        sb.append("\n  mechanism     : Spring @Configuration class enhancement");
        sb.append("\n                  Created by ConfigurationClassEnhancer, NOT CglibAopProxy");
        sb.append("\n                  Does NOT implement SpringProxy");
        sb.append("\n                  AopUtils.isCglibProxy()    = FALSE  (missed by old code)");
        sb.append("\n                  AopUtils.isJdkDynamicProxy() = FALSE");
        sb.append("\n                  class.getName().contains(\"$$EnhancerBySpringCGLIB$$\") = TRUE ← detected here");
        sb.append("\n  purpose       : intercepts @Bean method calls between methods in this");
        sb.append("\n                  @Configuration class to enforce singleton semantics.");
        sb.append("\n                  Without this, bean() { return new X(otherBean()); }");
        sb.append("\n                  would create a NEW instance of otherBean() each call.");
        sb.append("\n  when          : postProcessAfterInitialization()");
        sb.append("\n                  after constructor + @PostConstruct + all BPPs");
        sb.append("\n  ℹ  This is NORMAL — all @Configuration classes get this enhancement.");
        sb.append("\n     No action required.");

        log.debug(sb.toString());
    }

    /**
     * Logs a {@code [JDK-PROXY]} entry — a {@link java.lang.reflect.Proxy}
     * created by Spring AOP using {@link Proxy#newProxyInstance}.
     *
     * <h4>What makes this a JDK proxy</h4>
     * <ul>
     *   <li>Implements {@code SpringProxy} — detectable by
     *       {@link AopUtils#isJdkDynamicProxy(Object)}</li>
     *   <li>Implements {@link Advised} — holds advisor list</li>
     *   <li>{@link Proxy#isProxyClass(Class)} returns {@code true}</li>
     *   <li>Class name: {@code com.sun.proxy.$Proxy42} (Java 8–16) or
     *       {@code jdk.proxy1.$Proxy42} (Java 17+)</li>
     * </ul>
     *
     * <h4>Triggers</h4>
     * <ul>
     *   <li>{@code @Scope(proxyMode = ScopedProxyMode.INTERFACES)}</li>
     *   <li>Bean implements ≥1 interface AND
     *       {@code spring.aop.proxy-target-class=false} (not Spring Boot 2.x default)</li>
     * </ul>
     *
     * <h4>DevTools safety</h4>
     * <p>JDK proxies are safe with DevTools on Java 17+ because no CGLIB subclass
     * is generated — the JDK creates the proxy class fresh on each context start,
     * avoiding the duplicate-class-definition {@code LinkageError}.</p>
     *
     * @param bean      the JDK dynamic proxy
     * @param beanName  the Spring bean name
     * @param realClass the un-proxied target class
     */
    private void logJdkProxy(Object bean, String beanName, Class<?> realClass) {
        Class<?>[] ifaces = bean.getClass().getInterfaces();

        StringBuilder sb = new StringBuilder();
        sb.append("\n[JDK-PROXY] ").append(beanName);
        sb.append("\n  realClass  : ").append(realClass.getName());
        sb.append("\n  proxyClass : ").append(bean.getClass().getName());
        sb.append("\n  interfaces : ").append(formatInterfaces(ifaces));
        sb.append("\n  mechanism  : java.lang.reflect.Proxy (JDK dynamic proxy)");
        sb.append("\n               Implements SpringProxy + Advised");
        sb.append("\n               Proxy.isProxyClass() = TRUE");
        sb.append("\n               AopUtils.isJdkDynamicProxy() = TRUE  ← detected here");
        sb.append("\n  when       : postProcessAfterInitialization()");
        sb.append("\n               after constructor + @PostConstruct + all BPPs");

        if (bean instanceof Advised) {
            sb.append("\n  advisors   : ")
              .append(((Advised) bean).getAdvisors().length).append(" applied");
        }

        sb.append("\n  ✅ Safe with DevTools + Java 17 (no CGLIB subclass generated)");

        log.debug(sb.toString());
    }

    /**
     * Logs a {@code [NO-PROXY ]} entry — a plain, unproxied singleton bean.
     *
     * <p>No AOP advice applies to this bean and it has no scope mismatch
     * (it does not need to be injected into a longer-lived bean as a proxy).
     * Spring creates and registers it directly — zero proxy overhead.</p>
     *
     * <h4>All three detection steps returned false</h4>
     * <pre>
     *   AopUtils.isCglibProxy(bean)               = false  (not a SpringProxy + $$)
     *   AopUtils.isJdkDynamicProxy(bean)           = false  (not a SpringProxy + Proxy)
     *   className.contains("$$EnhancerBySpringCGLIB$$") = false  (no CGLIB marker)
     *   → NO-PROXY ✅
     * </pre>
     *
     * @param beanName the Spring bean name
     * @param beanClass the bean's actual runtime class
     */
    private void logNoProxy(String beanName, Class<?> beanClass) {
        log.debug("\n[NO-PROXY ] {}\n  class : {}\n  when  : plain singleton — no AOP advice, no scope mismatch, no @Configuration CGLIB",
                beanName, beanClass.getName());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PACKAGE FILTER
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if the bean's class FQCN starts with any of the
     * configured package prefixes, or if the wildcard is configured.
     *
     * <h4>Correctness for all proxy types</h4>
     * <table border="1" cellpadding="4">
     *   <caption>Package filter correctness per proxy type</caption>
     *   <tr><th>Proxy type</th><th>realClass FQCN</th><th>Filter result</th></tr>
     *   <tr>
     *     <td>CGLIB-AOP</td>
     *     <td>{@code com.github.yash777.…RequestLogCollector} (unwrapped)</td>
     *     <td>✅ matches "com.github.yash777."</td>
     *   </tr>
     *   <tr>
     *     <td>CGLIB-CFG</td>
     *     <td>{@code com.github.yash777.…ApiLoggingFilter$$EnhancerBySpringCGLIB$$…}
     *         (getTargetClass returns enhanced class itself)</td>
     *     <td>✅ matches "com.github.yash777." — FQCN still starts with original package</td>
     *   </tr>
     *   <tr>
     *     <td>JDK-PROXY</td>
     *     <td>{@code com.github.yash777.…SomeServiceImpl} (unwrapped)</td>
     *     <td>✅ matches "com.github.yash777."</td>
     *   </tr>
     *   <tr>
     *     <td>NO-PROXY</td>
     *     <td>{@code com.github.yash777.…OrderController}</td>
     *     <td>✅ matches "com.github.yash777."</td>
     *   </tr>
     * </table>
     *
     * @param realClass bean's real class (unwrapped by {@link AopUtils#getTargetClass})
     * @return {@code true} to include this bean in the log
     */
    private boolean matchesPackageFilter(Class<?> realClass) {
        List<String> prefixes = packagePrefixes;
        if (prefixes == null || prefixes.isEmpty()) { // null = not yet resolved → wildcard fallback
            return true;
        }
        String fqcn = realClass.getName();
        for (String prefix : packagePrefixes) {
            if (fqcn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads {@link #PROPERTY_KEY} from the {@link Environment} and builds
     * the normalised prefix list.
     *
     * <h4>Normalisation rules per comma-separated token</h4>
     * <ol>
     *   <li>Trim whitespace.</li>
     *   <li>Lone {@code "*"} → return empty list (wildcard — match all).</li>
     *   <li>Ends with {@code ".*"} → strip trailing {@code "*"}, keep {@code "."} →
     *       {@code "com.github.yash777.*"} becomes {@code "com.github.yash777."}</li>
     *   <li>No trailing {@code ".*"} → keep as-is (partial prefix) →
     *       {@code "com.github"} stays {@code "com.github"}</li>
     * </ol>
     *
     * <h4>Property source priority (highest → lowest)</h4>
     * <ol>
     *   <li>JVM system property ({@code -Dapi.request.logging.demo.diagnostic.packages=…})</li>
     *   <li>OS environment variable ({@code API_REQUEST_LOGGING_DEMO_DIAGNOSTIC_PACKAGES})</li>
     *   <li>{@code application.properties} / {@code application.yml}</li>
     *   <li>Default: {@code *}</li>
     * </ol>
     *
     * @return immutable normalised prefix list; empty = wildcard
     */
    private List<String> resolvePackagePrefixes() {
        String raw = (environment != null)
                ? environment.getProperty(PROPERTY_KEY, DEFAULT_PACKAGES)
                : DEFAULT_PACKAGES;
        if (raw == null || raw.trim().isEmpty()) raw = DEFAULT_PACKAGES;
        raw = raw.trim();

        if (WILDCARD.equals(raw)) {
            return Collections.emptyList();
        }

        String[] tokens = raw.split(",");
        List<String> prefixes = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (WILDCARD.equals(t)) return Collections.emptyList();
            if (t.endsWith(".*")) {
                prefixes.add(t.substring(0, t.length() - 1)); // strip *, keep .
            } else {
                prefixes.add(t);
            }
        }
        return Collections.unmodifiableList(prefixes);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BANNER
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Prints the startup banner at {@code INFO} level (always visible).
     *
     * <p>Called from {@link #init()} ({@code @PostConstruct}) once all
     * configuration has been resolved.  Per-bean detail lines are at
     * {@code DEBUG} level and require:</p>
     * <pre>
     * logging.level.com.github.yash777.apirequestlogging.demo.diagnostic=DEBUG
     * </pre>
     */
    private void printBanner() {
        log.info("\n{}", SEP);
        log.info(" BEAN PROXY DIAGNOSTIC REPORT — BeanProxyDiagnosticPostProcessor");
        log.info("{}", THIN_SEP);
        log.info(" Property : {}", PROPERTY_KEY);
        log.info(" Default  : {}  (wildcard — log all beans)", DEFAULT_PACKAGES);
        log.info("");

        if (packagePrefixes.isEmpty()) {
            log.info(" Package filter : * (wildcard — all beans, no filtering)");
        } else {
            log.info(" Package filter : {} active prefix(es):", packagePrefixes.size());
            for (String p : packagePrefixes) {
                log.info("                   → {}", p);
            }
        }

        log.info("");
        log.info(" Detection order (per bean in postProcessAfterInitialization):");
        log.info("   Step 1: AopUtils.isCglibProxy()       → [CGLIB-AOP]  Spring AOP CGLIB proxy");
        log.info("   Step 2: AopUtils.isJdkDynamicProxy()  → [JDK-PROXY]  java.lang.reflect.Proxy");
        log.info("   Step 3: className.contains(\"$$EnhancerBySpringCGLIB$$\")");
        log.info("                                         → [CGLIB-CFG]  @Configuration enhancement");
        log.info("   Step 4: (none matched)                → [NO-PROXY ]  plain singleton");
        log.info("");
        log.info(" KEY: Step 3 was MISSING in the previous version — causing");
        log.info("      @Configuration beans to be reported as [NO-PROXY] incorrectly.");
        log.info("      @Configuration CGLIB enhancements do NOT implement SpringProxy,");
        log.info("      so AopUtils.isCglibProxy() returns false for them.");
        log.info("");
        log.info(" Enable per-bean DEBUG output:");
        log.info("   logging.level.{} = DEBUG",
                BeanProxyDiagnosticPostProcessor.class.getPackage().getName());
        log.info("");
        log.info(" Proxy legend:");
        log.info("   [CGLIB-AOP] Spring AOP CGLIB proxy — $$EnhancerBySpringCGLIB$$");
        log.info("               trigger : @Scope(TARGET_CLASS), @Transactional w/o interface,");
        log.info("                         or spring.aop.proxy-target-class=true");
        log.info("               detects : AopUtils.isCglibProxy() = TRUE");
        log.info("               ⚠ Risk  : LinkageError on Java 17 + DevTools restart");
        log.info("   [CGLIB-CFG] @Configuration class enhancement — $$EnhancerBySpringCGLIB$$");
        log.info("               trigger : class annotated @Configuration or @SpringBootApplication");
        log.info("               detects : className.contains(\"$$EnhancerBySpringCGLIB$$\") ONLY");
        log.info("                         AopUtils.isCglibProxy() = FALSE (not a SpringProxy)");
        log.info("               ℹ  Normal — no action needed");
        log.info("   [JDK-PROXY] java.lang.reflect.Proxy — interface-based");
        log.info("               trigger : @Scope(INTERFACES), bean has interface");
        log.info("               detects : AopUtils.isJdkDynamicProxy() = TRUE");
        log.info("               ✅ Safe with DevTools + Java 17");
        log.info("   [NO-PROXY ] plain singleton — no proxy of any kind");
        log.info("               All 3 detection steps returned false");
        log.info("{}\n", SEP);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PUBLIC STATIC UTILITIES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns {@code true} if {@code clazz} is any kind of CGLIB-generated class —
     * either a Spring AOP CGLIB proxy OR a {@code @Configuration} class enhancement.
     *
     * <p>The check is based on the presence of {@code "$$"} in the class name,
     * which is the naming convention for all Spring CGLIB-generated classes.</p>
     *
     * <p>To distinguish between the two kinds, use
     * {@link AopUtils#isCglibProxy(Object)} — if it returns {@code true}, the
     * class is an AOP proxy; if it returns {@code false} but this method returns
     * {@code true}, the class is a {@code @Configuration} enhancement.</p>
     *
     * @param clazz the class to inspect; may be {@code null}
     * @return {@code true} if the class name contains {@code "$$"}
     */
    public static boolean isCglibGeneratedClass(Class<?> clazz) {
        return clazz != null && clazz.getName().contains("$$");
    }

    /**
     * Returns {@code true} if {@code obj} is a JDK dynamic proxy created by
     * {@link Proxy#newProxyInstance}.
     *
     * <p>Delegates to {@link Proxy#isProxyClass(Class)}.  Can be used as a
     * static utility without Spring context.</p>
     *
     * @param obj the object to inspect; may be {@code null}
     * @return {@code true} if {@code obj} is a JDK proxy instance
     */
    public static boolean isJdkDynamicProxy(Object obj) {
        return obj != null && Proxy.isProxyClass(obj.getClass());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Formats a {@link Class} array as {@code [SimpleName1, SimpleName2]}
     * or {@code [none]} when empty.
     *
     * @param interfaces the array to format; may be {@code null}
     * @return formatted string
     */
    private static String formatInterfaces(Class<?>[] interfaces) {
        if (interfaces == null || interfaces.length == 0) return "[none]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < interfaces.length; i++) {
            sb.append(interfaces[i].getSimpleName());
            if (i < interfaces.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}