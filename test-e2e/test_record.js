const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function testRecord() {
    const videoDir = path.join(__dirname, 'videos');
    if (!fs.existsSync(videoDir)) fs.mkdirSync(videoDir, { recursive: true });

    console.log('Launching browser...');
    const browser = await chromium.launch({
        channel: 'chrome',
        headless: true
    });

    const context = await browser.newContext({
        recordVideo: {
            dir: videoDir,
            size: { width: 1280, height: 720 }
        },
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();
    console.log('Navigating to https://legacyvault.codes...');
    await page.goto('https://legacyvault.codes', { waitUntil: 'networkidle' });

    console.log('Page title:', await page.title());
    await page.waitForTimeout(3000);

    await context.close();
    await browser.close();
    console.log('Recording completed. Videos saved in:', videoDir);
}

testRecord().catch(err => {
    console.error('Error:', err);
    process.exit(1);
});
