import React from 'react';

const MATERIAL_CLASS = {
    clear: 'lv-glass-clear',
    regular: 'lv-glass-regular',
    thick: 'lv-glass-thick',
    frosted: 'lv-glass-thick',
    tinted: 'lv-glass-tinted',
    standard: 'lv-glass-regular',
    secure: 'lv-glass-secure lv-surface-secure',
    inline: 'lv-glass-inline',
};

/**
 * Thành phần vật liệu Apple-inspired Liquid Glass theo 6 biến thể:
 * - clear: Floating dock / compact pills / floating controls
 * - regular: Header, sidebar, toolbar, standard cards & panels
 * - thick: Dialogs, sheets, large popovers, tables, data-heavy panels
 * - tinted: Active navigation capsule, primary action buttons, selected tabs, hero banner
 * - secure: Secret viewing, 2FA, recovery codes, auth & destructive dialogs
 * - inline: Inputs, selects, chips, badges, segmented controls, table rows
 */
export default function GlassSurface({
    material = 'regular',
    variant,
    interactive = false,
    selected = false,
    as: Component = 'div',
    className = '',
    children,
    ...rest
}) {
    const key = variant || material || 'regular';
    const matCls = MATERIAL_CLASS[key] || MATERIAL_CLASS.regular;
    const interactiveCls = interactive ? 'lv-glass-interactive' : '';
    const selectedCls = selected ? 'lv-glass-tinted' : '';
    return (
        <Component
            className={`${matCls} ${interactiveCls} ${selectedCls} ${className}`.trim()}
            {...rest}
        >
            {children}
        </Component>
    );
}
