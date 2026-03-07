#!/usr/bin/env node
/**
 * MeowHub MCP Server
 * Bridges OpenClaw AI Agent with MeowHub's device control capabilities
 * via MeowHub HTTP Bridge Server on 127.0.0.1:18790
 */

const http = require('http');
const readline = require('readline');

const BRIDGE_HOST = process.env.MEOWHUB_BRIDGE_HOST || '127.0.0.1';
const BRIDGE_PORT = parseInt(process.env.MEOWHUB_BRIDGE_PORT || '18790');
const BRIDGE_BASE = `http://${BRIDGE_HOST}:${BRIDGE_PORT}`;

function callBridge(endpoint, params = {}) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(params);
    const req = http.request({
      hostname: BRIDGE_HOST,
      port: BRIDGE_PORT,
      path: `/api/${endpoint}`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body)
      },
      timeout: 30000
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          if (res.statusCode === 503) {
            reject(new Error(json.error || 'MeowHub Socket not connected'));
          } else if (res.statusCode >= 400) {
            reject(new Error(json.error || `HTTP ${res.statusCode}`));
          } else {
            resolve(json);
          }
        } catch (e) {
          reject(new Error(`Invalid response: ${data.substring(0, 200)}`));
        }
      });
    });
    req.on('error', (err) => reject(new Error(`Bridge unreachable: ${err.message}`)));
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
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(new Error(`Invalid response from ${endpoint}`));
        }
      });
    });
    req.on('error', (err) => reject(new Error(`Bridge unreachable: ${err.message}`)));
    req.on('timeout', () => { req.destroy(); reject(new Error('Bridge request timeout')); });
    req.end();
  });
}

const rl = readline.createInterface({ input: process.stdin });

function sendMcpResponse(id, result) {
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, result }) + '\n');
}

function sendMcpError(id, code, message) {
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id, error: { code, message } }) + '\n');
}

const TOOLS = [
  {
    name: 'screenshot',
    description: 'Take a screenshot of the current screen and return as base64 JPEG',
    inputSchema: {
      type: 'object',
      properties: {
        quality: { type: 'number', description: 'JPEG quality 1-100', default: 50 },
        maxSize: { type: 'number', description: 'Max resolution', default: 720 }
      }
    }
  },
  {
    name: 'tap',
    description: 'Tap at screen coordinates (x, y)',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number', description: 'X coordinate' },
        y: { type: 'number', description: 'Y coordinate' }
      },
      required: ['x', 'y']
    }
  },
  {
    name: 'swipe',
    description: 'Swipe from (startX, startY) to (endX, endY)',
    inputSchema: {
      type: 'object',
      properties: {
        startX: { type: 'number' },
        startY: { type: 'number' },
        endX: { type: 'number' },
        endY: { type: 'number' },
        duration: { type: 'number', description: 'Duration in ms', default: 300 }
      },
      required: ['startX', 'startY', 'endX', 'endY']
    }
  },
  {
    name: 'type_text',
    description: 'Type text into the currently focused input field',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to type' }
      },
      required: ['text']
    }
  },
  {
    name: 'press_key',
    description: 'Press a system key (home, back, power, recent, volume_up, volume_down, enter)',
    inputSchema: {
      type: 'object',
      properties: {
        key: { type: 'string', description: 'Key name', enum: ['home', 'back', 'power', 'recent', 'volume_up', 'volume_down', 'enter'] }
      },
      required: ['key']
    }
  },
  {
    name: 'open_app',
    description: 'Open an app by name or package name',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string', description: 'App name or package name (e.g. "WeChat" or "com.tencent.mm")' }
      },
      required: ['name']
    }
  },
  {
    name: 'get_ui_tree',
    description: 'Get the current UI accessibility tree as JSON (for finding elements)',
    inputSchema: {
      type: 'object',
      properties: {}
    }
  },
  {
    name: 'find_element',
    description: 'Find UI elements by text content or resource ID',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to search for' },
        resourceId: { type: 'string', description: 'Resource ID to search for' },
        className: { type: 'string', description: 'Class name to filter by' }
      }
    }
  },
  {
    name: 'click_by_text',
    description: 'Click on a UI element by its text content',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to click on' },
        index: { type: 'number', description: 'Index if multiple matches', default: 0 }
      },
      required: ['text']
    }
  },
  {
    name: 'long_click',
    description: 'Long click at screen coordinates',
    inputSchema: {
      type: 'object',
      properties: {
        x: { type: 'number' },
        y: { type: 'number' },
        duration: { type: 'number', description: 'Duration in ms', default: 1000 }
      },
      required: ['x', 'y']
    }
  },
  {
    name: 'scroll',
    description: 'Scroll in a direction (up, down, left, right)',
    inputSchema: {
      type: 'object',
      properties: {
        direction: { type: 'string', enum: ['up', 'down', 'left', 'right'] }
      },
      required: ['direction']
    }
  },
  {
    name: 'execute_shell',
    description: 'Execute a shell command on the device',
    inputSchema: {
      type: 'object',
      properties: {
        command: { type: 'string', description: 'Shell command to execute' }
      },
      required: ['command']
    }
  },
  {
    name: 'get_device_info',
    description: 'Get device information (apps, battery, storage, network, bluetooth, all)',
    inputSchema: {
      type: 'object',
      properties: {
        type: { type: 'string', description: 'Info type', enum: ['apps', 'battery', 'storage', 'network', 'bluetooth', 'all'], default: 'all' }
      }
    }
  },
  {
    name: 'list_packages',
    description: 'List installed app packages with labels',
    inputSchema: {
      type: 'object',
      properties: {}
    }
  },
  {
    name: 'read_ui_text',
    description: 'Read all visible text on screen, optionally filtered',
    inputSchema: {
      type: 'object',
      properties: {
        filter: { type: 'string', description: 'Only include text containing this string' },
        exclude: { type: 'string', description: 'Comma-separated strings to exclude' }
      }
    }
  },
  {
    name: 'check_connection',
    description: 'Check MeowHub Bridge and Socket connection status',
    inputSchema: {
      type: 'object',
      properties: {}
    }
  }
];

