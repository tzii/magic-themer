// Magic Themer — project site interactions. No dependencies, no tracking.

const motionMQ = matchMedia('(prefers-reduced-motion: reduce)');
const darkMQ = matchMedia('(prefers-color-scheme: dark)');
const isReduced = () => motionMQ.matches;
const finePointer = matchMedia('(pointer: fine)').matches;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const NS = 'http://www.w3.org/2000/svg';
const COLORS_LIGHT = ['#8fdcb2', '#f6b0a4', '#a9cff6', '#f7dd8e', '#f2b632', '#1c474d'];
const COLORS_DARK = ['#8fdcb2', '#f6b0a4', '#a9cff6', '#f7dd8e', '#f2b632', '#eef6e6'];

function isDarkTheme() {
  const t = document.documentElement.dataset.theme;
  return t ? t === 'dark' : darkMQ.matches;
}

function makeSvg(id, cls) {
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('class', cls);
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(NS, 'use');
  use.setAttribute('href', id);
  svg.append(use);
  return svg;
}

/* Little star burst at a viewport point */
function burst(x, y, count = 8, dist = 70) {
  if (isReduced()) return;
  const palette = isDarkTheme() ? COLORS_DARK : COLORS_LIGHT;
  for (let i = 0; i < count; i++) {
    const s = makeSvg('#i-spark', 'sparkle');
    s.style.left = `${x - 7}px`;
    s.style.top = `${y - 7}px`;
    s.style.fill = palette[i % 5];
    document.body.append(s);
    const a = (i / count) * Math.PI * 2 + Math.random() * .5;
    const d = dist * (.6 + Math.random() * .6);
    s.animate([
      { transform: 'translate(0,0) scale(.3) rotate(0)', opacity: 1 },
      { transform: `translate(${Math.cos(a) * d}px, ${Math.sin(a) * d}px) scale(1.2) rotate(180deg)`, opacity: 0 },
    ], { duration: 700 + Math.random() * 300, easing: 'cubic-bezier(.2,.8,.3,1)' }).onfinish = () => s.remove();
  }
}

/* Confetti cannon from a viewport point */
function confetti(x, y, darkSurface = false, count = 44) {
  if (isReduced()) return;
  const palette = (darkSurface || isDarkTheme()) ? COLORS_DARK : COLORS_LIGHT;
  for (let i = 0; i < count; i++) {
    const c = document.createElement('span');
    c.className = 'confetto';
    c.style.left = `${x}px`;
    c.style.top = `${y}px`;
    c.style.background = palette[i % palette.length];
    if (i % 3 === 0) c.style.borderRadius = '50%';
    document.body.append(c);
    const a = -Math.PI / 2 + (Math.random() - .5) * 2.2;
    const v = 160 + Math.random() * 220;
    const dx = Math.cos(a) * v;
    const dy = Math.sin(a) * v;
    c.animate([
      { transform: 'translate(0,0) rotate(0)', opacity: 1 },
      { transform: `translate(${dx}px, ${dy}px) rotate(${360 * Math.random()}deg)`, opacity: 1, offset: .5 },
      { transform: `translate(${dx * 1.3}px, ${dy + 380}px) rotate(${720 * Math.random()}deg)`, opacity: 0 },
    ], { duration: 1400 + Math.random() * 600, easing: 'cubic-bezier(.15,.7,.4,1)' }).onfinish = () => c.remove();
  }
}

/* Theme toggle, aria-label & <meta name="theme-color"> sync */
{
  const btn = $('#theme-toggle');
  const meta = $('#theme-color');
  const root = document.documentElement;
  const syncThemeMeta = () => {
    const dark = isDarkTheme();
    if (meta) meta.content = dark ? '#0c1c1d' : '#f3f7ea';
    btn.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
  };
  btn.hidden = false;
  syncThemeMeta();
  darkMQ.addEventListener('change', () => { if (!root.dataset.theme) syncThemeMeta(); });
  btn.addEventListener('click', () => {
    root.dataset.theme = isDarkTheme() ? 'light' : 'dark';
    try { localStorage.setItem('magic-themer-theme', root.dataset.theme); } catch {}
    syncThemeMeta();
    if (isReduced()) return;
    btn.classList.remove('spin');
    void btn.offsetWidth;
    btn.classList.add('spin');
    const r = btn.getBoundingClientRect();
    burst(r.left + r.width / 2, r.top + r.height / 2, 6, 44);
  });
}

