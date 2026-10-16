import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
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
import BeneficiaryVaults from './Beneficiary/BeneficiaryVaults.jsx';
import BeneficiaryVaultDetail from './Beneficiary/BeneficiaryVaultDetail.jsx';
import OwnerVaults from './Owner/OwnerVaults.jsx';
import CreateVault from './Owner/CreateVault.jsx';
import OwnerVaultDetail from './Owner/OwnerVaultDetail.jsx';
import DeadMansSwitch from './Owner/DeadMansSwitch.jsx';
import OwnerExecutors from './Owner/OwnerExecutors.jsx';

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Dashboard />} />       
                <Route path="/home" element={<UserHome />} />    
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
                <Route path="/verify-email" element={<VerifyEmail />} />
                <Route path="/forgot-password" element={<ForgotPassword />} />
                <Route path="/reset-password" element={<ResetPassword />} />
                <Route path="/verifier/home" element={<VerifierHome />} />
                <Route path="/executor/home" element={<ExecutorHome />} />
                <Route path="/verifier" element={<LegalVerifierDashboard />} />
                <Route path="/verifier/request/:requestId" element={<VerificationRequestDetail />} />
                <Route path="/verifier/history" element={<VerificationHistory />} />
                <Route path="/beneficiary" element={<BeneficiaryVaults />} />
                <Route path="/beneficiary/vaults/:vaultId" element={<BeneficiaryVaultDetail />} />
                <Route path="/owner/vaults" element={<OwnerVaults />} />
                <Route path="/owner/vaults/new" element={<CreateVault />} />
                <Route path="/owner/vaults/:vaultId" element={<OwnerVaultDetail />} />
                <Route path="/owner/dms" element={<DeadMansSwitch />} />
                <Route path="/owner/executors" element={<OwnerExecutors />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;