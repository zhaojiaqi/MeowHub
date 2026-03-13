#!/usr/bin/env node
/**
 * MeowHub OpenClaw Node Host
 *
 * Connects to the OpenClaw Gateway via WebSocket and advertises
 * MeowHub device control commands as native Node tools.
 * Routes node.invoke requests to the MeowHub HTTP Bridge (:18790).
 */

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// ── Config ───────────────────────────────────────────────────────────
const GATEWAY_WS_URL = process.env.OPENCLAW_GATEWAY_WS || 'ws://127.0.0.1:18789';
const BRIDGE_HOST = process.env.MEOWHUB_BRIDGE_HOST || '127.0.0.1';
const BRIDGE_PORT = parseInt(process.env.MEOWHUB_BRIDGE_PORT || '18790');
const IDENTITY_PATH = process.env.MEOWHUB_NODE_IDENTITY
  || path.join(process.env.HOME || '/data/data/com.termux/files/home', '.openclaw', 'meowhub-node.json');
const PROTOCOL_VERSION = 3;
const NODE_ID = 'meowhub';
const CLIENT_VERSION = '1.0.0';

// ── Command definitions ──────────────────────────────────────────────
const COMMANDS = [
  { name: 'device.screenshot',   endpoint: 'screenshot',         method: 'POST', desc: 'Take a screenshot', params: { quality: 'number?', maxSize: 'number?' } },
  { name: 'device.tap',          endpoint: 'tap',                 method: 'POST', desc: 'Tap at coordinates', params: { x: 'number', y: 'number' } },
  { name: 'device.long_click',   endpoint: 'long_click',          method: 'POST', desc: 'Long click at coordinates', params: { x: 'number', y: 'number', duration: 'number?' } },
  { name: 'device.swipe',        endpoint: 'swipe',               method: 'POST', desc: 'Swipe gesture', params: { startX: 'number', startY: 'number', endX: 'number', endY: 'number', duration: 'number?' } },
  { name: 'device.scroll',       endpoint: 'scroll',              method: 'POST', desc: 'Scroll in direction', params: { direction: 'string' } },
  { name: 'device.type',         endpoint: 'type',                method: 'POST', desc: 'Type text', params: { text: 'string' } },
  { name: 'device.press_key',    endpoint: 'press_key',           method: 'POST', desc: 'Press a key', params: { key: 'string' } },
  { name: 'device.click_by_text',endpoint: 'click_by_text',       method: 'POST', desc: 'Click element by text', params: { text: 'string', index: 'number?' } },
  { name: 'device.open_app',     endpoint: 'open_app',            method: 'POST', desc: 'Open an app by package name', params: { name: 'string' } },
  { name: 'device.ui_tree',      endpoint: 'get_ui_tree',         method: 'POST', desc: 'Get UI accessibility tree', params: {} },
  { name: 'device.find_element', endpoint: 'find_element',        method: 'POST', desc: 'Find UI elements', params: { text: 'string?', resourceId: 'string?', className: 'string?' } },
  { name: 'device.read_ui_text', endpoint: 'read_ui_text',        method: 'POST', desc: 'Read visible text on screen', params: { filter: 'string?', exclude: 'string?' } },
  { name: 'device.info',         endpoint: 'device_info',         method: 'POST', desc: 'Get device info', params: { type: 'string?' } },
  { name: 'device.status',       endpoint: 'status',              method: 'GET',  desc: 'Check connection status', params: {} },
  { name: 'device.shell',        endpoint: 'execute_shell',       method: 'POST', desc: 'Execute shell command', params: { command: 'string', timeout: 'number?' } },
  { name: 'app.list',            endpoint: 'list_packages',       method: 'POST', desc: 'List installed apps', params: { thirdPartyOnly: 'boolean?', includeVersions: 'boolean?' } },
  { name: 'app.info',            endpoint: 'get_app_info',        method: 'POST', desc: 'Get app details', params: { package: 'string' } },
  { name: 'app.stop',            endpoint: 'force_stop_app',      method: 'POST', desc: 'Force stop an app', params: { package: 'string' } },
  { name: 'app.uninstall',       endpoint: 'uninstall_app',       method: 'POST', desc: 'Uninstall an app', params: { package: 'string', keepData: 'boolean?' } },
  { name: 'app.install',         endpoint: 'install_apk',         method: 'POST', desc: 'Install APK from path', params: { path: 'string' } },
  { name: 'app.clear_data',      endpoint: 'clear_app_data',      method: 'POST', desc: 'Clear app data', params: { package: 'string' } },
  { name: 'sms.send',            endpoint: 'send_sms',            method: 'POST', desc: 'Send SMS', params: { destination: 'string', text: 'string' } },
  { name: 'sms.read',            endpoint: 'read_sms',            method: 'POST', desc: 'Read SMS messages', params: { limit: 'number?', unreadOnly: 'boolean?' } },
  { name: 'call.accept',         endpoint: 'accept_call',         method: 'POST', desc: 'Accept incoming call', params: {} },
  { name: 'call.end',            endpoint: 'end_call',            method: 'POST', desc: 'End or reject call', params: {} },
  { name: 'call.make',           endpoint: 'make_call',           method: 'POST', desc: 'Make a phone call', params: { number: 'string' } },
  { name: 'audio.open',          endpoint: 'open_audio_channel',  method: 'POST', desc: 'Open audio channel', params: { mode: 'string?' } },
  { name: 'audio.close',         endpoint: 'close_audio_channel', method: 'POST', desc: 'Close audio channel', params: {} },
];

