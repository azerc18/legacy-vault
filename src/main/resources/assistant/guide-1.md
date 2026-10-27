# LegacyVault user guide

LegacyVault stores digital assets (bank accounts, crypto wallets, social media logins, documents) and hands them over to trusted people when the owner can no longer check in (Dead Man's Switch).

Roles:
- Vault Owner: creates vaults and decides who receives them.
- Executor: a trusted person who handles the hand-over after the Dead Man's Switch is triggered.
- Legal Verifier: checks the death certificate and signs to unlock vaults.
- Beneficiary: receives the unlocked vault.
- Administrator: manages users, roles and security.

One account can have several roles. After signing in, the home page ("Welcome back, <name>") shows one card per feature you can use. Click "LegacyVault" in the header to return home, click your name or avatar to open "My profile", and click "Log out" to sign out.

## Account

### Register
1. Open the sign-in page and click "Sign up".
2. Enter full name, email, password and confirm password, then submit.
3. Password rules: 8–72 characters, with at least one uppercase letter, one lowercase letter, one number and one special character. The strength bar under the field goes from "Very weak" to "Very strong"; the password is accepted from "Strong".
4. A 6-digit code is emailed to you. Continue with "Verify email".

### Verify email (OTP)
1. On "Verify your email", enter your email and the 6-digit code, then click "Verify".
2. The code is valid for 5 minutes. Click "Resend code" to get a new one.
3. If you skipped this step, use "Haven't verified your email? Verify now →" on the sign-in page. You cannot sign in before verifying.

### Sign in
1. Enter email and password, then click "Sign in".
2. Too many wrong passwords lock the account temporarily. Contact an administrator if it stays locked.

### Forgot password
1. On the sign-in page click "Forgot password?".
2. Enter your email and click "Send reset link". A 6-digit code is emailed if the email is registered.
3. On "Reset your password", enter email, the code, the new password and the confirmation, then click "Reset password".
4. Click "Go to sign in" and sign in with the new password.

### Profile and change password
1. Click your name or avatar in the header to open "My profile".
2. Edit full name or phone number, then click "Save changes".
3. Change photo: click the photo (PNG/JPG, up to 2MB). "Remove photo" deletes it.
4. Change password: enter current password, new password and confirmation, then click "Update password". You are signed out on every device and must sign in again.

## Vault Owner

### Create a vault
1. Home → "My vaults" → "New vault" (or "Create your first vault").
2. Under "Vault details", enter a name (required) and a description / hand-over instructions.
3. Optionally click "Add asset" for each asset: choose the type (Bank account, Crypto wallet, Social media, Document, Other), asset name, secret (password, seed phrase, account number...) and notes for the beneficiary.
4. Save. Secrets are encrypted on the server before they are stored.
5. The vault page opens with "Vault created. Now choose who will receive it."

### Manage assets
1. "My vaults" → open a vault → "Assets" section.
2. "Add asset" to add, "View" to reveal a secret, "Edit" to change it (leave the secret empty to keep the current one), "Delete" to remove.
3. Every view is recorded in the vault's activity log.

### Add legal documents
1. Open a vault → "Legal documents" section.
2. Choose the document type (Will, Ownership paper, Identity card, Other), choose a file, then click "Upload".
3. Allowed: PDF, DOC/DOCX, JPG/PNG, up to 10MB. Files are encrypted before storage.
4. Use the download button to get a file back, "Delete" to remove it.

### Assign a beneficiary
1. Open a vault → "Beneficiary" section.
2. Enter the beneficiary's email and click "Assign".
3. To change it, enter a new email and click "Replace", then confirm.
4. Each vault has one beneficiary.

### Edit or delete a vault
- Click "Edit" at the top of the vault page, change the name or description, then "Save".
- "Danger zone" → "Delete vault" deletes the vault with all assets and documents. This cannot be undone.
- Vaults cannot be changed after they are unlocked for the beneficiary.

### Assign executors
1. Home → "Executors".
2. "Primary Executor" is notified first when your Dead Man's Switch is triggered. "Backup Executor" takes over if the primary does not respond within 14 days.
3. Click "Assign", enter the person's email, then "Save". They must have a verified LegacyVault account.
4. "Replace" changes the person, "Revoke" removes them.

### Dead Man's Switch (DMS)
1. Home → "Dead Man's Switch". Create a vault first if the page asks you to.
2. Under "Schedule", set:
   - "Check-in every (days)": 1–365, how often you confirm you are okay.
   - "Grace period (days)": 1–90, extra time after a missed check-in before hand-over starts.
   - "Reminders": 1–10 emails sent during the grace period.
3. Click "Turn on" the first time, "Save changes" later.
4. Check in by clicking "I'm okay — check in" before "Next check-in due". The page also shows last check-in, next reminder and when hand-over would start.
5. Status: Active (running), Triggered (you missed the deadline, executors are notified), Suspended (paused by an administrator; contact support).

### Activity log
- Home → "Activity log" shows actions on your vaults. Choose "Last 30 days", "Last 90 days" or "Last 12 months". Each vault page also has "Activity log (last 30 days)".

## Executor

### Overview
1. Home → "Executor duties" opens the Executor Workspace.
2. "Active estates" are cases where you must act. "On standby" are owners whose switch has not been triggered yet.
3. Each case goes through: Acknowledge → Submit documents → Legal verification → Hand-over.

### Acknowledge the notice
1. When an owner's Dead Man's Switch is triggered you receive an email and the case shows "You have been activated".
2. Click "Acknowledge" on the case card or inside the case ("Open case").
3. If the primary executor does not acknowledge within 14 days, the backup executor is activated.

### Submit verification (death certificate)
1. Open the case → "Submit death certificate".
2. Select the vaults to unlock (at least one). Read the owner's instructions shown under each vault.
3. Attach the death certificate (PDF, PNG or JPG, up to 10MB) and optional notes (up to 1000 characters).
4. Click "Submit for verification". A Legal Verifier will review the request.

### Track the hand-over and message the verifier
1. "Hand-over progress" shows each vault's status: Waiting for verifier → Approved, awaiting signature → Unlocked for beneficiary → Received by beneficiary. Other statuses: Rejected, Expired.
2. If a request is rejected, the reason is shown; fix the problem and submit again for those vaults.
3. "Messages with Legal Verifier": type a message or attach a file (PDF/PNG/JPG up to 10MB), then click "Send".

## Legal Verifier

### Find requests
1. Home → "Verification requests". Requests are grouped into "Pending review" and "Awaiting your signature".
2. Click a request to open it. "Verification history" lists requests you already signed or rejected; filter by status and date.

### Review a request
1. The request page shows "Case information", "Vaults to unlock" and the "Death certificate".
2. Step 1: click "View document". Step 2: click "Check document" to record whether it is Valid or Invalid. Step 3: approve.
3. "Messages with the Executor": ask for more information or files, then click "Send reply". Use "Refresh" to load new messages.

### Approve or reject
- Approve: after viewing and checking the document, click "Approve request" and confirm. Approval alone does not unlock vaults; you still need to sign.
- Reject: only needs step 1 (View). Click "Reject request", choose a rejection reason (blurry or unreadable, not a valid death certificate, information does not match the account holder, suspected forgery, missing pages or stamp, or other with a note up to 500 characters), then confirm. The executor sees the reason.

### Sign with OTP
1. On an approved request, click "Send signing code to my email".
2. Enter the 6-digit code (valid for 5 minutes; "Resend code" after the countdown).
3. Click "Sign and unlock vault(s)" and confirm. The vaults are unlocked and the beneficiaries are notified.

## Beneficiary

### Find inherited vaults
1. Home → "Inherited vaults" lists vaults handed over to you, with the owner name and the claim deadline.
2. You have 60 days from unlocking to claim a vault. After that, the claim period ends and the vault can no longer be opened.

### Verify your identity
1. Open a vault → "Verify your identity".
2. Choose a method: "Email code" or "ID number (eKYC)", then click "Start verification".
3. Enter the 6-digit email code (valid for 5 minutes) or your ID number, then click "Verify". Use "Change method" to switch.

### View assets and documents
- After verification, "Assets" shows each asset; view the secret or download it. "Legal documents" lets you download files. Every view and download is recorded.

### Close the vault
1. Click "Review before closing" under "Finish and close". It lists assets you have not viewed yet; after closing you can no longer view them.
2. Click "Confirm and close". The vault is marked as claimed.

## Administrator

The admin area has the menu: Users, Roles, Vaults, Audit log, Alerts.

### Users
1. "Users": search by name or email.
2. Grant or remove roles such as Legal Verifier or Admin (Owner, Beneficiary and Executor are assigned by the system and cannot be changed here).
3. "Lock account" / "Unlock account" with a reason.
4. "Suspend DMS" / "Resume DMS" pauses or resumes a user's Dead Man's Switch, with a reason.

### Roles & permissions
- "Roles": click "New role", enter name and choose permissions, then "Create role". Use "Edit" → "Save changes" or "Delete" for existing custom roles.

### Vaults
- "Vaults": browse every vault and its status (Active, Unlocked, Claimed, Archived). "Details" shows metadata only; asset contents are never shown.

### Audit log
- "Audit log": every security-relevant action. Defaults to the last 30 days; filter by date ("From", "To") and other fields, then "Search". "Reset" clears filters.

### Security alerts
- "Alerts": suspicious activity found every 5 minutes, such as an account locked after failed logins, many failed logins from one IP, a reused refresh token, or a mass download by one user. HIGH alerts are also emailed to admins.
- "View logs" opens the related audit entries. "Acknowledge" moves an alert to the acknowledged tab.
- If a Dead Man's Switch is triggered and the owner has no executor, admins receive an email to handle it.

## Not supported / safety
- LegacyVault staff and this assistant never ask for your password, OTP codes or asset secrets.
- Google sign-in is not available yet.
- For problems not covered here, contact an administrator.
