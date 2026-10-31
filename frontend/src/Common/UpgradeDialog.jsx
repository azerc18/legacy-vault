import React from 'react';
import { useNavigate } from 'react-router-dom';
import { btnOutline, btnPrimary, Icon } from '../Owner/ownerUi.jsx';

export default function UpgradeDialog({ open, message, onClose }) {
    const navigate = useNavigate();
    if (!open) return null;

    const handleUpgrade = () => {
        if (onClose) onClose();
        navigate('/pricing');
    };

    return (
        <div
            className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center px-4"
            onClick={onClose}
        >
            <div
                className="bg-surface-container-lowest rounded-2xl p-6 w-full max-w-md border border-outline-variant/30 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center gap-2 text-primary">
                    <Icon name="workspace_premium" className="text-[26px]" />
                    <h3 className="font-title-lg text-title-lg">Upgrade to VIP</h3>
                </div>
                <p className="mt-3 text-sm text-on-surface-variant">
                    {message ||
                        'You have reached the limit of the Free plan. Upgrade to VIP to unlock up to 5 vaults, 5 beneficiaries per vault, 2 GB of encrypted storage, and legacy audio/video messages.'}
                </p>
                <ul className="mt-4 space-y-2 text-sm text-on-surface">
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        Up to 5 vaults &amp; 5 beneficiaries per vault
                    </li>
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        2 GB encrypted cloud storage
                    </li>
                    <li className="flex items-center gap-2">
                        <Icon name="check_circle" className="text-primary text-[18px]" />
                        Legacy audio &amp; video farewell messages
                    </li>
                </ul>
                <div className="mt-6 flex justify-end gap-3">
                    <button type="button" onClick={onClose} className={btnOutline}>
                        Not now
                    </button>
                    <button type="button" onClick={handleUpgrade} className={btnPrimary}>
                        <Icon name="workspace_premium" className="text-[18px]" />
                        View VIP Plans
                    </button>
                </div>
            </div>
        </div>
    );
}

