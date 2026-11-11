import React, { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AnimateUIProvider } from '@animate-ui/react/provider';
import Dashboard from './Dashboard.jsx';
import Login from './LogInLogOut/Login.jsx';
import RequireRole from './Common/RequireRole.jsx';
import NotFound from './Common/NotFound.jsx';
import AssistantWidget from './Assistant/AssistantWidget.jsx';
import PlanLimitUpgradeDialog from './Common/PlanLimitUpgradeDialog.jsx'; // T1 (m6)
import InteractiveVaultLock from './Common/InteractiveVaultLock.jsx';
import IdleLogout from './Common/IdleLogout.jsx'; // Hồ sơ > Tuỳ chọn: tự đăng xuất khi rảnh
import SpotlightTour from './Common/SpotlightTour.jsx';
import DemoBanner from './Common/DemoBanner.jsx';
import { useTranslation } from 'react-i18next';

// Tách code theo trang: chỉ tải JS của trang đang mở thay vì cả app (trước đây ~1,4MB một lần).
// Trang công khai hay vào nhất (landing, đăng nhập) vẫn tải sẵn.
const UserHome = lazy(() => import('./UserHome.jsx'));
const Register = lazy(() => import('./LogInLogOut/Register.jsx'));
const VerifyEmail = lazy(() => import('./LogInLogOut/VerifyEmail.jsx'));
const ForgotPassword = lazy(() => import('./LogInLogOut/ForgotPassword.jsx'));
const ResetPassword = lazy(() => import('./LogInLogOut/ResetPassword.jsx'));
const LegalVerifierDashboard = lazy(() => import('./LegalVerifier/LegalVerifierDashboard.jsx'));
const VerificationRequestDetail = lazy(() => import('./LegalVerifier/VerificationRequestDetail.jsx'));
const VerificationHistory = lazy(() => import('./LegalVerifier/VerificationHistory.jsx'));
const VerifierHome = lazy(() => import('./LegalVerifier/VerifierHome.jsx'));
const ExecutorHome = lazy(() => import('./Executor/ExecutorHome.jsx'));
const ExecutorCase = lazy(() => import('./Executor/ExecutorCase.jsx'));
const ExecutorInvitation = lazy(() => import('./Executor/ExecutorInvitation.jsx'));
const BeneficiaryVaults = lazy(() => import('./Beneficiary/BeneficiaryVaults.jsx'));
const BeneficiaryVaultDetail = lazy(() => import('./Beneficiary/BeneficiaryVaultDetail.jsx'));
const BeneficiaryGuide = lazy(() => import('./Beneficiary/BeneficiaryGuide.jsx'));
const OwnerVaults = lazy(() => import('./Owner/OwnerVaults.jsx'));
const CreateVault = lazy(() => import('./Owner/CreateVault.jsx'));
const OwnerVaultDetail = lazy(() => import('./Owner/OwnerVaultDetail.jsx'));
const DeadMansSwitch = lazy(() => import('./Owner/DeadMansSwitch.jsx'));
const OwnerExecutors = lazy(() => import('./Owner/OwnerExecutors.jsx'));
const OwnerActivity = lazy(() => import('./Owner/OwnerActivity.jsx'));
const DmsConfirmationResultPage = lazy(() => import('./Owner/DmsConfirmationResultPage.jsx'));
const DmsConfirmPage = lazy(() => import('./Owner/DmsConfirmPage.jsx'));
const PricingPage = lazy(() => import('./Owner/PricingPage.jsx'));
const BillingPage = lazy(() => import('./Owner/BillingPage.jsx'));
const PaymentResultPage = lazy(() => import('./Owner/PaymentResultPage.jsx'));
const CheckinDonePage = lazy(() => import('./Owner/CheckinDonePage.jsx'));
const CheckinConfirmPage = lazy(() => import('./Owner/CheckinConfirmPage.jsx'));
const AdminUsers = lazy(() => import('./Admin/AdminUsers.jsx'));
const AdminVaults = lazy(() => import('./Admin/AdminVaults.jsx'));
const ProfilePage = lazy(() => import('./Profile/ProfilePage.jsx'));
const AdminRoles = lazy(() => import('./Admin/AdminRoles.jsx'));
const AdminAuditLogs = lazy(() => import('./Admin/AdminAuditLogs.jsx'));
const AdminAlerts = lazy(() => import('./Admin/AdminAlerts.jsx'));
const AdminDashboard = lazy(() => import('./Admin/AdminDashboard.jsx'));
const AdminPayments = lazy(() => import('./Admin/AdminPayments.jsx'));
const AdminRevenue = lazy(() => import('./Admin/AdminRevenue.jsx'));
const AdminPlans = lazy(() => import('./Admin/AdminPlans.jsx'));
const AdminNotifications = lazy(() => import('./Admin/AdminNotifications.jsx'));
const AdminBeneficiaryLocks = lazy(() => import('./Admin/AdminBeneficiaryLocks.jsx'));
const DemoEntry = lazy(() => import('./Common/DemoEntry.jsx'));

