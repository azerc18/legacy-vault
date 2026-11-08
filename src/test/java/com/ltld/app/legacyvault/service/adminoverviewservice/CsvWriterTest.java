package com.ltld.app.legacyvault.service.adminoverviewservice;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void escape_quotesCommasAndNewlines() {
        assertThat(CsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.escape(null)).isEmpty();
    }

    @Test
    void escape_neutralisesFormulaCells() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(CsvWriter.escape("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.escape("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    void toBytes_startsWithBomAndUsesCrlf() {
        String s = new String(new CsvWriter().row("a", 1).row("Nguyễn", null).toBytes(), StandardCharsets.UTF_8);
        assertThat(s).isEqualTo("﻿a,1\r\nNguyễn,\r\n");
    }
}
