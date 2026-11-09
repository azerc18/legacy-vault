import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AnimateUIProvider } from '@animate-ui/react/provider';
import Dashboard from './Dashboard.jsx';
import UserHome from './UserHome.jsx';
import Login from './LogInLogOut/Login.jsx';
import Register from './LogInLogOut/Register.jsx';
import VerifyEmail from './LogInLogOut/VerifyEmail.jsx';
import ForgotPassword from './LogInLogOut/ForgotPassword.jsx';
import ResetPassword from './LogInLogOut/ResetPassword.jsx';
import LegalVerifierDashboard from './LegalVerifier/LegalVerifierDashboard.jsx';
import VerificationRequestDetail from './LegalVerifier/VerificationRequestDetail.jsx';
import VerificationHistory from './LegalVerifier/VerificationHistory.jsx';
import VerifierHome from './LegalVerifier/VerifierHome.jsx';
import ExecutorHome from './Executor/ExecutorHome.jsx';
import ExecutorCase from './Executor/ExecutorCase.jsx';
import BeneficiaryVaults from './Beneficiary/BeneficiaryVaults.jsx';
import BeneficiaryVaultDetail from './Beneficiary/BeneficiaryVaultDetail.jsx';
import BeneficiaryGuide from './Beneficiary/BeneficiaryGuide.jsx';
import OwnerVaults from './Owner/OwnerVaults.jsx';
import CreateVault from './Owner/CreateVault.jsx';
import OwnerVaultDetail from './Owner/OwnerVaultDetail.jsx';
import DeadMansSwitch from './Owner/DeadMansSwitch.jsx';
import OwnerExecutors from './Owner/OwnerExecutors.jsx';
import OwnerActivity from './Owner/OwnerActivity.jsx';
import DmsConfirmationResultPage from './Owner/DmsConfirmationResultPage.jsx';
import DmsConfirmPage from './Owner/DmsConfirmPage.jsx';
import PricingPage from './Owner/PricingPage.jsx';
import BillingPage from './Owner/BillingPage.jsx';
import PaymentResultPage from './Owner/PaymentResultPage.jsx';
import CheckinDonePage from './Owner/CheckinDonePage.jsx';
import CheckinConfirmPage from './Owner/CheckinConfirmPage.jsx';
import AdminUsers from './Admin/AdminUsers.jsx';
import AdminVaults from './Admin/AdminVaults.jsx';
import RequireRole from './Common/RequireRole.jsx';
import NotFound from './Common/NotFound.jsx';
import ProfilePage from './Profile/ProfilePage.jsx';
import AdminRoles from './Admin/AdminRoles.jsx';
import AdminAuditLogs from './Admin/AdminAuditLogs.jsx';
import AdminAlerts from './Admin/AdminAlerts.jsx';
import AdminDashboard from './Admin/AdminDashboard.jsx';
import AdminPayments from './Admin/AdminPayments.jsx';
import AdminPlans from './Admin/AdminPlans.jsx';
import AdminNotifications from './Admin/AdminNotifications.jsx';
import AdminBeneficiaryLocks from './Admin/AdminBeneficiaryLocks.jsx';
import AssistantWidget from './Assistant/AssistantWidget.jsx';
import PlanLimitUpgradeDialog from './Common/PlanLimitUpgradeDialog.jsx'; // T1 (m6)
import InteractiveVaultLock from './Common/InteractiveVaultLock.jsx';
import IdleLogout from './Common/IdleLogout.jsx'; // Hồ sơ > Tuỳ chọn: tự đăng xuất khi rảnh
import SpotlightTour from './Common/SpotlightTour.jsx';
import DemoBanner from './Common/DemoBanner.jsx';
import DemoEntry from './Common/DemoEntry.jsx';
import { useTranslation } from 'react-i18next';

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
                <Route path="/admin/plans" element={guard(ADMIN, <AdminPlans />)} />
                <Route path="/admin/notifications" element={guard(ADMIN, <AdminNotifications />)} />
                <Route path="/admin/beneficiary-locks" element={guard(ADMIN, <AdminBeneficiaryLocks />)} />

                <Route path="/me" element={guard(null, <ProfilePage />)} />

                <Route path="*" element={<NotFound />} />
            </Routes>
            <AssistantWidget />
            <PlanLimitUpgradeDialog />
            <SpotlightTour />
        </BrowserRouter>
        </AnimateUIProvider>
    );
}

export default App;
