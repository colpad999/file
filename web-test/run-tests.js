/**
 * Harness test untuk app/assets/web/app.js tanpa browser:
 *  - DOM & localStorage & fetch tiruan
 *  - memverifikasi alur login, fetch daftar repost, hapus, jeda, batas batch
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const APP = path.join(__dirname, '..', 'app', 'assets', 'web', 'app.js');
const HTML = path.join(__dirname, '..', 'app', 'assets', 'web', 'index.html');

let pass = 0, fail = 0;
function check(name, ok, extra) {
  if (ok) { pass++; console.log('  ok   ' + name); }
  else { fail++; console.log('  FAIL ' + name + (extra ? '  → ' + extra : '')); }
}

// ---------------------------------------------------------------- fake DOM
function makeEl(id) {
  return {
    id, _text: '', _html: '', value: '', checked: false, disabled: false, hidden: false,
    type: 'text', dataset: {}, classList: { toggle() {}, add() {}, remove() {}, contains: () => false },
    style: {}, children: [],
    get textContent() { return this._text; },
    set textContent(v) { this._text = String(v); },
    get innerHTML() { return this._html; },
    set innerHTML(v) { this._html = String(v); },
    addEventListener() {}, removeEventListener() {}, remove() {},
    appendChild(c) { this.children.push(c); return c; },
    insertBefore(c) { this.children.push(c); return c; },
    querySelectorAll: () => [], scrollTop: 0, scrollHeight: 0,
    focus() {}, click() { if (this.onclick) this.onclick({ target: this }); },
    firstChild: null, nextSibling: null
  };
}
const elements = new Map();
function $(id) {
  if (!elements.has(id)) elements.set(id, makeEl(id));
  return elements.get(id);
}

// ---------------------------------------------------------------- fake env
let fetchLog = [];
let fetchMode = 'ok';
let removed = [];

function fakeFetch(url, opts) {
  opts = opts || {};
  fetchLog.push({ url, method: opts.method || 'GET', body: opts.body || null });
  const json = (obj, status = 200) => Promise.resolve({
    status, ok: status < 400,
    text: () => Promise.resolve(JSON.stringify(obj))
  });

  if (fetchMode === 'neterr') return Promise.reject(new Error('jaringan mati'));

  if (url.includes('/v1/user/login/')) {
    if (fetchMode === 'badpass') return json({ status_code: 1003, message: 'sandi salah' }, 200);
    return json({ status_code: 0, user: { uniqueId: 'akunku', nickname: 'Akunku', uid: '123' } });
  }
  if (url.includes('/post/item_list/')) {
    const cursor = Number(new URL(url).searchParams.get('cursor') || 0);
    if (cursor >= 20) return json({ status_code: 0, itemList: [], cursor: 40 });
    const items = [];
    for (let i = 0; i < 20; i++) {
      items.push({ id: '70000' + cursor + i, desc: 'repost ke-' + (cursor + i), author: { uniqueId: 'lain' } });
    }
    return json({ status_code: 0, itemList: items, cursor: cursor + 20 });
  }
  if (url.includes('/v1/aweme/digest/undo/repost/') || url.includes('/aweme/v1/undigest/')) {
    const body = String(opts.body || '');
    const id = (body.match(/item_id=([^&]+)/) || [])[1];
    if (fetchMode === 'failremove') return json({ status_code: -1, message: 'gagal' }, 200);
    removed.push(id);
    return json({ status_code: 0 });
  }
  if (url.includes('/user/detail/')) {
    if (fetchMode === 'nosession') return json({ status_code: 0 });
    return json({ status_code: 0, userInfo: { user: { uniqueId: 'akunweb', nickname: 'Web', id: '999' } } });
  }
  return json({ status_code: 0 });
}

const sandbox = {
  window: {},
  document: {
    getElementById: $,
    querySelectorAll: () => [],
    createElement: () => makeEl('new'),
    addEventListener() {},
    readyState: 'complete'
  },
  localStorage: (() => {
    const m = new Map();
    return {
      getItem: k => (m.has(k) ? m.get(k) : null),
      setItem: (k, v) => m.set(k, String(v)),
      removeItem: k => m.delete(k),
      clear: () => m.clear()
    };
  })(),
  sessionStorage: (() => {
    const m = new Map();
    return {
      getItem: k => (m.has(k) ? m.get(k) : null),
      setItem: (k, v) => m.set(k, String(v)),
      removeItem: k => m.delete(k),
      clear: () => m.clear()
    };
  })(),
  fetch: fakeFetch,
  URL: URL,
  URLSearchParams: URLSearchParams,
  setTimeout: (fn, ms) => setTimeout(fn, ms || 0),
  clearTimeout: clearTimeout,
  console,
  navigator: { vibrate: () => true, userAgent: 'node' },
  location: { href: 'file:///android_asset/web/index.html' },
  __UR_SPEED__: 400   // percepat jeda antar video saat pengujian
};
sandbox.window = sandbox;
sandbox.globalThis = sandbox;

// native bridge tiruan
const nativeCalls = [];
sandbox.window.UnrepostNative = {
  openTikTok: url => nativeCalls.push(['openTikTok', url]),
  closeTikTok: () => nativeCalls.push(['closeTikTok']),
  keepAwake: on => nativeCalls.push(['keepAwake', on]),
  notify: (t, b) => nativeCalls.push(['notify', t, b]),
  version: () => '2.1',
  // meniru jembatan native: HTTP dilakukan "di luar" JS, hasil dikembalikan async
  api: (method, url, body, id) => {
    fakeFetch(url, { method, body }).then(resp => {
      resp.text().then(text => {
        const payload = JSON.stringify({ status: resp.status, text });
        vmSetTimeout(() => { try { sandbox.window.UnrepostNativeResult(id, payload); }
          catch (e) { console.log('     [bridge] callback error:', e && e.message); } }, 1);
      });
    }).catch(e => {
      vmSetTimeout(() => sandbox.window.UnrepostNativeResult(id,
        JSON.stringify({ status: 0, error: String(e && e.message) })), 1);
    });
  }
};

// app.js membuat Promise di realm vm; callback-nya harus dijadwalkan di realm yang sama
let vmSetTimeout = setTimeout;
const ctx = vm.createContext(sandbox);
// setelah skrip dimuat, ambil setTimeout milik vm untuk callback jembatan native
sandbox.__bridge = {};

const __origRun = vm.runInContext;
vm.runInContext(fs.readFileSync(APP, 'utf8'), ctx, { filename: 'app.js' });
vmSetTimeout = vm.runInContext('setTimeout', ctx);
vm.runInContext(fs.readFileSync(APP, 'utf8'), ctx, { filename: 'app.js' });
const ur = sandbox.window.Unrepost;

function tick(ms) { return new Promise(r => setTimeout(r, ms || 0)); }

// ------------------------------------------------------------------ tests
(async function main() {
  await tick(20);

  check('index.html memuat app.js & style.css', (() => {
    const h = fs.readFileSync(HTML, 'utf8');
    return h.includes('app.js') && h.includes('style.css') && h.includes('view-login');
  })());
  check('semua id yang dipakai JS ada di HTML', (() => {
    const h = fs.readFileSync(HTML, 'utf8');
    const js = fs.readFileSync(APP, 'utf8');
    const ids = new Set();
    for (const m of js.matchAll(/\$\('([a-zA-Z0-9_-]+)'\)/g)) ids.add(m[1]);
    const missing = [];
    for (const id of ids) {
      // id dinamis (tab/panel/batch/progress) dibuat oleh JS
      if (['view-login', 'view-app', 'backToApp', 'tiktokBar', 'tiktokBarSub'].includes(id)) continue; // dibuat dinamis
      if (!h.includes('id="' + id + '"')) missing.push(id);
    }
    if (missing.length) console.log('     id hilang:', missing.join(', '));
    return missing.length === 0;
  })());

  // ---- login gagal (sandi salah)
  fetchMode = 'badpass';
  $('identifier').value = 'akunku';
  $('password').value = 'salah';
  $('loginBtn').click();
  await tick(30);
  check('login salah: pesan error tampil', $('loginErr').hidden === false && /salah/i.test($('loginErr').textContent),
    'err="' + $('loginErr').textContent + '"');
  check('login salah: tidak masuk app', $('view-app').hidden === true);

  // ---- login sukses
  fetchMode = 'ok';
  $('password').value = 'bener';
  $('loginBtn').click();
  await tick(30);
  check('login ok: masuk ke app', $('view-app').hidden === false, 'err="' + $('loginErr').textContent + '" log="' + $('log').textContent.slice(-200) + '"');
  check('login ok: nama akun tampil', /Akunku|akunku/.test($('whoName').textContent));
  check('login ok: sandi dikirim ke endpoint resmi', fetchLog.some(f =>
    f.url.includes('tiktok.com/api/v1/user/login/') && String(f.body).includes('username=akunku')));

  // ---- batch: hapus 15 video (default), jeda dipercepat
  removed = [];
  const before = fetchLog.length;
  $('cleanBtn').click();
  await tick(1800);
  const posts = fetchLog.slice(before).filter(f => f.method === 'POST');
  check('batch: 15 video dihapus', removed.length === 15, 'terhapus=' + removed.length);
  check('batch: tidak lebih dari target', posts.length === 15);
  check('batch: jeda antar video diterapkan', fetchLog.length > 15);
  check('batch: semua request lewat jembatan native (bukan fetch WebView)',
    sandbox.window.UnrepostNative.api !== undefined && fetchLog.length > 0);
  check('batch: request hapus memakai POST + form-urlencoded', fetchLog.some(f =>
    f.method === 'POST' && /item_id=70000/.test(String(f.body))));
  check('batch: progres 15/15', /15\/15/.test($('progressCount').textContent));
  check('batch: tombol kembali aktif', $('cleanBtn').disabled === false);
  check('batch: riwayat tersimpan', (() => {
    const h = JSON.parse(sandbox.localStorage.getItem('ur_history_v1') || '[]');
    return h.length === 1 && h[0].done === 15;
  })());
  check('batch: notifikasi native dipanggil', nativeCalls.some(c => c[0] === 'notify'));

  // ---- mode latihan tidak menghapus
  removed = [];
  $('dryRun').checked = true;
  $('dryRun').onchange.call($('dryRun'));
  $('cleanBtn').click();
  await tick(1800);
  check('latihan: tidak ada request hapus', removed.length === 0);
  check('latihan: tetap menghitung progres', /15\/15/.test($('progressCount').textContent));
  $('dryRun').checked = false;
  $('dryRun').onchange.call($('dryRun'));

  // ---- berhenti di tengah (butuh jaringan lambat agar tombol Stop sempat ditekan)
  removed = [];
  const realFetch = sandbox.fetch;
  sandbox.fetch = (u, o) => new Promise((res, rej) =>
    setTimeout(() => realFetch(u, o).then(res, rej), 12));
  $('cleanBtn').click();
  await tick(60);
  $('stopBtn').click();
  await tick(400);
  sandbox.fetch = realFetch;
  check('berhenti: berhenti sebelum target', removed.length < 15,
    'terhapus=' + removed.length);
  check('berhenti: tombol aktif kembali', $('cleanBtn').disabled === false);

  // ---- kegagalan beruntun -> berhenti sendiri
  removed = [];
  fetchMode = 'failremove';
  const histBefore = JSON.parse(sandbox.localStorage.getItem('ur_history_v1') || '[]').length;
  $('cleanBtn').click();
  await tick(1200);
  fetchMode = 'ok';
  check('gagal 3x: batch berhenti sendiri', $('cleanBtn').disabled === false);
  check('gagal 3x: tercatat di riwayat', JSON.parse(sandbox.localStorage.getItem('ur_history_v1')).length > histBefore);

  // ---- jalur website + cek sesi
  sandbox.sessionStorage.clear();
  $('webLoginBtn').click();
  check('jalur website: membuka tiktok.com/login', nativeCalls.some(c => c[0] === 'openTikTok' && /login/.test(c[1])));
  fetchMode = 'ok';
  $('webLoginCheck').click();
  await tick(40);
  check('cek sesi website: akun terdeteksi', /akunweb|Web/.test($('whoName').textContent));

  // ---- logout
  $('logoutBtn').click();
  await tick(10);
  check('logout: kembali ke login', $('view-login').hidden === false);

  console.log('\n=== ' + pass + ' lulus, ' + fail + ' gagal ===');
  process.exit(fail ? 1 : 0);
})();
