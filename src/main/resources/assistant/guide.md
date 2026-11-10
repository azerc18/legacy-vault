# LegacyVault user guide

LegacyVault stores digital assets (bank accounts, exchange accounts, crypto wallets, e-wallets, email and social media accounts, documents) and hands them over to trusted people when the owner can no longer check in (Dead Man's Switch). Secrets and files are encrypted on the server before they are stored.

Roles:
- Vault Owner: creates vaults, chooses beneficiaries and executors, sets the Dead Man's Switch.
- Executor: a trusted person who handles the hand-over after the Dead Man's Switch is triggered.
- Legal Verifier: checks the death certificate and signs to unlock vaults.
- Beneficiary: receives the unlocked vault (or a share of its assets).
- Administrator: manages users, roles, plans, payments and security.

One account can have several roles. If you were just given a new role, sign out and sign in again.

## Getting around

- The left sidebar has "Home" and "Profile", then one group per role (for example Vault Owner: "My vaults", "Dead Man's Switch", "Executors", "Activity", "Billing & VIP"). Click a group name to open or close it. "Collapse sidebar" / "Expand sidebar" makes the sidebar narrow or wide.
- The bottom of the sidebar has: the "Upgrade" / "VIP" button (Vault Owner), the EN / VI language switch, the light / dark mode button, your avatar (opens "My profile") and "Log out".
- The bell icon at the bottom of the sidebar opens "Notifications" on every page after you sign in. A red number shows how many are unread; it refreshes about every minute.
  - What appears there: being named or removed as a beneficiary, being appointed or removed as an executor, DMS check-in reminders, a missed check-in (the owner and the trusted confirmers who have an account), a confirmer confirming you are safe, an administrator pausing or resuming your DMS, asset verified or needing re-verification, payment and VIP expiry, vault unlocked, legacy messages, claim deadline reminders, executor task results and verification results.
  - Click a notification to mark it as read and open the related page. "Unread" shows only unread ones, "Mark all as read" clears the number, "Show older" loads earlier notifications.
  - Notifications follow the language chosen with the EN / VI switch. Security emails (OTP codes, password reset, new device, account lock) and the confirmer's confirmation link are sent by email only.
- Vaulty (this assistant) is the owl button. Click it to chat, drag it to move it.

### Guide and tours
- On first sign-in you may see "Are you new to Legacy Vault?". Choose "I'm a new user (Start Guide)" for an on-screen tour, or "I've used it before".
- A short tour also starts by itself the first time you open each feature (for example "Dead Man's Switch" or "Executors") in this browser. Click "Got it" or "Skip tour" to close it.
- To see the create-vault tour again, click the "Guide" button on the "My vaults" page.
- Beneficiaries: on an inherited vault, click "Not sure what to do next? Read the guide" to open "Guide for beneficiaries".

### Demo mode
- On the sign-in page, "Try Demo Mode (No Account Needed)" opens the app with sample data. Nothing is saved. Click "Exit Demo" to leave.

## Account

### Register
1. Open the sign-in page and click "Sign up" (or use the Google button: "Sign in with Google" / "Sign up with Google", when it is available).
2. Enter full name, email, password and confirm password, then click "Sign up".
3. Password rules: 8–72 characters, with at least one uppercase letter, one lowercase letter, one number and one special character. The strength bar goes from "Very weak" to "Very strong".
4. A 6-digit code is emailed to you. Continue with "Verify email".
5. The language you are using (EN / VI) is saved as your email language. You can change it later in "My profile".
6. If someone already named you as a beneficiary, register with that same email. Their vault will appear under "Inherited vaults", even if it was unlocked before you signed up.

### Verify email (OTP)
1. On "Verify your email", enter your email and the 6-digit code, then click "Verify".
2. The code is valid for 5 minutes. Click "Resend code" to get a new one.
3. If you skipped this step, use "Haven't verified your email? Verify now →" on the sign-in page. You cannot sign in before verifying.

### Sign in
1. Enter email and password, then click "Sign in". Or use the Google button.
2. If two-factor authentication is on, enter the 6-digit code from your authenticator app, then "Verify". Lost your phone? Click "Use a backup code".
3. Too many wrong passwords lock the account for a while. Contact an administrator if it stays locked.

### Forgot password
1. On the sign-in page click "Forgot password?".
2. Enter your email and click "Send reset code". A 6-digit code is emailed if the email is registered.
3. On "Reset your password", enter email, the code, the new password and the confirmation, then click "Reset password".
4. Click "Go to sign in". Accounts created with Google can use this to set a password.

### My profile
Open "Profile" in the sidebar (or click your avatar). The page has these cards:
- "Profile strength": what is left to do (verify email, add a photo, add a phone number, turn on two-step verification). Click "Do it" next to an item.
- "My legacy": your vaults, assets, Dead Man's Switch status, current plan and "People in my legacy" (beneficiaries, executors, DMS confirmers).
- Photo: click the camera button on your avatar to "Change photo" (PNG/JPG, up to 2MB) or "Remove photo".
- "Personal information": edit full name and phone number, choose "Email and invoice language" (Tiếng Việt or English), then "Save changes". Email cannot be changed.
- "Change password": current password, new password, confirmation, then "Update password". You are signed out on every device.
- "Two-factor authentication": click "Turn on", scan the QR code with an authenticator app, enter the 6-digit code, then "Verify and turn on". Save the backup codes ("Download", then "I have saved them"); each works once. To turn it off, click "Turn off" and enter a code.
- "Signed-in devices": see where you are signed in. "Sign out" one device or "Sign out all other devices".
- "Personal preferences" (this browser only): "Appearance" (Light, Dark, System), "Interface language" and "Sign out when idle".
- "Has life changed?": pick an event (married, new child, divorce, loss of a loved one, new asset, new phone / email) to see suggested steps. Click "I've reviewed everything" to record a review; this also counts as a Dead Man's Switch check-in.
- "My data": "Download data" gives a JSON copy of your account. It contains no passwords, keys or asset contents.
- "Delete account": click "I want to delete my account". It is blocked while you still own vaults, are someone's executor or beneficiary, or have an Admin / Legal Verifier role; "Resolve" shows what to do. Then enter your password (and 2FA code if on), type your email and click "Delete account permanently". This cannot be undone and any VIP plan is cancelled without refund.

## Vault Owner

### Home and readiness
- The "Estate Readiness Score" card shows how ready your plan is (vault with assets, who receives each asset, a will document, primary and backup executor, Dead Man's Switch on, a safety net, a review in the last 6 months). Click "Fix now" next to a missing item.
- A banner at the top of "My vaults" warns you when something important is missing (no executor, Dead Man's Switch off, a vault without beneficiary) with a button to fix it.

### Create a vault
1. "My vaults" → "New vault" (or "Create your first vault").
2. "Vault details": enter a name (required) and a description / hand-over instructions.
3. "Beneficiaries": at least one. Enter email (required), full name and relationship. "Add beneficiary" adds more. You cannot add yourself or the same email twice.
4. "Assets" (optional): click "Add asset", choose the asset type, then fill the form for that type. Fields in the "Sealed section" (passwords, seed phrase, PIN...) are encrypted and only shown to beneficiaries after hand-over.
5. Click "Create vault".

### Asset types
Bank account, Exchange account, Crypto wallet, E-wallet, Email account, Social media, Document, Other. Each type has its own form. How it is handed over:
- Bank account, Exchange account, Document: the beneficiary makes a legal claim with an inheritance dossier.
- Crypto wallet: the secret goes to exactly one beneficiary.
- Email account and Social media: the executor handles it following your wish ("Memorialize the account", "Delete the account" or "Download data for the family").
Account numbers have 6 to 19 digits. "Estimated value" is only a hint for beneficiaries.

### Manage assets
1. "My vaults" → open a vault → "Assets". Assets are grouped by type.
2. "Add asset" to add, "View" to reveal a secret, "Edit" to change it (leave a secret empty to keep it), "Delete" to remove.
3. Every view is recorded in the vault's activity log.

### Choose who receives each asset (allocation)
In the asset form, "Allocation among beneficiaries":
- "All beneficiaries" (default): everyone in the vault can see it.
- "One beneficiary": only one person; you can add a "Backup beneficiary" who receives it if that person declines.
- "Split by percentage": shares must total 100%. Only for bank accounts, exchange accounts and documents.
- "K-of-N joint approval": the asset opens only when K of the chosen N people approve (for example 2 of 3). Editing this allocation clears earlier approvals.
Backup beneficiary, percentage split and joint approval need a VIP plan.

### Verify an asset
1. On the vault page, each asset shows a badge: "Self-declared", "Verification pending", "Verified", "Simulated", "Verification failed" or "Re-verify needed".
2. Click "Verify" (or "Re-verify"), choose a method, follow the steps, then "Complete verification" or "Check".
3. Methods by type:
    - Bank account: account name lookup (simulated), account number format check (only banks in the supported list: BIDV, Techcombank, MB), bank statement upload, micro-deposit amount (simulated).
    - Exchange account: exchange email code, read-only API key (simulated, format check only; never paste a key that can trade or withdraw), profile screenshot.
    - Crypto wallet: wallet address check, recovery phrase check, public balance lookup, wallet signature.
    - Email account: email OTP code, Google sign-in.
    - E-wallet: SMS code (simulated), PayPal (simulated).
    - Social media: bio challenge (simulated), profile screenshot.
    - Document: file fingerprint (SHA-256), second-person confirmation (code sent to your DMS confirmers), timestamp (simulated).
    - Other: no verification.
4. A verification is valid for 180 days. Changing key details sets it back to "Self-declared". Results marked "Simulated" are not real checks.

### Beneficiaries of a vault
1. Open a vault → "Beneficiaries". Enter email (and optional name, relationship), then "Add beneficiary".
2. Edit name or relationship, then "Save". Email cannot be edited: add the new person, then remove the old one.
3. You cannot remove the last beneficiary.
4. Status: "Waiting for sign-up", "Invited", "Verified", "Accepted", "Declined", "Waived".
5. Free plan: up to 2 beneficiaries per vault. VIP: up to 10.

### Legal documents
1. Open a vault → "Legal documents". Choose the type (Will, Ownership paper, Identity card, Citizen ID card (CCCD), National ID card (CMND), Other), choose a file, then "Upload".
2. Allowed: PDF, DOC/DOCX, JPG/PNG, up to 10MB. Files are encrypted.
3. "Upload new version" replaces a file and keeps old ones in "History". Download or "Delete" from the list.

### Legacy messages & Time-capsule (VIP)
1. Open a vault → "New legacy message".
2. Enter a title, choose the recipient ("All family" or a specific beneficiary), the delivery trigger ("On claim" or "Specific date"), the message and an optional attachment (photo, audio, video, PDF up to 10MB).
3. Click "Save message".

### Edit or delete a vault
- Click "Edit" on the vault page, change the name or description, then "Save".
- "Danger zone" → "Delete vault" deletes the vault with all assets and documents. This cannot be undone.
- A vault cannot be changed after it is unlocked.

### Executors
1. Sidebar → "Executors".
2. "Primary Executor" is notified first when your Dead Man's Switch is triggered. "Backup Executor" takes over if the primary does not respond in time (by default 14 days).
3. Click "Assign", enter the person's email, then "Save". They must have a verified LegacyVault account.
4. "Replace" changes the person, "Revoke" removes them.

### Dead Man's Switch (DMS)
1. Sidebar → "Dead Man's Switch". You need a vault and a primary executor first.
2. Under "Schedule": "Check-in every (days)" 1–365, "Grace period (days)" 1–90, "Reminders" 1–10 emails during the grace period.
3. Click "Turn on" the first time, "Save changes" later.
4. Check in with "I'm okay — check in", or with the one-click link in the reminder email ("I'm OK — check in now"; no sign-in needed).
5. Trusted Social Confirmers (VIP, up to 2): add name, email and relationship, then "Add". When your grace period ends they get an email (72 hours) asking if you are safe. "The owner is safe" postpones the hand-over by 7 days. "I cannot reach the owner" notifies your executor. While waiting, the status is "Waiting for confirmers" and you can still check in to cancel.
6. Status: Active, Waiting for confirmers, Triggered (executors notified, settings locked), Suspended (paused by an administrator; contact support).

### Activity log
- Sidebar → "Activity": Dead Man's Switch and executor events. Choose "All time", "Last 30 days", "Last 90 days" or "Last 12 months". Each vault page has its own activity log for vault events.

### Plans, payment and invoices
1. Free plan: 1 vault, 2 beneficiaries per vault, 50MB storage. VIP: up to 5 vaults, 10 beneficiaries per vault, 5GB, legacy messages, trusted confirmers and advanced allocation. Prices are shown on "Plans & Pricing".
2. Sidebar → "Billing & VIP" (or the "Upgrade" button) → "Upgrade to VIP".
3. Choose a plan, choose the payment method (VNPay or PayPal), then "Upgrade with …". PayPal charges the USD equivalent.
4. After paying you return to a result page. If it says the plan is still being activated, check "Billing & VIP" in a few minutes; do not pay again.
5. "Payment History" → "Download invoice" gives a bilingual (Vietnamese – English) simulated VAT invoice PDF. It is not a real tax invoice.
6. You get emails before a VIP plan expires. Use "Extend VIP" to renew.

## Executor

### Overview
1. Sidebar → "Estates" (or Home → "Executor duties") opens the Executor workspace.
2. "Active estates" need your action. "On standby" are owners whose switch has not been triggered.
3. Each case goes: Acknowledge → Submit documents → Legal verification → Hand-over.

### Acknowledge the notice
1. When an owner's Dead Man's Switch is triggered you get an email and the case shows "You have been activated".
2. Click "Acknowledge" on the case card or inside the case ("Open case"). The card shows the deadline ("Acknowledge before …").
3. If the primary executor does not acknowledge in time (by default 14 days), the backup executor is activated.

### Submit the death certificate
1. Open the case → "Submit death certificate".
2. Select the vaults to unlock (at least one). Read the owner's instructions under each vault.
3. Attach the death certificate (PDF, PNG or JPG, up to 10MB). Optional: "Scan & Extract with AI (OCR)" reads the document and gives advice only; "Insert AI summary into submission notes" copies it. The file is sent to Google Gemini for this.
4. Add notes (up to 1000 characters) and click "Submit for verification".

### Track the hand-over
1. "Hand-over progress": Waiting for verifier → Approved, awaiting signature → Unlocked for beneficiary → Received by beneficiary. Also Rejected or Expired.
2. If rejected, the reason is shown. Fix it and submit again.
3. "Messages with Legal Verifier": write a message or attach a file (PDF/PNG/JPG up to 10MB), then "Send".
4. "Beneficiary claims" shows each beneficiary's answer. "Send reminder" emails someone who has not responded.

### Online accounts checklist
1. After unlock, "Online accounts checklist" lists email and social accounts with the owner's wish and an "Official help page" link.
2. Set each to "Not started", "In progress" or "Done", add a note, then "Save".
3. "Done" needs a reference code (for example the provider's request number) or an evidence file (PDF/JPG/PNG up to 10MB).
4. The beneficiary can confirm or dispute within 7 days; after that it counts as accepted. If disputed, add new evidence and complete it again.

## Legal Verifier

### Find requests
1. Sidebar → "Home" shows counts and requests that need you. "Requests" lists "Pending review" and "Awaiting your signature". "History" lists requests you signed or rejected; filter by result and date.

### Review a request
1. The request page shows "Case information", the vaults to unlock and the "Death certificate".
2. Step 1 "Check document": the system checks the file content automatically (real PDF, JPG or PNG) and shows Valid or Invalid; you only click the button. Step 2 "View document". Step 3 approve or reject.
3. Optional: "✨ Analyze & Cross-Check with AI (OCR)" gives an AI advisory report (name match, seal, image quality). It is a suggestion only; the decision is yours.
4. "Messages with the Executor": ask for more information or files, then "Send reply". "Refresh" loads new messages.

### Approve or reject
- Approve: after checking and viewing, click "Approve request" and confirm. You still need to sign. An approved request that is not signed within 7 days goes back to "Pending review".
- Reject: needs steps 1–2. Choose a "Rejection reason" (blurry or unreadable, not a valid death certificate, information does not match, suspected forgery, missing pages or stamp, or other with a note up to 500 characters), then "Reject request". The executor sees the reason.

### Sign with OTP
1. On an approved request, click "Send signing code to my email".
2. Enter the 6-digit code (valid for 5 minutes; "Resend code" after the countdown).
3. Click "Sign and unlock vault(s)" and confirm. The vaults are unlocked and the beneficiaries are notified.

## Beneficiary

### Find inherited vaults
1. Sidebar → "My vaults" (or Home → "Inherited vaults") lists vaults handed over to you, with the owner and the claim deadline.
2. You have 60 days from unlocking to claim. After the deadline the content is archived. An administrator can extend it if you need more time.

### Steps to receive a vault
1. Verify your identity: open the vault → choose "Email code" (or "ID number (eKYC)" if offered) → "Start verification" → enter the code → "Verify". Verification lasts 30 minutes. 5 wrong codes lock verification for 24 hours (an administrator can unlock it).
2. "Your response": "Accept" your share, or "Decline" (optional reason, then "Confirm decline"). Declining cannot be undone; your share goes to the backup beneficiary if there is one.
3. "Assets": "View" or "Download" each asset allocated to you. For joint approval assets, click "Approve" and wait until enough people approve ("Refresh" to check).
4. "Legal documents": download files. "Inheritance dossier" → "Download PDF" lists your assets and the steps to claim them (no passwords).
5. "Executor progress on online accounts": see what the executor did. "Confirm" or "Dispute" (reason 5–500 characters) each completed task.
6. "Legacy messages & Time-capsule": "Read" messages for you. Time-capsule messages stay sealed until their date.
7. "Finish and close": "Review before closing" shows assets you have not viewed. Tick the confirmation, then "Confirm and close". After closing you can no longer view or download.

## Administrator

The admin menu: Overview, Users, Roles, Vaults, Audit log, Alerts, Payments, Plans, Failed emails, Locked.

- "Overview": users, vaults, open alerts, DMS needing attention, revenue, VIP users, and a "To handle" list. "Refresh" reloads.
- "Users": search by email or name, filter by role and status. "Roles…" to grant or remove Legal Verifier or Admin (Owner, Beneficiary and Executor are set by the system). "Lock account" / "Unlock account", "Suspend DMS" / "Resume DMS". "Assign executor" appoints an emergency executor when a triggered DMS has nobody who can act. "Open profile" shows a user's details, vaults, asset verification, payments, alerts and recent logs.
- "Roles": "New role", enter code, name and permissions, then "Create role". "Edit" → "Save changes", or "Delete".
- "Vaults": search by vault name or owner, filter by status or "claim deadline within 7 days". "Details" shows metadata only (never asset contents). "Extend claim period" adds days with a reason (at most 2 times per vault).
- "Audit log": every security-relevant action, last 30 days by default. Filter, then "Search"; "Reset" clears. "Export CSV" downloads.
- "Alerts": suspicious activity found every 5 minutes (account locked after failed logins, many failed logins from one IP, reused refresh token, mass download, ADMIN role granted). HIGH alerts are also emailed. "View logs", "Lock this user", "Acknowledge" with a note.
- "Payments": all payments, read-only. Filter, download a receipt, "Export CSV".
- "Plans": "Change price / name" (10,000–100,000,000 VND, with a reason). New prices only apply to future purchases. Limits cannot be changed here.
- "Failed emails": notification emails that could not be delivered. "Resend" after mail is fixed.
- "Locked": beneficiaries locked after 5 wrong identity codes. "Unlock" gives them 5 new attempts.

## Emails
- System emails use the language set in "My profile" → "Email and invoice language". People without an account get Vietnamese.
- You get emails for sign-up codes, DMS reminders and check-in links, executor activation, verification results, vault unlock, claim deadline reminders, payments and plan expiry.

## Not supported / safety
- LegacyVault staff and this assistant never ask for your password, OTP codes, backup codes or asset secrets.
- Some checks are simulations for the demo (marked "Simulated"), and the invoice is a simulated invoice.
- AI OCR results are advice only; a Legal Verifier always decides.
- For problems not covered here, contact an administrator.
