package com.github.yash777.apirequestlogging.demo.diagnostic;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * <h2>StartupDiagnosticUtil</h2>
 *
 * <p>A <strong>zero-dependency, pure-static</strong> utility that prints a
 * structured diagnostic banner to {@code System.out} <em>before</em>
 * {@code SpringApplication.run()} is called.  Because it runs before the
 * Spring context exists, it must not use any Spring API — it reads directly
 * from the JDK's {@link System}, {@link Runtime}, and
 * {@link java.lang.management} APIs.</p>
 *
 * <h3>Why call it before Spring starts?</h3>
 * <pre>
 *   DemoApplication.main()
 *       │
 *       ├─ 1. nonConsumer = true               ← demo guard
 *       ├─ 2. StartupDiagnosticUtil.print()    ← ★ WE RUN HERE
 *       │       no Spring context exists yet
 *       │       — pure JDK, no classpath risk
 *       │
 *       └─ 3. SpringApplication.run()          ← Spring Boot starts
 *               Bean definitions scanned
 *               Conditions evaluated
 *               Embedded Tomcat started
 * </pre>
 *
 * <p>Running at step 2 means the diagnostic is printed even if Spring fails to
 * start — a broken auto-configuration or missing property will not suppress the
 * environment snapshot, making it far easier to diagnose the failure context.</p>
 *
 * <h3>Information collected</h3>
 *
 * <table border="1" cellpadding="6">
 *   <caption>Diagnostic categories and their System-property / JDK API sources</caption>
 *   <tr><th>Category</th><th>Fields</th><th>Source</th></tr>
 *   <tr>
 *     <td><strong>Java / JVM</strong></td>
 *     <td>version, spec version, vendor, JVM name, JVM version, class path entries,
 *         JVM arguments, PID, uptime</td>
 *     <td>{@code System.getProperty("java.*")},
 *         {@link RuntimeMXBean}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Operating System</strong></td>
 *     <td>name, version, architecture, detected family (Windows/Linux/macOS/Unknown)</td>
 *     <td>{@code System.getProperty("os.*")}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Hardware / CPU</strong></td>
 *     <td>available processors (logical CPU count)</td>
 *     <td>{@link Runtime#availableProcessors()}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Memory</strong></td>
 *     <td>JVM heap max/used/committed, non-heap used, total system memory,
 *         free system memory, disk space (working directory)</td>
 *     <td>{@link MemoryMXBean}, {@link Runtime}, {@link File}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Network / Host</strong></td>
 *     <td>hostname, local IP address</td>
 *     <td>{@link InetAddress#getLocalHost()}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>User / Environment</strong></td>
 *     <td>user name, home directory, working directory, default charset,
 *         default locale, default timezone</td>
 *     <td>{@code System.getProperty("user.*")},
 *         {@link Charset#defaultCharset()},
 *         {@link Locale#getDefault()},
 *         {@link TimeZone#getDefault()}</td>
 *   </tr>
 *   <tr>
 *     <td><strong>Demo config</strong></td>
 *     <td>context path, server port, active Spring profiles (if set via
 *         {@code -Dspring.profiles.active}), logging level</td>
 *     <td>{@code System.getProperty()} for JVM {@code -D} arguments</td>
 *   </tr>
 * </table>
 *
 * <h3>Usage — in {@code DemoApplication.main()}</h3>
 * <pre>{@code
 * public static void main(String[] args) {
 *     nonConsumer = true;                           // 1. demo guard
 *     StartupDiagnosticUtil.print(args);            // 2. environment snapshot
 *     SpringApplication.run(DemoApplication.class, args);  // 3. Spring starts
 * }
 * }</pre>
 *
 * <h3>Sample output</h3>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         api-request-logging-spring-boot-starter — DEMO          ║
 * ║                  STARTUP DIAGNOSTIC REPORT                      ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ── JAVA / JVM ──────────────────────────────────────────────────────
 *    Java Version      : 17.0.10
 *    Java Spec Version : 17
 *    Java Vendor       : Eclipse Adoptium
 *    Java Home         : C:\Program Files\Eclipse Adoptium\jdk-17.0.10
 *    JVM Name          : OpenJDK 64-Bit Server VM
 *    JVM Version       : 17.0.10+7
 *    JVM PID           : 28872
 *    JVM Uptime        : 312 ms (time since JVM started to this print)
 *    JVM Arguments     : [-Dserver.port=8080, -Dspring.profiles.active=demo]
 *
 * ── OPERATING SYSTEM ────────────────────────────────────────────────
 *    OS Name           : Windows 11
 *    OS Version        : 10.0
 *    OS Architecture   : amd64
 *    OS Family         : Windows
 *
 * ── HARDWARE / CPU ──────────────────────────────────────────────────
 *    Available CPUs    : 12 (logical processors)
 *
 * ── MEMORY ──────────────────────────────────────────────────────────
 *    Heap — Max        : 4,096 MB
 *    Heap — Used       : 64 MB
 *    Heap — Committed  : 256 MB
 *    Non-Heap Used     : 28 MB
 *    Disk (work dir)   : 120,452 MB free of 476,837 MB total
 *
 * ── NETWORK / HOST ──────────────────────────────────────────────────
 *    Hostname          : DESKTOP-ABC123
 *    Local IP          : 192.168.1.42
 *
 * ── USER / ENVIRONMENT ──────────────────────────────────────────────
 *    User Name         : ymerugu
 *    User Home         : C:\Users\ymerugu
 *    Working Directory : C:\projects\api-request-logging-spring-boot-starter
 *    Default Charset   : UTF-8
 *    Default Locale    : en_IN
 *    Default Timezone  : Asia/Kolkata (IST, UTC+05:30)
 *
 * ── DEMO CONFIGURATION ──────────────────────────────────────────────
 *    Context Path      : /api-request-logging-demo
 *    Server Port       : 8080
 *    Active Profiles   : (none — using default)
 *    Logging Level     : (not set — using logback default)
 *    Command-line Args : (none)
 *
 * ════════════════════════════════════════════════════════════════════
 * Spring Boot starting… (SpringApplication.run)
 * ════════════════════════════════════════════════════════════════════
 * </pre>
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li><strong>No Spring dependency</strong> — runs before the Spring context
 *       exists; may not import any {@code org.springframework.*} class.</li>
 *   <li><strong>Never throws</strong> — every section catches all exceptions
 *       and prints a {@code (unavailable)} placeholder.  A diagnostic failure
 *       must never prevent Spring from starting.</li>
 *   <li><strong>All static</strong> — no instantiation; call via
 *       {@link #print(String[])}.</li>
 *   <li><strong>Output to {@code System.out}</strong> — SLF4J / Logback is not
 *       yet initialised when this runs; {@code System.out} is the only safe
 *       output channel at this point.</li>
 * </ul>
 *
 * @author Yash
 * @since 1.2.0
 * @see DemoApplication#main(String[])
 */
public final class StartupDiagnosticUtil {

    /** Banner width — number of characters in each separator line. */
    private static final int WIDTH = 68;

    /** Top-border character. */
    private static final String TOP    = "╔" + repeat('═', WIDTH - 2) + "╗";

    /** Mid-border character. */
    private static final String BOTTOM = "╚" + repeat('═', WIDTH - 2) + "╝";

    /** Section separator — printed before each diagnostic group. */
    private static final String SEP    = repeat('═', WIDTH);

    /** Thin section header separator. */
    private static final String THIN   = "── ";

    /**
     * Private constructor — all methods are static; instantiation is forbidden.
     *
     * @throws UnsupportedOperationException always
     */
    private StartupDiagnosticUtil() {
        throw new UnsupportedOperationException(
                "StartupDiagnosticUtil is a static utility class — do not instantiate.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints the full startup diagnostic report to {@code System.out}.
     *
     * <p>This is the only method that consumer code (i.e. {@code DemoApplication.main()})
     * needs to call.  All sections are printed in a fixed order:</p>
     * <ol>
     *   <li>Banner header</li>
     *   <li>Java / JVM details</li>
     *   <li>Operating system details</li>
     *   <li>Hardware / CPU</li>
     *   <li>Memory (heap, non-heap, disk)</li>
     *   <li>Network / hostname</li>
     *   <li>User and environment</li>
     *   <li>Demo configuration (JVM {@code -D} args and command-line args)</li>
     *   <li>Footer — "Spring Boot starting…"</li>
     * </ol>
     *
     * <p>Every section is wrapped in a try-catch so that a failure in one section
     * (e.g. a security manager blocking {@code InetAddress.getLocalHost()}) does
     * not prevent the remaining sections from printing.</p>
     *
     * @param args the command-line arguments passed to {@code main(String[])} —
     *             printed in the demo-configuration section; may be {@code null}
     *             or empty
     */
    public static void print(String[] args) {
        out("");
        out(TOP);
        outCentered("api-request-logging-spring-boot-starter — DEMO");
        outCentered("STARTUP DIAGNOSTIC REPORT");
        out(BOTTOM);
        out("");

        printJavaSection();
        printOsSection();
        printCpuSection();
        printMemorySection();
        printNetworkSection();
        printUserSection();
        printDemoConfigSection(args);

        out("");
        out(SEP);
        out("  Spring Boot starting\u2026 (SpringApplication.run)");
        out(SEP);
        out("");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — JAVA / JVM
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints Java language version, JVM vendor/name/version, process ID,
     * JVM uptime (ms since JVM start to this print call), and any JVM
     * arguments passed via {@code -D} or {@code -X} flags.
     *
     * <h4>System properties used</h4>
     * <ul>
     *   <li>{@code java.version} — e.g. {@code 17.0.10}</li>
     *   <li>{@code java.specification.version} — e.g. {@code 17}</li>
     *   <li>{@code java.vendor} — e.g. {@code Eclipse Adoptium}</li>
     *   <li>{@code java.home} — JRE/JDK root directory</li>
     *   <li>{@code java.vm.name} — e.g. {@code OpenJDK 64-Bit Server VM}</li>
     *   <li>{@code java.vm.version} — e.g. {@code 17.0.10+7}</li>
     * </ul>
     *
     * <h4>MXBean APIs used</h4>
     * <ul>
     *   <li>{@link RuntimeMXBean#getPid()} (Java 10+) with
     *       {@link RuntimeMXBean#getName()} fallback for Java 8</li>
     *   <li>{@link RuntimeMXBean#getUptime()} — milliseconds since JVM start</li>
     *   <li>{@link RuntimeMXBean#getInputArguments()} — JVM flags list</li>
     * </ul>
     */
    private static void printJavaSection() {
        try {
            sectionHeader("JAVA / JVM");
            RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();

            row("Java Version",      System.getProperty("java.version",               "(unavailable)"));
            row("Java Spec Version", System.getProperty("java.specification.version", "(unavailable)"));
            row("Java Vendor",       System.getProperty("java.vendor",                "(unavailable)"));
            row("Java Home",         System.getProperty("java.home",                  "(unavailable)"));
            row("JVM Name",          System.getProperty("java.vm.name",               "(unavailable)"));
            row("JVM Version",       System.getProperty("java.vm.version",            "(unavailable)"));

            // PID — RuntimeMXBean.getPid() added in Java 9; fall back to getName()
            // which returns "<pid>@<hostname>" on HotSpot.
            String pid;
            try {
                // Java 9+ — direct API
                pid = ""; //String.valueOf(rt.getPid());
            } catch (NoSuchMethodError e) {
                // Java 8 fallback: getName() = "12345@hostname"
                String name = rt.getName();
                pid = name.contains("@") ? name.substring(0, name.indexOf('@')) : name;
            }
            row("JVM PID",     pid);
            row("JVM Uptime",  rt.getUptime() + " ms  (time from JVM start to this print)");

            // JVM arguments (-Xmx, -Dkey=value, etc.)
            List<String> jvmArgs = rt.getInputArguments();
            if (jvmArgs == null || jvmArgs.isEmpty()) {
                row("JVM Arguments", "(none)");
            } else {
                row("JVM Arguments", jvmArgs.get(0));
                for (int i = 1; i < jvmArgs.size(); i++) {
                    row("",            jvmArgs.get(i));
                }
            }
            out("");
        } catch (Exception e) {
            out("   [Java/JVM section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — OPERATING SYSTEM
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints operating system name, version, CPU architecture, and a
     * detected OS family label.
     *
     * <h4>System properties used</h4>
     * <ul>
     *   <li>{@code os.name}  — e.g. {@code Windows 11}, {@code Linux}, {@code Mac OS X}</li>
     *   <li>{@code os.version} — kernel or build version string</li>
     *   <li>{@code os.arch} — e.g. {@code amd64}, {@code aarch64}, {@code x86}</li>
     * </ul>
     *
     * <h4>OS family detection</h4>
     * <table border="1" cellpadding="4">
     *   <caption>OS family detection logic</caption>
     *   <tr><th>os.name prefix / substring</th><th>Family label</th></tr>
     *   <tr><td>Starts with {@code "Windows"}</td><td>{@code Windows}</td></tr>
     *   <tr><td>Starts with {@code "Linux"}</td><td>{@code Linux}</td></tr>
     *   <tr><td>Contains {@code "mac"} (case-insensitive)</td><td>{@code macOS}</td></tr>
     *   <tr><td>Contains {@code "sunos"}</td><td>{@code Solaris}</td></tr>
     *   <tr><td>Anything else</td><td>{@code Unknown}</td></tr>
     * </table>
     */
    private static void printOsSection() {
        try {
            sectionHeader("OPERATING SYSTEM");
            String osName    = System.getProperty("os.name",    "(unavailable)");
            String osVersion = System.getProperty("os.version", "(unavailable)");
            String osArch    = System.getProperty("os.arch",    "(unavailable)");

            row("OS Name",         osName);
            row("OS Version",      osVersion);
            row("OS Architecture", osArch);
            row("OS Family",       detectOsFamily(osName));
            out("");
        } catch (Exception e) {
            out("   [OS section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — HARDWARE / CPU
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints the number of logical processors available to the JVM.
     *
     * <h4>API used</h4>
     * <p>{@link Runtime#availableProcessors()} returns the number of logical
     * CPU cores (including hyper-threaded virtual cores) that the JVM can use.
     * This number can change at runtime on some operating systems if the process
     * affinity mask is altered — but at startup it reflects the effective
     * concurrency ceiling for {@code ForkJoinPool.commonPool()} and the default
     * Tomcat thread pool size (10 × processors, capped at 200).</p>
     *
     * <p>Note: this is the <em>logical</em> processor count, not physical cores.
     * A 6-core processor with hyper-threading enabled will report {@code 12}.</p>
     */
    private static void printCpuSection() {
        try {
            sectionHeader("HARDWARE / CPU");
            int cpus = Runtime.getRuntime().availableProcessors();
            row("Available CPUs", cpus + " logical processor(s)");
            out("");
        } catch (Exception e) {
            out("   [CPU section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — MEMORY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints JVM heap statistics, non-heap usage, and working-directory disk space.
     *
     * <h4>Heap memory — {@link MemoryMXBean}</h4>
     * <table border="1" cellpadding="4">
     *   <caption>Heap memory fields</caption>
     *   <tr><th>Field</th><th>Meaning</th></tr>
     *   <tr>
     *     <td>Heap Max</td>
     *     <td>Maximum amount of memory the JVM will ever use for the heap
     *         ({@code -Xmx}). {@code -1} means unlimited.</td>
     *   </tr>
     *   <tr>
     *     <td>Heap Used</td>
     *     <td>Bytes currently occupied by live objects.  Rises during request
     *         processing and drops after GC.</td>
     *   </tr>
     *   <tr>
     *     <td>Heap Committed</td>
     *     <td>Bytes currently reserved from the OS for the heap.  May be smaller
     *         than Max — the JVM expands committed memory on demand.</td>
     *   </tr>
     *   <tr>
     *     <td>Non-Heap Used</td>
     *     <td>Bytes used by JVM internals: class metadata (Metaspace), JIT
     *         compiled code cache, thread stacks, etc.</td>
     *   </tr>
     * </table>
     *
     * <h4>Disk space — {@link File}</h4>
     * <p>Reports free and total disk space on the partition hosting the JVM's
     * working directory ({@code System.getProperty("user.dir")}).  Useful to
     * identify whether the demo will run out of space for log files.</p>
     */
    private static void printMemorySection() {
        try {
            sectionHeader("MEMORY");
            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

            MemoryUsage heap    = mem.getHeapMemoryUsage();
            MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

            row("Heap — Max",       formatMB(heap.getMax())       + " MB");
            row("Heap — Used",      formatMB(heap.getUsed())      + " MB");
            row("Heap — Committed", formatMB(heap.getCommitted()) + " MB");
            row("Non-Heap Used",    formatMB(nonHeap.getUsed())   + " MB");

            // Disk space on the working-directory partition
            File workDir = new File(System.getProperty("user.dir", "."));
            long freeBytes  = workDir.getFreeSpace();
            long totalBytes = workDir.getTotalSpace();
            row("Disk (work dir)",
                formatMB(freeBytes) + " MB free of " + formatMB(totalBytes) + " MB total");

            out("");
        } catch (Exception e) {
            out("   [Memory section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — NETWORK / HOST
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints the machine's hostname and primary local IP address.
     *
     * <h4>API used</h4>
     * <p>{@link InetAddress#getLocalHost()} performs a local DNS lookup to
     * resolve the machine name.  This call can block briefly on some systems
     * (typically {@code &lt;100 ms}) and may throw {@link UnknownHostException}
     * if the local hostname is not resolvable — in which case the field is
     * printed as {@code (unavailable)}.</p>
     *
     * <p>The IP address is the interface-bound address that the JVM's
     * networking stack considers "local" — usually the primary LAN/Wi-Fi
     * address or {@code 127.0.0.1} when no network interface is active.</p>
     */
    private static void printNetworkSection() {
        sectionHeader("NETWORK / HOST");
        try {
            InetAddress addr = InetAddress.getLocalHost();
            row("Hostname", addr.getHostName());
            row("Local IP", addr.getHostAddress());
        } catch (UnknownHostException e) {
            row("Hostname", "(unavailable — " + e.getMessage() + ")");
            row("Local IP", "(unavailable)");
        } catch (Exception e) {
            row("Hostname", "(unavailable — " + e.getMessage() + ")");
            row("Local IP", "(unavailable)");
        }
        out("");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — USER / ENVIRONMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints the OS user account, filesystem paths, character encoding,
     * locale, and timezone.
     *
     * <h4>System properties used</h4>
     * <ul>
     *   <li>{@code user.name} — OS login name of the process owner</li>
     *   <li>{@code user.home} — home directory of the process owner</li>
     *   <li>{@code user.dir}  — working directory at JVM launch (where
     *       {@code mvn spring-boot:run} was executed)</li>
     * </ul>
     *
     * <h4>JDK APIs used</h4>
     * <ul>
     *   <li>{@link Charset#defaultCharset()} — the default character encoding
     *       used by {@code FileReader}, {@code FileWriter}, and
     *       {@code InputStreamReader} when no charset is specified explicitly.
     *       Should be {@code UTF-8}; if not, file I/O bugs may occur.</li>
     *   <li>{@link Locale#getDefault()} — controls number formatting, date
     *       formatting, and collation order.</li>
     *   <li>{@link TimeZone#getDefault()} — the timezone used by
     *       {@link java.util.Date} and {@link java.util.Calendar}; does not
     *       affect {@link java.time} classes (those default to
     *       {@link ZoneId#systemDefault()}).</li>
     * </ul>
     */
    private static void printUserSection() {
        try {
            sectionHeader("USER / ENVIRONMENT");

            row("User Name",         System.getProperty("user.name", "(unavailable)"));
            row("User Home",         System.getProperty("user.home", "(unavailable)"));
            row("Working Directory", System.getProperty("user.dir",  "(unavailable)"));
            row("Default Charset",   Charset.defaultCharset().name());
            row("Default Locale",    Locale.getDefault().toString());

            TimeZone tz = TimeZone.getDefault();
            String tzDisplay = tz.getID()
                    + "  (" + tz.getDisplayName(false, TimeZone.SHORT)
                    + ",  UTC" + formatUtcOffset(tz.getRawOffset()) + ")";
            row("Default Timezone",  tzDisplay);

            out("");
        } catch (Exception e) {
            out("   [User/Environment section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SECTION — DEMO CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints demo-specific configuration values that were passed as JVM
     * {@code -D} system properties or as command-line arguments to {@code main}.
     *
     * <h4>Properties checked</h4>
     * <table border="1" cellpadding="4">
     *   <caption>JVM system properties checked for demo config</caption>
     *   <tr><th>System Property ({@code -D…})</th><th>Field label</th><th>Default if absent</th></tr>
     *   <tr>
     *     <td>{@code server.servlet.context-path}</td>
     *     <td>Context Path</td>
     *     <td>{@code (not set — Spring default: /)} </td>
     *   </tr>
     *   <tr>
     *     <td>{@code server.port}</td>
     *     <td>Server Port</td>
     *     <td>{@code (not set — Spring default: 8080)}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code spring.profiles.active}</td>
     *     <td>Active Profiles</td>
     *     <td>{@code (none — using default)}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code logging.level.root}</td>
     *     <td>Root Logging Level</td>
     *     <td>{@code (not set — using logback default)}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code api.request.logging.enabled}</td>
     *     <td>API Logging Enabled</td>
     *     <td>{@code (not set — check application.properties)}</td>
     *   </tr>
     * </table>
     *
     * <p>Command-line arguments passed to {@code main(String[])} are also printed,
     * which includes any {@code --spring.*} arguments passed via
     * {@code mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"}.</p>
     *
     * @param args the {@code String[]} passed to {@code DemoApplication.main(String[])}
     */
    private static void printDemoConfigSection(String[] args) {
        try {
            sectionHeader("DEMO CONFIGURATION");

            row("Context Path",
                sysProp("server.servlet.context-path",
                        "(not set — Spring default: /)"));
            row("Server Port",
                sysProp("server.port",
                        "(not set — Spring default: 8080)"));
            row("Active Profiles",
                sysProp("spring.profiles.active",
                        "(none — using default)"));
            row("Root Logging Level",
                sysProp("logging.level.root",
                        "(not set — using logback default)"));
            row("API Logging Enabled",
                sysProp("api.request.logging.enabled",
                        "(not set — check application.properties)"));

            // Command-line args (--key=value style, passed via mvn spring-boot:run)
            if (args == null || args.length == 0) {
                row("Command-line Args", "(none)");
            } else {
                row("Command-line Args", args[0]);
                for (int i = 1; i < args.length; i++) {
                    row("", args[i]);
                }
            }

            out("");
        } catch (Exception e) {
            out("   [Demo config section unavailable: " + e.getMessage() + "]");
            out("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS — formatting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prints a section header in the format:
     * <pre>── SECTION NAME ──────────────────────────────────────────</pre>
     *
     * @param title the section title in UPPER CASE
     */
    private static void sectionHeader(String title) {
        String line = THIN + title + " ";
        int remaining = WIDTH - line.length();
        if (remaining > 0) {
            line = line + repeat('─', remaining);
        }
        out(line);
    }

    /**
     * Prints a two-column label/value row padded so that all values align.
     *
     * <p>Label column is left-padded with 3 spaces and right-padded to
     * 20 characters.  If the label is empty (used for continuation lines),
     * the value is indented to align with the previous row's value.</p>
     *
     * @param label the field name (left column)
     * @param value the field value (right column)
     */
    private static void row(String label, String value) {
        if (label == null || label.isEmpty()) {
            // continuation line — indent to value column
            out("                          " + value);
        } else {
            String padded = String.format("   %-22s: %s", label, value);
            out(padded);
        }
    }

    /**
     * Converts bytes to megabytes, formatted with thousand-separator commas.
     *
     * <p>Returns {@code "unlimited"} when {@code bytes} is {@code -1},
     * which is what {@link MemoryUsage#getMax()} returns when no maximum
     * is configured ({@code -Xmx} not set).</p>
     *
     * @param bytes byte count; {@code -1} means unlimited
     * @return human-readable MB string, e.g. {@code "4,096"}
     */
    private static String formatMB(long bytes) {
        if (bytes == -1) return "unlimited";
        return String.format("%,d", bytes / (1024 * 1024));
    }

    /**
     * Formats a UTC offset in milliseconds as {@code +HH:MM} or {@code -HH:MM}.
     *
     * <p>Example: {@code 19800000} ms (India Standard Time) → {@code "+05:30"}</p>
     *
     * @param offsetMillis raw offset in milliseconds from
     *                     {@link TimeZone#getRawOffset()}
     * @return formatted UTC offset string
     */
    private static String formatUtcOffset(int offsetMillis) {
        int totalMinutes = Math.abs(offsetMillis) / 60_000;
        int hours        = totalMinutes / 60;
        int minutes      = totalMinutes % 60;
        char sign        = offsetMillis >= 0 ? '+' : '-';
        return String.format("%c%02d:%02d", sign, hours, minutes);
    }

    /**
     * Detects the OS family from the {@code os.name} system property.
     *
     * @param osName value of {@code System.getProperty("os.name")}
     * @return one of {@code Windows}, {@code Linux}, {@code macOS},
     *         {@code Solaris}, or {@code Unknown}
     */
    private static String detectOsFamily(String osName) {
        if (osName == null) return "Unknown";
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("windows")) return "Windows";
        if (lower.startsWith("linux"))   return "Linux";
        if (lower.contains("mac"))       return "macOS";
        if (lower.contains("sunos"))     return "Solaris";
        return "Unknown";
    }

    /**
     * Returns the value of a JVM system property, or {@code defaultValue}
     * if the property is not set.
     *
     * @param key          system property key (e.g. {@code "server.port"})
     * @param defaultValue value to return when the property is absent
     * @return property value or {@code defaultValue}
     */
    private static String sysProp(String key, String defaultValue) {
        String val = System.getProperty(key);
        return (val != null && !val.trim().isEmpty()) ? val : defaultValue;
    }

    /**
     * Prints a line to {@code System.out}.
     *
     * <p>All output in this class routes through this single method so that
     * switching to a different output channel (e.g. {@code System.err} or a
     * file writer) requires only one change.</p>
     *
     * @param line the text to print (a newline is appended by
     *             {@link System#out#println})
     */
    private static void out(String line) {
        System.out.println(line);
    }

    /**
     * Centers {@code text} within {@link #WIDTH} characters, surrounded by
     * {@code ║} border characters, for use in the banner header.
     *
     * @param text the text to centre
     */
    private static void outCentered(String text) {
        int innerWidth = WIDTH - 2; // subtract the two ║ characters
        int padding    = (innerWidth - text.length()) / 2;
        int extra      = (innerWidth - text.length()) % 2; // handle odd-length
        String line    = "║" + repeat(' ', padding) + text
                       + repeat(' ', padding + extra) + "║";
        out(line);
    }

    /**
     * Returns a {@link String} consisting of {@code count} repetitions of
     * {@code ch}.  Used to build separator lines and padding without requiring
     * Java 11's {@code String.repeat()}.
     *
     * @param ch    the character to repeat
     * @param count number of repetitions; must be {@code >= 0}
     * @return a string of {@code count} copies of {@code ch}
     */
    private static String repeat(char ch, int count) {
        if (count <= 0) return "";
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, ch);
        return new String(chars);
    }
}