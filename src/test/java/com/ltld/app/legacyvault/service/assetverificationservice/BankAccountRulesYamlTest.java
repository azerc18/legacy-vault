package com.ltld.app.legacyvault.service.assetverificationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bảng độ dài số tài khoản thật trong application.yaml: bắt lỗi gõ nhầm (khóa chưa chuẩn hóa, độ dài rỗng/âm,
 * trùng) và khóa chặt các ngân hàng đã có nguồn chính thức. Không cần Docker hay Spring context.
 */
class BankAccountRulesYamlTest {

    private static BankAccountRules loadFromApplicationYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        StandardEnvironment env = new StandardEnvironment();
        sources.forEach(env.getPropertySources()::addLast);
        // ${...} trong yaml (jwt, mail...) không liên quan tới bảng này nên không cần resolve
        BankAccountRules rules = new BankAccountRules();
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        binder.bind("app.verification.bank", org.springframework.boot.context.properties.bind.Bindable.ofInstance(rules));
        return rules;
    }

    @Test
    @DisplayName("Bảng đã có ít nhất một ngân hàng (cách BANK_FORMAT_CHECK được hiện)")
    void tableIsFilled() throws IOException {
        assertThat(loadFromApplicationYaml().hasAnyRule()).isTrue();
    }

    @Test
    @DisplayName("Mọi khóa đã chuẩn hóa, mọi độ dài dương, tăng dần và không trùng")
    void everyEntryIsWellFormed() throws IOException {
        BankAccountRules rules = loadFromApplicationYaml();
        assertThat(rules.getAccountLengths()).isNotEmpty();
        rules.getAccountLengths().forEach((key, lengths) -> {
            assertThat(BankAccountRules.keyOf(key)).as("khóa %s phải là chữ thường không dấu không khoảng trắng", key)
                    .isEqualTo(key);
            assertThat(lengths).as("độ dài của %s", key).isNotEmpty().doesNotHaveDuplicates()
                    .allSatisfy(n -> assertThat(n).isBetween(1, 30)).isSorted();
        });
    }

    @Test
    @DisplayName("BIDV: 3-8 và 10 chữ số theo thông báo của BIDV, không còn 14")
    void bidvFollowsItsOwnNotice() throws IOException {
        Optional<List<Integer>> bidv = loadFromApplicationYaml().allowedLengths("BIDV");
        assertThat(bidv).hasValue(List.of(3, 4, 5, 6, 7, 8, 10));
        assertThat(bidv.get()).doesNotContain(14);
    }

    @Test
    @DisplayName("Tên gọi tắt/viết khác vẫn khớp: Techcombank, TCB, MB Bank, MB")
    void aliasesResolve() throws IOException {
        BankAccountRules rules = loadFromApplicationYaml();
        assertThat(rules.allowedLengths("Techcombank")).isPresent();
        assertThat(rules.allowedLengths("TCB")).isEqualTo(rules.allowedLengths("Techcombank"));
        assertThat(rules.allowedLengths("MB Bank")).hasValue(List.of(9, 10, 11, 12, 13));
        assertThat(rules.allowedLengths("MB")).isEqualTo(rules.allowedLengths("MB Bank"));
        assertThat(rules.allowedLengths("Techcombank").get()).contains(14).doesNotContain(5, 15);
    }

    @Test
    @DisplayName("Ngân hàng chưa có nguồn chính thức thì không có luật")
    void unconfirmedBanksHaveNoRule() throws IOException {
        BankAccountRules rules = loadFromApplicationYaml();
        assertThat(rules.allowedLengths("Vietcombank")).isEmpty();
        assertThat(rules.allowedLengths("Agribank")).isEmpty();
        assertThat(rules.allowedLengths("")).isEmpty();
    }
}
