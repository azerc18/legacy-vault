const { BASE_URL, runTestCase } = require('./test_runner_utils');

async function runSuite1() {
    const results = [];

    // TC01: Guest Landing Page & Navigation
    results.push(await runTestCase({
        id: 'TC01',
        name: 'Guest Landing Page & Navigation',
        role: 'Khách vãng lai (Guest)',
        fn: async ({ page, step, screenshot }) => {
            await step('Truy cập trang chủ LegacyVault (https://legacyvault.codes)', async () => {
                await page.goto(BASE_URL, { waitUntil: 'networkidle' });
            });
            await screenshot('01_landing_hero');

            await step('Kiểm tra tiêu đề và nội dung phần Hero', async () => {
                const title = await page.title();
                console.log(`    Tiêu đề trang: "${title}"`);
                const brand = page.locator('text=LegacyVault').first();
                await brand.waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Cuộn trang xuống kiểm tra các phần Tính năng, Quy trình và Bảng giá', async () => {
                await page.evaluate(() => window.scrollTo(0, 800));
                await page.waitForTimeout(1000);
                await page.evaluate(() => window.scrollTo(0, 1600));
                await page.waitForTimeout(1000);
                await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
                await page.waitForTimeout(1000);
            });
            await screenshot('02_landing_footer');

            await step('Cuộn lại lên đầu trang và kiểm tra các nút điều hướng Header', async () => {
                await page.evaluate(() => window.scrollTo(0, 0));
                await page.waitForTimeout(500);
                const loginLink = page.locator('a[href="/login"]').first();
                const registerLink = page.locator('a[href="/register"]').first();
                await loginLink.waitFor({ state: 'visible' });
                await registerLink.waitFor({ state: 'visible' });
            });
            await screenshot('03_landing_header_ready');
        }
    }));

    // TC02: Language Switch (Song ngữ VI/EN)
    results.push(await runTestCase({
        id: 'TC02',
        name: 'Chuyển đổi ngôn ngữ Tiếng Việt và Tiếng Anh',
        role: 'Khách vãng lai (Guest)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang chủ LegacyVault', async () => {
                await page.goto(BASE_URL, { waitUntil: 'networkidle' });
            });

            await step('Xác định nút chuyển đổi ngôn ngữ trên Header', async () => {
                const langBtn = page.locator('button[aria-label*="language" i], button:has-text("VI"), button:has-text("EN")').first();
                await langBtn.waitFor({ state: 'visible' });
            });
            await screenshot('01_before_lang_switch');

            await step('Nhấn nút đổi ngôn ngữ sang Tiếng Việt / Tiếng Anh', async () => {
                const langBtn = page.locator('button[aria-label*="language" i], button:has-text("VI"), button:has-text("EN")').first();
                await langBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('02_after_first_lang_switch');

            await step('Nhấn chuyển đổi lại ngôn ngữ để kiểm tra tính nhất quán', async () => {
                const langBtn = page.locator('button[aria-label*="language" i], button:has-text("VI"), button:has-text("EN")').first();
                await langBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('03_lang_switched_back');
        }
    }));

    // TC03: Dark Mode / Light Mode Toggle
    results.push(await runTestCase({
        id: 'TC03',
        name: 'Chuyển đổi giao diện Sáng Tối (Dark Light Mode)',
        role: 'Khách vãng lai (Guest)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang chủ LegacyVault', async () => {
                await page.goto(BASE_URL, { waitUntil: 'networkidle' });
            });

            await step('Xác định nút chuyển đổi Theme (Dark/Light)', async () => {
                const themeBtn = page.locator('button[aria-label*="theme" i], button[aria-label*="chế độ" i], button:has(.material-symbols-outlined)').filter({ hasText: /dark_mode|light_mode/ }).first();
                if (await themeBtn.isVisible()) {
                    await themeBtn.click();
                    await page.waitForTimeout(1000);
                } else {
                    console.log('    Tìm nút theme qua selector dự phòng');
                    const anyThemeBtn = page.locator('header button').last();
                    await anyThemeBtn.click();
                    await page.waitForTimeout(1000);
                }
            });
            await screenshot('01_toggled_theme');

            await step('Bấm nút chuyển đổi lại giao diện ban đầu', async () => {
                const themeBtn = page.locator('button[aria-label*="theme" i], button[aria-label*="chế độ" i], header button').first();
                await themeBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('02_restored_theme');
        }
    }));

    // TC04: Đăng ký - Form Validation
    results.push(await runTestCase({
        id: 'TC04',
        name: 'Form Đăng ký - Kiểm tra Validation',
        role: 'Người dùng mới (Public)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang Đăng ký tài khoản (/register)', async () => {
                await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle' });
            });
            await screenshot('01_register_empty_form');

            await step('Nhấn nút "Tạo tài khoản" khi chưa điền dữ liệu', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
            });
            await screenshot('02_register_empty_error');

            await step('Kiểm tra thông báo lỗi yêu cầu điền đầy đủ các trường', async () => {
                const alertEl = page.locator('div[role="alert"]').first();
                await alertEl.waitFor({ state: 'visible', timeout: 5000 });
            });

            await step('Điền "Trần Long Vân" vào trường Họ và tên', async () => {
                await page.fill('input[name="fullName"]', 'Trần Long Vân');
            });

            await step('Điền "tlv48691@gmail.com" vào trường Email', async () => {
                await page.fill('input[name="email"]', 'tlv48691@gmail.com');
            });

            await step('Điền mật khẩu yếu "123456" vào ô Mật khẩu', async () => {
                await page.fill('#reg-password', '123456');
                await page.fill('#reg-confirm', '123456');
            });
            await screenshot('03_register_weak_password');

            await step('Nhấn nút "Tạo tài khoản" với mật khẩu yếu để kiểm tra thanh đo độ mạnh', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
                await page.waitForTimeout(800);
            });
            await screenshot('04_register_weak_error');

            await step('Điền mật khẩu xác nhận không khớp để kiểm tra thông báo lỗi mismatch', async () => {
                await page.fill('#reg-password', 'ValidPassword@2026');
                await page.fill('#reg-confirm', 'DifferentPassword@2026');
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
                await page.waitForTimeout(800);
            });
            await screenshot('05_register_mismatch_error');
        }
    }));

    // TC05: Đăng ký thành công & Chuyển sang Xác thực Email
    results.push(await runTestCase({
        id: 'TC05',
        name: 'Đăng ký tài khoản thành công chuyển sang Verify Email',
        role: 'Người dùng mới (Public)',
        fn: async ({ page, step, screenshot }) => {
            const uniqueEmail = `test_owner_${Date.now()}@testvault.com`;

            await step('Mở trang Đăng ký tài khoản (/register)', async () => {
                await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle' });
            });

            await step('Điền "Trần Long Vân" vào ô Họ và tên', async () => {
                await page.fill('input[name="fullName"]', 'Trần Long Vân');
            });

            await step(`Điền email mới "${uniqueEmail}" vào ô Email`, async () => {
                await page.fill('input[name="email"]', uniqueEmail);
            });

            await step('Điền mật khẩu đạt chuẩn "LegacyVault@2026!" vào ô Mật khẩu', async () => {
                await page.fill('#reg-password', 'LegacyVault@2026!');
            });

            await step('Điền lại "LegacyVault@2026!" vào ô Xác nhận mật khẩu', async () => {
                await page.fill('#reg-confirm', 'LegacyVault@2026!');
            });
            await screenshot('01_register_form_filled');

            await step('Nhấn nút "Tạo tài khoản" để gửi yêu cầu đăng ký', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
            });

            await step('Chờ hệ thống xử lý và chuyển hướng sang trang /verify-email', async () => {
                await page.waitForURL('**/verify-email', { timeout: 15000 });
                console.log(`    Đã chuyển sang: ${page.url()}`);
            });
            await screenshot('02_navigated_to_verify_email');
        }
    }));

    // TC06: Màn hình Xác thực Email (Verify Email Screen)
    results.push(await runTestCase({
        id: 'TC06',
        name: 'Màn hình Xác thực Email - Kiểm tra Cooldown & OTP format',
        role: 'Người dùng mới (Public)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang Xác thực Email (/verify-email)', async () => {
                await page.goto(`${BASE_URL}/verify-email`, { waitUntil: 'networkidle' });
            });
            await screenshot('01_verify_email_page');

            await step('Nhập email "tlv48691@gmail.com" và mã OTP chưa đủ 6 chữ số "123"', async () => {
                const emailInput = page.locator('input[type="email"]').first();
                if (await emailInput.isVisible()) {
                    await emailInput.fill('tlv48691@gmail.com');
                }
                const otpInput = page.locator('input[placeholder*="mã" i], input[placeholder*="code" i], input[inputmode="numeric"]').first();
                await otpInput.fill('123');
            });

            await step('Nhấn nút "Xác thực email" để kiểm tra cảnh báo độ dài OTP', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
            });
            await screenshot('02_otp_length_error');

            await step('Nhập mã OTP sai 6 số "999999" và nhấn "Xác thực email"', async () => {
                const otpInput = page.locator('input[placeholder*="mã" i], input[placeholder*="code" i], input[inputmode="numeric"]').first();
                await otpInput.fill('999999');
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('03_otp_invalid_api_error');

            await step('Nhấn liên kết "Quay lại đăng nhập" (Back to Login)', async () => {
                const backLink = page.locator('a[href="/login"]').first();
                await backLink.click();
                await page.waitForURL('**/login', { timeout: 5000 });
            });
            await screenshot('04_back_to_login_screen');
        }
    }));

    // TC07: Đăng nhập với thông tin không hợp lệ
    results.push(await runTestCase({
        id: 'TC07',
        name: 'Đăng nhập thất bại khi sai email hoặc mật khẩu',
        role: 'Người dùng đã có tài khoản (User)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang Đăng nhập (/login)', async () => {
                await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
            });
            await screenshot('01_login_page');

            await step('Nhấn nút "Đăng nhập" khi để trống form để kiểm tra validation', async () => {
                const loginBtn = page.locator('button[type="submit"]');
                await loginBtn.click();
            });
            await screenshot('02_login_required_error');

            await step('Điền email không tồn tại "unknown_user_999@test.com" vào ô Email', async () => {
                await page.fill('input[type="email"]', 'unknown_user_999@test.com');
            });

            await step('Điền mật khẩu sai "WrongPass@999" vào ô Mật khẩu', async () => {
                await page.fill('input[type="password"]', 'WrongPass@999');
            });
            await screenshot('03_login_filled_wrong_credentials');

            await step('Nhấn nút "Đăng nhập" và kiểm tra thông báo cảnh báo lỗi', async () => {
                const loginBtn = page.locator('button[type="submit"]');
                await loginBtn.click();
                await page.waitForTimeout(1000);
            });
            await screenshot('04_login_invalid_alert');
        }
    }));

    // TC08: Quên mật khẩu (Forgot Password)
    results.push(await runTestCase({
        id: 'TC08',
        name: 'Yêu cầu đặt lại mật khẩu khi quên mật khẩu',
        role: 'Người dùng đã có tài khoản (User)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang Đăng nhập (/login) và nhấn liên kết "Quên mật khẩu?"', async () => {
                await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
                const forgotLink = page.locator('a[href="/forgot-password"]').first();
                await forgotLink.click();
                await page.waitForURL('**/forgot-password', { timeout: 5000 });
            });
            await screenshot('01_forgot_password_screen');

            await step('Nhấn nút "Gửi mã xác thực" khi để trống ô email để kiểm tra validate', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
            });
            await screenshot('02_forgot_empty_error');

            await step('Điền email "tlv48691@gmail.com" vào ô Email tài khoản', async () => {
                await page.fill('#forgot-email', 'tlv48691@gmail.com');
            });
            await screenshot('03_forgot_email_filled');

            await step('Nhấn nút "Gửi mã xác thực" để hệ thống gửi OTP và chuyển sang /reset-password', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
                await page.waitForURL('**/reset-password', { timeout: 10000 });
            });
            await screenshot('04_reset_password_screen');
        }
    }));

    // TC09: Đặt lại mật khẩu (Reset Password Screen)
    results.push(await runTestCase({
        id: 'TC09',
        name: 'Màn hình Đặt lại mật khẩu - Kiểm tra Form & Validation',
        role: 'Người dùng đã có tài khoản (User)',
        fn: async ({ page, step, screenshot }) => {
            await step('Mở trang Đặt lại mật khẩu (/reset-password)', async () => {
                await page.goto(`${BASE_URL}/reset-password`, { waitUntil: 'networkidle' });
            });
            await screenshot('01_reset_password_page');

            await step('Kiểm tra các trường nhập liệu trên form Đặt lại mật khẩu', async () => {
                const emailInput = page.locator('input[type="email"]').first();
                const otpInput = page.locator('input[placeholder*="mã" i], input[placeholder*="code" i], input[inputmode="numeric"]').first();
                await otpInput.waitFor({ state: 'visible' });
            });

            await step('Nhập mật khẩu mới và mật khẩu xác nhận không trùng khớp', async () => {
                const passInputs = page.locator('input[type="password"]');
                if (await passInputs.count() >= 2) {
                    await passInputs.nth(0).fill('NewSecurePass@2026');
                    await passInputs.nth(1).fill('MismatchPass@2026');
                }
            });
            await screenshot('02_reset_password_mismatch');

            await step('Nhấn nút "Đổi mật khẩu" để kiểm tra cảnh báo lỗi không khớp', async () => {
                const submitBtn = page.locator('button[type="submit"]');
                await submitBtn.click();
                await page.waitForTimeout(500);
            });
            await screenshot('03_reset_password_error_shown');

            await step('Nhấn liên kết "Quay lại đăng nhập"', async () => {
                const backLink = page.locator('a[href="/login"]').first();
                await backLink.click();
                await page.waitForURL('**/login', { timeout: 5000 });
            });
            await screenshot('04_returned_to_login');
        }
    }));

    return results;
}

module.exports = { runSuite1 };

if (require.main === module) {
    runSuite1().then(results => {
        console.log('\n============================================================');
        console.log(`SUITE 1 FINISHED: ${results.filter(r => r.status === 'PASSED').length}/${results.length} PASSED`);
        console.log('============================================================\n');
    }).catch(err => {
        console.error('Suite 1 Fatal Error:', err);
    });
}
