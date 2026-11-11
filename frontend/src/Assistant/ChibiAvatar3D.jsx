// Vaulty 3D: cú gác đêm đeo kính một mắt, ôm cuốn sổ có ổ khoá.
// Props: action = 'idle' | 'wave' | 'jump' | 'talk' | 'think', costume = id trong COSTUMES, mini
// Cần: npm i three
import React, { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import ChibiAvatar from './ChibiAvatar.jsx';

// Kích thước hiển thị (px) - chỉnh ở đây nếu muốn to/nhỏ hơn
const W = 120;   // to hơn (trước 88x108) cho dễ thấy, vẫn cùng tỉ lệ
const H = 148;

const FEET_Y = -1.17;   // mặt đất của mô hình
const HEAD_Y = 0.95;    // trục xoay của đầu (cổ)

const C = {
    brown: '#7A5A43', dark: '#5E4332', cream: '#F1E2C4', ink: '#2B1D14',
    orange: '#F2A33A', gold: '#D4A017', blue: '#2F5D8A', pink: '#F4A6A6', page: '#F7F1E3',
};

/* ------------------------------------------------------------------ */
/* Dựng mô hình (toàn bộ từ hình khối cơ bản, không cần file .glb)     */
/* ------------------------------------------------------------------ */
function buildOwl() {
    // Toon shading 3 bậc + viền đen (inverted hull) cho giống nét vẽ 2D gốc
    const ramp = new THREE.DataTexture(new Uint8Array([110, 190, 255]), 3, 1, THREE.RedFormat);
    ramp.minFilter = ramp.magFilter = THREE.NearestFilter;
    ramp.generateMipmaps = false;
    ramp.needsUpdate = true;

    // vật dùng chung: đánh dấu keep để không bị dispose khi đổi trang phục
    const outlineMat = new THREE.MeshBasicMaterial({ color: C.ink, side: THREE.BackSide });
    const sphere = new THREE.SphereGeometry(1, 32, 24);
    const box = new THREE.BoxGeometry(1, 1, 1);
    [outlineMat, sphere, box].forEach((o) => { o.userData.keep = true; });

    const toon = (color, extra) => new THREE.MeshToonMaterial({ color, gradientMap: ramp, ...extra });
    const basic = (color, opacity = 1) => new THREE.MeshBasicMaterial({
        color, transparent: opacity < 1, opacity, depthWrite: opacity >= 1, side: THREE.DoubleSide,
    });
    const mk = (geo, color, { s = [1, 1, 1], p = [0, 0, 0], r = [0, 0, 0], outline = true, mat } = {}) => {
        const m = new THREE.Mesh(geo, mat || toon(color));
        m.scale.set(...s);
        m.position.set(...p);
        m.rotation.set(...r);
        if (outline) {
            const o = new THREE.Mesh(geo, outlineMat);
            o.scale.setScalar(1.07);
            m.add(o);
        }
        return m;
    };

    const scene = new THREE.Scene();
    scene.add(new THREE.AmbientLight(0xffffff, 1.5));
    const key = new THREE.DirectionalLight(0xffffff, 2.0);
    key.position.set(2.5, 4, 5);
    scene.add(key);
    const rimLight = new THREE.DirectionalLight(0xbcd4ff, 0.7);
    rimLight.position.set(-4, 2, -3);
    scene.add(rimLight);

    // bóng dưới chân
    const shadow = new THREE.Mesh(
        new THREE.CircleGeometry(1, 32),
        new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.2, depthWrite: false }),
    );
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = FEET_Y + 0.01;
    shadow.scale.set(1.2, 1.2, 1.2);
    scene.add(shadow);

    // pivot ở chân (để squash/stretch khi nhảy), owl là thân chính
    const pivot = new THREE.Group();
    pivot.position.y = FEET_Y;
    scene.add(pivot);
    const owl = new THREE.Group();
    owl.position.y = -FEET_Y;
    pivot.add(owl);

    // --- thân + bụng + chân ---
    const bodyM = mk(sphere, C.brown, { s: [1, 1.05, 0.9] });
    const bellyM = mk(sphere, C.cream, { s: [0.66, 0.78, 0.5], p: [0, -0.08, 0.5], outline: false });
    const feetM = [-1, 1].map((sx) => mk(sphere, C.orange, { s: [0.3, 0.1, 0.4], p: [sx * 0.38, -1.07, 0.4] }));
    owl.add(bodyM, bellyM, ...feetM);

    // --- đầu (pivot ở cổ) ---
    const head = new THREE.Group();
    head.position.y = HEAD_Y;
    owl.add(head);
    const h = (x, y, z) => [x, y - HEAD_Y, z];   // toạ độ owl -> toạ độ trong đầu

    head.add(mk(sphere, C.brown, { s: [1.18, 0.95, 1.0], p: h(0, 1.2, 0.05) }));

    const earGeo = new THREE.ConeGeometry(0.34, 0.75, 12);
    const ears = [
        mk(earGeo, C.dark, { s: [1, 1, 0.6], p: h(-0.78, 2.1, 0), r: [0, 0, 0.45] }),
        mk(earGeo, C.dark, { s: [1, 1, 0.6], p: h(0.78, 2.1, 0), r: [0, 0, -0.45] }),
    ];
    head.add(...ears);

    // vùng quanh mắt + mắt (nhóm mắt scaleY để chớp)
    const eyes = [-1, 1].map((sx) => {
        head.add(mk(sphere, C.cream, { s: [0.46, 0.46, 0.2], p: h(sx * 0.42, 1.28, 0.88), outline: false }));
        const g = new THREE.Group();
        g.position.set(...h(sx * 0.42, 1.28, 0.98));
        g.add(mk(sphere, C.ink, { s: [0.22, 0.25, 0.14], outline: false, mat: basic(C.ink) }));
        g.add(mk(sphere, '#fff', { s: [0.07, 0.07, 0.05], p: [0.08, 0.09, 0.1], outline: false, mat: basic('#ffffff') }));
        head.add(g);
        return g;
    });

    // kính một mắt (bên phải màn hình = +x) + dây xích bằng các hạt vàng
    head.add(mk(new THREE.TorusGeometry(0.4, 0.05, 12, 40), C.gold, { p: h(0.42, 1.28, 1.12), outline: false }));
    const glass = new THREE.Mesh(new THREE.CircleGeometry(0.4, 32), basic('#ffffff', 0.16));
    glass.position.set(...h(0.42, 1.28, 1.16));
    head.add(glass);
    const glint = mk(sphere, '#fff', { s: [0.06, 0.06, 0.06], p: h(0.3, 1.45, 1.2), outline: false, mat: basic('#ffffff') });
    head.add(glint);
    const chain = new THREE.CatmullRomCurve3([
        new THREE.Vector3(0.7, 1.0, 1.12), new THREE.Vector3(1.15, 0.78, 0.6), new THREE.Vector3(1.05, 0.3, 0.5),
    ]).getPoints(9);
    const beadGeo = new THREE.SphereGeometry(0.035, 8, 8);
    const beadMat = toon(C.gold);
    chain.forEach((v) => {
        const b = new THREE.Mesh(beadGeo, beadMat);
        b.position.set(v.x, v.y - HEAD_Y, v.z);
        head.add(b);
    });

    // má hồng
    [-1, 1].forEach((sx) => head.add(mk(sphere, C.pink, {
        s: [0.17, 0.09, 0.05], p: h(sx * 0.78, 0.93, 0.76), r: [0, sx * 0.64, 0],
        outline: false, mat: basic(C.pink, 0.85),
    })));

    // mỏ (group để nhép khi nói)
    const beak = new THREE.Group();
    beak.position.set(...h(0, 1.02, 1.02));
    beak.rotation.x = 0.25;
    beak.add(mk(new THREE.ConeGeometry(0.15, 0.32, 6), C.orange, { p: [0, 0, 0.15], r: [Math.PI / 2, 0, 0] }));
    head.add(beak);

    // --- cánh phải (vẫy) ---
    const wingR = new THREE.Group();
    wingR.position.set(0.92, 0.75, 0.05);
    wingR.rotation.z = 0.12;
    const wingRM = mk(sphere, C.dark, { s: [0.2, 0.62, 0.4], p: [0.1, -0.5, 0] });
    wingR.add(wingRM);
    owl.add(wingR);

    // --- cuốn sổ + cánh trái ôm sổ ---
    const book = new THREE.Group();
    book.position.set(-0.28, -0.38, 1.02);
    book.rotation.z = 0.1;
    book.add(mk(box, C.blue, { s: [0.95, 1.05, 0.3] }));
    book.add(mk(box, C.page, { s: [0.1, 0.95, 0.24], p: [0.5, 0, 0], outline: false }));
    book.add(mk(new THREE.CylinderGeometry(0.13, 0.13, 0.05, 20), C.gold, { p: [-0.12, 0.1, 0.17], r: [Math.PI / 2, 0, 0], outline: false }));
    book.add(mk(new THREE.CylinderGeometry(0.04, 0.04, 0.03, 12), C.ink, { p: [-0.12, 0.12, 0.2], r: [Math.PI / 2, 0, 0], outline: false, mat: basic(C.ink) }));
    book.add(mk(box, C.ink, { s: [0.03, 0.1, 0.02], p: [-0.12, 0.05, 0.2], outline: false, mat: basic(C.ink) }));
    owl.add(book);

    const wingL = new THREE.Group();
    wingL.position.set(-0.92, 0.6, 0.12);
    wingL.rotation.set(-0.5, 0, 0.35);
    const wingLM = mk(sphere, C.dark, { s: [0.2, 0.55, 0.34], p: [-0.04, -0.45, 0.2] });
    wingL.add(wingLM);
    owl.add(wingL);

    // --- 3 chấm "đang nghĩ" bên cạnh đầu (chỉ hiện ở trạng thái think) ---
    const dots = [[1.35, 2.0, 0.3, 0.08], [1.6, 2.28, 0.3, 0.11], [1.88, 2.62, 0.3, 0.15]].map(([x, y, z, r]) => {
        const d = mk(sphere, '#F5D06B', { p: [x, y, z], outline: false, mat: basic('#F5D06B') });
        d.userData.r = r;
        d.visible = false;
        owl.add(d);
        return d;
    });

    // --- đốm sáng bung ra khi đổi trang phục ---
    const burstMat = new THREE.MeshBasicMaterial({ color: '#F5D06B' });
    const burstGeo = new THREE.SphereGeometry(1, 8, 8);
    const burst = Array.from({ length: 10 }, (_, i) => {
        const m = new THREE.Mesh(burstGeo, burstMat);
        const a = (i / 10) * Math.PI * 2;
        m.userData = { dx: Math.cos(a), dy: Math.sin(a) * 0.9, dz: (i % 3) * 0.3 };
        m.visible = false;
        scene.add(m);
        return m;
    });
    const burstState = { t0: -10, pending: false };

    /* ---------------- trang phục ---------------- */
    const cHead = new THREE.Group();   // đồ đội đầu (toạ độ trong đầu, dùng h())
    const cBody = new THREE.Group();   // áo choàng, khăn... (toạ độ owl)
    const cWing = new THREE.Group();   // đồ cầm ở cánh phải
    head.add(cHead);
    owl.add(cBody);
    wingR.add(cWing);

    const tints = {
        body: [bodyM.material], belly: [bellyM.material],
        wings: [wingRM.material, wingLM.material], feet: feetM.map((f) => f.material),
    };
    const defaults = new Map();
    Object.values(tints).flat().forEach((m) => defaults.set(m, m.color.getHex()));
    const sways = [];

    const sway = (obj, axis, amp, speed, phase = 0) => {
        sways.push({ obj, axis, base: obj.rotation[axis], amp, speed, phase });
    };
    const ring = (parent, { R, tube, y, sx = 1, sz = 1, z = 0, color }) => {
        const g = new THREE.Group();
        g.position.set(0, y, z);
        g.scale.set(sx, 1, sz);
        g.add(mk(new THREE.TorusGeometry(R, tube, 12, 40), color, { r: [Math.PI / 2, 0, 0], outline: false }));
        parent.add(g);
        return g;
    };
    // áo choàng mở phía trước, đung đưa nhẹ
    const cape = (color, { top = 0.95, bottom = 1.45, y0 = 0.4, y1 = -1.05 } = {}) => {
        const g = new THREE.Group();
        g.position.y = y0;
        const hgt = y0 - y1;
        const m = new THREE.Mesh(
            new THREE.CylinderGeometry(top, bottom, hgt, 32, 1, true, 1.4, Math.PI * 2 - 2.8),
            toon(color, { side: THREE.DoubleSide }),
        );
        m.position.y = -hgt / 2;
        m.scale.z = 0.95;
        g.add(m);
        cBody.add(g);
        sway(g, 'x', 0.05, 1.6);
        return g;
    };
    // mũ trùm đầu chừa mặt: vỏ hở phía trước + chỏm che trán
    const hood = (color, { open = 1.0, down = 2.25, cap = 0.85, s = [1.3, 1.08, 1.12], z = 0 } = {}) => {
        const g = new THREE.Group();
        g.position.set(...h(0, 1.2, z));
        const side = new THREE.SphereGeometry(1, 32, 20, Math.PI / 2 + open, Math.PI * 2 - open * 2, 0, down);
        const top = new THREE.SphereGeometry(1, 32, 12, 0, Math.PI * 2, 0, cap);
        [side, top].forEach((geo) => g.add(mk(geo, color, { s, mat: toon(color, { side: THREE.DoubleSide }) })));
        cHead.add(g);
        return g;
    };
    const x = {
        head: cHead, body: cBody, wing: cWing, mk, toon, basic, sphere, box, h, sway, ring, cape, hood,
        tint: (part, color) => tints[part].forEach((m) => m.color.set(color)),
        hideEars: () => ears.forEach((e) => { e.visible = false; }),
    };

    const builders = {
        wizard(k) {
            k.hideEars();
            const purple = '#5B3FA0';
            const hat = new THREE.Group();
            hat.position.set(...k.h(0, 1.9, 0.05));
            hat.rotation.set(-0.1, 0, -0.1);
            k.head.add(hat);
            hat.add(k.mk(new THREE.CylinderGeometry(1.3, 1.3, 0.07, 36), purple, { p: [0, 0.03, 0] }));
            hat.add(k.mk(new THREE.ConeGeometry(0.72, 0.95, 28), purple, { p: [0, 0.5, 0] }));
            hat.add(k.mk(new THREE.ConeGeometry(0.2, 0.42, 16), purple, { p: [0.1, 1.1, 0], r: [0, 0, -0.55] }));
            hat.add(k.mk(new THREE.CylinderGeometry(0.66, 0.69, 0.18, 28), C.gold, { p: [0, 0.14, 0], outline: false }));
            hat.add(k.mk(k.box, '#F5D06B', { s: [0.26, 0.2, 0.05], p: [0, 0.14, 0.7], outline: false }));
            [[0.12, 0.5, 0.36], [-0.04, 0.78, 0.16], [-0.2, 0.36, 0.44]].forEach(([a, b, c]) => hat.add(
                k.mk(k.sphere, '#F5D06B', { s: [0.05, 0.05, 0.05], p: [a, b, c], outline: false, mat: k.basic('#F5D06B') }),
            ));
            k.sway(hat, 'z', 0.03, 1.4);
            k.cape('#2C3E8F');
            k.ring(k.body, { R: 0.95, tube: 0.1, y: 0.38, sz: 0.98, color: '#2C3E8F' });
            k.body.add(k.mk(k.sphere, C.gold, { s: [0.1, 0.1, 0.1], p: [0, 0.3, 0.98] }));
        },
        king(k) {
            const crown = new THREE.Group();
            crown.position.set(...k.h(0, 2.08, 0.05));
            k.head.add(crown);
            crown.add(k.mk(new THREE.CylinderGeometry(0.5, 0.56, 0.3, 20), C.gold, { p: [0, 0.15, 0] }));
            for (let i = 0; i < 5; i += 1) {
                const a = (i / 5) * Math.PI * 2;
                crown.add(k.mk(new THREE.ConeGeometry(0.09, 0.3, 8), C.gold, { p: [Math.sin(a) * 0.5, 0.44, Math.cos(a) * 0.5] }));
            }
            crown.add(k.mk(k.sphere, '#E0383B', { s: [0.09, 0.09, 0.09], p: [0, 0.17, 0.56], outline: false, mat: k.basic('#E0383B') }));
            k.cape('#B3202A');
            k.ring(k.body, { R: 0.95, tube: 0.17, y: 0.38, sz: 0.98, color: C.page });
            for (let i = 0; i < 9; i += 1) {
                const a = -1.3 + (i / 8) * 2.6;
                k.body.add(k.mk(k.sphere, C.ink, {
                    s: [0.04, 0.04, 0.04], p: [Math.sin(a) * 1.08, 0.44, Math.cos(a) * 1.06], outline: false, mat: k.basic(C.ink),
                }));
            }
            k.body.add(k.mk(k.sphere, C.gold, { s: [0.13, 0.13, 0.1], p: [0, 0.3, 1.0] }));
            k.body.add(k.mk(k.sphere, '#E0383B', { s: [0.08, 0.08, 0.06], p: [0, 0.3, 1.08], outline: false, mat: k.basic('#E0383B') }));
        },
        detective(k) {
            k.hideEars();
            const brown = '#8A6A4A';
            const cap = new THREE.Group();
            cap.position.set(...k.h(0, 1.9, 0.05));
            k.head.add(cap);
            cap.add(k.mk(new THREE.SphereGeometry(1, 24, 14, 0, Math.PI * 2, 0, Math.PI / 2), brown, { s: [0.84, 0.52, 0.8] }));
            cap.add(k.mk(k.sphere, '#6F5238', { s: [0.42, 0.05, 0.34], p: [0, 0, 0.78], r: [0.25, 0, 0] }));
            cap.add(k.mk(k.sphere, '#6F5238', { s: [0.36, 0.05, 0.3], p: [0, 0, -0.76], r: [-0.25, 0, 0] }));
            cap.add(k.mk(k.sphere, '#4A2E1B', { s: [0.09, 0.09, 0.09], p: [0, 0.52, 0] }));
            k.cape('#8B6B47', { y1: -0.8 });
            k.ring(k.body, { R: 0.95, tube: 0.12, y: 0.38, sz: 0.98, color: '#8B6B47' });
            const bow = new THREE.Group();
            bow.position.set(0, 0.3, 1.0);
            bow.add(k.mk(new THREE.ConeGeometry(0.13, 0.24, 8), '#4A2E1B', { p: [-0.13, 0, 0], r: [0, 0, Math.PI / 2] }));
            bow.add(k.mk(new THREE.ConeGeometry(0.13, 0.24, 8), '#4A2E1B', { p: [0.13, 0, 0], r: [0, 0, -Math.PI / 2] }));
            bow.add(k.mk(k.sphere, '#4A2E1B', { s: [0.07, 0.07, 0.07] }));
            k.body.add(bow);
            // kính lúp ở cánh phải
            const lens = new THREE.Group();
            lens.position.set(0.22, -1.0, 0.32);
            lens.add(k.mk(new THREE.TorusGeometry(0.26, 0.05, 10, 28), '#3A2A1E', { outline: false }));
            const gl = new THREE.Mesh(new THREE.CircleGeometry(0.26, 24), k.basic('#8CC0F0', 0.5));
            gl.position.z = 0.01;
            lens.add(gl);
            lens.add(k.mk(new THREE.CylinderGeometry(0.04, 0.04, 0.4, 8), '#3A2A1E', { p: [-0.06, -0.42, 0], r: [0, 0, 0.15], outline: false }));
            k.wing.add(lens);
        },
        astronaut(k) {
            k.hideEars();
            k.tint('body', '#E6EAF6'); k.tint('belly', '#F7F9FD'); k.tint('wings', '#E6EAF6'); k.tint('feet', '#B9C3E0');
            const white = '#F4F6FC';
            const dome = k.mk(k.sphere, '#CFE6FF', { s: [1.32, 1.12, 1.16], p: k.h(0, 1.2, 0.05), outline: false, mat: k.basic('#CFE6FF', 0.3) });
            dome.renderOrder = 2;
            k.head.add(dome);
            k.ring(k.head, { R: 1.0, tube: 0.13, y: 0.34 - HEAD_Y, z: 0.05, sz: 0.9, color: white });
            const earG = new THREE.ConeGeometry(0.3, 0.55, 12);
            k.head.add(k.mk(earG, white, { s: [1, 1, 0.6], p: k.h(-0.8, 2.2, 0), r: [0, 0, 0.4] }));
            k.head.add(k.mk(earG, white, { s: [1, 1, 0.6], p: k.h(0.8, 2.2, 0), r: [0, 0, -0.4] }));
            k.ring(k.body, { R: 0.95, tube: 0.14, y: 0.38, sz: 0.98, color: white });
            k.body.add(k.mk(k.box, '#3C4F8C', { s: [0.38, 0.32, 0.08], p: [0.58, -0.12, 0.88], r: [0, -0.5, 0] }));
            k.body.add(k.mk(k.sphere, '#FF6B6B', { s: [0.04, 0.04, 0.03], p: [0.5, -0.06, 0.95], outline: false, mat: k.basic('#FF6B6B') }));
            k.body.add(k.mk(k.sphere, '#6BFF9A', { s: [0.04, 0.04, 0.03], p: [0.62, -0.06, 0.93], outline: false, mat: k.basic('#6BFF9A') }));
        },
        ninja(k) {
            k.tint('body', '#2A2D3A'); k.tint('belly', '#3A3F52'); k.tint('wings', '#1E2030'); k.tint('feet', '#2A2D3A');
            const navy = '#1F2A44';
            k.ring(k.head, { R: 1.0, tube: 0.1, y: 1.86 - HEAD_Y, z: 0.05, sx: 0.88, sz: 0.82, color: navy });
            k.head.add(k.mk(k.box, '#B8C0CC', { s: [0.46, 0.2, 0.06], p: k.h(0, 1.86, 0.9) }));
            [-0.1, 0.1].forEach((dx, i) => {
                const t = new THREE.Group();
                t.position.set(...k.h(dx, 1.86, -0.78));
                t.add(k.mk(k.box, navy, { s: [0.14, 0.7, 0.03], p: [0, -0.35, 0], outline: false }));
                k.head.add(t);
                k.sway(t, 'x', 0.25, 3, i);
            });
            k.ring(k.body, { R: 0.95, tube: 0.14, y: 0.38, sz: 0.98, color: '#C4262E' });
            const tail = new THREE.Group();
            tail.position.set(-0.9, 0.45, -0.2);
            tail.add(k.mk(k.box, '#C4262E', { s: [1.0, 0.2, 0.05], p: [-0.5, 0, 0], outline: false }));
            tail.add(k.mk(k.box, '#C4262E', { s: [0.8, 0.18, 0.05], p: [-0.4, -0.2, 0], r: [0, 0, -0.15], outline: false }));
            k.body.add(tail);
            k.sway(tail, 'z', 0.14, 5);
            k.sway(tail, 'y', 0.1, 3, 1);
            const star = new THREE.Group();
            star.position.set(0.22, -1.0, 0.28);
            star.add(k.mk(k.box, '#9AA3B0', { s: [0.5, 0.5, 0.04], outline: false }));
            star.add(k.mk(k.box, '#9AA3B0', { s: [0.5, 0.5, 0.04], r: [0, 0, Math.PI / 4], outline: false }));
            star.add(k.mk(k.sphere, C.ink, { s: [0.07, 0.07, 0.06], p: [0, 0, 0.03], outline: false, mat: k.basic(C.ink) }));
            k.wing.add(star);
        },
        graduate(k) {
            k.hideEars();
            const navy = '#1B2340';
            const cap = new THREE.Group();
            cap.position.set(...k.h(0, 1.98, 0.05));
            k.head.add(cap);
            cap.add(k.mk(new THREE.CylinderGeometry(0.62, 0.66, 0.34, 24), navy, { p: [0, 0.1, 0] }));
            cap.add(k.mk(k.box, navy, { s: [1.9, 0.09, 1.9], p: [0, 0.3, 0], r: [0, Math.PI / 4, 0] }));
            cap.add(k.mk(k.sphere, C.gold, { s: [0.07, 0.07, 0.07], p: [0, 0.38, 0], outline: false }));
            cap.add(k.mk(new THREE.CylinderGeometry(0.025, 0.025, 1.3, 6), C.gold, { p: [-0.65, 0.37, 0], r: [0, 0, Math.PI / 2], outline: false }));
            const tass = new THREE.Group();
            tass.position.set(-1.3, 0.37, 0);
            tass.add(k.mk(new THREE.CylinderGeometry(0.03, 0.03, 0.5, 6), C.gold, { p: [0, -0.25, 0], outline: false }));
            tass.add(k.mk(new THREE.CylinderGeometry(0.07, 0.11, 0.3, 10), C.gold, { p: [0, -0.62, 0] }));
            cap.add(tass);
            k.sway(tass, 'z', 0.1, 2.2);
            k.cape('#1F2A55');
            k.ring(k.body, { R: 0.95, tube: 0.13, y: 0.38, sz: 0.98, color: '#1F2A55' });
            k.ring(k.body, { R: 0.99, tube: 0.04, y: 0.5, sz: 0.98, color: C.gold });
            k.body.add(k.mk(k.sphere, C.gold, { s: [0.07, 0.07, 0.06], p: [0, 0.32, 1.0], outline: false }));
            [-0.06, 0.06].forEach((dx) => k.body.add(k.mk(k.box, C.gold, { s: [0.08, 0.3, 0.03], p: [dx, 0.14, 1.0], r: [0, 0, dx * 4], outline: false })));
        },
        santa(k) {
            k.hideEars();
            k.tint('body', '#C62E37'); k.tint('wings', '#B8262F'); k.tint('belly', '#F7F1E3');
            const hat = new THREE.Group();
            hat.position.set(...k.h(0, 1.88, 0.05));
            k.head.add(hat);
            k.ring(hat, { R: 0.9, tube: 0.17, y: 0, sx: 0.92, sz: 0.88, color: C.page });
            const tilt = new THREE.Group();
            tilt.rotation.z = 0.45;
            tilt.add(k.mk(new THREE.ConeGeometry(0.8, 1.2, 24), '#D6303A', { p: [0, 0.6, 0] }));
            tilt.add(k.mk(k.sphere, C.page, { s: [0.2, 0.2, 0.2], p: [0, 1.25, 0] }));
            hat.add(tilt);
            k.sway(tilt, 'z', 0.06, 1.8);
            k.ring(k.body, { R: 0.95, tube: 0.17, y: 0.38, sz: 0.98, color: C.page });
            const sack = new THREE.Group();
            sack.position.set(-1.05, -0.6, -0.35);
            sack.add(k.mk(k.sphere, '#C62E37', { s: [0.55, 0.6, 0.5] }));
            sack.add(k.mk(k.sphere, '#C62E37', { s: [0.18, 0.18, 0.18], p: [0, 0.62, 0] }));
            k.body.add(sack);
        },
        chef(k) {
            k.hideEars();
            const white = '#FBFBFD';
            k.tint('body', '#F6F6FA'); k.tint('belly', '#F6F6FA'); k.tint('wings', '#F6F6FA');
            const hat = new THREE.Group();
            hat.position.set(...k.h(0, 1.95, 0.05));
            k.head.add(hat);
            hat.add(k.mk(new THREE.CylinderGeometry(0.56, 0.5, 0.42, 24), white, { p: [0, 0.2, 0] }));
            [[0, 0.78, 0, 0.5], [-0.42, 0.62, 0, 0.4], [0.42, 0.62, 0, 0.4], [0, 0.62, 0.4, 0.38], [0, 0.62, -0.4, 0.38]]
                .forEach(([a, b, c, r]) => hat.add(k.mk(k.sphere, white, { s: [r, r * 0.9, r], p: [a, b, c] })));
            k.sway(hat, 'z', 0.025, 1.5);
            k.ring(k.body, { R: 0.95, tube: 0.12, y: 0.38, sz: 0.98, color: '#C8323B' });
            k.body.add(k.mk(k.sphere, '#C8323B', { s: [0.14, 0.12, 0.1], p: [0.02, 0.26, 1.0] }));
            [-1, 1].forEach((sx) => k.body.add(k.mk(new THREE.ConeGeometry(0.1, 0.3, 6), '#C8323B', {
                p: [sx * 0.13, 0.12, 1.0], r: [0, 0, Math.PI - sx * 0.3], outline: false,
            })));
            const spoon = new THREE.Group();
            spoon.position.set(0.3, -0.7, 0.3);
            spoon.rotation.z = -0.12;
            k.wing.add(spoon);
            spoon.add(k.mk(new THREE.CylinderGeometry(0.045, 0.045, 1.0, 8), '#9A6B3A', { outline: false }));
            spoon.add(k.mk(k.sphere, '#A9753F', { s: [0.17, 0.24, 0.06], p: [0, 0.62, 0] }));
        },
        knight(k) {
            k.hideEars();
            const steel = '#B9C0CC';
            k.tint('body', steel); k.tint('belly', '#CBD1DB'); k.tint('wings', '#A5ADBB'); k.tint('feet', '#8A92A0');
            k.hood(steel, { open: 1.0, down: 2.1 });
            [-0.3, 0, 0.3].forEach((dx) => k.head.add(k.mk(k.box, '#2B2F3A', {
                s: [0.07, 0.22, 0.05], p: k.h(dx, 2.1, 0.64 - Math.abs(dx) * 0.25), r: [-0.5, 0, 0], outline: false,
            })));
            const plume = new THREE.Group();
            plume.position.set(...k.h(0, 2.3, -0.1));
            k.head.add(plume);
            [[0, 0.25, 0, 0.5], [-0.18, 0.18, -0.1, 0.8], [0.02, 0.3, -0.2, 0.3]].forEach(([a, b, c, rz]) => plume.add(
                k.mk(k.sphere, '#C0262D', { s: [0.12, 0.5, 0.1], p: [a, b, c], r: [0, 0, rz], outline: false }),
            ));
            k.sway(plume, 'z', 0.08, 2.2);
            k.ring(k.body, { R: 0.95, tube: 0.13, y: 0.38, sz: 0.98, color: steel });
            k.cape('#B3202A');
            const shield = new THREE.Group();
            shield.position.set(-1.02, -0.1, 0.5);
            shield.rotation.set(0, -0.35, 0.08);
            k.body.add(shield);
            shield.add(k.mk(k.box, '#D4A017', { s: [0.66, 0.8, 0.07] }));
            shield.add(k.mk(k.box, '#3E6FB0', { s: [0.54, 0.68, 0.09], outline: false }));
            shield.add(k.mk(k.sphere, '#F5D06B', { s: [0.1, 0.18, 0.03], p: [0, 0.02, 0.06], outline: false }));
        },
        devil(k) {
            k.hideEars();
            const dark = '#2B2233', red = '#D0252F';
            k.tint('wings', dark);
            k.hood(dark, { open: 1.0, down: 2.25 });
            [-1, 1].forEach((sx) => k.head.add(k.mk(new THREE.ConeGeometry(0.14, 0.5, 10), red, {
                p: k.h(sx * 0.62, 2.25, 0.05), r: [0, 0, -sx * 0.35],
            })));
            k.cape(dark, { bottom: 1.6 });
            k.ring(k.body, { R: 0.95, tube: 0.12, y: 0.38, sz: 0.98, color: dark });
            const bow = new THREE.Group();
            bow.position.set(0, 0.3, 1.0);
            bow.add(k.mk(new THREE.ConeGeometry(0.13, 0.24, 8), red, { p: [-0.13, 0, 0], r: [0, 0, Math.PI / 2] }));
            bow.add(k.mk(new THREE.ConeGeometry(0.13, 0.24, 8), red, { p: [0.13, 0, 0], r: [0, 0, -Math.PI / 2] }));
            bow.add(k.mk(k.sphere, red, { s: [0.07, 0.07, 0.07] }));
            k.body.add(bow);
            // cánh dơi
            const shape = new THREE.Shape();
            [[0, 0], [0.4, 0.55], [0.75, 0.4], [0.95, 0.7], [1.15, 0.1], [0.8, 0.15], [0.85, -0.3], [0.45, 0], [0.3, -0.4], [0, -0.2]]
                .forEach(([a, b], i) => (i ? shape.lineTo(a, b) : shape.moveTo(a, b)));
            const batGeo = new THREE.ShapeGeometry(shape);
            [-1, 1].forEach((sx, i) => {
                const w = new THREE.Group();
                w.position.set(sx * 0.55, 0.35, -0.75);
                w.rotation.y = sx * 0.7;
                w.scale.x = sx;
                w.add(k.mk(batGeo, '#8E1B2B', { outline: false, mat: k.toon('#8E1B2B', { side: THREE.DoubleSide }) }));
                k.body.add(w);
                k.sway(w, 'z', 0.1, 4, i);
            });
            // đinh ba
            const tri = new THREE.Group();
            tri.position.set(0.32, -0.2, 0.28);
            k.wing.add(tri);
            tri.add(k.mk(new THREE.CylinderGeometry(0.04, 0.04, 1.8, 8), '#7A2020', { outline: false }));
            [[0, 0.98, 0.34], [-0.14, 0.92, 0.26], [0.14, 0.92, 0.26]].forEach(([a, b, hh]) => tri.add(
                k.mk(new THREE.ConeGeometry(0.05, hh, 6), red, { p: [a, b, 0], outline: false }),
            ));
            tri.add(k.mk(k.box, red, { s: [0.34, 0.05, 0.04], p: [0, 0.85, 0], outline: false }));
        },
        spy(k) {
            k.hideEars();
            const coat = '#2A2A30', hatC = '#3A3A40', black = '#15151A';
            k.tint('body', coat); k.tint('belly', '#F2F2F5'); k.tint('wings', coat);
            const hat = new THREE.Group();
            hat.position.set(...k.h(0, 1.92, 0.05));
            hat.rotation.set(-0.05, 0, 0.08);
            k.head.add(hat);
            hat.add(k.mk(new THREE.CylinderGeometry(1.05, 1.05, 0.05, 36), hatC, { p: [0, 0.02, 0] }));
            hat.add(k.mk(new THREE.CylinderGeometry(0.62, 0.7, 0.5, 28), hatC, { p: [0, 0.28, 0] }));
            hat.add(k.mk(new THREE.CylinderGeometry(0.71, 0.71, 0.14, 28), '#8A8A92', { p: [0, 0.1, 0], outline: false }));
            k.head.add(k.mk(k.sphere, black, { s: [0.44, 0.32, 0.05], p: k.h(-0.42, 1.28, 1.12), outline: false, mat: k.basic(black) }));
            const lens = new THREE.Mesh(new THREE.CircleGeometry(0.4, 28), k.basic('#1B1B22', 0.8));
            lens.position.set(...k.h(0.42, 1.28, 1.2));
            k.head.add(lens);
            k.head.add(k.mk(k.box, black, { s: [0.3, 0.06, 0.04], p: k.h(0, 1.3, 1.1), outline: false, mat: k.basic(black) }));
            k.cape(coat, { y1: -0.85 });
            k.ring(k.body, { R: 0.95, tube: 0.1, y: 0.38, sz: 0.98, color: '#F2F2F5' });
            [-1, 1].forEach((sx) => k.body.add(k.mk(k.box, coat, {
                s: [0.42, 0.34, 0.08], p: [sx * 0.42, 0.42, 0.95], r: [0, -sx * 0.3, -sx * 0.5],
            })));
            k.body.add(k.mk(k.box, black, { s: [0.1, 0.42, 0.04], p: [0, 0.12, 1.0], outline: false, mat: k.basic(black) }));
        },
        panda(k) {
            k.hideEars();
            const white = '#F8F8FA', black = '#26262B';
            k.tint('body', white); k.tint('belly', white); k.tint('wings', black); k.tint('feet', black);
            k.hood(white, { open: 1.0, down: 2.25 });
            [-1, 1].forEach((sx) => k.head.add(k.mk(k.sphere, black, { s: [0.34, 0.34, 0.2], p: k.h(sx * 0.85, 2.05, 0) })));
            k.head.add(k.mk(k.sphere, black, { s: [0.12, 0.08, 0.05], p: k.h(0, 2.08, 0.72), outline: false, mat: k.basic(black) }));
            k.ring(k.body, { R: 0.95, tube: 0.15, y: 0.38, sz: 0.98, color: white });
            const paw = (a, b, sx, sy, sz) => k.body.add(k.mk(k.sphere, black, { s: [sx, sy, sz], p: [a, b, 0.92], outline: false, mat: k.basic(black) }));
            paw(0.45, -0.62, 0.12, 0.09, 0.03);
            [[0.3, -0.45], [0.4, -0.4], [0.5, -0.4], [0.6, -0.45]].forEach(([a, b]) => paw(a, b, 0.045, 0.05, 0.03));
        },
        whitemage(k) {
            k.hideEars();
            const white = '#F4F1EA';
            k.tint('wings', white);
            k.hood(white, { open: 1.0, down: 2.3, s: [1.32, 1.1, 1.14] });
            k.head.add(k.mk(k.sphere, C.gold, { s: [0.13, 0.13, 0.05], p: k.h(0, 2.02, 0.74) }));
            k.head.add(k.mk(k.sphere, '#4AA3FF', { s: [0.07, 0.07, 0.04], p: k.h(0, 2.02, 0.79), outline: false, mat: k.basic('#4AA3FF') }));
            k.cape(white, { bottom: 1.55 });
            k.ring(k.body, { R: 0.95, tube: 0.14, y: 0.38, sz: 0.98, color: white });
            k.ring(k.body, { R: 0.99, tube: 0.035, y: 0.48, sz: 0.98, color: C.gold });
            k.body.add(k.mk(k.sphere, C.gold, { s: [0.12, 0.12, 0.08], p: [0, 0.3, 1.0] }));
            k.body.add(k.mk(k.sphere, '#4AA3FF', { s: [0.06, 0.06, 0.04], p: [0, 0.3, 1.07], outline: false, mat: k.basic('#4AA3FF') }));
            const staff = new THREE.Group();
            staff.position.set(0.34, -0.2, 0.28);
            k.wing.add(staff);
            staff.add(k.mk(new THREE.CylinderGeometry(0.045, 0.045, 1.9, 8), C.gold, { outline: false }));
            staff.add(k.mk(k.sphere, '#7CC4FF', { s: [0.17, 0.17, 0.17], p: [0, 1.08, 0], outline: false, mat: k.basic('#7CC4FF') }));
            staff.add(k.mk(new THREE.TorusGeometry(0.22, 0.03, 8, 24), C.gold, { p: [0, 0.98, 0], r: [Math.PI / 2, 0, 0], outline: false }));
        },
        beach(k) {
            k.hideEars();
            const straw = '#E2B96B', brownD = '#3A2418';
            k.tint('body', '#2F86D0'); k.tint('belly', '#3C93DB'); k.tint('wings', '#2F86D0');
            const hat = new THREE.Group();
            hat.position.set(...k.h(0, 1.9, 0.05));
            hat.rotation.z = -0.06;
            k.head.add(hat);
            hat.add(k.mk(new THREE.CylinderGeometry(1.4, 1.4, 0.06, 40), straw, { p: [0, 0.03, 0] }));
            hat.add(k.mk(new THREE.SphereGeometry(1, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), straw, { s: [0.68, 0.5, 0.68], p: [0, 0.05, 0] }));
            hat.add(k.mk(new THREE.CylinderGeometry(0.69, 0.72, 0.16, 28), '#2D6FB3', { p: [0, 0.14, 0], outline: false }));
            // hoa hướng dương
            const flower = new THREE.Group();
            flower.position.set(-0.55, 0.22, 0.55);
            flower.rotation.set(-0.5, -0.4, 0);
            for (let i = 0; i < 8; i += 1) {
                const a = (i / 8) * Math.PI * 2;
                flower.add(k.mk(k.sphere, '#F7C531', { s: [0.07, 0.12, 0.03], p: [Math.sin(a) * 0.17, Math.cos(a) * 0.17, 0], r: [0, 0, -a], outline: false }));
            }
            flower.add(k.mk(k.sphere, '#6B4423', { s: [0.1, 0.1, 0.05], p: [0, 0, 0.02], outline: false }));
            hat.add(flower);
            // kính râm gác trên mũ
            [0.05, 0.45].forEach((dx) => hat.add(k.mk(k.sphere, brownD, { s: [0.22, 0.16, 0.04], p: [dx, 0.4, 0.5], r: [-0.9, 0, 0], outline: false })));
            hat.add(k.mk(k.box, brownD, { s: [0.2, 0.04, 0.03], p: [0.25, 0.4, 0.5], r: [-0.9, 0, 0], outline: false }));
            k.ring(k.body, { R: 0.95, tube: 0.1, y: 0.38, sz: 0.98, color: '#CFE8FB' });
            // hoa văn áo
            [[0.35, 0.1], [0.5, -0.2], [0.3, -0.42], [0.1, 0.22], [0.52, 0.05]].forEach(([a, b]) => {
                const zz = 0.5 + 0.5 * Math.sqrt(Math.max(0, 1 - (a / 0.66) ** 2 - ((b + 0.08) / 0.78) ** 2)) + 0.02;
                k.body.add(k.mk(k.sphere, '#FFFFFF', { s: [0.07, 0.07, 0.025], p: [a, b, zz], outline: false, mat: k.basic('#FFFFFF') }));
            });
            // ly dừa
            const coco = new THREE.Group();
            coco.position.set(0.28, -1.0, 0.34);
            k.wing.add(coco);
            coco.add(k.mk(k.sphere, '#6B4A2B', { s: [0.22, 0.22, 0.22] }));
            coco.add(k.mk(new THREE.CylinderGeometry(0.17, 0.17, 0.03, 16), '#8BC34A', { p: [0, 0.2, 0], outline: false }));
            coco.add(k.mk(new THREE.CylinderGeometry(0.02, 0.02, 0.4, 6), '#FF8FB1', { p: [0.06, 0.38, 0], r: [0, 0, -0.2], outline: false }));
        },
        fox(k) {
            k.hideEars();
            const orange = '#E8833A', cream = '#F7E6C8';
            k.tint('body', orange); k.tint('belly', cream); k.tint('wings', '#C8642B'); k.tint('feet', '#4A3426');
            k.hood(orange, { open: 1.0, down: 2.25 });
            [-1, 1].forEach((sx) => {
                k.head.add(k.mk(new THREE.ConeGeometry(0.3, 0.6, 12), orange, { s: [1, 1, 0.6], p: k.h(sx * 0.72, 2.28, 0), r: [0, 0, -sx * 0.3] }));
                k.head.add(k.mk(new THREE.ConeGeometry(0.17, 0.4, 10), cream, { s: [1, 1, 0.5], p: k.h(sx * 0.71, 2.25, 0.1), r: [0, 0, -sx * 0.3], outline: false }));
            });
            k.head.add(k.mk(k.sphere, cream, { s: [0.5, 0.2, 0.08], p: k.h(0, 2.0, 0.7), r: [-0.5, 0, 0], outline: false }));
            k.head.add(k.mk(k.sphere, C.ink, { s: [0.07, 0.05, 0.04], p: k.h(0, 2.06, 0.78), outline: false, mat: k.basic(C.ink) }));
            [-1, 1].forEach((sx) => k.head.add(k.mk(k.sphere, C.ink, { s: [0.04, 0.05, 0.03], p: k.h(sx * 0.2, 2.0, 0.76), outline: false, mat: k.basic(C.ink) })));
            k.ring(k.body, { R: 0.95, tube: 0.15, y: 0.38, sz: 0.98, color: '#C4262E' });
            const tail = new THREE.Group();
            tail.position.set(-0.7, -0.7, -0.5);
            tail.rotation.set(0, 0.5, 0.25);
            tail.add(k.mk(k.sphere, orange, { s: [0.75, 0.38, 0.38], p: [-0.4, 0.1, 0] }));
            tail.add(k.mk(k.sphere, '#FFF6E8', { s: [0.32, 0.3, 0.3], p: [-1.05, 0.2, 0] }));
            k.body.add(tail);
            k.sway(tail, 'y', 0.2, 2.4, 0);
        },
        soccer(k) {
            const blue = '#1F3FA8';
            k.tint('body', blue); k.tint('belly', blue); k.tint('wings', blue); k.tint('feet', '#F2F2F5');
            k.body.add(k.mk(new THREE.CylinderGeometry(0.94, 0.52, 0.5, 24), '#14297A', { p: [0, -0.69, 0], s: [1, 1, 0.93] }));
            // số 10 trên ngực (vẽ bằng canvas)
            const cv = document.createElement('canvas');
            cv.width = 128; cv.height = 96;
            const g2 = cv.getContext('2d');
            g2.font = 'bold 80px sans-serif';
            g2.textAlign = 'center';
            g2.textBaseline = 'middle';
            g2.fillStyle = '#FFFFFF';
            g2.fillText('10', 64, 52);
            const tex = new THREE.CanvasTexture(cv);
            tex.colorSpace = THREE.SRGBColorSpace;
            const num = new THREE.Mesh(new THREE.PlaneGeometry(0.5, 0.38), new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false }));
            num.position.set(0.42, 0.0, 0.96);
            num.rotation.y = 0.35;
            k.body.add(num);
            // quả bóng
            const ball = new THREE.Group();
            ball.position.set(-0.95, -0.8, 0.8);
            k.body.add(ball);
            ball.add(k.mk(k.sphere, '#FFFFFF', { s: [0.38, 0.38, 0.38] }));
            [[0, 0, 1], [0.85, 0.35, 0.4], [-0.85, 0.3, 0.4], [0.3, -0.9, 0.3], [-0.2, 0.95, 0.2]].forEach(([a, b, c]) => {
                const n = Math.hypot(a, b, c);
                ball.add(k.mk(k.sphere, '#222222', { s: [0.1, 0.1, 0.1], p: [(a / n) * 0.37, (b / n) * 0.37, (c / n) * 0.37], outline: false, mat: k.basic('#222222') }));
            });
        },
        cyber(k) {
            k.hideEars();
            const dark = '#1E1633', neon = '#3DE6FF', violet = '#7A4DFF';
            k.tint('body', dark); k.tint('belly', '#2A1F47'); k.tint('wings', dark); k.tint('feet', '#2A1F47');
            k.hood(dark, { open: 1.0, down: 2.25 });
            [-1, 1].forEach((sx) => {
                k.head.add(k.mk(new THREE.ConeGeometry(0.3, 0.6, 12), dark, { s: [1, 1, 0.6], p: k.h(sx * 0.78, 2.22, 0), r: [0, 0, -sx * 0.4] }));
                k.head.add(k.mk(new THREE.ConeGeometry(0.15, 0.36, 10), neon, { s: [1, 1, 0.5], p: k.h(sx * 0.76, 2.2, 0.08), r: [0, 0, -sx * 0.4], outline: false, mat: k.basic(neon) }));
                k.head.add(k.mk(new THREE.CylinderGeometry(0.3, 0.3, 0.22, 20), '#2A1F47', { p: k.h(sx * 1.36, 1.2, 0.05), r: [0, 0, Math.PI / 2] }));
                k.head.add(k.mk(new THREE.TorusGeometry(0.2, 0.035, 8, 24), neon, { p: k.h(sx * 1.48, 1.2, 0.05), r: [0, Math.PI / 2, 0], outline: false, mat: k.basic(neon) }));
            });
            k.head.add(k.mk(new THREE.TorusGeometry(1.38, 0.07, 8, 32, Math.PI), violet, { s: [1, 0.8, 1], p: k.h(0, 1.2, 0.05), outline: false, mat: k.basic(violet) }));
            k.cape('#1A1230', { bottom: 1.4 });
            k.ring(k.body, { R: 0.95, tube: 0.13, y: 0.38, sz: 0.98, color: dark });
            k.ring(k.body, { R: 0.99, tube: 0.035, y: 0.47, sz: 0.98, color: neon });
        },
        fpt(k) {
            const ORANGE = '#F26B1D', CREAM = '#F6F1E8', NAVY = '#1F2A55', BLACK = '#1E1F26';
            // --- tiện ích vẽ chữ/logo bằng canvas ---
            const canvasTex = (w, hh, draw) => {
                const cv = document.createElement('canvas');
                cv.width = w; cv.height = hh;
                draw(cv.getContext('2d'), w, hh);
                const t = new THREE.CanvasTexture(cv);
                t.colorSpace = THREE.SRGBColorSpace;
                return t;
            };
            const plane = (w, hh, map) => new THREE.Mesh(
                new THREE.PlaneGeometry(w, hh),
                new THREE.MeshBasicMaterial({ map, transparent: true, depthWrite: false, side: THREE.DoubleSide }),
            );
            const logoTex = canvasTex(384, 160, (g) => {
                g.scale(2, 2);
                [['F', '#2D5FA8'], ['P', ORANGE], ['T', '#3AA33C']].forEach(([ch, col], i) => {
                    g.fillStyle = col;
                    g.fillRect(8 + i * 60, 8, 56, 64);
                    g.fillStyle = '#FFFFFF';
                    g.font = 'bold 46px sans-serif';
                    g.textAlign = 'center';
                    g.textBaseline = 'middle';
                    g.fillText(ch, 36 + i * 60, 42);
                });
            });
            const bookTex = canvasTex(256, 110, (g) => {
                g.fillStyle = '#FFFFFF';
                g.textAlign = 'center';
                g.textBaseline = 'middle';
                g.font = 'bold 56px sans-serif';
                g.fillText('FPT', 128, 30);
                g.font = 'bold 34px sans-serif';
                g.fillText('UNIVERSITY', 128, 80);
            });
            const flagTex = canvasTex(256, 150, (g) => {
                g.fillStyle = ORANGE;
                g.beginPath();
                g.moveTo(0, 0); g.lineTo(256, 0); g.lineTo(256, 150); g.lineTo(0, 150); g.lineTo(34, 75);
                g.closePath();
                g.fill();
                g.fillStyle = '#FFFFFF';
                g.textAlign = 'center';
                g.textBaseline = 'middle';
                g.font = 'bold 60px sans-serif';
                g.fillText('FPT', 150, 52);
                g.font = 'bold 32px sans-serif';
                g.fillText('UNIVERSITY', 150, 102);
            });

            // --- nón lưỡi trai trắng, lưỡi cam, xoay lệch sang trái ---
            const cap = new THREE.Group();
            cap.position.set(...k.h(0, 1.97, 0.05));
            k.head.add(cap);
            cap.add(k.mk(new THREE.SphereGeometry(1, 28, 14, 0, Math.PI * 2, 0, Math.PI / 2), CREAM, { s: [0.8, 0.58, 0.82] }));
            cap.add(k.mk(k.sphere, ORANGE, { s: [0.5, 0.04, 0.56], p: [0, -0.02, 0.84], r: [0.2, 0, 0] }));
            cap.add(k.mk(k.sphere, CREAM, { s: [0.46, 0.035, 0.5], p: [0, 0.015, 0.84], r: [0.2, 0, 0], outline: false }));
            cap.add(k.mk(k.box, ORANGE, { s: [0.58, 0.16, 0.07], p: [0, 0.1, -0.78], outline: false }));
            const capLogo = plane(0.64, 0.27, logoTex);
            capLogo.position.set(0, 0.3, 0.8);
            capLogo.rotation.x = -0.25;
            cap.add(capLogo);
            k.sway(cap, 'z', 0.02, 1.4);

            // --- áo polo cam ---
            k.tint('body', ORANGE); k.tint('belly', '#F47F2B'); k.tint('feet', '#F7F3EA');
            k.ring(k.body, { R: 0.95, tube: 0.1, y: 0.38, sz: 0.98, color: ORANGE });
            [-1, 1].forEach((sx) => k.body.add(k.mk(k.box, ORANGE, {
                s: [0.3, 0.22, 0.06], p: [sx * 0.3, 0.38, 0.98], r: [0, -sx * 0.2, -sx * 0.45],
            })));
            k.wing.add(k.mk(k.sphere, ORANGE, { s: [0.24, 0.27, 0.42], p: [0.1, -0.12, 0] }));
            k.body.add(k.mk(k.sphere, ORANGE, { s: [0.25, 0.27, 0.4], p: [-0.96, 0.5, 0.14] }));
            const chestLogo = plane(0.2, 0.085, logoTex);
            chestLogo.position.set(0.5, 0.12, 0.93);
            chestLogo.rotation.y = 0.25;
            k.body.add(chestLogo);

            // --- dây đeo + thẻ sinh viên ---
            const strap = (x1, y1, x2, y2) => {
                const dx = x2 - x1, dy = y2 - y1;
                k.body.add(k.mk(k.box, NAVY, {
                    s: [0.04, Math.hypot(dx, dy), 0.02], p: [(x1 + x2) / 2, (y1 + y2) / 2, 0.99],
                    r: [0, 0, Math.atan2(-dx, dy)], outline: false,
                }));
            };
            strap(-0.22, 0.4, 0.38, -0.15);
            strap(0.3, 0.4, 0.38, -0.15);
            k.body.add(k.mk(k.box, '#F4F4F6', { s: [0.28, 0.36, 0.04], p: [0.38, -0.38, 1.0] }));
            k.body.add(k.mk(k.box, '#B49A7C', { s: [0.22, 0.06, 0.02], p: [0.38, -0.5, 1.03], outline: false }));
            const badgeLogo = plane(0.16, 0.07, logoTex);
            badgeLogo.position.set(0.38, -0.33, 1.03);
            k.body.add(badgeLogo);

            // --- quần túi hộp đen + giày trắng mũi cam ---
            k.body.add(k.mk(new THREE.CylinderGeometry(0.94, 0.52, 0.55, 24), BLACK, { p: [0, -0.68, 0], s: [1, 1, 0.93] }));
            k.body.add(k.mk(k.box, BLACK, { s: [0.12, 0.3, 0.28], p: [0.88, -0.68, 0.25] }));
            [-1, 1].forEach((sx) => k.body.add(k.mk(k.sphere, ORANGE, { s: [0.2, 0.08, 0.2], p: [sx * 0.38, -1.08, 0.66], outline: false })));

            // --- balo đen sau lưng (lộ ra hai bên) ---
            k.body.add(k.mk(k.sphere, BLACK, { s: [1.05, 1.05, 0.6], p: [-0.2, -0.05, -0.8] }));
            k.body.add(k.mk(k.sphere, '#2B2D38', { s: [0.4, 0.5, 0.3], p: [-1.15, -0.4, -0.45] }));
            const bagLogo = plane(0.3, 0.13, logoTex);
            bagLogo.position.set(-1.2, -0.38, -0.18);
            bagLogo.rotation.y = -0.9;
            k.body.add(bagLogo);
            [-1, 1].forEach((sx) => k.body.add(k.mk(k.box, BLACK, {
                s: [0.14, 0.7, 0.07], p: [sx * 0.6, 0.05, 0.74], r: [0, 0, sx * 0.1], outline: false,
            })));

            // --- chữ "FPT UNIVERSITY" trên bìa sách xanh có sẵn ---
            const bookText = plane(0.7, 0.3, bookTex);
            bookText.position.set(-0.26, -0.62, 1.185);
            bookText.rotation.z = 0.1;
            k.body.add(bookText);

            // --- cờ FPT University cắm sau lưng bên trái ---
            const flagPole = new THREE.Group();
            flagPole.position.set(-1.15, 0.2, -0.6);
            flagPole.rotation.z = 0.06;
            k.body.add(flagPole);
            flagPole.add(k.mk(new THREE.CylinderGeometry(0.03, 0.03, 2.4, 8), '#555A6B', { outline: false }));
            flagPole.add(k.mk(k.sphere, '#555A6B', { s: [0.06, 0.06, 0.06], p: [0, 1.2, 0], outline: false }));
            const flag = plane(0.85, 0.5, flagTex);
            flag.position.set(-0.43, 0.88, 0);
            flagPole.add(flag);
            k.sway(flagPole, 'y', 0.25, 2.6);
        },
    };

    const disposeTree = (o) => o.traverse((n) => {
        if (n.geometry && !n.geometry.userData.keep) n.geometry.dispose();
        const m = n.material;
        if (m) (Array.isArray(m) ? m : [m]).forEach((mm) => { if (!mm.userData.keep) { mm.map?.dispose?.(); mm.dispose(); } });
    });
    const clear = (g) => {
        while (g.children.length) {
            const c = g.children[0];
            g.remove(c);
            disposeTree(c);
        }
    };

    const rig = {
        scene, pivot, owl, head, eyes, beak, wingR, glint, shadow, dots, burst, burstState, sways, costumeId: null,
        setCostume(id, withBurst = false) {
            [cHead, cBody, cWing].forEach(clear);
            sways.length = 0;
            defaults.forEach((hex, m) => m.color.set(hex));
            ears.forEach((e) => { e.visible = true; });
            builders[id]?.(x);
            rig.costumeId = id;
            if (withBurst) burstState.pending = true;
        },
    };
    return rig;
}

