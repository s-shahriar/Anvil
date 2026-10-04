/* Drives a pre-rendered formula page. Everything is rendered "covered" once; this switches cover mode on and off,
   reveals one covered element per tap, and talks to Slate (stars, current section). */
(function () {
  var init = window.__init || {};
  var cover = !!init.cover;
  var covers = [];

  function collect() {
    var els = document.querySelectorAll('.mf-covered,.mf-tex-covered,.eq-covered');
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      var cls = e.classList.contains('mf-covered') ? 'mf-covered' : e.classList.contains('mf-tex-covered') ? 'mf-tex-covered' : 'eq-covered';
      e.setAttribute('data-cv', cls);
      covers.push(e);
    }
  }

  window.setCover = function (on) {
    cover = !!on;
    var wrap = document.querySelector('.mf-wrap');
    if (wrap) wrap.classList.toggle('mf-cover-on', cover);
    var shown = document.querySelectorAll('.mf-revealed');
    for (var i = 0; i < shown.length; i++) shown[i].classList.remove('mf-revealed');
    for (var j = 0; j < covers.length; j++) covers[j].classList.toggle(covers[j].getAttribute('data-cv'), cover);
  };

  window.setImportant = function (uids) {
    var set = {};
    for (var i = 0; i < uids.length; i++) set[uids[i]] = 1;
    var cards = document.querySelectorAll('.mf-card[data-uid]');
    for (var j = 0; j < cards.length; j++) {
      var c = cards[j], on = !!set[c.getAttribute('data-uid')];
      c.classList.toggle('is-important', on);
      var b = c.querySelector(':scope > .mf-imp-btn');
      if (b) { b.classList.toggle('on', on); b.setAttribute('aria-pressed', on ? 'true' : 'false'); }
    }
  };

  window.setImportantOnly = function (on) {
    var r = document.querySelector('.mf-root');
    if (r) r.classList.toggle('mf-important-only', !!on);
  };

  window.scrollToId = function (id) {
    var el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t || !t.closest) return;
    var star = t.closest('.mf-imp-btn');
    if (star) {
      var card = star.closest('.mf-card');
      var uid = card && card.getAttribute('data-uid');
      if (uid && window.Slate) window.Slate.toggleImportant(uid);
      e.preventDefault();
      return;
    }
    if (!cover) return;
    var cv = t.closest('[data-cv]');
    if (cv) { cv.classList.toggle(cv.getAttribute('data-cv')); return; }
    var st = t.closest('.mf-cmp-table:not(.mf-no-cover) td.hl, .mf-fi, .mf-set-box-val, .mf-cv');
    if (st) st.classList.toggle('mf-revealed');
  }, true);

  function start() {
    collect();
    window.setCover(cover);
    window.setImportant(init.important || []);
    window.setImportantOnly(!!init.importantOnly);
    // Tell Slate which section is under the top of the page, so its chip row can follow.
    var secs = document.querySelectorAll('.mf-section, .eq-group');
    if ('IntersectionObserver' in window && window.Slate && window.Slate.onSection) {
      var obs = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { if (en.isIntersecting) window.Slate.onSection(en.target.id); });
      }, { rootMargin: '-10% 0px -75% 0px' });
      for (var i = 0; i < secs.length; i++) obs.observe(secs[i]);
    }
    if (window.Slate && window.Slate.onReady) window.Slate.onReady();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
