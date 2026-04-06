package com.github.yash777.apirequestlogging.masking;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for masking sensitive field values in JSON strings and HTTP headers.
 *
 * <p>Masking is applied via case-insensitive regex on the serialised JSON string,
 * replacing matching field values with a configurable replacement token. The original
 * value type does not matter — string, number, boolean, and null values are all
 * matched and replaced with the replacement token written as a quoted JSON string.</p>
 *
 * <h3>Example — all JSON value types masked as a quoted string</h3>
 * <pre>{@code
 * Input:  {"username":"john","password":"secret123","amount":500,"active":true,"ref":null}
 * Fields: [password, amount, active, ref]
 *
 * Output: {"username":"john","password":"***MASKED***","amount":"***MASKED***","active":"***MASKED***","ref":"***MASKED***"}
 * }</pre>
 *
 * <p><b>Note:</b> Because the replacement is always emitted as a quoted JSON string,
 * the output remains parseable even when the original value was a number, boolean,
 * or null.</p>
 *
 * <p><b>Limitation:</b> Regex-based masking reliably handles flat JSON and fields at
 * any nesting depth (matched left-to-right). Deeply nested duplicate keys or
 * array-valued fields may require a Jackson-based approach for full accuracy.</p>
 *
 * @author Yash
 * @since 1.1.0
 */
public final class SecretMaskingUtil {

    private SecretMaskingUtil() {}

    /**
     * Regex fragment matching any valid JSON value. Covers all four JSON value types:
     * <ol>
     *   <li><b>Quoted string</b> — handles internal escaped quotes ({@code \"})</li>
     *   <li><b>Number</b>        — integer, decimal, scientific notation, optional leading minus</li>
     *   <li><b>Boolean</b>       — {@code true} or {@code false}</li>
     *   <li><b>Null</b>          — {@code null}</li>
     * </ol>
     * The string branch is listed first so it takes priority over the number branch
     * when a quoted number (e.g. {@code "42"}) is encountered.
     */
    private static final String ANY_JSON_VALUE =
            "(?:\"(?:[^\"\\\\]|\\\\.)*\""                          // 1. "string" (handles \" inside)
          + "|-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"         // 2. number (int / decimal / sci)
          + "|true|false|null"                                      // 3. boolean / null
          + ")";

    /**
     * Masks sensitive field values in a JSON string, including string, number,
     * boolean, and null value types.
     *
     * <p>Matching is case-insensitive and tolerates optional whitespace around the
     * colon separator. Fields at any nesting depth are matched because the regex
     * scans the entire serialised string. When the same key appears more than once,
     * all occurrences are masked (left-to-right via {@link String#replaceAll}).</p>
     *
     * <p>The replacement is always written as a quoted JSON string (e.g.
     * {@code "***MASKED***"}), so the output remains parseable even when the
     * original value was a number, boolean, or null.</p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * Input:  {"username":"john","password":"s3cr3t","retries":3,"active":true}
     * Fields: [password, retries, active]
     *
     * Output: {"username":"john","password":"***MASKED***","retries":"***MASKED***","active":"***MASKED***"}
     * }</pre>
     *
     * @param json        the JSON string to process; returned unchanged if {@code null} or empty
     * @param fields      field names whose values should be masked; matching is case-insensitive;
     *                    returned unchanged if {@code null} or empty
     * @param replacement the replacement token (e.g. {@code "***MASKED***"}); written as a
     *                    quoted JSON string in the output
     * @return the masked JSON string, or the original string when no masking is applicable
     */
    public static String mask(String json, List<String> fields, String replacement) {
        if (json == null || json.isEmpty() || fields == null || fields.isEmpty()) {
            return json;
        }

        // Always emit replacement as a JSON string; escape \ and $ to avoid
        // back-reference interpretation by replaceAll, and escape " for JSON validity.
        String quotedReplacement = "\"" + replacement.replace("\\", "\\\\")
                                                      .replace("$", "\\$")
                                                      .replace("\"", "\\\"") + "\"";
        String masked = json;
        for (String field : fields) {
            // Group $1 captures the key + colon + optional whitespace verbatim,
            // preserving the original formatting while only replacing the value.
            String regex = "(?i)(\"" + Pattern.quote(field) + "\"\\s*:\\s*)" + ANY_JSON_VALUE;
            masked = masked.replaceAll(regex, "$1" + quotedReplacement);
        }
        return masked;
    }

    /**
     * Masks sensitive field values in a JSON string, but only for fields whose
     * values are JSON strings (i.e. quoted values). Number, boolean, and null
     * values are left untouched.
     *
     * <p>Use {@link #mask(String, List, String)} when non-string value types
     * (numbers, booleans, nulls) also need to be masked.</p>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * Input:  {"username":"john","password":"secret123","amount":500}
     * Fields: [password]
     *
     * Output: {"username":"john","password":"***MASKED***","amount":500}
     * }</pre>
     *
     * @param json        the JSON string to process; returned unchanged if {@code null} or empty
     * @param fields      field names whose string values should be masked; matching is
     *                    case-insensitive; returned unchanged if {@code null} or empty
     * @param replacement the replacement token (e.g. {@code "***MASKED***"})
     * @return the masked JSON string, or the original string when no masking is applicable
     */
    public static String maskOnlyString(String json, List<String> fields, String replacement) {
        if (json == null || json.isEmpty() || fields == null || fields.isEmpty()) {
            return json;
        }
        String masked = json;
        // Escape $ to prevent replaceAll from interpreting it as a back-reference.
        String safeReplacement = "\"" + replacement.replace("$", "\\$") + "\"";
        for (String field : fields) {
            // Matches: "fieldName" : "any string value"
            // Handles optional whitespace around the colon.
            String regex = "(?i)(\"" + Pattern.quote(field) + "\"\\s*:\\s*)\"[^\"]*\"";
            masked = masked.replaceAll(regex, "$1" + safeReplacement);
        }
        return masked;
    }

    /**
     * Returns {@code true} when {@code headerName} appears in the mask field list,
     * using a case-insensitive comparison.
     *
     * <p>Intended for use by {@code RequestLogCollector} to determine whether an
     * HTTP header value should be masked before it is stored in the request log.</p>
     *
     * @param headerName the HTTP header name to check; {@code null} returns {@code false}
     * @param fields     the configured list of field names to mask; {@code null} returns {@code false}
     * @return {@code true} if {@code headerName} matches any entry in {@code fields}
     *         (case-insensitive); {@code false} otherwise
     */
    public static boolean shouldMaskHeader(String headerName, List<String> fields) {
        if (headerName == null || fields == null) return false;
        for (String field : fields) {
            if (field.equalsIgnoreCase(headerName)) return true;
        }
        return false;
    }
}