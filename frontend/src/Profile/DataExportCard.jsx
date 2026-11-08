// "Dữ liệu của tôi" (ý từ Bitwarden Export / Google Takeout): tải một file JSON từ các API có sẵn.
// Không chứa bí mật: API tài sản chỉ trả trường công khai; vẫn lọc thêm các khoá nhạy cảm cho chắc.
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { userApi } from '../api/userApi.js';
import { ownerApi } from '../api/ownerApi.js';
import { paymentApi } from '../api/paymentApi.js';
import { sessionApi } from '../api/sessionApi.js';
import { getErrorMessage } from '../api/http.js';
import { Card, Alert, Icon, btnOutline } from '../Owner/ownerUi.jsx';

// So theo từng từ của tên khoá (camelCase / snake_case / kebab) để "pin" không bắt nhầm "shipping", "mapping"
const SENSITIVE_WORD = /^(secrets?|passwords?|passphrases?|passcodes?|seeds?|pins?|otps?|tokens?|cvv|cvc|mfa)$/;
function isSensitiveKey(key) {
    const words = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    return words.some((w, i) => SENSITIVE_WORD.test(w) || w === 'privatekey' || (w === 'private' && words[i + 1] === 'key'));
}

// Bỏ mọi khoá có tên nhạy cảm ở mọi tầng (vd. publicFields của tài sản do người dùng tự đặt tên)
function scrub(value) {
    if (Array.isArray(value)) return value.map(scrub);
    if (value && typeof value === 'object') {
        return Object.fromEntries(Object.entries(value)
            .filter(([k]) => !isSensitiveKey(k))
            .map(([k, v]) => [k, scrub(v)]));
    }
    return value;
}

const safe = (p, fallback = null) => p.catch(() => fallback);

async function collect(isOwner) {
    const profile = await userApi.getMe();
    const data = { exportedAt: new Date().toISOString(), profile, signedInDevices: await safe(sessionApi.list(), []) };
    if (!isOwner) return data;

    const vaults = await safe(ownerApi.listVaults(), []);
    const withAssets = await Promise.all(vaults.map(async (v) => ({
        ...v,
        assets: await safe(ownerApi.listAssets(v.id), []),
        documents: await safe(ownerApi.listDocuments(v.id), []),   // chỉ thông tin tệp, không kèm nội dung
    })));
    const [executors, dms, confirmers, subscription, payments] = await Promise.all([
        safe(ownerApi.listExecutors(), []),
        safe(ownerApi.getDms()),
        safe(ownerApi.listDmsConfirmers(), []),
        safe(paymentApi.getMySubscription()),
        safe(paymentApi.getMyPayments(), []),
    ]);
    if (subscription) delete subscription.availablePlans;
    return { ...data, vaults: withAssets, executors, deadMansSwitch: dms, dmsConfirmers: confirmers, subscription, payments };
}

export default function DataExportCard({ profile }) {
    const { t } = useTranslation('profile');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const download = async () => {
        setError(''); setBusy(true);
        try {
            const data = scrub(await collect(profile.roles?.includes('OWNER')));
            const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
            const href = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = href;
            a.download = `legacyvault-${new Date().toISOString().slice(0, 10)}.json`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(href);
        } catch (e) {
            setError(getErrorMessage(e, t('export.failed')));
        } finally {
            setBusy(false);
        }
    };

    return (
        <Card title={t('export.title')} icon="download">
            <Alert>{error}</Alert>
            <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                <div className="flex-1 text-sm text-on-surface-variant space-y-1">
                    <p>{t('export.description')}</p>
                    <p className="flex items-center gap-1.5 text-xs">
                        <Icon name="lock" className="text-[16px]" /> {t('export.noSecrets')}
                    </p>
                </div>
                <button type="button" onClick={download} disabled={busy} className={`${btnOutline} shrink-0`}>
                    <Icon name={busy ? 'progress_activity' : 'file_download'} className={`text-[18px] ${busy ? 'animate-spin' : ''}`} />
                    {busy ? t('export.preparing') : t('export.button')}
                </button>
            </div>
        </Card>
    );
}
