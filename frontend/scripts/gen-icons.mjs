// 生成 PNG 图标：深色圆角底 + 品牌青折线。
// 纯 Node（zlib）实现，不依赖任何图形库。
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const OUT = process.argv[2];
if (!OUT) { console.error('用法: node gen-icons.mjs <输出目录>'); process.exit(2); }

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function encodePng(w, h, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;   // bit depth
  ihdr[9] = 6;   // RGBA
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- 几何（32 单位设计稿）----------
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
function sdRoundRect(px, py, hw, hh, r) {
  const qx = Math.abs(px) - hw + r;
  const qy = Math.abs(py) - hh + r;
  const ax = Math.max(qx, 0), ay = Math.max(qy, 0);
  return Math.hypot(ax, ay) + Math.min(Math.max(qx, qy), 0) - r;
}
function sdSegment(px, py, ax, ay, bx, by) {
  const pax = px - ax, pay = py - ay, bax = bx - ax, bay = by - ay;
  const h = clamp((pax * bax + pay * bay) / (bax * bax + bay * bay), 0, 1);
  return Math.hypot(pax - bax * h, pay - bay * h);
}

const INK = [11, 13, 18];      // #0B0D12
const ACCENT = [224, 139, 139]; // #E08B8B
const POLY = [[8, 21], [13, 16], [18, 19], [24, 10]];

function render(N) {
  const buf = Buffer.alloc(N * N * 4);
  const s = N / 32;
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const px = (x + 0.5) / s - 16; // 以中心为原点
      const py = (y + 0.5) / s - 16;
      let r = 0, g = 0, b = 0, a = 0;

      // 底：圆角方形
      const dRect = sdRoundRect(px, py, 15, 15, 6);
      const covRect = clamp(0.5 - dRect, 0, 1);
      if (covRect > 0) {
        r = INK[0]; g = INK[1]; b = INK[2]; a = covRect;
        // 边框：距边界 0.7 单位一圈
        const dRing = Math.abs(dRect + 0.7) - 0.7;
        const covRing = clamp(0.5 - dRing, 0, 1) * 0.16;
        if (covRing > 0) {
          r = r * (1 - covRing) + 255 * covRing;
          g = g * (1 - covRing) + 255 * covRing;
          b = b * (1 - covRing) + 255 * covRing;
        }
      }

      // 折线
      let dLine = Infinity;
      for (let i = 0; i < POLY.length - 1; i++) {
        const d = sdSegment(px, py, POLY[i][0] - 16, POLY[i][1] - 16, POLY[i + 1][0] - 16, POLY[i + 1][1] - 16);
        if (d < dLine) dLine = d;
      }
      const covLine = clamp(0.5 - (dLine - 1.3), 0, 1);
      if (covLine > 0) {
        const k = covLine;
        r = r * (1 - k) + ACCENT[0] * k;
        g = g * (1 - k) + ACCENT[1] * k;
        b = b * (1 - k) + ACCENT[2] * k;
        a = Math.max(a, k);
      }

      // 端点圆点
      const dDot = Math.hypot(px - (24 - 16), py - (10 - 16));
      const covDot = clamp(0.5 - (dDot - 2), 0, 1);
      if (covDot > 0) {
        const k = covDot;
        r = r * (1 - k) + ACCENT[0] * k;
        g = g * (1 - k) + ACCENT[1] * k;
        b = b * (1 - k) + ACCENT[2] * k;
        a = Math.max(a, k);
      }

      const i = (y * N + x) * 4;
      buf[i] = Math.round(r);
      buf[i + 1] = Math.round(g);
      buf[i + 2] = Math.round(b);
      buf[i + 3] = Math.round(a * 255);
    }
  }
  return encodePng(N, N, buf);
}

mkdirSync(OUT, { recursive: true });
for (const [name, size] of [['favicon-32.png', 32], ['favicon-64.png', 64], ['apple-touch-icon.png', 180]]) {
  const file = join(OUT, name);
  writeFileSync(file, render(size));
  console.log(`wrote ${name} (${size}x${size})`);
}
