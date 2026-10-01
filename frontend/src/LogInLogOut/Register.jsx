import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

export default function Register() {
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        password: '',
        passwordConfirm: ''
    });
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const handleRegister = (e) => {
        e.preventDefault();
        const { fullName, email, password, passwordConfirm } = formData;

        if (!fullName || !email || !password || !passwordConfirm) {
            setError('Vui lòng điền đầy đủ tất cả các trường.');
            return;
        }

        if (password.length < 6) {
            setError('Mật khẩu phải có ít nhất 6 ký tự.');
            return;
        }

        if (password !== passwordConfirm) {
            setError('Mật khẩu xác nhận không khớp.');
            return;
        }

        setError('');

        // TODO: Người số 1 sẽ tích hợp gọi API POST /api/auth/register ở đây
        console.log('Registering user:', { fullName, email, password });

        // Giả lập đăng ký thành công, chuyển hướng sang trang nhập OTP xác thực email
        navigate('/login');
    };

    return (
        <div className="bg-background min-h-screen font-body-md text-on-surface antialiased flex flex-col justify-center items-center px-4 py-8">
            {/* Brand Header */}
            <div className="mb-6 text-center">
                <div className="w-12 h-12 rounded-2xl bg-primary text-on-primary flex items-center justify-center shadow-sm mx-auto mb-3">
                    <span className="material-symbols-outlined text-[28px]">person_add</span>
                </div>
                <h1 className="font-headline-lg text-primary font-bold tracking-tight text-2xl">Tạo tài khoản LegacyVault</h1>
                <p className="font-body-sm text-on-surface-variant mt-1">Đăng ký tài khoản mới với phân quyền chủ sở hữu (Owner)</p>
            </div>

            {/* Register Card */}
            <div className="bg-surface-container-lowest rounded-2xl p-8 shadow-sm border border-outline-variant/30 max-w-[440px] w-full">
                {error && (
                    <div className="mb-4 p-3 rounded-lg bg-error-container text-on-error-container text-sm flex items-center gap-2">
                        <span className="material-symbols-outlined text-[18px]">error</span>
                        {error}
                    </div>
                )}

                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Họ và tên</label>
                        <input
                            type="text"
                            name="fullName"
                            placeholder="Nguyễn Văn A"
                            value={formData.fullName}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Địa chỉ Email</label>
                        <input
                            type="email"
                            name="email"
                            placeholder="name@example.com"
                            value={formData.email}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Mật khẩu</label>
                        <input
                            type="password"
                            name="password"
                            placeholder="Ít nhất 6 ký tự"
                            value={formData.password}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <div className="flex flex-col gap-1.5">
                        <label className="font-label-sm text-on-surface-variant font-semibold">Xác nhận mật khẩu</label>
                        <input
                            type="password"
                            name="passwordConfirm"
                            placeholder="Nhập lại mật khẩu"
                            value={formData.passwordConfirm}
                            onChange={handleChange}
                            className="w-full h-12 px-4 rounded-xl border border-outline-variant bg-background text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all"
                        />
                    </div>

                    <button
                        type="submit"
                        className="w-full h-12 mt-3 rounded-xl bg-primary text-on-primary font-label-md font-bold hover:bg-primary-container transition-all shadow-sm flex items-center justify-center gap-2"
                    >
                        <span className="material-symbols-outlined text-[18px]">how_to_reg</span>
                        Đăng ký tài khoản
                    </button>
                </form>

                <div className="mt-6 pt-6 border-t border-outline-variant/20 text-center">
                    <p className="text-sm text-on-surface-variant">
                        Đã có tài khoản?{' '}
                        <Link to="/login" className="text-secondary font-bold hover:underline">
                            Đăng nhập ngay
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    );
}