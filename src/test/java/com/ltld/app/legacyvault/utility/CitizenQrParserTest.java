package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.VnProvince;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitizenQrParserTest {

    @Test
    void parse_readsAllFields() {
        String qr = "079201123456|025123456|NGUYỄN VĂN AN|02052001|Nam|"
                + "123 Lê Lợi, Phường Bến Thành, Quận 1, Thành phố Hồ Chí Minh|15072021";

        CitizenQrParser.Result r = CitizenQrParser.parse(qr);

        assertThat(r.citizenId()).isEqualTo("079201123456");
        assertThat(r.oldIdNumber()).isEqualTo("025123456");
        assertThat(r.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(r.dateOfBirth()).isEqualTo(LocalDate.of(2001, 5, 2));
        assertThat(r.gender()).isEqualTo(CitizenId.Gender.MALE);
        assertThat(r.issueDate()).isEqualTo(LocalDate.of(2021, 7, 15));
        assertThat(VnProvince.fromAddress(r.address())).contains(VnProvince.HO_CHI_MINH);
    }

    @Test
    void parse_emptyOldIdAndFemale() {
        CitizenQrParser.Result r = CitizenQrParser.parse("001185000001||Trần Thị Bình|01011985|Nữ|Hà Nội|01012022");
        assertThat(r.oldIdNumber()).isNull();
        assertThat(r.gender()).isEqualTo(CitizenId.Gender.FEMALE);
    }

    @Test
    void parse_rejectsNonCitizenQr() {
        assertThatThrownBy(() -> CitizenQrParser.parse("https://example.com/promo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CitizenQrParser.parse("abc|def|g|h|i|j|k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CitizenQrParser.parse("079201123456||Name|99999999|Nam|x|01012022"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
