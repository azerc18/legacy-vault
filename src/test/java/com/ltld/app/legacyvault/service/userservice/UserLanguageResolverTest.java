package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLanguageResolverTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserLanguageResolver resolver;

    @Test
    void forUser_usesProfileLanguage() {
        User u = User.builder().email("a@x.com").preferredLanguage(AppLanguage.EN).build();
        assertThat(resolver.forUser(u)).isEqualTo(AppLanguage.EN);
    }

    @Test
    void forUser_nullUser_isDefault() {
        assertThat(resolver.forUser(null)).isEqualTo(AppLanguage.DEFAULT);
    }

    @Test
    void forEmail_existingUser_usesProfileLanguage() {
        when(userRepository.findByEmail("a@x.com"))
                .thenReturn(Optional.of(User.builder().email("a@x.com").preferredLanguage(AppLanguage.EN).build()));
        assertThat(resolver.forEmail(" a@x.com ")).isEqualTo(AppLanguage.EN);
    }

    @Test
    void forEmail_unknownEmail_isDefault() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());
        assertThat(resolver.forEmail("nobody@x.com")).isEqualTo(AppLanguage.DEFAULT);
    }

    @Test
    void forEmail_blank_isDefaultWithoutLookup() {
        assertThat(resolver.forEmail("  ")).isEqualTo(AppLanguage.DEFAULT);
        assertThat(resolver.forEmail(null)).isEqualTo(AppLanguage.DEFAULT);
        verifyNoInteractions(userRepository);
    }
}