/* Header border + JS fallback for scroll-progress ribbon */
{
  const header = $('.site-header');
  const hasScrollTimeline = typeof CSS !== 'undefined' && CSS.supports && CSS.supports('animation-timeline', 'scroll()');
  let ticking = false;
  const update = () => {
    ticking = false;
    header.classList.toggle('is-stuck', scrollY > 8);
    if (!hasScrollTimeline && !isReduced()) {
      const max = document.documentElement.scrollHeight - innerHeight;
      const ratio = max > 0 ? Math.min(1, Math.max(0, scrollY / max)) : 0;
      header.style.setProperty('--read', ratio.toFixed(4));
    }
  };
  addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(update); } }, { passive: true });
  update();
}

/* Scroll reveal (JS stagger + clean release so hover transitions stay snappy) */
{
  const revealNow = (el, delay = 0) => {
    const show = () => {
      el.classList.add('in');
      setTimeout(() => el.classList.remove('reveal'), isReduced() ? 0 : 820);
    };
    if (delay && !isReduced()) setTimeout(show, delay);
    else show();
  };
  const io = new IntersectionObserver((entries) => {
    let stickerBatch = 0;
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      io.unobserve(e.target);
      const delay = e.target.classList.contains('sticker') ? (stickerBatch++ % 4) * 75 : 0;
      revealNow(e.target, delay);
    }
  }, { threshold: 0.14, rootMargin: '0px 0px -36px 0px' });
  $$('.reveal').forEach((el) => io.observe(el));
}

/* Mascot: hop, chat, cursor parallax & 5-tap secret twirl */
{
  const mascot = $('#mascot');
  const bubble = $('#bubble');
  const art = $('#hero-art');
  const lines = [
    'Hi! I turn icon packs into HONOR themes ✨',
    'No internet needed. I live on your phone!',
    'Try the packs in the demo below 👇',
    'Missing an icon? I\'ll suggest one!',
    'Wheee!',
    'I never overwrite your original theme.',
    'Free and open source, forever.',
  ];
  let n = 0;
  let taps = [];

  mascot.addEventListener('click', () => {
    const now = performance.now();
    taps = taps.filter((t) => now - t < 2500);
    taps.push(now);
    const party = taps.length >= 5;
    if (party) taps = [];

    bubble.textContent = party ? 'Wheee, secret spin unlocked! ✨' : lines[n++ % lines.length];
    bubble.classList.remove('again');
    void bubble.offsetWidth;
    bubble.classList.add('again');
    if (isReduced()) return;

    const cls = party ? 'twirl' : 'hop';
    mascot.classList.remove('hop', 'twirl');
    void mascot.offsetWidth;
    mascot.classList.add(cls);
    const r = mascot.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height * .45;
    if (party) confetti(cx, cy, false, 34);
    else burst(cx, cy, 10, 110);
  });

  if (finePointer && art) {
    let raf = 0;
    let mx = 0;
    let my = 0;
    const apply = () => {
      raf = 0;
      art.style.setProperty('--mx', mx.toFixed(3));
      art.style.setProperty('--my', my.toFixed(3));
    };
    art.addEventListener('pointermove', (e) => {
      if (isReduced()) return;
      const r = art.getBoundingClientRect();
      mx = Math.max(-1, Math.min(1, ((e.clientX - r.left) / r.width - .5) * 2));
      my = Math.max(-1, Math.min(1, ((e.clientY - r.top) / r.height - .5) * 2));
      if (!raf) raf = requestAnimationFrame(apply);
    });
    art.addEventListener('pointerleave', () => {
      mx = 0;
      my = 0;
      if (!raf) raf = requestAnimationFrame(apply);
    });
  }
}

/* CTA peeking mascot: tap to hop + sparkle */
{
  const peek = $('#peek');
  if (peek) {
    peek.addEventListener('click', () => {
      if (isReduced()) return;
      peek.classList.remove('wave');
      void peek.offsetWidth;
      peek.classList.add('wave');
      const r = peek.getBoundingClientRect();
      burst(r.left + r.width / 2, r.top + r.height * .35, 10, 95);
    });
  }
}

