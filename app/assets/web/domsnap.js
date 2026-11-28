/* ==========================================================================
   DOM Snapshoter (di-inject ke halaman TikTok di WebView).
   - Mengirim HTML yang sudah "dibersihkan" ke native (UnrepostSnap.snap)
   - Menandai elemen kandidat dengan data-ur / id unik
   - Menjalankan perintah: info, find, menu, click, back, scroll, clickXY
   ========================================================================== */
(function () {
  if (window.UnrepostSnap && window.UnrepostSnap.__v >= 3) return;

  var MAX_BYTES = 700 * 1024;
  var KEEP = {
    A: 1, BUTTON: 1, SPAN: 1, DIV: 1, P: 1, IMG: 1, SVG: 1, VIDEO: 1, LI: 1, UL: 1,
    H1: 1, H2: 1, H3: 1, H4: 1, SECTION: 1, HEADER: 1, FOOTER: 1, NAV: 1, MAIN: 1,
    ARTICLE: 1, ASIDE: 1, FORM: 1, LABEL: 1, INPUT: 1, TEXTAREA: 1, DIALOG: 1,
    SHARE: 0, TEMPLATE: 0, SCRIPT: 0, STYLE: 0, NOSCRIPT: 0, LINK: 0, META: 0, IFRAME: 0
  };

  function isVisible(el) {
    try {
      if (!el) return false;
      var st = el.ownerDocument && el.ownerDocument.defaultView
        ? el.ownerDocument.defaultView.getComputedStyle(el) : window.getComputedStyle(el);
      if (st) {
        if (st.display === 'none' || st.visibility === 'hidden') return false;
        // opacity kosong ('') berarti 1 — jangan dianggap 0
        var op = st.opacity;
        if (op !== '' && op != null && +op === 0) return false;
      }
      if (el.hidden) return false;
      if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return false;
      var r = el.getBoundingClientRect();
      // layout belum siap (0x0) tetap dianggap ada selama tidak display:none
      if (r && (r.width > 0 || r.height > 0)) return true;
      return true;
    } catch (e) { return true; }
  }

  function labelOf(el) {
    var parts = [];
    // naik ke induk yang bisa diklik untuk mendapatkan label yang lebih jelas
    try {
      var up = el;
      for (var i = 0; i < 3 && up && up.parentElement; i++) {
        if (up.getAttribute && (up.getAttribute('aria-label') || up.getAttribute('data-e2e'))) break;
        up = up.parentElement;
        var al2 = up.getAttribute && (up.getAttribute('aria-label') || up.getAttribute('title'));
        if (al2) { parts.push(al2); break; }
      }
    } catch (e) { /* abaikan */ }
    var al = el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('title'));
    if (al) parts.push(al);
    var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
    if (txt && txt.length < 60) parts.push(txt);
    if (el.id) parts.push('#' + el.id);
    if (el.getAttribute && el.getAttribute('data-e2e')) parts.push('[e2e=' + el.getAttribute('data-e2e') + ']');
    return parts.join(' | ').slice(0, 120);
  }

  function clean(root) {
    var out = [];
    (function walk(node) {
      if (!node || out.length > 2500) return;
      var tag = (node.tagName || '').toUpperCase();
      if (!KEEP[tag] && tag !== 'BODY' && tag !== 'HTML') return;
      var keep = node === root || isVisible(node);
      if (keep && tag && tag !== 'HTML' && tag !== 'BODY') {
        if (!node.getAttribute('data-ur')) {
          node.setAttribute('data-ur', String(++SEQ));
        }
        var attrs = ' data-ur="' + node.getAttribute('data-ur') + '"';
        ['role', 'aria-label', 'data-e2e', 'type', 'href', 'alt', 'title', 'name'].forEach(function (a) {
          var v = node.getAttribute && node.getAttribute(a);
          if (v) attrs += ' ' + a + '="' + String(v).replace(/"/g, '&quot;').slice(0, 80) + '"';
        });
        var text = '';
        if (tag === 'INPUT' || tag === 'TEXTAREA') {
          text = (node.value || '').slice(0, 40);
        } else {
          var direct = '';
          for (var i = 0; i < node.childNodes.length; i++) {
            var c = node.childNodes[i];
            if (c.nodeType === 3) direct += c.nodeValue;
          }
          direct = direct.replace(/\s+/g, ' ').trim();
          if (direct) text = direct.slice(0, 160);
        }
        out.push('<' + tag.toLowerCase() + attrs + '>' + escape(text));
        var kids = node.children || [];
        for (var j = 0; j < kids.length; j++) walk(kids[j]);
        out.push('</' + tag.toLowerCase() + '>');
      } else {
        var kids2 = node.children || [];
        for (var k = 0; k < kids2.length; k++) walk(kids2[k]);
      }
    })(root || document.body);
    var html = out.join('');
    if (html.length > MAX_BYTES) html = html.slice(0, MAX_BYTES) + '<!--truncated-->';
    if (!html && window.__UR_DEBUG__) {
      try { console.log('clean kosong; body children=' + (document.body ? document.body.children.length : -1)); }
      catch (e) { /* abaikan */ }
    }
    return html;
  }

  function escape(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  var SEQ = 0;

  function snap(url) {
    try {
      var html = clean(document.body);
      var payload = JSON.stringify({ url: url || location.href, html: html });
      post(payload);
    } catch (e) {
      post(JSON.stringify({ url: location.href, html: '<!--error ' + e.message + '-->' }));
    }
  }

  function post(payload) {
    try {
      if (window.UnrepostDom && window.UnrepostDom.snap) window.UnrepostDom.snap(payload);
    } catch (e) { /* jembatan hilang */ }
  }

  function q(sel) {
    try { return document.querySelector(sel); } catch (e) { return null; }
  }

  function findByText(regexSource) {
    var re;
    try { re = new RegExp(regexSource, 'i'); } catch (e) { return null; }
    var nodes = document.querySelectorAll('button, [role="button"], a, div, span, li, p');
    var best = null;
    for (var i = 0; i < nodes.length && i < 4000; i++) {
      var el = nodes[i];
      if (!isVisible(el)) continue;
      var t = (el.innerText || el.textContent || '').trim();
      var a = el.getAttribute && (el.getAttribute('aria-label') || '');
      if (re.test(t) || re.test(a)) {
        // pilih elemen terkecil (paling spesifik) yang cocok
        if (!best || t.length < (best.innerText || '').length) best = el;
      }
    }
    return best;
  }

  function info() {
    var doc = document;
    var btns = 0;
    var all = doc.querySelectorAll('button, [role="button"], a');
    for (var i = 0; i < all.length && i < 2000; i++) if (isVisible(all[i])) btns++;
    return JSON.stringify({
      url: location.href,
      title: doc.title || '',
      ready: doc.readyState,
      buttons: btns,
      hasLoginForm: !!q('input[type="password"]'),
      hasVideo: !!q('video'),
      vh: window.innerHeight || 0
    });
  }

  function scrollByY(dy) {
    try {
      var sc = document.scrollingElement || document.documentElement;
      if (sc) sc.scrollTop += dy;
      var main = document.querySelector('main') || document.querySelector('[role="main"]');
      if (main) main.scrollTop += dy;
    } catch (e) { /* abaikan */ }
    return 'ok';
  }

  function docEl() { return document.documentElement; }

  // ---- snapshot otomatis: TikTok adalah SPA, halaman tidak reload saat pindah tab.
  var lastSig = '';
  function signature() {
    try {
      var t = document.body ? (document.body.innerText || '') : '';
      return (location.href || '') + '|' + t.length + '|' + (t.slice(0, 120));
    } catch (e) { return String(Date.now()); }
  }

  function autoSnap() {
    try {
      var sig = signature();
      if (sig !== lastSig) {
        lastSig = sig;
        snap(location.href);
      }
    } catch (e) { /* abaikan */ }
  }

  var INTERVAL = 1200;
  try {
    window.setInterval(autoSnap, INTERVAL);
    if (window.MutationObserver && document.body) {
      var pend = null;
      var obs = new MutationObserver(function () {
        if (pend) return;
        pend = window.setTimeout(function () { pend = null; autoSnap(); }, 450);
      });
      obs.observe(document.body, { childList: true, subtree: true, attributes: true });
    }
    // URL berubah (SPA) → snapshot segera
    ['pushState', 'replaceState'].forEach(function (fn) {
      var orig = history[fn];
      if (!orig) return;
      history[fn] = function () {
        var r = orig.apply(this, arguments);
        try { window.setTimeout(autoSnap, 60); } catch (e) {}
        return r;
      };
    });
    window.addEventListener('popstate', function () { window.setTimeout(autoSnap, 60); });
  } catch (e) { /* abaikan */ }

  window.UnrepostSnap = {
    __v: 3,
    snapNow: function () { snap(); return 'ok'; },
    info: info,
    find: function (regexSource, tap) {
      var el = findByText(regexSource);
      if (!el) return 'notfound';
      if (tap) return clickEl(el);
      return labelOf(el);
    },
    send: function () { snap(); return 'ok'; },
    menu: function () {
      // cari tombol menu/bagikan yang umum di halaman video TikTok
      var sels = [
        '[data-e2e="share-icon"]', '[data-e2e="more-menu"]', '[data-e2e="browse-more"]',
        '[data-e2e="video-share-icon"]', 'button[aria-label*="More"]',
        'button[aria-label*="more"]', 'button[aria-label*="Share"]',
        'button[aria-label*="share"]', '[role="button"][aria-label*="Bagikan"]'
      ];
      for (var i = 0; i < sels.length; i++) {
        var el = q(sels[i]);
        if (el && isVisible(el)) return clickEl(el);
      }
      var byText = findByText('^(bagikan|share|lainnya|more|opsi|options)$');
      if (byText) return clickEl(byText);
      return 'notfound';
    },
    click: function (ur) {
      var el = document.querySelector('[data-ur="' + ur + '"]');
      if (!el) return 'notfound';
      return clickEl(el);
    },
    clickXY: function (x, y) {
      var el = null;
      try { el = document.elementFromPoint(x, y); } catch (e) { el = null; }
      if (!el) return 'notfound';
      return clickEl(el);
    },
    back: function () {
      try { history.back(); return 'ok'; } catch (e) { return 'error'; }
    },
    scroll: function (dy) { return scrollByY(dy); },
    /** Ringkasan halaman untuk kalibrasi (tidak mengubah apa pun). */
    summary: function () {
      try {
        var tabs = [];
        var nodes = document.querySelectorAll('[role="tab"], [data-e2e*="tab"]');
        for (var i = 0; i < nodes.length && i < 12; i++) {
          tabs.push(((nodes[i].innerText || '').trim() || nodes[i].getAttribute('data-e2e') || '?').slice(0, 24));
        }
        var videos = 0;
        var links = document.querySelectorAll('a[href*="/video/"]');
        for (var j = 0; j < links.length && j < 500; j++) videos++;
        var buttons = [];
        var b = document.querySelectorAll('button, [role="button"]');
        for (var k = 0; k < b.length && k < 25; k++) {
          var txt = (b[k].getAttribute('aria-label') || b[k].innerText || '').trim();
          if (txt) buttons.push(txt.slice(0, 20));
        }
        return JSON.stringify({
          url: location.href,
          tabs: tabs,
          videos: videos,
          buttons: buttons,
          title: document.title
        });
      } catch (e) { return JSON.stringify({ error: e.message }); }
    }
  };

  function clickEl(el) {
    try {
      el.scrollIntoView({ block: 'center', behavior: 'instant' });
    } catch (e) {
      try { el.scrollIntoView(); } catch (x) { /* abaikan */ }
    }
    try {
      var r = el.getBoundingClientRect();
      var opts = { bubbles: true, cancelable: true, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
      el.dispatchEvent(new MouseEvent('pointerdown', opts));
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      el.dispatchEvent(new MouseEvent('pointerup', opts));
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      // satu klik saja: el.click() sudah mengirim event click
      if (typeof el.click === 'function') el.click();
      else el.dispatchEvent(new MouseEvent('click', opts));
      return 'ok';
    } catch (e) {
      return 'error:' + e.message;
    }
  }
})();
