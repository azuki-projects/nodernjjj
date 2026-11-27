package com.example.runtime

object NodeCoreModules {

    fun buildBootstrapScript(
        projectPath: String,
        entryFilePath: String,
        nodeVersion: String,
        npmVersion: String,
        arch: String,
        argvJson: String,
        envJson: String
    ): String {
        return """
(function(global) {
  const __projectRoot = ${quote(projectPath)};
  const __entryFile = ${quote(entryFilePath)};
  const __argv = $argvJson;
  const __env = $envJson;
  let __cwd = __projectRoot;
  const __startTime = Date.now();
  let __activeTimers = 0;
  let __activeServers = 0;
  let __nextTimerId = 1;
  const __cancelledTimers = new Set();

  function __formatError(err) {
    if (err === undefined || err === null) return String(err);
    if (typeof err !== 'object') return String(err);
    const name = err.name || 'Error';
    const msg = (err.message !== undefined && err.message !== null) ? String(err.message) : '';
    const header = msg ? (name + ': ' + msg) : name;
    const stack = err.stack ? String(err.stack).trimEnd() : '';
    const parts = [];
    if (!stack) {
      parts.push(header);
    } else if (stack.startsWith(header) || (msg && stack.includes(msg))) {
      parts.push(stack);
    } else {
      parts.push(header);
      parts.push(stack);
    }
    if (err.code) parts.push('  code: ' + err.code);
    if (err.cause) parts.push('Caused by: ' + __formatError(err.cause));
    return parts.join('\n');
  }

  function __formatArg(arg) {
    if (arg === undefined) return 'undefined';
    if (arg === null) return 'null';
    if (typeof arg === 'string') return arg;
    if (arg instanceof Error || (typeof arg === 'object' && typeof arg.message === 'string' && typeof arg.stack === 'string')) {
      return __formatError(arg);
    }
    if (typeof arg === 'object') {
      try { return JSON.stringify(arg, null, 2); } catch (_) { return String(arg); }
    }
    return String(arg);
  }

  function __joinArgs(args) {
    const out = [];
    for (let i = 0; i < args.length; i++) out.push(__formatArg(args[i]));
    return out.join(' ');
  }

  const __timersMap = new Map();
  global.console = {
    log: (...args) => __native.log('STDOUT', __joinArgs(args)),
    info: (...args) => __native.log('INFO', __joinArgs(args)),
    debug: (...args) => __native.log('STDOUT', __joinArgs(args)),
    warn: (...args) => __native.log('INFO', '[warn] ' + __joinArgs(args)),
    error: (...args) => __native.log('STDERR', __joinArgs(args)),
    dir: (obj) => __native.log('STDOUT', __formatArg(obj)),
    assert: (cond, ...args) => {
      if (!cond) __native.log('STDERR', 'Assertion failed: ' + __joinArgs(args));
    },
    time: (label = 'default') => __timersMap.set(label, Date.now()),
    timeEnd: (label = 'default') => {
      const start = __timersMap.get(label);
      if (start !== undefined) {
        __native.log('STDOUT', label + ': ' + (Date.now() - start) + 'ms');
        __timersMap.delete(label);
      }
    },
    table: (data) => __native.log('STDOUT', __formatArg(data)),
    clear: () => __native.log('SYSTEM', '__CLEAR__')
  };

  global.process = {
    version: ${quote(nodeVersion)},
    versions: { node: ${quote(nodeVersion.removePrefix("v"))}, npm: ${quote(npmVersion)}, v8: '12.4.254.21-quickjs', quickjs: '2024.02' },
    platform: 'android',
    arch: ${quote(arch)},
    pid: 1042,
    ppid: 1,
    argv: __argv,
    env: __env,
    cwd: () => __cwd,
    chdir: (dir) => {
      const resolved = __pathMod.resolve(__cwd, dir);
      if (!__native.fsExists(resolved)) throw new Error('ENOENT: no such directory: ' + resolved);
      __cwd = resolved;
    },
    uptime: () => (Date.now() - __startTime) / 1000,
    hrtime: (prev) => {
      const ms = Date.now() - __startTime;
      const sec = Math.floor(ms / 1000);
      const nano = (ms % 1000) * 1e6;
      if (Array.isArray(prev)) {
        let ds = sec - prev[0];
        let dn = nano - prev[1];
        if (dn < 0) { ds -= 1; dn += 1e9; }
        return [ds, dn];
      }
      return [sec, nano];
    },
    nextTick: (fn, ...args) => Promise.resolve().then(() => fn(...args)),
    exit: (code = 0) => {
      __native.log('INFO', 'Process exited with code ' + code);
      throw { __isProcessExit: true, code };
    },
    stdout: { write: (s) => __native.log('STDOUT', String(s).replace(/\n$/, '')) },
    stderr: { write: (s) => __native.log('STDERR', String(s).replace(/\n$/, '')) }
  };

  global.setTimeout = function(fn, delay = 0, ...args) {
    const id = __nextTimerId++;
    __activeTimers++;
    __native.sleep(Math.max(0, Number(delay) || 0)).then(() => {
      __activeTimers--;
      if (!__cancelledTimers.has(id)) {
        try { fn(...args); } catch (e) { console.error(__formatError(e)); }
      }
      __cancelledTimers.delete(id);
    }).catch(() => { __activeTimers--; });
    return id;
  };

  global.clearTimeout = function(id) {
    __cancelledTimers.add(id);
  };

  global.setInterval = function(fn, delay = 10, ...args) {
    const id = __nextTimerId++;
    const ms = Math.max(10, Number(delay) || 10);
    __activeTimers++;
    (async () => {
      try {
        while (!__cancelledTimers.has(id)) {
          await __native.sleep(ms);
          if (__cancelledTimers.has(id)) break;
          try { fn(...args); } catch (e) { console.error(__formatError(e)); }
        }
      } finally {
        __activeTimers--;
        __cancelledTimers.delete(id);
      }
    })();
    return id;
  };

  global.clearInterval = function(id) {
    __cancelledTimers.add(id);
  };

  global.setImmediate = (fn, ...args) => global.setTimeout(fn, 0, ...args);
  global.clearImmediate = (id) => global.clearTimeout(id);
  global.queueMicrotask = (fn) => Promise.resolve().then(fn);

  global.fetch = async function(url, options = {}) {
    const method = (options && options.method) ? String(options.method).toUpperCase() : 'GET';
    const headersJson = options && options.headers ? JSON.stringify(options.headers) : '{}';
    const body = options && options.body ? String(options.body) : '';
    const raw = await __native.httpFetch(String(url), method, headersJson, body);
    const res = JSON.parse(raw);
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      statusText: res.statusText || '',
      headers: {
        get: (k) => res.headers[String(k).toLowerCase()] || null,
        raw: () => res.headers
      },
      text: async () => res.body,
      json: async () => JSON.parse(res.body)
    };
  };

  class Buffer extends Uint8Array {
    static from(input, encoding = 'utf8') {
      if (typeof input === 'string') {
        if (encoding === 'hex') {
          const bytes = [];
          for (let i = 0; i < input.length; i += 2) bytes.push(parseInt(input.substr(i, 2), 16));
          return new Buffer(bytes);
        }
        if (encoding === 'base64') {
          const b64 = __native.base64Decode(input);
          return new Buffer(JSON.parse(b64));
        }
        const utf8 = unescape(encodeURIComponent(input));
        const arr = new Buffer(utf8.length);
        for (let i = 0; i < utf8.length; i++) arr[i] = utf8.charCodeAt(i);
        return arr;
      }
      if (Array.isArray(input) || input instanceof Uint8Array) return new Buffer(input);
      return new Buffer(0);
    }
    static alloc(size, fill = 0) {
      const b = new Buffer(size);
      if (fill !== 0) b.fill(typeof fill === 'string' ? fill.charCodeAt(0) : fill);
      return b;
    }
    static isBuffer(obj) { return obj instanceof Buffer || obj instanceof Uint8Array; }
    static concat(list) {
      const total = list.reduce((acc, b) => acc + b.length, 0);
      const out = new Buffer(total);
      let offset = 0;
      for (const b of list) { out.set(b, offset); offset += b.length; }
      return out;
    }
    toString(encoding = 'utf8') {
      if (encoding === 'hex') {
        return Array.from(this).map(b => b.toString(16).padStart(2, '0')).join('');
      }
      if (encoding === 'base64') {
        return __native.base64Encode(JSON.stringify(Array.from(this)));
      }
      let str = '';
      for (let i = 0; i < this.length; i++) str += String.fromCharCode(this[i]);
      try { return decodeURIComponent(escape(str)); } catch (_) { return str; }
    }
  }
  global.Buffer = Buffer;

  const __pathMod = {
    sep: '/',
    delimiter: ':',
    join: (...parts) => __pathMod.normalize(parts.filter(Boolean).join('/')),
    resolve: (...parts) => {
      let resolved = '';
      for (let i = parts.length - 1; i >= 0; i--) {
        const p = parts[i];
        if (!p) continue;
        resolved = p + (resolved ? '/' + resolved : '');
        if (p.startsWith('/')) break;
      }
      if (!resolved.startsWith('/')) resolved = __cwd + '/' + resolved;
      return __pathMod.normalize(resolved);
    },
    normalize: (p) => {
      const isAbs = p.startsWith('/');
      const segs = p.split('/').filter(Boolean);
      const stack = [];
      for (const s of segs) {
        if (s === '.') continue;
        if (s === '..') { if (stack.length && stack[stack.length - 1] !== '..') stack.pop(); else if (!isAbs) stack.push('..'); }
        else stack.push(s);
      }
      return (isAbs ? '/' : '') + stack.join('/') || (isAbs ? '/' : '.');
    },
    dirname: (p) => {
      const norm = __pathMod.normalize(p);
      const idx = norm.lastIndexOf('/');
      if (idx < 0) return '.';
      return idx === 0 ? '/' : norm.slice(0, idx);
    },
    basename: (p, ext) => {
      const base = p.split('/').filter(Boolean).pop() || '';
      return (ext && base.endsWith(ext)) ? base.slice(0, -ext.length) : base;
    },
    extname: (p) => {
      const base = __pathMod.basename(p);
      const idx = base.lastIndexOf('.');
      return idx > 0 ? base.slice(idx) : '';
    },
    isAbsolute: (p) => typeof p === 'string' && p.startsWith('/'),
    relative: (from, to) => {
      const a = __pathMod.resolve(from).split('/').filter(Boolean);
      const b = __pathMod.resolve(to).split('/').filter(Boolean);
      let i = 0;
      while (i < a.length && i < b.length && a[i] === b[i]) i++;
      return [...Array(a.length - i).fill('..'), ...b.slice(i)].join('/') || '.';
    }
  };

  const __fsMod = {
    existsSync: (p) => __native.fsExists(__pathMod.resolve(__cwd, p)),
    readFileSync: (p, opt) => {
      const abs = __pathMod.resolve(__cwd, p);
      const text = __native.fsRead(abs);
      if (opt === 'utf8' || opt === 'utf-8' || (opt && opt.encoding)) return text;
      return Buffer.from(text);
    },
    writeFileSync: (p, data) => {
      const abs = __pathMod.resolve(__cwd, p);
      const text = Buffer.isBuffer(data) ? data.toString('utf8') : String(data);
      __native.fsWrite(abs, text, false);
    },
    appendFileSync: (p, data) => {
      const abs = __pathMod.resolve(__cwd, p);
      __native.fsWrite(abs, String(data), true);
    },
    mkdirSync: (p, opt) => {
      __native.fsMkdir(__pathMod.resolve(__cwd, p));
    },
    readdirSync: (p) => JSON.parse(__native.fsReaddir(__pathMod.resolve(__cwd, p))),
    statSync: (p) => JSON.parse(__native.fsStat(__pathMod.resolve(__cwd, p))),
    unlinkSync: (p) => __native.fsDelete(__pathMod.resolve(__cwd, p)),
    rmSync: (p) => __native.fsDelete(__pathMod.resolve(__cwd, p)),
    renameSync: (a, b) => __native.fsRename(__pathMod.resolve(__cwd, a), __pathMod.resolve(__cwd, b)),
    promises: {
      readFile: async (p, opt) => __fsMod.readFileSync(p, opt),
      writeFile: async (p, d) => __fsMod.writeFileSync(p, d),
      appendFile: async (p, d) => __fsMod.appendFileSync(p, d),
      mkdir: async (p, o) => __fsMod.mkdirSync(p, o),
      readdir: async (p) => __fsMod.readdirSync(p),
      stat: async (p) => __fsMod.statSync(p),
      unlink: async (p) => __fsMod.unlinkSync(p),
      rm: async (p) => __fsMod.rmSync(p)
    }
  };

  class EventEmitter {
    constructor() { this._events = new Map(); }
    on(evt, fn) {
      if (!this._events.has(evt)) this._events.set(evt, []);
      this._events.get(evt).push(fn);
      return this;
    }
    addListener(evt, fn) { return this.on(evt, fn); }
    once(evt, fn) {
      const wrapper = (...args) => { this.off(evt, wrapper); fn(...args); };
      return this.on(evt, wrapper);
    }
    off(evt, fn) {
      const list = this._events.get(evt);
      if (list) this._events.set(evt, list.filter(f => f !== fn));
      return this;
    }
    removeListener(evt, fn) { return this.off(evt, fn); }
    emit(evt, ...args) {
      const list = this._events.get(evt);
      if (!list || !list.length) return false;
      list.slice().forEach(fn => fn(...args));
      return true;
    }
    removeAllListeners(evt) {
      if (evt) this._events.delete(evt); else this._events.clear();
      return this;
    }
  }

  const __osMod = {
    platform: () => 'android',
    arch: () => ${quote(arch)},
    hostname: () => 'nodestudio-android',
    type: () => 'Linux',
    release: () => '6.1-android',
    homedir: () => __projectRoot,
    tmpdir: () => __projectRoot + '/.tmp',
    EOL: '\n',
    cpus: () => [{ model: 'ARMv8 Mobile Core', speed: 2400 }, { model: 'ARMv8 Mobile Core', speed: 2400 }],
    totalmem: () => 4 * 1024 * 1024 * 1024,
    freemem: () => 2 * 1024 * 1024 * 1024
  };

  const __cryptoMod = {
    randomUUID: () => __native.cryptoUuid(),
    randomBytes: (size) => Buffer.from(JSON.parse(__native.cryptoRandomBytes(Number(size) || 16))),
    createHash: (alg) => {
      const chunks = [];
      return {
        update(data) { chunks.push(String(data)); return this; },
        digest(enc = 'hex') { return __native.cryptoHash(String(alg), chunks.join(''), String(enc)); }
      };
    },
    createHmac: (alg, key) => {
      const chunks = [];
      return {
        update(data) { chunks.push(String(data)); return this; },
        digest(enc = 'hex') { return __native.cryptoHmac(String(alg), String(key), chunks.join(''), String(enc)); }
      };
    }
  };

  const __httpMod = {
    STATUS_CODES: { 200: 'OK', 201: 'Created', 204: 'No Content', 400: 'Bad Request', 404: 'Not Found', 500: 'Internal Server Error' },
    createServer: (requestListener) => {
      const emitter = new EventEmitter();
      if (typeof requestListener === 'function') emitter.on('request', requestListener);
      let serverId = null;
      let running = false;
      emitter.listen = (portOrOpts = 3000, hostOrCb, cb) => {
        let requestedPort = 3000;
        let callback = typeof hostOrCb === 'function' ? hostOrCb : cb;
        if (typeof portOrOpts === 'object' && portOrOpts !== null) {
          requestedPort = Number(portOrOpts.port) || 3000;
          if (typeof hostOrCb === 'function') callback = hostOrCb;
        } else {
          requestedPort = Number(portOrOpts) || 3000;
        }
        __activeServers++;
        running = true;
        __native.httpStartServer(requestedPort).then(async (idStr) => {
          try {
            const info = JSON.parse(idStr);
            serverId = info.serverId;
            emitter._actualPort = info.port;
            if (typeof callback === 'function') callback();
            emitter.emit('listening');
            while (running) {
              const rawReq = await __native.httpNextRequest(serverId);
              if (!rawReq) break;
              const reqData = JSON.parse(rawReq);
              const req = new EventEmitter();
              req.method = reqData.method;
              req.url = reqData.url;
              req.headers = reqData.headers || {};
              let statusCode = 200;
              const resHeaders = { 'Content-Type': 'text/plain; charset=utf-8' };
              let ended = false;
              const bodyChunks = [];
              const res = {
                get statusCode() { return statusCode; },
                set statusCode(v) { statusCode = Number(v) || 200; },
                setHeader: (k, v) => { resHeaders[String(k)] = String(v); },
                getHeader: (k) => resHeaders[String(k)],
                writeHead: (code, hdrs) => {
                  statusCode = Number(code) || 200;
                  if (hdrs && typeof hdrs === 'object') Object.assign(resHeaders, hdrs);
                },
                write: (chunk) => { if (chunk !== undefined) bodyChunks.push(String(chunk)); },
                end: (chunk) => {
                  if (ended) return;
                  ended = true;
                  if (chunk !== undefined) bodyChunks.push(Buffer.isBuffer(chunk) ? chunk.toString('utf8') : String(chunk));
                  __native.httpSendResponse(reqData.reqId, statusCode, JSON.stringify(resHeaders), bodyChunks.join(''));
                }
              };
              try {
                emitter.emit('request', req, res);
                if (reqData.body) req.emit('data', Buffer.from(reqData.body));
                req.emit('end');
              } catch (err) {
                const formatted = __formatError(err);
                console.error(formatted);
                if (!ended) __native.httpSendResponse(reqData.reqId, 500, '{"Content-Type":"text/plain"}', formatted);
              }
            }
          } finally {
            if (running) {
              running = false;
              __activeServers = Math.max(0, __activeServers - 1);
            }
          }
        }).catch((err) => {
          if (running) {
            running = false;
            __activeServers = Math.max(0, __activeServers - 1);
          }
          emitter.emit('error', err);
        });
        return emitter;
      };
      emitter.address = () => ({ port: emitter._actualPort || 3000, address: '0.0.0.0', family: 'IPv4' });
      emitter.close = (cb) => {
        if (running) {
          running = false;
          __activeServers = Math.max(0, __activeServers - 1);
          if (serverId) __native.httpStopServer(serverId);
        }
        if (typeof cb === 'function') cb();
      };
      return emitter;
    }
  };

  class SimpleURL {
    constructor(input, base) {
      const raw = String(input || '/');
      const full = base
        ? (/^[a-zA-Z0-9+.-]+:\/\//.test(raw) ? raw : String(base).replace(/\/$/, '') + '/' + raw.replace(/^\//, ''))
        : raw;
      const m = /^([a-zA-Z0-9+.-]+):\/\/([^/:?#]+)(?::(\d+))?([^?#]*)(\?[^#]*)?(#.*)?$/.exec(full);
      if (m) {
        this.href = full;
        this.protocol = m[1] + ':';
        this.hostname = m[2];
        this.port = m[3] || '';
        this.host = this.port ? (this.hostname + ':' + this.port) : this.hostname;
        this.pathname = m[4] || '/';
        this.search = m[5] || '';
        this.hash = m[6] || '';
      } else {
        const rel = /^([^?#]*)(\?[^#]*)?(#.*)?$/.exec(full);
        this.href = full;
        this.protocol = '';
        this.hostname = '';
        this.port = '';
        this.host = '';
        this.pathname = (rel && rel[1]) ? rel[1] : '/';
        this.search = (rel && rel[2]) ? rel[2] : '';
        this.hash = (rel && rel[3]) ? rel[3] : '';
      }
      this.searchParams = new URLSearchParams(this.search);
    }
    toString() { return this.href; }
  }

  class URLSearchParams {
    constructor(init = '') {
      this._map = new Map();
      const s = String(init).replace(/^\?/, '');
      if (s) {
        for (const pair of s.split('&')) {
          const [k, v = ''] = pair.split('=');
          if (k) this._map.set(decodeURIComponent(k), decodeURIComponent(v));
        }
      }
    }
    get(k) { return this._map.get(k) ?? null; }
    set(k, v) { this._map.set(String(k), String(v)); }
    has(k) { return this._map.has(k); }
    toString() {
      return Array.from(this._map.entries())
        .map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v))
        .join('&');
    }
  }
  global.URL = SimpleURL;
  global.URLSearchParams = URLSearchParams;

  const __utilMod = {
    format: (fmt, ...args) => {
      if (typeof fmt !== 'string') return __joinArgs([fmt, ...args]);
      let i = 0;
      const out = fmt.replace(/%[sdjOo]/g, (m) => {
        if (i >= args.length) return m;
        const val = args[i++];
        return m === '%j' ? JSON.stringify(val) : String(val);
      });
      return args.slice(i).reduce((acc, a) => acc + ' ' + __formatArg(a), out);
    },
    inspect: (obj) => __formatArg(obj),
    promisify: (fn) => (...args) => new Promise((resolve, reject) => {
      fn(...args, (err, res) => err ? reject(err) : resolve(res));
    })
  };

  const __assertMod = function(cond, msg) {
    if (!cond) throw new Error(msg || 'AssertionError');
  };
  __assertMod.strictEqual = (a, b, msg) => { if (a !== b) throw new Error(msg || (a + ' !== ' + b)); };
  __assertMod.deepStrictEqual = (a, b, msg) => {
    if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(msg || 'Deep strict equal failed');
  };

  const __builtins = {
    fs: __fsMod,
    'fs/promises': __fsMod.promises,
    path: __pathMod,
    os: __osMod,
    crypto: __cryptoMod,
    http: __httpMod,
    https: __httpMod,
    events: Object.assign(EventEmitter, { EventEmitter }),
    url: {
      URL: SimpleURL,
      URLSearchParams,
      parse: (u, parseQuery) => {
        const parsed = new SimpleURL(u);
        parsed.query = parseQuery ? Object.fromEntries(parsed.searchParams._map.entries()) : parsed.search.replace(/^\?/, '');
        return parsed;
      }
    },
    util: __utilMod,
    buffer: { Buffer },
    querystring: {
      parse: (s) => Object.fromEntries(new URLSearchParams(s)._map.entries()),
      stringify: (obj) => Object.entries(obj || {}).map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&')
    },
    assert: __assertMod,
    timers: { setTimeout, clearTimeout, setInterval, clearInterval, setImmediate, clearImmediate },
    stream: { EventEmitter },
    process: global.process
  };

  const __moduleCache = new Map();

  function __resolveModulePath(id, baseDir) {
    const clean = id.startsWith('node:') ? id.slice(5) : id;
    if (__builtins[clean]) return { builtin: clean };

    if (id.startsWith('./') || id.startsWith('../') || id.startsWith('/')) {
      const candidate = __pathMod.resolve(baseDir, id);
      const tries = [candidate, candidate + '.js', candidate + '.json', candidate + '.cjs', candidate + '/index.js', candidate + '/index.json'];
      for (const t of tries) {
        if (__native.fsExists(t) && !__native.fsIsDir(t)) return { file: t };
      }
      throw new Error("Cannot find module '" + id + "' from '" + baseDir + "'");
    }

    let dir = baseDir;
    while (true) {
      const nmBase = dir + '/node_modules/' + id;
      const pkgFile = nmBase + '/package.json';
      if (__native.fsExists(pkgFile)) {
        const pkg = JSON.parse(__native.fsRead(pkgFile));
        let mainEntry = pkg.main;
        if (!mainEntry && pkg.exports) {
          if (typeof pkg.exports === 'string') mainEntry = pkg.exports;
          else if (typeof pkg.exports['.'] === 'string') mainEntry = pkg.exports['.'];
          else if (pkg.exports['.'] && typeof pkg.exports['.'] === 'object') {
            mainEntry = pkg.exports['.'].require || pkg.exports['.'].default || pkg.exports['.'].import;
          }
        }
        mainEntry = mainEntry || 'index.js';
        const resolved = __pathMod.resolve(nmBase, mainEntry);
        const tries = [resolved, resolved + '.js', resolved + '/index.js'];
        for (const t of tries) {
          if (__native.fsExists(t) && !__native.fsIsDir(t)) return { file: t };
        }
      }
      const directTries = [nmBase + '.js', nmBase + '/index.js'];
      for (const t of directTries) {
        if (__native.fsExists(t) && !__native.fsIsDir(t)) return { file: t };
      }
      if (dir === __projectRoot || dir === '/' || dir.length <= 1) break;
      dir = __pathMod.dirname(dir);
    }
    throw new Error("Cannot find module '" + id + "'. Install it with: npm install " + id);
  }

  function __makeRequire(currentDir) {
    const req = function(id) {
      const resolved = __resolveModulePath(id, currentDir);
      if (resolved.builtin) return __builtins[resolved.builtin];
      const filePath = resolved.file;
      if (__moduleCache.has(filePath)) return __moduleCache.get(filePath).exports;

      const mod = { exports: {}, filename: filePath, id: filePath, loaded: false };
      __moduleCache.set(filePath, mod);

      let content = __native.fsRead(filePath);
      if (filePath.endsWith('.json')) {
        mod.exports = JSON.parse(content);
        mod.loaded = true;
        return mod.exports;
      }
      if (content.startsWith('#!')) {
        content = content.replace(/^#![^\n]*/, '');
      }

      const dirName = __pathMod.dirname(filePath);
      const localRequire = __makeRequire(dirName);
      const wrapped = '(function(exports, require, module, __filename, __dirname) {\n' + content + '\n})';
      let fn;
      try {
        fn = (0, eval)(wrapped);
      } catch (syntaxErr) {
        if (syntaxErr && typeof syntaxErr === 'object') {
          const origName = syntaxErr.name || 'SyntaxError';
          const origMsg = syntaxErr.message || String(syntaxErr);
          syntaxErr.message = origMsg + ' (in ' + filePath + ')';
          if (!syntaxErr.stack) {
            syntaxErr.stack = origName + ': ' + syntaxErr.message + '\n    at ' + filePath;
          }
          throw syntaxErr;
        }
        throw new Error(String(syntaxErr) + ' (in ' + filePath + ')');
      }
      fn(mod.exports, localRequire, mod, filePath, dirName);
      mod.loaded = true;
      return mod.exports;
    };
    req.resolve = (id) => {
      const r = __resolveModulePath(id, currentDir);
      return r.builtin || r.file;
    };
    return req;
  }

  global.__runEntry = async function() {
    try {
      const entryReq = __makeRequire(__pathMod.dirname(__entryFile));
      entryReq(__entryFile);
      while (__activeTimers > 0 || __activeServers > 0) {
        await __native.sleep(50);
      }
    } catch (e) {
      if (e && e.__isProcessExit) return;
      console.error(__formatError(e));
    }
  };
})(globalThis);
        """.trimIndent()
    }

    private fun quote(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "'$escaped'"
    }
}
