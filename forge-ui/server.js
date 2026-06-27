const express = require('express');
const http    = require('http');
const WebSocket = require('ws');
const multer  = require('multer');
const { spawn, execSync, exec } = require('child_process');

// Promise wrapper for exec — keeps the event loop free
function run(cmd, timeout = 5000) {
  return new Promise((resolve, reject) => {
    exec(cmd, { timeout, maxBuffer: 12 * 1024 * 1024 }, (err, stdout) => {
      if (err) reject(err); else resolve(stdout);
    });
  });
}
const path = require('path');
const fs   = require('fs');
const yaml = require('js-yaml');

const app    = express();
const server = http.createServer(app);
const wss    = new WebSocket.Server({ server });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const FORGE_ROOT     = path.join(__dirname, '..');
const TESTDATA_ROOT  = path.join(FORGE_ROOT, 'src/test/java/com/popclub/androidTests');
const FLOWS_ROOT     = path.join(FORGE_ROOT, 'src/test/java/com/popclub/androidFlows');
const APK_DIR        = path.join(FORGE_ROOT, 'src/main/resources');
const APP_PACKAGE    = 'com.popclub.android';

// ─── Helpers ────────────────────────────────────────────────────────────────

function walkYaml(dir, base = '') {
  const results = [];
  for (const entry of fs.readdirSync(dir).sort()) {
    const full = path.join(dir, entry);
    const rel  = base ? `${base}/${entry}` : entry;
    if (fs.statSync(full).isDirectory()) {
      results.push(...walkYaml(full, rel));
    } else if (entry.endsWith('.yaml') || entry.endsWith('.yml')) {
      results.push(rel);
    }
  }
  return results;
}

function suiteFor(testFile) {
  // Pick the most appropriate testng suite based on file path
  if (testFile.includes('shop') || testFile.includes('home') ||
      testFile.includes('login') || testFile.includes('profile') ||
      testFile.includes('card')) return 'testng-shop.xml';
  return 'testng.xml';
}

function getConnectedDevice() {
  try {
    const out = execSync('adb devices', { timeout: 3000 }).toString();
    const lines = out.split('\n').slice(1).filter(l => l.includes('\tdevice'));
    if (lines.length > 0) return lines[0].split('\t')[0].trim();
  } catch (_) {}
  return null;
}

// Build `adb [-s <serial>] shell <cmd>` string for use with run()
function adbShell(cmd) {
  const serial = getConnectedDevice();
  return serial ? `adb -s ${serial} shell ${cmd}` : `adb shell ${cmd}`;
}

// Build args array for spawn('adb', adbArgs(...))
function adbArgs(...args) {
  const serial = getConnectedDevice();
  return serial ? ['-s', serial, ...args] : [...args];
}

// ─── API Routes ──────────────────────────────────────────────────────────────

// ── Flows API ─────────────────────────────────────────────────────────────────

