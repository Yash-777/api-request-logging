package com.github.yash777.apirequestlogging.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecretMaskingUtil}.
 *
 * <p>Covers all four JSON value types (string, number, boolean, null),
 * edge cases (null input, empty fields, case-insensitivity), and
 * the header-masking helper {@link SecretMaskingUtil#shouldMaskHeader}.</p>
 */
public class SecretMaskingUtilTest {

    private static final String REPLACEMENT = "***MASKED***";
    private static final List<String> FIELDS =
            Arrays.asList("password", "token", "authorization");

    // ══════════════════════════════════════════════════════════════════
    //  mask() — string values
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("mask() — string JSON values")
    class StringValues {

        @Test
        @DisplayName("masks a single string field")
        void singleStringField() {
            String json = "{\"username\":\"john\",\"password\":\"secret123\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"username\":\"john\",\"password\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks multiple string fields in one pass")
        void multipleStringFields() {
            String json = "{\"password\":\"abc\",\"token\":\"tok123\",\"user\":\"john\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertTrue(result.contains("\"password\":\"***MASKED***\""));
            assertTrue(result.contains("\"token\":\"***MASKED***\""));
            assertTrue(result.contains("\"user\":\"john\""));
        }

        @Test
        @DisplayName("masks field with whitespace around colon")
        void whitespaceAroundColon() {
            String json = "{\"password\" : \"secret\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertTrue(result.contains("\"***MASKED***\""));
            assertFalse(result.contains("\"secret\""));
        }

        @Test
        @DisplayName("masks empty string value")
        void emptyStringValue() {
            String json = "{\"password\":\"\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"password\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks field with escaped quotes inside value")
        void escapedQuotesInValue() {
            String json = "{\"password\":\"he said \\\"hello\\\"\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertTrue(result.contains("\"***MASKED***\""));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  mask() — numeric values  (THE BUG THAT WAS FIXED)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("mask() — numeric JSON values (fixed bug)")
    class NumericValues {

        @Test
        @DisplayName("masks integer value")
        void integerValue() {
            String json = "{\"password\":12345}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"password\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks decimal value")
        void decimalValue() {
            // This was the exact failing case from the weather API response
            String json = "{\"latitude\":17.375,\"longitude\":78.5}";
            List<String> numericFields = Arrays.asList("latitude", "longitude");
            String result = SecretMaskingUtil.mask(json, numericFields, REPLACEMENT);
            assertEquals("{\"latitude\":\"***MASKED***\",\"longitude\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks negative number")
        void negativeNumber() {
            String json = "{\"token\":-99}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"token\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks scientific-notation number")
        void scientificNotation() {
            String json = "{\"token\":1.5e10}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"token\":\"***MASKED***\"}", result);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  mask() — boolean and null values  (THE BUG THAT WAS FIXED)
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("mask() — boolean and null JSON values (fixed bug)")
    class BooleanAndNullValues {

        @Test
        @DisplayName("masks true boolean value")
        void trueBoolean() {
            String json = "{\"authorization\":true}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"authorization\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks false boolean value")
        void falseBoolean() {
            String json = "{\"authorization\":false}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"authorization\":\"***MASKED***\"}", result);
        }

        @Test
        @DisplayName("masks null value")
        void nullJsonValue() {
            String json = "{\"token\":null}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals("{\"token\":\"***MASKED***\"}", result);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  mask() — case-insensitivity
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("mask() — case-insensitive field matching")
    class CaseInsensitivity {

        @Test
        @DisplayName("matches field name in uppercase")
        void uppercaseField() {
            String json = "{\"PASSWORD\":\"secret\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertTrue(result.contains("\"***MASKED***\""));
        }

        @Test
        @DisplayName("matches field name in mixed case")
        void mixedCaseField() {
            String json = "{\"Authorization\":\"Bearer abc\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertTrue(result.contains("\"***MASKED***\""));
        }

        @Test
        @DisplayName("does not mask non-matching field")
        void nonMatchingField() {
            String json = "{\"username\":\"john\"}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals(json, result);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  mask() — real-world weather API response (regression test)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("masks numeric fields in Open-Meteo weather API response")
    void weatherApiResponse() {
        String json = "{\"latitude\":17.375,\"longitude\":78.5,"
                + "\"generationtime_ms\":0.018,\"utc_offset_seconds\":0,"
                + "\"timezone\":\"GMT\",\"elevation\":498.0,"
                + "\"current\":{\"temperature_2m\":33.5}}";

        List<String> sensitiveFields = Arrays.asList("latitude", "longitude", "elevation");
        String result = SecretMaskingUtil.mask(json, sensitiveFields, REPLACEMENT);

        assertTrue(result.contains("\"latitude\":\"***MASKED***\""),
                "latitude numeric should be masked");
        assertTrue(result.contains("\"longitude\":\"***MASKED***\""),
                "longitude numeric should be masked");
        assertTrue(result.contains("\"elevation\":\"***MASKED***\""),
                "elevation numeric should be masked");
        // non-masked fields should remain
        assertTrue(result.contains("\"timezone\":\"GMT\""),
                "timezone string should not be masked");
        assertTrue(result.contains("\"utc_offset_seconds\":0"),
                "utc_offset_seconds should not be masked (not in field list)");
    }

    // ══════════════════════════════════════════════════════════════════
    //  mask() — edge / guard cases
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("mask() — guard conditions")
    class GuardCases {

        @Test
        @DisplayName("returns null when json is null")
        void nullJson() {
            assertNull(SecretMaskingUtil.mask(null, FIELDS, REPLACEMENT));
        }

        @Test
        @DisplayName("returns empty string unchanged")
        void emptyJson() {
            assertEquals("", SecretMaskingUtil.mask("", FIELDS, REPLACEMENT));
        }

        @Test
        @DisplayName("returns input unchanged when fields list is null")
        void nullFields() {
            String json = "{\"password\":\"secret\"}";
            assertEquals(json, SecretMaskingUtil.mask(json, null, REPLACEMENT));
        }

        @Test
        @DisplayName("returns input unchanged when fields list is empty")
        void emptyFields() {
            String json = "{\"password\":\"secret\"}";
            assertEquals(json,
                    SecretMaskingUtil.mask(json, Collections.emptyList(), REPLACEMENT));
        }

        @Test
        @DisplayName("does not alter fields not in the mask list")
        void unmatchedFieldsUnchanged() {
            String json = "{\"name\":\"Alice\",\"age\":30,\"active\":true}";
            String result = SecretMaskingUtil.mask(json, FIELDS, REPLACEMENT);
            assertEquals(json, result);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  shouldMaskHeader()
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("shouldMaskHeader()")
    class ShouldMaskHeader {

        @Test
        @DisplayName("returns true for exact match")
        void exactMatch() {
            assertTrue(SecretMaskingUtil.shouldMaskHeader("authorization", FIELDS));
        }

        @Test
        @DisplayName("returns true for case-insensitive match")
        void caseInsensitiveMatch() {
            assertTrue(SecretMaskingUtil.shouldMaskHeader("Authorization", FIELDS));
            assertTrue(SecretMaskingUtil.shouldMaskHeader("AUTHORIZATION", FIELDS));
            assertTrue(SecretMaskingUtil.shouldMaskHeader("PASSWORD", FIELDS));
        }

        @Test
        @DisplayName("returns false for non-matching header")
        void nonMatchingHeader() {
            assertFalse(SecretMaskingUtil.shouldMaskHeader("content-type", FIELDS));
            assertFalse(SecretMaskingUtil.shouldMaskHeader("accept", FIELDS));
        }

        @Test
        @DisplayName("returns false for null headerName")
        void nullHeaderName() {
            assertFalse(SecretMaskingUtil.shouldMaskHeader(null, FIELDS));
        }

        @Test
        @DisplayName("returns false for null fields list")
        void nullFieldsList() {
            assertFalse(SecretMaskingUtil.shouldMaskHeader("authorization", null));
        }
    }
}
