// ─── SceneView QR helper ─────────────────────────────────────────────────
//
// Tiny ESM wrapper around the vendored qrcode-generator library
// (`qrcode-vendor.js`, ~55 KB, MIT, © 2009 Kazuhiko Arase). The vendor
// script ships as UMD which doesn't expose anything when loaded as an
// ES module — so we inject it once as a regular `<script>` tag, wait
// for it, then read `window.qrcode`.
//
// Loaded on demand by:
//   - website-static/open/index.html — desktop QR for "scan with phone"
//   - website-static/rerun/index.html — future "open in app" prompts
//
// Public API:
//   import { renderQrSvg } from './qrcode-min.js';
//   const svgString = await renderQrSvg('https://…', { size: 200 });
//   container.innerHTML = svgString;

let _ready = null;

function ensureVendor() {
  if (_ready) return _ready;
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('qrcode-min: window is required'));
  }
  if (typeof window.qrcode === 'function') {
    _ready = Promise.resolve(window.qrcode);
    return _ready;
  }
  _ready = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    // Resolve relative to *this* module so callers don't have to know
    // where the vendor file lives.
    s.src = new URL('./qrcode-vendor.js', import.meta.url).href;
    s.onload = () => {
      if (typeof window.qrcode === 'function') resolve(window.qrcode);
      else reject(new Error('qrcode-min: vendor script loaded but window.qrcode is missing'));
    };
    s.onerror = () => reject(new Error('qrcode-min: failed to load qrcode-vendor.js'));
    document.head.appendChild(s);
  });
  return _ready;
}

/**
 * Render a QR code as an SVG string.
 *
 * @param {string} text - what the QR code encodes (URL, command, etc.)
 * @param {Object} [opts]
 * @param {number} [opts.size=200] - rendered size in CSS pixels.
 * @param {number} [opts.padding=0] - extra quiet-zone modules outside the QR.
 * @param {string} [opts.errorCorrection='L'] - 'L' | 'M' | 'Q' | 'H'.
 * @returns {Promise<string>} an `<svg>…</svg>` string ready for `innerHTML`.
 */
export async function renderQrSvg(text, opts = {}) {
  if (!text || typeof text !== 'string') {
    throw new Error('qrcode-min: text must be a non-empty string');
  }
  const size = Number.isFinite(opts.size) && opts.size > 0 ? opts.size : 200;
  const padding = Number.isFinite(opts.padding) && opts.padding >= 0 ? opts.padding : 0;
  const ec = opts.errorCorrection || 'L';
  if (!/^[LMQH]$/.test(ec)) {
    throw new Error(`qrcode-min: errorCorrection must be one of L M Q H, got "${ec}"`);
  }

  const qrcode = await ensureVendor();
  // typeNumber=0 = auto-detect the smallest QR version that fits the data.
  const qr = qrcode(0, ec);
  qr.addData(text);
  qr.make();

  // The vendor lib's `createSvgTag({...})` produces a self-contained
  // `<svg>…</svg>` string. We pass `cellSize` such that the rendered SVG
  // fills `size` pixels when the modules are stretched via `width/height`
  // attributes.
  const moduleCount = qr.getModuleCount();
  // Target one CSS pixel ≥ one module to keep the QR crisp on retina.
  const cellSize = Math.max(1, Math.floor(size / (moduleCount + padding * 2)));
  const margin = cellSize * padding;

  return qr.createSvgTag({
    cellSize,
    margin,
    scalable: true,
  });
}

/**
 * Convenience helper: render and inject into a DOM node.
 *
 * @param {HTMLElement} container - element whose `innerHTML` becomes the QR.
 * @param {string} text - what the QR code encodes.
 * @param {Object} [opts] - same shape as [renderQrSvg].
 * @returns {Promise<void>}
 */
export async function injectQrInto(container, text, opts) {
  if (!container) throw new Error('qrcode-min: container is required');
  container.innerHTML = await renderQrSvg(text, opts);
}