/* ------------------------------------------------------------------ */
/* Animation: idle (nhún, chớp mắt, quay nhẹ, đầu nhìn theo chuột),     */
/* wave (vẫy cánh), jump (nhảy + squash/stretch), talk (nhép mỏ),       */
/* think (ngẩng đầu + 3 chấm suy nghĩ)                                  */
/* ------------------------------------------------------------------ */
function makeAnimator(rig, getAction) {
    let lookX = 0, lookY = 0, wing = 0.12, think = 0, nextBlink = 2, blinkStart = -1;

    return function update(t, dt, mx, my) {
        const a = getAction();
        const at = t - a.t0;
        const ease = (rate) => 1 - Math.exp(-dt * rate);

        lookX += (mx - lookX) * ease(6);
        lookY += (my - lookY) * ease(6);
        think += ((a.name === 'think' ? 1 : 0) - think) * ease(7);

        // nhảy
        let jy = 0, sy = 1;
        if (a.name === 'jump' && at < 0.6) {
            const k = at / 0.6;
            jy = Math.sin(k * Math.PI) * 0.5;
            sy = 1 + 0.07 * Math.sin(k * Math.PI)
                - 0.1 * Math.exp(-(k * k) / 0.0064)
                - 0.1 * Math.exp(-((k - 1) * (k - 1)) / 0.0064);
        }
        const sx = 1 / Math.sqrt(sy);
        rig.pivot.position.y = FEET_Y + jy;
        rig.pivot.scale.set(sx, sy, sx);
        rig.owl.position.y = -FEET_Y + Math.sin(t * 2.1) * 0.035;
        rig.owl.rotation.y = lookX * 0.28 * (1 - think) + Math.sin(t * 0.8) * 0.14;

        // đầu
        const talking = a.name === 'talk';
        const nod = talking ? Math.sin(t * 14) * 0.03 : 0;
        rig.head.rotation.set(
            lookY * 0.2 * (1 - think) + nod - think * (0.16 - Math.sin(t * 1.6) * 0.04),
            lookX * 0.3 * (1 - think) + Math.sin(t * 1.3) * 0.04 + think * 0.3,
            Math.sin(t * 1.7) * 0.03 - lookX * 0.04 * (1 - think) - think * 0.1,
        );

        // cánh phải: vẫy khi chào
        const waving = a.name === 'wave';
        const wingTarget = waving ? 1.85 + Math.sin(at * 16) * 0.35 : 0.12 + Math.sin(t * 2) * 0.03;
        wing += (wingTarget - wing) * ease(waving ? 14 : 8);
        rig.wingR.rotation.z = wing;

        // chớp mắt
        if (t > nextBlink) {
            blinkStart = t;
            nextBlink = t + 2.5 + Math.random() * 3;
        }
        const p = blinkStart >= 0 ? (t - blinkStart) / 0.16 : 1;
        const blink = p < 1 ? 1 - 0.92 * Math.sin(Math.PI * p) : 1;
        rig.eyes.forEach((e) => { e.scale.y = blink; });

        // mỏ nhép khi nói, ánh kính lấp lánh
        rig.beak.scale.y = 1 - (talking ? 0.55 * (0.5 + 0.5 * Math.sin(t * 26)) : 0);
        rig.glint.scale.setScalar(0.06 * (1 + 0.3 * Math.sin(t * 3 + think * 6)));

        // 3 chấm suy nghĩ
        rig.dots.forEach((d, i) => {
            d.visible = think > 0.05;
            d.scale.setScalar(d.userData.r * think * (0.6 + 0.4 * Math.max(0, Math.sin(t * 5 - i * 0.9))));
        });

        // trang phục: mũ/áo choàng/khăn đung đưa
        rig.sways.forEach((s) => { s.obj.rotation[s.axis] = s.base + Math.sin(t * s.speed + s.phase) * s.amp; });

        // đốm sáng khi đổi đồ
        if (rig.burstState.pending) {
            rig.burstState.pending = false;
            rig.burstState.t0 = t;
        }
        const bk = (t - rig.burstState.t0) / 0.8;
        rig.burst.forEach((m) => {
            if (bk >= 0 && bk < 1) {
                const e = 1 - (1 - bk) ** 3;
                m.visible = true;
                m.position.set(m.userData.dx * e * 1.8, 0.8 + m.userData.dy * e * 1.7, 1.0 + m.userData.dz);
                m.scale.setScalar(0.15 * (1 - bk));
            } else {
                m.visible = false;
            }
        });

        // bóng co lại khi nhảy
        const sh = 1.2 * (1 - jy * 0.5);
        rig.shadow.scale.set(sh, sh, sh);
        rig.shadow.material.opacity = 0.2 * (1 - jy);
    };
}