/* Sparkle trail in the hero */
if (finePointer) {
  const hero = $('.hero');
  let last = 0;
  hero.addEventListener('pointermove', (e) => {
    if (isReduced()) return;
    const now = performance.now();
    if (now - last < 48) return;
    last = now;
    const s = makeSvg('#i-spark', 'sparkle');
    s.style.left = `${e.clientX - 7}px`;
    s.style.top = `${e.clientY - 7}px`;
    s.style.fill = COLORS_LIGHT[Math.floor(Math.random() * 5)];
    document.body.append(s);
    s.animate([
      { transform: 'scale(1) rotate(0)', opacity: 1 },
      { transform: `translate(${(Math.random() - .5) * 30}px, ${20 + Math.random() * 20}px) scale(0) rotate(160deg)`, opacity: 0 },
    ], { duration: 800, easing: 'ease-out' }).onfinish = () => s.remove();
  });
}

/* Tap-jelly on hero chips */
$$('.chips li').forEach((chip) => {
  chip.addEventListener('click', () => {
    if (isReduced()) return;
    chip.classList.remove('jelly');
    void chip.offsetWidth;
    chip.classList.add('jelly');
  });
});

/* Replay stamp animation on privacy "nopes" */
$$('.nopes li').forEach((item) => {
  const restamp = () => {
    if (isReduced()) return;
    item.classList.remove('restamp');
    void item.offsetWidth;
    item.classList.add('restamp');
  };
  item.addEventListener('click', restamp);
  if (finePointer) item.addEventListener('mouseenter', restamp);
});

/* Confetti on download buttons (navigation still happens) */
$$('.confetti').forEach((btn) => btn.addEventListener('click', () => {
  const r = btn.getBoundingClientRect();
  confetti(r.left + r.width / 2, r.top + r.height / 2, Boolean(btn.closest('.cta')));
}));

/* Star rides the rope & active-step blob pop in "How it works" */
{
  const wrap = $('.steps-wrap');
  const steps = $('#steps');
  const rope = wrap ? $('.rope', wrap) : null;
  if (steps && rope) {
    let ticking = false;
    const update = () => {
      ticking = false;
      if (isReduced() || rope.offsetParent === null) return;
      const r = steps.getBoundingClientRect();
      const p = Math.min(1, Math.max(0, (innerHeight * .55 - r.top) / r.height));
      rope.style.setProperty('--p', `${(p * 100).toFixed(2)}%`);
    };
    addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(update); } }, { passive: true });
    update();
  }
  if (steps) {
    const items = $$('.step', steps);
    const activeIO = new IntersectionObserver((entries) => {
      for (const e of entries) e.target.classList.toggle('is-active', e.isIntersecting);
    }, { rootMargin: '-36% 0px -36% 0px', threshold: 0.01 });
    items.forEach((s) => activeIO.observe(s));
  }
}

/* Real HONOR Magic V5 Before & After wipe slider */
{
  const compare = $('#compare');
  const range = $('#cmp-range');
  if (compare && range) {
    const presets = $$('.cmp-btns [data-cmp]');
    let touched = false;
    let animId = 0;

    const setPos = (val) => {
      const clamped = Math.max(0, Math.min(100, val));
      compare.style.setProperty('--pos', `${clamped.toFixed(2)}%`);
      range.value = String(Math.round(clamped));
      compare.dataset.hideL = String(clamped < 22);
      compare.dataset.hideR = String(clamped > 78);
      presets.forEach((b) => {
        const target = Number(b.dataset.cmp);
        b.setAttribute('aria-pressed', String(Math.abs(clamped - target) <= 3));
      });
    };

    const glideTo = (target, ms = 520, celebrate = false) => {
      cancelAnimationFrame(animId);
      const start = parseFloat(compare.style.getPropertyValue('--pos')) || Number(range.value) || 50;
      if (isReduced() || ms <= 0) {
        setPos(target);
        return;
      }
      const t0 = performance.now();
      const step = (now) => {
        const p = Math.min(1, (now - t0) / ms);
        const ease = 1 - Math.pow(1 - p, 3);
        setPos(start + (target - start) * ease);
        if (p < 1) animId = requestAnimationFrame(step);
        else if (celebrate) {
          const r = compare.getBoundingClientRect();
          if (r.top < innerHeight && r.bottom > 0) burst(r.right - 20, r.top + r.height * .58, 10, 65);
        }
      };
      animId = requestAnimationFrame(step);
    };

    const stopIntro = () => {
      touched = true;
      cancelAnimationFrame(animId);
    };

    range.addEventListener('pointerdown', () => { stopIntro(); compare.classList.add('dragging'); });
    addEventListener('pointerup', () => compare.classList.remove('dragging'));
    addEventListener('pointercancel', () => compare.classList.remove('dragging'));
    range.addEventListener('input', () => {
      stopIntro();
      setPos(Number(range.value));
    });
    presets.forEach((b) => b.addEventListener('click', () => {
      stopIntro();
      const val = Number(b.dataset.cmp);
      glideTo(val, 480, val === 100);
    }));

    setPos(50);

    // Gentle one-time intro sweep when scrolled into view so visitors notice it's draggable
    if (!isReduced()) {
      const io = new IntersectionObserver((entries) => {
        if (!entries[0].isIntersecting || touched || isReduced()) return;
        io.disconnect();
        const t0 = performance.now();
        const sweep = (now) => {
          if (touched || isReduced()) return;
          const p = Math.min(1, (now - t0) / 1800);
          const pos = 50 + Math.sin(p * Math.PI * 2) * 34 * (1 - p * .35);
          setPos(pos);
          if (p < 1) animId = requestAnimationFrame(sweep);
          else setPos(50);
        };
        animId = requestAnimationFrame(sweep);
      }, { threshold: 0.5 });
      io.observe(compare);
    }
  }
}