// Bọc trang bằng kiểm tra role; roles = null => chỉ cần đăng nhập
const guard = (roles, el) => <RequireRole roles={roles}>{el}</RequireRole>;

const OWNER = ['OWNER'];
const BENEFICIARY = ['BENEFICIARY'];
const EXECUTOR = ['EXECUTOR'];
const VERIFIER = ['LEGAL_VERIFIER'];
const ADMIN = ['ADMIN'];

function App() {
    useTranslation();
    return (
        <AnimateUIProvider>
            <BrowserRouter>
                <DemoBanner />
                <IdleLogout />
                <InteractiveVaultLock variant="sentinel" />
                {/* fallback rỗng: không hiện chữ "Đang tải"; trang tải rất nhanh vì chỉ là một phần nhỏ */}
                <Suspense fallback={null}>
                <Routes>
                {/* Công khai */}
                <Route path="/" element={<Dashboard />} />
                <Route path="/demo" element={<DemoEntry />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route path="/verify-email" element={<VerifyEmail />} />
                <Route path="/forgot-password" element={<ForgotPassword />} />
                <Route path="/reset-password" element={<ResetPassword />} />
                <Route path="/dms/confirm-done" element={<DmsConfirmationResultPage />} />
                <Route path="/dms/confirm" element={<DmsConfirmPage />} />
                <Route path="/payment/result" element={<PaymentResultPage />} />
                <Route path="/dms/checkin" element={<CheckinConfirmPage />} />
                <Route path="/dms/checkin-done" element={<CheckinDonePage />} />

                {/* Đã đăng nhập */}
                <Route path="/home" element={guard(null, <UserHome />)} />

                {/* Vault Owner */}
                <Route path="/owner/vaults" element={guard(OWNER, <OwnerVaults />)} />
                <Route path="/owner/vaults/new" element={guard(OWNER, <CreateVault />)} />
                <Route path="/owner/vaults/:vaultId" element={guard(OWNER, <OwnerVaultDetail />)} />
                <Route path="/owner/dms" element={guard(OWNER, <DeadMansSwitch />)} />
                <Route path="/owner/executors" element={guard(OWNER, <OwnerExecutors />)} />
                <Route path="/owner/activity" element={guard(OWNER, <OwnerActivity />)} />
                <Route path="/pricing" element={guard(OWNER, <PricingPage />)} />
                <Route path="/billing" element={guard(OWNER, <BillingPage />)} />

                {/* Beneficiary */}
                <Route path="/beneficiary" element={guard(BENEFICIARY, <BeneficiaryVaults />)} />
                <Route path="/beneficiary/vaults/:vaultId" element={guard(BENEFICIARY, <BeneficiaryVaultDetail />)} />
                <Route path="/beneficiary/guide" element={guard(BENEFICIARY, <BeneficiaryGuide />)} />

                {/* Executor */}
                <Route path="/executor/home" element={guard(EXECUTOR, <ExecutorHome />)} />
                <Route path="/executor/cases/:assignmentId" element={guard(EXECUTOR, <ExecutorCase />)} />
                {/* Lời mời: chỉ cần đăng nhập — JWT của phiên cũ có thể chưa mang role EXECUTOR vừa được cấp */}
                <Route path="/executor/invitations/:assignmentId" element={<ExecutorInvitation />} />

                {/* Legal Verifier */}
                <Route path="/verifier/home" element={guard(VERIFIER, <VerifierHome />)} />
                <Route path="/verifier" element={guard(VERIFIER, <LegalVerifierDashboard />)} />
                <Route path="/verifier/request/:requestId" element={guard(VERIFIER, <VerificationRequestDetail />)} />
                <Route path="/verifier/history" element={guard(VERIFIER, <VerificationHistory />)} />

                {/* System Admin */}
                <Route path="/admin" element={guard(ADMIN, <AdminDashboard />)} />
                <Route path="/admin/users" element={guard(ADMIN, <AdminUsers />)} />
                <Route path="/admin/vaults" element={guard(ADMIN, <AdminVaults />)} />

                <Route path="/admin/roles" element={guard(ADMIN, <AdminRoles />)} />

                <Route path="/admin/audit-logs" element={guard(ADMIN, <AdminAuditLogs />)} />
                <Route path="/admin/alerts" element={guard(ADMIN, <AdminAlerts />)} />
                <Route path="/admin/payments" element={guard(ADMIN, <AdminPayments />)} />
                <Route path="/admin/revenue" element={guard(ADMIN, <AdminRevenue />)} />
                <Route path="/admin/plans" element={guard(ADMIN, <AdminPlans />)} />
                <Route path="/admin/notifications" element={guard(ADMIN, <AdminNotifications />)} />
                <Route path="/admin/beneficiary-locks" element={guard(ADMIN, <AdminBeneficiaryLocks />)} />

                <Route path="/me" element={guard(null, <ProfilePage />)} />

                <Route path="*" element={<NotFound />} />
            </Routes>
                </Suspense>
            <AssistantWidget />
            <PlanLimitUpgradeDialog />
            <SpotlightTour />
        </BrowserRouter>
        </AnimateUIProvider>
    );
}

export default App;
