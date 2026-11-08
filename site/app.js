// SlideSwitch pitch page: the live remote demo, the scroll-lit manifesto, the pairing demo,
// press feedback, scroll reveals, and platform-aware download buttons.

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------- Scroll reveals ----------
const revealer = new IntersectionObserver((entries) => {
  for (const e of entries) if (e.isIntersecting) { e.target.classList.add('in'); revealer.unobserve(e.target); }
}, { rootMargin: '0px 0px -8% 0px', threshold: 0.1 });
document.querySelectorAll('.reveal').forEach((el) => {
  // Stagger siblings that enter together (tiles, cards).
  const siblings = [...el.parentElement.children].filter((c) => c.classList.contains('reveal'));
  el.style.transitionDelay = `${Math.min(siblings.indexOf(el), 5) * 70}ms`;
  revealer.observe(el);
});

// ---------- Press feedback on pointer-down ----------
document.querySelectorAll('.btn, .key, .dl').forEach((el) => {
  const off = () => el.classList.remove('pressed');
  el.addEventListener('pointerdown', () => el.classList.add('pressed'));
  ['pointerup', 'pointercancel', 'pointerleave'].forEach((t) => el.addEventListener(t, off));
});

// ---------- Live remote demo ----------
const deck = document.getElementById('deck');
const counter = document.getElementById('deck-counter');
const blackout = document.getElementById('blackout');
const blackoutText = document.getElementById('blackout-text');
const total = deck.children.length;
let index = 0;
let black = false;
let ended = false;

function render() {
  deck.style.transform = `translateX(calc(${-index} * (100% + 1rem)))`;
  counter.textContent = ended ? 'Slideshow ended' : `${index + 1} / ${total}`;
  blackout.classList.toggle('on', black || ended);
  blackoutText.textContent = ended ? 'End of slide show. Tap Start to begin again.' : '';
}

function act(action) {
  // A short buzz on phones that support it: one tap, one tick.
  if (navigator.vibrate) navigator.vibrate(8);
  switch (action) {
    case 'next':
      if (ended) return;
      black = false;
      if (index < total - 1) index++;
      else ended = true;
      break;
    case 'previous':
      if (ended) { ended = false; break; }
      black = false;
      index = Math.max(0, index - 1);
      break;
    case 'start': index = 0; black = false; ended = false; break;
    case 'black': if (!ended) black = !black; break;
    case 'end': ended = true; black = false; break;
  }
  render();
}

document.querySelectorAll('.phone [data-action]').forEach((btn) => {
  btn.addEventListener('click', () => act(btn.dataset.action));
});

// Arrow keys drive the demo while it's on screen, like a real clicker.
let stageVisible = false;
new IntersectionObserver(([e]) => { stageVisible = e.isIntersecting; }, { threshold: 0.4 })
  .observe(document.getElementById('try'));
addEventListener('keydown', (e) => {
  if (!stageVisible || e.metaKey || e.ctrlKey || e.altKey) return;
  const map = { ArrowRight: 'next', ArrowLeft: 'previous', ' ': 'next', PageDown: 'next', PageUp: 'previous', b: 'black', B: 'black', Escape: 'end' };
  const action = map[e.key];
  if (!action) return;
  e.preventDefault();
  act(action);
  const btn = document.querySelector(`.phone [data-action="${action}"]`);
  btn?.classList.add('pressed');
  setTimeout(() => btn?.classList.remove('pressed'), 120);
});
render();

// ---------- Manifesto: words light up as the paragraph crosses the viewport ----------
const manifesto = document.getElementById('manifesto');
const words = manifesto.textContent.trim().split(/\s+/);
manifesto.innerHTML = words.map((w) => `<span class="w">${w}</span>`).join(' ');
const spans = [...manifesto.querySelectorAll('.w')];
function lightWords() {
  const r = manifesto.getBoundingClientRect();
  const vh = innerHeight;
  // 0 when the paragraph's top reaches 85% of the viewport, 1 when its bottom reaches 45%.
  const progress = Math.min(1, Math.max(0, (vh * 0.85 - r.top) / (r.height + vh * 0.4)));
  const lit = Math.round(progress * spans.length);
  spans.forEach((s, i) => s.classList.toggle('lit', i < lit));
}
if (reduceMotion) spans.forEach((s) => s.classList.add('lit'));
else {
  let queued = false;
  addEventListener('scroll', () => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => { queued = false; lightWords(); });
  }, { passive: true });
  lightWords();
}

// ---------- Pairing demo: a QR pattern and a code that refreshes now and then ----------
const grid = document.getElementById('qr-grid');
function drawQr(seed) {
  let s = seed;
  const rand = () => ((s = (s * 9301 + 49297) % 233280) / 233280);
  const cells = [];
  for (let y = 0; y < 21; y++) {
    for (let x = 0; x < 21; x++) {
      // Three finder squares in the corners, like a real QR code.
      const inFinder = (fx, fy) => x >= fx && x < fx + 7 && y >= fy && y < fy + 7;
      const finder = [[0, 0], [14, 0], [0, 14]].find(([fx, fy]) => inFinder(fx, fy));
      let on;
      if (finder) {
        const dx = x - finder[0], dy = y - finder[1];
        on = dx === 0 || dy === 0 || dx === 6 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
      } else {
        const nearFinder = [[0, 0], [14, 0], [0, 14]].some(([fx, fy]) => x >= fx - 1 && x <= fx + 7 && y >= fy - 1 && y <= fy + 7);
        on = !nearFinder && rand() > 0.52;
      }
      cells.push(`<i class="${on ? '' : 'off'}"></i>`);
    }
  }
  grid.innerHTML = cells.join('');
}
drawQr(482913);

const code = document.getElementById('code');
if (!reduceMotion) {
  setInterval(() => {
    if (document.hidden) return;
    const next = String(Math.floor(Math.random() * 1e6)).padStart(6, '0');
    code.classList.add('rolling');
    setTimeout(() => {
      [...code.children].forEach((s, i) => { s.textContent = next[i]; });
      drawQr(Number(next));
      code.classList.remove('rolling');
    }, 380);
  }, 6000);
}

// ---------- Platform-aware download ----------
const ua = navigator.userAgent;
const platform = /Android/i.test(ua) ? 'android'
  : /Mac/i.test(ua) && !/iPhone|iPad/i.test(ua) ? 'mac'
  : /Windows/i.test(ua) ? 'windows' : null;
if (platform) {
  const card = document.querySelector(`.dl[data-platform="${platform}"]`);
  card?.classList.add('is-yours');
  const hero = document.getElementById('hero-download');
  if (card && hero) {
    hero.href = card.href;
    hero.textContent = { android: 'Download for Android', mac: 'Download for Mac', windows: 'Download for Windows' }[platform];
  }
}
