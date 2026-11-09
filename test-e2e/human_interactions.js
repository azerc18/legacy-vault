// Helper for realistic, human-like browser interactions and visible mouse pointer

async function attachHumanCursor(page) {
    await page.addInitScript(() => {
        const init = () => {
            if (document.getElementById('human-cursor-dot')) return;

            const cursor = document.createElement('div');
            cursor.id = 'human-cursor-dot';
            cursor.style.position = 'fixed';
            cursor.style.top = '0';
            cursor.style.left = '0';
            cursor.style.width = '22px';
            cursor.style.height = '22px';
            cursor.style.borderRadius = '50%';
            cursor.style.backgroundColor = 'rgba(239, 68, 68, 0.9)'; // High-visibility red dot
            cursor.style.border = '2.5px solid #ffffff';
            cursor.style.boxShadow = '0 0 10px rgba(0,0,0,0.6), 0 0 16px rgba(239, 68, 68, 0.6)';
            cursor.style.pointerEvents = 'none';
            cursor.style.zIndex = '2147483647';
            cursor.style.transform = 'translate(-50%, -50%)';
            cursor.style.transition = 'transform 0.12s cubic-bezier(0.2, 0.8, 0.2, 1), background-color 0.15s';
            document.documentElement.appendChild(cursor);

            // Click ripple ring
            const ripple = document.createElement('div');
            ripple.id = 'human-cursor-ripple';
            ripple.style.position = 'fixed';
            ripple.style.top = '0';
            ripple.style.left = '0';
            ripple.style.width = '46px';
            ripple.style.height = '46px';
            ripple.style.borderRadius = '50%';
            ripple.style.border = '2.5px solid rgba(16, 185, 129, 0.95)'; // Emerald ring on click
            ripple.style.pointerEvents = 'none';
            ripple.style.zIndex = '2147483646';
            ripple.style.transform = 'translate(-50%, -50%) scale(0)';
            ripple.style.opacity = '0';
            ripple.style.transition = 'transform 0.3s ease-out, opacity 0.3s ease-out';
            document.documentElement.appendChild(ripple);

            window.addEventListener('mousemove', (e) => {
                cursor.style.left = `${e.clientX}px`;
                cursor.style.top = `${e.clientY}px`;
                ripple.style.left = `${e.clientX}px`;
                ripple.style.top = `${e.clientY}px`;
            });

            window.addEventListener('mousedown', () => {
                cursor.style.backgroundColor = 'rgba(16, 185, 129, 0.95)'; // turns emerald
                cursor.style.transform = 'translate(-50%, -50%) scale(0.65)';
                ripple.style.transform = 'translate(-50%, -50%) scale(1.4)';
                ripple.style.opacity = '1';
            });

            window.addEventListener('mouseup', () => {
                cursor.style.backgroundColor = 'rgba(239, 68, 68, 0.9)';
                cursor.style.transform = 'translate(-50%, -50%) scale(1)';
                ripple.style.opacity = '0';
                setTimeout(() => {
                    ripple.style.transform = 'translate(-50%, -50%) scale(0)';
                }, 300);
            });
        };

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
        } else {
            init();
        }
    });
}

async function humanMoveAndClick(page, target, { pauseBefore = 400, pauseAfter = 1600 } = {}) {
    try {
        let locator = typeof target === 'string' ? page.locator(target).first() : target;
        const exists = await locator.count().catch(() => 0);
        if (exists === 0) return;

        await locator.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
        const box = await locator.boundingBox().catch(() => null);
        
        if (box) {
            const targetX = box.x + box.width / 2;
            const targetY = box.y + box.height / 2;
            await page.mouse.move(targetX, targetY, { steps: 16 });
            await page.waitForTimeout(pauseBefore);
            await page.mouse.down();
            await page.waitForTimeout(80);
            await page.mouse.up();
            await locator.click({ force: true }).catch(() => {});
        } else {
            await locator.click({ force: true }).catch(() => {});
        }
    } catch (_) {}
    await page.waitForTimeout(pauseAfter);
}

async function humanType(page, target, text, { delay = 50, pauseAfter = 1000 } = {}) {
    try {
        let locator = typeof target === 'string' ? page.locator(target).first() : target;
        const exists = await locator.count().catch(() => 0);
        if (exists === 0) return;

        await locator.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
        const box = await locator.boundingBox().catch(() => null);
        
        if (box) {
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 14 });
            await page.waitForTimeout(250);
        }
        await locator.click({ force: true }).catch(() => {});
        await locator.fill('').catch(() => {});
        await page.waitForTimeout(150);
        
        // Type realistically character by character
        await page.keyboard.type(text, { delay });
    } catch (_) {}
    await page.waitForTimeout(pauseAfter);
}

async function humanSelectOption(page, target, valueOrLabel, { pauseBefore = 500, pauseAfter = 2000 } = {}) {
    try {
        let locator = typeof target === 'string' ? page.locator(target).first() : target;
        const exists = await locator.count().catch(() => 0);
        if (exists === 0) return;

        await locator.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
        const box = await locator.boundingBox().catch(() => null);
        
        if (box) {
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 14 });
            await page.waitForTimeout(pauseBefore);
            await locator.click({ force: true }).catch(() => {});
            await page.waitForTimeout(400);
        }

        // Try select by value first, then by label
        try {
            await locator.selectOption(valueOrLabel);
        } catch (_) {
            await locator.selectOption({ label: valueOrLabel }).catch(() => {});
        }
    } catch (_) {}
    await page.waitForTimeout(pauseAfter);
}

async function humanCheck(page, target, { pauseBefore = 300, pauseAfter = 1200 } = {}) {
    try {
        let locator = typeof target === 'string' ? page.locator(target).first() : target;
        const exists = await locator.count().catch(() => 0);
        if (exists === 0) return;

        await locator.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
        const box = await locator.boundingBox().catch(() => null);
        if (box) {
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 12 });
            await page.waitForTimeout(pauseBefore);
        }
        await locator.check({ force: true }).catch(() => {});
    } catch (_) {}
    await page.waitForTimeout(pauseAfter);
}

async function humanHover(page, target, { steps = 14, pause = 1500 } = {}) {
    try {
        let locator = typeof target === 'string' ? page.locator(target).first() : target;
        const exists = await locator.count().catch(() => 0);
        if (exists === 0) return;

        await locator.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
        const box = await locator.boundingBox().catch(() => null);
        if (box) {
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps });
            await page.waitForTimeout(pause);
        }
    } catch (_) {}
}

async function humanScroll(page, deltaY, { pauseAfter = 2000 } = {}) {
    try {
        await page.evaluate((y) => {
            window.scrollBy({ top: y, behavior: 'smooth' });
        }, deltaY);
    } catch (_) {}
    await page.waitForTimeout(pauseAfter);
}

async function humanPause(page, ms = 2500) {
    await page.waitForTimeout(ms);
}

module.exports = {
    attachHumanCursor,
    humanMoveAndClick,
    humanType,
    humanSelectOption,
    humanCheck,
    humanHover,
    humanScroll,
    humanPause
};
