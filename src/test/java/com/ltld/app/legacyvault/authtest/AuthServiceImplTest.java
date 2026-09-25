package com.ltld.app.legacyvault.authtest;

import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.exception.EmailAlreadyExistsException;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.authservice.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role ownerRole;

    @BeforeEach
    void setUp() {
        ownerRole = Role.builder().code("OWNER").build();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Nguyen Van A");
        request.setPassword("Password123");
        request.setPasswordConfirm("Password123");
        return request;
    }

    @Test
    void register_success_savesUserWithEncodedPasswordAndDefaultRole() {
        RegisterRequest request = validRegisterRequest();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(request.getEmail());
        assertThat(savedUser.getFullName()).isEqualTo(request.getFullName());
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRoles()).containsExactly(ownerRole);

        verify(passwordEncoder, times(1)).encode(request.getPassword());
    }

    @Test
    void register_emailAlreadyExists_throwsEmailAlreadyExistsException_andDoesNotSave() {
        RegisterRequest request = validRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists.");

        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByCode(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void register_defaultRoleMissing_throwsRuntimeException_andDoesNotSave() {
        RegisterRequest request = validRegisterRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Default role not found in database.");

        verify(userRepository, never()).save(any());
    }


}
