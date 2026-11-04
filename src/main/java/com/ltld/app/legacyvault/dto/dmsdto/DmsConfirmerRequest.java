package com.ltld.app.legacyvault.dto.dmsdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsConfirmerRequest {

    @NotBlank(message = "Confirmer name is required.")
    @Size(max = 120, message = "Confirmer name must not exceed 120 characters.")
    private String name;

    @NotBlank(message = "Confirmer email is required.")
    @Email(message = "Confirmer email is invalid.")
    @Size(max = 255, message = "Confirmer email must not exceed 255 characters.")
    private String email;

    @Size(max = 80, message = "Relationship must not exceed 80 characters.")
    private String relationship;
}

