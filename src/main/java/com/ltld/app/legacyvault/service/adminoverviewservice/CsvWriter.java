package com.ltld.app.legacyvault.service.adminoverviewservice;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * B4: dựng file CSV (UTF-8 có BOM để Excel đọc đúng tiếng Việt).
 * Ô bắt đầu bằng = + - @ được thêm dấu ' để Excel không chạy như công thức (CSV injection).
 */
public final class CsvWriter {

    private final StringBuilder sb = new StringBuilder("﻿");

    public CsvWriter row(Object... cells) {
        return row(List.of(cells));
    }

    public CsvWriter row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    public byte[] toBytes() {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