// List all flow names (without .yaml)
app.get('/api/flows', (req, res) => {
  try {
    if (!fs.existsSync(FLOWS_ROOT)) fs.mkdirSync(FLOWS_ROOT, { recursive: true });
    const files = fs.readdirSync(FLOWS_ROOT)
      .filter(f => f.endsWith('.yaml') || f.endsWith('.yml'))
      .map(f => f.replace(/\.(yaml|yml)$/, ''))
      .sort();
    res.json(files);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get a flow's YAML content
app.get('/api/flow', (req, res) => {
  const name = req.query.name;
  if (!name) return res.status(400).json({ error: 'name required' });
  const file = path.join(FLOWS_ROOT, name.endsWith('.yaml') ? name : name + '.yaml');
  if (!file.startsWith(FLOWS_ROOT)) return res.status(403).end();
  try {
    res.type('text/plain').send(fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '');
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Save (create/update) a flow
app.post('/api/flow', (req, res) => {
  const { name, content } = req.body;
  if (!name || content == null) return res.status(400).json({ error: 'name and content required' });
  if (!fs.existsSync(FLOWS_ROOT)) fs.mkdirSync(FLOWS_ROOT, { recursive: true });
  const file = path.join(FLOWS_ROOT, name.endsWith('.yaml') ? name : name + '.yaml');
  if (!file.startsWith(FLOWS_ROOT)) return res.status(403).end();

  try { yaml.load(content); } catch (yamlErr) {
    const mark = yamlErr.mark;
    const loc  = mark ? ` (line ${mark.line + 1}, col ${mark.column + 1})` : '';
    return res.status(422).json({ yamlError: yamlErr.reason + loc });
  }

  try {
    fs.writeFileSync(file, content, 'utf8');
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Delete a flow
app.delete('/api/flow', (req, res) => {
  const name = req.query.name;
  if (!name) return res.status(400).json({ error: 'name required' });
  const file = path.join(FLOWS_ROOT, name.endsWith('.yaml') ? name : name + '.yaml');
  if (!file.startsWith(FLOWS_ROOT)) return res.status(403).end();
  try {
    if (fs.existsSync(file)) fs.unlinkSync(file);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// List all YAML test files
app.get('/api/tests', (req, res) => {
  try {
    res.json(walkYaml(TESTDATA_ROOT));
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/tests-meta — file list with testName + tags extracted from each YAML
app.get('/api/tests-meta', (req, res) => {
  try {
    const files = walkYaml(TESTDATA_ROOT);
    const meta  = files.map(file => {
      try {
        const parsed = yaml.load(fs.readFileSync(path.join(TESTDATA_ROOT, file), 'utf8'));
        return { file, testName: parsed.testName || file, tags: Array.isArray(parsed.tags) ? parsed.tags : [] };
      } catch (_) {
        return { file, testName: file, tags: [] };
      }
    });
    res.json(meta);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/rename-test
app.post('/api/rename-test', (req, res) => {
  const { file, newFile } = req.body;
  if (!file || !newFile) return res.status(400).json({ error: 'file and newFile required' });
  const from = path.join(TESTDATA_ROOT, file);
  const to   = path.join(TESTDATA_ROOT, newFile);
  if (!from.startsWith(TESTDATA_ROOT) || !to.startsWith(TESTDATA_ROOT))
    return res.status(403).json({ error: 'Forbidden' });
  if (!fs.existsSync(from)) return res.status(404).json({ error: 'Source not found' });
  if (fs.existsSync(to))    return res.status(409).json({ error: 'Target already exists' });
  try {
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.renameSync(from, to);
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// POST /api/duplicate-test
app.post('/api/duplicate-test', (req, res) => {
  const { file, newFile } = req.body;
  if (!file || !newFile) return res.status(400).json({ error: 'file and newFile required' });
  const from = path.join(TESTDATA_ROOT, file);
  const to   = path.join(TESTDATA_ROOT, newFile);
  if (!from.startsWith(TESTDATA_ROOT) || !to.startsWith(TESTDATA_ROOT))
    return res.status(403).json({ error: 'Forbidden' });
  if (!fs.existsSync(from)) return res.status(404).json({ error: 'Source not found' });
  if (fs.existsSync(to))    return res.status(409).json({ error: 'Target already exists' });
  try {
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.copyFileSync(from, to);
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Parse YAML and return test name + steps
function loadFlowSubSteps(flowName, elMap) {
  try {
    const flowPath = path.join(FLOWS_ROOT, flowName + '.yaml');
    const parsed   = yaml.load(fs.readFileSync(flowPath, 'utf8'));
    return (parsed.steps || []).map((s, i) => ({
      index       : i,
      action      : s.action,
      element     : s.element || null,
      locatorValue: s.element ? (elMap[s.element] || null) : null,
      value       : s.value   || null,
      text        : s.text    || null,
    }));
  } catch (e) {
    return [];  // flow file not found or parse error — just show empty
  }
}

app.get('/api/test-steps', (req, res) => {
  const file = req.query.file;
  if (!file) return res.status(400).json({ error: 'file param required' });

  const fullPath = path.join(TESTDATA_ROOT, file);
  try {
    const parsed   = yaml.load(fs.readFileSync(fullPath, 'utf8'));
    const platform = parsed.platform || 'android';
    const elMap    = loadElementLocatorMap();
    res.json({
      testName : parsed.testName || file,
      platform,
      steps    : (parsed.steps || []).map((s, i) => {
        const step = {
          index       : i,
          action      : s.action,
          element     : s.element || null,
          locatorValue: s.element ? (elMap[s.element] || null) : null,
          value       : s.value   || null,
          text        : s.text    || null,
          flow        : s.flow    || null,
          url         : s.url     || null,
          method      : s.method  || null,
        };
        // Expand call steps with their flow sub-steps
        if (s.action === 'call' && s.flow) {
          step.subSteps = loadFlowSubSteps(s.flow, elMap);
        }
        return step;
      })
    });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

const SCREEN_TMP = '/tmp/forge_screen.png';
const SCREEN_JPG = '/tmp/forge_screen.jpg';

function sendPng(res, buf) {
  if (!res.headersSent) {
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'no-store');
    res.send(buf);
  }
}

// Live device screenshot — spawn (non-blocking) + JPEG via sips (~5x smaller payload)
app.get('/api/screenshot', (req, res) => {
  const serial = getConnectedDevice();
  const adbArgs = serial
    ? ['-s', serial, 'exec-out', 'screencap', '-p']
    : ['exec-out', 'screencap', '-p'];
  const adb    = spawn('adb', adbArgs);
  const chunks = [];

  adb.stdout.on('data', chunk => chunks.push(chunk));
  adb.stderr.on('data', () => {});

  adb.on('close', (code) => {
    if (code !== 0 || !chunks.length) {
      if (!res.headersSent) res.status(503).json({ error: 'Screenshot unavailable' });
      return;
    }
    const png = Buffer.concat(chunks);

    // Convert PNG → JPEG via sips (macOS built-in, no extra installs)
    fs.writeFile(SCREEN_TMP, png, (writeErr) => {
      if (writeErr) return sendPng(res, png);

      exec(`sips -s format jpeg -s formatOptions 60 "${SCREEN_TMP}" --out "${SCREEN_JPG}" 2>/dev/null`,
        { timeout: 3000 }, (sipsErr) => {
          if (sipsErr) return sendPng(res, png);

          fs.readFile(SCREEN_JPG, (readErr, jpg) => {
            if (readErr || !jpg) return sendPng(res, png);
            if (!res.headersSent) {
              res.setHeader('Content-Type', 'image/jpeg');
              res.setHeader('Cache-Control', 'no-store');
              res.send(jpg);
            }
          });
        }
      );
    });
  });

  adb.on('error', (e) => {
    if (!res.headersSent) res.status(503).json({ error: e.message });
  });

  req.on('close', () => adb.kill()); // client aborted → kill screencap immediately
});

// Device screen size
app.get('/api/screen-size', async (req, res) => {
  try {
    const out = await run(adbShell('wm size'), 3000);
    const m = out.match(/(\d+)x(\d+)/);
    if (m) return res.json({ width: parseInt(m[1]), height: parseInt(m[2]) });
    res.status(500).json({ error: 'Could not parse screen size' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Parse all nodes from uiautomator XML
function parseNodes(xml) {
  const nodeRegex = /<node([^>]+?)(?:\/>|>)/g;
  const attrRegex = /(\w[\w-]*)="([^"]*)"/g;
  const nodes = [];

  let m;
  while ((m = nodeRegex.exec(xml)) !== null) {
    const attrs = {};
    let a;
    attrRegex.lastIndex = 0;
    while ((a = attrRegex.exec(m[1])) !== null) {
      attrs[a[1]] = a[2];
    }
    const boundsStr = attrs.bounds || '';
    const bm = boundsStr.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/);
    if (!bm) continue;

    nodes.push({
      contentDesc : attrs['content-desc'] || '',
      resourceId  : attrs['resource-id']  || '',
      text        : attrs['text']         || '',
      className   : attrs['class']        || '',
      bounds      : boundsStr,
      x1: parseInt(bm[1]), y1: parseInt(bm[2]),
      x2: parseInt(bm[3]), y2: parseInt(bm[4]),
      clickable   : attrs['clickable']  === 'true',
      scrollable  : attrs['scrollable'] === 'true',
      enabled     : attrs['enabled']    === 'true',
    });
  }
  return nodes;
}

async function dumpXml() {
  await run(adbShell('uiautomator dump --compressed /sdcard/ui_dump.xml'), 8000);
  return run(adbShell('cat /sdcard/ui_dump.xml'), 5000);
}

// Reverse map: accessibilityId/locatorValue → element key name in YAMLs
// Built lazily and cached for 10 s so file changes are picked up quickly during development.
let _reverseMapCache   = null;
let _reverseMapBuiltAt = 0;

function getReverseElementMap() {
  const now = Date.now();
  if (_reverseMapCache && now - _reverseMapBuiltAt < 10_000) return _reverseMapCache;
  const map = {};
  const locatorMap = loadElementLocatorMap(); // { key → locatorValue }
  for (const [key, val] of Object.entries(locatorMap)) {
    if (val) map[val.toLowerCase()] = key;  // case-insensitive lookup
  }
  _reverseMapCache   = map;
  _reverseMapBuiltAt = now;
  return map;
}

// GET /api/inspect?x=&y=  — find element at device coords, prefer one with contentDesc
app.get('/api/inspect', async (req, res) => {
  const x = parseInt(req.query.x);
  const y = parseInt(req.query.y);
  if (isNaN(x) || isNaN(y)) return res.status(400).json({ error: 'x and y required' });

  try {
    const xml   = await dumpXml();
    const nodes = parseNodes(xml);

    // Collect all nodes that contain (x,y), sorted smallest area first
    const hits = nodes
      .filter(n => x >= n.x1 && x <= n.x2 && y >= n.y1 && y <= n.y2)
      .sort((a, b) => (a.x2 - a.x1) * (a.y2 - a.y1) - (b.x2 - b.x1) * (b.y2 - b.y1));

    // Prefer the smallest node that has a contentDesc (qaTestTag)
    const withTag = hits.find(n => n.contentDesc);
    const best    = withTag || hits[0] || null;

    if (best) {
      // Reverse-lookup: find the element key from the YAMLs that maps to this contentDesc
      const reverseMap = getReverseElementMap();
      const elementKey = best.contentDesc
        ? (reverseMap[best.contentDesc.toLowerCase()] || null)
        : null;
      res.json({ ...best, elementKey });
    } else {
      res.json({ contentDesc: '', resourceId: '', text: '', className: '', bounds: '', clickable: false, elementKey: null });
    }
  } catch (e) {
    res.status(500).json({ error: 'Inspect failed: ' + e.message });
  }
});

// POST /api/device-action — perform ADB input actions on the device (async)
app.post('/api/device-action', async (req, res) => {
  const { type, x, y, x2, y2, text, key, duration, value } = req.body;
  try {
    switch (type) {
      case 'tap':
        await run(adbShell(`input tap ${Math.round(x)} ${Math.round(y)}`), 3000);
        break;
      case 'swipe':
        await run(adbShell(`input swipe ${Math.round(x)} ${Math.round(y)} ${Math.round(x2)} ${Math.round(y2)} ${duration || 300}`), 4000);
        break;
      case 'text': {
        const escaped = (text || '').replace(/(['"\\$`!#&*?|<>(){};])/g, '\\$1').replace(/ /g, '%s');
        await run(adbShell(`input text "${escaped}"`), 4000);
        break;
      }
      case 'key':
        await run(adbShell(`input keyevent ${key}`), 3000);
        break;
      case 'longpress':
        await run(adbShell(`input swipe ${Math.round(x)} ${Math.round(y)} ${Math.round(x)} ${Math.round(y)} 800`), 4000);
        break;
      case 'scroll': {
        const cx = Math.round(x), cy = Math.round(y);
        const dist = 400;
        const dy = y2 > 0 ? -dist : dist;
        await run(adbShell(`input swipe ${cx} ${cy} ${cx} ${cy + dy} 400`), 4000);
        break;
      }
      case 'back':
        await run(adbShell('input keyevent 4'), 3000);
        break;
      case 'hideKeyboard':
        await run(adbShell('input keyevent 111'), 3000);
        break;
      case 'clearText':
        // Tap → select all → delete
        await run(adbShell(`input tap ${Math.round(x)} ${Math.round(y)}`), 3000);
        await new Promise(r => setTimeout(r, 300));
        await run(adbShell('input keyevent --longpress 29'), 2000);  // Ctrl+A
        await run(adbShell('input keyevent 67'), 2000);              // DEL
        break;
      case 'sleep':
        await new Promise(r => setTimeout(r, parseInt(value) || 1000));
        break;
      default:
        return res.status(400).json({ error: 'Unknown action: ' + type });
    }
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/run-step — resolve element key → UI dump → coords → execute action
// Used by editor Ctrl+Enter to run element-based steps on the device.
app.post('/api/run-step', async (req, res) => {
  const { action, element, value, x, y } = req.body;
  try {
    let tapX = x != null ? +x : null;
    let tapY = y != null ? +y : null;
    let resolvedLocator = element || null;

    // Resolve element key → coordinates via UI dump
    if (element) {
      const locatorMap   = loadElementLocatorMap();
      const locatorValue = locatorMap[element] || element;  // fallback: key itself
      resolvedLocator    = locatorValue;

      const xml   = await dumpXml();
      const nodes = parseNodes(xml);

      const found = nodes.find(n =>
        n.contentDesc === locatorValue ||
        n.resourceId  === locatorValue ||
        n.resourceId.split('/').pop() === locatorValue ||
        n.text        === locatorValue
      );

      if (!found) {
        return res.json({ ok: false, error: `Element "${element}" (locator: "${locatorValue}") not found on current screen` });
      }
      tapX = Math.round((found.x1 + found.x2) / 2);
      tapY = Math.round((found.y1 + found.y2) / 2);
    }

    switch (action) {
      case 'tap':
      case 'tapIfPresent':
        if (tapX === null) return res.json({ ok: false, error: 'tap requires element or x/y coords' });
        await run(adbShell(`input tap ${tapX} ${tapY}`), 3000);
        break;
      case 'longPress':
        if (tapX === null) return res.json({ ok: false, error: 'longPress requires element or x/y' });
        await run(adbShell(`input swipe ${tapX} ${tapY} ${tapX} ${tapY} 800`), 4000);
        break;
      case 'enterText':
        if (tapX !== null) {
          await run(adbShell(`input tap ${tapX} ${tapY}`), 3000);
          await new Promise(r => setTimeout(r, 400));
        }
        if (value) {
          const esc = value.replace(/(['"\\$`!#&*?|<>(){};])/g, '\\$1').replace(/ /g, '%s');
          await run(adbShell(`input text "${esc}"`), 4000);
        }
        break;
      case 'clearText':
        if (tapX !== null) await run(adbShell(`input tap ${tapX} ${tapY}`), 3000);
        await new Promise(r => setTimeout(r, 300));
        await run(adbShell('input keyevent --longpress 29'), 2000);
        await run(adbShell('input keyevent 67'), 2000);
        break;
      case 'scrollDown': case 'swipeDown':
        await run(adbShell('input swipe 540 1400 540 400 400'), 4000);
        break;
      case 'scrollUp': case 'swipeUp':
        await run(adbShell('input swipe 540 400 540 1400 400'), 4000);
        break;
      case 'back':
        await run(adbShell('input keyevent 4'), 3000);
        break;
      case 'hideKeyboard':
        await run(adbShell('input keyevent 111'), 3000);
        break;
      case 'sleep':
        await new Promise(r => setTimeout(r, parseInt(value) || 1000));
        break;
      case 'waitFor':
      case 'verifyElement':
        // Just dump and verify presence — don't tap
        if (!element) return res.json({ ok: false, error: 'waitFor/verifyElement requires element' });
        return res.json({
          ok: tapX !== null,
          message: tapX !== null
            ? `"${resolvedLocator}" found on screen @ (${tapX},${tapY})`
            : `"${resolvedLocator}" NOT found on screen`
        });
      case 'scrollTo':
        // Try scrolling down up to 3 times looking for element
        if (!element) return res.json({ ok: false, error: 'scrollTo requires element' });
        for (let attempt = 0; attempt < 3; attempt++) {
          const xmlCheck = await dumpXml();
          const found2 = parseNodes(xmlCheck).find(n =>
            n.contentDesc === resolvedLocator || n.resourceId.split('/').pop() === resolvedLocator || n.text === resolvedLocator
          );
          if (found2) return res.json({ ok: true, message: `Found "${resolvedLocator}" after ${attempt} scroll(s)` });
          await run(adbShell('input swipe 540 1400 540 400 400'), 4000);
          await new Promise(r => setTimeout(r, 800));
        }
        return res.json({ ok: false, error: `"${resolvedLocator}" not found after 3 scrolls` });
      default:
        return res.json({ ok: false, error: `No device preview for: ${action}` });
    }

    const msg = `${action}${resolvedLocator ? ' → ' + resolvedLocator : ''}${tapX !== null ? ` @ (${tapX},${tapY})` : ''}`;
    res.json({ ok: true, message: msg });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// POST /api/step-op — duplicate or reorder steps in a YAML test file
app.post('/api/step-op', (req, res) => {
  const { op, file, index, from, to } = req.body;
  if (!file) return res.status(400).json({ error: 'file required' });
  const fullPath = path.join(TESTDATA_ROOT, file);
  if (!fullPath.startsWith(TESTDATA_ROOT)) return res.status(403).json({ error: 'Forbidden' });

  try {
    const content = fs.readFileSync(fullPath, 'utf8');
    const lines   = content.split('\n');

    // Find 'steps:' line
    const stepsLineIdx = lines.findIndex(l => /^steps:\s*$/.test(l));
    if (stepsLineIdx === -1) return res.status(422).json({ error: 'No steps: section found' });

    // Split step body into blocks (each starts with '  - action:')
    const header     = lines.slice(0, stepsLineIdx + 1).join('\n');
    const stepLines  = lines.slice(stepsLineIdx + 1);
    const blocks     = [];   // array of string[] (lines for each step)
    let   cur        = null;

    for (const l of stepLines) {
      if (/^  - action:/.test(l)) {
        if (cur !== null) blocks.push(cur);
        cur = [l];
      } else if (cur !== null) {
        cur.push(l);
      }
    }
    if (cur !== null) blocks.push(cur);

    if (op === 'duplicate') {
      if (index == null || index < 0 || index >= blocks.length)
        return res.status(400).json({ error: 'invalid index' });
      blocks.splice(index + 1, 0, [...blocks[index]]);
    } else if (op === 'reorder') {
      if (from == null || to == null || from === to)
        return res.json({ ok: true, noop: true });
      if (from < 0 || to < 0 || from >= blocks.length || to >= blocks.length)
        return res.status(400).json({ error: 'index out of range' });
      const [moved] = blocks.splice(from, 1);
      blocks.splice(to, 0, moved);
    } else {
      return res.status(400).json({ error: 'op must be duplicate or reorder' });
    }

    // Reconstruct: join blocks with single blank line between them for readability
    const newContent = header + '\n' + blocks.map(b => b.join('\n')).join('\n\n') + '\n';

    // Validate YAML before writing
    try { yaml.load(newContent); } catch (yamlErr) {
      return res.status(422).json({ error: 'YAML validation failed: ' + yamlErr.message });
    }

    fs.writeFileSync(fullPath, newContent, 'utf8');
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/tagged-elements — return ALL elements that have a contentDesc (qaTestTag)
app.get('/api/tagged-elements', async (req, res) => {
  try {
    const xml   = await dumpXml();
    const nodes = parseNodes(xml);

    const tagged = nodes
      .filter(n => n.contentDesc)
      .map(n => ({
        contentDesc : n.contentDesc,
        resourceId  : n.resourceId,
        text        : n.text,
        className   : n.className.split('.').pop(),
        bounds      : n.bounds,
        x1: n.x1, y1: n.y1, x2: n.x2, y2: n.y2,
        clickable   : n.clickable,
        scrollable  : n.scrollable,
      }));

    res.json(tagged);
  } catch (e) {
    res.status(500).json({ error: 'Dump failed: ' + e.message });
  }
});

// Get raw YAML content for editor
app.get('/api/test-yaml', (req, res) => {
  const file = req.query.file;
  if (!file) return res.status(400).json({ error: 'file param required' });
  const fullPath = path.join(TESTDATA_ROOT, file);
  if (!fullPath.startsWith(TESTDATA_ROOT)) return res.status(403).send('Forbidden');
  try {
    res.setHeader('Content-Type', 'text/plain');
    res.send(fs.readFileSync(fullPath, 'utf8'));
  } catch (e) {
    res.status(404).json({ error: e.message });
  }
});

// Save edited YAML back to disk
app.post('/api/save-test', (req, res) => {
  const { file, content } = req.body;
  if (!file || content === undefined) return res.status(400).json({ error: 'file and content required' });
  const fullPath = path.join(TESTDATA_ROOT, file);
  if (!fullPath.startsWith(TESTDATA_ROOT)) return res.status(403).json({ error: 'Forbidden' });

  // Validate YAML before writing — return a friendly error if it won't parse
  try {
    yaml.load(content);
  } catch (yamlErr) {
    // js-yaml error has .mark with line/col info
    const mark = yamlErr.mark;
    const loc  = mark ? ` (line ${mark.line + 1}, col ${mark.column + 1})` : '';
    return res.status(422).json({ yamlError: yamlErr.reason + loc });
  }

  try {
    fs.mkdirSync(path.dirname(fullPath), { recursive: true });
    fs.writeFileSync(fullPath, content, 'utf8');
    res.json({ ok: true, path: file });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Create new test from template
app.post('/api/new-test', (req, res) => {
  const { file } = req.body;
  if (!file) return res.status(400).json({ error: 'file required' });
  const fullPath = path.join(TESTDATA_ROOT, file);
  if (!fullPath.startsWith(TESTDATA_ROOT)) return res.status(403).json({ error: 'Forbidden' });

  const name     = path.basename(file, '.yaml').replace(/_/g, ' ');
  const template =
`testName: ${name}
platform: android
noReset: true
loginRequired: true
retry: 1

features:
  - common
  - login
  - home
  - shop

tags:
  - smoke

steps:
  - action: launchApp

  - action: loginIfNeeded
    value: "1234561122"
    text: "560102"

  - action: waitFor
    element: home_tab

`;
  try {
    fs.mkdirSync(path.dirname(fullPath), { recursive: true });
    fs.writeFileSync(fullPath, template, 'utf8');
    res.json({ ok: true, content: template });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/delete-test — permanently delete a test YAML file
app.post('/api/delete-test', (req, res) => {
  const { file } = req.body;
  if (!file) return res.status(400).json({ error: 'file required' });

  // Resolve to absolute path and ensure it's inside TESTDATA_ROOT (no path traversal)
  const fullPath = path.resolve(TESTDATA_ROOT, file);
  if (!fullPath.startsWith(TESTDATA_ROOT + path.sep) && fullPath !== TESTDATA_ROOT) {
    return res.status(403).json({ error: 'Forbidden: path outside test data root' });
  }
  if (!fs.existsSync(fullPath)) return res.status(404).json({ error: 'File not found' });

  try {
    fs.unlinkSync(fullPath);
    console.log('[delete-test]', file);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Connected device info
app.get('/api/device', (req, res) => {
  const device = getConnectedDevice();
  res.json({ device });
});

// POST /api/stop — HTTP fallback for stopping a test run when WS is unavailable
app.post('/api/stop', (req, res) => {
  stopTest(null); // broadcast handles notifying all WS clients
  res.json({ ok: true, hadRun: !!currentRun });
});

// ── APK management ────────────────────────────────────────────────────────────

// GET /api/apk/status — is app installed + available APKs in resources
app.get('/api/apk/status', async (req, res) => {
  // List APKs in src/main/resources/
  let apks = [];
  try {
    apks = fs.readdirSync(APK_DIR)
      .filter(f => f.endsWith('.apk'))
      .sort()
      .reverse()   // newest-named first
      .map(f => ({ name: f, path: f, size: fs.statSync(path.join(APK_DIR, f)).size }));
  } catch (_) {}

  // Check if app is installed on device
  let installed = false;
  try {
    const out = await run(adbShell(`pm list packages ${APP_PACKAGE}`), 4000);
    installed = out.includes(APP_PACKAGE);
  } catch (_) {}

  res.json({ installed, apks, package: APP_PACKAGE });
});

// POST /api/apk/install — install APK, stream progress via WebSocket
app.post('/api/apk/install', (req, res) => {
  const { apk } = req.body;
  if (!apk) return res.status(400).json({ error: 'apk filename required' });

  // Security: only allow files from APK_DIR
  const apkPath = path.join(APK_DIR, path.basename(apk));
  if (!fs.existsSync(apkPath)) return res.status(404).json({ error: 'APK not found: ' + apk });

  const ws = [...wss.clients].find(c => c.readyState === WebSocket.OPEN);
  const log = (line, level = '') => ws && send(ws, { type: 'log', line, level });

  log(`▶ Installing ${path.basename(apkPath)}…`, 'step');
  res.json({ ok: true });   // respond immediately, install streams to WS

  const proc = spawn('adb', adbArgs('install', '-r', apkPath), { shell: true });

  proc.stdout.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(l => log(l));
  });
  proc.stderr.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(l => log(l, 'error'));
  });
  proc.on('close', code => {
    if (code === 0) {
      log('✅ Install successful', 'pass');
    } else {
      log(`❌ Install failed (exit ${code})`, 'error');
    }
    // Notify UI to refresh app status
    ws && send(ws, { type: 'apk_install_done', success: code === 0 });
  });
  proc.on('error', e => log('Failed to start adb: ' + e.message, 'error'));
});

// ─── WebSocket — test runner ──────────────────────────────────────────────────

let currentRun    = null;
let runWasStopped = false;   // true when user clicked Stop — suppresses 'done' event

wss.on('connection', (ws) => {
  console.log('UI client connected');

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw); } catch (_) { return; }

    if (msg.type === 'run') {
      startTest(msg.file, msg.device, ws, msg.fromStep);
    } else if (msg.type === 'stop') {
      stopTest(ws);
    } else if (msg.type === 'chat') {
      handleChatMessage(msg.message, msg.context || null, ws);
    }
  });

  ws.on('close', () => console.log('UI client disconnected'));
});

function send(ws, data) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

function broadcast(data) {
  const payload = JSON.stringify(data);
  for (const client of wss.clients) {
    if (client.readyState === WebSocket.OPEN) client.send(payload);
  }
}

function stopTest(ws) {
  stopNetworkCapture();
  if (!currentRun) {
    // Nothing running — still ack so the client can reset its UI
    if (ws) send(ws, { type: 'stopped' });
    return;
  }
  runWasStopped = true;
  const pid = currentRun.pid;
  currentRun = null;

  // SIGTERM first — gives Maven/Surefire a chance to clean up
  try { process.kill(-pid, 'SIGTERM'); } catch (_) {}

  // SIGKILL after 3 s in case SIGTERM is ignored (Surefire fork sometimes is)
  setTimeout(() => {
    try { process.kill(-pid, 'SIGKILL'); } catch (_) {}
  }, 3000);

  if (ws) send(ws, { type: 'stopped' });
  // Also broadcast to all connected clients so other tabs update
  broadcast({ type: 'stopped' });
}

function startTest(file, deviceOverride, ws, fromStep) {
  if (currentRun) {
    try { process.kill(-currentRun.pid, 'SIGTERM'); } catch (_) {}
    currentRun = null;
  }
  runWasStopped = false;

  const suite  = suiteFor(file);
  const device = deviceOverride || getConnectedDevice() || '10BDCM0YJZ00043';

  // Derive filename only (TestRunnerTest looks up by basename)
  const fileName = path.basename(file);

  // fromStep is 0-indexed from the client; Forge expects 1-indexed
  const startStep = (typeof fromStep === 'number' && fromStep > 0) ? fromStep + 1 : null;

  lastRunningStepIdx = -1;  // reset for new run
  send(ws, { type: 'started', file, suite, device, fromStep: startStep });
  startNetworkCapture(ws);

  const args = [
    'test',
    `-Dsurefire.suiteXmlFiles=src/test/resources/suites/${suite}`,
    `-DtestFile=${fileName}`,
    `-DdeviceSerial=${device}`,
    '--no-transfer-progress',
  ];

  if (startStep) {
    args.push(`-DfromStep=${startStep}`);
  }

  console.log(`Running: mvn ${args.join(' ')}`);

  const mvn = spawn('mvn', args, {
    cwd     : FORGE_ROOT,
    shell   : true,
    detached: true,          // creates a new process group so kill(-pid) kills mvn + forked JVM
    env     : { ...process.env }
  });
  mvn.unref();               // don't keep the Node event loop alive for this child

  currentRun = mvn;

  let buffer = '';

  mvn.stdout.on('data', (chunk) => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop(); // hold incomplete line

    for (const line of lines) {
      processLine(line.trim(), ws);
    }
  });

  mvn.stderr.on('data', (chunk) => {
    const text = chunk.toString().trim();
    if (text) send(ws, { type: 'log', level: 'error', line: text });
  });

  mvn.on('close', (code) => {
    if (buffer.trim()) processLine(buffer.trim(), ws);
    stopNetworkCapture();
    currentRun = null;
    if (runWasStopped) {
      // User clicked Stop — already sent 'stopped'; don't show BUILD FAILURE
      runWasStopped = false;
      return;
    }
    send(ws, { type: 'done', success: code === 0, code });
  });

  mvn.on('error', (err) => {
    send(ws, { type: 'log', level: 'error', line: 'Failed to start mvn: ' + err.message });
    send(ws, { type: 'done', success: false });
  });
}

let lastRunningStepIdx = -1;  // tracks which top-level step is currently running

// ── OkHttp network capture ────────────────────────────────────────────────────

let networkLogcat  = null;    // spawned adb logcat process
let networkBuffer  = '';      // incomplete logcat line
let currentRequest = null;    // request being accumulated
let currentResponse = null;   // response being accumulated

function startNetworkCapture(ws) {
  stopNetworkCapture();
  networkBuffer   = '';
  currentRequest  = null;
  currentResponse = null;

  const device = getConnectedDevice();
  // okhttp.OkHttpClient logs at INFO (survives MediaTek DEBUG suppression).
  // OkHttp:D is the HttpLoggingInterceptor tag — kept as fallback for non-MediaTek devices.
  const tagFilters = ['OkHttp:D', 'okhttp.OkHttpClient:I'];
  const args = [
    ...(device ? ['-s', device] : []),
    'logcat', '-s', ...tagFilters, '-v', 'brief',
  ];

  networkLogcat = spawn('adb', args);

  networkLogcat.stdout.on('data', (chunk) => {
    networkBuffer += chunk.toString();
    const lines = networkBuffer.split('\n');
    networkBuffer = lines.pop();
    for (const raw of lines) {
      // logcat brief format: "PRIORITY/TAG(PID): message"
      // matches both OkHttp and okhttp.OkHttpClient tags
      const m = raw.match(/^[DVIWE]\/(?:OkHttp|okhttp\.OkHttpClient)\s*\(\s*\d+\):\s*(.*)/);
      if (!m) continue;
      processOkHttpLine(m[1], ws);
    }
  });

  networkLogcat.on('close', () => { networkLogcat = null; });
  networkLogcat.on('error', () => { networkLogcat = null; });
}

function stopNetworkCapture() {
  if (networkLogcat) {
    try { networkLogcat.kill('SIGTERM'); } catch (_) {}
    networkLogcat = null;
  }
}

/**
 * Infer a human-readable endpoint label from request/response bodies.
 * URLs are redacted (***) by the app's logging interceptor, so we pattern-match
 * on body content to identify what the call actually is.
 */
function inferEndpointLabel(method, reqBody, resBody, status) {
  // ── Response body patterns ────────────────────────────────────────────────
  if (resBody.includes('jwt_access_token'))                        return 'Login / OTP verify';
  if (resBody.includes('"otp_sent"') || resBody.includes('OTP sent')) return 'OTP send';
  if (resBody.includes('"Events processed successfully"'))         return 'Analytics events';
  if (resBody.includes('talsec') || resBody.includes('security_threat')) return 'Talsec security';
  if (resBody.includes('"cart_id"') || resBody.includes('"cart":{')) return 'Cart';
  if (resBody.includes('"order_summary"') || resBody.includes('Continue to checkout')) return 'Checkout';
  if (resBody.includes('"order_id"') || resBody.includes('"order_status"')) return 'Order';
  if (resBody.includes('"results"') && resBody.includes('"name"') && resBody.includes('"price"')) return 'Search PLP';
  if (resBody.includes('"product_id"') || resBody.includes('"pdp"')) return 'Product (PDP)';
  if (resBody.includes('"wishlist"'))                              return 'Wishlist';
  if (resBody.includes('"address"') && method === 'POST')         return 'Add address';
  if (resBody.includes('"profile"') || resBody.includes('"user_name"')) return 'User profile';
  if (resBody.includes('"token"') && !resBody.includes('jwt'))    return 'Auth token';
  if (resBody.includes('Signature verification failed'))          return 'Payment auth (401)';
  if (resBody.includes('"payment"') || resBody.includes('"razorpay"')) return 'Payment';

  // ── Request body patterns (fallback) ─────────────────────────────────────
  if (reqBody.includes('"query"'))                                 return 'Search';
  if (reqBody.includes('merchantId') || reqBody.includes('merchantChannelId')) return 'Payment SDK';
  if (reqBody.includes('"mobile_number"') || reqBody.includes('"otp"')) return 'Login / OTP';
  if (reqBody.includes('"event_name"'))                           return 'Analytics';
  if (reqBody.includes('"cart_item"') || reqBody.includes('"item_id"')) return 'Cart action';

  return null;  // no label — UI will show *** as-is
}

function processOkHttpLine(msg, ws) {
  // ── Request start: "--> METHOD URL"
  // URL may be a real https:// URL or redacted as *** by the app's logging interceptor
  if (msg.startsWith('--> ') && !msg.startsWith('--> END')) {
    const m = msg.match(/^--> (\w+) (\S+)(.*)/);
    if (m) {
      currentRequest = {
        method   : m[1],
        url      : m[2],   // may be *** if app redacts URLs
        headers  : [],
        body     : [],
        bodyNote : m[3].trim() || null,
      };
      currentResponse = null;
    }
    return;
  }

  // ── Request end: "--> END METHOD (N-byte body)"
  if (msg.startsWith('--> END ')) {
    return;
  }

  // ── Response start: "<-- STATUS URL (Nms)"
  // URL may be *** — match any non-space token
  if (msg.startsWith('<-- ') && !msg.startsWith('<-- END')) {
    const m = msg.match(/^<-- (\d+) (\S+) \((\d+)ms\)/);
    if (m && currentRequest) {
      // Prefer the real URL captured at request time if it looks like a URL
      const url = (currentRequest.url && currentRequest.url.startsWith('http'))
        ? currentRequest.url
        : m[2];
      currentResponse = {
        status  : parseInt(m[1], 10),
        url,
        ms      : parseInt(m[3], 10),
        headers : [],
        body    : [],
      };
    }
    return;
  }

  // ── Response end: "<-- END HTTP (N-byte body)" or "<-- END HTTP"
  if (msg.startsWith('<-- END HTTP') || msg === '<-- END HTTP') {
    if (currentRequest && currentResponse) {
      const reqBody = currentRequest.body.join('\n').trim();
      const resBody = currentResponse.body.join('\n').trim();
      const call = {
        type        : 'network_call',
        method      : currentRequest.method,
        url         : currentRequest.url,
        label       : inferEndpointLabel(currentRequest.method, reqBody, resBody, currentResponse.status),
        status      : currentResponse.status,
        ms          : currentResponse.ms,
        reqHeaders  : currentRequest.headers.join('\n'),
        reqBody,
        resHeaders  : currentResponse.headers.join('\n'),
        resBody,
      };
      send(ws, call);
    }
    currentRequest  = null;
    currentResponse = null;
    return;
  }

  // ── Accumulate headers / body
  if (currentResponse) {
    if (msg === '') { currentResponse._bodyStarted = true; return; }
    if (currentResponse._bodyStarted) {
      currentResponse.body.push(msg);
    } else {
      currentResponse.headers.push(msg);
    }
  } else if (currentRequest) {
    if (msg === '') { currentRequest._bodyStarted = true; return; }
    if (currentRequest._bodyStarted) {
      currentRequest.body.push(msg);
    } else {
      // Grab Host header to reconstruct URL when it was redacted
      if (currentRequest.url && !currentRequest.url.startsWith('http')) {
        const hostMatch = msg.match(/^[Hh]ost:\s*(\S+)/);
        if (hostMatch) currentRequest.url = 'https://' + hostMatch[1];
      }
      currentRequest.headers.push(msg);
    }
  }
}

function processLine(line, ws) {
  if (!line) return;

  // Always forward raw log
  send(ws, { type: 'log', line });

  // [STEP] [1] tap  — top-level step starts
  const stepMatch = line.match(/\[STEP\]\s+\[(\d+)\]\s+(.+)/);
  if (stepMatch) {
    lastRunningStepIdx = parseInt(stepMatch[1], 10) - 1;
    send(ws, {
      type   : 'step_update',
      index  : lastRunningStepIdx,
      status : 'running',
      action : stepMatch[2].trim()
    });
    return;
  }

  // [PASS] Step 1 passed
  const passMatch = line.match(/\[PASS\]\s+Step\s+(\d+)/);
  if (passMatch) {
    send(ws, {
      type  : 'step_update',
      index : parseInt(passMatch[1], 10) - 1,
      status: 'passed'
    });
    return;
  }

  // [FAIL] Step 1 failed: ...
  const failMatch = line.match(/\[FAIL\]\s+Step\s+(\d+)\s+failed:\s*(.*)/);
  if (failMatch) {
    const stepNum  = parseInt(failMatch[1], 10);
    const errorMsg = failMatch[2].trim();
    // Send failure immediately so UI updates without waiting for screenshot
    send(ws, { type: 'step_update', index: stepNum - 1, status: 'failed', error: errorMsg });
    // Capture screenshot async — sends a second update ~1 s later with thumbnail URL
    captureFailureShot(stepNum).then(url => {
      if (url) send(ws, { type: 'step_update', index: stepNum - 1,
        status: 'failed', error: errorMsg, failureScreenshot: url });
    });
    return;
  }

  // Retry notice
  const retryMatch = line.match(/Retry step (\d+) attempt (\d+)/);
  if (retryMatch) {
    send(ws, {
      type   : 'step_update',
      index  : parseInt(retryMatch[1], 10) - 1,
      status : 'retrying',
      attempt: parseInt(retryMatch[2], 10)
    });
    return;
  }

  // [STEP] [F:1] tap element_name  — flow sub-step starts
  const flowStepMatch = line.match(/\[STEP\]\s+\[F:(\d+)\]\s+(.*)/);
  if (flowStepMatch && lastRunningStepIdx >= 0) {
    send(ws, {
      type       : 'substep_update',
      parentIndex: lastRunningStepIdx,
      subIndex   : parseInt(flowStepMatch[1], 10) - 1,
      status     : 'running',
      action     : flowStepMatch[2].trim()
    });
    return;
  }

  // [PASS] F:1 passed  — flow sub-step passed
  const flowPassMatch = line.match(/\[PASS\]\s+F:(\d+)/);
  if (flowPassMatch && lastRunningStepIdx >= 0) {
    send(ws, {
      type       : 'substep_update',
      parentIndex: lastRunningStepIdx,
      subIndex   : parseInt(flowPassMatch[1], 10) - 1,
      status     : 'passed'
    });
    return;
  }

  // [FAIL] F:1 failed: ...  — flow sub-step failed
  const flowFailMatch = line.match(/\[FAIL\]\s+F:(\d+)\s+failed:\s*(.*)/);
  if (flowFailMatch && lastRunningStepIdx >= 0) {
    send(ws, {
      type       : 'substep_update',
      parentIndex: lastRunningStepIdx,
      subIndex   : parseInt(flowFailMatch[1], 10) - 1,
      status     : 'failed',
      error      : flowFailMatch[2].trim()
    });
    return;
  }

  // Self-heal
  if (line.includes('[SelfHeal]')) {
    send(ws, { type: 'heal', line });
  }
}

const REPORTS_ROOT   = path.join(FORGE_ROOT, 'reports');
const ELEMENTS_ROOT  = path.join(FORGE_ROOT, 'src/test/resources/testdata/elements');
const FAILURE_DIR    = path.join(REPORTS_ROOT, 'failures');

// ── Element helpers ───────────────────────────────────────────────────────────

function loadAllElementKeys() {
  const keys = new Set();
  if (!fs.existsSync(ELEMENTS_ROOT)) return keys;
  for (const f of fs.readdirSync(ELEMENTS_ROOT)) {
    if (!f.endsWith('.yaml') && !f.endsWith('.yml')) continue;
    try {
      const parsed = yaml.load(fs.readFileSync(path.join(ELEMENTS_ROOT, f), 'utf8'));
      if (parsed && typeof parsed === 'object') Object.keys(parsed).forEach(k => keys.add(k));
    } catch (_) {}
  }
  return keys;
}

// Returns { elementKey → firstAndroidLocatorValue } across all element YAMLs
function loadElementLocatorMap() {
  const map = {};
  if (!fs.existsSync(ELEMENTS_ROOT)) return map;
  for (const f of fs.readdirSync(ELEMENTS_ROOT)) {
    if (!f.endsWith('.yaml') && !f.endsWith('.yml')) continue;
    try {
      const parsed = yaml.load(fs.readFileSync(path.join(ELEMENTS_ROOT, f), 'utf8'));
      if (!parsed || typeof parsed !== 'object') continue;
      for (const [key, def] of Object.entries(parsed)) {
        if (!def) continue;
        const locators = def.android || def.ios || [];
        if (Array.isArray(locators) && locators.length > 0 && locators[0].value) {
          map[key] = locators[0].value;
        }
      }
    } catch (_) {}
  }
  return map;
}

// GET /api/all-elements — flat sorted list of every element key across all element YAMLs
app.get('/api/all-elements', (req, res) => {
  try {
    res.json([...loadAllElementKeys()].sort());
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/elements-full — all elements across every YAML file, flat + annotated
app.get('/api/elements-full', (req, res) => {
  try {
    if (!fs.existsSync(ELEMENTS_ROOT)) return res.json({ ok: true, elements: [], files: [] });
    const files = fs.readdirSync(ELEMENTS_ROOT)
      .filter(f => f.endsWith('.yaml') || f.endsWith('.yml')).sort();
    const result = [];
    for (const filename of files) {
      try {
        const parsed = yaml.load(fs.readFileSync(path.join(ELEMENTS_ROOT, filename), 'utf8'));
        if (!parsed || typeof parsed !== 'object') continue;
        for (const [key, def] of Object.entries(parsed)) {
          if (!def) continue;
          const al = Array.isArray(def.android) ? def.android : (def.android ? [def.android] : []);
          const il = Array.isArray(def.ios)     ? def.ios     : (def.ios     ? [def.ios]     : []);
          result.push({
            file        : filename,
            key,
            android     : al[0]?.value     || null,
            androidType : al[0]?.type      || 'accessibilityId',
            ios         : il[0]?.value     || null,
            iosType     : il[0]?.type      || 'accessibilityId',
          });
        }
      } catch (_) {}
    }
    result.sort((a, b) => a.key.localeCompare(b.key));
    res.json({ ok: true, elements: result, files });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// POST /api/save-element — add or update one element in an elements YAML file
app.post('/api/save-element', (req, res) => {
  const { file, key, android, androidType = 'accessibilityId', ios, iosType = 'accessibilityId', oldKey } = req.body;
  if (!file || !key) return res.status(400).json({ ok: false, error: 'file and key required' });
  if (!/^[a-zA-Z0-9_]+$/.test(key)) return res.status(400).json({ ok: false, error: 'key must be alphanumeric/underscore only' });
  if (!android && !ios)  return res.status(400).json({ ok: false, error: 'at least one locator (android or ios) required' });

  const fullPath = path.join(ELEMENTS_ROOT, file);
  if (!fullPath.startsWith(ELEMENTS_ROOT)) return res.status(403).json({ ok: false, error: 'Forbidden' });
  try {
    let parsed = {};
    if (fs.existsSync(fullPath)) {
      try { parsed = yaml.load(fs.readFileSync(fullPath, 'utf8')) || {}; } catch (_) {}
    }
    // If renaming (oldKey != key), remove old entry
    if (oldKey && oldKey !== key && parsed[oldKey] !== undefined) {
      delete parsed[oldKey];
    }
    const def = {};
    if (android) def.android = [{ type: androidType, value: android }];
    if (ios)     def.ios     = [{ type: iosType,     value: ios }];
    parsed[key] = def;
    const sorted     = Object.fromEntries(Object.entries(parsed).sort(([a], [b]) => a.localeCompare(b)));
    const newContent = yaml.dump(sorted, { lineWidth: -1, quotingType: '"' });
    fs.mkdirSync(path.dirname(fullPath), { recursive: true });
    fs.writeFileSync(fullPath, newContent, 'utf8');
    _reverseMapCache = null;   // bust cache
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// POST /api/delete-element — remove one element key from an elements YAML file
app.post('/api/delete-element', (req, res) => {
  const { file, key } = req.body;
  if (!file || !key) return res.status(400).json({ error: 'file and key required' });
  const fullPath = path.join(ELEMENTS_ROOT, file);
  if (!fullPath.startsWith(ELEMENTS_ROOT)) return res.status(403).json({ error: 'Forbidden' });
  if (!fs.existsSync(fullPath)) return res.status(404).json({ error: 'File not found' });
  try {
    const parsed = yaml.load(fs.readFileSync(fullPath, 'utf8')) || {};
    if (!parsed[key]) return res.json({ ok: true, noop: true });
    delete parsed[key];
    fs.writeFileSync(fullPath, yaml.dump(parsed, { lineWidth: -1, quotingType: '"' }), 'utf8');
    _reverseMapCache = null;
    res.json({ ok: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// GET /api/elements-usage — missing (used in tests, not defined) + unused (defined, never used)
app.get('/api/elements-usage', (req, res) => {
  try {
    const knownKeys = loadAllElementKeys();
    const testFiles = walkYaml(TESTDATA_ROOT);
    const usageMap  = {};   // key → [file, ...]
    for (const file of testFiles) {
      try {
        const parsed = yaml.load(fs.readFileSync(path.join(TESTDATA_ROOT, file), 'utf8'));
        for (const s of (parsed.steps || [])) {
          if (s.element) {
            if (!usageMap[s.element]) usageMap[s.element] = [];
            if (!usageMap[s.element].includes(file)) usageMap[s.element].push(file);
          }
        }
      } catch (_) {}
    }
    const missing = Object.entries(usageMap)
      .filter(([k]) => !knownKeys.has(k))
      .map(([key, usedIn]) => ({ key, usedIn }))
      .sort((a, b) => a.key.localeCompare(b.key));

    const elementFiles = fs.existsSync(ELEMENTS_ROOT)
      ? fs.readdirSync(ELEMENTS_ROOT).filter(f => f.endsWith('.yaml') || f.endsWith('.yml')) : [];
    const unused = [...knownKeys]
      .filter(k => !usageMap[k])
      .map(key => {
        let definedIn = '';
        for (const f of elementFiles) {
          try {
            const p = yaml.load(fs.readFileSync(path.join(ELEMENTS_ROOT, f), 'utf8'));
            if (p && p[key]) { definedIn = f; break; }
          } catch (_) {}
        }
        return { key, definedIn };
      })
      .sort((a, b) => a.key.localeCompare(b.key));

    res.json({ ok: true, missing, unused, usageMap });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// GET /api/untagged-check — screen elements (contentDesc/resourceId) with NO registered element key
app.get('/api/untagged-check', async (req, res) => {
  try {
    // Build a set of all known locator values from elements YAMLs
    const locatorMap = loadElementLocatorMap();   // key → locatorValue
    const registeredLocators = new Set(Object.values(locatorMap));

    const xml   = await dumpXml();
    const nodes = parseNodes(xml);

    // Collect unique locator values from the screen that have no registered element key
    const seen    = new Set();
    const untagged = [];
    for (const n of nodes) {
      const locatorVal = n.contentDesc || (n.resourceId ? n.resourceId.split('/').pop() : '');
      if (!locatorVal || seen.has(locatorVal)) continue;
      seen.add(locatorVal);
      if (!registeredLocators.has(locatorVal)) {
        untagged.push({ locatorVal });
      }
    }
    untagged.sort((a, b) => a.locatorVal.localeCompare(b.locatorVal));

    res.json({ ok: true, untagged, found: seen.size - untagged.length,
               total: seen.size, screenNodes: nodes.length });
  } catch (e) { res.status(500).json({ ok: false, error: e.message }); }
});

// GET /api/validate-test?file= — check each element: value exists in element YAMLs
app.get('/api/validate-test', (req, res) => {
  const file = req.query.file;
  if (!file) return res.status(400).json({ error: 'file required' });
  try {
    const known = loadAllElementKeys();
    const NEED_ELEMENT = new Set([
      'tap','waitFor','verifyElement','enterText','scrollTo','longPress',
      'swipeUp','swipeDown','assertText','clearText','captureText','assertStoredText',
    ]);
    const parsed = yaml.load(fs.readFileSync(path.join(TESTDATA_ROOT, file), 'utf8'));
    const errors = (parsed.steps || [])
      .map((s, i) => ({ s, i }))
      .filter(({ s }) => s.element && NEED_ELEMENT.has(s.action) && !known.has(s.element))
      .map(({ s, i }) => ({ stepIndex: i, element: s.element,
        message: `"${s.element}" not found in any element YAML` }));
    res.json({ valid: errors.length === 0, errors });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Failure screenshot capture ────────────────────────────────────────────────

function captureFailureShot(stepNum) {
  return new Promise(resolve => {
    try { fs.mkdirSync(FAILURE_DIR, { recursive: true }); } catch (_) {}
    const fname = `step_${String(stepNum).padStart(2, '0')}.jpg`;
    const dest  = path.join(FAILURE_DIR, fname);
    const tmp   = dest.replace('.jpg', '_tmp.png');
    const adb   = spawn('adb', adbArgs('exec-out', 'screencap', '-p'));
    const chunks = [];
    adb.stdout.on('data', c => chunks.push(c));
    adb.on('close', code => {
      if (code !== 0 || !chunks.length) return resolve(null);
      fs.writeFile(tmp, Buffer.concat(chunks), err => {
        if (err) return resolve(null);
        exec(`sips -s format jpeg -s formatOptions 65 "${tmp}" --out "${dest}" 2>/dev/null`,
          { timeout: 4000 }, sipsErr => {
            fs.unlink(tmp, () => {});
            resolve(sipsErr ? null : `/api/failure-shot/${fname}`);
          });
      });
    });
    adb.on('error', () => resolve(null));
  });
}

app.get('/api/failure-shot/:file', (req, res) => {
  const p = path.join(FAILURE_DIR, path.basename(req.params.file));
  if (!fs.existsSync(p)) return res.status(404).end();
  res.sendFile(p);
});

// ── Tag Sync helpers ─────────────────────────────────────────────────────────

// Generate a meaningful tag name suggestion from an element's properties
function suggestTagName(el) {
  if (el.text && el.text.trim().length > 1) {
    const clean = el.text.trim()
      .replace(/[^a-z0-9\s]/gi, ' ')
      .trim()
      .replace(/\s+/g, '_')
      .toLowerCase()
      .replace(/_+/g, '_')
      .slice(0, 32);
    if (clean.length > 1) return clean;
  }
  if (el.resourceId) {
    const id = el.resourceId.split('/').pop()
      .replace(/[^a-z0-9]/gi, '_')
      .toLowerCase()
      .replace(/_+/g, '_')
      .slice(0, 32);
    if (id.length > 1) return id;
  }
  return null;
}

// Returns the fraction of node n's area that is covered by tagged node t
function overlapFraction(n, t) {
  const ox = Math.max(0, Math.min(n.x2, t.x2) - Math.max(n.x1, t.x1));
  const oy = Math.max(0, Math.min(n.y2, t.y2) - Math.max(n.y1, t.y1));
  const nodeArea = (n.x2 - n.x1) * (n.y2 - n.y1);
  return nodeArea > 0 ? (ox * oy) / nodeArea : 0;
}

// GET /api/untagged-elements — interactive elements on screen that have NO qaTestTag
app.get('/api/untagged-elements', async (req, res) => {
  try {
    const xml   = await dumpXml();
    const nodes = parseNodes(xml);

    // Collect all nodes that already have a real qaTestTag (non-empty contentDesc)
    const tagged = nodes.filter(n => n.contentDesc);

    const seen = new Set();
    const candidates = [];

    for (const n of nodes) {
      // Skip elements that already have a qaTestTag
      if (n.contentDesc) continue;
      // Skip empty elements with no useful identifier
      if (!n.text && !n.resourceId) continue;
      // Skip very large containers (full-screen wrappers etc)
      const area = (n.x2 - n.x1) * (n.y2 - n.y1);
      if (area > 600000) continue;

      const cls = (n.className || '').toLowerCase();
      const isInteractive = n.clickable || n.scrollable;
      const isTextOrInput = cls.includes('text') || cls.includes('edit') ||
                            cls.includes('button') || cls.includes('image') ||
                            cls.includes('recycler');
      if (!isInteractive && !isTextOrInput) continue;

      // Skip child text nodes that sit inside an already-tagged sibling/parent of
      // comparable size (e.g. the "Shop" TextView inside common_shop_tab).
      // BUT do NOT filter if the tagged element is a large container (e.g. a full-screen
      // root like search_products_screen_root) — those are ancestors, not the same widget.
      const nArea = (n.x2 - n.x1) * (n.y2 - n.y1);
      const coveredByTag = tagged.some(t => {
        const tArea = (t.x2 - t.x1) * (t.y2 - t.y1);
        // Only suppress if the tagged element is at most 5× larger than this node
        if (tArea > nArea * 5) return false;
        return overlapFraction(n, t) >= 0.70;
      });
      if (coveredByTag) continue;

      const key = (n.text + '|' + n.resourceId).trim();
      if (!key || key === '|') continue;
      if (seen.has(key)) continue;
      seen.add(key);

      const suggested = suggestTagName(n);
      if (!suggested) continue;

      candidates.push({
        text       : n.text,
        resourceId : n.resourceId,
        className  : n.className.split('.').pop(),
        bounds     : n.bounds,
        x1: n.x1, y1: n.y1, x2: n.x2, y2: n.y2,
        suggested,
      });
    }

    res.json(candidates);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/apply-missing-tags — write rich YAML context + run `claude /qa-tags` in popdroid
let tagSyncProc = null; // track so we can cancel

app.post('/api/apply-missing-tags', (req, res) => {
  const { elements } = req.body;  // [{tagName, text, resourceId, className, bounds}]
  if (!Array.isArray(elements) || !elements.length) {
    return res.status(400).json({ error: 'elements[] required' });
  }

  // Write a rich YAML file with all the context Claude needs
  const ts       = Date.now();
  const tagDir   = path.join(REPORTS_ROOT, 'tag_sync');
  fs.mkdirSync(tagDir, { recursive: true });

  let yamlOut = `# Missing qaTestTags — generated by Forge UI\n`;
  yamlOut    += `# ${new Date().toISOString()}\n`;
  yamlOut    += `#\n`;
  yamlOut    += `# For each entry: find the Compose component that renders this element and add\n`;
  yamlOut    += `#   Modifier.qaTestTag("<suggestedTag>")  (or .testTag if qaTestTag is unavailable)\n`;
  yamlOut    += `# Elements are from the current device screen via uiautomator dump.\n\n`;
  yamlOut    += `missing:\n`;

  for (const el of elements) {
    yamlOut += `  - suggestedTag: "${el.tagName}"\n`;
    if (el.text)       yamlOut += `    visibleText: "${(el.text || '').replace(/"/g, '\\"')}"\n`;
    if (el.resourceId) yamlOut += `    resourceId:  "${el.resourceId}"\n`;
    if (el.className)  yamlOut += `    className:   "${el.className}"\n`;
    if (el.bounds)     yamlOut += `    bounds:      "${el.bounds}"\n`;
    yamlOut += '\n';
  }

  const missingFile = path.join(tagDir, `missing_tags_${ts}.yaml`);
  fs.writeFileSync(missingFile, yamlOut, 'utf8');
  const relFile = path.relative(FORGE_ROOT, missingFile);

  const popdroid = path.join(FORGE_ROOT, '..', 'popdroid');
  const args     = ['/qa-tags', missingFile];

  broadcast({ type: 'log', level: 'step', line: `▶ claude /qa-tags — adding ${elements.length} tag(s) in popdroid…` });
  broadcast({ type: 'log', line: `📄 context: ${relFile}` });

  // Kill any previous tag sync that might still be running
  if (tagSyncProc) { try { tagSyncProc.kill('SIGTERM'); } catch (_) {} tagSyncProc = null; }

  const proc = spawn('claude', args, {
    cwd  : popdroid,
    shell: true,
    env  : { ...process.env }
  });
  tagSyncProc = proc;

  // 10-minute hard timeout — prevents infinite stuck state
  const timeout = setTimeout(() => {
    if (tagSyncProc === proc) {
      try { proc.kill('SIGTERM'); } catch (_) {}
      tagSyncProc = null;
      broadcast({ type: 'log', level: 'error', line: '⏱ qa-tags timed out after 10 minutes — killed' });
      broadcast({ type: 'qa_tags_done', success: false });
    }
  }, 10 * 60 * 1000);

  proc.stdout.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(line =>
      broadcast({ type: 'log', line })
    );
  });
  proc.stderr.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(line =>
      broadcast({ type: 'log', level: 'error', line })
    );
  });
  proc.on('close', code => {
    clearTimeout(timeout);
    tagSyncProc = null;
    const ok = code === 0;
    broadcast({ type: 'log', line: ok ? '✅ Tags applied in popdroid' : `❌ claude exited ${code}` });
    broadcast({ type: 'qa_tags_done', success: ok });
  });
  proc.on('error', e => {
    clearTimeout(timeout);
    tagSyncProc = null;
    broadcast({ type: 'log', level: 'error', line: 'Failed to start claude: ' + e.message });
    broadcast({ type: 'qa_tags_done', success: false });
  });

  res.json({ ok: true, missingFile: relFile, count: elements.length });
});

// POST /api/cancel-tags — kill a stuck tag sync
app.post('/api/cancel-tags', (req, res) => {
  if (tagSyncProc) {
    try { tagSyncProc.kill('SIGTERM'); } catch (_) {}
    tagSyncProc = null;
    broadcast({ type: 'log', level: 'error', line: '⏹ Tag sync cancelled by user' });
    broadcast({ type: 'qa_tags_done', success: false });
    res.json({ ok: true });
  } else {
    res.json({ ok: false, message: 'No tag sync running' });
  }
});

// ── Chat (claude --output-format stream-json) ────────────────────────────────
// Uses structured JSON streaming so we can detect MCP tool calls and show
// live status steps in the UI — same idea as Maestro's streaming feedback.
// Session continuity: captures session_id from each run and uses --resume on
// follow-up messages so Claude remembers the full conversation context.
let chatProc         = null;
let chatSessionId    = null;   // persists across messages in the same UI session
let chatLastTestFile = null;   // last test file mentioned in this session

// Human-readable labels for MCP tool calls
const TOOL_LABELS = {
  forge_get_hierarchy      : '🔍 Scanning device screen…',
  forge_run_test           : '▶ Running test on device…',
  forge_heal_step          : '🔧 Healing broken step…',
  forge_save_test          : '💾 Saving test to androidTests…',
  forge_read_test          : '📄 Reading existing test…',
  forge_device_screenshot  : '📸 Taking screenshot…',
  forge_device_tap         : '👆 Tapping element…',
  forge_device_type        : '⌨️  Typing text…',
  forge_device_key         : '🔑 Pressing key…',
  forge_device_swipe       : '👆 Swiping…',
  forge_device_launch      : '🚀 Launching app…',
  forge_list_tests         : '📋 Listing tests…',
  forge_validate_test      : '✅ Validating elements…',
};

// Build a structured context preamble injected into every chat prompt
function buildContextPreamble(context) {
  if (!context) return '';
  const lines = [];

  // Active test + YAML content
  if (context.activeTest) {
    lines.push(`ACTIVE TEST: "${context.activeTest}"${context.testName ? ` — "${context.testName}"` : ''}`);
    try {
      const fullPath = path.join(TESTDATA_ROOT, context.activeTest);
      const content  = fs.readFileSync(fullPath, 'utf8');
      lines.push(`\nTEST YAML:\n\`\`\`yaml\n${content}\n\`\`\``);
    } catch (_) { /* file not found — skip */ }
  }

  // Last run result + failed steps
  if (context.lastResult) {
    lines.push(`\nLAST RUN: ${context.lastResult === 'pass' ? '✅ PASSED' : '❌ FAILED'}`);
  }
  if (context.failedSteps?.length) {
    lines.push('FAILED STEPS:');
    for (const s of context.failedSteps) {
      lines.push(`  Step ${s.index + 1} — action: ${s.action}${s.element ? `, element: ${s.element}` : ''}${s.error ? `\n    Error: ${s.error}` : ''}`);
    }
  }

  // Current screen elements (captured live via uiautomator dump)
  if (context.screenElements?.length) {
    lines.push(`\nCURRENT SCREEN (${context.screenElements.length} elements from uiautomator):`);
    for (const el of context.screenElements) {
      const name = el.contentDesc || el.text || el.resourceId || '—';
      const cls  = (el.className || '').split('.').pop();
      lines.push(`  • ${name}  [${cls}]  bounds=${el.bounds}${el.clickable ? '  clickable' : ''}`);
    }
  }

  return lines.length
    ? `=== FORGE CONTEXT ===\n${lines.join('\n')}\n=== END CONTEXT ===\n\n`
    : '';
}

// WS message handler for chat (called from WS message handler)
function handleChatMessage(message, context, ws) {
  if (chatProc) { try { chatProc.kill('SIGTERM'); } catch (_) {} chatProc = null; }

  // Resolve claude binary — use absolute path so it works regardless of server PATH
  const claudeBin = process.env.CLAUDE_BIN || 'claude';

  // PATH: ensure Homebrew bin is included (claude is installed there on macOS)
  const env = {
    ...process.env,
    FORCE_COLOR: '0',
    PATH: `${process.env.PATH}:/opt/homebrew/bin:/usr/local/bin`
  };

  const mcpConfig = path.join(FORGE_ROOT, '.claude', 'settings.json');
  const mcpArgs   = fs.existsSync(mcpConfig) ? ['--mcp-config', mcpConfig] : [];

  // Build the prompt — inject active test + UI context so Claude always knows the situation
  const ctxPreamble = buildContextPreamble(context);
  let prompt;
  if (chatSessionId) {
    // Follow-up: resume session + inject context prefix so Claude never asks "which test?"
    const sessionCtx = chatLastTestFile
      ? `CONTEXT: The test we are working on is "${chatLastTestFile}". ` +
        `Any edit/run/fix request refers to THIS file unless explicitly stated otherwise.\n\n`
      : '';
    prompt = sessionCtx + ctxPreamble + message;
    console.log('[chat] resume', chatSessionId, '| active:', chatLastTestFile,
                ctxPreamble ? '| +context' : '');
  } else {
    // New session: if we have structured context (fix flow, from-screen flow) use it directly;
    // otherwise prefix with /generate-test skill so Claude knows to write a test
    if (ctxPreamble) {
      prompt = ctxPreamble + message;
    } else {
      prompt = `/generate-test ${message}`;
    }
    console.log('[chat] new session:', prompt.substring(0, 100));
  }

  const sessionArgs = chatSessionId
    ? ['--resume', chatSessionId]
    : [];

  const spawnArgs = [...sessionArgs, '-p', prompt,
                     '--dangerously-skip-permissions', ...mcpArgs];

  const proc = spawn(claudeBin, spawnArgs, { cwd: FORGE_ROOT, shell: false, env });
  chatProc = proc;

  // Immediate acknowledgment so the UI shows activity during Claude's startup
  broadcast({ type: 'chat_thinking', message });

  let full      = '';
  let stderrBuf = '';

  // ── Silence detector — shows "using tools" when Claude stops typing ───────
  // Claude goes quiet on stdout while MCP tools run. We detect this and show
  // a status indicator rather than leaving the UI frozen.
  let silenceTimer = null;
  let toolActive   = false;

  function resetSilenceTimer() {
    if (silenceTimer) clearTimeout(silenceTimer);
    if (toolActive) { toolActive = false; }
    silenceTimer = setTimeout(() => {
      if (chatProc) {   // still running — Claude is using a tool
        toolActive = true;
        broadcast({ type: 'chat_status', label: '🔧 Using tools…' });
      }
    }, 2000);  // 2 s silence → tool in progress
  }

  proc.stdout.on('data', chunk => {
    const text = chunk.toString();
    full += text;
    resetSilenceTimer();

    // Stream tokens to chat bubble immediately (true per-character streaming)
    broadcast({ type: 'chat_token', token: text });

    // ── Detect tool call labels from stderr-style lines claude prints ─────
    // claude -p prints tool names to stderr; we check stdout too for any
    // embedded markers. Primary detection is via stderr below.

    // ── Detect forge_save_test completion — push file to editor ──────────
    const savedMatch = text.match(/✅ Saved: ([\w/.-]+\.ya?ml)/);
    if (savedMatch) {
      const relPath = savedMatch[1].trim();
      const absPath = path.join(FORGE_ROOT, relPath);
      try {
        const yaml = fs.readFileSync(absPath, 'utf8');
        broadcast({ type: 'chat_file_saved', filePath: relPath, content: yaml });
      } catch (_) {}
    }

    // Track last mentioned .yaml — update immediately so next follow-up has context
    const yamlMentions = (text.match(/[\w/.-]+\.yaml/g) || [])
      .filter(f => !f.includes('element') && !f.includes('testng') && !f.includes('suite'));
    if (yamlMentions.length) {
      const prev = chatLastTestFile;
      chatLastTestFile = yamlMentions[yamlMentions.length - 1];
      if (chatLastTestFile !== prev) {
        broadcast({ type: 'chat_active_test', testFile: chatLastTestFile });
      }
    }
  });

  proc.stderr.on('data', chunk => {
    const text = chunk.toString().replace(/\x1b\[[0-9;]*m/g, '').replace(/\r/g, '');
    stderrBuf += text;
    if (text.trim()) console.log('[chat stderr]', text.trim());

    // claude prints tool use info to stderr — detect and show status labels
    for (const line of text.split('\n')) {
      for (const [toolName, label] of Object.entries(TOOL_LABELS)) {
        if (line.includes(toolName)) {
          broadcast({ type: 'chat_status', label });
          resetSilenceTimer(); // reset so silence timer doesn't double-fire
        }
      }
      // Session ID — claude prints it to stderr on start
      const sidMatch = line.match(/session[_\s]?id[:\s]+([a-f0-9-]{20,})/i);
      if (sidMatch && !chatSessionId) {
        chatSessionId = sidMatch[1].trim();
        console.log('[chat] session from stderr:', chatSessionId);
        broadcast({ type: 'chat_session', sessionId: chatSessionId });
      }
    }
  });
  proc.on('close', code => {
    chatProc = null;
    if (silenceTimer) clearTimeout(silenceTimer);
    console.log('[chat] claude exited', code, '| response length:', full.length);
    if (!full && code !== 0) {
      const detail = stderrBuf.trim() ? `\n\nDetails:\n${stderrBuf.trim()}` : '';
      full = `❌ claude exited with code ${code}.${detail}`;
    }
    // Track last yaml mention across full response
    const allYaml = full.match(/[\w/]+\.yaml/g);
    if (allYaml) chatLastTestFile = allYaml[allYaml.length - 1];
    broadcast({ type: 'chat_done', response: full, success: code === 0 });
  });
  proc.on('error', e => {
    chatProc = null;
    console.error('[chat] spawn error:', e.message);
    broadcast({ type: 'chat_done', response: `❌ Failed to start claude: ${e.message}\n\nMake sure claude CLI is installed: https://claude.ai/download`, success: false });
  });
}

// HTTP fallback (when WS isn't open yet)
app.post('/api/chat', (req, res) => {
  const { message, context } = req.body;
  if (!message) return res.status(400).json({ error: 'message required' });
  handleChatMessage(message, context || null, null);
  res.json({ ok: true });
});

// Reset session — next message starts a fresh Claude conversation
app.post('/api/chat-reset', (req, res) => {
  chatSessionId    = null;
  chatLastTestFile = null;
  res.json({ ok: true });
});

app.post('/api/cancel-chat', (req, res) => {
  if (chatProc) {
    try { chatProc.kill('SIGTERM'); } catch (_) {}
    chatProc = null;
    broadcast({ type: 'chat_done', response: '', success: false });
    res.json({ ok: true });
  } else {
    res.json({ ok: false });
  }
});

// ── Recorder ─────────────────────────────────────────────────────────────────
let recorderProcess    = null;
let recorderSessionDir = null;

app.post('/api/recorder/start', (req, res) => {
  if (recorderProcess) return res.status(400).json({ error: 'Already recording' });
  recorderSessionDir = null; // clear stale session from previous recording

  const device = getConnectedDevice();
  const ws     = [...wss.clients].find(c => c.readyState === WebSocket.OPEN);
  const rlog   = (line, level = '') => ws && send(ws, { type: 'recorder_log', line, level });

  rlog('▶ Starting recorder…', 'step');

  // Run ForgeRecorder.java directly via java — avoids Maven exec:java which swallows stdin
  const javaArgs = ['-cp', 'target/classes', 'com.popclub.ai.app.ForgeRecorder'];
  if (device) javaArgs.push(device);

  recorderProcess = spawn('java', javaArgs, { cwd: FORGE_ROOT, shell: false });

  let buf = '';
  recorderProcess.stdout.on('data', chunk => {
    buf += chunk.toString();
    const lines = buf.split('\n');
    buf = lines.pop();
    for (const raw of lines) {
      const line = raw.trim();
      if (!line) continue;
      rlog(line);
      // Detect session directory — Recorder prints "SESSION_DIR: /absolute/path"
      const sdirM = line.match(/^SESSION_DIR:\s*(.+)/);
      if (sdirM) {
        recorderSessionDir = sdirM[1].trim();
        console.log('[recorder] session dir:', recorderSessionDir);
      }
      // Parse recorded step lines — forward as recorder_step events so UI can show live feed
      // Recorder prints lines like:  [REC] tap  some_element  →  {"action":"tap","element":"some_element",...}
      const recM = line.match(/^\[REC\]\s+(\w+)\s+(.*)/);
      if (recM) {
        ws && send(ws, { type: 'recorder_step', action: recM[1], detail: recM[2].trim() });
      }
    }
  });
  recorderProcess.stderr.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(l => {
      // Filter Maven noise — only forward meaningful lines
      rlog(l, 'error');
    });
  });
  recorderProcess.on('close', code => {
    recorderProcess = null;
    ws && send(ws, { type: 'recorder_stopped', sessionDir: recorderSessionDir });
  });
  recorderProcess.on('error', e => rlog('Failed to start recorder: ' + e.message, 'error'));

  res.json({ ok: true, device: device || 'default' });
});

app.post('/api/recorder/stop', (req, res) => {
  if (!recorderProcess) return res.status(400).json({ error: 'Not recording' });
  const proc = recorderProcess;
  // Primary: send exit via stdin (Recorder reads it, drains tap executor, then saves YAML)
  try { proc.stdin.write('exit\n'); proc.stdin.end(); } catch (_) {}
  // Backup: if stdin doesn't complete save within 5s, SIGTERM triggers the shutdown hook (also saves)
  setTimeout(() => { try { proc.kill('SIGTERM'); } catch (_) {} }, 5000);
  res.json({ ok: true });
});

app.post('/api/recorder/pause', (req, res) => {
  if (!recorderProcess) return res.status(400).json({ error: 'Not recording' });
  try { recorderProcess.stdin.write('pause\n'); } catch (_) {}
  res.json({ ok: true });
});

app.post('/api/recorder/resume', (req, res) => {
  if (!recorderProcess) return res.status(400).json({ error: 'Not recording' });
  try { recorderProcess.stdin.write('resume\n'); } catch (_) {}
  res.json({ ok: true });
});

app.post('/api/recorder/scan', (req, res) => {
  if (!recorderProcess) return res.status(400).json({ error: 'Not recording' });
  try { recorderProcess.stdin.write('scan\n'); } catch (_) {}
  res.json({ ok: true });
});

app.get('/api/recorder/output', (req, res) => {
  // Use the session dir captured from SESSION_DIR: output; fall back to most recently created dir
  let dir = recorderSessionDir;
  if (!dir || !fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    const base = path.join(FORGE_ROOT, 'reports/recorded');
    if (fs.existsSync(base)) {
      const dirs = fs.readdirSync(base)
        .map(d => path.join(base, d))
        .filter(p => fs.statSync(p).isDirectory())
        .sort((a, b) => fs.statSync(b).birthtimeMs - fs.statSync(a).birthtimeMs); // newest first by creation time
      if (dirs.length) dir = dirs[0];
    }
  }
  if (!dir) return res.status(404).json({ error: 'No recording found' });
  try {
    const files = fs.readdirSync(dir).filter(f => f.endsWith('.yaml') || f.endsWith('.yml'));
    if (!files.length) return res.status(404).json({ error: 'No YAML generated yet — recording may still be in progress' });
    const content = fs.readFileSync(path.join(dir, files[0]), 'utf8');
    res.json({ content, filename: path.basename(files[0]), dir });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ─── Start ───────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 3847;
server.listen(PORT, () => {
  console.log(`\n  ⚡ Forge UI  →  http://localhost:${PORT}\n`);
});
