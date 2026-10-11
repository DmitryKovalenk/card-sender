package com.privatbank.cardsender.logging;

import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;

final class HttpLogFormatter {
    static final int MAX_BODY_BYTES = 8192;
    private static final String MASKED = "[MASKED]";
    private static final Pattern LONG_DIGITS = Pattern.compile("[0-9]{12,}");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "cardnumber",
            "apikey",
            "password",
            "token",
            "authorization"
    );
    private final ObjectMapper mapper = new ObjectMapper();

    String body(byte[] body, String contentType) {
        if (body.length == 0) {
            return "[empty or not consumed]";
        }
        if (body.length >= MAX_BODY_BYTES) {
            return "[body omitted: size limit]";
        }
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return "[non-JSON body omitted]";
        }
        try {
            Object value = mapper.readValue(body, Object.class);
            return sanitize(mapper.writeValueAsString(mask(value)));
        } catch (RuntimeException exception) {
            return "[unreadable JSON omitted]";
        }
    }

    String path(String value) {
        return sanitize(LONG_DIGITS.matcher(value).replaceAll(match -> match.group().length() == 16
                        ? maskCardNumber(match.group()) : "***"));
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_");
    }

    private String maskCardNumber(Object value) {
        String number = String.valueOf(value);
        return number.matches("[0-9]{16}")
                ? number.substring(0, 4) + "********" + number.substring(12)
                : MASKED;
    }

    private Object mask(Object value) {
        if (value instanceof Map<?, ?> fields) {
            var result = new LinkedHashMap<String, Object>();
            fields.forEach((key, field) -> {
                String name = key.toString();
                String normalized = name
                        .replace("_", "")
                        .replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if ("cardnumber".equals(normalized)) {
                    result.put(path(name), maskCardNumber(field));
                } else if ("phone".equals(normalized) && field instanceof String phone) {
                    result.put(path(name), sanitize(phone));
                } else if ("waybill".equals(normalized) && field instanceof String number
                        && number.matches("[0-9]{1,30}")) {
                    result.put(path(name), number);
                } else {
                    result.put(path(name), SENSITIVE_FIELDS.contains(normalized) ? MASKED : mask(field));
                }
            });
            return result;
        }
        if (value instanceof List<?> values) {
            return values.stream().map(this::mask).toList();
        }
        if (value instanceof String text) {
            return path(text);
        }
        if (value instanceof Number number && LONG_DIGITS.matcher(number.toString()).find()) {
            return path(number.toString());
        }
        return value;
    }
}
