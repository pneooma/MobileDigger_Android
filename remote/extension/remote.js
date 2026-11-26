// MobileDigger Remote — talks to the phone's "Desk remote" over Bluetooth LE (Web Bluetooth).
// Runs as the Chrome extension's page (global hotkeys come from the extension) or as a
// stand-alone HTML file (keys work while the tab or the mini player is in front).
'use strict';

const SVC = '8b7c0001-3f2a-4c1e-9d5b-6d6f62696c65';
const CMD = '8b7c0002-3f2a-4c1e-9d5b-6d6f62696c65';
const ST  = '8b7c0003-3f2a-4c1e-9d5b-6d6f62696c65';
const ACTIONS = [
  { cmd: 'prev', label: 'Previous', icon: '⏮' },
  { cmd: 'play', label: 'Play / Pause', icon: '⏯' },
  { cmd: 'next', label: 'Next', icon: '⏭' },
  { cmd: 'like', label: 'Like', icon: '👍' },
  { cmd: 'dislike', label: 'Dislike', icon: '👎' },
];
const DEFAULT_KEYS = { prev: 'ArrowLeft', play: 'Space', next: 'ArrowRight', like: 'KeyL', dislike: 'KeyD' };
const IS_EXT = !!(window.chrome && chrome.runtime && chrome.runtime.id);
const $ = id => document.getElementById(id);

let device = null, cmdChar = null, unlocked = false, queue = Promise.resolve(), lastPin = '';
let nowPlaying = '—', pipWin = null, capturing = null;

// ---------------------------------------------------------------- key bindings (in-page / mini player)
function loadKeys() {
  try { return Object.assign({}, DEFAULT_KEYS, JSON.parse(localStorage.getItem('mdr_keys') || '{}')); }
  catch (_) { return Object.assign({}, DEFAULT_KEYS); }
}
let keys = loadKeys();
function saveKeys() { try { localStorage.setItem('mdr_keys', JSON.stringify(keys)); } catch (_) {} }

/** "Ctrl+Shift+KeyL" from a keydown event. */
function comboOf(e) {
  const mods = [];
  if (e.ctrlKey) mods.push('Ctrl'); if (e.altKey) mods.push('Alt');
  if (e.shiftKey) mods.push('Shift'); if (e.metaKey) mods.push('Meta');
  return [...mods, e.code].join('+');
}
/** "Ctrl+Shift+L" for showing. */
function pretty(combo) {
  return combo.split('+').map(p => p.replace(/^Key/, '').replace(/^Digit/, '')
    .replace('ArrowLeft', '←').replace('ArrowRight', '→').replace('ArrowUp', '↑').replace('ArrowDown', '↓')).join(' + ');
}

function onKey(e) {
  if (capturing) {
    e.preventDefault();
    if (e.key === 'Escape') { capturing = null; renderKeys(); return; }
    if (['Control', 'Shift', 'Alt', 'Meta'].includes(e.key)) return; // wait for the real key
    const combo = comboOf(e);
    for (const k of Object.keys(keys)) if (keys[k] === combo) keys[k] = ''; // one action per key
    keys[capturing] = combo; capturing = null; saveKeys(); renderKeys();
    return;
  }
  if (e.target && e.target.tagName === 'INPUT' || e.repeat) return;
  const combo = comboOf(e);
  const action = Object.keys(keys).find(k => keys[k] === combo);
  if (!action) return;
  e.preventDefault();
  send(action);
}
document.addEventListener('keydown', onKey);

function renderKeys() {
  const box = $('keyList'); if (!box) return;
  box.textContent = '';
  for (const a of ACTIONS) {
    const row = document.createElement('div'); row.className = 'bind';
    const name = document.createElement('span'); name.textContent = a.icon + ' ' + a.label;
    const btn = document.createElement('button'); btn.className = 'kbd-btn';
    btn.textContent = capturing === a.cmd ? 'Press a key… (Esc cancels)' : (keys[a.cmd] ? pretty(keys[a.cmd]) : 'Not set');
    btn.addEventListener('click', () => { capturing = a.cmd; renderKeys(); });
    row.append(name, btn); box.append(row);
  }
}