/* ------------------------------------------------------------------ */
/* Component                                                            */
/* ------------------------------------------------------------------ */
function Owl3D({ action, costume }) {
    const hostRef = useRef(null);
    const actionRef = useRef({ name: action, t0: performance.now() / 1000 });
    const costumeRef = useRef(costume);
    const rigRef = useRef(null);
    const drawRef = useRef(null);
    const [failed, setFailed] = useState(false);

    costumeRef.current = costume;

    useEffect(() => {
        actionRef.current = { name: action, t0: performance.now() / 1000 };
    }, [action]);

    useEffect(() => {
        const host = hostRef.current;
        let renderer;
        try {
            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
        } catch {
            setFailed(true);   // máy không có WebGL -> dùng lại bản SVG
            return undefined;
        }
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
        renderer.setSize(W, H);
        renderer.setClearColor(0x000000, 0);
        host.appendChild(renderer.domElement);
        const onLost = (e) => { e.preventDefault(); setFailed(true); };
        renderer.domElement.addEventListener('webglcontextlost', onLost);

        const camera = new THREE.PerspectiveCamera(30, W / H, 0.1, 50);
        camera.position.set(0, 2.8, 10);
        camera.lookAt(0, 0.95, 0);

        const rig = buildOwl();
        rig.setCostume(costumeRef.current, false);
        rigRef.current = rig;
        drawRef.current = () => renderer.render(rig.scene, camera);
        const update = makeAnimator(rig, () => actionRef.current);

        // đầu nhìn theo con trỏ chuột
        const mouse = { x: 0, y: 0 };
        const clamp = (v) => Math.max(-1, Math.min(1, v));
        const onMove = (e) => {
            const r = host.getBoundingClientRect();
            mouse.x = clamp((e.clientX - (r.left + r.width / 2)) / 300);
            mouse.y = clamp((e.clientY - (r.top + r.height / 2)) / 300);
        };
        window.addEventListener('pointermove', onMove, { passive: true });

        let raf = 0;
        const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
        if (reduce) {
            update(performance.now() / 1000, 0, 0, 0);   // chỉ vẽ 1 khung tĩnh
            drawRef.current();
        } else {
            let last = performance.now() / 1000;
            const loop = () => {
                raf = requestAnimationFrame(loop);
                const t = performance.now() / 1000;
                if (document.hidden) { last = t; return; }   // tab ẩn thì không vẽ
                update(t, Math.min(t - last, 0.05), mouse.x, mouse.y);
                last = t;
                renderer.render(rig.scene, camera);
            };
            loop();
        }

        return () => {
            cancelAnimationFrame(raf);
            window.removeEventListener('pointermove', onMove);
            renderer.domElement.removeEventListener('webglcontextlost', onLost);
            rigRef.current = null;
            drawRef.current = null;
            rig.scene.traverse((o) => {
                o.geometry?.dispose?.();
                const m = o.material;
                if (m) (Array.isArray(m) ? m : [m]).forEach((xx) => { xx.map?.dispose?.(); xx.gradientMap?.dispose?.(); xx.dispose(); });
            });
            renderer.dispose();
            renderer.domElement.remove();
        };
    }, []);

    // đổi trang phục khi prop thay đổi (có đốm sáng bung ra)
    useEffect(() => {
        const rig = rigRef.current;
        if (rig && rig.costumeId !== costume) {
            rig.setCostume(costume, true);
            drawRef.current?.();
        }
    }, [costume]);

    if (failed) return <ChibiAvatar action={action} />;
    return (
        <div ref={hostRef} aria-hidden="true"
             style={{ width: W, height: H, cursor: 'inherit', lineHeight: 0 }} />
    );
}

