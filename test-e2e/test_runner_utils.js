const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'https://legacyvault.codes';
const ARTIFACTS_DIR = path.resolve(__dirname, '..', 'test-artifacts');
const VIDEOS_DIR = path.join(ARTIFACTS_DIR, 'videos');
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, 'screenshots');

if (!fs.existsSync(VIDEOS_DIR)) fs.mkdirSync(VIDEOS_DIR, { recursive: true });
if (!fs.existsSync(SCREENSHOTS_DIR)) fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });

async function runTestCase({ id, name, role, fn }) {
    console.log(`\n============================================================`);
    console.log(`[START] ${id}: ${name} (Role: ${role})`);
    console.log(`============================================================`);

    const browser = await chromium.launch({
        channel: 'chrome',
        headless: true
    });

    // Temporary video directory for this run
    const tempVideoDir = path.join(VIDEOS_DIR, `temp_${id}`);
    if (!fs.existsSync(tempVideoDir)) fs.mkdirSync(tempVideoDir, { recursive: true });

    const context = await browser.newContext({
        recordVideo: {
            dir: tempVideoDir,
            size: { width: 1280, height: 720 }
        },
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();
    const steps = [];
    let status = 'PASSED';
    let errorMessage = null;

    const runner = {
        page,
        async step(description, actionFn) {
            const stepNum = steps.length + 1;
            console.log(`  Step ${stepNum}: ${description}`);
            try {
                await actionFn();
                steps.push({ stepNum, description, status: 'PASSED' });
                await page.waitForTimeout(600); // Visual pacing for video playback
            } catch (err) {
                console.error(`  [FAILED] Step ${stepNum}: ${err.message}`);
                steps.push({ stepNum, description, status: 'FAILED', error: err.message });
                throw err;
            }
        },
        async screenshot(label) {
            const ssPath = path.join(SCREENSHOTS_DIR, `${id}_${label}.png`);
            await page.screenshot({ path: ssPath, fullPage: false });
            return ssPath;
        }
    };

    try {
        await fn(runner);
    } catch (err) {
        status = 'FAILED';
        errorMessage = err.message;
        try {
            await runner.screenshot('error');
        } catch (_) {}
    } finally {
        const video = page.video();
        await context.close();
        await browser.close();

        // Move/rename video file
        let videoFile = null;
        if (video) {
            const rawPath = await video.path();
            const cleanName = name.replace(/[^a-zA-Z0-9_\u00C0-\u024F\u1E00-\u1EFF]/g, '_').substring(0, 40);
            const targetPath = path.join(VIDEOS_DIR, `${id}_${cleanName}.webm`);
            try {
                if (fs.existsSync(targetPath)) fs.unlinkSync(targetPath);
                fs.renameSync(rawPath, targetPath);
                videoFile = `${id}_${cleanName}.webm`;
                console.log(`[VIDEO SAVED] ${videoFile}`);
            } catch (renameErr) {
                console.warn(`Could not rename video: ${renameErr.message}`);
                videoFile = path.basename(rawPath);
            }
        }
        try {
            if (fs.existsSync(tempVideoDir)) fs.rmdirSync(tempVideoDir, { recursive: true });
        } catch (_) {}

        console.log(`[FINISH] ${id} => ${status}`);
        return {
            id,
            name,
            role,
            status,
            errorMessage,
            steps,
            videoFile
        };
    }
}

module.exports = {
    BASE_URL,
    ARTIFACTS_DIR,
    VIDEOS_DIR,
    SCREENSHOTS_DIR,
    runTestCase
};
