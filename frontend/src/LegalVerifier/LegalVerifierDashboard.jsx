import React, { useState } from 'react';
import { Link } from 'react-router-dom';

export default function LegalVerifierDashboard() {
    const [selectedRequest, setSelectedRequest] = useState(1);
    const [signaturePin, setSignaturePin] = useState('');
    const [actionStatus, setActionStatus] = useState(null); // 'approved' | 'rejected'

    // Mock dữ liệu các yêu cầu đang chờ duyệt từ Executor
    const requests = [
        {
            id: 1,
            ownerName: "John Doe",
            executorName: "Robert (Primary Executor)",
            submittedDate: "2026-10-01",
            status: "Pending Review",
            deathCertificate: "death_cert_john_doe.pdf",
            vaultsCount: 2,
            assets: ["Family Trust & Wills", "Crypto & Digital Assets"]
        },
        {
            id: 2,
            ownerName: "Alice Smith",
            executorName: "David Johnson (Primary Executor)",
            submittedDate: "2026-09-28",
            status: "Pending Review",
            deathCertificate: "certificate_alice_smith.pdf",
            vaultsCount: 1,
            assets: ["Personal Directives & Insurance"]
        }
    ];

    const currentReq = requests.find(r => r.id === selectedRequest) || requests[0];

    const handleApprove = () => {
        if (signaturePin.length < 6) {
            alert("Vui lòng nhập đủ 6 chữ số mã PIN chữ ký số!");
            return;
        }
        setActionStatus('approved');
    };

    const handleReject = () => {
        const reason = prompt("Nhập lý do từ chối hồ sơ:");
        if (reason) {
            setActionStatus('rejected');
        }
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col">
            {/* HEADER */}
            <header className="fixed top-0 left-0 right-0 w-full z-50 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] h-20">
                <div className="max-w-[1440px] w-full mx-auto px-6 h-full flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-on-primary shadow-sm">
                            <span className="material-symbols-outlined text-[24px]">verified_user</span>
                        </div>
                        <div>
                            <span className="font-headline-sm text-[18px] text-primary font-bold block leading-tight">LegacyVault</span>
                            <span className="font-label-sm text-[11px] text-secondary font-semibold tracking-wide uppercase block">Legal Verifier Portal</span>
                        </div>
                    </div>

                    <div className="flex items-center gap-4">
                        <Link to="/" className="text-sm font-semibold text-on-surface-variant hover:text-primary transition-colors flex items-center gap-1">
                            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
                            Về Dashboard chung
                        </Link>
                        <div className="h-6 w-[1px] bg-outline-variant/30"></div>
                        <div className="flex items-center gap-3">
                            <span className="text-sm font-semibold text-on-surface-variant">Verifier: <strong className="text-primary">Officer Sarah</strong></span>
                            <div className="w-9 h-9 rounded-full bg-primary text-on-primary flex items-center justify-center font-bold text-sm">
                                OS
                            </div>
                        </div>
                    </div>
                </div>
            </header>

            {/* MAIN LAYOUT */}
            <main className="w-full pt-28 pb-12 flex-1 max-w-[1440px] mx-auto px-6">
                <div className="mb-6">
                    <h1 className="font-headline-lg text-primary font-bold tracking-tight">Legal Verification & Digital Signatures</h1>
                    <p className="font-body-md text-on-surface-variant">Duyệt hồ sơ tử vong do Executor gửi lên và tiến hành ký điện tử cấp phép mở khóa Vault.</p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* CỘT TRÁI: Danh sách yêu cầu */}
                    <div className="bg-surface-container-lowest rounded-xl p-4 shadow-sm border border-outline-variant/30 flex flex-col gap-3 h-fit">
                        <h2 className="font-label-lg text-primary font-bold px-2">Yêu cầu chờ duyệt ({requests.length})</h2>

                        <div className="flex flex-col gap-2">
                            {requests.map((req) => (
                                <div
                                    key={req.id}
                                    onClick={() => { setSelectedRequest(req.id); setActionStatus(null); setSignaturePin(''); }}
                                    className={`p-4 rounded-xl cursor-pointer transition-all border ${
                                        selectedRequest === req.id
                                            ? 'bg-surface-container-low border-primary shadow-xs'
                                            : 'bg-background border-transparent hover:bg-surface-container-low/50'
                                    }`}
                                >
                                    <div className="flex justify-between items-start mb-1">
                                        <span className="font-label-md font-bold text-on-surface">{req.ownerName}</span>
                                        <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed font-semibold">
                                            {req.status}
                                        </span>
                                    </div>
                                    <p className="text-xs text-on-surface-variant mb-2">Executor: {req.executorName}</p>
                                    <p className="text-[11px] text-outline">Ngày gửi: {req.submittedDate}</p>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* CỘT PHẢI: Chi tiết hồ sơ và Ký số */}
                    <div className="lg:col-span-2 flex flex-col gap-6">
                        {actionStatus === 'approved' ? (
                            <div className="bg-success-container border border-success/30 rounded-xl p-8 text-center flex flex-col items-center justify-center gap-3 shadow-sm">
                                <span className="material-symbols-outlined text-[48px] text-on-success-container">task_alt</span>
                                <h2 className="font-headline-md text-on-success-container font-bold">Đã ký duyệt & Cấp phép mở khóa thành công!</h2>
                                <p className="text-on-success-container/90 max-w-md text-sm">Chữ ký số đã được ghi nhận vào hệ thống. Quyền truy cập Vault đã được chuyển giao cho Executor.</p>
                                <button
                                    onClick={() => { setActionStatus(null); setSignaturePin(''); }}
                                    className="mt-4 px-5 py-2.5 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-xs"
                                >
                                    Tiếp tục xử lý hồ sơ khác
                                </button>
                            </div>
                        ) : actionStatus === 'rejected' ? (
                            <div className="bg-error-container border border-error/30 text-on-error-container rounded-xl p-8 text-center flex flex-col items-center justify-center gap-3 shadow-sm">
                                <span className="material-symbols-outlined text-[48px] text-error">cancel</span>
                                <h2 className="font-headline-md font-bold">Đã từ chối hồ sơ xác minh</h2>
                                <p className="max-w-md text-sm opacity-90">Hồ sơ đã bị từ chối và thông báo đã được gửi về cho Executor kèm theo lý do.</p>
                                <button
                                    onClick={() => { setActionStatus(null); setSignaturePin(''); }}
                                    className="mt-4 px-5 py-2.5 rounded-xl bg-surface-container text-on-surface font-label-md font-bold hover:bg-surface-container-high transition-all shadow-xs"
                                >
                                    Quay lại danh sách
                                </button>
                            </div>
                        ) : (
                            <>
                                {/* Thông tin chi tiết hồ sơ */}
                                <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm border border-outline-variant/30 flex flex-col gap-5">
                                    <div className="flex justify-between items-center border-b border-outline-variant/20 pb-4">
                                        <div>
                                            <h2 className="font-headline-md text-primary font-bold">Chi tiết hồ sơ: {currentReq.ownerName}</h2>
                                            <p className="text-sm text-on-surface-variant">Kiểm tra thông tin pháp lý và tài liệu đính kèm</p>
                                        </div>
                                        <span className="px-3 py-1 bg-surface-container text-primary rounded-lg font-label-sm font-semibold">
                                            ID Yêu cầu: #VER-{currentReq.id}026
                                        </span>
                                    </div>

                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <div className="bg-background p-4 rounded-xl border border-outline-variant/20">
                                            <p className="text-xs text-on-surface-variant uppercase font-bold tracking-wider mb-1">Người ủy thác (Owner)</p>
                                            <p className="font-label-md text-on-surface font-bold text-base">{currentReq.ownerName}</p>
                                        </div>
                                        <div className="bg-background p-4 rounded-xl border border-outline-variant/20">
                                            <p className="text-xs text-on-surface-variant uppercase font-bold tracking-wider mb-1">Người thực thi (Executor)</p>
                                            <p className="font-label-md text-on-surface font-bold text-base">{currentReq.executorName}</p>
                                        </div>
                                    </div>

                                    {/* Tài liệu đính kèm */}
                                    <div>
                                        <p className="font-label-md text-on-surface font-bold mb-2">Tài liệu pháp lý đính kèm</p>
                                        <div className="flex items-center justify-between p-4 rounded-xl bg-surface-container-low border border-outline-variant/20">
                                            <div className="flex items-center gap-3">
                                                <span className="material-symbols-outlined text-secondary text-[28px]">description</span>
                                                <div>
                                                    <p className="font-label-sm text-primary font-bold">{currentReq.deathCertificate}</p>
                                                    <p className="text-xs text-on-surface-variant">Giấy chứng tử chính thức • Đã mã hóa bảo mật</p>
                                                </div>
                                            </div>
                                            <button
                                                onClick={() => alert(`Đang mở xem trước tài liệu: ${currentReq.deathCertificate}`)}
                                                className="px-3.5 py-2 rounded-lg bg-surface-container-lowest text-primary hover:bg-surface-container font-label-sm font-bold shadow-xs transition-colors flex items-center gap-1.5"
                                            >
                                                <span className="material-symbols-outlined text-[16px]">visibility</span>
                                                Xem tài liệu
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                {/* Khu vực Ký số điện tử (Digital Signature Module) */}
                                <div className="bg-surface-container-lowest rounded-xl p-6 shadow-sm border border-outline-variant/30 flex flex-col gap-5">
                                    <div className="flex items-center gap-2">
                                        <span className="material-symbols-outlined text-secondary text-[24px]">lock_reset</span>
                                        <h3 className="font-label-lg text-on-surface font-bold">Phê duyệt & Ký số cấp phép</h3>
                                    </div>

                                    <div className="flex flex-col gap-2">
                                        <label className="font-label-sm text-on-surface-variant">Nhập mã PIN Chữ ký số an toàn (Hệ thống HSM Mock):</label>
                                        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
                                            <input
                                                type="password"
                                                maxLength="6"
                                                placeholder="• • • • • •"
                                                value={signaturePin}
                                                onChange={(e) => setSignaturePin(e.target.value)}
                                                className="w-full sm:w-48 h-12 px-4 rounded-xl border border-outline-variant bg-background text-lg tracking-[0.5em] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
                                            />
                                            <span className="text-xs text-on-surface-variant">Nhập 6 chữ số định danh chữ ký số của bạn để xác thực pháp lý.</span>
                                        </div>
                                    </div>

                                    <div className="flex flex-col sm:flex-row gap-3 pt-4 border-t border-outline-variant/20">
                                        <button
                                            onClick={handleApprove}
                                            className="flex-1 h-12 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all flex items-center justify-center gap-2 shadow-sm"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">how_to_reg</span>
                                            Ký số & Phê duyệt mở khóa Vault
                                        </button>
                                        <button
                                            onClick={handleReject}
                                            className="px-6 h-12 rounded-xl bg-error-container text-on-error-container font-label-md font-bold hover:bg-error hover:text-on-error transition-all flex items-center justify-center gap-2"
                                        >
                                            <span className="material-symbols-outlined text-[20px]">block</span>
                                            Từ chối hồ sơ
                                        </button>
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </main>
        </div>
    );
}