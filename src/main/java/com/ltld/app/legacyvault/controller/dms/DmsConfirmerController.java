package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * X2: Quản lý người xác nhận chéo DMS của Owner và endpoint công khai (permitAll) để Confirmer xác nhận từ email.
 */
@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmsConfirmerController {

    private final DmsConfirmerService dmsConfirmerService;

    @GetMapping("/confirmers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<DmsConfirmerResponse>>> listConfirmers(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Trusted confirmers retrieved successfully",
                dmsConfirmerService.listConfirmers(ownerId)
        ));
    }

    @PostMapping("/confirmers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<DmsConfirmerResponse>> addConfirmer(
            Principal principal,
            @Valid @RequestBody DmsConfirmerRequest request
    ) {
        UUID ownerId = UUID.fromString(principal.getName());
        DmsConfirmerResponse response = dmsConfirmerService.addConfirmer(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trusted confirmer added successfully", response));
    }

    @DeleteMapping("/confirmers/{confirmerId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> removeConfirmer(
            Principal principal,
            @PathVariable UUID confirmerId
    ) {
        UUID ownerId = UUID.fromString(principal.getName());
        dmsConfirmerService.removeConfirmer(ownerId, confirmerId);
        return ResponseEntity.ok(ApiResponse.success("Trusted confirmer removed successfully", null));
    }

    /**
     * Endpoint công khai (permitAll): Confirmer bấm nút "Owner is safe" (decision=ALIVE)
     * hoặc "Unreachable / Confirmed" (decision=UNREACHABLE) trong email khẩn cấp.
     */
    @GetMapping("/confirm")
    public ResponseEntity<Void> confirmFromEmail(
            @RequestParam("token") String token,
            @RequestParam(value = "decision", required = false) String decision
    ) {
        String redirectUrl = dmsConfirmerService.respondAndBuildRedirectUrl(token, decision);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}

