package com.ltld.app.legacyvault.beneficiary.service;

import com.ltld.app.legacyvault.beneficiary.dto.AssetViewResponse;
import com.ltld.app.legacyvault.beneficiary.dto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.beneficiary.dto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.beneficiary.dto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.beneficiary.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.beneficiary.entity.IdentityVerification;
import com.ltld.app.legacyvault.beneficiary.enums.ClaimStatus;
import com.ltld.app.legacyvault.beneficiary.enums.VerificationStatus;
import com.ltld.app.legacyvault.beneficiary.exception.BeneficiaryException;
import com.ltld.app.legacyvault.beneficiary.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.beneficiary.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// @Service: Đánh dấu class này là một Service Component trong Spring Boot.
// Spring sẽ tự động tạo một instance (singleton) của class này và đưa vào bộ nhớ để quản lý.
@Service
// @RequiredArgsConstructor: Tự động tạo constructor (hàm khởi tạo) cho tất cả các biến 'final'.
// Đây là cách chuẩn để inject (tiêm) các Repository vào Service thay vì dùng @Autowired.
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    // Tiêm các Repository để giao tiếp với Database. Các biến này bắt buộc phải có chữ 'final'.
    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;

    /**
     * Hàm xử lý yêu cầu nhận tài sản từ Beneficiary (Người thụ hưởng).
     *
     * @Transactional: Rất quan trọng! Annotation này tạo ra một "Giao dịch" (Transaction).
     * - Nếu tất cả code chạy trơn tru từ trên xuống dưới -> Commit (chốt lưu toàn bộ xuống DB).
     * - Nếu có BẤT KỲ Exception nào văng ra giữa chừng -> Rollback (Hoàn tác mọi thao tác lưu,
     *   tránh tình trạng dữ liệu bị rác hoặc lưu được một nửa gây lỗi hệ thống).
     */
    @Override
    @Transactional
    public BeneficiaryClaimResponse processClaim(BeneficiaryClaimRequest request) {

        // ========================================================================
        // BƯỚC 1: TÌM KIẾM VÀ KIỂM TRA SỰ TỒN TẠI CỦA YÊU CẦU
        // Mục đích: Xác minh xem Vault ID mà Frontend gửi lên có thực sự nằm trong
        // bảng beneficiary_claims của Database hay không.
        // ========================================================================

        // Hàm findByVaultId trả về một Optional. Nó giống như một cái hộp:
        // có thể chứa dữ liệu (nếu tìm thấy) hoặc rỗng (nếu không tìm thấy).
        Optional<BeneficiaryClaim> optionalClaim = claimRepository.findByVaultId(request.getVaultId());

        // Kiểm tra xem hộp có rỗng không
        if (optionalClaim.isEmpty()) {
            // Nếu rỗng, ném ra lỗi. Chữ chạy (execution) sẽ dừng ngay tại dòng này,
            // nhảy thẳng ra ngoài Controller và báo lỗi cho Frontend, không chạy các bước bên dưới.
            throw new BeneficiaryException("Không tìm thấy yêu cầu nhận tài sản cho Vault ID này.");
        }

        // Nếu qua được ngã rẽ trên, ta mở hộp lấy đối tượng BeneficiaryClaim thực sự ra để dùng
        BeneficiaryClaim claim = optionalClaim.get();


        // ========================================================================
        // BƯỚC 2: KIỂM TRA ĐIỀU KIỆN NGHIỆP VỤ (BUSINESS RULES VALIDATION)
        // Mục đích: Chặn các yêu cầu sai logic (Ví dụ: đã nhận tài sản rồi thì không được nhận lại)
        // ========================================================================

        // Rule 1: Nếu trạng thái hiện tại là CLAIMED (đã nhận), chặn lại.
        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new BeneficiaryException("Tài sản này đã được nhận, không thể yêu cầu lại.");
        }

        // Rule 2: Kiểm tra thời hạn.
        // - claim.getStatus() == ClaimStatus.EXPIRED: Trạng thái trong DB đã bị set là hết hạn
        // - LocalDateTime.now().isAfter(...): Hoặc thời gian của hệ thống hiện tại đã vượt qua
        //   mốc thời gian hạn chót (claimDeadlineAt) được lưu.
        if (claim.getStatus() == ClaimStatus.EXPIRED || LocalDateTime.now().isAfter(claim.getClaimDeadlineAt())) {
            throw new BeneficiaryException("Thời hạn yêu cầu nhận tài sản đã kết thúc.");
        }


        // ========================================================================
        // BƯỚC 3: KHỞI TẠO PHIÊN XÁC THỰC DANH TÍNH (LƯU VÀO BẢNG IDENTITY_VERIFICATIONS)
        // Mục đích: Mở một phiên xác thực mới dựa trên lựa chọn (OTP hoặc eKYC) từ người dùng.
        // ========================================================================

        // Dùng Design Pattern 'Builder' của thư viện Lombok để tạo object gọn gàng, dễ đọc
        IdentityVerification verification = IdentityVerification.builder()
                // ID người thụ hưởng và Vault ID được lấy từ bản ghi claim hợp lệ ở Bước 1
                .beneficiaryId(claim.getBeneficiaryId())
                .vaultId(claim.getVaultId())
                // Lấy động phương thức xác thực do Frontend truyền lên (OTP hoặc EKYC_MOCK)
                .method(request.getVerificationMethod())
                // Set trạng thái ban đầu của phiên xác thực là PENDING (chờ xử lý)
                .status(VerificationStatus.PENDING)
                // Lưu ý: Các trường như 'attemptCount' (lần thử) hay 'createdAt' (ngày tạo)
                // không cần gán ở đây vì ta đã dùng @Builder.Default ở class Entity
                // để tự động gán giá trị mặc định là 0 và ngày hiện tại rồi.
                .build();

        // Ghi đối tượng verification mới tạo vào Database bằng lệnh save().
        verificationRepository.save(verification);


        // ========================================================================
        // BƯỚC 4: ĐÓNG GÓI DỮ LIỆU VÀ TRẢ VỀ CHO FRONTEND (MAPPING ENTITY -> DTO)
        // Mục đích: Không ném thẳng class Entity (chứa cấu trúc Database thật) ra ngoài mạng,
        // mà chỉ nhặt những trường cần thiết bỏ vào class Response (DTO) để bảo mật.
        // ========================================================================

        return BeneficiaryClaimResponse.builder()
                .claimId(claim.getId())
                .vaultId(claim.getVaultId())
                .status(claim.getStatus())
                .claimDeadlineAt(claim.getClaimDeadlineAt())
                .claimedAt(claim.getClaimedAt())
                .build();
    }

    // ========================================================================
    // FR-17 & FR-18: XÁC THỰC OTP/KYC VÀ XEM/TẢI TÀI SẢN
    // ========================================================================
    @Override
    @Transactional
    public AssetViewResponse verifyIdentityAndViewAsset(VerifyIdentityRequest request) {

        // 1. Tìm phiên xác thực đang PENDING của Vault này
        // (Trong thực tế, bạn có thể cần viết thêm hàm findByVaultIdAndStatus trong IdentityVerificationRepository)
        IdentityVerification verification = verificationRepository.findAll().stream()
                .filter(v -> v.getVaultId().equals(request.getVaultId()) && v.getStatus() == VerificationStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new BeneficiaryException("Không tìm thấy phiên xác thực hợp lệ hoặc phiên đã hết hạn."));

        // 2. Mock Logic Xác thực (Trọng tâm kỹ thuật)
        // Mặc định cho phép mã "123456" là mã OTP/KYC đúng để pass qua bước này
        if (!"123456".equals(request.getAuthCode())) {
            verification.setAttemptCount(verification.getAttemptCount() + 1);
            verificationRepository.save(verification);
            throw new BeneficiaryException("Mã xác thực không chính xác. Số lần thử: " + verification.getAttemptCount());
        }

        // 3. Đánh dấu xác thực thành công
        verification.setStatus(VerificationStatus.SUCCESS);
        verification.setVerifiedAt(LocalDateTime.now());
        verificationRepository.save(verification);

        // 4. Mock Logic Giải mã (Trả về cho FR-17 và link cho FR-18)
        // Trong hệ thống thực, đây là nơi bạn gọi thuật toán AES/RSA để giải mã file
        return AssetViewResponse.builder()
                .vaultId(request.getVaultId())
                .decryptedContent("ĐÂY LÀ NỘI DUNG TÀI SẢN ĐÃ ĐƯỢC GIẢI MÃ TẠM THỜI (Mock Data)")
                .downloadUrl("/api/v1/beneficiaries/download/" + request.getVaultId()) // Link để Frontend gọi API tải file (FR-18)
                .build();
    }

    // ========================================================================
    // FR-19: XÁC NHẬN ĐÓNG HỒ SƠ (BONUS)
    // ========================================================================
    @Override
    @Transactional
    public void confirmClaimClosing(UUID vaultId) {

        BeneficiaryClaim claim = claimRepository.findByVaultId(vaultId)
                .orElseThrow(() -> new BeneficiaryException("Không tìm thấy hồ sơ để đóng."));

        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new BeneficiaryException("Hồ sơ này đã được đóng trước đó.");
        }

        // Cập nhật trạng thái thành CLAIMED và ghi nhận thời gian
        claim.setStatus(ClaimStatus.CLAIMED);
        claim.setClaimedAt(LocalDateTime.now());

        claimRepository.save(claim);
    }
}