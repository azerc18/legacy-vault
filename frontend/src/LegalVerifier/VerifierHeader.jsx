import React from 'react';
import AppHeader, { PAGE_WIDTH, PageTitle } from '../Common/AppHeader.jsx';
import { getUser, ROLES } from '../auth/auth.js';

// Header + tiêu đề dùng chung cho các trang con của Legal Verifier (danh sách, chi tiết, lịch sử).
// Giữ nguyên props cũ để không phải sửa nơi gọi: backTo = link quay lại; right = phần tử bên phải tiêu đề.
const BACK_LABEL = { '/verifier': 'All requests', '/verifier/home': 'Verifier home' };

export default function VerifierHeader({ title, subtitle, backTo = '/verifier/home', backLabel, right = null }) {
    const roles = getUser()?.roles ?? [];
    const hasOtherRoles = roles.some((r) => r !== ROLES.LEGAL_VERIFIER);
    return (
        <>
            <AppHeader icon="verified_user" workspace="Legal Verifier portal" home="/verifier/home" userHome={hasOtherRoles} />
            <div className={`${PAGE_WIDTH} pt-10`}>
                <PageTitle className="" back={{ to: backTo, label: backLabel || BACK_LABEL[backTo] || 'Back' }}
                           title={title} subtitle={subtitle} right={right} />
            </div>
        </>
    );
}