// ---------------------------------------------------------------- global hotkeys (extension only)
async function renderGlobal() {
  const box = $('globalBox'); if (!box) return;
  if (!IS_EXT) { box.hidden = true; $('globalHint').hidden = false; return; }
  box.hidden = false; $('globalHint').hidden = true;
  const list = $('globalList'); list.textContent = '';
  const cmds = await chrome.commands.getAll();
  for (const a of ACTIONS) {
    const c = cmds.find(x => x.name === a.cmd);
    const row = document.createElement('div'); row.className = 'bind';
    const name = document.createElement('span'); name.textContent = a.icon + ' ' + a.label;
    const k = document.createElement('code'); k.textContent = c && c.shortcut ? c.shortcut : 'Not set';
    row.append(name, k); list.append(row);
  }
}
if (IS_EXT) {
  chrome.runtime.onMessage.addListener((msg, _sender, reply) => {
    if (msg && msg.type === 'cmd') {
      if (unlocked) { send(msg.cmd); reply({ ok: true }); }
      else { showErr('A global shortcut was pressed, but the phone isn’t connected. Click Connect.'); reply({ ok: false }); }
    }
  });
  window.addEventListener('focus', renderGlobal);
}

// ---------------------------------------------------------------- Bluetooth
function setStatus(text, state) {
  $('status').textContent = text;
  $('dot').className = 'dot' + (state === 'on' ? ' on' : state === 'wait' ? ' wait' : '');
}
function showErr(text) { $('err').textContent = text || ''; $('err').hidden = !text; }
function setUnlocked(v) {
  unlocked = v;
  document.querySelectorAll('[data-cmd]').forEach(b => b.disabled = !v);
  if (pipWin) pipWin.document.querySelectorAll('[data-cmd]').forEach(b => b.disabled = !v);
  $('pinRow').hidden = v || !cmdChar;
}

function send(text) {
  if (!cmdChar) return Promise.resolve();
  queue = queue.then(() => cmdChar.writeValueWithResponse(new TextEncoder().encode(text)))
               .catch(e => showErr('Couldn’t send: ' + e.message));
  return queue;
}

function setNowPlaying(t) {
  nowPlaying = t || '—';
  $('np').textContent = nowPlaying;
  if (pipWin) pipWin.document.getElementById('pipTitle').textContent = nowPlaying;
  document.title = (nowPlaying !== '—' ? nowPlaying + ' · ' : '') + 'MobileDigger Remote';
}

function onMessage(ev) {
  const msg = new TextDecoder().decode(ev.target.value);
  if (msg === 'ok:pin') { setUnlocked(true); showErr(''); setStatus('Connected to ' + (device && device.name || 'phone') + '.', 'on'); }
  else if (msg === 'err:pin') { setUnlocked(false); showErr('Wrong PIN. Check the number on the phone (it changes each time the remote is switched on).'); $('pin').select(); }
  else if (msg === 'err:locked') { setUnlocked(false); showErr('Type the PIN shown on the phone first.'); }
  else if (msg.startsWith('np:')) setNowPlaying(msg.slice(3));
  else if (msg.startsWith('ok:')) flash(msg.slice(3));
}

function flash(cmd) {
  const docs = [document]; if (pipWin) docs.push(pipWin.document);
  for (const d of docs) {
    const b = d.querySelector('[data-cmd="' + cmd + '"]'); if (!b) continue;
    b.classList.add('flash'); setTimeout(() => b.classList.remove('flash'), 220);
  }
}

async function attach() {
  setStatus('Connecting to ' + (device.name || 'phone') + '…', 'wait');
  const server = await device.gatt.connect();
  const svc = await server.getPrimaryService(SVC);
  cmdChar = await svc.getCharacteristic(CMD);
  const st = await svc.getCharacteristic(ST);
  st.addEventListener('characteristicvaluechanged', onMessage);
  await st.startNotifications();
  $('disconnectBtn').hidden = false;
  if (lastPin) { send('pin:' + lastPin); setStatus('Unlocking…', 'wait'); }
  else { setUnlocked(false); setStatus('Connected. Type the PIN shown on the phone.', 'wait'); $('pin').focus(); }
}

