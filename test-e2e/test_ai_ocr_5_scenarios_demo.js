/**
 * AUTOMATED E2E DEMO & VIDEO RECORDING OF ALL 5 AI OCR ADVISORY SCENARIOS
 * Scenarios tested:
 *  1. giay_chung_tu_sai_ten.pdf           -> Sai họ tên Chủ két (15% death proof)
 *  2. giay_chung_tu_mo_nhoe.pdf           -> Ảnh mờ nhòe (48% image clarity)
 *  3. giay_bao_tu_benh_vien.pdf           -> Tài liệu liên quan - Giấy báo tử y tế (76% death proof)
 *  4. giay_chung_tu_gia_mao_nam_sinh.pdf  -> Giả mạo tinh vi: Đúng mẫu, rõ nét 96%, đúng tên TRẦN LONG VÂN, nhưng Năm sinh (1975) mâu thuẫn mã số CCCD (079065... = 1965) (38% death proof, SUSPECTED_FORGERY)
 *  5. trich_luc_khai_tu_tran_long_van.pdf -> Hợp lệ chuẩn 100% (Gemini 3.6 Flash Vision AI: 96% rõ nét, 98% chứng minh qua đời)
 *  6. Legal Verifier cross-checks the submitted document with 4-criteria advisory panel
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
const VIDEO_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'videos', 'ai-ocr-5-scenarios');
const SAMPLE_DIR = path.resolve(__dirname, '..', 'test-artifacts', 'sample-docs');

if (!fs.existsSync(VIDEO_DIR)) fs.mkdirSync(VIDEO_DIR, { recursive: true });

(async () => {
    const files = {
        mismatch: path.join(SAMPLE_DIR, 'giay_chung_tu_sai_ten.pdf'),
        blurry: path.join(SAMPLE_DIR, 'giay_chung_tu_mo_nhoe.pdf'),
        related: path.join(SAMPLE_DIR, 'giay_bao_tu_benh_vien.pdf'),
        forgedSmallDetail: path.join(SAMPLE_DIR, 'giay_chung_tu_gia_mao_nam_sinh.pdf'),
        valid: path.join(SAMPLE_DIR, 'trich_luc_khai_tu_tran_long_van.pdf'),
    };

    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    const context = await browser.newContext({
        locale: 'vi-VN',
        viewport: { width: 1440, height: 920 },
        recordVideo: {
            dir: VIDEO_DIR,
            size: { width: 1440, height: 920 }
        }
    });

    const page = await context.newPage();
    await page.addInitScript(() => {
        localStorage.setItem('i18nextLng', 'vi');
        localStorage.setItem('lv_lang', 'vi');
    });

    try {
        console.log('=== BƯỚC 1: ĐĂNG NHẬP EXECUTOR ===');
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'DEMO TỰ ĐỘNG 5 KỊCH BẢN AI OCR CỐ VẤN — ĐĂNG NHẬP EXECUTOR');
        await humanPause(page, 600);

        await humanType(page, 'input[type="email"]', 'executor@legacyvault.vn', 'Nhập email Executor');
        await humanType(page, 'input[type="password"]', 'Admin@123456', 'Nhập mật khẩu');
        await humanMoveAndClick(page, 'button[type="submit"]', 'Nhấn Đăng nhập');

        await page.waitForURL('**/owner**', { timeout: 10000 }).catch(() => {});
        await humanPause(page, 700);

        await page.goto(`${BASE_URL}/executor/cases/55555555-5555-5555-5555-555555555501`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'MỞ HỒ SƠ DI SẢN CỦA CHỦ KÉT: TRẦN LONG VÂN');
        await humanPause(page, 800);

        // Uncheck the 2nd and 3rd vaults so 2 vaults remain eligible after this demo!
        const checkboxes = page.locator('#submit-verification input[type="checkbox"]');
        const cbCount = await checkboxes.count();
        if (cbCount > 1) {
            for (let i = 1; i < cbCount; i++) {
                if (await checkboxes.nth(i).isChecked()) {
                    await checkboxes.nth(i).uncheck();
                }
            }
        }

        const fileInput = page.locator('#submit-verification input[type="file"]').first();

        // -------------------------------------------------------------------------
        // SCENARIO 1: SAI HỌ TÊN CHỦ KÉT (giay_chung_tu_sai_ten.pdf)
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 1: TÀI LIỆU SAI HỌ TÊN CHỦ KÉT ===');
        await attachHumanCursor(page, 'KỊCH BẢN 1: QUÉT GIẤY CHỨNG TỬ SAI HỌ TÊN (NGUYỄN VĂN KHÁC vs TRẦN LONG VÂN)');
        await fileInput.setInputFiles(files.mismatch);
        await humanPause(page, 500);
        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Quét AI OCR tệp sai tên');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 1600);
        console.log('  -> Badge:', (await page.locator('[data-testid="ocr-match-badge"]').innerText()).trim());
        console.log('  -> Death Proof %:', (await page.locator('[data-testid="ocr-death-proof-percent"]').innerText()).trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_01_sai_ten.png'), fullPage: false });

        // -------------------------------------------------------------------------
        // SCENARIO 2: ẢNH CHỤP MỜ NHÒE (giay_chung_tu_mo_nhoe.pdf)
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 2: ẢNH CHỤP MỜ NHÒE / ĐỘ PHÂN GIẢI THẤP ===');
        await attachHumanCursor(page, 'KỊCH BẢN 2: QUÉT TÀI LIỆU BỊ MỜ NHÒE (ĐỘ RÕ NÉT 48% — GỢI Ý CHỤP LẠI)');
        await fileInput.setInputFiles(files.blurry);
        await humanPause(page, 500);
        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Quét AI OCR tệp ảnh mờ nhòe');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 1600);
        console.log('  -> Badge:', (await page.locator('[data-testid="ocr-match-badge"]').innerText()).trim());
        console.log('  -> Image Quality %:', (await page.locator('[data-testid="ocr-image-quality-score"]').innerText()).trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_02_anh_mo_nhoe.png'), fullPage: false });

        // -------------------------------------------------------------------------
        // SCENARIO 3: TÀI LIỆU LIÊN QUAN - GIẤY BÁO TỬ BỆNH VIỆN (giay_bao_tu_benh_vien.pdf)
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 3: TÀI LIỆU Y TẾ LIÊN QUAN (GIẤY BÁO TỬ BỆNH VIỆN) ===');
        await attachHumanCursor(page, 'KỊCH BẢN 3: QUÉT GIẤY BÁO TỬ Y TẾ (TIÊU ĐỀ LIÊN QUAN — CHỨNG MINH QUA ĐỜI 76%)');
        await fileInput.setInputFiles(files.related);
        await humanPause(page, 500);
        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Quét AI OCR tệp Giấy báo tử bệnh viện');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 1600);
        console.log('  -> Badge:', (await page.locator('[data-testid="ocr-match-badge"]').innerText()).trim());
        console.log('  -> Header Title:', (await page.locator('[data-testid="ocr-detected-header"]').innerText()).trim());
        console.log('  -> Death Proof %:', (await page.locator('[data-testid="ocr-death-proof-percent"]').innerText()).trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_03_giay_bao_tu_lien_quan.png'), fullPage: false });

        // -------------------------------------------------------------------------
        // SCENARIO 4: GIẢ MẠO TINH VI - ĐÚNG TÊN NHƯNG SỬA NĂM SINH / NƠI Ở (giay_chung_tu_gia_mao_nam_sinh.pdf)
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 4: GIẢ MẠO TINH VI (ĐÚNG TÊN NHƯNG MÂU THUẪN NĂM SINH ↔ SỐ CCCD) ===');
        await attachHumanCursor(page, 'KỊCH BẢN 4: PHÁT HIỆN GIẢ MẠO TINH VI (ĐÚNG TÊN NHƯNG NĂM SINH 1975 MÂU THUẪN MÃ CCCD 079065...)');
        await fileInput.setInputFiles(files.forgedSmallDetail);
        await humanPause(page, 500);
        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Quét AI OCR tệp giả mạo chi tiết nhỏ');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 2000);
        console.log('  -> Badge:', (await page.locator('[data-testid="ocr-match-badge"]').innerText()).trim());
        console.log('  -> Death Proof %:', (await page.locator('[data-testid="ocr-death-proof-percent"]').innerText()).trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_04_gia_mao_nam_sinh_cccd.png'), fullPage: false });

        // -------------------------------------------------------------------------
        // SCENARIO 5: HỢP LỆ 100% VỚI GEMINI 3.6 FLASH VISION AI (trich_luc_khai_tu_tran_long_van.pdf)
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 5: TRÍCH LỤC KHAI TỬ HỢP LỆ 100% (GEMINI 3.6 FLASH VISION AI) ===');
        await attachHumanCursor(page, 'KỊCH BẢN 5: QUÉT TRÍCH LỤC KHAI TỬ HỢP LỆ 100% BẰNG GEMINI 3.6 FLASH & GỬI HỒ SƠ');
        await fileInput.setInputFiles(files.valid);
        await humanPause(page, 500);
        await humanMoveAndClick(page, '[data-testid="executor-ocr-btn"]', 'Quét AI OCR tệp hợp lệ bằng Gemini 3.6 Flash');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 1800);
        console.log('  -> Badge:', (await page.locator('[data-testid="ocr-match-badge"]').innerText()).trim());
        console.log('  -> Death Proof %:', (await page.locator('[data-testid="ocr-death-proof-percent"]').innerText()).trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_05_hop_le_100_gemini.png'), fullPage: false });

        // Click "Chèn tóm tắt gợi ý AI vào Ghi chú nộp hồ sơ" and Submit
        const applyNoteBtn = page.locator('[data-testid="ocr-apply-note-btn"]');
        if (await applyNoteBtn.isVisible()) {
            await humanMoveAndClick(page, '[data-testid="ocr-apply-note-btn"]', 'Chèn tóm tắt gợi ý AI vào Ghi chú');
            await humanPause(page, 600);
        }
        await page.locator('#submit-verification button[type="submit"]').scrollIntoViewIfNeeded();
        await humanMoveAndClick(page, '#submit-verification button[type="submit"]', 'Gửi yêu cầu xác minh (vẫn thuộc quyền quyết định của Executor)');
        await humanPause(page, 2000);

        // -------------------------------------------------------------------------
        // SCENARIO 6: LEGAL VERIFIER ĐỐI CHIẾU CHÉO BẰNG BẢNG CỐ VẤN AI 4 TIÊU CHÍ
        // -------------------------------------------------------------------------
        console.log('=== KỊCH BẢN 6: LEGAL VERIFIER PHÂN TÍCH ĐỘC LẬP BẰNG AI OCR ===');
        await page.evaluate(() => {
            localStorage.clear();
            localStorage.setItem('i18nextLng', 'vi');
            localStorage.setItem('lv_lang', 'vi');
        });
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
        await attachHumanCursor(page, 'KỊCH BẢN 6: LEGAL VERIFIER ĐĂNG NHẬP & THẨM ĐỊNH BẰNG BẢNG CỐ VẤN AI 4 TIÊU CHÍ');
        await humanPause(page, 600);

        await humanType(page, 'input[type="email"]', 'verifier@legacyvault.vn', 'Nhập email Legal Verifier');
        await humanType(page, 'input[type="password"]', 'Admin@123456', 'Nhập mật khẩu');
        await humanMoveAndClick(page, 'button[type="submit"]', 'Nhấn Đăng nhập');

        await page.waitForURL('**/verifier**', { timeout: 10000 }).catch(() => {});
        await humanPause(page, 800);
        await page.goto(`${BASE_URL}/verifier`, { waitUntil: 'networkidle' });
        await humanPause(page, 800);

        const firstRequestLink = page.locator('a[href^="/verifier/request/"]').first();
        await firstRequestLink.waitFor({ state: 'visible', timeout: 10000 });
        await humanMoveAndClick(page, 'a[href^="/verifier/request/"]', 'Mở chi tiết hồ sơ xác minh vừa nộp');
        await page.waitForLoadState('networkidle');
        await humanPause(page, 800);

        await page.locator('[data-testid="verifier-ocr-btn"]').scrollIntoViewIfNeeded();
        await humanMoveAndClick(page, '[data-testid="verifier-ocr-btn"]', 'Nhấn ✨ Phân tích & Đối chiếu AI (OCR) cho Verifier');
        await page.waitForSelector('[data-testid="ocr-analysis-card"]', { timeout: 15000 });
        await page.locator('[data-testid="ocr-analysis-card"]').scrollIntoViewIfNeeded();
        await humanPause(page, 2000);

        console.log('  -> Verifier Suggestion:', (await page.locator('[data-testid="ocr-role-suggestion"]').innerText()).replace(/\s+/g, ' ').trim());
        await page.screenshot({ path: path.join(ARTIFACT_DIR, 'demo_06_verifier_advisory_ocr.png'), fullPage: false });

        console.log('=== HOÀN TẤT 100% 6 KỊCH BẢN DEMO & GHI HÌNH ===');
    } finally {
        const video = page.video();
        await context.close();
        await browser.close();
        if (video) {
            const videoPath = await video.path();
            const targetVideo = path.join(ARTIFACT_DIR, 'AI_OCR_5_SCENARIOS_DEMO.webm');
            fs.copyFileSync(videoPath, targetVideo);
            console.log('Saved full 5-scenario demo video to:', targetVideo);
        }
    }
})();
