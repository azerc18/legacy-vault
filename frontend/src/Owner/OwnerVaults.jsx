import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { getUser } from '../auth/auth.js';
import { ownerApi } from '../api/ownerApi.js';
import { OwnerLayout, Icon, Alert, formatDate, btnPrimary } from './ownerUi.jsx';

export default function OwnerVaults() {
    const user = getUser();
    const [vaults, setVaults] = useState([]);

    useEffect(() => { ownerApi.listVaults().then(setVaults); }, []);

    if (!user) return <Navigate to="/login" replace />;

    return (
        <OwnerLayout
            title="My vaults"
            subtitle="Store assets and legal documents for the people you trust."
            actions={
                <Link to="/owner/vaults/new" className={btnPrimary}>
                    <Icon name="add" className="text-[18px]" /> New vault
                </Link>
            }
        >
            {/* Tạm thời: BE chưa có GET /api/vaults nên chỉ hiện vault tạo từ trình duyệt này */}
            <Alert type="info">
                This list shows vaults created from this browser. A full list will appear once the server supports it.
            </Alert>

            {vaults.length === 0 ? (
                <div className="text-center py-16 bg-surface-container-lowest rounded-2xl border border-outline-variant/30">
                    <Icon name="inventory_2" className="text-[40px] text-outline" />
                    <p className="mt-3 text-on-surface-variant">You have not created any vault yet.</p>
                    <Link to="/owner/vaults/new" className={`${btnPrimary} mt-5`}>Create your first vault</Link>
                </div>
            ) : (
                <div className="grid sm:grid-cols-2 gap-5">
                    {vaults.map((v) => (
                        <Link
                            key={v.id}
                            to={`/owner/vaults/${v.id}`}
                            className="block bg-surface-container-lowest rounded-2xl p-6 border border-outline-variant/30 hover:border-primary transition-all"
                        >
                            <div className="flex justify-between items-start gap-3">
                                <h3 className="font-title-lg text-title-lg">{v.name}</h3>
                                <span className="shrink-0 text-xs font-semibold px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">
                                    {v.status}
                                </span>
                            </div>
                            {v.description && <p className="mt-2 text-sm text-on-surface-variant line-clamp-2">{v.description}</p>}
                            <p className="mt-4 text-xs text-outline">Created {formatDate(v.createdAt)}</p>
                        </Link>
                    ))}
                </div>
            )}
        </OwnerLayout>
    );
}