// Avatar nhỏ trong khung chat: chụp một khung hình 3D của con cú đang mặc đúng trang phục rồi dùng làm ảnh,
// nên avatar trong chat luôn giống con cú bên ngoài mà không phải mở thêm WebGL context cho từng tin nhắn.
const snapCache = new Map();
function snapshotOwl(costume) {
    if (snapCache.has(costume)) return snapCache.get(costume);
    const p = new Promise((resolve) => {
        let renderer;
        try {
            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
        } catch {
            resolve(null);
            return;
        }
        let rig;
        try {
            renderer.setPixelRatio(1);
            renderer.setSize(192, 192);
            renderer.setClearColor(0x000000, 0);
            const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 50);
            camera.position.set(0, 2.5, 8.2);
            camera.lookAt(0, 1.25, 0);
            rig = buildOwl();
            rig.setCostume(costume, false);
            makeAnimator(rig, () => ({ name: 'idle', t0: 0 }))(1, 0.016, 0, 0);
            renderer.render(rig.scene, camera);
            resolve(renderer.domElement.toDataURL('image/png'));
        } catch {
            resolve(null);
        } finally {
            rig?.scene.traverse((o) => {
                o.geometry?.dispose?.();
                const m = o.material;
                if (m) (Array.isArray(m) ? m : [m]).forEach((xx) => { xx.map?.dispose?.(); xx.gradientMap?.dispose?.(); xx.dispose(); });
            });
            renderer.dispose();
        }
    });
    snapCache.set(costume, p);
    return p;
}

function MiniOwl({ action, costume }) {
    const [src, setSrc] = useState(null);
    useEffect(() => {
        let alive = true;
        setSrc(null);
        snapshotOwl(costume).then((u) => { if (alive) setSrc(u); });
        return () => { alive = false; };
    }, [costume]);
    if (!src) return <ChibiAvatar mini action={action} />;
    return <img src={src} alt="" aria-hidden="true" className="chibi-mini" style={{ objectFit: 'contain' }} />;
}

export default function ChibiAvatar3D({ action = 'idle', costume = 'none', mini = false }) {
    // avatar nhỏ 32px trong khung chat: ảnh chụp con cú 3D đúng trang phục; chưa mặc gì thì giữ SVG 2D
    if (mini) return costume === 'none' ? <ChibiAvatar mini action={action} /> : <MiniOwl action={action} costume={costume} />;
    return <Owl3D action={action} costume={costume} />;
}
