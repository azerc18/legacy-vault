package com.ltld.app.legacyvault.dto.registerdto;

import com.ltld.app.legacyvault.utility.PasswordPolicy;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email(message = "This is not a valid email format.")
    @NotBlank(message = "This field is required")
    private String email;

    @NotBlank(message = "This field is required")
    @Size(min = 2, message = "Fullname must be longer than 2 characters." )
    private String fullName;

    @NotBlank(message = "This field is required")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String password;

    @NotBlank(message = "This field is required.")
    private String passwordConfirm;

    @AssertTrue(message = "Password is not matching.")
    public boolean isPasswordMatching() {
        if(password == null || passwordConfirm == null) return true;
        return passwordConfirm.equals(password);
    }
}