const COMMAND_MAP = Object.fromEntries(COMMANDS.map(c => [c.name, c]));
const CAPS = ['device', 'sms', 'call', 'audio', 'app'];

// ── HTTP Bridge helpers ──────────────────────────────────────────────
function callBridge(endpoint, params = {}) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(params);
    const req = http.request({
      hostname: BRIDGE_HOST,
      port: BRIDGE_PORT,
      path: `/api/${endpoint}`,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
      timeout: 30000
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          if (res.statusCode >= 400) reject(new Error(json.error || `HTTP ${res.statusCode}`));
          else resolve(json);
        } catch (e) {
          reject(new Error(`Invalid response: ${data.substring(0, 200)}`));
        }
      });
    });
    req.on('error', err => reject(new Error(`Bridge unreachable: ${err.message}`)));
    req.on('timeout', () => { req.destroy(); reject(new Error('Bridge request timeout')); });
    req.write(body);
    req.end();
  });
}

function callBridgeGet(endpoint) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: BRIDGE_HOST,
      port: BRIDGE_PORT,
      path: `/api/${endpoint}`,
      method: 'GET',
      timeout: 5000
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); }
        catch (e) { reject(new Error(`Invalid response from ${endpoint}`)); }
      });
    });
    req.on('error', err => reject(new Error(`Bridge unreachable: ${err.message}`)));
    req.on('timeout', () => { req.destroy(); reject(new Error('Bridge request timeout')); });
    req.end();
  });
}

// ── Encoding helpers ─────────────────────────────────────────────────
const ED25519_SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

function base64UrlEncode(buf) {
  return buf.toString('base64').replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/g, '');
}

function derivePublicKeyRaw(publicKeyPem) {
  const spki = crypto.createPublicKey(publicKeyPem).export({ type: 'spki', format: 'der' });
  if (spki.length === ED25519_SPKI_PREFIX.length + 32 &&
      spki.subarray(0, ED25519_SPKI_PREFIX.length).equals(ED25519_SPKI_PREFIX)) {
    return spki.subarray(ED25519_SPKI_PREFIX.length);
  }
  return spki;
}

function deriveDeviceId(publicKeyPem) {
  const raw = derivePublicKeyRaw(publicKeyPem);
  return crypto.createHash('sha256').update(raw).digest('hex');
}

