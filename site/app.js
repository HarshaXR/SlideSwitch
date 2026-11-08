// SlideSwitch site: scroll reveals, instant press feedback, and highlighting the download
// that matches the visitor's device.

// Reveal elements as they enter the viewport (opacity/transform only; CSS handles reduced motion).
const io = new IntersectionObserver((entries) => {
  for (const entry of entries) {
    if (entry.isIntersecting) {
      entry.target.classList.add('in');
      io.unobserve(entry.target);
    }
  }
}, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
document.querySelectorAll('.reveal').forEach((el, i) => {
  el.style.transitionDelay = `${Math.min(i % 4, 3) * 60}ms`;
  io.observe(el);
});

// Feedback on pointer-down (not on click), released on up/cancel/leave.
document.querySelectorAll('.btn').forEach((btn) => {
  const release = () => btn.classList.remove('pressed');
  btn.addEventListener('pointerdown', () => btn.classList.add('pressed'));
  ['pointerup', 'pointercancel', 'pointerleave'].forEach((t) => btn.addEventListener(t, release));
});

// Point the hero button at the visitor's platform and mark that card.
const ua = navigator.userAgent;
const platform = /Android/i.test(ua) ? 'android'
  : /Mac/i.test(ua) && !/iPhone|iPad/i.test(ua) ? 'mac'
  : /Windows/i.test(ua) ? 'windows'
  : null;
if (platform) {
  const card = document.querySelector(`.download[data-platform="${platform}"]`);
  card?.classList.add('is-yours');
  const link = card?.querySelector('.btn');
  const hero = document.getElementById('hero-download');
  if (link && hero) {
    hero.href = link.href;
    hero.textContent = { android: 'Download for Android', mac: 'Download for Mac', windows: 'Download for Windows' }[platform];
  }
}
