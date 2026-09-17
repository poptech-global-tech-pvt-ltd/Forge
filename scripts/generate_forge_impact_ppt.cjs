'use strict';
const pptxgen = require('pptxgenjs');

// ─── Palette ──────────────────────────────────────────────────────────────────
const C = {
  navy:    '0D1B2A',   // dark background (title/closing slides)
  navyMid: '1A2E45',   // slightly lighter dark
  blue:    '1565C0',   // primary accent blue
  blueSoft:'EFF6FF',   // very light blue slide bg
  orange:  'E85D04',   // forge orange
  white:   'FFFFFF',
  offwhite:'F0F4F8',
  muted:   '607D8B',
  cardBg:  'FFFFFF',
  red:     'C62828',
  redSoft: 'FFEBEE',
  greenSoft:'E8F5E9',
  green:   '2E7D32',
  text:    '0D1B2A',
  bodyText:'37474F',
};

const FONT_TITLE  = 'Georgia';
const FONT_BODY   = 'Calibri';

const W = 10;  // slide width in inches
const H = 5.625;

const pres = new pptxgen();
pres.layout  = 'LAYOUT_16x9';
pres.author  = 'POP Club QA Engineering';
pres.title   = 'Forge — Built In-House, Built to Scale';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeShadow() {
  return { type: 'outer', color: '000000', blur: 8, offset: 2, angle: 135, opacity: 0.10 };
}

// Thin left-accent bar for cards
function accentBar(slide, x, y, h, color = C.orange) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w: 0.06, h,
    fill: { color },
    line: { color, width: 0 },
  });
}

// White card with shadow
function card(slide, x, y, w, h, fillColor) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w, h,
    fill: { color: fillColor || C.cardBg },
    line: { color: 'D8E4F0', width: 0.5 },
    shadow: makeShadow(),
  });
}

// Numbered circle (for steps)
function stepCircle(slide, x, y, num, bgColor) {
  slide.addShape(pres.shapes.OVAL, {
    x, y, w: 0.52, h: 0.52,
    fill: { color: bgColor || C.blue },
    line: { color: bgColor || C.blue, width: 0 },
  });
  slide.addText(String(num), {
    x, y: y + 0.01, w: 0.52, h: 0.52,
    fontSize: 15, bold: true, color: C.white,
    align: 'center', valign: 'middle',
    fontFace: FONT_BODY, margin: 0,
  });
}

// Dark slide background
function darkSlide(slide) {
  slide.background = { color: C.navy };
  // subtle diagonal lines texture via a few thin shapes
  for (let i = 0; i < 3; i++) {
    slide.addShape(pres.shapes.RECTANGLE, {
      x: W - 2.5 + i * 0.8, y: 0, w: 0.4, h: H,
      fill: { color: C.navyMid, transparency: 85 },
      line: { color: C.navyMid, width: 0 },
    });
  }
}

// Light slide background
function lightSlide(slide) {
  slide.background = { color: C.blueSoft };
}

// Orange top bar
function topBar(slide, color) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: W, h: 0.07,
    fill: { color: color || C.orange },
    line: { color: color || C.orange, width: 0 },
  });
}

// Bottom bar
function bottomBar(slide, color) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: H - 0.06, w: W, h: 0.06,
    fill: { color: color || C.blue },
    line: { color: color || C.blue, width: 0 },
  });
}

// Slide label (small bottom-right corner tag)
function slideTag(slide, num, dark) {
  slide.addText(`${num} / 13`, {
    x: W - 1, y: H - 0.38, w: 0.8, h: 0.25,
    fontSize: 8, color: dark ? '4A6080' : C.muted,
    align: 'right', fontFace: FONT_BODY,
  });
}

// Speaker note helper
function note(slide, text) {
  slide.addNotes(text);
}

// ─── SLIDE 1 — Title ─────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  darkSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  // Big FORGE wordmark
  s.addText('FORGE', {
    x: 0.7, y: 1.1, w: 5, h: 1.6,
    fontSize: 88, bold: true, color: C.orange,
    fontFace: FONT_TITLE, margin: 0,
    charSpacing: 8,
  });

  // Tagline
  s.addText('We Built Our Own Testing Framework.', {
    x: 0.7, y: 2.75, w: 8.5, h: 0.55,
    fontSize: 20, bold: false, color: C.white,
    fontFace: FONT_TITLE, italic: true, margin: 0,
  });
  s.addText("Here's Why That Was the Right Call.", {
    x: 0.7, y: 3.22, w: 8.5, h: 0.55,
    fontSize: 20, bold: false, color: C.white,
    fontFace: FONT_TITLE, italic: true, margin: 0,
  });

  // Right side decorative vertical line
  s.addShape(pres.shapes.LINE, {
    x: 5.8, y: 0.8, w: 0, h: 4.2,
    line: { color: C.orange, width: 1.5, transparency: 60 },
  });

  // POP Club tag
  s.addText('POP CLUB  ·  QA ENGINEERING  ·  2026', {
    x: 0.7, y: H - 0.6, w: 7, h: 0.3,
    fontSize: 9, color: '8899AA', fontFace: FONT_BODY,
    charSpacing: 3, margin: 0,
  });

  slideTag(s, 1, true);
  note(s, "I want to tell you a story about a problem that was quietly slowing our team down — and what we decided to do about it instead of buying a solution. Forge is the testing framework we built in-house at POP Club. And the reason I'm talking about it today isn't just the technology — it's what it means for how we work, and where it could go next.");
}

