package com.ltld.app.legacyvault.service.userservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cho biết nên gửi email bằng ngôn ngữ nào. Dùng ở các chỗ gửi mail chạy nền (không có request nên không có
 * Accept-Language): người dùng có tài khoản thì lấy theo hồ sơ, người chưa có tài khoản thì dùng mặc định.
 */
@Component
@RequiredArgsConstructor
public class UserLanguageResolver {

    private final UserRepository userRepository;

    public AppLanguage forUser(User user) {
        return user == null || user.getPreferredLanguage() == null ? AppLanguage.DEFAULT : user.getPreferredLanguage();
    }

    @Transactional(readOnly = true)
    public AppLanguage forEmail(String email) {
        if (email == null || email.isBlank()) {
            return AppLanguage.DEFAULT;
        }
        return userRepository.findByEmail(email.trim())
                .map(this::forUser)
                .orElse(AppLanguage.DEFAULT);
    }
}
