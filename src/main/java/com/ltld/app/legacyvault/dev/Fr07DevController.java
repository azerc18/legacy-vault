package com.ltld.app.legacyvault.dev;

import com.ltld.app.legacyvault.dev.Fr07DevDtos.*;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/** CHỈ Ở PROFILE dev: console test trực quan cho FR-07. Không tồn tại ở các profile khác. */
@RestController
@Profile("dev")
@RequiredArgsConstructor
public class Fr07DevController {

    private final Fr07DevService devService;

    @GetMapping(value = "/dev/fr07", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> console() {
        return ResponseEntity.ok(new ClassPathResource("dev-ui/fr07-console.html"));
    }

    @PostMapping("/api/dev/fr07/seed")
    public ResponseEntity<ApiResponse<SeedResponse>> seed(@RequestBody(required = false) SeedRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Seeded", devService.seed(request)));
    }

    @PostMapping("/api/dev/fr07/scan")
    public ResponseEntity<ApiResponse<Void>> scan(@RequestBody(required = false) ScanRequest request) {
        LocalDateTime now = request != null && request.now() != null ? request.now() : LocalDateTime.now();
        devService.scan(now);
        return ResponseEntity.ok(ApiResponse.success("Scan executed at " + now));
    }

    @PostMapping("/api/dev/fr07/mail-failure")
    public ResponseEntity<ApiResponse<Void>> mailFailure(@RequestBody MailFailureRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        devService.setMailFailure(enabled);
        return ResponseEntity.ok(ApiResponse.success("Mail failure simulation " + (enabled ? "ON" : "OFF")));
    }

    @GetMapping("/api/dev/fr07/state/{ownerId}")
    public ResponseEntity<ApiResponse<StateResponse>> state(@PathVariable UUID ownerId) {
        return ResponseEntity.ok(ApiResponse.success(devService.state(ownerId)));
    }
}
