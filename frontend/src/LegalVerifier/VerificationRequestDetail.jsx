import React, { useState } from 'react';
import { Link, useParams } from 'react-router-dom';

export default function VerificationRequestDetail() {
    // Lấy ID yêu cầu từ URL (ví dụ: /verifier/request/123)
    const { requestId } = useParams();

    // Giả lập dữ liệu chi tiết yêu cầu (sau này sẽ lấy từ API của bạn)
    const [requestData, setRequestData] = useState({
        id: requestId,
        status: 'PENDING_VERIFICATION', // Trạng thái hiện tại
        submittedAt: '2023-10-27T10:00:00Z',

        // Thông tin Vault Owner (Người đã mất)
        deceasedName: 'Trần Văn B',
        deathCertificateProvided: false, // Chưa tải lên

        // Thông tin Người thừa kế (Executor) gửi yêu cầu
        executorName: 'Nguyễn Thị C',
        executorEmail: 'nguyenthic@example.com',
        relationshipToDeceased: 'Con gái',

        // Danh sách tài sản trong Vault (để verifier nắm bắt quy mô)
        assetsCount: 3,
        vaultName: 'Di sản gia đình Trần',
    });

    const [rejectionReason, setRejectionReason] = useState('');
    const [isSigning, setIsSigning] = useState(false);
    const [verificationResult, setVerificationResult] = useState(null); // 'APPROVED' hoặc 'REJECTED'

    const handleApprove = () => {
        // TODO: Người số 5 sẽ tích hợp API gọi chữ ký số tại đây
        console.log('Approving request and initiating digital signature...');
        setIsSigning(true);

        // Giả lập ký số thành công sau 2 giây
        setTimeout(() => {
            setIsSigning(false);
            setVerificationResult('APPROVED');
            setRequestData({ ...requestData, status: 'APPROVED_AND_SIGNED' });
            alert('Hồ sơ đã được xác minh hợp lệ và ký điện tử thành công!');
        }, 2000);
    };

    const handleReject = () => {
        if (!rejectionReason) {
            alert('Vui lòng nhập lý do từ chối.');
            return;
        }
        // TODO: Người số 5 sẽ tích hợp API từ chối tại đây
        console.log('Rejecting request with reason:', rejectionReason);
        setVerificationResult('REJECTED');
        setRequestData({ ...requestData, status: 'REJECTED' });
        alert('Yêu cầu đã được từ chối thành công.');
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased">
            {/* Header riêng cho trang chi tiết */}
            <header className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-outline-variant/30">
                <div className="max-w-[1600px] mx-auto px-5 h-20 flex items-center justify-between gap-6">
                    <div className="flex items-center gap-3">
                        <Link to="/verifier" className="p-2 rounded-full hover:bg-surface-container-high text-on-surface-variant">
                            <span className="material-symbols-outlined">arrow_back</span>
                        </Link>
                        <div>
                            <h1 className="font-headline-sm text-primary font-bold tracking-tight">Xử lý Yêu cầu Xác minh #XYZ-{requestData.id}</h1>
                            <p className="font-body-sm text-on-surface-variant mt-0.5">Dành riêng cho vai trò Legal Verifier</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-3">
             <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${requestData.status === 'PENDING_VERIFICATION' ? 'bg-warning-container text-on-warning-container' : 'bg-success-container text-on-success-container'}`}>
                {requestData.status.replace('_', ' ')}
            </span>
                    </div>
                </div>
            </header>

            {/* Main Content - Chia 2 cột */}
            <main className="max-w-[1600px] mx-auto p-5 grid grid-cols-1 xl:grid-cols-3 gap-8 mt-8">

                {/* Cột trái: Thông tin chi tiết */}
                <div className="xl:col-span-2 space-y-8">
                    {/* Thông tin Người thừa kế (Executor) */}
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">person</span>
                            Thông tin Người gửi Yêu cầu (Executor)
                        </h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                            <div>
                                <label className="font-label-sm text-on-surface-variant">Họ và tên</label>
                                <p className="font-body-lg font-medium mt-1">{requestData.executorName}</p>
                            </div>
                            <div>
                                <label className="font-label-sm text-on-surface-variant">Email liên hệ</label>
                                <p className="font-body-lg font-medium mt-1">{requestData.executorEmail}</p>
                            </div>
                            <div>
                                <label className="font-label-sm text-on-surface-variant">Mối quan hệ với người đã mất</label>
                                <p className="font-body-lg font-medium mt-1">{requestData.relationshipToDeceased}</p>
                            </div>
                        </div>
                    </section>

                    {/* Thông tin Người đã mất (Vault Owner) */}
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">folder_shared</span>
                            Thông tin Người đã mất (Vault Owner & Tài sản)
                        </h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5 border-b border-outline-variant/20 pb-6 mb-6">
                            <div>
                                <label className="font-label-sm text-on-surface-variant">Họ và tên (trên giấy tờ)</label>
                                <p className="font-headline-sm font-bold mt-1 text-primary">{requestData.deceasedName}</p>
                            </div>
                        </div>
                        <div className="flex items-center gap-4 p-5 rounded-xl bg-surface-container-high border border-outline-variant/50">
                            <span className="material-symbols-outlined text-[40px] text-outline">inventory_2</span>
                            <div>
                                <p className="font-label-lg text-on-surface font-bold">Vault được yêu cầu mở:</p>
                                <p className="font-body-lg text-on-surface-variant">{requestData.vaultName} (Chứa {requestData.assetsCount} tài sản số)</p>
                            </div>
                        </div>
                    </section>
                </div>

                {/* Cột phải: Khu vực Hành động Verifier */}
                <div className="space-y-8">
                    <section className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 sticky top-28">
                        <h2 className="font-title-lg text-on-surface font-bold mb-6 flex items-center gap-3">
                            <span className="material-symbols-outlined text-secondary">law</span>
                            Hành động Pháp lý (Legal Action)
                        </h2>

                        {/* Bước 1: Tải và Kiểm tra Giấy chứng tử */}
                        <div className="mb-8">
                            <h3 className="font-label-lg text-on-surface font-bold mb-4">1. Kiểm tra Giấy chứng tử</h3>
                            {!requestData.deathCertificateProvided ? (
                                <div className="p-5 rounded-xl bg-error-container text-on-error-container flex items-center gap-4 border border-error/20">
                                    <span className="material-symbols-outlined text-[30px]">pending_actions</span>
                                    <div>
                                        <p className="font-label-md font-bold">Chờ tải lên / Đang chờ xử lý</p>
                                        <p className="font-body-sm text-on-error-container/90">Executor chưa tải lên giấy chứng tử.</p>
                                    </div>
                                </div>
                            ) : (
                                <div className="p-5 rounded-xl bg-success-container text-on-success-container flex items-center gap-4 border border-success/20">
                                    <span className="material-symbols-outlined text-[30px]">check_circle</span>
                                    <div>
                                        <p className="font-label-md font-bold">Đã tải lên và hợp lệ</p>
                                        <a href="#" className="font-body-sm text-secondary underline">Xem tài liệu gốc (PDF)</a>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Bước 2: Quyết định Phê duyệt / Từ chối */}
                        <div>
                            <h3 className="font-label-lg text-on-surface font-bold mb-4">2. Kết quả xác minh</h3>

                            {verificationResult === 'APPROVED' && (
                                <div className="p-5 mb-4 rounded-xl bg-success-container text-on-success-container flex items-center gap-4 border border-success/20">
                                    <span className="material-symbols-outlined text-[30px]">thumb_up</span>
                                    <div>
                                        <p className="font-label-md font-bold">Hồ sơ đã được phê duyệt</p>
                                        <p className="font-body-sm text-on-success-container/90">Yêu cầu đã được ký số thành công.</p>
                                    </div>
                                </div>
                            )}

                            {verificationResult === 'REJECTED' && (
                                <div className="p-5 mb-4 rounded-xl bg-error-container text-on-error-container flex items-center gap-4 border border-error/20">
                                    <span className="material-symbols-outlined text-[30px]">thumb_down</span>
                                    <div>
                                        <p className="font-label-md font-bold">Hồ sơ đã bị từ chối</p>
                                        <p className="font-body-sm text-on-error-container/90">Lý do: {rejectionReason}</p>
                                    </div>
                                </div>
                            )}

                            {verificationResult === null && (
                                <div className="space-y-4">
                                    <button
                                        onClick={handleApprove}
                                        disabled={!requestData.deathCertificateProvided || isSigning}
                                        className={`w-full h-14 rounded-xl text-on-primary font-label-md font-bold transition-all shadow-md flex items-center justify-center gap-2 ${!requestData.deathCertificateProvided || isSigning ? 'bg-surface-container text-outline cursor-not-allowed' : 'bg-primary hover:bg-primary-container'}`}
                                    >
                                        {isSigning ? (
                                            <>
                                                <span className="material-symbols-outlined animate-spin text-[20px]">sync</span>
                                                Đang khởi tạo chữ ký số...
                                            </>
                                        ) : (
                                            <>
                                                <span className="material-symbols-outlined text-[20px]">lock_clock</span>
                                                Phê duyệt & Ký số Cấp phép
                                            </>
                                        )}
                                    </button>
                                    <p className="text-xs text-center text-on-surface-variant mt-2">
                                        Lưu ý: Chức năng ký số chỉ hoạt động sau khi xác nhận Giấy chứng tử hợp lệ.
                                    </p>

                                    <div className="relative mt-6 pt-6 border-t border-outline-variant/30">
                                        <div className="absolute -top-2 left-1/2 -translate-x-1/2 bg-surface-container-lowest px-2 text-xs text-outline font-medium">HOẶC</div>
                                        <div className="flex flex-col gap-2">
                                            <label className="font-label-sm text-on-surface-variant">Lý do từ chối (nếu có)</label>
                                            <input
                                                type="text"
                                                placeholder="Nhập lý do tại đây..."
                                                value={rejectionReason}
                                                onChange={(e) => setRejectionReason(e.target.value)}
                                                className="w-full h-10 px-4 rounded-lg border border-outline-variant bg-background focus:outline-none focus:border-error"
                                            />
                                            <button
                                                onClick={handleReject}
                                                disabled={!rejectionReason || isSigning}
                                                className={`w-full h-12 rounded-xl font-label-md font-bold transition-all flex items-center justify-center gap-2 ${!rejectionReason || isSigning ? 'bg-surface-container text-outline cursor-not-allowed' : 'bg-error-container text-on-error-container hover:bg-error/20'}`}
                                            >
                                                <span className="material-symbols-outlined text-[18px]">block</span>
                                                Từ chối yêu cầu
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Footer note */}
                        <p className="text-xs text-outline mt-6 pt-6 border-t border-outline-variant/20 text-center">
                            Quyết định này sẽ được ghi lại trong Audit Log hệ thống (FR-15).
                        </p>
                    </section>
                </div>
            </main>
        </div>
    );
}