// ── Device identity (Ed25519) ────────────────────────────────────────
function loadOrCreateIdentity() {
  try {
    if (fs.existsSync(IDENTITY_PATH)) {
      const data = JSON.parse(fs.readFileSync(IDENTITY_PATH, 'utf8'));
      if (data.version === 1 && data.deviceId && data.privateKeyPem && data.publicKeyPem) {
        // Verify/fix deviceId derivation
        const derivedId = deriveDeviceId(data.publicKeyPem);
        if (derivedId !== data.deviceId) {
          data.deviceId = derivedId;
          fs.writeFileSync(IDENTITY_PATH, JSON.stringify(data, null, 2) + '\n', { mode: 0o600 });
        }
        log(`Loaded device identity (id=${data.deviceId.substring(0, 12)}...)`);
        return data;
      }
    }
  } catch (e) {
    log(`Warning: failed to load identity: ${e.message}`);
  }

  log('Generating new Ed25519 device identity...');
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  const publicKeyPem = publicKey.export({ type: 'spki', format: 'pem' }).toString();
  const privateKeyPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  const deviceId = deriveDeviceId(publicKeyPem);

  const data = {
    version: 1,
    deviceId,
    displayName: 'MeowHub',
    publicKeyPem,
    privateKeyPem,
    createdAtMs: Date.now()
  };

  const dir = path.dirname(IDENTITY_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(IDENTITY_PATH, JSON.stringify(data, null, 2) + '\n', { mode: 0o600 });
  log(`Identity saved (id=${deviceId.substring(0, 12)}...)`);
  return data;
}

function signChallenge(identity, nonce) {
  const signedAtMs = Date.now();
  const scopes = 'operator.admin';
  // v3 payload format: v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily
  const payload = [
    'v3', identity.deviceId, 'node-host', 'node', 'node',
    scopes, String(signedAtMs), '', nonce, 'android', ''
  ].join('|');

  const privateKey = crypto.createPrivateKey(identity.privateKeyPem);
  const signature = base64UrlEncode(crypto.sign(null, Buffer.from(payload, 'utf8'), privateKey));
  const publicKeyB64Url = base64UrlEncode(derivePublicKeyRaw(identity.publicKeyPem));

  return { signature, signedAtMs, publicKey: publicKeyB64Url };
}

// ── WebSocket connection ─────────────────────────────────────────────
let ws = null;
let reqId = 0;
let reconnectDelay = 1000;
let identity = null;
let deviceToken = null;

function log(msg) {
  const ts = new Date().toISOString().slice(11, 23);
  process.stderr.write(`[node-host ${ts}] ${msg}\n`);
}

function send(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function sendReq(method, params) {
  const id = String(++reqId);
  send({ type: 'req', id, method, params });
  return id;
}

function connect() {
  log(`Connecting to ${GATEWAY_WS_URL}...`);

  try {
    ws = new WebSocket(GATEWAY_WS_URL);
  } catch (e) {
    log(`WebSocket constructor failed: ${e.message}`);
    scheduleReconnect();
    return;
  }

  ws.addEventListener('open', () => {
    log('WebSocket connected, waiting for challenge...');
    reconnectDelay = 1000;
  });

  ws.addEventListener('message', (event) => {
    let msg;
    try {
      msg = JSON.parse(typeof event.data === 'string' ? event.data : event.data.toString());
    } catch (e) {
      log(`Failed to parse message: ${e.message}`);
      return;
    }
    handleMessage(msg);
  });

  ws.addEventListener('close', (event) => {
    log(`WebSocket closed: code=${event.code} reason=${event.reason || 'none'}`);
    scheduleReconnect();
  });

  ws.addEventListener('error', (event) => {
    log(`WebSocket error: ${event.message || 'unknown'}`);
  });
}

function scheduleReconnect() {
  const delay = Math.min(reconnectDelay, 30000);
  log(`Reconnecting in ${delay}ms...`);
  setTimeout(connect, delay);
  reconnectDelay = Math.min(reconnectDelay * 2, 30000);
}

// ── Message handling ─────────────────────────────────────────────────
function handleMessage(msg) {
  if (msg.type === 'event') {
    handleEvent(msg);
  } else if (msg.type === 'res') {
    handleResponse(msg);
  }
}

const IGNORED_EVENTS = new Set(['agent', 'tick', 'health', 'presence', 'sessions']);

function handleEvent(msg) {
  switch (msg.event) {
    case 'connect.challenge':
      handleChallenge(msg.payload);
      break;
    case 'node.invoke.request':
      handleInvoke(msg.payload);
      break;
    default:
      if (!IGNORED_EVENTS.has(msg.event)) {
        log(`Unhandled event: ${msg.event}`);
      }
  }
}

function handleChallenge(payload) {
  const { nonce, ts } = payload;
  log(`Received challenge (nonce=${nonce?.substring(0, 8)}...)`);

  const signed = signChallenge(identity, nonce);

  const params = {
    minProtocol: PROTOCOL_VERSION,
    maxProtocol: PROTOCOL_VERSION,
    client: {
      id: 'node-host',
      displayName: 'MeowHub',
      version: CLIENT_VERSION,
      platform: 'android',
      mode: 'node'
    },
    role: 'node',
    scopes: ['operator.admin'],
    caps: CAPS,
    commands: COMMANDS.map(c => c.name),
    auth: deviceToken ? { deviceToken } : {},
    device: {
      id: identity.deviceId,
      publicKey: signed.publicKey,
      signature: signed.signature,
      signedAt: signed.signedAtMs,
      nonce
    }
  };

  sendReq('connect', params);
}

function handleResponse(msg) {
  if (msg.ok) {
    if (msg.payload && msg.payload.type === 'hello-ok') {
      log(`Connected as node (protocol=${msg.payload.protocol})`);
      if (msg.payload.auth && msg.payload.auth.deviceToken) {
        deviceToken = msg.payload.auth.deviceToken;
        // Persist token
        try {
          const data = JSON.parse(fs.readFileSync(IDENTITY_PATH, 'utf8'));
          data.deviceToken = deviceToken;
          fs.writeFileSync(IDENTITY_PATH, JSON.stringify(data, null, 2));
          log('Device token persisted');
        } catch (e) {
          log(`Warning: failed to persist device token: ${e.message}`);
        }
      }
    }
  } else {
    log(`Request ${msg.id} failed: ${JSON.stringify(msg.error || msg.payload)}`);
  }
}

async function handleInvoke(payload) {
  const { id, command, paramsJSON, timeoutMs } = payload;
  log(`Invoke: ${command} (id=${id})`);

  const cmd = COMMAND_MAP[command];
  if (!cmd) {
    sendInvokeResult(id, false, { error: `Unknown command: ${command}` });
    return;
  }

  let params = {};
  try {
    if (paramsJSON) params = JSON.parse(paramsJSON);
  } catch (e) {
    sendInvokeResult(id, false, { error: `Invalid paramsJSON: ${e.message}` });
    return;
  }

  try {
    let result;
    if (cmd.method === 'GET') {
      result = await callBridgeGet(cmd.endpoint);
    } else {
      result = await callBridge(cmd.endpoint, params);
    }
    sendInvokeResult(id, true, result);
  } catch (e) {
    log(`Invoke error for ${command}: ${e.message}`);
    sendInvokeResult(id, false, { error: e.message });
  }
}

function sendInvokeResult(invokeId, ok, payload) {
  sendReq('node.invoke.result', {
    id: invokeId,
    nodeId: identity.deviceId,
    ok,
    payloadJSON: JSON.stringify(payload)
  });
}

// ── Main ─────────────────────────────────────────────────────────────
function main() {
  log('MeowHub Node Host starting...');
  identity = loadOrCreateIdentity();

  // Load persisted device token if available
  try {
    const data = JSON.parse(fs.readFileSync(IDENTITY_PATH, 'utf8'));
    if (data.deviceToken) deviceToken = data.deviceToken;
  } catch (_) {}

  connect();
}

process.on('SIGINT', () => {
  log('Shutting down...');
  if (ws) ws.close();
  process.exit(0);
});

process.on('SIGTERM', () => {
  log('Shutting down...');
  if (ws) ws.close();
  process.exit(0);
});

main();
