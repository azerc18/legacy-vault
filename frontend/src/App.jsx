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
            </Routes>
        </BrowserRouter>
    );
}

export default App;