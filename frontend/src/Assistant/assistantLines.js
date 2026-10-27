// Câu thoại có sẵn của Vaulty (không gọi AI)

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

function timeGreeting() {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 18) return 'Good afternoon';
    return 'Good evening';
}

const GREETINGS = [
    (n) => `${timeGreeting()}, ${n}! Welcome back to LegacyVault 👋`,
    (n) => `Hi ${n}! I'm Vaulty. Click me if you need help.`,
    (n) => `Hello ${n}! Good to see you again.`,
];

// Key trùng với role trong JWT
const TIPS = {
    COMMON: [
        'Click me if you have any questions!',
        'You can open "My profile" from your name in the header.',
        'Never share your password or OTP codes with anyone.',
    ],
    OWNER: [
        "Don't forget your Dead Man's Switch check-in!",
        'Each vault needs a beneficiary. Have you assigned one?',
        'A backup executor takes over if the primary does not respond.',
        'Secrets are encrypted before they are stored.',
    ],
    EXECUTOR: [
        'Check "Executor duties" for new cases.',
        'Acknowledge a new notice so the hand-over can start.',
        'You can message the Legal Verifier inside each case.',
    ],
    LEGAL_VERIFIER: [
        'View and check the document before approving.',
        'Approved requests still need your OTP signature.',
        'Pending requests are waiting in "Verification requests".',
    ],
    BENEFICIARY: [
        'Inherited vaults must be claimed within 60 days.',
        'Verify your identity to see the assets left for you.',
    ],
    ADMIN: [
        'Check "Alerts" for suspicious activity.',
        'The audit log keeps every security-relevant action.',
    ],
};

export const pickGreeting = (name) => pick(GREETINGS)(name || 'there');

export function pickTip(roles = []) {
    return pick([...TIPS.COMMON, ...roles.flatMap((r) => TIPS[r] || [])]);
}
