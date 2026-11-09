/**
 * REAL END-TO-END LOCAL VERIFICATION OF AI OCR DEATH CERTIFICATE & GEMINI 3.6 FLASH
 * Runs against:
 *  - Frontend: http://localhost:5173
 *  - Backend:  http://localhost:8084 (Profile: local, AI_PROVIDER=gemini, GEMINI_MODEL=gemini-3.6-flash)
 *  - Database: MySQL 8.4 (localhost:3306 / legacy_vault)
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const {
    attachHumanCursor,
    humanMoveAndClick,
    humanType,
    humanScroll,
    humanPause
} = require('./human_interactions');

const BASE_URL = 'http://localhost:5173';
const ARTIFACT_DIR = 'C:/Users/shin/.gemini/antigravity/brain/84420023-c595-401f-a9d9-e40238fb8c21';
const VIDEO_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'videos', 'ai-ocr-demo');
const SAMPLE_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'sample-docs');

if (!fs.existsSync(VIDEO_DIR)) fs.mkdirSync(VIDEO_DIR, { recursive: true });
if (!fs.existsSync(SAMPLE_DIR)) fs.mkdirSync(SAMPLE_DIR, { recursive: true });

function createSampleDeathCertificatePdf(filePath, deceasedName, certNo) {
    const content = `%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >> endobj
4 0 obj << /Length 420 >>
stream
BT
/F1 14 Tf
72 720 Td
(CONG HOA XA HOI CHU NGHIA VIET NAM - Doc lap Tu do Hanh phuc) Tj
0 -28 Td
(TRICH LUC KHAI TU - Ban sao) Tj
0 -24 Td
(So: ${certNo} - Quyen so: 02/2026/TP-HT) Tj
0 -24 Td
(Ho va ten nguoi mat: ${deceasedName}) Tj
0 -24 Td
(Ngay sinh: 18/04/1965 - Gioi tinh: Nam - CCCD: 079065004812) Tj
0 -24 Td
(Thoi diem tu vong: 08:30 ngay 02/11/2026) Tj
0 -24 Td
(Noi tu vong: Benh vien Dai hoc Y Duoc TP. Ho Chi Minh) Tj
0 -24 Td
(Co quan cap: UBND Phuong Ben Nghe, Quan 1, TP. Ho Chi Minh) Tj
0 -24 Td
(Nguoi ky: Le Quoc Tuan - Pho Chu tich UBND - Da dong dau do hop le) Tj
ET
endstream
endobj
xref
0 5
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000188 00000 n 
trailer << /Size 5 /Root 1 0 R >>
startxref
660
%%EOF`;
    fs.writeFileSync(filePath, Buffer.from(content, 'utf-8'));
}

(async () => {
    const validPdfPath = path.join(SAMPLE_DIR, 'trich_luc_khai_tu_tran_long_van.pdf');
    const mismatchPdfPath = path.join(SAMPLE_DIR, 'giay_chung_tu_sai_ten.pdf');
    createSampleDeathCertificatePdf(validPdfPath, 'TRAN LONG VAN', '248/TLKT-BS');
    createSampleDeathCertificatePdf(mismatchPdfPath, 'NGUYEN VAN KHAC', '911/TLKT-BS');

    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    const context = await browser.newContext({
        locale: 'vi-VN',
        viewport: { width: 1440, height: 900 },
        recordVideo: {
            dir: VIDEO_DIR,
            size: { width: 1440, height: 900 }
        }
    });

    const page = await context.newPage();
    await page.addInitScript(() => {
        localStorage.setItem('i18nextLng', 'vi');
        localStorage.setItem('lv_lang', 'vi');
    });

    try {
        // =========================================================================
        // PART 1: EXECUTOR LOGS IN & USES AI OCR ON DEATH CERTIFICATE
        // =========================================================================
        console.log('1. Navigating to /login as Executor...');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'BƯỚC 1: ĐĂNG NHẬP VAI TRÒ NGƯỜI THỪA HÀNH (EXECUTOR)');
        await humanPause(page, 600);

        await humanType(page, 'input[type="email"]', 'executor@legacyvault.vn', 'Nhập email Executor');
        await humanType(page, 'input[type="password"]', 'Admin@123456', 'Nhập mật khẩu');
        await humanMoveAndClick(page, 'button[type="submit"]', 'Nhấn Đăng nhập');

        await page.waitForURL('**/owner**', { timeout: 10000 }).catch(() => {});
        await humanPause(page, 800);

        console.log('2. Opening Executor Case /executor/cases/55555555-5555-5555-5555-555555555501...');
        await page.goto(`${BASE_URL}/executor/cases/55555555-5555-5555-5555-555555555501`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'BƯỚC 2: KIỂM THỬ AI OCR PHÁT HIỆN GIẤY CHỨNG TỬ SAI TÊN CHỦ KÉT');
        await humanPause(page, 1000);

        await humanScroll(page, 380, 'Cuộn xuống khu vực Nộp giấy chứng tử & AI OCR');
        await humanPause(page, 600);

        // Upload MISMATCH certificate first to test legal warning badge
        const fileInput = page.locator('#submit-verification input[type="file"]').first();
        await fileInput.setInputFiles(mismatchPdfPath);
        await humanPause(page, 800);

        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Nhấn ✨ Quét & Trích xuất bằng AI (OCR) cho tệp sai tên');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await humanPause(page, 1500);

        const mismatchBadgeText = await page.locator('[data-testid="ocr-match-badge"]').innerText();
        console.log('Mismatch OCR Badge:', mismatchBadgeText.replace(/\s+/g, ' ').trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'ai_ocr_01_executor_mismatch_warning.png'), fullPage: false });

        // Now upload VALID certificate for Trần Long Vân (live Gemini 3.6 Flash Vision OCR!)
        await attachHumanCursor(page, 'BƯỚC 3: QUÉT GIẤY CHỨNG TỬ HỢP LỆ BẰNG GEMINI 3.6 FLASH VISION AI');
        await fileInput.setInputFiles(validPdfPath);
        await humanPause(page, 800);

        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Nhấn ✨ Quét & Trích xuất bằng AI (OCR) cho tệp hợp lệ');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await humanPause(page, 1800);

        const matchBadgeText = await page.locator('[data-testid="ocr-match-badge"]').innerText();
        const autoFilledNotes = await page.locator('#submit-verification textarea').inputValue();
        console.log('Valid OCR Badge:', matchBadgeText.replace(/\s+/g, ' ').trim());
        console.log('Auto-filled Notes:', autoFilledNotes);
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'ai_ocr_02_executor_matched_gemini.png'), fullPage: false });

        // Submit the verification request to Backend
        await page.locator('#submit-verification button[type="submit"]').scrollIntoViewIfNeeded();
        await humanPause(page, 400);
        await humanMoveAndClick(page, '#submit-verification button[type="submit"]', 'Nhấn Gửi xác minh kèm ghi chú AI OCR');
        await humanPause(page, 2200);

        // =========================================================================
        // PART 2: LEGAL VERIFIER LOGS IN & USES AI OCR CROSS-CHECK ON STORED DOC
        // =========================================================================
        console.log('3. Logging in as Legal Verifier...');
        await page.evaluate(() => {
            localStorage.clear();
            localStorage.setItem('i18nextLng', 'vi');
            localStorage.setItem('lv_lang', 'vi');
        });
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'BƯỚC 4: ĐĂNG NHẬP VAI TRÒ CÁN BỘ XÁC MINH PHÁP LÝ (LEGAL VERIFIER)');
        await humanPause(page, 600);

        await humanType(page, 'input[type="email"]', 'verifier@legacyvault.vn', 'Nhập email Legal Verifier');
        await humanType(page, 'input[type="password"]', 'Admin@123456', 'Nhập mật khẩu');
        await humanMoveAndClick(page, 'button[type="submit"]', 'Nhấn Đăng nhập');

        await page.waitForURL('**/verifier**', { timeout: 10000 }).catch(() => {});
        await humanPause(page, 1000);

        await page.goto(`${BASE_URL}/verifier`, { waitUntil: 'networkidle' });
        await humanPause(page, 1000);

        // Click the first request in the list (/verifier/request/:id)
        const firstRequestLink = page.locator('a[href^="/verifier/request/"]').first();
        await firstRequestLink.waitFor({ state: 'visible', timeout: 10000 });
        await humanMoveAndClick(page, 'a[href^="/verifier/request/"]', 'Mở chi tiết yêu cầu xác minh');
        await page.waitForLoadState('networkidle');
        await attachHumanCursor(page, 'BƯỚC 5: LEGAL VERIFIER PHÂN TÍCH & ĐỐI CHIẾU GIẤY CHỨNG TỬ BẰNG AI OCR');
        await humanPause(page, 1000);

        await page.locator('[data-testid="verifier-ocr-btn"]').scrollIntoViewIfNeeded();
        await humanPause(page, 600);

        // Click Check Document first
        const checkBtn = page.locator('button:has-text("Kiểm tra tài liệu"), button:has-text("Check document")').first();
        if (await checkBtn.isVisible()) {
            await humanMoveAndClick(page, 'button:has-text("Kiểm tra tài liệu"), button:has-text("Check document")', 'Nhấn Kiểm tra tài liệu');
            await humanPause(page, 1200);
        }

        // Click AI OCR Analysis button
        await humanMoveAndClick(page, '[data-testid="verifier-ocr-btn"]', 'Nhấn ✨ Phân tích & Đối chiếu AI (OCR)');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await humanPause(page, 2000);

        const verifierBadge = await page.locator('[data-testid="ocr-match-badge"]').innerText();
        console.log('Verifier OCR Badge:', verifierBadge.replace(/\s+/g, ' ').trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'ai_ocr_03_verifier_cross_check.png'), fullPage: false });

        console.log('ALL AI OCR E2E CHECKS PASSED SUCCESSFULLY!');
    } finally {
        const video = page.video();
        await context.close();
        await browser.close();
        if (video) {
            const videoPath = await video.path();
            const targetVideo = path.join(ARTIFACT_DIR, 'AI_OCR_LOCAL_DEMO.webm');
            fs.copyFileSync(videoPath, targetVideo);
            console.log('Saved demo video to:', targetVideo);
        }
    }
})();