// ─── SLIDE 2 — The Problem ───────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.red);
  bottomBar(s, C.red);

  s.addText('Testing Was the Bottleneck', {
    x: 0.5, y: 0.25, w: 9, h: 0.65,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });
  s.addText('Three problems that were invisible — until they weren\'t', {
    x: 0.5, y: 0.88, w: 9, h: 0.35,
    fontSize: 13, italic: true, color: C.muted,
    fontFace: FONT_BODY, margin: 0,
  });

  const problems = [
    {
      num: '01',
      headline: 'Zero automated testing',
      body: 'Everything was manual. No regression suite, no repeatable coverage — every release carried hidden risk.',
      color: C.red,
    },
    {
      num: '02',
      headline: 'Compose apps have no element IDs',
      body: 'Standard tools like Espresso and UIAutomator were blind to our Jetpack Compose UI. No reliable hooks.',
      color: C.red,
    },
    {
      num: '03',
      headline: 'QA depended on developers',
      body: 'Any automation required Java expertise. QA couldn\'t write or own tests independently — always a dependency.',
      color: C.red,
    },
  ];

  problems.forEach((p, i) => {
    const x = 0.3 + i * 3.23;
    const y = 1.4;
    const cw = 3.0;
    const ch = 3.7;

    card(s, x, y, cw, ch, C.redSoft);
    accentBar(s, x, y, ch, p.color);

    s.addText(p.num, {
      x: x + 0.2, y: y + 0.22, w: 0.5, h: 0.4,
      fontSize: 22, bold: true, color: p.color,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(p.headline, {
      x: x + 0.18, y: y + 0.65, w: cw - 0.28, h: 1.0,
      fontSize: 15, bold: true, color: C.text,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(p.body, {
      x: x + 0.18, y: y + 1.75, w: cw - 0.28, h: 1.7,
      fontSize: 12.5, color: C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  slideTag(s, 2, false);
  note(s, "We had no automated testing at all. Every release was validated manually — which means every release was a gamble. The second problem was specific to our tech stack: Jetpack Compose doesn't generate resource IDs, so every standard tool we looked at simply couldn't see our UI. And the third problem was structural — even if a tool had existed, QA couldn't write tests without a developer sitting next to them. That dependency was the root cause of everything. You can't scale quality when QA can't own their own tools.");
}

// ─── SLIDE 3 — The Decision ──────────────────────────────────────────────────
{
  const s = pres.addSlide();
  darkSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  // Big quote
  s.addText('"Instead of fitting our product\nto a tool —', {
    x: 0.8, y: 0.6, w: 8.5, h: 1.5,
    fontSize: 28, bold: false, color: C.white,
    fontFace: FONT_TITLE, italic: true, margin: 0,
  });
  s.addText('we built a tool that fits our product."', {
    x: 0.8, y: 2.0, w: 8.5, h: 1.1,
    fontSize: 28, bold: true, color: C.orange,
    fontFace: FONT_TITLE, italic: true, margin: 0,
  });

  // 2 pills
  const pills = ['1 Engineer', 'YAML-Driven  ·  AI-Native'];
  pills.forEach((txt, i) => {
    const pw = i === 1 ? 2.5 : 1.3;
    const px = 0.8 + i * 2.8;
    s.addShape(pres.shapes.RECTANGLE, {
      x: px, y: 3.35, w: pw, h: 0.46,
      fill: { color: C.orange },
      line: { color: C.orange, width: 0 },
    });
    s.addText(txt, {
      x: px, y: 3.35, w: pw, h: 0.46,
      fontSize: 12, bold: true, color: C.white,
      fontFace: FONT_BODY, align: 'center', valign: 'middle', margin: 0,
    });
  });

  s.addText('The decision wasn\'t ego. It was math.', {
    x: 0.8, y: 4.1, w: 8, h: 0.4,
    fontSize: 13, italic: true, color: '8899AA',
    fontFace: FONT_BODY, margin: 0,
  });

  slideTag(s, 3, true);
  note(s, "The decision to build in-house wasn't ego. It was math. We had no automated testing at all — and every tool we looked at required either Java expertise or didn't understand Jetpack Compose testTags. We evaluated Espresso, Appium with UIAutomator, Maestro, even Detox. None of them were designed for a Compose-first codebase where QA, not just developers, needed to own the tests. So we built something that was. One engineer. One clear constraint: if a QA engineer can't write a test without asking a developer, the framework has failed.");
}

// ─── SLIDE 4 — How I Built It ────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.blue);
  bottomBar(s, C.blue);

  s.addText('How Forge Was Born', {
    x: 0.5, y: 0.2, w: 9, h: 0.6,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });
  s.addText('A personal story of building something that didn\'t exist', {
    x: 0.5, y: 0.78, w: 9, h: 0.3,
    fontSize: 12, italic: true, color: C.muted,
    fontFace: FONT_BODY, margin: 0,
  });

  // Timeline line
  s.addShape(pres.shapes.LINE, {
    x: 0.85, y: 2.62, w: 8.3, h: 0,
    line: { color: C.blue, width: 2 },
  });

  const steps = [
    { week: 'Phase 1', title: 'The Locator\nProblem', body: 'Mapped Compose testTag as the only reliable locator. Built the accessibilityId strategy.', x: 0.5 },
    { week: 'Phase 2', title: 'YAML Engine\n& Registry', body: 'Built the YAML test DSL, element registry, and execution pipeline. First test ran.', x: 2.85 },
    { week: 'Phase 3', title: 'Self-Healing\n& AI', body: 'Added Jaccard token matching for broken elements. Wired in Claude API for failure triage.', x: 5.2 },
    { week: 'Today', title: '16 MCP Tools\n& Forge UI', body: 'Live device mirror, AI chat, recorder, YAML editor. Claude writes and runs tests.', x: 7.55 },
  ];

  steps.forEach((st, i) => {
    const cx = st.x + 0.63;
    // Circle on timeline
    s.addShape(pres.shapes.OVAL, {
      x: cx - 0.18, y: 2.44, w: 0.36, h: 0.36,
      fill: { color: i === 3 ? C.orange : C.blue },
      line: { color: C.white, width: 2 },
    });

    // Card
    const cardH = 1.85;
    const cardY = i % 2 === 0 ? 0.95 : 3.05;
    card(s, st.x, cardY, 2.2, cardH);
    accentBar(s, st.x, cardY, cardH, i === 3 ? C.orange : C.blue);

    // Week label
    s.addText(st.week, {
      x: st.x + 0.14, y: cardY + 0.1, w: 2.0, h: 0.28,
      fontSize: 9.5, bold: true, color: i === 3 ? C.orange : C.blue,
      fontFace: FONT_BODY, margin: 0,
    });
    s.addText(st.title, {
      x: st.x + 0.14, y: cardY + 0.36, w: 2.0, h: 0.65,
      fontSize: 13.5, bold: true, color: C.text,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(st.body, {
      x: st.x + 0.14, y: cardY + 0.98, w: 2.0, h: 0.82,
      fontSize: 11, color: C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });

    // Connector line from card to timeline dot
    if (i % 2 === 0) {
      s.addShape(pres.shapes.LINE, {
        x: cx, y: cardY + cardH, w: 0, h: 2.44 - (cardY + cardH),
        line: { color: C.blue, width: 1, dashType: 'dash', transparency: 40 },
      });
    } else {
      s.addShape(pres.shapes.LINE, {
        x: cx, y: 2.62 + 0.18, w: 0, h: cardY - 2.62 - 0.18,
        line: { color: C.blue, width: 1, dashType: 'dash', transparency: 40 },
      });
    }
  });

  slideTag(s, 4, false);
  note(s, "I started by solving the hardest problem first: how do you reliably find elements in a Compose app that has no resource IDs? The answer was testTag — a Compose modifier that sets the accessibility content-description. Once you build your entire locator strategy around that, everything else follows. The second phase was proving the concept — building the YAML parser, the element registry, and getting the first test to actually run on a real device. The self-healing engine came next. That was the moment I knew Forge could survive in production — not just work in a demo.");
}

// ─── SLIDE 5 — How It Works ──────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.blue);
  bottomBar(s, C.orange);

  s.addText('Plain English → Working Test', {
    x: 0.5, y: 0.18, w: 9, h: 0.6,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });
  s.addText('No Java. No driver setup. No element selectors. Just steps.', {
    x: 0.5, y: 0.76, w: 9, h: 0.32,
    fontSize: 12.5, italic: true, color: C.muted,
    fontFace: FONT_BODY, margin: 0,
  });

  // 3 step cards left side
  const stepItems = [
    { n: 1, title: 'Write steps in YAML', body: 'Describe what to do: tap this, type that, verify this text. No code required.' },
    { n: 2, title: 'Forge resolves & runs', body: 'Finds elements, retries on failure, heals broken steps automatically.' },
    { n: 3, title: 'Results stream live', body: 'Step-level pass/fail in real time. Failures auto-reported to TestSigma.' },
  ];

  stepItems.forEach((st, i) => {
    const y = 1.2 + i * 1.35;
    card(s, 0.3, y, 4.5, 1.18);
    stepCircle(s, 0.46, y + 0.33, st.n, C.blue);
    s.addText(st.title, {
      x: 1.15, y: y + 0.13, w: 3.45, h: 0.42,
      fontSize: 14.5, bold: true, color: C.text,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(st.body, {
      x: 1.15, y: y + 0.55, w: 3.45, h: 0.55,
      fontSize: 11.5, color: C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  // Arrow between steps
  for (let i = 0; i < 2; i++) {
    const ay = 2.38 + i * 1.35;
    s.addText('▼', {
      x: 1.9, y: ay, w: 0.5, h: 0.28,
      fontSize: 11, color: C.blue, align: 'center', margin: 0, fontFace: FONT_BODY,
    });
  }

  // Code card right side
  const codeY = 1.15;
  s.addShape(pres.shapes.RECTANGLE, {
    x: 5.3, y: codeY, w: 4.4, h: 3.9,
    fill: { color: '1A2535' },
    line: { color: '1A2535', width: 0 },
    shadow: makeShadow(),
  });
  // Code window dots
  ['C62828', 'E6A817', '2E7D32'].forEach((col, i) => {
    s.addShape(pres.shapes.OVAL, {
      x: 5.5 + i * 0.22, y: codeY + 0.14, w: 0.12, h: 0.12,
      fill: { color: col }, line: { color: col, width: 0 },
    });
  });
  s.addText('test.yaml', {
    x: 5.3, y: codeY + 0.08, w: 4.4, h: 0.3,
    fontSize: 9, color: '8899AA', align: 'center',
    fontFace: FONT_BODY, margin: 0,
  });

  const codeLines = [
    { t: '- action: ', c: '8899AA' },
    { t: '    tap', c: '14B8A6' },
    { t: '  element: shop_search_button', c: 'E8EDF2' },
    { t: '', c: 'E8EDF2' },
    { t: '- action: enterText', c: '14B8A6' },
    { t: '  element: shop_search_input', c: 'E8EDF2' },
    { t: '  value: "t-shirt"', c: 'FCD34D' },
    { t: '', c: 'E8EDF2' },
    { t: '- action: assertText', c: '14B8A6' },
    { t: '  element: product_price', c: 'E8EDF2' },
    { t: '  value: "Rs.499"', c: 'FCD34D' },
    { t: '', c: 'E8EDF2' },
    { t: '✓ Test passed  (3 steps, 4.2s)', c: '4ADE80' },
  ];

  codeLines.forEach((line, i) => {
    s.addText(line.t, {
      x: 5.5, y: codeY + 0.52 + i * 0.245, w: 3.9, h: 0.26,
      fontSize: 10, color: line.c, fontFace: 'Courier New',
      margin: 0,
    });
  });

  slideTag(s, 5, false);
  note(s, "A QA engineer with zero Java knowledge can write this. They describe what to do in plain YAML steps — tap this, type that, verify this text. Forge handles all the driver interaction, waiting, retries, and reporting. The engineer just describes the scenario. What you're seeing on the right is a real Forge test. Three steps. It runs in about four seconds. And the QA engineer who wrote it had never written a line of Java. That's the point.");
}

// ─── SLIDE 6 — Forge in Action (Screenshot) ─────────────────────────────────
{
  const s = pres.addSlide();
  s.background = { color: '0D1B2A' };
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  s.addText('Forge in Action', {
    x: 0.5, y: 0.12, w: 6, h: 0.5,
    fontSize: 22, bold: true, color: C.white,
    fontFace: FONT_TITLE, margin: 0,
  });

  // Full Forge UI screenshot
  s.addImage({
    path: '/tmp/forge_screenshots/forge_ui_running.png',
    x: 0.18, y: 0.72, w: 9.64, h: 4.72,
  });

  // Callout labels
  const callouts = [
    { text: 'Live Device\nMirror', x: 0.18, y: 0.72, w: 1.1, h: 0.65, anchor: 'left' },
    { text: 'YAML Steps\n& Test Runner', x: 3.5, y: 0.72, w: 1.5, h: 0.65, anchor: 'center' },
    { text: 'Live Log\n& Results', x: 8.5, y: 0.72, w: 1.3, h: 0.65, anchor: 'right' },
  ];

  callouts.forEach(c => {
    s.addShape(pres.shapes.RECTANGLE, {
      x: c.x, y: c.y, w: c.w, h: c.h,
      fill: { color: C.orange, transparency: 15 },
      line: { color: C.orange, width: 1 },
    });
    s.addText(c.text, {
      x: c.x, y: c.y, w: c.w, h: c.h,
      fontSize: 8, bold: true, color: C.white,
      fontFace: FONT_BODY, align: 'center', valign: 'middle', margin: 2,
    });
  });

  slideTag(s, 6, true);
  note(s, "This is what Forge actually looks like running a real test on a real device. On the left, you have the live mirror of the physical device — that's our POP app running in real time. In the middle, every step of the test is listed and lights up green as it passes. On the right, the log streams every action, every element resolution, every captured value. You can see the test finding products, capturing prices, and asserting them in real time. This isn't a simulation — this is a 59-step end-to-end checkout test running live.");
}

// ─── SLIDE 7 — Self-Healing ──────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.orange);

  s.addText('Tests That Fix Themselves', {
    x: 0.5, y: 0.18, w: 9, h: 0.6,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });

  // BEFORE card
  const bx = 0.3, by = 1.05, bw = 4.2, bh = 3.8;
  card(s, bx, by, bw, bh, C.redSoft);
  accentBar(s, bx, by, bh, C.red);
  s.addText('BEFORE', {
    x: bx + 0.18, y: by + 0.18, w: bw - 0.28, h: 0.35,
    fontSize: 10, bold: true, color: C.red, fontFace: FONT_BODY,
    charSpacing: 3, margin: 0,
  });
  s.addText('Developer renames a\ncomposable', {
    x: bx + 0.18, y: by + 0.55, w: bw - 0.28, h: 0.7,
    fontSize: 16, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });

  const beforeSteps = [
    '20 tests fail overnight',
    'QA engineer raises a ticket',
    'Developer investigates, finds the rename',
    'Manually updates every test YAML',
    '2 days lost. Every. Sprint.',
  ];
  beforeSteps.forEach((st, i) => {
    s.addText(`✕  ${st}`, {
      x: bx + 0.18, y: by + 1.35 + i * 0.48, w: bw - 0.28, h: 0.42,
      fontSize: 12.5, color: C.red,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  // Arrow
  s.addText('→', {
    x: 4.7, y: 2.7, w: 0.6, h: 0.5,
    fontSize: 28, color: C.orange, align: 'center',
    fontFace: FONT_BODY, margin: 0,
  });

  // AFTER card
  const ax = 5.4, ay = 1.05, aw = 4.3, ah = 3.8;
  card(s, ax, ay, aw, ah, C.greenSoft);
  accentBar(s, ax, ay, ah, C.green);
  s.addText('WITH FORGE', {
    x: ax + 0.18, y: ay + 0.18, w: aw - 0.28, h: 0.35,
    fontSize: 10, bold: true, color: C.green, fontFace: FONT_BODY,
    charSpacing: 3, margin: 0,
  });
  s.addText('Developer renames a\ncomposable', {
    x: ax + 0.18, y: ay + 0.55, w: aw - 0.28, h: 0.7,
    fontSize: 16, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });

  const afterSteps = [
    'Forge detects element not found',
    'Scans live screen for closest match',
    'Jaccard token similarity: 87% match',
    'Patches the step automatically',
    '✓ Test passes. Zero human effort.',
  ];
  afterSteps.forEach((st, i) => {
    const isLast = i === 4;
    s.addText(`${isLast ? '' : '✓  '}${st}`, {
      x: ax + 0.18, y: ay + 1.35 + i * 0.48, w: aw - 0.28, h: 0.42,
      fontSize: 12.5, bold: isLast, color: isLast ? C.green : C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  slideTag(s, 7, false);
  note(s, "This one feature alone was worth building Forge. When a developer renames a composable from cart_checkout_btn to cart_checkout_button, Forge's self-healing engine looks at what's actually on screen, tokenises both the old name and every element on the live screen, and finds the closest match using a similarity algorithm. If it's confident enough — 87% match or better — it patches the test automatically and the test passes. No human ever knows it happened. That two-day-per-sprint maintenance cost just disappears.");
}

// ─── SLIDE 7 — AI + Forge ────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  darkSlide(s);
  topBar(s, C.blue);
  bottomBar(s, C.orange);

  s.addText('Claude Writes the Tests.\nForge Runs Them.', {
    x: 0.6, y: 0.2, w: 8.8, h: 1.3,
    fontSize: 34, bold: true, color: C.white,
    fontFace: FONT_TITLE, margin: 0,
  });

  // Claude box
  const claudeBox = (x, y, w, h, title, items, accent) => {
    s.addShape(pres.shapes.RECTANGLE, {
      x, y, w, h,
      fill: { color: C.navyMid },
      line: { color: accent, width: 2 },
      shadow: makeShadow(),
    });
    s.addShape(pres.shapes.RECTANGLE, {
      x, y, w, h: 0.42,
      fill: { color: accent },
      line: { color: accent, width: 0 },
    });
    s.addText(title, {
      x: x + 0.18, y: y + 0.05, w: w - 0.28, h: 0.34,
      fontSize: 12, bold: true, color: C.white,
      fontFace: FONT_BODY, margin: 0,
    });
    items.forEach((item, i) => {
      s.addText(`◆  ${item}`, {
        x: x + 0.18, y: y + 0.56 + i * 0.44, w: w - 0.28, h: 0.38,
        fontSize: 11.5, color: 'B8D4F0', fontFace: FONT_BODY, margin: 0,
      });
    });
  };

  claudeBox(0.4, 1.65, 3.8, 3.2,
    'Claude  ·  via Forge MCP',
    [
      'Takes a screenshot',
      'Scans the live UI hierarchy',
      'Generates YAML test steps',
      'Validates element keys',
      'Heals failing steps',
    ],
    C.orange
  );

  // Arrow
  s.addShape(pres.shapes.LINE, {
    x: 4.35, y: 3.25, w: 1.3, h: 0,
    line: { color: C.white, width: 2 },
  });
  s.addText('▶', {
    x: 5.2, y: 3.1, w: 0.4, h: 0.4,
    fontSize: 14, color: C.white, align: 'center',
    fontFace: FONT_BODY, margin: 0,
  });

  claudeBox(5.8, 1.65, 3.8, 3.2,
    'Forge  ·  Test Engine',
    [
      'Resolves elements',
      'Executes on device',
      'Streams step results',
      'Auto-heals on failure',
      'Reports to TestSigma',
    ],
    C.blue
  );

  // Caption
  s.addText('16 MCP tools. One conversation. Zero manual effort.', {
    x: 0.6, y: H - 0.52, w: 9, h: 0.3,
    fontSize: 11, italic: true, color: '8899AA',
    fontFace: FONT_BODY, margin: 0,
  });

  slideTag(s, 8, true);
  note(s, "We connected Forge to Claude Code via the Model Context Protocol. Now Claude can literally see the phone screen, understand what elements are on it, write a test, validate every element key, run it, and fix any failures — all in one conversation. I ask Claude to take a screenshot. It does. I ask it to write a test for the checkout flow. It scans the screen, maps every element to its tier, and produces valid YAML. I ask it to run the test. It does, streams results, and if something fails, heals the broken step automatically. This is what AI-native testing actually looks like.");
}

// ─── SLIDE 8 — Impact ────────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.blue);
  bottomBar(s, C.blue);

  s.addText('What Changed', {
    x: 0.5, y: 0.15, w: 9, h: 0.6,
    fontSize: 32, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });
  s.addText('From a dependency on developers to a QA team that owns its own coverage', {
    x: 0.5, y: 0.73, w: 9, h: 0.3,
    fontSize: 12, italic: true, color: C.muted,
    fontFace: FONT_BODY, margin: 0,
  });

  const stats = [
    { num: '46+', label: 'YAML Actions', sub: 'Full test vocabulary.\nZero Java required.' },
    { num: '4-Tier', label: 'Element System', sub: 'Every Compose element\nclassified & tracked.' },
    { num: 'Self-\nHealing', label: 'Engine Built-in', sub: 'Broken tests recover\nautomatically.' },
    { num: '3 Layers', label: 'Android · Web · API', sub: 'One framework covers\nevery test type.' },
  ];

  const cols = 2, cw = 4.4, ch = 1.9, gx = 0.3, gy = 1.2, gap = 0.2;
  stats.forEach((st, i) => {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const x = gx + col * (cw + gap);
    const y = gy + row * (ch + gap);

    card(s, x, y, cw, ch);
    accentBar(s, x, y, ch, C.blue);

    s.addText(st.num, {
      x: x + 0.2, y: y + 0.18, w: 1.5, h: 0.85,
      fontSize: 30, bold: true, color: C.blue,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(st.label, {
      x: x + 1.55, y: y + 0.22, w: cw - 1.65, h: 0.42,
      fontSize: 13, bold: true, color: C.text,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(st.sub, {
      x: x + 1.55, y: y + 0.64, w: cw - 1.65, h: 0.8,
      fontSize: 11, color: C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  slideTag(s, 9, false);
  note(s, "The team went from needing a developer to write every test, to QA engineers independently authoring, running, and debugging their own tests. That's the real impact — not the technology, but what it unlocked for the people using it. 46 actions covers everything from tapping and scrolling to API calls and cross-layer price verification. The four-tier element system means we know exactly which elements on every screen are testable, which need a developer to add a testTag, and which we can reach by text alone. And the three-layer coverage — Android, web, and API — means one framework does everything.");
}

// ─── SLIDE 9 — Team Transformation ──────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  s.addText('QA Engineers Are Now Autonomous', {
    x: 0.5, y: 0.18, w: 9, h: 0.6,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });

  const transforms = [
    {
      before: 'Ask a dev to write the test',
      after: 'Write it yourself in 10 minutes',
      note: 'YAML takes less time to learn than a Jira ticket takes to get answered.',
    },
    {
      before: 'Test breaks, file a bug, wait',
      after: 'Claude heals it in seconds',
      note: 'Self-healing + MCP means a broken test is a conversation, not a ticket.',
    },
    {
      before: 'Coverage = whatever devs had time for',
      after: 'QA owns coverage end-to-end',
      note: 'When QA controls the tools, coverage scales with the team — not with dev bandwidth.',
    },
  ];

  transforms.forEach((t, i) => {
    const y = 1.0 + i * 1.45;
    const cw = 9.4, ch = 1.28;
    card(s, 0.3, y, cw, ch);
    accentBar(s, 0.3, y, ch, C.orange);

    // Before
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: y + 0.22, w: 3.5, h: 0.46,
      fill: { color: C.redSoft },
      line: { color: 'FFCDD2', width: 0.5 },
    });
    s.addText(`✕  Before:  ${t.before}`, {
      x: 0.52, y: y + 0.24, w: 3.48, h: 0.44,
      fontSize: 11.5, color: C.red, fontFace: FONT_BODY, margin: 5,
    });

    // Arrow
    s.addText('→', {
      x: 4.1, y: y + 0.2, w: 0.5, h: 0.48,
      fontSize: 22, color: C.orange, align: 'center',
      fontFace: FONT_BODY, margin: 0,
    });

    // After
    s.addShape(pres.shapes.RECTANGLE, {
      x: 4.7, y: y + 0.22, w: 3.5, h: 0.46,
      fill: { color: C.greenSoft },
      line: { color: 'C8E6C9', width: 0.5 },
    });
    s.addText(`✓  After:  ${t.after}`, {
      x: 4.72, y: y + 0.24, w: 3.48, h: 0.44,
      fontSize: 11.5, color: C.green, fontFace: FONT_BODY, margin: 5,
    });

    // Note
    s.addText(t.note, {
      x: 8.3, y: y + 0.26, w: 1.3, h: 0.7,
      fontSize: 8.5, italic: true, color: C.muted,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  slideTag(s, 10, false);
  note(s, "This is the shift I'm most proud of. QA is no longer dependent on engineering bandwidth to scale test coverage. They have the tools, the vocabulary, and the AI assistant to own testing completely. That changes the dynamic of the whole team. When a developer ships a feature, QA can write a test the same day — not two sprints later when a developer has time. And when a test breaks, QA fixes it themselves. That independence is what makes the team faster. Not just the QA team — the whole engineering team, because developers stop getting interrupted to fix test infrastructure.");
}

// ─── SLIDE 10 — Road Ahead ───────────────────────────────────────────────────
{
  const s = pres.addSlide();
  darkSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  s.addText('Forge Is Ready to Go Bigger', {
    x: 0.6, y: 0.18, w: 8.8, h: 0.65,
    fontSize: 30, bold: true, color: C.white,
    fontFace: FONT_TITLE, margin: 0,
  });
  s.addText('We built something the market doesn\'t have yet', {
    x: 0.6, y: 0.8, w: 8.8, h: 0.32,
    fontSize: 13, italic: true, color: '8899AA',
    fontFace: FONT_BODY, margin: 0,
  });

  // Path A
  const pathBox = (x, y, w, h, accent, tag, title, points) => {
    s.addShape(pres.shapes.RECTANGLE, {
      x, y, w, h,
      fill: { color: C.navyMid },
      line: { color: accent, width: 2 },
      shadow: makeShadow(),
    });
    s.addShape(pres.shapes.RECTANGLE, {
      x, y, w, h: 0.5,
      fill: { color: accent },
      line: { color: accent, width: 0 },
    });
    s.addText(tag, {
      x: x + 0.18, y: y + 0.07, w: w - 0.28, h: 0.38,
      fontSize: 12, bold: true, color: C.white,
      fontFace: FONT_BODY, charSpacing: 2, margin: 0,
    });
    s.addText(title, {
      x: x + 0.18, y: y + 0.65, w: w - 0.28, h: 0.6,
      fontSize: 17, bold: true, color: C.white,
      fontFace: FONT_TITLE, margin: 0,
    });
    points.forEach((pt, i) => {
      s.addText(`◆  ${pt}`, {
        x: x + 0.18, y: y + 1.35 + i * 0.55, w: w - 0.28, h: 0.48,
        fontSize: 12, color: 'B8D4F0', fontFace: FONT_BODY, margin: 0,
      });
    });
  };

  pathBox(0.4, 1.25, 4.2, 3.9,
    C.orange,
    'PATH A  ·  OPEN SOURCE',
    'Share with the\nAndroid community',
    [
      'Fill a real gap in Compose tooling',
      'Build reputation & contributor base',
      'POP Club becomes the company that\nsolves the Compose testing problem',
    ]
  );

  // VS divider
  s.addText('OR', {
    x: 4.7, y: 2.95, w: 0.6, h: 0.5,
    fontSize: 16, bold: true, color: C.orange,
    align: 'center', fontFace: FONT_TITLE, margin: 0,
  });
  s.addShape(pres.shapes.LINE, {
    x: 5.0, y: 1.4, w: 0, h: 3.5,
    line: { color: C.navyMid, width: 1 },
  });

  pathBox(5.4, 1.25, 4.2, 3.9,
    C.blue,
    'PATH B  ·  OFFER AS A SERVICE',
    'License Forge to\nother Compose teams',
    [
      'Every startup on Compose has this pain',
      'Recurring revenue. Differentiated IP.',
      'We\'ve proven it works at\nproduction scale — credibility built-in',
    ]
  );

  slideTag(s, 11, true);
  note(s, "Here's the thing — every company building on Jetpack Compose has the same problem we had. There is no mature YAML-based testing tool for Compose apps. Maestro is the closest, but it doesn't understand the testTag hierarchy and has no self-healing. Forge fills that gap. Whether we open source it to build community and reputation, or license it as a product to other startups, there's a real opportunity here. I'm not saying we should do either tomorrow. I'm saying the optionality exists — and that's a good problem to have.");
}

// ─── SLIDE 11 — Why Now ──────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  lightSlide(s);
  topBar(s, C.blue);
  bottomBar(s, C.orange);

  s.addText('The Timing Is Right', {
    x: 0.5, y: 0.18, w: 9, h: 0.6,
    fontSize: 30, bold: true, color: C.text,
    fontFace: FONT_TITLE, margin: 0,
  });

  const reasons = [
    {
      num: '01',
      title: 'Jetpack Compose adoption is accelerating',
      body: 'Google\'s recommended Android UI toolkit since 2023. Every Android team is migrating right now. The tooling gap is growing, not shrinking.',
      accent: C.blue,
    },
    {
      num: '02',
      title: 'AI + testing is the next frontier',
      body: 'Most teams are still doing manual testing or fighting with tools that don\'t understand Compose. Forge + Claude is already where the industry is heading.',
      accent: C.orange,
    },
    {
      num: '03',
      title: 'We\'ve proven it works at production scale',
      body: 'Real tests. Real app. Real devices. Not a prototype or a side project — Forge is in production, catching bugs, today.',
      accent: C.green,
    },
  ];

  reasons.forEach((r, i) => {
    const y = 1.05 + i * 1.42;
    card(s, 0.3, y, 9.4, 1.25);
    accentBar(s, 0.3, y, 1.25, r.accent);

    s.addText(r.num, {
      x: 0.52, y: y + 0.2, w: 0.6, h: 0.55,
      fontSize: 24, bold: true, color: r.accent,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(r.title, {
      x: 1.25, y: y + 0.12, w: 5.5, h: 0.44,
      fontSize: 15, bold: true, color: C.text,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(r.body, {
      x: 1.25, y: y + 0.56, w: 8.2, h: 0.6,
      fontSize: 12, color: C.bodyText,
      fontFace: FONT_BODY, margin: 0,
    });
  });

  slideTag(s, 12, false);
  note(s, "Jetpack Compose went from experimental to the recommended Android toolkit in 2023. The companies that are building on it right now are all facing the same testing problem we faced. And while there are a handful of tools emerging, none of them are purpose-built for Compose with the level of AI integration that Forge has. The window where this is a differentiator is probably two to three years. After that, someone bigger solves it. The question is whether we want to be the ones who solved it first.");
}

// ─── SLIDE 12 — Closing ──────────────────────────────────────────────────────
{
  const s = pres.addSlide();
  darkSlide(s);
  topBar(s, C.orange);
  bottomBar(s, C.blue);

  // Big closing statement
  s.addText('We didn\'t buy a solution.', {
    x: 0.7, y: 0.55, w: 8.6, h: 0.8,
    fontSize: 36, bold: false, color: 'AABBCC',
    fontFace: FONT_TITLE, italic: true, margin: 0,
  });
  s.addText('We built competitive advantage.', {
    x: 0.7, y: 1.3, w: 8.6, h: 0.85,
    fontSize: 36, bold: true, color: C.white,
    fontFace: FONT_TITLE, margin: 0,
  });

  // 3 lines with left orange bar
  s.addShape(pres.shapes.RECTANGLE, {
    x: 0.7, y: 2.42, w: 0.05, h: 2.1,
    fill: { color: C.orange },
    line: { color: C.orange, width: 0 },
  });

  const lines = [
    { text: 'Forge tests our app today', sub: '— and gets better every week' },
    { text: 'Forge can test any Compose app tomorrow', sub: '— it\'s not tied to POP Club' },
    { text: 'It started with a problem.', sub: 'It ends with a product.' },
  ];
  lines.forEach((l, i) => {
    s.addText(l.text, {
      x: 0.95, y: 2.45 + i * 0.72, w: 7.5, h: 0.4,
      fontSize: 15.5, bold: true, color: C.white,
      fontFace: FONT_TITLE, margin: 0,
    });
    s.addText(l.sub, {
      x: 0.95, y: 2.82 + i * 0.72, w: 7.5, h: 0.28,
      fontSize: 11.5, italic: true, color: '8899AA',
      fontFace: FONT_BODY, margin: 0,
    });
  });

  // POP Club tag
  s.addText('POP CLUB  ·  QA ENGINEERING  ·  2026', {
    x: 0.7, y: H - 0.48, w: 7, h: 0.26,
    fontSize: 8.5, color: '4A6080', fontFace: FONT_BODY,
    charSpacing: 3, margin: 0,
  });

  slideTag(s, 13, true);
  note(s, "I'll leave you with this. When we started, Forge was a solution to our testing problem. A QA pain point that needed fixing. Today it's a framework that could stand on its own as a product. That doesn't happen often — when you solve a real problem well enough, the solution becomes valuable beyond the original use case. Forge tests our app today. But it could test any Jetpack Compose app. It was built in six weeks by one engineer. Imagine what two engineers and six months could do. Thank you — I'm happy to take any questions, walk through a live demo, or dig into any part of the architecture.");
}

// ─── Write ────────────────────────────────────────────────────────────────────
pres.writeFile({ fileName: '/Users/deepa/repos/Forge/Forge_Impact_Presentation.pptx' })
  .then(() => {
    const fs = require('fs');
    const size = fs.statSync('/Users/deepa/repos/Forge/Forge_Impact_Presentation.pptx').size;
    console.log(`✅ Written: Forge_Impact_Presentation.pptx`);
    console.log(`   Slides: 13`);
    console.log(`   Size:   ${(size / 1024).toFixed(0)} KB`);
  })
  .catch(err => {
    console.error('❌ Error:', err.message);
    process.exit(1);
  });
