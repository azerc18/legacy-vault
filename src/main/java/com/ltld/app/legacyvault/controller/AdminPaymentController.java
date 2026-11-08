package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentFilter;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentSummaryResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminPaymentService;
import com.ltld.app.legacyvault.service.adminoverviewservice.CsvWriter;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A2: Admin xem giao dịch thanh toán. Controller riêng, CHỈ ĐỌC — không đụng luồng thanh toán VNPay/PayPal.
 */
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminPaymentController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminPaymentService adminPaymentService;
    private final PaymentReceiptService paymentReceiptService;
    private final PaymentReceiptPdfService paymentReceiptPdfService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminPaymentResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPaymentService.search(new AdminPaymentFilter(status, email, from, to), page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminPaymentSummaryResponse>> summary(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPaymentService.summary(new AdminPaymentFilter(status, email, from, to))));
    }

    // B4: xuất CSV theo đúng bộ lọc đang chọn
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<AdminPaymentResponse> rows = adminPaymentService.export(new AdminPaymentFilter(status, email, from, to));
        CsvWriter csv = new CsvWriter().row("id", "created_at", "paid_at", "status", "provider", "user_email",
                "user_name", "plan", "amount_vnd", "provider_txn_no", "bank_code", "current_plan");
        for (AdminPaymentResponse r : rows) {
            csv.row(r.getId(), r.getCreatedAt(), r.getPaidAt(), r.getStatus(), r.getProvider(), r.getUserEmail(),
                    r.getUserFullName(), r.getPlanCode(), r.getAmountVnd(), r.getProviderTransactionNo(),
                    r.getBankCode(), r.getCurrentPlanCode());
        }
        return csvResponse("payments-" + LocalDate.now() + ".csv", csv.toBytes());
    }

    // Tải lại biên lai PDF (dùng chung service của #191), chỉ giao dịch SUCCESS
    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID transactionId) {
        PaymentReceiptData data = paymentReceiptService.getReceiptDataForAdmin(transactionId);
        byte[] pdf = paymentReceiptPdfService.render(data);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("bien-lai-" + data.receiptNo() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    static ResponseEntity<byte[]> csvResponse(String fileName, byte[] body) {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
