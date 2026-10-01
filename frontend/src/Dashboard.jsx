import React, { useState } from 'react';
import { Link } from 'react-router-dom';
export default function Dashboard() {
    const [isLargeFont, setIsLargeFont] = useState(false);

    const handleReadAloud = () => {
        if ('speechSynthesis' in window) {
            const text =
                'Good morning, Robert. You are currently assigned as an Executor for 2 people: John Doe and Jane Smith. No immediate action is required while your loved ones are healthy.';
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.rate = 0.9;
            window.speechSynthesis.speak(utterance);
        }
    };

    return (
        <div
            className={`bg-background font-body-md text-on-surface antialiased min-h-screen flex flex-col ${
                isLargeFont ? 'text-lg' : ''
            }`}
        >
            {/* HEADER ĐÃ ĐƯỢC CHỈNH LẠI ĐỂ KHÔNG BỊ VỠ */}
            <header className="fixed top-0 left-0 right-0 w-full z-50 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] overflow-hidden">
                <div className="h-24 max-w-[1440px] w-full mx-auto px-4 xl:px-8 flex items-center justify-between gap-2">

                    <div className="flex items-center gap-2 shrink-0">
                        <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-on-primary shadow-sm shrink-0">
                            <span className="material-symbols-outlined text-[24px]">shield_locked</span>
                        </div>
                        <div className="whitespace-nowrap">
                            <span className="font-headline-sm text-[20px] text-primary tracking-tight block leading-tight font-bold">LegacyVault</span>
                            <span className="font-label-sm text-[12px] text-secondary font-semibold tracking-wide uppercase block">Executor Services</span>
                        </div>
                    </div>

                    <div className="hidden lg:block h-8 w-[1px] bg-outline-variant/60 shrink-0 mx-2"></div>

                    <nav className="hidden xl:flex items-center gap-1 shrink-0">
                        <a href="#" className="whitespace-nowrap px-2 py-2 transition-colors bg-surface-container-high text-primary font-bold rounded-lg">Estate Overview</a>
                        <a href="#" className="whitespace-nowrap px-2 py-2 rounded-lg font-label-md text-on-surface-variant hover:text-on-surface transition-colors">Executor Duties</a>
                        <a href="#" className="whitespace-nowrap px-2 py-2 rounded-lg font-label-md text-on-surface-variant hover:text-on-surface transition-colors">Document Vault</a>
                        <a href="#" className="whitespace-nowrap px-2 py-2 rounded-lg font-label-md text-on-surface-variant hover:text-on-surface transition-colors">Beneficiaries</a>
                        <a href="#" className="whitespace-nowrap px-2 py-2 rounded-lg font-label-md text-on-surface-variant hover:text-on-surface transition-colors">Timeline</a>
                    </nav>

                    <div className="flex-1"></div>

                    <div className="flex items-center gap-3 shrink-0">
                        <div className="hidden md:flex flex-col items-end whitespace-nowrap">
                            <span className="font-label-lg text-[16px] text-on-surface font-semibold">Welcome, Robert</span>
                            <span className="font-label-sm text-[13px] text-on-surface-variant">Appointed Primary Executor</span>
                        </div>

                        <a href="tel:18005558285" className="hidden lg:flex items-center gap-2 px-3 py-2 rounded-xl bg-surface-container-low text-primary hover:bg-surface-container transition-all whitespace-nowrap shrink-0">
                            <span className="material-symbols-outlined text-[20px]">support_agent</span>
                            <div className="flex flex-col text-left">
                                <span className="text-[10px] font-semibold leading-tight text-on-surface-variant uppercase tracking-wider">Need Immediate Help?</span>
                                <span className="text-[14px] font-bold">1-800-555-VAULT</span>
                            </div>
                        </a>



                        {/* Thêm nút Login vào cụm bên phải của Header */}
                        <Link
                            to="/login"
                            className="inline-flex items-center justify-center gap-1.5 h-12 px-4 rounded-xl bg-surface-container-high text-primary hover:bg-surface-container transition-all font-label-md font-bold shadow-xs whitespace-nowrap shrink-0"
                        >
                            <span className="material-symbols-outlined text-[20px]">login</span>
                            <span>Đăng nhập</span>
                        </Link>
                    </div>
                </div>
            </header>

            {/* MAIN CONTENT */}
            <main className="w-full pt-24 bg-background flex-1">
                <div className="max-w-[1140px] mx-auto px-gutter py-space-lg w-full">
                    <div className="flex flex-col w-full">
                        <div className="w-full bg-surface-container-low rounded-xl p-space-sm sm:px-space-md flex flex-wrap items-center justify-between gap-space-sm shadow-sm mb-space-md">
                            <div className="flex items-center gap-space-xs text-on-surface">
                <span className="material-symbols-outlined text-secondary text-[22px]">
                  visibility
                </span>
                                <span className="font-label-sm text-label-sm text-on-surface-variant font-semibold">
                  Reading Assist:
                </span>
                                <div className="inline-flex items-center bg-surface-container-lowest rounded-lg p-0.5 shadow-sm">
                                    <button
                                        aria-label="Standard text size"
                                        className={`px-2.5 py-1 text-xs font-bold rounded transition-colors ${
                                            !isLargeFont ? 'bg-primary text-on-primary shadow-xs' : 'text-primary hover:bg-surface-container'
                                        }`}
                                        onClick={() => setIsLargeFont(false)}
                                        type="button"
                                    >
                                        A
                                    </button>
                                    <button
                                        aria-label="Large text size"
                                        className={`px-2.5 py-1 text-sm font-bold rounded transition-colors ${
                                            isLargeFont ? 'bg-primary text-on-primary shadow-xs' : 'text-primary hover:bg-surface-container'
                                        }`}
                                        onClick={() => setIsLargeFont(true)}
                                        type="button"
                                    >
                                        A+
                                    </button>
                                </div>
                                <button
                                    className="ml-2 inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-surface-container-lowest text-primary hover:bg-surface-container transition-colors font-label-sm text-label-sm shadow-sm"
                                    onClick={handleReadAloud}
                                    type="button"
                                >
                  <span className="material-symbols-outlined text-[18px]">
                    volume_up
                  </span>
                                    <span className="hidden sm:inline">Read Aloud</span>
                                </button>
                            </div>
                            <a
                                className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-surface-container-lowest text-on-surface hover:bg-surface-container transition-all shadow-sm"
                                href="tel:18005829214"
                            >
                <span className="material-symbols-outlined text-secondary text-[20px]">
                  phone_in_talk
                </span>
                                <span className="font-label-sm text-label-sm">
                  <span className="text-on-surface-variant">
                    24/7 Bereavement &amp; Legal Support:{' '}
                  </span>
                  <strong className="text-primary font-bold ml-1">
                    (800) 582-9214
                  </strong>
                </span>
                            </a>
                        </div>

                        <section className="mb-space-lg">
                            <div className="flex flex-col md:flex-row md:items-end justify-between gap-space-md pb-space-sm">
                                <div className="space-y-space-xs max-w-2xl">
                                    <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed font-label-sm text-label-sm">
                    <span className="material-symbols-outlined text-[16px]">
                      verified
                    </span>
                                        Fiduciary Portal Active
                                    </div>
                                    <h1 className="font-headline-lg text-headline-lg text-primary tracking-tight font-bold">
                                        Good morning, Robert
                                    </h1>
                                    <p className="font-body-xl text-body-xl text-on-surface-variant leading-relaxed">
                                        Thank you for serving as a trusted executor. Here is a clear
                                        summary of the individuals who have entrusted you with their
                                        wishes.
                                    </p>
                                </div>
                                <div className="shrink-0 flex items-center gap-3">
                                    <div className="w-12 h-12 rounded-xl bg-surface-container-high flex items-center justify-center text-primary shadow-sm">
                    <span className="material-symbols-outlined text-[28px]">
                      assignment_ind
                    </span>
                                    </div>
                                    <div className="text-left">
                    <span className="block font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
                      Assigned Records
                    </span>
                                        <span className="block font-headline-sm text-headline-sm text-primary font-bold">
                      2 Active Estates
                    </span>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section className="mb-space-xl">
                            <div className="bg-surface-container-low rounded-xl p-space-md sm:p-space-lg shadow-sm relative overflow-hidden">
                                <div className="flex flex-col lg:flex-row items-start gap-space-lg relative z-10">
                                    <div className="flex items-start gap-space-md flex-1">
                                        <div className="w-14 h-14 rounded-2xl bg-surface-container-lowest text-primary flex items-center justify-center shadow-sm shrink-0 mt-1">
                      <span className="material-symbols-outlined text-[32px] text-secondary">
                        handshake
                      </span>
                                        </div>
                                        <div className="space-y-space-xs">
                                            <h2 className="font-headline-sm text-headline-sm text-primary font-bold">
                                                Your Role &amp; Reassurance
                                            </h2>
                                            <p className="font-body-lg text-body-lg text-on-surface font-medium leading-relaxed">
                                                You are currently assigned as an Executor for 2 people. Your
                                                role is to help them transfer their digital legacy when the
                                                time comes.
                                            </p>
                                            <div className="pt-space-xs">
                                                <a
                                                    className="inline-flex items-center gap-1 font-label-md text-label-md text-secondary hover:text-primary transition-colors font-bold group"
                                                    href="#"
                                                >
                                                    <span>Read the 3-minute Executor Guide</span>
                                                    <span className="material-symbols-outlined text-[20px] group-hover:translate-x-1 transition-transform">
                            arrow_forward
                          </span>
                                                </a>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="w-full lg:w-[460px] bg-surface-container-lowest rounded-xl p-space-md shadow-sm space-y-space-sm">
                                        <div className="flex items-start gap-3">
                                            <div className="w-6 h-6 rounded-full bg-secondary-fixed flex items-center justify-center shrink-0 mt-0.5">
                                                <span className="material-symbols-outlined text-[16px] text-primary">check</span>
                                            </div>
                                            <p className="font-body-md text-body-md text-on-surface">
                                                <strong>No immediate action is needed</strong> while loved
                                                ones are healthy and active.
                                            </p>
                                        </div>
                                        <div className="flex items-start gap-3">
                                            <div className="w-6 h-6 rounded-full bg-secondary-fixed flex items-center justify-center shrink-0 mt-0.5">
                                                <span className="material-symbols-outlined text-[16px] text-primary">check</span>
                                            </div>
                                            <p className="font-body-md text-body-md text-on-surface">
                                                <strong>Your access remains locked</strong> until a formal,
                                                verified life transition occurs.
                                            </p>
                                        </div>
                                        <div className="flex items-start gap-3">
                                            <div className="w-6 h-6 rounded-full bg-secondary-fixed flex items-center justify-center shrink-0 mt-0.5">
                                                <span className="material-symbols-outlined text-[16px] text-primary">check</span>
                                            </div>
                                            <p className="font-body-md text-body-md text-on-surface">
                                                <strong>Our legal care team</strong> will personally guide
                                                you through every required step when necessary.
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section className="mb-space-xl">
                            <div className="mb-space-md">
                                <h2 className="font-headline-md text-headline-md text-primary font-bold">
                                    People You Represent
                                </h2>
                                <p className="font-body-lg text-body-lg text-on-surface-variant">
                                    Individuals who have designated you in their estate plan.
                                </p>
                            </div>
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-space-lg">
                                <article className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col justify-between transition-all hover:shadow-md">
                                    <div>
                                        <div className="flex items-start justify-between gap-space-md mb-space-md">
                                            <div className="flex items-center gap-space-md">
                                                <div className="w-16 h-16 rounded-2xl bg-primary text-on-primary font-headline-md text-headline-md flex items-center justify-center font-bold tracking-tight shadow-sm shrink-0">
                                                    JD
                                                </div>
                                                <div>
                                                    <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                                                        John Doe
                                                    </h3>
                                                    <p className="font-label-md text-label-md text-on-surface-variant font-medium mt-0.5">
                                                        Assigned: Primary Executor • Family Trust &amp; Digital Assets
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="mb-space-md">
                      <span className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-surface-container text-primary font-label-md text-label-md font-semibold">
                        <span className="w-2.5 h-2.5 rounded-full bg-secondary" />
                        Active • In Good Standing
                      </span>
                                        </div>
                                        <div className="bg-surface-container-low rounded-xl p-space-md mb-space-lg">
                                            <div className="flex items-start gap-2.5">
                        <span className="material-symbols-outlined text-secondary text-[22px] shrink-0 mt-0.5">
                          info
                        </span>
                                                <div>
                          <span className="block font-label-md text-label-md text-primary font-bold">
                            What this means
                          </span>
                                                    <p className="font-body-md text-body-md text-on-surface mt-0.5">
                                                        You are the first person designated to carry out John's
                                                        wishes. All legal and digital instructions will flow
                                                        directly to you upon official verification.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="space-y-space-md pt-space-xs">
                                        <button
                                            className="w-full min-h-[52px] px-space-md py-3.5 rounded-xl bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg font-bold flex items-center justify-center gap-2.5 shadow-sm transition-all text-center"
                                            type="button"
                                        >
                                            <Link
                                                to="/verifier"
                                                className="w-full min-h-[52px] px-space-md py-3.5 rounded-xl bg-primary hover:bg-primary-container text-on-primary font-label-lg text-label-lg font-bold flex items-center justify-center gap-2.5 shadow-sm transition-all text-center"
                                            >
                      <span className="material-symbols-outlined text-[22px]">
                        lock_clock
                      </span>
                                            <span>Report Passing (Initiate Unlock)</span>
                                            </Link>
                                        </button>
                                        <p className="font-body-md text-[14px] leading-relaxed text-on-surface-variant text-center px-space-xs">
                                            Only use this in the event of John's passing. This initiates a
                                            secure, verified verification process with death certificate review.
                                        </p>
                                        <div className="text-center pt-space-xs">
                                            <a
                                                className="inline-flex items-center gap-1.5 font-label-md text-label-md text-secondary hover:text-primary transition-colors underline decoration-secondary/40 font-semibold"
                                                href="#"
                                            >
                        <span className="material-symbols-outlined text-[18px]">
                          download
                        </span>
                                                <span>View Executor Authorization Certificate (PDF)</span>
                                            </a>
                                        </div>
                                    </div>
                                </article>

                                <article className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col justify-between transition-all hover:shadow-md">
                                    <div>
                                        <div className="flex items-start justify-between gap-space-md mb-space-md">
                                            <div className="flex items-center gap-space-md">
                                                <div className="w-16 h-16 rounded-2xl bg-surface-container-high text-primary font-headline-md text-headline-md flex items-center justify-center font-bold tracking-tight shadow-sm shrink-0">
                                                    JS
                                                </div>
                                                <div>
                                                    <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                                                        Jane Smith
                                                    </h3>
                                                    <p className="font-label-md text-label-md text-on-surface-variant font-medium mt-0.5">
                                                        Assigned: Backup Executor • Personal &amp; Healthcare Directives
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="mb-space-md">
                      <span className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-surface-container-high text-on-surface-variant font-label-md text-label-md font-semibold">
                        <span className="w-2.5 h-2.5 rounded-full bg-outline" />
                        Waiting for Primary • Secondary in Line
                      </span>
                                        </div>
                                        <div className="bg-surface-container-low rounded-xl p-space-md mb-space-lg">
                                            <div className="flex items-start gap-2.5">
                        <span className="material-symbols-outlined text-secondary text-[22px] shrink-0 mt-0.5">
                          people_alt
                        </span>
                                                <div>
                          <span className="block font-label-md text-label-md text-primary font-bold">
                            What this means
                          </span>
                                                    <p className="font-body-md text-body-md text-on-surface mt-0.5">
                                                        Jane has designated Arthur Smith as primary. You will
                                                        only be notified if Arthur is unable to serve or
                                                        formally steps aside.
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="space-y-space-md pt-space-xs">
                                        <button
                                            className="w-full min-h-[52px] px-space-md py-3.5 rounded-xl bg-surface-container text-on-surface-variant font-label-lg text-label-lg font-bold flex items-center justify-center gap-2.5 cursor-not-allowed opacity-80 text-center"
                                            disabled
                                            type="button"
                                        >
                      <span className="material-symbols-outlined text-[22px]">
                        pause_circle
                      </span>
                                            <span>No action needed</span>
                                        </button>
                                        <p className="font-body-md text-[14px] leading-relaxed text-on-surface-variant text-center px-space-xs">
                                            As a backup executor, no steps are required from you at this
                                            time. Arthur Smith holds current fiduciary custody.
                                        </p>
                                        <div className="text-center pt-space-xs">
                                            <a
                                                className="inline-flex items-center gap-1.5 font-label-md text-label-md text-secondary hover:text-primary transition-colors underline decoration-secondary/40 font-semibold"
                                                href="#"
                                            >
                        <span className="material-symbols-outlined text-[18px]">
                          verified
                        </span>
                                                <span>View Confirmation of Appointment</span>
                                            </a>
                                        </div>
                                    </div>
                                </article>
                            </div>
                        </section>

                        <section className="mb-space-xl">
                            <div className="flex items-center justify-between gap-space-md mb-space-md">
                                <div>
                                    <h2 className="font-headline-md text-headline-md text-primary font-bold">
                                        Recent Notifications &amp; Updates
                                    </h2>
                                    <p className="font-body-md text-body-md text-on-surface-variant">
                                        Clear chronological record of changes made to documents in your
                                        purview.
                                    </p>
                                </div>
                                <button
                                    className="hidden sm:inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-surface-container-low text-primary hover:bg-surface-container font-label-md text-label-md font-semibold transition-colors"
                                    type="button"
                                >
                                    <span>View all past activity (5)</span>
                                    <span className="material-symbols-outlined text-[18px]">
                    chevron_right
                  </span>
                                </button>
                            </div>
                            <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden">
                                <div className="p-space-md sm:p-space-lg flex items-start gap-space-md hover:bg-surface-container-lowest/70 transition-colors">
                                    <div className="w-12 h-12 rounded-xl bg-secondary-fixed text-primary flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                    <span className="material-symbols-outlined text-[24px]">
                      description
                    </span>
                                    </div>
                                    <div className="flex-1 min-w-0 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                                        <div className="space-y-0.5">
                                            <h3 className="font-label-lg text-label-lg text-on-surface font-bold">
                                                John Doe updated his vault
                                            </h3>
                                            <p className="font-body-md text-body-md text-on-surface-variant">
                                                Added an updated homeowners insurance policy.
                                            </p>
                                        </div>
                                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0 self-start sm:self-center bg-surface-container-low px-3 py-1 rounded-full">
                      2 days ago
                    </span>
                                    </div>
                                </div>
                                <div className="h-[1px] bg-surface-container-high mx-space-md" />
                                <div className="p-space-md sm:p-space-lg flex items-start gap-space-md hover:bg-surface-container-lowest/70 transition-colors">
                                    <div className="w-12 h-12 rounded-xl bg-tertiary-fixed text-tertiary-container flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                    <span className="material-symbols-outlined text-[24px]">
                      verified_user
                    </span>
                                    </div>
                                    <div className="flex-1 min-w-0 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                                        <div className="space-y-0.5">
                                            <h3 className="font-label-lg text-label-lg text-on-surface font-bold">
                                                Annual Check-in Confirmed
                                            </h3>
                                            <p className="font-body-md text-body-md text-on-surface-variant">
                                                Jane Smith verified her contact information and executor
                                                list.
                                            </p>
                                        </div>
                                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0 self-start sm:self-center bg-surface-container-low px-3 py-1 rounded-full">
                      2 weeks ago
                    </span>
                                    </div>
                                </div>
                                <div className="h-[1px] bg-surface-container-high mx-space-md" />
                                <div className="p-space-md sm:p-space-lg flex items-start gap-space-md hover:bg-surface-container-lowest/70 transition-colors">
                                    <div className="w-12 h-12 rounded-xl bg-primary-fixed text-primary flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                    <span className="material-symbols-outlined text-[24px]">
                      menu_book
                    </span>
                                    </div>
                                    <div className="flex-1 min-w-0 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                                        <div className="space-y-0.5">
                                            <h3 className="font-label-lg text-label-lg text-on-surface font-bold">
                                                Executor Handbook Updated
                                            </h3>
                                            <p className="font-body-md text-body-md text-on-surface-variant">
                                                Version 2.4 available with simplified probate instructions
                                                for 2025.
                                            </p>
                                        </div>
                                        <span className="font-label-sm text-label-sm text-on-surface-variant shrink-0 self-start sm:self-center bg-surface-container-low px-3 py-1 rounded-full">
                      1 month ago
                    </span>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section className="mb-space-lg">
                            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col lg:flex-row items-center justify-between gap-space-lg">
                                <div className="flex items-center gap-space-md max-w-xl">
                                    <div className="w-14 h-14 rounded-2xl bg-surface-container text-primary flex items-center justify-center shrink-0 shadow-sm">
                    <span className="material-symbols-outlined text-[32px] text-secondary">
                      support_agent
                    </span>
                                    </div>
                                    <div>
                                        <h3 className="font-headline-sm text-headline-sm text-primary font-bold">
                                            Questions about your responsibilities?
                                        </h3>
                                        <p className="font-body-lg text-body-lg text-on-surface-variant mt-1 leading-relaxed">
                                            Speak with a licensed estate specialist. Free, confidential
                                            support for all appointed executors.
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-4">
                                    {/* Nút Gọi */}
                                    <a
                                        href="tel:8005829214"
                                        className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-surface-container-low hover:bg-surface-container text-primary font-label-md font-bold transition-all whitespace-nowrap shadow-xs"
                                    >
                                        <span className="material-symbols-outlined text-[18px]">call</span>
                                        <span>Call (800) 582-9214</span>
                                    </a>

                                    {/* Nút Lịch hẹn */}
                                    <button
                                        className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-surface-container-low hover:bg-surface-container text-primary font-label-md font-bold transition-all whitespace-nowrap shadow-xs"
                                    >
                                        <span className="material-symbols-outlined text-[18px]">calendar_today</span>
                                        <span>Schedule a Call</span>
                                    </button>
                                </div>
                            </div>
                        </section>
                    </div>
                </div>
            </main>

            <footer className="w-full bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] mt-auto">
                <div className="max-w-[1140px] mx-auto px-gutter py-space-xl">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-space-lg pb-space-lg">
                        <div className="space-y-space-xs">
                            <div className="flex items-center gap-2 text-primary">
                                <span className="material-symbols-outlined text-[22px] text-secondary">lock</span>
                                <span className="font-label-lg text-label-lg font-semibold">Bank-Grade 256-Bit Encryption</span>
                            </div>
                            <p className="font-body-md text-body-md text-on-surface-variant">
                                All estate records, digital keys, and sensitive directives are
                                protected with hardware-isolated cryptographic custody.
                            </p>
                        </div>
                        <div className="space-y-space-xs">
                            <div className="flex items-center gap-2 text-primary">
                                <span className="material-symbols-outlined text-[22px] text-secondary">verified_user</span>
                                <span className="font-label-lg text-label-lg font-semibold">Fiduciary &amp; Legal Compliance</span>
                            </div>
                            <p className="font-body-md text-body-md text-on-surface-variant">
                                Compliant with state fiduciary trust statutes, probate court
                                archiving guidelines, and protected health directives under HIPAA.
                            </p>
                        </div>
                        <div className="space-y-space-xs">
                            <div className="flex items-center gap-2 text-primary">
                                <span className="material-symbols-outlined text-[22px] text-secondary">schedule</span>
                                <span className="font-label-lg text-label-lg font-semibold">Dedicated Concierge Support</span>
                            </div>
                            <p className="font-body-md text-body-md text-on-surface-variant">
                                Trained estate paralegals are available Monday – Friday, 8:00 AM –
                                8:00 PM EST to assist executors and appointed trustees.
                            </p>
                        </div>
                    </div>
                    <div className="pt-space-md flex flex-col md:flex-row items-center justify-between gap-space-md">
                        <div className="text-left">
                            <p className="font-body-md text-label-sm text-on-surface-variant">
                                © 2024 LegacyVault Estate Services Inc. All rights reserved.
                                Dedicated to compassionate family continuity.
                            </p>
                        </div>
                        <div className="flex items-center gap-space-md">
                            <a className="font-label-sm text-label-sm text-on-surface-variant hover:text-on-surface transition-colors" href="#">Privacy Policy</a>
                            <span className="text-outline-variant">•</span>
                            <a className="font-label-sm text-label-sm text-on-surface-variant hover:text-on-surface transition-colors" href="#">Terms of Fiduciary Service</a>
                            <span className="text-outline-variant">•</span>
                            <a className="font-label-sm text-label-sm text-on-surface-variant hover:text-on-surface transition-colors" href="#">Security &amp; Encryption</a>
                        </div>
                    </div>
                </div>
            </footer>
        </div>
    );
}