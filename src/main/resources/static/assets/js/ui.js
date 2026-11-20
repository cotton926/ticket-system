/* ==========================================================================
   通用 UI 层：转义、格式化、状态映射、Toast、Modal
   ========================================================================== */
window.UI = (function () {
  'use strict';

  /* ---------------------------- 转义 ----------------------------
   * 所有要拼进 innerHTML 的用户数据都必须过这里。
   * 工单标题/内容/评论全部是用户输入，漏一处就是存储型 XSS。
   */
  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function icon(name, cls) {
    return '<svg class="ic ' + (cls || '') + '" aria-hidden="true"><use href="#i-' + name + '"></use></svg>';
  }

  /* --------------------------- 时间处理 --------------------------- */

  function parseTime(s) {
    if (!s) return null;
    // 后端返回 "2026-11-18 20:00:00"，Safari 不认空格分隔，换成 T
    var d = new Date(String(s).replace(' ', 'T'));
    return isNaN(d.getTime()) ? null : d;
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  function fmtTime(s) {
    var d = parseTime(s);
    if (!d) return '—';
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function fmtShort(s) {
    var d = parseTime(s);
    if (!d) return '—';
    var now = new Date();
    if (d.getFullYear() === now.getFullYear()) {
      return pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    }
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function fromNow(s) {
    var d = parseTime(s);
    if (!d) return '—';
    var diff = (Date.now() - d.getTime()) / 1000;
    if (diff < 0) return fmtTime(s);
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 86400 * 2) return '昨天 ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (diff < 86400 * 30) return Math.floor(diff / 86400) + ' 天前';
    return fmtShort(s);
  }

  /** SLA 剩余时间：未超时显示"剩 Xh"，超时显示"超 Xh" */
  function slaText(deadline, timeout) {
    var d = parseTime(deadline);
    if (!d) return { text: '—', over: false };
    var diffMs = d.getTime() - Date.now();
    var abs = Math.abs(diffMs);
    var h = Math.floor(abs / 3600000);
    var m = Math.floor((abs % 3600000) / 60000);
    var span = h >= 24 ? Math.floor(h / 24) + '天' + (h % 24) + 'h' : (h > 0 ? h + 'h' : m + 'm');
    if (timeout || diffMs < 0) return { text: '超时 ' + span, over: true };
    return { text: '剩 ' + span, over: false };
  }

  /* --------------------------- 状态 / 优先级 --------------------------- */

  var STATUS = {
    0:  { label: '草稿',   cls: 'st-0' },
    10: { label: '待分派', cls: 'st-10' },
    20: { label: '待处理', cls: 'st-20' },
    30: { label: '处理中', cls: 'st-30' },
    40: { label: '待验收', cls: 'st-40' },
    50: { label: '已关闭', cls: 'st-50' },
    60: { label: '已驳回', cls: 'st-60' },
    70: { label: '已挂起', cls: 'st-70' }
  };

  var STATUS_COLOR = {
    0: '#98a2b3', 10: '#f59e0b', 20: '#3b82f6', 30: '#6366f1',
    40: '#06b6d4', 50: '#12b76a', 60: '#e5484d', 70: '#8b5cf6'
  };

  var PRIORITY = {
    1: { label: '紧急', cls: 'pri-1' },
    2: { label: '高',   cls: 'pri-2' },
    3: { label: '普通', cls: 'pri-3' },
    4: { label: '低',   cls: 'pri-4' }
  };

  function statusMeta(code) {
    return STATUS[code] || { label: '未知', cls: 'st-0' };
  }

  function priorityMeta(p) {
    return PRIORITY[p] || { label: '—', cls: 'pri-4' };
  }

  function statusBadge(code, text) {
    var m = statusMeta(code);
    return '<span class="badge ' + m.cls + '">' + esc(text || m.label) + '</span>';
  }

  function priorityTag(p) {
    var m = priorityMeta(p);
    return '<span class="pri ' + m.cls + '">' + m.label + '</span>';
  }

  /* ----------------------------- 头像 ----------------------------- */

  function initial(name) {
    if (!name) return '?';
    var s = String(name).trim();
    // 中文头像只取首字（姓）：两字挤在 28px 的小方块里会换行，
    // 而「系统管理员」取末尾会变成「理员」，取首字最稳妥
    return s ? s.charAt(0) : '?';
  }

  function avatarClass(seed) {
    var n = typeof seed === 'number' ? seed : String(seed || '').length;
    return 'a' + (Math.abs(n) % 5 + 1);
  }

  function avatar(name, seed, size) {
    var style = size ? ' style="width:' + size + 'px;height:' + size + 'px;border-radius:' + Math.round(size / 3.6) + 'px"' : '';
    return '<span class="av ' + avatarClass(seed) + '"' + style + '>' + esc(initial(name)) + '</span>';
  }

  /* ----------------------------- Toast ----------------------------- */

  function toast(msg, type) {
    var box = document.getElementById('toasts');
    if (!box) return;
    type = type || 'info';
    var ic = type === 'ok' ? 'check' : (type === 'err' ? 'alert' : 'activity');
    var el = document.createElement('div');
    el.className = 'toast ' + type;
    el.innerHTML = icon(ic) + '<span>' + esc(msg) + '</span>';
    box.appendChild(el);
    setTimeout(function () {
      el.classList.add('out');
      setTimeout(function () { el.remove(); }, 200);
    }, 2600);
  }

  /* ----------------------------- Modal ----------------------------- */

  var openModals = 0;

  function modal(cfg) {
    var root = document.getElementById('modal-root');
    var mask = document.createElement('div');
    mask.className = 'mask';
    mask.innerHTML =
      '<div class="modal' + (cfg.wide ? ' wide' : '') + '" role="dialog" aria-modal="true">' +
        '<div class="modal-head">' +
          '<div><h3>' + esc(cfg.title || '') + '</h3>' +
          (cfg.subtitle ? '<p>' + esc(cfg.subtitle) + '</p>' : '') + '</div>' +
          '<button class="btn btn-ghost btn-icon btn-sm x" type="button" aria-label="关闭">' + icon('x', 'ic-sm') + '</button>' +
        '</div>' +
        '<div class="modal-body">' + (cfg.body || '') + '</div>' +
        (cfg.footer === false ? '' : '<div class="modal-foot">' + (cfg.footer || '') + '</div>') +
      '</div>';

    root.appendChild(mask);
    openModals++;
    document.body.style.overflow = 'hidden';

    function close() {
      if (!mask.parentNode) return;
      mask.remove();
      openModals = Math.max(0, openModals - 1);
      if (openModals === 0) document.body.style.overflow = '';
      document.removeEventListener('keydown', onKey);
      if (cfg.onClose) cfg.onClose();
    }

    function onKey(e) { if (e.key === 'Escape') close(); }

    mask.querySelector('.x').addEventListener('click', close);
    mask.addEventListener('mousedown', function (e) {
      if (e.target === mask && cfg.maskClosable !== false) close();
    });
    document.addEventListener('keydown', onKey);

    // 自动聚焦第一个可输入元素
    setTimeout(function () {
      var first = mask.querySelector('input:not([type=hidden]),textarea,select');
      if (first) first.focus();
    }, 60);

    return { el: mask, close: close };
  }

  function confirm(cfg) {
    return new Promise(function (resolve) {
      var m = modal({
        title: cfg.title,
        subtitle: cfg.subtitle,
        body: '<p style="margin:0;font-size:13.5px;line-height:1.75;color:var(--text-2)">' + esc(cfg.message || '') + '</p>',
        footer:
          '<div class="spacer"></div>' +
          '<button class="btn" data-act="no" type="button">取消</button>' +
          '<button class="btn ' + (cfg.danger ? 'btn-danger' : 'btn-primary') + '" data-act="yes" type="button">' +
          esc(cfg.okText || '确定') + '</button>',
        onClose: function () { resolve(false); }
      });
      m.el.querySelector('[data-act=no]').addEventListener('click', function () { resolve(false); m.close(); });
      m.el.querySelector('[data-act=yes]').addEventListener('click', function () { resolve(true); m.close(); });
    });
  }

  /* ----------------------------- 片段 ----------------------------- */

  function skeletonRows(rows, cols) {
    var out = '';
    for (var i = 0; i < rows; i++) {
      out += '<tr>';
      for (var c = 0; c < cols; c++) {
        var w = [72, 46, 58, 40, 52, 44][c % 6];
        out += '<td><div class="sk" style="height:13px;width:' + w + '%"></div></td>';
      }
      out += '</tr>';
    }
    return out;
  }

  function emptyState(title, desc, actionHtml) {
    return '<div class="empty">' +
      '<svg class="art" viewBox="0 0 96 96" fill="none" aria-hidden="true">' +
        '<rect x="16" y="26" width="64" height="46" rx="8" stroke="#d6dce6" stroke-width="2"/>' +
        '<path d="M16 40h64" stroke="#e3e8ef" stroke-width="2"/>' +
        '<circle cx="26" cy="33" r="2" fill="#d6dce6"/><circle cx="34" cy="33" r="2" fill="#e3e8ef"/>' +
        '<path d="M30 52h24M30 60h16" stroke="#dfe4ec" stroke-width="2.4" stroke-linecap="round"/>' +
        '<circle cx="68" cy="60" r="12" fill="#eef0ff"/>' +
        '<path d="M68 54v6M68 64v.5" stroke="#a5abff" stroke-width="2.6" stroke-linecap="round"/>' +
      '</svg>' +
      '<h4>' + esc(title) + '</h4><p>' + esc(desc || '') + '</p>' + (actionHtml || '') + '</div>';
  }

  function errorBox(msg) {
    return '<div class="empty">' +
      '<h4 style="color:var(--danger)">加载失败</h4><p>' + esc(msg) + '</p>' +
      '<button class="btn" onclick="location.reload()">重新加载</button></div>';
  }

  return {
    esc: esc, icon: icon,
    parseTime: parseTime, fmtTime: fmtTime, fmtShort: fmtShort, fromNow: fromNow, slaText: slaText,
    statusMeta: statusMeta, priorityMeta: priorityMeta, statusBadge: statusBadge, priorityTag: priorityTag,
    STATUS: STATUS, STATUS_COLOR: STATUS_COLOR, PRIORITY: PRIORITY,
    initial: initial, avatar: avatar, avatarClass: avatarClass,
    toast: toast, modal: modal, confirm: confirm,
    skeletonRows: skeletonRows, emptyState: emptyState, errorBox: errorBox
  };
})();