/* Demo phone */
{
  const screen = $('#screen');
  const apps = $$('.app', screen);
  const buttons = $$('.pack');
  const status = $('#demo-status');
  const meter = $('#meter');
  const fill = $('#meter-fill');
  const fixBtn = $('#fill-gap');
  const gaps = { mint: 'weather', candy: 'notes', night: 'music' };
  const names = { mint: 'Mint', candy: 'Candy', night: 'Midnight' };
  let pack = 'stock';
  let gapFixed = false;
  let busy = [];
  let autoTimer = null;
  let inView = false;
  let touched = false;
  let runId = 0;

  const clearGapA11y = (app) => {
    app.classList.remove('gap');
    app.removeAttribute('tabindex');
    app.removeAttribute('role');
    app.removeAttribute('aria-label');
  };

  const markGap = (app) => {
    const label = app.querySelector('.lbl').textContent;
    app.classList.add('gap');
    app.setAttribute('tabindex', '0');
    app.setAttribute('role', 'button');
    app.setAttribute('aria-label', `Pick a suggested icon for ${label}`);
  };

  const setStyle = (app, style, delay) => new Promise((resolve) => {
    const t1 = setTimeout(() => {
      if (isReduced()) {
        app.dataset.style = style;
        return resolve();
      }
      app.classList.remove('land');
      app.classList.add('flip');
      const t2 = setTimeout(() => {
        app.dataset.style = style;
        app.classList.remove('flip');
        app.classList.add('land');
        const ico = app.querySelector('.ico');
        ico.addEventListener('animationend', () => app.classList.remove('land'), { once: true });
        if (style !== 'stock') {
          const r = ico.getBoundingClientRect();
          if (r.top < innerHeight && r.bottom > 0) burst(r.left + r.width / 2, r.top + r.height / 2, 5, 40);
        }
        resolve();
      }, 190);
      busy.push(t2);
    }, delay);
    busy.push(t1);
  });

  const report = () => {
    const total = apps.length;
    meter.classList.remove('full');
    if (pack === 'stock') {
      fill.style.width = '0%';
      status.textContent = 'Stock icons. A bit of a mess, right?';
      fixBtn.hidden = true;
      return;
    }
    const themed = gapFixed ? total : total - 1;
    fill.style.width = `${(themed / total) * 100}%`;
    const gapName = apps.find((a) => a.dataset.app === gaps[pack]).querySelector('.lbl').textContent;
    if (gapFixed) {
      status.textContent = `All ${total} of ${total} apps themed with ${names[pack]}. Ready to save! 🎉`;
      fixBtn.hidden = true;
      if (!isReduced()) {
        void meter.offsetWidth;
        meter.classList.add('full');
        const r = meter.getBoundingClientRect();
        if (r.top < innerHeight && r.bottom > 0) burst(r.right - 8, r.top + r.height / 2, 7, 50);
      }
    } else {
      status.textContent = `${themed} of ${total} apps themed. ${gapName} needs an icon — tap it!`;
      fixBtn.hidden = false;
    }
  };

  const choose = async (next) => {
    const myRun = ++runId;
    busy.forEach(clearTimeout);
    busy = [];
    pack = next;
    gapFixed = false;
    screen.dataset.pack = next;
    buttons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.pack === next)));
    fixBtn.hidden = true;
    meter.classList.remove('full');
    apps.forEach((a) => {
      clearGapA11y(a);
      a.classList.remove('flip', 'land');
    });
    status.textContent = next === 'stock' ? 'Back to stock…' : `Applying ${names[next]}…`;
    await Promise.all(apps.map((a, i) => {
      const uncovered = next !== 'stock' && a.dataset.app === gaps[next];
      return setStyle(a, uncovered ? 'stock' : next, i * 65);
    }));
    if (myRun !== runId) return;
    if (next !== 'stock') {
      const gapApp = apps.find((a) => a.dataset.app === gaps[next]);
      if (gapApp) markGap(gapApp);
    }
    report();
  };

  const fixGap = async () => {
    if (pack === 'stock' || gapFixed) return;
    const myRun = runId;
    const app = apps.find((a) => a.dataset.app === gaps[pack]);
    if (!app) return;
    clearGapA11y(app);
    gapFixed = true;
    fixBtn.hidden = true;
    await setStyle(app, pack, 0);
    if (myRun === runId) report();
  };

  function stopAuto() {
    if (!touched) {
      touched = true;
      status.setAttribute('aria-live', 'polite');
    }
    clearTimeout(autoTimer);
    autoTimer = null;
  }

  buttons.forEach((b) => b.addEventListener('click', () => {
    stopAuto();
    if (b.dataset.pack === pack) return;
    choose(b.dataset.pack);
  }));
  fixBtn.addEventListener('click', () => { stopAuto(); fixGap(); });
  screen.addEventListener('click', (e) => {
    const gapApp = e.target.closest('.app.gap');
    if (!gapApp) return;
    stopAuto();
    fixGap();
  });
  screen.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    const gapApp = e.target.closest('.app.gap');
    if (!gapApp) return;
    e.preventDefault();
    stopAuto();
    fixGap();
  });

  // Gentle autoplay while the phone is visible; waits for each animation to finish before scheduling next.
  const order = ['mint', 'fix', 'candy', 'fix', 'night', 'fix', 'stock'];
  let step = 0;
  const scheduleNext = (delay) => {
    clearTimeout(autoTimer);
    if (touched || !inView || isReduced()) return;
    autoTimer = setTimeout(async () => {
      if (touched || !inView || isReduced()) return;
      const action = order[step++ % order.length];
      if (action === 'fix') {
        await fixGap();
        scheduleNext(2100);
      } else {
        await choose(action);
        scheduleNext(action === 'stock' ? 1900 : 2300);
      }
    }, delay);
  };

  new IntersectionObserver((entries) => {
    inView = entries[0].isIntersecting;
    if (inView && !touched && !isReduced()) scheduleNext(step === 0 ? 700 : 1400);
    else if (!inView) { clearTimeout(autoTimer); autoTimer = null; }
  }, { threshold: 0.45 }).observe(screen);
}

