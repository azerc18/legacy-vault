// Vaulty: chú cú gác đêm đeo kính một mắt, ôm cuốn sổ (nhân vật gốc của LegacyVault)
// Giữ các class chibi-body / chibi-arm-r / chibi-eyes / chibi-mouth / chibi-antenna để assistant.css điều khiển animation
export default function ChibiAvatar({ action = 'idle', mini = false }) {
    return (
        <svg viewBox={mini ? '20 4 80 80' : '0 0 120 140'}
             className={mini ? 'chibi-mini' : `chibi chibi--${action}`}
             aria-hidden="true">
            <ellipse cx="60" cy="134" rx="30" ry="5" fill="#000" opacity=".12" />

            <g className="chibi-body" stroke="#2B1D14" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round">
                {/* chân */}
                <path d="M47 124 q-3 6 2 6 q2 0 3-4 q1 4 4 4 q3 0 1-6Z" fill="#F2A33A" />
                <path d="M64 124 q-3 6 2 6 q2 0 3-4 q1 4 4 4 q3 0 1-6Z" fill="#F2A33A" />

                {/* thân + bụng */}
                <ellipse cx="60" cy="92" rx="33" ry="34" fill="#7A5A43" />
                <ellipse cx="60" cy="98" rx="21" ry="24" fill="#F1E2C4" />
                <path d="M52 92 l3 3 l3-3 M62 92 l3 3 l3-3 M57 102 l3 3 l3-3 M52 112 l3 3 l3-3 M62 112 l3 3 l3-3"
                      fill="none" stroke="#C9A97A" strokeWidth="1.8" />

                {/* cánh phải: vẫy khi chào */}
                <g className="chibi-arm-r">
                    <path d="M88 76 q14 10 10 34 q-6 -4 -10 -2 q-2 -16 0 -32Z" fill="#5E4332" />
                </g>

                {/* tai + đầu */}
                <path d="M28 30 L24 8 L44 22Z" fill="#5E4332" />
                <path d="M92 30 L96 8 L76 22Z" fill="#5E4332" />
                <ellipse cx="60" cy="48" rx="38" ry="31" fill="#7A5A43" />

                {/* vùng quanh mắt */}
                <circle cx="45" cy="50" r="15" fill="#F1E2C4" />
                <circle cx="75" cy="50" r="15" fill="#F1E2C4" />

                {/* mắt: chớp */}
                <g className="chibi-eyes" stroke="none">
                    <circle cx="45" cy="50" r="8" fill="#2B1D14" />
                    <circle cx="75" cy="50" r="8" fill="#2B1D14" />
                    <circle cx="48" cy="47" r="2.6" fill="#fff" />
                    <circle cx="78" cy="47" r="2.6" fill="#fff" />
                </g>

                {/* kính một mắt + dây */}
                <circle cx="75" cy="50" r="13" fill="#fff" fillOpacity=".18" stroke="#D4A017" strokeWidth="3" />
                <path d="M86 57 Q100 72 90 84" fill="none" stroke="#D4A017" strokeWidth="1.8" strokeDasharray="2 3" />
                <circle className="chibi-antenna" cx="69" cy="43" r="2" fill="#fff" stroke="none" />

                {/* má hồng */}
                <ellipse cx="34" cy="64" rx="5" ry="2.5" fill="#F4A6A6" opacity=".8" stroke="none" />
                <ellipse cx="86" cy="64" rx="5" ry="2.5" fill="#F4A6A6" opacity=".8" stroke="none" />

                {/* mỏ: mấp máy khi nói */}
                <path className="chibi-mouth" d="M55 61 L65 61 L60 70Z" fill="#F2A33A" />

                {/* cuốn sổ có ổ khoá + cánh trái ôm sổ */}
                <g transform="rotate(-6 50 106)">
                    <rect x="34" y="90" width="30" height="34" rx="3" fill="#2F5D8A" />
                    <rect x="60" y="92" width="4" height="30" fill="#F7F1E3" strokeWidth="1.5" />
                    <circle cx="49" cy="103" r="3.5" fill="#D4A017" strokeWidth="1.5" />
                    <path d="M49 106 v6" stroke="#D4A017" strokeWidth="2.5" />
                </g>
                <path d="M31 80 q-11 13 -4 30 q8 0 12 -8 q2 -13 -8 -22Z" fill="#5E4332" />
            </g>
        </svg>
    );
}
