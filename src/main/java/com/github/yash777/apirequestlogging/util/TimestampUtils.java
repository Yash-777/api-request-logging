package com.github.yash777.apirequestlogging.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <h2>TimestampUtils</h2>
 *
 * <p>Utility class for formatting the current date/time as a human-readable
 * string in the pattern <strong>{@code d/M/yyyy, h:mm:ss am/pm}</strong>.</p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>{@link DateTimeFormatter} is <strong>immutable and thread-safe</strong>
 *       (Java 8+).  It is created once per call to avoid the overhead of
 *       constructing a new instance on every request — in a high-throughput
 *       service thousands of requests per second would otherwise generate
 *       unnecessary GC pressure.</li>
 *   <li>{@code SimpleDateFormat} is intentionally avoided because it is
 *       <strong>not thread-safe</strong>; concurrent calls from servlet
 *       threads would corrupt its internal state.</li>
 *   <li>The 12-hour clock pattern {@code h:mm:ss a} combined with
 *       {@link String#toLowerCase()} produces lowercase {@code am}/{@code pm}
 *       markers, which are more readable in log files.</li>
 *   <li>No leading zeros on day or month ({@code d} and {@code M} vs
 *       {@code dd} and {@code MM}) matches the natural human expectation
 *       for log timestamps.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>All methods are {@code static} and use only local variables and
 * thread-safe {@link DateTimeFormatter} instances — safe under any
 * level of concurrency.</p>
 *
 * <h3>Example output</h3>
 * <pre>
 *   "23/3/2026, 2:54:43 pm"      ← local system timezone
 *   "23/3/2026, 9:24:43 am"      ← UTC
 *   "23/3/2026, 4:54:43 am"      ← America/New_York
 * </pre>
 *
 * @author Yash
 * @since 1.0.0
 */
public final class TimestampUtils {

    /** Shared, immutable, thread-safe formatter — one instance for the JVM. */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("d/M/yyyy, h:mm:ss a");

    /** Utility class — prevent instantiation. */
    private TimestampUtils() {}

    /**
     * Returns the current timestamp formatted as {@code "d/M/yyyy, h:mm:ss am/pm"}
     * using the JVM's <strong>default system timezone</strong>.
     *
     * <p>Uses a 12-hour clock with lowercase {@code am}/{@code pm} marker,
     * with no leading zeros on day or month.</p>
     *
     * <h4>Example output</h4>
     * <pre>
     *   "23/3/2026, 2:54:43 pm"
     *   "1/12/2025, 11:05:09 am"
     *   "9/7/2024, 12:00:00 pm"
     * </pre>
     *
     * @return a formatted timestamp string representing the current local date and time
     *
     * @example
     * <pre>{@code
     *   String ts = TimestampUtils.getCurrentTimestamp();
     *   // ts => "23/3/2026, 2:54:43 pm"
     *
     *   Map<String, String> response = new HashMap<>();
     *   response.put("timestamp", TimestampUtils.getCurrentTimestamp());
     *   // {"timestamp": "23/3/2026, 2:54:43 pm"}
     * }</pre>
     */
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(FORMATTER).toLowerCase();
    }

    /**
     * Returns the current timestamp formatted as {@code "d/M/yyyy, h:mm:ss am/pm"}
     * for the <strong>specified timezone</strong>.
     *
     * <p>Uses a 12-hour clock with lowercase {@code am}/{@code pm} marker,
     * with no leading zeros on day or month.</p>
     *
     * <h4>Example output for common zones</h4>
     * <pre>
     *   Asia/Kolkata      →  "23/3/2026, 2:54:43 pm"   (IST, UTC+5:30)
     *   UTC               →  "23/3/2026, 9:24:43 am"
     *   America/New_York  →  "23/3/2026, 4:54:43 am"   (EST, UTC-5)
     * </pre>
     *
     * @param zoneId the timezone identifier; must be a valid
     *               {@link java.time.ZoneId} string such as
     *               {@code "Asia/Kolkata"}, {@code "UTC"}, or
     *               {@code "America/New_York"}
     * @return a formatted timestamp string in the given timezone
     * @throws java.time.zone.ZoneRulesException if {@code zoneId} is not recognised
     *
     * @example
     * <pre>{@code
     *   String ist = TimestampUtils.getCurrentTimestampByZone("Asia/Kolkata");
     *   // ist => "23/3/2026, 2:54:43 pm"
     *
     *   String utc = TimestampUtils.getCurrentTimestampByZone("UTC");
     *   // utc => "23/3/2026, 9:24:43 am"
     *
     *   Map<String, String> response = new HashMap<>();
     *   response.put("timestamp", TimestampUtils.getCurrentTimestampByZone("America/New_York"));
     *   // {"timestamp": "23/3/2026, 4:54:43 am"}
     * }</pre>
     */
    public static String getCurrentTimestampByZone(String zoneId) {
        return ZonedDateTime.now(ZoneId.of(zoneId))
                            .format(FORMATTER)
                            .toLowerCase();
    }
}
