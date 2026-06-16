const express = require('express');
const http    = require('http');
const WebSocket = require('ws');
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
const TESTDATA_ROOT  = path.join(FORGE_ROOT, 'src/test/resources/testdata');
const FLOWS_ROOT     = path.join(FORGE_ROOT, 'src/test/resources/flows');
const TESTNG_ROOT    = path.join(FORGE_ROOT, 'src/test/resources/testNg');
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
  if (testFile.includes('shop')) return 'testng-shop.xml';
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
      res.json(best);
    } else {
      res.json({ contentDesc: '', resourceId: '', text: '', className: '', bounds: '', clickable: false });
    }
  } catch (e) {
    res.status(500).json({ error: 'Inspect failed: ' + e.message });
  }
});

// POST /api/device-action — perform ADB input actions on the device (async)
app.post('/api/device-action', async (req, res) => {
  const { type, x, y, x2, y2, text, key, duration } = req.body;
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
      default:
        return res.status(400).json({ error: 'Unknown action: ' + type });
    }
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
    fs.writeFileSync(fullPath, content, 'utf8');
    res.json({ ok: true });
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
noReset: false

features:
  - common
  - login

tags:
  - smoke
retry: 1
steps:
  - action: launchApp

  - action: loginIfNeeded
    value: "1234561122"
    text: "560102"

  # Add steps below
`;
  try {
    fs.mkdirSync(path.dirname(fullPath), { recursive: true });
    fs.writeFileSync(fullPath, template, 'utf8');
    res.json({ ok: true, content: template });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Connected device info
app.get('/api/device', (req, res) => {
  const device = getConnectedDevice();
  res.json({ device });
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

let currentRun = null;

wss.on('connection', (ws) => {
  console.log('UI client connected');

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw); } catch (_) { return; }

    if (msg.type === 'run') {
      startTest(msg.file, msg.device, ws, msg.fromStep);
    } else if (msg.type === 'stop') {
      stopTest(ws);
    }
  });

  ws.on('close', () => console.log('UI client disconnected'));
});

function send(ws, data) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

function stopTest(ws) {
  stopNetworkCapture();
  if (currentRun) {
    currentRun.kill('SIGTERM');
    currentRun = null;
    send(ws, { type: 'stopped' });
  }
}

function startTest(file, deviceOverride, ws, fromStep) {
  if (currentRun) {
    currentRun.kill('SIGTERM');
    currentRun = null;
  }

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
    `-Dsurefire.suiteXmlFiles=src/test/resources/testNg/${suite}`,
    `-DtestFile=${fileName}`,
    `-DdeviceSerial=${device}`,
    '--no-transfer-progress',
  ];

  if (startStep) {
    args.push(`-DfromStep=${startStep}`);
  }

  console.log(`Running: mvn ${args.join(' ')}`);

  const mvn = spawn('mvn', args, {
    cwd  : FORGE_ROOT,
    shell: true,
    env  : { ...process.env }
  });

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
    send(ws, { type: 'done', success: code === 0, code });
    currentRun = null;
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
  const args   = device
    ? ['-s', device, 'logcat', '-s', 'OkHttp:D', '-v', 'brief']
    : ['logcat', '-s', 'OkHttp:D', '-v', 'brief'];

  networkLogcat = spawn('adb', args);

  networkLogcat.stdout.on('data', (chunk) => {
    networkBuffer += chunk.toString();
    const lines = networkBuffer.split('\n');
    networkBuffer = lines.pop();
    for (const raw of lines) {
      // logcat brief format: "D/OkHttp(12345): message"
      const m = raw.match(/^[DVIWE]\/OkHttp\s*\(\s*\d+\):\s*(.*)/);
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

function processOkHttpLine(msg, ws) {
  // ── Request start: "--> METHOD URL" or "--> METHOD URL (N-byte body)"
  if (msg.startsWith('--> ') && !msg.startsWith('--> END')) {
    const m = msg.match(/^--> (\w+) (https?:\/\/\S+)(.*)/);
    if (m) {
      currentRequest = {
        method   : m[1],
        url      : m[2],
        headers  : [],
        body     : [],
        bodyNote : m[3].trim() || null,
      };
      currentResponse = null;
    }
    return;
  }

  // ── Request end: "--> END METHOD" or "--> END METHOD (N-byte body)"
  if (msg.startsWith('--> END ')) {
    // request fully accumulated; wait for response
    return;
  }

  // ── Response start: "<-- STATUS URL (Nms)" or "<-- HTTP/1.1 STATUS URL (Nms)"
  if (msg.startsWith('<-- ') && !msg.startsWith('<-- END')) {
    const m = msg.match(/^<-- (\d+)(?: \S+)? (https?:\/\/\S+) \((\d+)ms\)/);
    if (m && currentRequest) {
      currentResponse = {
        status  : parseInt(m[1], 10),
        url     : m[2],
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
      const call = {
        type    : 'network_call',
        method  : currentRequest.method,
        url     : currentRequest.url,
        status  : currentResponse.status,
        ms      : currentResponse.ms,
        reqHeaders  : currentRequest.headers.join('\n'),
        reqBody     : currentRequest.body.join('\n').trim(),
        resHeaders  : currentResponse.headers.join('\n'),
        resBody     : currentResponse.body.join('\n').trim(),
      };
      send(ws, call);
    }
    currentRequest  = null;
    currentResponse = null;
    return;
  }

  // ── Accumulate headers / body
  if (currentResponse) {
    // After blank line it's body; before blank line it's headers
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
const ELEMENTS_ROOT  = path.join(FORGE_ROOT, 'src/test/resources/elements');
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
app.post('/api/apply-missing-tags', (req, res) => {
  const { elements } = req.body;  // [{tagName, text, resourceId, className, bounds}]
  if (!Array.isArray(elements) || !elements.length) {
    return res.status(400).json({ error: 'elements[] required' });
  }

  const ws = [...wss.clients].find(c => c.readyState === WebSocket.OPEN);
  if (!ws) return res.status(400).json({ error: 'No UI client connected' });

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

  send(ws, { type: 'log', level: 'step', line: `▶ claude /qa-tags — adding ${elements.length} tag(s) in popdroid…` });
  send(ws, { type: 'log', line:  `📄 context: ${relFile}` });

  const proc = spawn('claude', args, {
    cwd  : popdroid,
    shell: true,
    env  : { ...process.env }
  });

  proc.stdout.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(line =>
      send(ws, { type: 'log', line })
    );
  });
  proc.stderr.on('data', chunk => {
    chunk.toString().split('\n').filter(Boolean).forEach(line =>
      send(ws, { type: 'log', level: 'error', line })
    );
  });
  proc.on('close', code => {
    const ok = code === 0;
    send(ws, { type: 'log', line: ok ? '✅ Tags applied in popdroid' : `❌ claude exited ${code}` });
    send(ws, { type: 'qa_tags_done', success: ok });
  });
  proc.on('error', e =>
    send(ws, { type: 'log', level: 'error', line: 'Failed to start claude: ' + e.message })
  );

  res.json({ ok: true, missingFile: relFile, count: elements.length });
});

// ─── Start ───────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 3847;
server.listen(PORT, () => {
  console.log(`\n  ⚡ Forge UI  →  http://localhost:${PORT}\n`);
});
