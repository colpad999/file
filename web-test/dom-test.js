/**
 * Uji domsnap.js dengan jsdom: mensimulasikan halaman TikTok (grid, video, menu),
 * lalu memastikan snapshot + klik yang dihasilkan memicu handler halaman.
 */
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const SNAP = fs.readFileSync(path.join(__dirname, '..', 'app', 'assets', 'web', 'domsnap.js'), 'utf8');

let pass = 0, fail = 0;
const check = (name, ok, extra) => {
  if (ok) { pass++; console.log('  ok   ' + name); }
  else { fail++; console.log('  FAIL ' + name + (extra ? '  → ' + extra : '')); }
};

function makePage(html) {
  const dom = new JSDOM(html, { runScripts: 'dangerously', pretendToBeVisual: true, url: 'https://www.tiktok.com/@aku?lang=id' });
  const w = dom.window;
  // jsdom tidak punya layout: beri ukuran agar elemen dianggap terlihat
  w.HTMLElement.prototype.getBoundingClientRect = function () {
    return { width: 120, height: 60, top: 10, left: 10, bottom: 70, right: 130, x: 10, y: 10 };
  };
  w.document.elementFromPoint = () => w.document.querySelector('button');
  const snaps = [];
  w.UnrepostDom = { snap: (p) => snaps.push(JSON.parse(p)) };
  w.eval(SNAP);
  return { dom, w, snaps };
}

// ---------------------------------------------------------------- 1) snapshot
{
  const { w, snaps } = makePage(`<!doctype html><html><body>
    <div role="tablist"><span role="tab">Video</span><span role="tab" id="repostTab">Repost</span></div>
    <a href="/@aku/video/1001"><img alt="v1" src="a.jpg"></a>
    <a href="/@aku/video/1002"><img alt="v2" src="b.jpg"></a>
    <button data-e2e="share-icon" aria-label="Bagikan"><svg></svg></button>
  </body></html>`);
  w.UnrepostSnap.snapNow();
  const s = snaps[0];
  check('snapshot terkirim ke native', !!s && typeof s.html === 'string');
  check('snapshot memuat data-ur', /data-ur="\d+"/.test(s.html));
  check('snapshot memuat e2e tombol bagikan', s.html.includes('share-icon'));
  check('snapshot memuat tautan video', s.html.includes('/@aku/video/1001'));
  check('script/style tidak ikut', !/<script/i.test(s.html));
  check('info() mengenali tombol', JSON.parse(w.UnrepostSnap.info()).buttons >= 1);
}

// ---------------------------------------------------------------- 2) klik tab
{
  const { w } = makePage(`<!doctype html><html><body>
    <div id="tablist"><span role="tab" id="v">Video</span><span role="tab" id="r">Repost</span></div>
  </body></html>`);
  let clicked = 0;
  w.document.getElementById('r').addEventListener('click', () => clicked++);
  // cari elemen berdasarkan teks lalu klik, seperti yang dilakukan DomEngine
  w.UnrepostSnap.snapNow();   // menandai elemen dengan data-ur (seperti di WebView)
  const label = w.UnrepostSnap.find('^Repost$', false);
  check('find() mengembalikan elemen tab', label !== 'notfound', label);
  const el = w.document.querySelector('[role="tab"]:not(#v)');
  const ur = el.getAttribute('data-ur');
  const res = w.UnrepostSnap.click(ur);
  check('click(ur) memicu handler tepat satu kali', clicked === 1 && res === 'ok', 'clicked=' + clicked + ' res=' + res);
}

// ---------------------------------------------------------------- 3) menu & hapus repost
{
  const { w } = makePage(`<!doctype html><html><body>
    <video src="x.mp4"></video>
    <button data-e2e="share-icon" id="share"><svg></svg></button>
    <div id="sheet" hidden>
      <button id="save">Simpan video</button>
      <button id="remove">Hapus repost</button>
    </div>
    <script>
      window.events = [];
      document.getElementById('share').addEventListener('click', () => {
        window.events.push('share');
        document.getElementById('sheet').hidden = false;
      });
      document.getElementById('remove').addEventListener('click', () => window.events.push('remove'));
    </script>
  </body></html>`);
  w.UnrepostSnap.snapNow();
  const r1 = w.UnrepostSnap.menu();
  check('menu() menekan tombol bagikan', r1 === 'ok' && w.events.includes('share'), r1 + ' ' + w.events);
  w.UnrepostSnap.snapNow();
  const hit = w.UnrepostSnap.find('^\\s*hapus\\s+repost\\s*$', true);
  check('find(...hapus repost, tap=true) menekan aksi', hit === 'ok' && w.events.includes('remove'),
    hit + ' ' + w.events);
}

// ---------------------------------------------------------------- 4) klik koordinat & scroll
{
  const { w } = makePage(`<!doctype html><html><body>
    <div id="big">Batal</div><button id="ok" onclick="window.x=1">Hapus</button>
  </body></html>`);
  w.document.getElementById('ok').addEventListener('click', () => { w.ok = 1; });
  const res = w.UnrepostSnap.clickXY(60, 30);
  check('clickXY mengklik elemen di titik itu', res === 'ok');
  check('scroll tidak error', w.UnrepostSnap.scroll(500) === 'ok');
  check('back tidak error', w.UnrepostSnap.back() === 'ok');
}

// ---------------------------------------------------------------- 5) SPA: snapshot berkala
(async () => {
  const { w, snaps } = makePage(`<!doctype html><html><body>
    <div id="app">Halaman awal</div></body></html>`);
  w.UnrepostSnap.snapNow();
  const before = snaps.length;
  // simulasikan SPA: konten berubah tanpa reload (TikTok pindah tab)
  await new Promise(r => setTimeout(r, 60));
  w.document.getElementById('app').textContent = 'TAB REPOST: banyak video';
  await new Promise(r => setTimeout(r, 1800));   // tunggu interval 1200ms
  const after = snaps.length;
  check('SPA: snapshot terkirim ulang setelah konten berubah', after > before,
    before + ' -> ' + after);
  check('SPA: snapshot terbaru memuat konten baru',
    snaps[snaps.length - 1].html.includes('TAB REPOST'));
  check('summary() melaporkan ringkasan halaman', (() => {
    const s = JSON.parse(w.UnrepostSnap.summary());
    return typeof s.videos === 'number' && Array.isArray(s.tabs);
  })());

  console.log('\n=== ' + pass + ' lulus, ' + fail + ' gagal ===');
  process.exit(fail ? 1 : 0);
})();
