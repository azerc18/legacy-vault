package com.ltld.app.legacyvault.service.paymentservice;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseNumberWordsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|Không đồng chẵn.",
            "5000|Năm nghìn đồng chẵn.",
            "15000|Mười lăm nghìn đồng chẵn.",
            "21000|Hai mươi mốt nghìn đồng chẵn.",
            "99000|Chín mươi chín nghìn đồng chẵn.",
            "990000|Chín trăm chín mươi nghìn đồng chẵn.",
            "1005000|Một triệu không trăm linh năm nghìn đồng chẵn.",
            "1000000|Một triệu đồng chẵn.",
            "2000000000|Hai tỷ đồng chẵn.",
            "1234567|Một triệu hai trăm ba mươi tư nghìn năm trăm sáu mươi bảy đồng chẵn.",
            "104|Một trăm linh bốn đồng chẵn."})
    void vndInWords(long amount, String expected) {
        assertThat(VietnameseNumberWords.vndInWords(amount)).isEqualTo(expected);
    }
}
