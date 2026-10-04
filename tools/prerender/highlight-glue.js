// Page glue for highlights in a pre-rendered page. Runs after the web's own textAnchor/highlightDom code above.
// Slate sends the saved highlights in (setHighlights) and hears about selections and taps on marks (Slate.onSelection/onMark).
var all = {};            // uid -> [highlight]
var settleTimer = null;

function roots() { return document.querySelectorAll('[data-hl-root]'); }

function paint() {
  roots().forEach(function (root) {
    var uid = root.getAttribute('data-hl-root');
    var list = all[uid] || [];
    root.querySelectorAll('[data-hl-block]').forEach(function (el) {
      var block = el.getAttribute('data-hl-block');
      var mine = list.filter(function (h) { return h.block === block; });
      // A formula is KaTeX markup, so character offsets mean nothing inside it: any mark covers the whole formula.
      if (/\.formula$/.test(block)) {
        el.classList.remove('hl-mark', 'hl-c-mint', 'hl-c-amber', 'hl-c-rose', 'hl-c-violet', 'hl-whole');
        el.removeAttribute('data-hl-ids'); el.removeAttribute('data-hl-color');
        if (mine.length) {
          el.classList.add('hl-mark', 'hl-whole', 'hl-c-' + mine[0].color);
          el.setAttribute('data-hl-ids', mine.map(function (h) { return h.id; }).join(','));
          el.setAttribute('data-hl-color', mine[0].color);
        }
        return;
      }
      applyRanges(el, mine.length ? rangesFor(el.textContent, mine) : []);
    });
  });
}

window.setHighlights = function (json) { all = JSON.parse(json); paint(); };
window.clearSelection = function () { var s = window.getSelection(); if (s) s.removeAllRanges(); };

function report() {
  var sel = window.getSelection();
  if (!sel || sel.isCollapsed || !sel.rangeCount) { if (window.Slate && Slate.onSelection) Slate.onSelection(''); return; }
  var node = sel.anchorNode && (sel.anchorNode.nodeType === 3 ? sel.anchorNode.parentElement : sel.anchorNode);
  var root = node && node.closest && node.closest('[data-hl-root]');
  if (!root) { Slate.onSelection(''); return; }
  var anchors = selectionToAnchors(sel).map(function (a) {
    if (/\.formula$/.test(a.block)) {                    // a formula is marked whole
      var el = root.querySelector('[data-hl-block="' + a.block.replace(/"/g, '\\"') + '"]');
      var t = el ? el.textContent : a.quote;
      return { block: a.block, start: 0, end: t.length, quote: t };
    }
    return a;
  });
  if (!anchors.length) { Slate.onSelection(''); return; }
  Slate.onSelection(JSON.stringify({ uid: root.getAttribute('data-hl-root'), anchors: anchors }));
}

document.addEventListener('selectionchange', function () {
  clearTimeout(settleTimer);
  settleTimer = setTimeout(report, 300);
});

// Tapping a mark offers recolour / remove for it.
document.addEventListener('click', function (e) {
  var mark = e.target.closest && e.target.closest('.hl-mark');
  if (!mark || !window.Slate || !Slate.onMark) return;
  var root = mark.closest('[data-hl-root]');
  if (!root) return;
  Slate.onMark(JSON.stringify({ uid: root.getAttribute('data-hl-root'), ids: mark.getAttribute('data-hl-ids').split(','), color: mark.getAttribute('data-hl-color') }));
}, true);
