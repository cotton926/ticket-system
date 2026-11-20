/* ==========================================================================
   应用控制器：路由 / 事件委托 / 弹窗编排
   ========================================================================== */
window.App = (function () {
  'use strict';

  var esc = UI.esc;
  var icon = UI.icon;

  var LIST_DEFAULTS = function () {
    return {
      tab: '',              // 空表示还没定过，首次进入时按角色决定（见 defaultTab）
      pageNum: 1,
      pageSize: 20,
      filters: { status: '', priority: '', typeId: '', keyword: '' },
      rows: [],
      total: 0,
      loading: true,
      error: null
    };
  };

  var S = {
    user: null,
    types: [],
    typeMap: {},
    current: null,       // 当前详情
    seq: 0,              // 请求序号，用于丢弃过期响应
    list: LIST_DEFAULTS()
  };

  function $(sel, root) { return (root || document).querySelector(sel); }
  function mount(html) { $('#app').innerHTML = html; }

  /* ============================== 启动 / 路由 ============================== */

  function boot() {
    if (!Api.getToken()) { renderLogin(); return; }
    Api.me().then(function (u) {
      S.user = u;
      return loadTypes();
    }).then(function () {
      if (!location.hash) { location.hash = '#/tickets'; return; }
      route();
    }).catch(function () {
      renderLogin();
    });
  }

  function route() {
    if (!Api.getToken()) { renderLogin(); return; }
    closeDrawer();
    var h = location.hash || '';
    var m = h.match(/^#\/tickets\/(\d+)$/);
    if (m) { renderDetail(parseInt(m[1], 10)); return; }
    if (h === '#/stats') { renderStats(); return; }
    renderTickets();
  }

  function goto(h) {
    if (location.hash === h) route();
    else location.hash = h;
  }

  function onUnauthorized() {
    renderLogin();
    UI.toast('登录已过期，请重新登录', 'err');
  }

  function loadTypes() {
    if (S.types.length) return Promise.resolve();
    return Api.ticketTypes().then(function (list) {
      S.types = list || [];
      S.typeMap = {};
      S.types.forEach(function (t) { S.typeMap[t.id] = t.name; });
    }).catch(function () {
      // 分类加载失败不阻塞主流程，列表里分类列退化为 "—"
    });
  }

  /* ============================== 登录页 ============================== */

  function renderLogin() {
    // 已经停在登录页就不要再重绘一次，否则会把用户刚输入的内容清空
    if (document.querySelector('#app .login')) return;
    S.user = null;
    // 换账号登录必须清掉上一个账号的列表状态（tab/筛选/页码），
    // 否则新账号进来看到的还是上一个人的视图，角色默认 tab 也不会生效
    S.list = LIST_DEFAULTS();
    S.current = null;
    mount(Views.login());
    var form = $('#login-form');
    var errBox = $('#login-err');
    var btn = $('#login-btn');

    function showErr(msg) {
      errBox.querySelector('span').textContent = msg;
      errBox.classList.add('show');
    }
    function hideErr() { errBox.classList.remove('show'); }

    Array.prototype.forEach.call(document.querySelectorAll('.demo-chip'), function (chip) {
      chip.addEventListener('click', function () {
        $('#login-user').value = chip.getAttribute('data-user');
        $('#login-pass').value = '123456';
        hideErr();
        $('#login-pass').focus();
      });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var u = $('#login-user').value.trim();
      var p = $('#login-pass').value;
      if (!u || !p) { showErr('请输入账号和密码'); return; }
      hideErr();
      btn.disabled = true;
      btn.textContent = '登录中…';

      Api.login(u, p).then(function (user) {
        S.user = user;
        return loadTypes();
      }).then(function () {
        goto('#/tickets');
      }).catch(function (err) {
        btn.disabled = false;
        btn.textContent = '登 录';
        showErr(err.message || '登录失败');
      });
    });
  }

  /* ============================== 工单列表 ============================== */

  function renderTickets() {
    var st = S.list;
    if (!st.tab) st.tab = defaultTab(S.user);
    st.loading = true;
    st.error = null;
    st.rows = [];
    st.total = 0;
    // 分类字典挂到列表状态上，视图层只认一个对象，避免多传一个参数
    st.types = S.types;
    st.typeMap = S.typeMap;

    mount(Views.shell(
      S.user, 'tickets', '工单管理', '服务台 / 工单管理', '',
      Views.list(st)
    ));
    bindPage('tickets');
    fetchTickets();
  }

  /** 首次进入列表时的默认视图：按角色给最可能要看的那一屏，避免首屏是空的 */
  function defaultTab(user) {
    var roles = (user && user.roles) || [];
    if (roles.indexOf('ADMIN') > -1 || roles.indexOf('DISPATCHER') > -1) return 'all';
    if (roles.indexOf('HANDLER') > -1) return 'todo';
    return 'created';
  }

  function fetchTickets() {
    var st = S.list;
    st.loading = true;
    st.error = null;
    updateListBody();

    var q = { tab: st.tab, pageNum: st.pageNum, pageSize: st.pageSize, needTotal: true };
    if (st.filters.status !== '') q.status = st.filters.status;
    if (st.filters.priority !== '') q.priority = st.filters.priority;
    if (st.filters.typeId !== '') q.typeId = st.filters.typeId;
    if (st.filters.keyword) q.keyword = st.filters.keyword;

    var seq = ++S.seq;
    Api.tickets(q).then(function (d) {
      // 连续切换 tab/筛选时响应可能乱序返回，只认最后一次
      if (seq !== S.seq) return;
      st.rows = (d && d.list) || [];
      st.total = (d && d.total) || 0;
      st.loading = false;
      updateListBody();
    }).catch(function (err) {
      if (seq !== S.seq) return;
      st.loading = false;
      st.error = err.message;
      updateListBody();
    });
  }

  function updateListBody() {
    var el = $('#list-body');
    if (!el) return;
    el.innerHTML = Views.listBody(S.list);
    var sum = $('#list-summary');
    if (sum) sum.textContent = Views.summaryText(S.list);
  }

  function switchTab(tab) {
    if (S.list.tab === tab) return;
    S.list.tab = tab;
    S.list.pageNum = 1;
    Array.prototype.forEach.call(document.querySelectorAll('#list-tabs .tab'), function (el) {
      el.classList.toggle('active', el.getAttribute('data-tab') === tab);
    });
    fetchTickets();
  }

  function resetFilters() {
    S.list.filters = { status: '', priority: '', typeId: '', keyword: '' };
    S.list.pageNum = 1;
    $('#f-keyword').value = '';
    $('#f-status').value = '';
    $('#f-priority').value = '';
    $('#f-type').value = '';
    fetchTickets();
  }

  /* ============================== 工单详情 ============================== */

  function renderDetail(id) {
    S.current = null;
    mount(Views.shell(
      S.user, 'tickets', '工单详情', '服务台 / 工单管理 / 详情', '',
      '<div style="display:flex;align-items:center;gap:10px;margin-bottom:16px">' +
        '<a class="btn btn-sm" href="#/tickets">' + icon('arrow-left', 'ic-sm') + '返回列表</a>' +
      '</div>' +
      '<div class="card"><div class="card-pad muted">加载中…</div></div>'
    ));
    bindPage('detail');
    loadDetail(id);
  }

  function loadDetail(id) {
    return Api.ticket(id).then(function (d) {
      S.current = d;
      $('#page').innerHTML = Views.detail(d, { typeMap: S.typeMap });
    }).catch(function (err) {
      $('#page').innerHTML = UI.errorBox(err.message);
    });
  }

  /* ============================== 统计 ============================== */

  function renderStats() {
    mount(Views.shell(
      S.user, 'stats', '统计看板', '服务台 / 统计看板', '',
      '<div class="card"><div class="card-pad muted">加载中…</div></div>'
    ));
    bindPage('stats');
    loadStats();
  }

  function loadStats() {
    Api.stats().then(function (d) {
      $('#page').innerHTML = Views.stats(d || {});
    }).catch(function (err) {
      $('#page').innerHTML = UI.errorBox(err.message);
    });
  }

  /* ============================== 新建工单 ============================== */

  function openCreate() {
    var m = UI.modal({
      title: '新建工单',
      subtitle: '提交后会进入待分派队列',
      wide: true,
      body: Views.createTicketBody(S.types),
      footer:
        '<span class="muted" style="font-size:12px">' + icon('shield', 'ic-sm') + ' 幂等令牌保护，重复提交会被拦截</span>' +
        '<div class="spacer"></div>' +
        '<button class="btn" data-act="cancel" type="button">取消</button>' +
        '<button class="btn btn-primary" data-act="ok" type="button">' + icon('send', 'ic-sm') + '提交工单</button>'
    });

    var okBtn = m.el.querySelector('[data-act=ok]');
    m.el.querySelector('[data-act=cancel]').addEventListener('click', m.close);

    okBtn.addEventListener('click', function () {
      var title = $('#ct-title', m.el).value.trim();
      var typeId = $('#ct-type', m.el).value;
      var priority = $('#ct-priority', m.el).value;
      var content = $('#ct-content', m.el).value.trim();

      if (!title) { UI.toast('请填写工单标题', 'err'); $('#ct-title', m.el).focus(); return; }
      if (!content) { UI.toast('请填写问题描述', 'err'); $('#ct-content', m.el).focus(); return; }
      if (!typeId) { UI.toast('请选择工单分类', 'err'); return; }

      okBtn.disabled = true;
      okBtn.textContent = '提交中…';

      // 先取一次性令牌，再带着 X-Idempotent-Token 提交；
      // 后端切面原子消费，双击/网络重试都不会产生两条工单。
      Api.idempotentToken().then(function (res) {
        var token = (res && res.token) || res;
        return Api.createTicket({
          title: title,
          content: content,
          typeId: parseInt(typeId, 10),
          priority: parseInt(priority, 10)
        }, token);
      }).then(function (created) {
        m.close();
        UI.toast('工单已提交：' + (created.ticketNo || ''), 'ok');
        goto('#/tickets/' + created.id);
      }).catch(function (err) {
        okBtn.disabled = false;
        okBtn.innerHTML = icon('send', 'ic-sm') + '提交工单';
        UI.toast(err.message, 'err');
      });
    });
  }

  /* ============================== 工单操作 ============================== */

  var ENDPOINT = {
    ASSIGN: 'assign', ACCEPT: 'accept', FINISH: 'finish', TRANSFER: 'transfer',
    SUSPEND: 'suspend', RESUME: 'resume', VERIFY_PASS: 'verify', VERIFY_REJECT: 'verify',
    REOPEN: 'reopen', CLOSE: 'close'
  };

  var REMARK_HINT = {
    ASSIGN: '交接说明（可选）',
    ACCEPT: '接单备注（可选）',
    FINISH: '处理结果说明（可选，建议写清怎么解决的）',
    TRANSFER: '转派原因（可选）',
    SUSPEND: '挂起原因（可选）',
    RESUME: '恢复说明（可选）',
    VERIFY_PASS: '验收意见（可选）',
    VERIFY_REJECT: '驳回原因（必填）',
    REOPEN: '重开原因（可选）',
    CLOSE: '关闭原因（可选）'
  };

  function openAction(action) {
    var d = S.current;
    if (!d) return;
    var meta = Views.ACTION_META[action];
    if (!meta) return;

    var needPeople = action === 'ASSIGN' || action === 'TRANSFER';
    var remarkRequired = action === 'VERIFY_REJECT';

    var body = '';
    if (needPeople) {
      body += '<div class="field"><label>选择处理人<span class="req">*</span></label>' +
        '<div id="people-slot"><div class="sk" style="height:56px;border-radius:10px"></div></div></div>';
    }
    body += '<div class="field"><label>' + (remarkRequired ? '驳回原因' : '备注') +
      (remarkRequired ? '<span class="req">*</span>' : '') + '</label>' +
      '<textarea class="textarea" id="act-remark" style="min-height:84px" placeholder="' +
      esc(REMARK_HINT[action] || '') + '"></textarea></div>';

    var m = UI.modal({
      title: meta.label,
      subtitle: d.ticketNo + ' · ' + d.title,
      body: body,
      footer:
        '<div class="spacer"></div>' +
        '<button class="btn" data-act="cancel" type="button">取消</button>' +
        '<button class="btn ' + (meta.cls || 'btn-primary') + '" data-act="ok" type="button">' +
        esc(meta.label) + '</button>'
    });

    var selectedId = null;
    var okBtn = m.el.querySelector('[data-act=ok]');
    m.el.querySelector('[data-act=cancel]').addEventListener('click', m.close);

    if (needPeople) {
      Api.handlers().then(function (list) {
        var slot = $('#people-slot', m.el);
        if (!slot) return;
        slot.innerHTML = Views.peoplePicker(list);
        slot.addEventListener('click', function (e) {
          var p = e.target.closest('.person');
          if (!p) return;
          Array.prototype.forEach.call(slot.querySelectorAll('.person'), function (x) {
            x.classList.remove('sel');
          });
          p.classList.add('sel');
          selectedId = parseInt(p.getAttribute('data-id'), 10);
        });
      }).catch(function (err) {
        var slot = $('#people-slot', m.el);
        if (slot) slot.innerHTML = '<p class="muted" style="font-size:13px;margin:0">人员加载失败：' + esc(err.message) + '</p>';
      });
    }

    okBtn.addEventListener('click', function () {
      if (needPeople && !selectedId) { UI.toast('请选择处理人', 'err'); return; }
      var remark = $('#act-remark', m.el).value.trim();
      if (remarkRequired && !remark) { UI.toast('驳回必须填写原因', 'err'); return; }

      var payload;
      if (needPeople) payload = { assigneeId: selectedId, remark: remark };
      else if (action === 'VERIFY_PASS') payload = { pass: true, remark: remark };
      else if (action === 'VERIFY_REJECT') payload = { pass: false, remark: remark };
      else payload = { remark: remark };

      okBtn.disabled = true;
      okBtn.textContent = '处理中…';

      Api.act(d.id, ENDPOINT[action], payload).then(function () {
        m.close();
        UI.toast(meta.label + '成功', 'ok');
        return loadDetail(d.id);
      }).catch(function (err) {
        okBtn.disabled = false;
        okBtn.textContent = meta.label;
        UI.toast(err.message, 'err');
      });
    });
  }

  function sendComment() {
    var d = S.current;
    if (!d) return;
    var input = $('#cm-input');
    var content = input.value.trim();
    if (!content) { UI.toast('评论内容不能为空', 'err'); input.focus(); return; }

    var btn = $('#cm-send');
    btn.disabled = true;
    Api.addComment(d.id, { content: content, internal: $('#cm-internal').checked })
      .then(function () {
        UI.toast('已发表', 'ok');
        return loadDetail(d.id);
      })
      .catch(function (err) {
        btn.disabled = false;
        UI.toast(err.message, 'err');
      });
  }

  /* ============================== 事件委托 ============================== */

  // 委托挂在 #page 上而不是每次渲染重挂：列表刷新只替换 #list-body 的子节点，
  // #page 本身不销毁，监听器一直有效。
  // 注意必须比对元素引用而不是布尔标记 —— 整页 mount() 会换掉 #page，
  // 用布尔标记会导致新 #page 上没有监听器，整个页面点不动（踩过）。
  var boundEl = null;

  function bindPage(mode) {
    var page = $('#page');
    if (!page) return;
    page.setAttribute('data-mode', mode);
    if (boundEl === page) return;
    boundEl = page;

    page.addEventListener('click', onClick);
    page.addEventListener('change', onChange);
    page.addEventListener('input', onInput);
  }

  function onClick(e) {
    var mode = $('#page').getAttribute('data-mode');

    var actionEl = e.target.closest('[data-action]');
    if (actionEl && mode === 'detail') {
      openAction(actionEl.getAttribute('data-action'));
      return;
    }

    if (e.target.closest('#cm-send')) { sendComment(); return; }

    var tabEl = e.target.closest('[data-tab]');
    if (tabEl) { switchTab(tabEl.getAttribute('data-tab')); return; }

    var pageEl = e.target.closest('[data-page]');
    if (pageEl && !pageEl.disabled) {
      S.list.pageNum = parseInt(pageEl.getAttribute('data-page'), 10);
      fetchTickets();
      return;
    }

    if (e.target.closest('#btn-create') || e.target.closest('#empty-create')) { openCreate(); return; }
    if (e.target.closest('#btn-refresh')) {
      if (mode === 'stats') loadStats();
      else if (mode === 'detail' && S.current) loadDetail(S.current.id);
      else fetchTickets();
      return;
    }
    if (e.target.closest('#f-reset')) { resetFilters(); return; }

    var tr = e.target.closest('tr[data-id]');
    if (tr) { goto('#/tickets/' + tr.getAttribute('data-id')); }
  }

  function onChange(e) {
    var id = e.target.id;
    if (id === 'f-status') { S.list.filters.status = e.target.value; S.list.pageNum = 1; fetchTickets(); }
    else if (id === 'f-priority') { S.list.filters.priority = e.target.value; S.list.pageNum = 1; fetchTickets(); }
    else if (id === 'f-type') { S.list.filters.typeId = e.target.value; S.list.pageNum = 1; fetchTickets(); }
  }

  var kwTimer = null;
  function onInput(e) {
    if (e.target.id !== 'f-keyword') return;
    var v = e.target.value;
    clearTimeout(kwTimer);
    // 输入防抖：每敲一个字都发请求既浪费又会造成结果闪烁
    kwTimer = setTimeout(function () {
      S.list.filters.keyword = v.trim();
      S.list.pageNum = 1;
      fetchTickets();
    }, 380);
  }

  /* 移动端侧边栏抽屉 */
  function closeDrawer() {
    var side = document.querySelector('.side');
    var mask = document.getElementById('side-mask');
    if (side) side.classList.remove('open');
    if (mask) mask.classList.remove('show');
  }

  /* 全局点击：退出登录 + 移动端抽屉开合 */
  document.addEventListener('click', function (e) {
    if (e.target.closest('#side-toggle')) {
      var side = document.querySelector('.side');
      var mask = document.getElementById('side-mask');
      if (side) side.classList.toggle('open');
      if (mask) mask.classList.toggle('show');
      return;
    }
    if (e.target.closest('#side-mask') || e.target.closest('.nav-item')) {
      closeDrawer();
      return;
    }

    if (!e.target.closest('#logout-btn')) return;
    e.preventDefault();
    Api.logout().catch(function () { /* 服务端会话清理失败不影响本地登出 */ }).then(function () {
      Api.clearSession();
      closeDrawer();
      renderLogin();
      UI.toast('已退出登录', 'info');
    });
  });

  window.addEventListener('hashchange', function () {
    if (!Api.getToken()) { renderLogin(); return; }
    route();
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  return { onUnauthorized: onUnauthorized, reload: boot };
})();
