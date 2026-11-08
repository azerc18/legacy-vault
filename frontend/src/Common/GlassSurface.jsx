import React from 'react';

const MATERIAL_CLASS = {
    clear: 'lv-glass-clear',
    regular: 'lv-glass-regular',
    frosted: 'lv-glass-frosted',
    standard: 'lv-surface-standard',
    secure: 'lv-surface-secure',
};

/**
 * Thành phần vật liệu Apple-inspired Liquid Glass theo 5 phân tầng:
 * - clear: Floating dock / compact pills
 * - regular: Header banner / segmented controls
 * - frosted: Popover / contextual menus
 * - standard: Dashboard cards, tables, forms (mặc định)
 * - secure: Secret viewing, 2FA, destructive dialogs
 */
export default function GlassSurface({
    material = 'standard',
    as: Component = 'div',
    className = '',
    children,
    ...rest
}) {
    const matCls = MATERIAL_CLASS[material] || MATERIAL_CLASS.standard;
    return (
        <Component className={`${matCls} ${className}`.trim()} {...rest}>
            {children}
        </Component>
    );
}

