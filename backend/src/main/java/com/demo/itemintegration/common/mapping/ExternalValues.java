package com.demo.itemintegration.common.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Normalisation rules for raw hosted-server values, shared by every domain mapper so
 * all entities treat empty and inconsistent values the same way:
 * <ul>
 *   <li>text: null, missing, or blank becomes {@code null}; values are trimmed</li>
 *   <li>numbers / booleans: accepted as JSON values or strings; unparseable becomes {@code null}</li>
 *   <li>lists: null, missing, {@code ""}, {@code []} or {@code "[]"} become an empty list;
 *       a single value becomes a one-element list</li>
 *   <li>enum keys: upper-cased, with {@code _}, {@code -} and whitespace collapsed to one space</li>
 * </ul>
 * None of these methods throw for bad values.
 */
public final class ExternalValues {

    private ExternalValues() {
    }

    public static String text(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (node.isArray()) {
            List<String> parts = list(node, ExternalValues::scalarText);
            return parts.isEmpty() ? null : String.join(", ", parts);
        }
        return scalarText(node);
    }

    public static Long toLong(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return node.longValue();
        }
        String text = scalarText(node);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer toInteger(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (node.isNumber()) {
            return node.canConvertToInt() ? node.intValue() : null;
        }
        String text = scalarText(node);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(text);
                return d == Math.rint(d) ? (int) d : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /** Decimal number (e.g. a coordinate) from a JSON number or numeric string; non-finite becomes {@code null}. */
    public static Double toDecimal(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        String text = scalarText(node);
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Boolean toBoolean(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        String text = scalarText(node);
        if (text == null) {
            return null;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "1" -> Boolean.TRUE;
            case "false", "f", "no", "n", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Upper-cased key for matching enum-like values ("not_assessed", "Not-Assessed" and
     * "NOT ASSESSED" all become {@code "NOT ASSESSED"}); {@code null} when blank.
     */
    public static String enumKey(JsonNode node) {
        String value = text(node);
        return value == null ? null : value.toUpperCase(Locale.ROOT).replaceAll("[_\\-\\s]+", " ").trim();
    }

    /**
     * Normalises a list-like value: a JSON array, an array-like string such as
     * {@code "[]"} / {@code "['A', 'B']"}, a comma-separated string, or a single scalar.
     * Elements the converter cannot handle are dropped.
     */
    public static <T> List<T> list(JsonNode node, Function<JsonNode, T> elementConverter) {
        List<T> result = new ArrayList<>();
        if (isAbsent(node)) {
            return result;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                addIfPresent(result, elementConverter.apply(element));
            }
            return result;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1);
            }
            for (String part : text.split(",")) {
                String cleaned = stripQuotes(part.trim());
                if (!cleaned.isEmpty()) {
                    addIfPresent(result, elementConverter.apply(TextNode.valueOf(cleaned)));
                }
            }
            return result;
        }
        addIfPresent(result, elementConverter.apply(node));
        return result;
    }

    /** Element converter for {@link #list}: trimmed text of a scalar, or {@code null}. */
    public static String scalarText(JsonNode node) {
        if (isAbsent(node) || node.isContainerNode()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static <T> void addIfPresent(List<T> list, T value) {
        if (value != null) {
            list.add(value);
        }
    }
}
