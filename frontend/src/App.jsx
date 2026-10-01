import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Dashboard from './Dashboard';
import LegalVerifierDashboard from './LegalVerifier/LegalVerifierDashboard.jsx'; // Import trang của bạn
import Login from './LogInLogOut/Login.jsx';
import Register from './LogInLogOut/Register.jsx';

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Dashboard />} />
                {/* Đường dẫn dành riêng cho Người số 5 - Legal Verifier */}
                <Route path="/verifier" element={<LegalVerifierDashboard />} />
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<Register />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;