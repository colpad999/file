/* ==========================================================================
   Unrepost — logika situs di dalam APK.
   Tanpa framework, tanpa dependensi: hanya DOM + fetch ke domain TikTok.
   ========================================================================== */
(function () {
  'use strict';

  // Timer lokal (beberapa lingkungan WebView ketat tidak mengekspos setTimeout global).
  var ST = (typeof window !== 'undefined' && window.setTimeout) ? window.setTimeout.bind(window) : setTimeout;
  var CT = (typeof window !== 'undefined' && window.clearTimeout) ? window.clearTimeout.bind(window) : clearTimeout;

  // ---------------------------------------------------------------- konstanta
  var API = 'https://www.tiktok.com/api';
  var MOBILE_API = 'https://api16-normal-c-useast1a.tiktokv.com/aweme/v1';
  var LOGIN_URL = 'https://www.tiktok.com/login/phone-or-email/email';
  var WEB_LOGIN_URL = 'https://www.tiktok.com/login';

  var STORE = {
    session: 'ur_session_v1',
    settings: 'ur_settings_v1',
    history: 'ur_history_v1',
    lastRun: 'ur_lastrun_v1'
  };

  // Faktor percepatan waktu; hanya diisi oleh test otomatis (default normal).
  var SPEED = (typeof window !== 'undefined' && window.__UR_SPEED__) || 1;

  var DEFAULTS = { batch: 15, gap: 12, dryRun: false, notify: true, vibrate: true, keepAwake: true };

  // Label yang menandakan menu "hapus repost" pada respons list/repost.
  var REPOST_FLAGS = ['isRepost', 'is_repost', 'reposted', 'repostFrom'];

  // ---------------------------------------------------------------- state
  var session = null;        // { identifier, username, nickname, userId, exp, ts }
  var settings = Object.assign({}, DEFAULTS);
  var running = false;
  var paused = false;
  var stopped = false;
  var runStats = { done: 0, failed: 0, target: 0, startedAt: 0, dryRun: false, items: [] };
  var sleepTimer = null;
  var collectCursor = 0;
  var queue = [];

  // ---------------------------------------------------------------- helper
  function $(id) { return document.getElementById(id); }
  function store(kind) {
    try {
      var raw = localStorage.getItem(kind);
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }
  function save(kind, value) {
    try { localStorage.setItem(kind, JSON.stringify(value)); } catch (e) { /* mode privat */ }
  }
  function toast(msg, ms) {
    var el = $('toast');
    el.textContent = msg;
    el.hidden = false;
    CT(el._t);
    el._t = ST(function () { el.hidden = true; }, ms || 2200);
  }
  function log(line) {
    var el = $('log');
    var stamp = new Date().toLocaleTimeString('id-ID', { hour12: false });
    var next = '[' + stamp + '] ' + line;
    el.textContent = (el.textContent === 'siap.' ? '' : el.textContent + '\n') + next;
    el.scrollTop = el.scrollHeight;
  }
  function vibrate(ms) {
    if (settings.vibrate && navigator.vibrate) navigator.vibrate(ms || 30);
  }
  function sleep(ms) {
    return new Promise(function (resolve, reject) {
      sleepTimer = ST(function () { sleepTimer = null; resolve(); }, Math.max(0, ms / SPEED));
    });
  }
  function fmt(ts) {
    return new Date(ts).toLocaleString('id-ID', { dateStyle: 'medium', timeStyle: 'short' });
  }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c];
    });
  }

  // ---------------------------------------------------------------- navigasi
  var page = { name: 'login', history: [] };
  function go(name) {
    page.name = name;
    if (name !== 'tiktok') page.history = [];
    else if (page.history[page.history.length - 1] !== 'app') page.history.push('app');
    renderRoute();
  }
  /** Dipanggil native (WebViewClient) saat URL berubah. */
  function onNavigate(url) {
    if (!url) return;
    if (url.indexOf('tiktok.com') === -1) return;
    page.history.push(url);
    renderRoute();
  }
  function renderRoute() {
    var atTikTok = page.name === 'tiktok' && page.history.length > 0;
    $('view-login').hidden = page.name !== 'login';
    $('view-app').hidden = page.name !== 'app';
    $('brandSub').textContent = atTikTok ? 'menampilkan tiktok.com' : 'pembersih repost TikTok';
    if (atTikTok) renderTikTokBar();
  }

  // -------------------------------------------------------------- sesi/login
  function loadSession() {
    try { session = JSON.parse(sessionStorage.getItem(STORE.session) || 'null'); } catch (e) { session = null; }
  }
  function saveSession(s) {
    session = s;
    try { sessionStorage.setItem(STORE.session, JSON.stringify(s)); } catch (e) { /* abaikan */ }
  }
  function clearSession() {
    session = null;
    try { sessionStorage.removeItem(STORE.session); } catch (e) { /* abaikan */ }
  }

  /** Jalur cepat: login email/username + sandi ke endpoint resmi TikTok. */
  function loginQuick(identifier, password) {
    var body = new URLSearchParams({
      username: identifier,
      password: password,
      service: 'https://www.tiktok.com',
      aid: '1988',
      account_sdk_source: 'web',
      language: 'id'
    });
    return http('POST', API + '/v1/user/login/', body.toString());
  }

  function readJson(res) {
    return res.text().then(function (txt) {
      try { return { status: res.status, data: JSON.parse(txt) }; }
      catch (e) { return { status: res.status, data: null, raw: txt }; }
    });
  }

  function errorMessage(out) {
    if (!out) return 'Tidak ada respons dari TikTok.';
    if (out.status === 0) return 'Tidak ada koneksi internet.';
    var d = out.data || {};
    if (d.message) return d.message;
    if (d.data && d.data.description) return d.data.description;
    if (out.status === 403) return 'Diblokir TikTok (403). Coba jalur website + login manual.';
    if (out.status === 429) return 'Terlalu banyak percobaan. Tunggu beberapa menit.';
    if (out.status >= 500) return 'Server TikTok bermasalah (' + out.status + ').';
    return 'Login gagal (HTTP ' + out.status + '). Cek username/sandi.';
  }

  function onLoginClick() {
    var id = $('identifier').value.trim();
    var pw = $('password').value;
    var err = $('loginErr');
    err.hidden = true;
    if (!id || !pw) {
      err.textContent = 'Isi username dan sandi dulu.';
      err.hidden = false;
      return;
    }
    var btn = $('loginBtn');
    btn.disabled = true;
    btn.textContent = 'Menghubungkan…';
    loginQuick(id, pw).then(function (out) {
      var d = (out && out.data) || {};
      var ok = out && (d.status_code === 0 || d.statusCode === 0) && !d.error;
      if (!ok) {
        err.textContent = errorMessage(out);
        err.hidden = false;
        btn.disabled = false;
        btn.textContent = 'Masuk →';
        log('login gagal: ' + errorMessage(out));
        return;
      }
      var user = d.user || (d.data && d.data.user) || {};
      saveSession({
        identifier: id,
        username: user.uniqueId || id.replace(/^@/, ''),
        nickname: user.nickname || '',
        userId: user.uid || user.userId || '',
        ts: Date.now()
      });
      $('password').value = '';
      afterLogin('jalur cepat');
    }).catch(function (e) {
      err.textContent = 'Gagal menghubungi TikTok: ' + (e && e.message ? e.message : 'jaringan');
      err.hidden = false;
      btn.disabled = false;
      btn.textContent = 'Masuk →';
    });
  }

  function afterLogin(how) {
    $('loginBtn').disabled = false;
    $('loginBtn').textContent = 'Masuk →';
    log('sesi tersimpan (' + how + ')');
    toast('Berhasil login');
    refreshSessionCard();
    go('app');
    switchTab('clean');
  }

  /** Jalur website: buka tiktok.com/login lalu periksa sesi setelah pengguna kembali. */
  function checkWebSession() {
    return http('GET', API + '/user/detail/?uniqueId=' +
      encodeURIComponent(session ? session.username : '')).then(function (out) {
      var d = (out && out.data) || {};
      var user = d.userInfo && d.userInfo.user;
      if (!user) return null;
      return {
        identifier: user.uniqueId,
        username: user.uniqueId,
        nickname: user.nickname || '',
        userId: user.id || '',
        ts: Date.now()
      };
    });
  }

  function refreshSessionCard() {
    var name = $('whoName');
    var sub = $('whoSub');
    if (session) {
      name.textContent = session.nickname || ('@' + session.username);
      sub.textContent = '@' + session.username + ' · sejak ' + fmt(session.ts);
      $('avatar').textContent = (session.nickname || session.username || '?').charAt(0).toUpperCase();
      $('sessionInfo').textContent = 'Login sebagai @' + session.username +
        (session.nickname ? ' (' + session.nickname + ')' : '') + ' · ' + fmt(session.ts);
    } else {
      name.textContent = 'Belum terhubung';
      sub.textContent = 'login dulu untuk mulai';
      $('avatar').textContent = '@';
      $('sessionInfo').textContent = 'Belum login.';
    }
  }

  // ---------------------------------------------------------------- TikTok
  /** Buka halaman TikTok di dalam aplikasi (WebView native menampilkannya). */
  function openTikTok(url) {
    if (window.UnrepostNative && window.UnrepostNative.openTikTok) {
      window.UnrepostNative.openTikTok(url);
      page.name = 'tiktok';
      page.history = [url];
      renderRoute();
    } else {
      window.open(url, '_blank');
    }
  }

  function renderTikTokBar() {
    if (document.getElementById('tiktokBar')) return;
    var bar = document.createElement('div');
    bar.id = 'tiktokBar';
    bar.className = 'card session-card';
    bar.innerHTML = '<div class="who"><div class="who-name">TikTok terbuka</div>' +
      '<div class="who-sub" id="tiktokBarSub">login lalu kembali ke Unrepost</div></div>' +
      '<button class="ghost" id="backToApp" type="button">✕</button>';
    var view = $('view-app');
    view.insertBefore(bar, view.firstChild.nextSibling);
    $('backToApp').onclick = function () {
      if (window.UnrepostNative && window.UnrepostNative.closeTikTok) {
        window.UnrepostNative.closeTikTok();
      }
      bar.remove();
      page.name = 'app';
      page.history = [];
      renderRoute();
    };
  }

  // ---------------------------------------------------------- transpor HTTP
  // Di dalam APK: semua request ke TikTok lewat jembatan native (bebas CORS,
  // cookie dari WebView ikut terpakai, POST didukung).
  // Di browser biasa: pakai fetch apa adanya (butuh server proxy karena CORS).
  var native = (typeof window !== 'undefined' && window.UnrepostNative) || null;
  var pending = {};
  var pendingSeq = 0;

  if (typeof window !== 'undefined') {
    window.UnrepostNativeResult = function (id, payload) {
      var cb = pending[id];
      if (!cb) return;
      delete pending[id];
      var out = null;
      try { out = JSON.parse(payload); } catch (e) { out = { status: 0, error: 'parse' }; }
      if (out && typeof out.text === 'string') {
        try { out.data = JSON.parse(out.text); } catch (e) { out.data = null; out.raw = out.text; }
      }
      cb(out);
    };
  }

  function http(method, url, body) {
    if (native && native.api) {
      return new Promise(function (resolve) {
        var id = 'r' + (++pendingSeq);
        pending[id] = resolve;
        ST(function () {
          if (pending[id]) { delete pending[id]; resolve({ status: 0, error: 'timeout' }); }
        }, 30000 / SPEED);
        native.api(method, url, body || null, id);
      });
    }
    var opts = { method: method, credentials: 'include' };
    if (body) {
      opts.headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
      opts.body = body;
    }
    return fetch(url, opts).then(readJson).catch(function (e) {
      return { status: 0, error: (e && e.message) || 'jaringan' };
    });
  }

  // ------------------------------------------------------------- fetch util
  function api(path, params) {
    var qs = new URLSearchParams(Object.assign({ aid: '1988', app_language: 'id', region: 'ID' }, params || {}));
    return http('GET', API + path + '?' + qs.toString());
  }

  function itemId(it) {
    return it.id || it.aweme_id || it.item_id || it.awemeId || '';
  }

  function authorOf(it) {
    var a = it.author || (it.authorInfo) || {};
    return a.uniqueId || a.unique_id || a.nickname || '';
  }

  function looksRepost(it) {
    for (var i = 0; i < REPOST_FLAGS.length; i++) {
      if (it[REPOST_FLAGS[i]]) return true;
    }
    var stats = it.stats || {};
    if (it.isOriginal === false || it.is_original === false) return true;
    return false;
  }

  /** Ambil daftar item dari tab Repost di profil pengguna yang login. */
  function fetchRepostList(max) {
    var seen = {};
    var out = [];
    var pages = 0;
    function next() {
      if (out.length >= max || pages >= 6) return Promise.resolve(out);
      pages++;
      var params = {
        count: '20',
        cursor: String(collectCursor),
        user_id: (session && session.userId) || '',
        uniqueId: (session && session.username) || '',
        secUid: ''
      };
      return api('/post/item_list/', params).then(function (res) {
        var d = (res && res.data) || {};
        var items = d.itemList || d.items || d.item_list || [];
        if (!items.length) {
          return out;
        }
        var before = out.length;
        items.forEach(function (it) {
          var id = itemId(it);
          if (!id || seen[id]) return;
          seen[id] = true;
          out.push({
            id: id,
            desc: (it.desc || '').slice(0, 120),
            author: authorOf(it),
            repost: looksRepost(it)
          });
        });
        collectCursor = d.cursor || d.maxCursor || (out.length);
        if (out.length === before) return out; // tidak ada progres halaman
        return next();
      }).catch(function () { return out; });
    }
    return next();
  }

  /** Hapus satu item dari daftar repost. */
  function removeRepost(item) {
    if (settings.dryRun) {
      log('LATIHAN: tidak menghapus ' + shortId(item));
      return Promise.resolve({ ok: true, dry: true });
    }
    var body = new URLSearchParams({
      item_id: item.id,
      aweme_id: item.id,
      aweme_type: '1',
      aid: '1988',
      action_source: 'web'
    });
    return http('POST', API + '/v1/aweme/digest/undo/repost/', body.toString()).then(function (out) {
      var d = (out && out.data) || {};
      if (out && (d.status_code === 0 || d.statusCode === 0)) {
        return { ok: true };
      }
      // sebagian build memakai endpoint "unrepost"
      return http('POST', API + '/aweme/v1/undigest/', body.toString()).then(function (out2) {
        var d2 = (out2 && out2.data) || {};
        var ok = out2 && (d2.status_code === 0 || d2.statusCode === 0);
        return { ok: !!ok, message: ok ? null : errorMessage(out2) };
      });
    }).catch(function (e) { return { ok: false, message: (e && e.message) || 'jaringan' }; });
  }

  function shortId(item) {
    return item && item.id ? ('#' + String(item.id).slice(-6)) : '#?';
  }

  // =============================================================== MODE OTOMASI
  // Menjalankan otomasi di halaman TikTok asli lewat jembatan native.
  var dom = {
    running: false,
    batch: 15,
    gap: 12,
    dry: false,
    deleted: 0,
    target: 0,
    log: []
  };

  function domLog(line) {
    dom.log.push(line);
    if (dom.log.length > 300) dom.log.shift();
    var el = $('domLog');
    if (el) {
      var stamp = new Date().toLocaleTimeString('id-ID', { hour12: false });
      el.textContent = (el.textContent === 'siap.' ? '' : el.textContent + '\n') + '[' + stamp + '] ' + line;
      el.scrollTop = el.scrollHeight;
    }
  }

  // Dipanggil native selama otomasi berjalan
  window.UnrepostDomLog = function (line) { domLog(line); };
  window.UnrepostDomProgress = function (text, note, deleted, finished, aborted) {
    dom.deleted = deleted;
    $('domProgressWrap').hidden = false;
    $('domProgressText').textContent = aborted ? 'berhenti' : (finished ? 'selesai' : 'berjalan…');
    $('domCount').textContent = deleted + '/' + dom.target;
    $('domNote').textContent = (text || '') + (note ? ' · ' + note : '');
    if (finished || aborted) {
      dom.running = false;
      $('domStartBtn').disabled = false;
      $('domStartText').textContent = '🗑  Mulai hapus repost otomatis';
      $('domStopBtn').hidden = true;
      domLog((aborted ? 'DIHENTIKAN: ' : 'SELESAI: ') + (note || '') + ' · ' + deleted + ' repost');
      if (native && native.keepAwake) native.keepAwake(false);
      vibrate([50, 60, 50]);
      saveDomRun(aborted ? 'aborted' : 'done');
    }
  };
  window.UnrepostDomProbe = function (value) {
    var out = $('domProbeOut');
    out.hidden = false;
    out.textContent = String(value);
    domLog('probe: ' + value);
    copyText(String(value) + '\n\n=== log ===\n' + dom.log.join('\n'));
  };

  function domSessionCheck() {
    var ok = false;
    try { ok = !!(native && native.hasTiktokSession && native.hasTiktokSession()); } catch (e) { ok = false; }
    $('domLoginBox').hidden = ok;
    $('domSessionInfo').textContent = ok
      ? 'Sesi TikTok terdeteksi di aplikasi ini.'
      : 'Belum ada sesi TikTok. Login lewat mode otomasi dulu.';
    return ok;
  }

  function startDom() {
    if (!native || !native.startDomClean) {
      toast('Otomasi hanya tersedia di dalam aplikasi');
      return;
    }
    var username = (session && session.username) || '';
    if (!username) {
      // coba ambil dari cookie/sesi native; kalau tidak ada, minta login
      toast('Login TikTok dulu');
      openTikTok(WEB_LOGIN_URL);
      return;
    }
    dom.running = true;
    dom.deleted = 0;
    dom.target = dom.batch;
    $('domStartBtn').disabled = true;
    $('domStartText').textContent = '⏳  Bekerja di halaman TikTok…';
    $('domStopBtn').hidden = false;
    $('domProgressWrap').hidden = false;
    $('domCount').textContent = '0/' + dom.target;
    $('domLog').textContent = 'siap.';
    domLog('mulai otomasi: ' + dom.batch + ' video · jeda ' + dom.gap + ' dtk' + (dom.dry ? ' · LATIHAN' : ''));
    if (native.keepAwake) native.keepAwake(true);
    native.startDomClean(dom.batch, dom.gap * 1000, dom.dry, username);
  }

  function stopDom() {
    if (native && native.stopDomClean) native.stopDomClean();
    dom.running = false;
    $('domStartBtn').disabled = false;
    $('domStartText').textContent = '🗑  Mulai hapus repost otomatis';
    $('domStopBtn').hidden = true;
    domLog('berhenti diminta');
    if (native && native.keepAwake) native.keepAwake(false);
  }

  function saveDomRun(reason) {
    var all = store(STORE.history) || [];
    all.unshift({
      ts: Date.now(), mode: 'dom', reason: reason, done: dom.deleted,
      failed: 0, target: dom.target, dry: dom.dry,
      account: (session && session.username) || '?', items: []
    });
    save(STORE.history, all.slice(0, 40));
    renderHistory();
  }

  function copyText(text) {
    var done = function () { toast('Disalin — tempel ke chat'); };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () { fallbackCopy(text, done); });
    } else {
      fallbackCopy(text, done);
    }
  }

  // ------------------------------------------------------------- batch run
  function updateProgress() {
    var pct = runStats.target ? Math.min(100, Math.round(runStats.done / runStats.target * 100)) : 0;
    $('barFill').style.width = pct + '%';
    $('progressText').textContent = paused ? 'dijeda' : (runStats.failed ? 'berjalan · ' + runStats.failed + ' gagal' : 'berjalan…');
    $('progressCount').textContent = runStats.done + '/' + runStats.target;
  }

  function setRunningUI(on) {
    $('cleanBtn').disabled = on;
    $('progressWrap').hidden = !on;
    $('pauseBtn').hidden = !on;
    $('stopBtn').hidden = !on;
    $('cleanBtnText').textContent = on ? '⏳  Sedang membersihkan…' : '🗑  Bersihkan repost sekarang';
  }

  function startBatch() {
    if (running) return;
    if (!session) {
      toast('Login dulu ya');
      switchTab('settings');
      return;
    }
    runStats = { done: 0, failed: 0, target: settings.batch, startedAt: Date.now(), dryRun: settings.dryRun, items: [] };
    running = true;
    paused = false;
    stopped = false;
    collectCursor = 0;
    queue = [];
    $('log').textContent = 'siap.';
    log('mulai batch: target ' + settings.batch + ' · jeda ' + settings.gap + 'dtk' + (settings.dryRun ? ' · LATIHAN' : ''));
    setRunningUI(true);
    updateProgress();
    if (window.UnrepostNative && window.UnrepostNative.keepAwake) window.UnrepostNative.keepAwake(true);

    fetchRepostList(Math.min(60, settings.batch + 10)).then(function (items) {
      log('daftar repost terbaca: ' + items.length + ' item');
      if (!items.length) {
        log('tidak ada repost di profil — mungkin semua sudah bersih, atau perlu login ulang');
        toast('Tidak ada repost ditemukan');
        finishBatch('empty');
        return;
      }
      queue = items;
      runNext();
    }).catch(function (e) {
      log('gagal mengambil daftar: ' + (e && e.message ? e.message : 'jaringan'));
      finishBatch('error');
    });
  }

  function runNext() {
    if (stopped) { finishBatch('stopped'); return; }
    if (paused) {
      ST(runNext, 700);
      return;
    }
    if (runStats.done >= runStats.target || !queue.length) {
      finishBatch('done');
      return;
    }
    var item = queue.shift();
    updateProgress();
    log('hapus ' + shortId(item) + (item.desc ? ' — ' + item.desc.slice(0, 42) : ''));
    removeRepost(item).then(function (res) {
      if (res.ok) {
        runStats.done++;
        runStats.items.push({ id: item.id, desc: item.desc, ok: true, dry: !!res.dry });
        log('berhasil (' + (res.dry ? 'latihan' : 'terhapus') + ') → ' + runStats.done + '/' + runStats.target);
        if (runStats.done % 5 === 0) vibrate(60);
      } else {
        runStats.failed++;
        runStats.items.push({ id: item.id, desc: item.desc, ok: false, message: res.message });
        log('gagal: ' + (res.message || 'tidak diketahui'));
        if (runStats.failed >= 3) {
          log('3 kegagalan beruntun — berhenti supaya tidak dianggap spam');
          finishBatch('failed');
          return;
        }
      }
      updateProgress();
      var waitMs = settings.gap * 1000;
      log('istirahat ' + settings.gap + ' detik…');
      sleep(waitMs).then(runNext);
    });
  }

  function finishBatch(reason) {
    running = false;
    paused = false;
    setRunningUI(false);
    if (window.UnrepostNative && window.UnrepostNative.keepAwake) window.UnrepostNative.keepAwake(false);
    var label = { done: 'selesai', stopped: 'dihentikan', empty: 'tidak ada repost', failed: 'gagal beruntun', error: 'galat' }[reason] || reason;
    log('batch ' + label + ' · ' + runStats.done + ' berhasil, ' + runStats.failed + ' gagal');
    $('progressText').textContent = label;
    saveHistory(reason);
    toast('Batch ' + label + ': ' + runStats.done + ' repost');
    vibrate([40, 60, 40]);
    notifyNative('Batch ' + label, runStats.done + ' repost diproses');
  }

  function notifyNative(title, body) {
    if (settings.notify && window.UnrepostNative && window.UnrepostNative.notify) {
      window.UnrepostNative.notify(title, body);
    }
  }

  // --------------------------------------------------------------- riwayat
  function saveHistory(reason) {
    var all = store(STORE.history) || [];
    all.unshift({
      ts: Date.now(),
      reason: reason,
      done: runStats.done,
      failed: runStats.failed,
      target: runStats.target,
      dry: runStats.dryRun,
      account: session ? session.username : '?',
      items: runStats.items.slice(0, 60)
    });
    save(STORE.history, all.slice(0, 40));
    renderHistory();
  }

  function renderHistory() {
    var all = store(STORE.history) || [];
    var box = $('historyList');
    if (!all.length) {
      box.innerHTML = '<p class="muted small">Belum ada riwayat.</p>';
      return;
    }
    box.innerHTML = all.map(function (b) {
      var pill = b.dry ? '<span class="pill dry">latihan</span>'
        : b.failed > 0 ? '<span class="pill bad">' + b.failed + ' gagal</span>'
          : '<span class="pill ok">bersih</span>';
      var modePill = b.mode === 'dom' ? '<span class="pill dry">otomasi</span>' : '';
      var names = (b.items || []).slice(0, 6).map(function (i) {
        return '<div class="muted small">· ' + esc(i.desc || ('#' + String(i.id).slice(-6))) + (i.ok ? '' : ' ✕') + '</div>';
      }).join('');
      return '<div class="batch"><b>' + b.done + '/' + b.target + ' repost</b>' + pill + modePill +
        '<div class="meta">@' + esc(b.account) + ' · ' + fmt(b.ts) + ' · ' + esc(reasonLabel(b.reason)) + '</div>' +
        names + '</div>';
    }).join('');
  }

  function reasonLabel(r) {
    return { done: 'selesai', stopped: 'dihentikan', empty: 'tidak ada repost', failed: 'gagal beruntun', error: 'galat' }[r] || r;
  }

  // ------------------------------------------------------------------ tabs
  function switchTab(name) {
    ['clean', 'history', 'settings'].forEach(function (t) {
      var panel = $('panel-' + t);
      if (panel) panel.hidden = t !== name;
    });
    Array.prototype.forEach.call(document.querySelectorAll('.tab'), function (b) {
      b.classList.toggle('active', b.dataset.tab === name);
    });
    if (name === 'history') renderHistory();
    if (name === 'clean') domSessionCheck();
  }

  // -------------------------------------------------------------- settings
  function loadSettings() {
    settings = Object.assign({}, DEFAULTS, store(STORE.settings) || {});
    $('batch').value = settings.batch;
    $('batchVal').textContent = settings.batch;
    $('gap').value = settings.gap;
    $('gapVal').textContent = settings.gap;
    $('dryRun').checked = !!settings.dryRun;
    $('prefNotify').checked = settings.notify !== false;
    $('prefVibrate').checked = settings.vibrate !== false;
    $('prefKeepAwake').checked = settings.keepAwake !== false;
  }
  function saveSettings() {
    save(STORE.settings, settings);
  }

  // ------------------------------------------------------------------ init
  function bind() {
    $('loginBtn').onclick = onLoginClick;
    $('password').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') onLoginClick();
    });
    $('peekBtn').onclick = function () {
      var p = $('password');
      p.type = p.type === 'password' ? 'text' : 'password';
    };
    $('quickToggle').onclick = function () {
      var box = $('quickBox');
      box.hidden = !box.hidden;
      this.textContent = box.hidden ? 'buka' : 'tutup';
    };
    $('webLoginBtn').onclick = function () {
      openTikTok(WEB_LOGIN_URL);
      toast('Login di halaman TikTok, lalu kembali');
    };
    $('webLoginCheck').onclick = function () {
      toast('Memeriksa sesi…');
      checkWebSession().then(function (s) {
        if (s) { saveSession(s); afterLogin('website tiktok'); domSessionCheck(); }
        else toast('Belum terdeteksi login. Coba login di halaman TikTok dulu.');
      }).catch(function () { toast('Belum bisa memeriksa sesi'); });
    };

    Array.prototype.forEach.call(document.querySelectorAll('.tab'), function (b) {
      b.onclick = function () { switchTab(b.dataset.tab); };
    });

    $('batch').oninput = function () {
      settings.batch = parseInt(this.value, 10);
      $('batchVal').textContent = settings.batch;
      saveSettings();
    };
    $('gap').oninput = function () {
      settings.gap = parseInt(this.value, 10);
      $('gapVal').textContent = settings.gap;
      saveSettings();
    };
    $('dryRun').onchange = function () { settings.dryRun = this.checked; saveSettings(); };
    $('prefNotify').onchange = function () { settings.notify = this.checked; saveSettings(); };
    $('prefVibrate').onchange = function () { settings.vibrate = this.checked; saveSettings(); };
    $('prefKeepAwake').onchange = function () { settings.keepAwake = this.checked; saveSettings(); };

    // ---- mode otomasi (DOM)
    $('domLoginBtn').onclick = function () { openTikTok(WEB_LOGIN_URL); };
    $('domStartBtn').onclick = startDom;
    $('domStopBtn').onclick = stopDom;
    $('domBatch').oninput = function () {
      dom.batch = parseInt(this.value, 10);
      $('domBatchVal').textContent = dom.batch;
    };
    $('domGap').oninput = function () {
      dom.gap = parseInt(this.value, 10);
      $('domGapVal').textContent = dom.gap;
    };
    $('domDry').onchange = function () { dom.dry = this.checked; };
    $('domProbe').onclick = function () {
      if (native && native.probeDom) {
        toast('Memeriksa halaman TikTok…');
        native.probeDom();
      } else {
        toast('Hanya tersedia di dalam aplikasi');
      }
    };
    $('domRelogin').onclick = function () { openTikTok(WEB_LOGIN_URL); };
    $('domClearCookies').onclick = function () {
      if (native && native.clearTiktokSession) native.clearTiktokSession();
      clearSession();
      refreshSessionCard();
      domSessionCheck();
      toast('Sesi TikTok di app dihapus');
    };
    $('toggleApi').onclick = function () {
      var box = $('apiBox');
      box.hidden = !box.hidden;
      this.textContent = box.hidden ? 'Tampilkan mode API' : 'Sembunyikan mode API';
    };

    $('cleanBtn').onclick = startBatch;
    $('pauseBtn').onclick = function () {
      paused = !paused;
      this.textContent = paused ? 'Lanjut' : 'Jeda';
      updateProgress();
      log(paused ? 'dijeda' : 'dilanjutkan');
    };
    $('stopBtn').onclick = function () {
      stopped = true;
      log('berhenti diminta…');
    };

    $('helpBtn').onclick = function () { $('helpSheet').hidden = false; };
    $('helpClose').onclick = function () { $('helpSheet').hidden = true; };
    $('logoutBtn').onclick = function () {
      clearSession();
      refreshSessionCard();
      go('login');
      toast('Sesi dihapus');
    };
    $('reloginBtn').onclick = function () { go('login'); };
    $('verifyBtn').onclick = function () {
      if (!session) { go('login'); return; }
      checkWebSession().then(function (s) {
        if (s) { saveSession(Object.assign({}, session, s)); refreshSessionCard(); toast('Sesi valid'); }
        else toast('Sesi tidak valid — login ulang');
      });
    };
    $('copyDiag').onclick = copyDiagnostics;
    $('clearHistory').onclick = function () {
      save(STORE.history, []);
      renderHistory();
      toast('Riwayat dihapus');
    };
  }

  /** Mode pratinjau (docs/mockup.html): isi data contoh tanpa jaringan. */
  function bootMock(kind) {
    session = { identifier: 'kreator.id', username: 'kreator.id', nickname: 'Kreator ID', ts: Date.now() - 3600e3 };
    $('log').textContent = '[12:04:11] sesi tersimpan (jalur cepat)\n' +
      '[12:04:20] daftar repost terbaca: 18 item\n' +
      '[12:04:20] hapus #883412 — video dance viral\n' +
      '[12:04:21] berhasil (terhapus) → 1/15\n' +
      '[12:04:33] hapus #771290 — repost dari @lain\n' +
      '[12:04:34] berhasil (terhapus) → 2/15\n' +
      '[12:04:34] istirahat 12 detik…';
    $('progressWrap').hidden = false;
    $('pauseBtn').hidden = false;
    $('stopBtn').hidden = false;
    $('cleanBtn').disabled = true;
    $('cleanBtnText').textContent = '⏳  Sedang membersihkan…';
    $('barFill').style.width = '13%';
    $('progressText').textContent = 'berjalan…';
    $('progressCount').textContent = '2/15';
    save(STORE.history, [{
      ts: Date.now() - 86400e3, reason: 'done', done: 15, failed: 0, target: 15, dry: false,
      account: 'kreator.id', items: [{ id: '883412', desc: 'video dance viral', ok: true }]
    }]);
    loadSettings();
    bind();
    refreshSessionCard();
    renderHistory();
    if (kind === 'app') { go('app'); switchTab('clean'); }
    else { go('login'); }
    $('identifier').value = '';
  }

  function renderDiagnostics() {
    var el = $('diag');
    if (!el) return;
    var lines = [];
    lines.push('Unrepost ' + (native ? '2.2' : 'web'));
    if (native && native.deviceInfo) {
      try {
        var d = JSON.parse(native.deviceInfo());
        lines.push('Android ' + d.android + ' · ' + d.model);
        lines.push('Memori maks ' + d.heapMB + ' MB');
      } catch (e) { /* abaikan */ }
    }
    lines.push('Transpor: ' + (native && native.api ? 'native HTTP' : 'fetch browser'));
    lines.push('Sesi: ' + (session ? '@' + session.username : 'belum login'));
    el.textContent = lines.join(' · ');
  }

  function copyDiagnostics() {
    var text = '=== Unrepost diagnostik ===\n' + ($('diag') ? $('diag').textContent : '') +
      '\n\n=== log ===\n' + $('log').textContent;
    var done = function () { toast('Disalin — tempel ke chat pengembang'); };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () { fallbackCopy(text, done); });
    } else {
      fallbackCopy(text, done);
    }
  }

  function fallbackCopy(text, done) {
    try {
      var ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      done();
    } catch (e) { toast('Tidak bisa menyalin otomatis'); }
  }

  function boot() {
    var mock = null;
    try {
      var q = (window.location && window.location.search) || '';
      var m = q.match(/mock=([a-z]+)/);
      if (m) mock = m[1];
    } catch (e) { /* abaikan */ }
    if (mock) { bootMock(mock); return; }
    var note = native ? 'mode aplikasi (HTTP native)' : 'mode browser';
    log('mulai · ' + note);
    loadSession();
    loadSettings();
    bind();
    refreshSessionCard();
    renderHistory();
    renderDiagnostics();
    if ($('domBatch')) { $('domBatchVal').textContent = dom.batch; $('domGapVal').textContent = dom.gap; }
    domSessionCheck();
    go(session ? 'app' : 'login');

    // Jangan biarkan satu error kecil menutup aplikasi.
    if (typeof window.addEventListener === 'function') {
      window.addEventListener('error', function (e) {
        try { log('error UI: ' + (e && e.message ? e.message : 'tidak diketahui')); } catch (x) {}
      });
      window.addEventListener('unhandledrejection', function (e) {
        try { log('promise gagal: ' + (e && e.reason ? String(e.reason) : 'tidak diketahui')); } catch (x) {}
      });
    }
  }

  // API untuk native
  window.Unrepost = {
    onNavigate: onNavigate,
    openTiktokDone: function () { page.name = 'app'; renderRoute(); },
    isRunning: function () { return running; },
    version: '2.1'
  };

  document.addEventListener('DOMContentLoaded', boot);
  if (document.readyState !== 'loading') boot();
})();