async function handleToolCall(name, args) {
  switch (name) {
    case 'check_connection': {
      const status = await callBridgeGet('status');
      return [{
        type: 'text',
        text: `MeowHub connection: ${status.connected ? 'Connected' : 'Disconnected'} (${status.status}), screen: ${status.screenWidth}x${status.screenHeight}`
      }];
    }
    case 'screenshot': {
      const result = await callBridge('screenshot', {
        quality: args.quality || 50,
        maxSize: args.maxSize || 720
      });
      if (result.error) throw new Error(result.error);
      const content = [{ type: 'text', text: `Screenshot captured (${result.width}x${result.height})` }];
      if (result.data) {
        content.push({ type: 'image', data: result.data, mimeType: result.mimeType || 'image/jpeg' });
      }
      return content;
    }
    case 'tap': {
      const result = await callBridge('tap', { x: args.x, y: args.y });
      return [{ type: 'text', text: `Tapped at (${args.x}, ${args.y})` }];
    }
    case 'swipe': {
      await callBridge('swipe', {
        startX: args.startX, startY: args.startY,
        endX: args.endX, endY: args.endY,
        duration: args.duration || 300
      });
      return [{ type: 'text', text: `Swiped from (${args.startX},${args.startY}) to (${args.endX},${args.endY})` }];
    }
    case 'type_text': {
      await callBridge('type', { text: args.text });
      return [{ type: 'text', text: `Typed: "${args.text}"` }];
    }
    case 'press_key': {
      await callBridge('press_key', { key: args.key });
      return [{ type: 'text', text: `Pressed ${args.key}` }];
    }
    case 'open_app': {
      await callBridge('open_app', { name: args.name || args.package });
      return [{ type: 'text', text: `Opened app: ${args.name || args.package}` }];
    }
    case 'get_ui_tree': {
      const result = await callBridge('get_ui_tree', {});
      return [{ type: 'text', text: JSON.stringify(result, null, 2) }];
    }
    case 'find_element': {
      const result = await callBridge('find_element', {
        text: args.text, resourceId: args.resourceId, className: args.className
      });
      return [{ type: 'text', text: JSON.stringify(result, null, 2) }];
    }
    case 'click_by_text': {
      await callBridge('click_by_text', { text: args.text, index: args.index || 0 });
      return [{ type: 'text', text: `Clicked element with text: "${args.text}"` }];
    }
    case 'long_click': {
      await callBridge('long_click', { x: args.x, y: args.y });
      return [{ type: 'text', text: `Long clicked at (${args.x}, ${args.y})` }];
    }
    case 'scroll': {
      await callBridge('scroll', { direction: args.direction });
      return [{ type: 'text', text: `Scrolled ${args.direction}` }];
    }
    case 'execute_shell': {
      const result = await callBridge('execute_shell', { command: args.command });
      return [{ type: 'text', text: result.output || '' }];
    }
    case 'get_device_info': {
      const result = await callBridge('device_info', { type: args.type || 'all' });
      return [{ type: 'text', text: result.info || JSON.stringify(result) }];
    }
    case 'list_packages': {
      const result = await callBridge('list_packages', {});
      return [{ type: 'text', text: JSON.stringify(result.packages || result, null, 2) }];
    }
    case 'read_ui_text': {
      const result = await callBridge('read_ui_text', {
        filter: args.filter || '', exclude: args.exclude || ''
      });
      return [{ type: 'text', text: result.text || '' }];
    }
    default:
      throw new Error(`Unknown tool: ${name}`);
  }
}

async function checkBridgeAvailable() {
  try {
    const status = await callBridgeGet('status');
    return status.connected === true;
  } catch (e) {
    return false;
  }
}

async function handleMessage(msg) {
  const { id, method, params } = msg;

  switch (method) {
    case 'initialize':
      sendMcpResponse(id, {
        protocolVersion: '2024-11-05',
        capabilities: { tools: {} },
        serverInfo: { name: 'meowhub-device', version: '2.0.0' }
      });
      break;

    case 'notifications/initialized': {
      const connected = await checkBridgeAvailable();
      process.stderr.write(`[meowhub-mcp] Bridge ${connected ? 'connected' : 'not available'} at ${BRIDGE_BASE}\n`);
      break;
    }

    case 'tools/list':
      sendMcpResponse(id, { tools: TOOLS });
      break;

    case 'tools/call': {
      const { name, arguments: args } = params;
      try {
        const content = await handleToolCall(name, args || {});
        sendMcpResponse(id, { content });
      } catch (e) {
        sendMcpResponse(id, {
          content: [{ type: 'text', text: `Error: ${e.message}` }],
          isError: true
        });
      }
      break;
    }

    default:
      if (id !== undefined) {
        sendMcpError(id, -32601, `Method not found: ${method}`);
      }
  }
}

rl.on('line', async (line) => {
  try {
    const msg = JSON.parse(line);
    await handleMessage(msg);
  } catch (e) {
    process.stderr.write(`[meowhub-mcp] Error: ${e.message}\n`);
  }
});

process.on('SIGINT', () => process.exit(0));
process.on('SIGTERM', () => process.exit(0));
