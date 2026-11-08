package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPlanResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.UpdatePlanPriceRequest;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminPlanService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** C1: Quản lý gói — chỉ đổi tên/giá cho lần mua sau, không đụng gói đang chạy. */
@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminPlanController {

    private final AdminPlanService adminPlanService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminPlanResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(adminPlanService.list()));
    }

    @PatchMapping("/{code}")
    public ResponseEntity<ApiResponse<AdminPlanResponse>> updatePrice(Principal principal, @PathVariable String code,
                                                                      @Valid @RequestBody UpdatePlanPriceRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Plan updated",
                adminPlanService.updatePrice(adminId, code, request)));
    }
}