async function connect() {
  showErr('');
  if (!navigator.bluetooth) {
    showErr('This browser can’t use Bluetooth here. Use Chrome or Edge on Windows/macOS.');
    return;
  }
  try {
    device = await navigator.bluetooth.requestDevice({ filters: [{ services: [SVC] }] });
    device.addEventListener('gattserverdisconnected', onDisconnected);
    await attach();
  } catch (e) {
    if (e.name === 'NotFoundError') setStatus('No phone picked. Is Desk remote switched on, and the phone nearby?', '');
    else showErr(e.name === 'SecurityError' ? 'Bluetooth is blocked for this page (browser or company policy).' : 'Couldn’t connect: ' + e.message);
  }
}

let retrying = false;
async function onDisconnected() {
  cmdChar = null; setUnlocked(false); $('pinRow').hidden = true;
  setStatus('Connection lost. Reconnecting…', 'wait');
  if (retrying || !device) return;
  retrying = true;
  for (let i = 0; i < 5 && device; i++) {
    try { await attach(); retrying = false; return; } catch (_) { await new Promise(r => setTimeout(r, 2000 * (i + 1))); }
  }
  retrying = false;
  setStatus('Disconnected. Is Desk remote still on? Click Connect to try again.', '');
  $('disconnectBtn').hidden = true;
}

function disconnect() {
  const d = device; device = null; cmdChar = null; setUnlocked(false);
  $('pinRow').hidden = true; $('disconnectBtn').hidden = true;
  if (d && d.gatt && d.gatt.connected) d.gatt.disconnect();
  setStatus('Disconnected.', '');
}

// ---------------------------------------------------------------- always-on-top mini player
async function openMini() {
  if (!('documentPictureInPicture' in window)) {
    showErr('This browser can’t open an always-on-top window. Use Chrome or Edge 116 or newer.');
    return;
  }
  if (pipWin) { pipWin.focus(); return; }
  pipWin = await documentPictureInPicture.requestWindow({ width: 380, height: 112 });
  const d = pipWin.document;
  // Same look as the page
  for (const sheet of document.styleSheets) {
    try {
      const style = d.createElement('style');
      style.textContent = [...sheet.cssRules].map(r => r.cssText).join('\n');
      d.head.append(style);
    } catch (_) {
      if (sheet.href) { const l = d.createElement('link'); l.rel = 'stylesheet'; l.href = sheet.href; d.head.append(l); }
    }
  }
  d.title = 'MobileDigger';
  d.body.className = 'pip';
  const title = d.createElement('div'); title.id = 'pipTitle'; title.className = 'pip-title'; title.textContent = nowPlaying;
  const row = d.createElement('div'); row.className = 'pip-row';
  for (const a of ACTIONS) {
    const b = d.createElement('button'); b.className = 'pip-btn' + (a.cmd === 'like' ? ' like' : a.cmd === 'dislike' ? ' dislike' : '');
    b.dataset.cmd = a.cmd; b.title = a.label; b.textContent = a.icon; b.disabled = !unlocked;
    b.addEventListener('click', () => send(a.cmd));
    row.append(b);
  }
  d.body.append(title, row);
  d.addEventListener('keydown', onKey);
  pipWin.addEventListener('pagehide', () => { pipWin = null; $('miniBtn').textContent = 'Open mini player'; });
  $('miniBtn').textContent = 'Mini player is open';
}

// ---------------------------------------------------------------- wiring
$('connectBtn').addEventListener('click', connect);
$('disconnectBtn').addEventListener('click', disconnect);
$('miniBtn').addEventListener('click', () => openMini().catch(e => showErr('Couldn’t open the mini player: ' + e.message)));
$('pinBtn').addEventListener('click', () => {
  lastPin = $('pin').value.replace(/\D/g, '');
  if (lastPin.length !== 6) { showErr('The PIN has 6 digits.'); return; }
  send('pin:' + lastPin);
});
$('pin').addEventListener('keydown', e => { if (e.key === 'Enter') $('pinBtn').click(); });
document.querySelectorAll('.keys [data-cmd]').forEach(b => b.addEventListener('click', () => send(b.dataset.cmd)));
$('resetKeys').addEventListener('click', () => { keys = Object.assign({}, DEFAULT_KEYS); saveKeys(); renderKeys(); });
const gBtn = $('globalBtn');
if (gBtn) gBtn.addEventListener('click', () => { if (IS_EXT) chrome.tabs.create({ url: 'chrome://extensions/shortcuts' }); });
renderKeys();
renderGlobal();