/* Step 2 interactive 3-screen real-app stepper */
{
  const stepper = $('#stepper');
  if (stepper) {
    const pills = $$('.step-pills button[data-go]', stepper);
    const hotspots = $$('.hotspot', stepper);
    let idx = 0;
    let touched = false;
    let timer = null;

    const setStep = (next, burstEl = null) => {
      idx = ((next % 3) + 3) % 3;
      stepper.dataset.step = String(idx);
      pills.forEach((p) => p.setAttribute('aria-pressed', String(Number(p.dataset.go) === idx)));
      hotspots.forEach((h) => {
        if (h.tagName === 'BUTTON') h.disabled = Number(h.dataset.on) !== idx;
      });
      if (burstEl && !isReduced()) {
        const r = burstEl.getBoundingClientRect();
        burst(r.left + r.width / 2, r.top + r.height / 2, idx === 2 ? 12 : 7, idx === 2 ? 75 : 50);
      }
    };

    const stopAuto = () => { touched = true; clearInterval(timer); timer = null; };

    setStep(0);
    hotspots.forEach((h) => {
      if (h.tagName !== 'BUTTON') return;
      h.addEventListener('click', (e) => {
        e.stopPropagation();
        stopAuto();
        setStep(Number(h.dataset.on) + 1, h);
      });
    });
    pills.forEach((p) => p.addEventListener('click', () => {
      stopAuto();
      setStep(Number(p.dataset.go));
    }));
    stepper.addEventListener('click', stopAuto);

    new IntersectionObserver((entries) => {
      if (touched || isReduced()) return;
      if (entries[0].isIntersecting && !timer) {
        timer = setInterval(() => {
          if (touched || isReduced()) { clearInterval(timer); timer = null; return; }
          setStep(idx + 1);
        }, 2800);
      } else if (!entries[0].isIntersecting) {
        clearInterval(timer);
        timer = null;
      }
    }, { threshold: 0.45 }).observe(stepper);
  }
}

