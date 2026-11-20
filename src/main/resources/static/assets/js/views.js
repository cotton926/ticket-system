/* ==========================================================================
   页面视图（纯渲染函数，返回 HTML 字符串；交互由 app.js 绑定）
   ========================================================================== */
window.Views = (function () {
  'use strict';

  var esc = UI.esc;
  var icon = UI.icon;

  var DEMO = [
    { u: 'admin', n: '系统管理员', r: '管理员', c: '#6366f1' },
    { u: 'dispatcher', n: '王调度', r: '调度', c: '#0ea5e9' },
    { u: 'handler', n: '李工', r: '处理人', c: '#14b8a6' },
    { u: 'employee2', n: '赵四', r: '提交人', c: '#f59e0b' }
  ];

  var ACTION_META = {
    ASSIGN:        { label: '分派',     cls: 'btn-primary' },
    ACCEPT:        { label: '接单',     cls: 'btn-primary' },
    FINISH:        { label: '提交完成', cls: 'btn-primary' },
    TRANSFER:      { label: '转派',     cls: '' },
    SUSPEND:       { label: '挂起',     cls: '' },
    RESUME:        { label: '恢复处理', cls: 'btn-primary' },
    VERIFY_PASS:   { label: '验收通过', cls: 'btn-primary' },
    VERIFY_REJECT: { label: '驳回',     cls: 'btn-danger' },
    REOPEN:        { label: '重新打开', cls: '' },
    CLOSE:         { label: '强制关闭', cls: 'btn-danger' }
  };

  var ACTION_ICON = {
    ASSIGN: 'user', ACCEPT: 'check', FINISH: 'send', TRANSFER: 'refresh',
    SUSPEND: 'clock', RESUME: 'zap', VERIFY_PASS: 'check', VERIFY_REJECT: 'x',
    REOPEN: 'refresh', CLOSE: 'lock'
  };

  var TABS = [
    { k: 'todo', l: '待我处理' },
    { k: 'created', l: '我提交的' },
    { k: 'dept', l: '本部门' },
    { k: 'all', l: '全部工单' }
  ];

  /* ============================== 登录 ============================== */

  function login() {
    var chips = DEMO.map(function (d) {
      return '<button type="button" class="demo-chip" data-user="' + esc(d.u) + '" data-name="' + esc(d.n) + '">' +
        '<i style="background:' + d.c + '"></i>' + esc(d.n) + '<span class="muted" style="font-size:11.5px">· ' + esc(d.r) + '</span>' +
        '</button>';
    }).join('');

    var feats = [
      { i: 'layers', t: '8 状态 · 13 条流转规则', d: '状态机集中管控，非法跳转一律拒绝' },
      { i: 'shield', t: '数据权限 + 幂等防护', d: '越权访问拦截，重复提交自动去重' },
      { i: 'activity', t: '全链路流转留痕', d: '每一步操作可追溯，含操作人与备注' }
    ].map(function (f) {
      return '<div class="login-feat"><span class="tile">' + icon(f.i) + '</span>' +
        '<div><b>' + esc(f.t) + '</b><span>' + esc(f.d) + '</span></div></div>';
    }).join('');

    return '' +
      '<div class="login">' +
        '<aside class="login-brand">' +
          '<div class="brand-mark">' +
            '<span class="brand-logo">' + icon('ticket') + '</span>' +
            '<div><div class="brand-name">智慧工单</div><div class="brand-sub">Ticket Service Desk</div></div>' +
          '</div>' +
          '<div class="login-hero">' +
            '<h1>让每一个工单<br>都有<em>明确的去向</em></h1>' +
            '<p>从提交、分派、处理到验收，全流程在线流转。每一步都有记录，每一条都有归属。</p>' +
            '<div class="login-feats">' + feats + '</div>' +
          '</div>' +
          '<div class="login-foot">© 2026 智慧工单管理系统 · 内部服务台</div>' +
        '</aside>' +
        '<main class="login-panel">' +
          '<div class="login-box">' +
            '<h2>登录</h2>' +
            '<p class="lead">使用企业账号进入服务台</p>' +
            '<form class="login-form" id="login-form" autocomplete="on">' +
              '<div class="field">' +
                '<label for="login-user">账号</label>' +
                '<div class="input-icon">' + icon('user', 'ic-sm') +
                  '<input class="input" id="login-user" name="username" autocomplete="username" placeholder="请输入账号">' +
                '</div>' +
              '</div>' +
              '<div class="field">' +
                '<label for="login-pass">密码</label>' +
                '<div class="input-icon">' + icon('lock', 'ic-sm') +
                  '<input class="input" id="login-pass" name="password" type="password" autocomplete="current-password" placeholder="请输入密码">' +
                '</div>' +
              '</div>' +
              '<div class="login-error" id="login-err">' + icon('alert', 'ic-sm') + '<span></span></div>' +
              '<button class="btn btn-primary btn-block" id="login-btn" type="submit" style="height:42px;font-size:14px">登 录</button>' +
            '</form>' +
            '<div class="demo-list">' +
              '<div class="demo-title">演示账号 · 密码均为 123456 · 点击快速填充</div>' +
              '<div class="demo-chips">' + chips + '</div>' +
            '</div>' +
          '</div>' +
        '</main>' +
      '</div>';
  }

  /* ============================== 外壳 ============================== */

  function shell(user, active, title, crumb, topActions, content) {
    var navItem = function (key, href, ic, label) {
      return '<a class="nav-item' + (active === key ? ' active' : '') + '" href="#' + href + '">' +
        icon(ic) + '<span>' + label + '</span></a>';
    };

    document.title = title + ' · 智慧工单';

    // 顶栏只放面包屑：页头已经有主标题，两处都写标题会显得像 bug
    var crumbs = String(crumb || title).split(' / ');
    var crumbHtml = crumbs.map(function (seg, i) {
      var last = i === crumbs.length - 1;
      return (i ? '<span class="sep">/</span>' : '') +
        (last ? '<b>' + esc(seg) + '</b>' : '<span>' + esc(seg) + '</span>');
    }).join('');

    return '' +
      '<div class="shell">' +
        '<aside class="side">' +
          '<div class="side-brand">' +
            '<span class="brand-logo">' + icon('ticket') + '</span>' +
            '<div><div class="brand-name">智慧工单</div><div class="brand-sub">Service Desk</div></div>' +
          '</div>' +
          '<div class="side-label">工作台</div>' +
          '<nav class="nav">' +
            navItem('tickets', '/tickets', 'ticket', '工单管理') +
            navItem('stats', '/stats', 'chart', '统计看板') +
          '</nav>' +
          '<div class="side-foot">' +
            '<div class="side-user">' +
              '<span class="avatar">' + esc(UI.initial(user.realName || user.username)) + '</span>' +
              '<div class="who"><b>' + esc(user.realName || user.username) + '</b>' +
              '<span>' + esc(roleText(user)) + '</span></div>' +
              '<a href="javascript:void 0" id="logout-btn" title="退出登录" aria-label="退出登录">' + icon('logout', 'ic-sm') + '</a>' +
            '</div>' +
          '</div>' +
        '</aside>' +
        '<div class="main">' +
          '<header class="topbar">' +
            '<button class="btn btn-ghost btn-icon btn-sm side-toggle" id="side-toggle" type="button" aria-label="打开菜单">' +
              icon('menu', 'ic-sm') + '</button>' +
            '<div class="crumb">' + crumbHtml + '</div>' +
            '<div class="actions">' + (topActions || '') + '</div>' +
          '</header>' +
          '<div class="page" id="page">' + content + '</div>' +
        '</div>' +
        '<div class="side-mask" id="side-mask"></div>' +
      '</div>';
  }

  function roleText(user) {
    var map = { ADMIN: '管理员', DISPATCHER: '调度', HANDLER: '处理人', EMPLOYEE: '员工' };
    var roles = (user.roles || []).map(function (r) { return map[r] || r; });
    return roles.length ? roles.join(' · ') : '成员';
  }

  /* ============================== 工单列表 ============================== */

  function list(st) {
    var tabs = TABS.map(function (t) {
      return '<button class="tab' + (st.tab === t.k ? ' active' : '') + '" data-tab="' + t.k + '" type="button">' + t.l + '</button>';
    }).join('');

    return '' +
      '<div class="page-head">' +
        '<div class="titles"><h2>工单管理</h2>' +
          '<p id="list-summary">' + summaryText(st) + '</p></div>' +
        '<div class="actions">' +
          '<button class="btn btn-icon" id="btn-refresh" title="刷新" type="button">' + icon('refresh', 'ic-sm') + '</button>' +
          '<button class="btn btn-primary" id="btn-create" type="button">' + icon('plus', 'ic-sm') + '新建工单</button>' +
        '</div>' +
      '</div>' +
      '<div class="card">' +
        '<div style="padding:14px 16px;border-bottom:1px solid var(--border)">' +
          '<div class="tabs" id="list-tabs">' + tabs + '</div>' +
        '</div>' +
        // 筛选栏与表格分开渲染：筛选栏只渲染一次，改条件时只替换 #list-body，
        // 否则每敲一个字重绘一次 DOM，输入框会丢焦点。
        filters(st) +
        '<div id="list-body">' + listBody(st) + '</div>' +
      '</div>';
  }

  function summaryText(st) {
    if (st.loading) return '加载中…';
    if (st.error) return '加载失败';
    return '共 ' + (st.total != null ? st.total : 0) + ' 条记录 · ' + tabDesc(st.tab);
  }

  function filters(st) {
    var statusOpts = '<option value="">全部状态</option>' + Object.keys(UI.STATUS).map(function (k) {
      return '<option value="' + k + '"' + (String(st.filters.status) === k ? ' selected' : '') + '>' + UI.STATUS[k].label + '</option>';
    }).join('');

    var priOpts = '<option value="">全部优先级</option>' + Object.keys(UI.PRIORITY).map(function (k) {
      return '<option value="' + k + '"' + (String(st.filters.priority) === k ? ' selected' : '') + '>' + UI.PRIORITY[k].label + '</option>';
    }).join('');

    var typeOpts = '<option value="">全部分类</option>' + (st.types || []).map(function (t) {
      return '<option value="' + t.id + '"' + (String(st.filters.typeId) === String(t.id) ? ' selected' : '') + '>' + esc(t.name) + '</option>';
    }).join('');

    return '' +
      '<div class="filters">' +
        '<div class="input-icon">' + icon('search', 'ic-sm') +
          '<input class="input" id="f-keyword" placeholder="搜索工单号 / 标题" value="' + esc(st.filters.keyword || '') + '">' +
        '</div>' +
        '<select class="select" id="f-status">' + statusOpts + '</select>' +
        '<select class="select" id="f-priority">' + priOpts + '</select>' +
        '<select class="select" id="f-type">' + typeOpts + '</select>' +
        '<div class="spacer"></div>' +
        '<button class="btn btn-sm btn-ghost" id="f-reset" type="button">' + icon('refresh', 'ic-sm') + '重置</button>' +
      '</div>';
  }

  function listBody(st) {
    if (st.loading) {
      return '<div class="table-wrap"><table class="tb">' + tableHead() +
        '<tbody>' + UI.skeletonRows(8, 7) + '</tbody></table></div>';
    }
    if (st.error) return UI.errorBox(st.error);
    if (!st.rows.length) {
      return UI.emptyState('还没有工单',
        st.tab === 'todo' ? '当前没有需要你处理的工单，去「全部工单」看看？' : '换个筛选条件，或者新建一条工单试试',
        '<button class="btn btn-primary" id="empty-create" type="button">' + icon('plus', 'ic-sm') + '新建工单</button>');
    }
    return '<div class="table-wrap"><table class="tb">' + tableHead() +
      '<tbody>' + st.rows.map(function (r) { return row(r, st); }).join('') + '</tbody></table></div>' + pager(st);
  }

  function tableHead() {
    return '<thead><tr>' +
      '<th class="col-title">工单</th>' +
      '<th class="col-hide">分类</th>' +
      '<th>优先级</th>' +
      '<th>状态</th>' +
      '<th class="col-hide">处理人</th>' +
      '<th class="col-hide">创建时间</th>' +
      '<th>SLA</th>' +
      '</tr></thead>';
  }

  function tabDesc(tab) {
    return ({ todo: '待你接单或处理中的工单', created: '由你提交的工单', dept: '本部门范围内的工单', all: '数据权限内的全部工单' })[tab] || '';
  }

  function row(r, st) {
    var type = (st.typeMap || {})[r.typeId] || '—';
    var sla = UI.slaText(r.deadline, r.timeout);
    var assignee = r.assigneeName
      ? '<div class="tk-person">' + UI.avatar(r.assigneeName, r.assigneeId) + '<span>' + esc(r.assigneeName) + '</span></div>'
      : '<span class="muted">未分派</span>';
    return '<tr data-id="' + r.id + '">' +
      '<td class="col-title">' +
        '<div class="tk-title">' + esc(r.title) + '</div>' +
        '<div class="tk-no">' + esc(r.ticketNo) + '</div>' +
      '</td>' +
      '<td class="col-hide"><span class="chip">' + esc(type) + '</span></td>' +
      '<td>' + UI.priorityTag(r.priority) + '</td>' +
      '<td>' + UI.statusBadge(r.status) + '</td>' +
      '<td class="col-hide">' + assignee + '</td>' +
      '<td class="col-hide nowrap"><span class="muted">' + UI.fmtShort(r.createTime) + '</span></td>' +
      '<td class="nowrap"><span class="sla' + (sla.over ? ' over' : '') + '">' +
        (sla.over ? icon('alert', 'ic-sm') : icon('clock', 'ic-sm')) + esc(sla.text) + '</span></td>' +
      '</tr>';
  }

  function pager(st) {
    var totalPages = Math.max(1, Math.ceil((st.total || 0) / st.pageSize));
    var cur = st.pageNum;
    var nums = [];
    if (totalPages <= 7) {
      for (var i = 1; i <= totalPages; i++) nums.push(i);
    } else if (cur <= 4) {
      nums = [1, 2, 3, 4, 5, '...', totalPages];
    } else if (cur >= totalPages - 3) {
      nums = [1, '...'].concat([totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages]);
    } else {
      nums = [1, '...', cur - 1, cur, cur + 1, '...', totalPages];
    }

    var btn = nums.map(function (n) {
      if (n === '...') return '<span class="pg dots">…</span>';
      return '<button class="pg' + (n === cur ? ' active' : '') + '" data-page="' + n + '" type="button">' + n + '</button>';
    }).join('');

    var from = (cur - 1) * st.pageSize + 1;
    var to = Math.min(cur * st.pageSize, st.total || 0);

    return '<div class="pager">' +
      '<span class="info">' + (st.total ? from + '–' + to + ' / 共 ' + st.total + ' 条' : '暂无数据') + '</span>' +
      '<div class="pages">' +
        '<button class="pg" data-page="' + (cur - 1) + '"' + (cur <= 1 ? ' disabled' : '') + ' type="button" aria-label="上一页">' + icon('left', 'ic-sm') + '</button>' +
        btn +
        '<button class="pg" data-page="' + (cur + 1) + '"' + (cur >= totalPages ? ' disabled' : '') + ' type="button" aria-label="下一页">' + icon('right', 'ic-sm') + '</button>' +
      '</div></div>';
  }

  /* ============================== 工单详情 ============================== */

  function detail(d, ctx) {
    var type = (ctx.typeMap || {})[d.typeId] || '—';
    var actions = (d.availableActions || []).map(function (a) {
      var m = ACTION_META[a];
      if (!m) return '';
      return '<button class="btn ' + m.cls + '" data-action="' + a + '" type="button">' +
        icon(ACTION_ICON[a] || 'zap', 'ic-sm') + m.label + '</button>';
    }).join('');

    var sla = UI.slaText(d.deadline, d.timeout);

    var flows = (d.flows || []).map(function (f) {
      var tone = toneOf(f.action);
      var flow = (f.fromStatusDesc && f.toStatusDesc)
        ? '<span class="tl-flow"><b>' + esc(f.fromStatusDesc) + '</b> → <b>' + esc(f.toStatusDesc) + '</b></span>'
        : '';
      return '<div class="tl-item ' + tone + '">' +
        '<span class="tl-node"><i></i></span>' +
        '<div class="tl-line"><span class="tl-act">' + esc(f.actionDesc || f.action) + '</span>' + flow + '</div>' +
        '<div class="tl-meta"><span>' + esc(f.operatorName || '系统') + '</span><span>' + UI.fmtTime(f.createTime) + '</span></div>' +
        (f.remark ? '<div class="tl-remark">' + esc(f.remark) + '</div>' : '') +
        '</div>';
    }).join('');

    var comments = (d.comments || []).map(function (c) {
      var internal = c.isInternal === 1 || c.isInternal === true;
      return '<div class="cm-item">' + UI.avatar(c.userName, c.userId) +
        '<div class="cm-body">' +
          '<div class="cm-top"><b>' + esc(c.userName || '用户') + '</b>' +
          (internal ? '<span class="chip danger">内部备注</span>' : '') +
          '<time>' + UI.fmtTime(c.createTime) + '</time></div>' +
          '<div class="cm-text">' + esc(c.content) + '</div>' +
        '</div></div>';
    }).join('');

    var prop = function (k, v) {
      return '<div class="prop"><span class="k">' + esc(k) + '</span><span class="v">' + v + '</span></div>';
    };

    var props = '' +
      prop('状态', UI.statusBadge(d.status, d.statusDesc)) +
      prop('优先级', UI.priorityTag(d.priority)) +
      prop('分类', '<span class="chip">' + esc(type) + '</span>') +
      prop('提交人', '<div class="tk-person">' + UI.avatar(d.creatorName, d.creatorId) + '<span>' + esc(d.creatorName || '—') + '</span>' +
        (d.creatorDeptName ? '<span class="muted" style="font-size:12px">· ' + esc(d.creatorDeptName) + '</span>' : '') + '</div>') +
      prop('处理人', d.assigneeName
        ? '<div class="tk-person">' + UI.avatar(d.assigneeName, d.assigneeId) + '<span>' + esc(d.assigneeName) + '</span>' +
          (d.assigneeDeptName ? '<span class="muted" style="font-size:12px">· ' + esc(d.assigneeDeptName) + '</span>' : '') + '</div>'
        : '<span class="muted">未分派</span>') +
      prop('创建时间', '<span class="mono muted">' + UI.fmtTime(d.createTime) + '</span>') +
      prop('SLA 截止', '<span class="mono ' + (sla.over ? '' : 'muted') + '" style="' + (sla.over ? 'color:var(--danger);font-weight:600' : '') + '">' +
        UI.fmtTime(d.deadline) + '</span>') +
      (d.assignTime ? prop('接单时间', '<span class="mono muted">' + UI.fmtTime(d.assignTime) + '</span>') : '') +
      (d.finishTime ? prop('完成时间', '<span class="mono muted">' + UI.fmtTime(d.finishTime) + '</span>') : '') +
      (d.closeTime ? prop('关闭时间', '<span class="mono muted">' + UI.fmtTime(d.closeTime) + '</span>') : '');

    var slaBanner = sla.over
      ? '<div class="card" style="margin-bottom:16px;border-color:#f6d5d6;background:var(--danger-soft)">' +
          '<div style="display:flex;gap:10px;align-items:center;padding:12px 16px;color:#b3272c;font-size:13px">' +
          icon('alert', 'ic-sm') + '<b>该工单已超时</b><span class="muted" style="color:#c96b6f">' + esc(sla.text) + '</span></div></div>'
      : '';

    return '' +
      '<div style="display:flex;align-items:center;gap:10px;margin-bottom:16px">' +
        '<a class="btn btn-sm" href="#/tickets">' + icon('arrow-left', 'ic-sm') + '返回列表</a>' +
      '</div>' +
      slaBanner +
      '<div class="detail">' +
        '<div>' +
          '<div class="card">' +
            '<div class="detail-head">' +
              '<div class="grow">' +
                '<div class="no">' + icon('ticket', 'ic-sm') + esc(d.ticketNo) +
                  '<span class="muted" style="margin-left:6px">· ' + UI.fromNow(d.createTime) + '创建</span></div>' +
                '<h2>' + esc(d.title) + '</h2>' +
                '<div class="tags">' + UI.statusBadge(d.status) + UI.priorityTag(d.priority) +
                  '<span class="chip">' + esc(type) + '</span>' +
                  (sla.over ? '<span class="chip danger">' + esc(sla.text) + '</span>' : '') +
                '</div>' +
              '</div>' +
              '<div class="action-bar">' + (actions || '<span class="muted" style="font-size:12.5px">无可执行操作</span>') + '</div>' +
            '</div>' +

            '<div class="section">' +
              '<h4 class="section-title">' + icon('message') + '工单内容</h4>' +
              '<div class="content-body">' + (d.content ? esc(d.content) : '<span class="muted">无描述</span>') + '</div>' +
            '</div>' +

            '<div class="section">' +
              '<h4 class="section-title">' + icon('activity') + '流转记录<span class="num">' + (d.flows || []).length + '</span></h4>' +
              (flows ? '<div class="tl">' + flows + '</div>' : '<p class="muted" style="margin:0;font-size:13px">暂无流转记录</p>') +
            '</div>' +

            '<div class="section">' +
              '<h4 class="section-title">' + icon('message') + '评论与备注<span class="num">' + (d.comments || []).length + '</span></h4>' +
              (comments || '<p class="muted" style="margin:0;font-size:13px">还没有评论</p>') +
              '<div class="cm-box">' +
                '<textarea class="textarea" id="cm-input" placeholder="补充处理说明，或 @ 相关人员…" style="min-height:78px"></textarea>' +
                '<div class="row">' +
                  '<label class="check"><input type="checkbox" id="cm-internal"> 仅内部可见</label>' +
                  '<div class="spacer" style="flex:1"></div>' +
                  '<button class="btn btn-primary btn-sm" id="cm-send" type="button">' + icon('send', 'ic-sm') + '发送</button>' +
                '</div>' +
              '</div>' +
            '</div>' +
          '</div>' +
        '</div>' +

        '<div>' +
          '<div class="card">' +
            '<div class="card-head"><h3>工单属性</h3></div>' +
            '<div class="card-pad" style="padding-top:6px;padding-bottom:10px">' + props + '</div>' +
          '</div>' +
        '</div>' +
      '</div>';
  }

  function toneOf(action) {
    if (!action) return '';
    if (action.indexOf('REJECT') > -1) return 'bad';
    if (action.indexOf('SUSPEND') > -1) return 'warn';
    if (action.indexOf('SUBMIT') > -1 || action.indexOf('ASSIGN') > -1) return 'info';
    return 'done';
  }

  /* ============================== 统计看板 ============================== */

  function stats(s) {
    var kpi = function (cls, ic, label, value, unit, foot) {
      return '<div class="card kpi ' + cls + '">' +
        '<div class="head"><span class="tile">' + icon(ic) + '</span><span class="lbl">' + esc(label) + '</span></div>' +
        '<div class="val">' + esc(value) + (unit ? '<small>' + esc(unit) + '</small>' : '') + '</div>' +
        '<div class="foot">' + esc(foot) + '</div></div>';
    };

    var avg = s.avgCostMinutes;
    var avgText = avg == null ? '—' : (avg >= 60 ? (avg / 60).toFixed(1) : avg);
    var avgUnit = avg == null ? '' : (avg >= 60 ? '小时' : '分钟');

    var kpis = '' +
      kpi('', 'layers', '工单总量', s.total, '', '系统内全部工单') +
      kpi('', 'plus', '今日新增', s.todayCreated, '', '今日 00:00 起提交') +
      kpi('danger', 'alert', '超时工单', s.timeoutCount, '', s.timeoutCount > 0 ? '需要优先处理' : '暂无超时，状态良好') +
      kpi('ok', 'clock', '平均处理时长', avgText, avgUnit, '近 30 天已完成的工单');

    var byStatus = (s.byStatus || []).slice().sort(function (a, b) { return a.status - b.status; });
    var maxCount = Math.max.apply(null, byStatus.map(function (x) { return x.count || 0; }).concat([1]));
    var total = s.total || 1;

    var bars = byStatus.map(function (x) {
      var color = UI.STATUS_COLOR[x.status] || '#98a2b3';
      var pct = Math.max(2, Math.round((x.count || 0) / maxCount * 100));
      var share = Math.round((x.count || 0) / total * 100);
      return '<div class="bar-row">' +
        '<span class="nm"><i class="d" style="background:' + color + '"></i>' + esc(x.statusDesc) + '</span>' +
        '<span class="bar-track"><i class="bar-fill" style="width:' + pct + '%;background:' + color + '"></i></span>' +
        '<span class="num">' + x.count + '<span class="muted" style="font-weight:500"> · ' + share + '%</span></span>' +
        '</div>';
    }).join('');

    var loads = (s.topLoad || []);
    var maxLoad = Math.max.apply(null, loads.map(function (x) { return x.pending || 0; }).concat([1]));
    var rank = loads.map(function (x, i) {
      var name = x.assigneeName || ('用户 #' + x.assigneeId);
      var pct = Math.max(4, Math.round((x.pending || 0) / maxLoad * 100));
      return '<div class="rank-item">' +
        '<span class="rank-no">' + (i + 1) + '</span>' +
        '<span class="avatar" style="width:28px;height:28px;border-radius:8px;font-size:12.5px">' + esc(UI.initial(name)) + '</span>' +
        '<div class="rank-who"><b>' + esc(name) + '</b>' +
          '<span class="bar-track" style="display:block;margin-top:5px"><i class="bar-fill" style="width:' + pct + '%;background:linear-gradient(90deg,#6366f1,#8b5cf6)"></i></span>' +
        '</div>' +
        '<span class="num" style="font-size:13px;font-weight:650;color:var(--text-2)">' + x.pending + ' 单</span>' +
        '</div>';
    }).join('');

    return '' +
      '<div class="page-head">' +
        '<div class="titles"><h2>统计看板</h2><p>实时汇总当前数据权限范围内的工单</p></div>' +
        '<div class="actions"><button class="btn btn-icon" id="btn-refresh" title="刷新" type="button">' + icon('refresh', 'ic-sm') + '</button></div>' +
      '</div>' +
      '<div class="kpis">' + kpis + '</div>' +
      '<div class="grid-2">' +
        '<div class="card">' +
          '<div class="card-head"><h3>状态分布</h3><span class="sub">各状态工单占比</span></div>' +
          '<div class="card-pad"><div class="bars">' + (bars || '<p class="muted" style="margin:0">暂无数据</p>') + '</div></div>' +
        '</div>' +
        '<div class="card">' +
          '<div class="card-head"><h3>处理人负载</h3><span class="sub">待处理 + 处理中 Top 10</span></div>' +
          '<div class="card-pad"><div class="rank">' + (rank || '<p class="muted" style="margin:0">暂无在办工单</p>') + '</div></div>' +
        '</div>' +
      '</div>';
  }

  /* ============================== 弹窗内容 ============================== */

  function createTicketBody(types) {
    var typeOpts = (types || []).map(function (t) {
      return '<option value="' + t.id + '">' + esc(t.name) + (t.slaHours ? '（SLA ' + t.slaHours + 'h）' : '') + '</option>';
    }).join('');

    var priOpts = [1, 2, 3, 4].map(function (p) {
      var m = UI.PRIORITY[p];
      return '<option value="' + p + '"' + (p === 3 ? ' selected' : '') + '>' + m.label + '</option>';
    }).join('');

    return '' +
      '<div class="field"><label>工单标题<span class="req">*</span></label>' +
        '<input class="input" id="ct-title" maxlength="100" placeholder="一句话说清问题，例如：财务部打印机无法连接">' +
      '</div>' +
      '<div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">' +
        '<div class="field"><label>工单分类<span class="req">*</span></label>' +
          '<select class="select" id="ct-type">' + typeOpts + '</select></div>' +
        '<div class="field"><label>优先级<span class="req">*</span></label>' +
          '<select class="select" id="ct-priority">' + priOpts + '</select></div>' +
      '</div>' +
      '<div class="field"><label>问题描述<span class="req">*</span></label>' +
        '<textarea class="textarea" id="ct-content" maxlength="2000" style="min-height:132px" ' +
        'placeholder="补充现象、影响范围、已尝试的操作、期望结果…"></textarea>' +
        '<span class="hint">描述越具体，处理越快</span>' +
      '</div>';
  }

  function peoplePicker(list) {
    if (!list || !list.length) {
      return '<p class="muted" style="font-size:13px;margin:0">暂无可分派人员</p>';
    }
    var roleMap = { HANDLER: '处理人', DISPATCHER: '调度', ADMIN: '管理员' };
    return '<div class="people" id="pp-list">' + list.map(function (u) {
      return '<div class="person" data-id="' + u.id + '">' +
        '<span class="avatar" style="width:28px;height:28px;border-radius:8px;font-size:12.5px">' + esc(UI.initial(u.realName || u.username)) + '</span>' +
        '<div class="who"><b>' + esc(u.realName || u.username) + '</b>' +
        '<span>' + esc(u.deptName || '未分配部门') + ' · ' + esc(roleMap[u.roleCode] || u.roleCode) + '</span></div>' +
        '<span class="tick">' + icon('check', 'ic-sm') + '</span>' +
        '</div>';
    }).join('') + '</div>';
  }

  return {
    login: login, shell: shell,
    list: list, listBody: listBody, summaryText: summaryText,
    detail: detail, stats: stats,
    createTicketBody: createTicketBody, peoplePicker: peoplePicker,
    ACTION_META: ACTION_META, TABS: TABS, DEMO: DEMO
  };
})();
