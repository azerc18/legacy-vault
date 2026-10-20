import React from 'react';
import { Link } from 'react-router-dom';
import { getUser } from '../auth/auth.js';

export default function NotFound() {
    const user = getUser();
    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface flex flex-col items-center justify-center px-5 text-center">
            <p className="font-headline-lg text-[64px] leading-none text-primary font-bold">404</p>
            <h1 className="mt-4 font-headline-md text-headline-md">Page not found</h1>
            <p className="mt-2 text-on-surface-variant">The page you are looking for does not exist or has been moved.</p>
            <Link to={user ? '/home' : '/'} className="mt-6 h-11 px-5 rounded-xl bg-primary text-on-primary font-bold inline-flex items-center">
                {user ? 'Back to home' : 'Go to homepage'}
            </Link>
        </div>
    );
}