/* Screenshots: "Unfold & go dark" foldable morph */
{
  const fold = $('#fold');
  const toggle = $('#fold-toggle');
  if (fold && toggle) {
    const label = $('span', toggle);
    let open = false;
    let touched = false;
    let timer = null;

    const setOpen = (next) => {
      open = next;
      fold.dataset.open = String(open);
      toggle.setAttribute('aria-pressed', String(open));
      if (label) label.textContent = open ? 'Fold & go light' : 'Unfold & go dark';
    };

    const stopAuto = () => { touched = true; clearInterval(timer); timer = null; };

    toggle.addEventListener('click', () => {
      stopAuto();
      setOpen(!open);
      const r = toggle.getBoundingClientRect();
      burst(r.left + r.width / 2, r.top + r.height / 2, 8, 55);
    });
    fold.addEventListener('click', stopAuto);

    new IntersectionObserver((entries) => {
      if (touched || isReduced()) return;
      if (entries[0].isIntersecting && !timer) {
        timer = setInterval(() => {
          if (touched || isReduced()) { clearInterval(timer); timer = null; return; }
          setOpen(!open);
        }, 3600);
      } else if (!entries[0].isIntersecting) {
        clearInterval(timer);
        timer = null;
      }
    }, { threshold: 0.45 }).observe(fold);
  }
}

/* Tap-to-zoom native <dialog> lightbox on every real screenshot */
{
  const dialog = $('#lightbox');
  const lbImg = $('#lb-img');
  const lbCap = $('#lb-cap');
  const prevBtn = $('#lb-prev');
  const nextBtn = $('#lb-next');
  const closeBtn = $('#lb-close');

  if (dialog && lbImg && lbCap) {
    const zoomImgs = $$('img[data-zoom]');
    const items = [];
    const indexBySrc = new Map();

    const captionFor = (img) => {
      const gCap = img.closest('.g-item')?.querySelector('figcaption')?.textContent?.trim();
      return gCap ? `${gCap} — ${img.alt}` : img.alt;
    };

    for (const img of zoomImgs) {
      const key = img.getAttribute('src');
      if (!indexBySrc.has(key)) {
        indexBySrc.set(key, items.length);
        items.push({ src: key, alt: img.alt, cap: captionFor(img) });
      }
    }

    let current = 0;
    let opener = null;

    const show = (i) => {
      current = ((i % items.length) + items.length) % items.length;
      const item = items[current];
      lbImg.src = item.src;
      lbImg.alt = item.alt;
      lbCap.textContent = item.cap;
    };

    const openFor = (img, trigger) => {
      const idx = indexBySrc.get(img.getAttribute('src'));
      if (idx == null) return;
      opener = trigger || img;
      show(idx);
      if (!dialog.open) dialog.showModal();
      closeBtn.focus();
    };

    zoomImgs.forEach((img) => {
      img.addEventListener('click', () => openFor(img, img));
    });

    // Inject accessible magnify buttons onto every real phone/foldable bezel
    const hosts = [...$$('.device'), $('#fold')].filter(Boolean);
    for (const host of hosts) {
      if (!host.querySelector('img[data-zoom]')) continue;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'zoom-btn';
      btn.setAttribute('aria-label', 'Zoom screenshot');
      btn.append(makeSvg('#i-zoom', 'i'));
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        let img = host.querySelector('img[data-zoom]');
        if (host.id === 'fold') {
          img = host.dataset.open === 'true' ? $('.fold-open', host) : $('.fold-closed', host);
        } else if (host.classList.contains('compare-device')) {
          const range = $('#cmp-range');
          img = (range && Number(range.value) >= 50) ? $('.cmp-after', host) : $('.cmp-before', host);
        } else if (host.closest('#stepper')) {
          const step = host.closest('#stepper').dataset.step || '0';
          img = $(`.frame[data-frame="${step}"]`, host) || img;
        }
        if (img) openFor(img, btn);
      });
      host.append(btn);
    }

    prevBtn.addEventListener('click', () => show(current - 1));
    nextBtn.addEventListener('click', () => show(current + 1));
    closeBtn.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog || e.target.tagName === 'FIGURE') dialog.close();
    });
    dialog.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowLeft') { e.preventDefault(); show(current - 1); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); show(current + 1); }
    });
    dialog.addEventListener('close', () => {
      if (opener && opener.matches && opener.matches('button, a, [tabindex]')) opener.focus();
    });
  }
}
