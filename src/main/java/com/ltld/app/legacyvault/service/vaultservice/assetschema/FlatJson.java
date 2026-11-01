package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * N3: mã hóa/giải mã JSON phẳng {"key":"value"} (chỉ chuỗi). Tự viết để không phụ thuộc phiên bản Jackson.
 */
public final class FlatJson {

    private FlatJson() {
    }

    public static String encode(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            quote(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    public static Map<String, String> decode(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        int[] i = {0};
        skipWs(json, i);
        expect(json, i, '{');
        skipWs(json, i);
        if (peek(json, i) == '}') {
            return out;
        }
        while (true) {
            skipWs(json, i);
            String key = readString(json, i);
            skipWs(json, i);
            expect(json, i, ':');
            skipWs(json, i);
            out.put(key, readString(json, i));
            skipWs(json, i);
            char c = json.charAt(i[0]++);
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Invalid JSON at " + (i[0] - 1));
            }
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static String readString(String s, int[] i) {
        expect(s, i, '"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i[0] >= s.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            char c = s.charAt(i[0]++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char n = s.charAt(i[0]++);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16));
                    i[0] += 4;
                }
                default -> sb.append(n);
            }
        }
    }

    private static void skipWs(String s, int[] i) {
        while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) {
            i[0]++;
        }
    }

    private static char peek(String s, int[] i) {
        return s.charAt(i[0]);
    }

    private static void expect(String s, int[] i, char c) {
        if (i[0] >= s.length() || s.charAt(i[0]) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at " + i[0]);
        }
        i[0]++;
    }
}
