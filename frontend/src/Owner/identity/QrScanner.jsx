// Quét QR CCCD bằng camera sau của điện thoại / webcam. Dừng camera ngay khi đọc được hoặc khi đóng.
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { decodeQrFromVideo, looksLikeCitizenQr } from './qrDecode.js';
import { Alert, Icon, btnOutline } from '../ownerUi.jsx';

export default function QrScanner({ onResult, onCancel }) {
    const { t } = useTranslation('identity');
    const videoRef = useRef(null);
    const canvasRef = useRef(null);
    const [error, setError] = useState('');
    const [hint, setHint] = useState('');

    useEffect(() => {
        let stream;
        let timer;
        let stopped = false;

        const stop = () => {
            stopped = true;
            clearInterval(timer);
            stream?.getTracks().forEach((track) => track.stop());
        };

        (async () => {
            if (!navigator.mediaDevices?.getUserMedia) {
                setError(t('scan.unsupported'));
                return;
            }
            try {
                stream = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
                    audio: false,
                });
            } catch (err) {
                setError(err?.name === 'NotAllowedError' ? t('scan.denied') : t('scan.noCamera'));
                return;
            }
            if (stopped) {
                stream.getTracks().forEach((track) => track.stop());
                return;
            }
            const video = videoRef.current;
            video.srcObject = stream;
            await video.play().catch(() => {});
            timer = setInterval(() => {
                const text = decodeQrFromVideo(video, canvasRef.current);
                if (!text) return;
                if (!looksLikeCitizenQr(text)) {
                    setHint(t('scan.notCitizenQr'));
                    return;
                }
                stop();
                onResult(text);
            }, 250);
        })();

        return stop;
    }, []); // eslint-disable-line react-hooks/exhaustive-deps -- chỉ mở camera một lần

    return (
        <div className="space-y-3">
            <Alert>{error}</Alert>
            {!error && (
                <div className="relative overflow-hidden rounded-2xl bg-black aspect-[4/3] max-w-md mx-auto">
                    <video ref={videoRef} playsInline muted className="w-full h-full object-cover" />
                    {/* Khung ngắm: canh mã QR ở góc trên bên phải mặt trước CCCD vào giữa */}
                    <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                        <div className="w-1/2 aspect-square rounded-2xl border-4 border-white/80 shadow-[0_0_0_9999px_rgba(0,0,0,0.35)]" />
                    </div>
                </div>
            )}
            <canvas ref={canvasRef} className="hidden" />
            <p className="text-sm text-on-surface-variant text-center">{hint || t('scan.aim')}</p>
            <div className="flex justify-center">
                <button type="button" onClick={onCancel} className={btnOutline}>
                    <Icon name="close" className="text-[18px]" /> {t('actions.back')}
                </button>
            </div>
        </div>
    );
}
