/* ==========================================================================
   API 客户端
   - 统一解包后端的 Result<T>：code=0 取 data，否则抛 ApiError(message)
   - 20001(登录过期) 全局拦截，清 token 回登录页
   ========================================================================== */
window.Api = (function () {
  'use strict';

  var BASE = '/api/v1';
  var TOKEN_KEY = 'ticket.token';
  var USER_KEY = 'ticket.user';

  function ApiError(message, code, traceId) {
    this.name = 'ApiError';
    this.message = message;
    this.code = code;
    this.traceId = traceId;
  }
  ApiError.prototype = Object.create(Error.prototype);

  function getToken() {
    try { return localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { return ''; }
  }

  function setSession(token, user) {
    try {
      localStorage.setItem(TOKEN_KEY, token || '');
      localStorage.setItem(USER_KEY, JSON.stringify(user || null));
    } catch (e) { /* 隐私模式下 localStorage 可能不可写，静默降级 */ }
  }

  function getUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch (e) { return null; }
  }

  function clearSession() {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch (e) { /* ignore */ }
  }

  /** 把 {a:1,b:[2,3]} 拼成查询串，跳过 null/undefined/空串 */
  function qs(params) {
    if (!params) return '';
    var parts = [];
    Object.keys(params).forEach(function (k) {
      var v = params[k];
      if (v === null || v === undefined || v === '') return;
      if (Array.isArray(v)) {
        v.forEach(function (item) {
          if (item !== null && item !== undefined && item !== '') {
            parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(item));
          }
        });
      } else {
        parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
      }
    });
    return parts.length ? '?' + parts.join('&') : '';
  }

  function request(method, path, opts) {
    opts = opts || {};
    var url = BASE + path + qs(opts.query);
    var headers = { 'Accept': 'application/json' };
    var token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (opts.headers) Object.keys(opts.headers).forEach(function (k) { headers[k] = opts.headers[k]; });

    return fetch(url, {
      method: method,
      headers: headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body)
    }).then(function (res) {
      return res.json().catch(function () {
        throw new ApiError('服务返回了无法解析的内容（HTTP ' + res.status + '）', res.status);
      }).then(function (body) {
        if (body && body.code === 0) return body.data;

        var code = body ? body.code : res.status;
        // 登录过期 / 未登录：统一踢回登录页，避免每个页面各写一遍
        if (code === 20001) {
          clearSession();
          if (window.App && App.onUnauthorized) App.onUnauthorized();
          throw new ApiError((body && body.message) || '登录已过期，请重新登录', code);
        }
        throw new ApiError(
          (body && body.message) || ('请求失败（HTTP ' + res.status + '）'),
          code,
          body && body.traceId
        );
      });
    }, function () {
      throw new ApiError('网络连接失败，请检查服务是否启动', -1);
    });
  }

  return {
    ApiError: ApiError,
    getToken: getToken,
    getUser: getUser,
    setSession: setSession,
    clearSession: clearSession,

    login: function (username, password) {
      return request('POST', '/auth/login', { body: { username: username, password: password } })
        .then(function (data) {
          setSession(data.token, data);
          return data;
        });
    },
    me: function () { return request('GET', '/auth/me'); },
    logout: function () { return request('POST', '/auth/logout'); },

    idempotentToken: function () { return request('GET', '/idempotent-token'); },

    handlers: function () { return request('GET', '/users/handlers'); },

    ticketTypes: function () { return request('GET', '/tickets/types'); },

    tickets: function (query) { return request('GET', '/tickets', { query: query }); },

    ticket: function (id) { return request('GET', '/tickets/' + id); },

    createTicket: function (body, token) {
      return request('POST', '/tickets', {
        body: body,
        headers: { 'X-Idempotent-Token': token }
      });
    },

    act: function (id, action, body) {
      var map = {
        assign: '/assign', accept: '/accept', finish: '/finish', verify: '/verify',
        transfer: '/transfer', suspend: '/suspend', resume: '/resume',
        close: '/close', reopen: '/reopen'
      };
      var seg = map[action];
      if (!seg) throw new ApiError('未知操作: ' + action, -2);
      return request('PUT', '/tickets/' + id + seg, body === undefined ? {} : { body: body });
    },

    comments: function (id) { return request('GET', '/tickets/' + id + '/comments'); },

    addComment: function (id, body) {
      return request('POST', '/tickets/' + id + '/comments', { body: body });
    },

    stats: function () { return request('GET', '/stats/overview'); }
  };
})();
