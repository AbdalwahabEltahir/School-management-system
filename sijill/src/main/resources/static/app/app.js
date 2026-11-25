/* Hafez web app: one page, plain JavaScript, talks to the real /api endpoints.
   What each person may see or do is decided by the server; this file only chooses which screens to offer. */
'use strict';

// =============================================================== helpers
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
class Raw { constructor(s) { this.s = s; } }
const raw = s => new Raw(s);
const val = v => v instanceof Raw ? v.s : Array.isArray(v) ? v.map(val).join('') : esc(v);
/** Builds safe HTML. In Arabic, text between tags and a few readable attributes are translated; values that land
 *  inside a tag (ids, routes, filter values) are never translated, so behaviour does not change with the language. */
const LOC_ATTR = /(placeholder|aria-label|title|alt|data-confirm)="([^"]*)"/g;
function locTag(t) { return t.replace(LOC_ATTR, (m, a, v) => a + '="' + esc(trText(v.replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&'))) + '"'); }
const html = (strs, ...vals) => {
  if (I18N.lang !== 'ar') return raw(strs.reduce((a, s, i) => a + s + (i < vals.length ? val(vals[i]) : ''), ''));
  let out = '', inTag = false, q = null, buf = '';
  const flush = () => { out += inTag ? buf : trText(buf); buf = ''; };
  const flushTag = () => { out += locTag(buf); buf = ''; };
  for (let i = 0; i < strs.length; i++) {
    for (const ch of strs[i]) {
      if (!inTag) { if (ch === '<') { flush(); inTag = true; buf = '<'; } else buf += ch; }
      else { buf += ch; if (q) { if (ch === q) q = null; } else if (ch === '"' || ch === "'") q = ch; else if (ch === '>') { flushTag(); inTag = false; } }
    }
    if (i < vals.length) {
      const v = vals[i];
      if (inTag) buf += val(v);
      else { flush(); out += locVal(v); }
    }
  }
  if (inTag) flushTag(); else flush();
  return raw(out);
};
const locVal = v => v instanceof Raw ? v.s : Array.isArray(v) ? v.map(locVal).join('') : esc(trText(v == null ? '' : String(v)));
/** Static HTML strings (sidebar art, small snippets) through the same translator. */
const loc = s => html([s]);
/** Dates everywhere read the same way, with the month's name: "16 Nov 2026" (Arabic month names in Arabic).
 *  A plain "2026-11-16" is a calendar day, so it is read as local midnight and never shifts a day. */
const toDate = d => typeof d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(d) ? new Date(d + 'T00:00:00') : new Date(d);
const DATE_OPTS = { day: 'numeric', month: 'short', year: 'numeric' };
const fmt = d => d ? toDate(d).toLocaleString(uiLocale(), { ...DATE_OPTS, hour: '2-digit', minute: '2-digit' }) : '—';
const fmtDate = d => d ? toDate(d).toLocaleDateString(uiLocale(), DATE_OPTS) : '—';
const fmtDay = d => d ? toDate(d).toLocaleDateString(uiLocale(), { weekday: 'long', ...DATE_OPTS }) : '—';
const fmtDayShort = d => d ? toDate(d).toLocaleDateString(uiLocale(), { day: 'numeric', month: 'short' }) : '—';
/** A calendar day as yyyy-mm-dd in local time (toISOString would move it to UTC). */
const isoLocal = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
const cookie = n => (document.cookie.split('; ').find(c => c.startsWith(n + '=')) || '').split('=')[1];
const iso = d => d.toISOString().slice(0, 10);
const monday = () => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return iso(d); };
const title = s => String(s || '').toLowerCase().replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());

function toast(msg, bad) {
  const t = document.getElementById('toast');
  t.textContent = trText(msg); t.hidden = false; t.style.background = bad ? '#8E1B1B' : '';
  clearTimeout(toast.t); toast.t = setTimeout(() => { t.hidden = true; }, 4200);
}

async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') { const t = cookie('XSRF-TOKEN'); if (t) headers['X-XSRF-TOKEN'] = decodeURIComponent(t); }
  const r = await fetch(path, { method, headers, credentials: 'same-origin', redirect: 'manual', body: body !== undefined ? JSON.stringify(body) : undefined });
  const type = r.headers.get('content-type') || '';
  const data = type.includes('json') ? await r.json().catch(() => null) : null;
  if (r.type === 'opaqueredirect' || r.redirected || (r.ok && type.includes('html'))) { const e = new Error('Not signed in'); e.status = 401; throw e; }
  if (!r.ok) { const e = new Error((data && data.message) || ('Request failed (' + r.status + ')')); e.status = r.status; e.body = data; throw e; }
  return data;
}
const get = p => api(p);
const post = (p, body) => api(p, { method: 'POST', body: body ?? {} });
const put = (p, body) => api(p, { method: 'PUT', body });
const patch = (p, body) => api(p, { method: 'PATCH', body });
const del = p => api(p, { method: 'DELETE' });

// =============================================================== state and reference data
const S = { me: null, files: null, rules: null, people: null, sites: null, subjects: null, docTypes: null, unread: 0, flash: null };
const ROLE_LABEL = { TEACHER: 'Teacher', COORDINATOR: 'Coordinator', PRINCIPAL: 'Principal', PRINCIPAL_ASSISTANT: 'Principal Assistant', SCHOOL_IT: 'School IT', LEADERSHIP: 'Leadership', EAA_ADMIN: 'EAA IT', ATTENDANCE_OFFICER: 'Attendance Clerk', STUDENT: 'Student', PARENT: 'Parent' };
const OPEN = ['OWNER_NOTIFIED', 'REMINDER_SENT', 'NEEDS_FOLLOW_UP', 'NEEDS_REVIEW', 'OWNER_DISAGREES', 'OWNER_ASKED_FOR_HELP'];
const STATUS = {
  OWNER_NOTIFIED: ['Owner notified', 'b-internal'], REMINDER_SENT: ['Reminder sent', 'b-watch'], NEEDS_FOLLOW_UP: ['Needs follow-up', 'b-bad'],
  NEEDS_REVIEW: ['Needs review', 'b-watch'], OWNER_DISAGREES: ['Owner disagrees', 'b-watch'], OWNER_ASKED_FOR_HELP: ['Asked for help', 'b-watch'],
  FIXED: ['Fixed', 'b-good'], ALLOWED_EXCEPTION: ['Allowed exception', 'b-neutral'], RULE_PAUSED: ['Rule paused', 'b-neutral'], CLOSED_RULE_REMOVED: ['Rule removed', 'b-neutral']
};
const CHECK_TYPES = ['LOCATION', 'PUBLIC_LINK', 'OUTSIDE_SHARING', 'INTERNAL_ACCESS', 'DUPLICATES', 'SHARING_BEFORE_DATE', 'OWNER_LEAVING', 'GENERIC_OWNER', 'DELETED_OR_TRASHED'];
const SENS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];
const ROLES = ['TEACHER', 'COORDINATOR', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT', 'SCHOOL_IT', 'LEADERSHIP', 'EAA_ADMIN', 'ATTENDANCE_OFFICER', 'STUDENT', 'PARENT'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday'];

const isNetworkRole = () => !!S.me && (S.me.role === 'LEADERSHIP' || S.me.role === 'EAA_ADMIN');
const HIDDEN_ROUTES = {
  LEADERSHIP: ['weekly', 'recognition', 'sites'],
  SCHOOL_IT: ['pickup', 'weekly', 'matrix', 'recognition', 'sites'],
  EAA_ADMIN: ['overview']
};
const NAV = {
  EAA_ADMIN: [['incidents', 'Incidents'], ['support', 'Support tickets'], ['rules', 'Rules'], ['sites', 'Schools & settings'], ['connection', 'Connection'], ['approvals', 'Approvals'], ['restores', 'Restores'], ['activity', 'Activity log'], ['notifications', 'Notifications']],
  ATTENDANCE_OFFICER: [['attendance', 'Attendance'], ['pickup', 'Pick-up board'], ['support', 'Support tickets'], ['notices', 'Notices'], ['notifications', 'Notifications']],
  TEACHER: [['today', 'Today'],
    ['#', 'Teaching'], ['attendance', 'Attendance'], ['homework', 'Homework'], ['quizzes', 'Assessments'], ['marks', 'Marks'], ['planning', 'Weekly plans'], ['materials', 'Study materials'], ['classNotices', 'Class notices'],
    ['#', 'Students and families'], ['chat', 'Messages'], ['points', 'Points'], ['matrix', 'Matrix'], ['pickup', 'Pick-up board'],
    ['#', 'My files'], ['mine', 'My issues'], ['files', 'My files'],
    ['#', 'School'], ['notices', 'Notices'], ['approvals', 'Approvals'], ['support', 'Support tickets'], ['notifications', 'Notifications']],
  PARENT: [['children', 'My children'], ['homework', 'Homework'], ['chat', 'Chat with teachers'], ['pickup', 'Pick-up'], ['notices', 'Notices'], ['notifications', 'Notifications']],
  STUDENT: [['mydash', 'My day'], ['homework', 'Homework'], ['quizzes', 'Assessments'], ['results', 'My results'], ['mymaterials', 'Study materials'], ['chat', 'Chat with teachers'], ['notices', 'Notices'], ['notifications', 'Notifications']],
  COORDINATOR: [['dashboard', 'Overview'],
    ['#', 'Academics'], ['weekly', 'Weekly grades'], ['planning', 'Weekly plan review'], ['matrix', 'Matrix'], ['points', 'Points'],
    ['#', 'School'], ['pickup', 'Pick-up board'],
    ['#', 'Files'], ['followup', 'Follow-up'], ['restores', 'Restores'],
    ['#', 'Requests'], ['approvals', 'Approvals'], ['investigations', 'Investigations'], ['support', 'Support tickets'], ['notifications', 'Notifications']],
  PRINCIPAL: [['school', 'My school'], ['metrics', 'School metrics'],
    ['#', 'Academics'], ['weekly', 'Weekly grades'], ['matrix', 'Matrix'], ['planning', 'Weekly plans'], ['points', 'Points'], ['recognition', 'Recognition'],
    ['#', 'Staff'], ['performance', 'Teacher performance'],
    ['#', 'School'], ['achievements', 'Achievements'], ['pickup', 'Pick-up board'], ['approvals', 'Approvals'], ['support', 'Support tickets'],
    ['#', 'Files'], ['issues', 'Issues'], ['files', 'Files'], ['restores', 'Restores'], ['notifications', 'Notifications']],
  PRINCIPAL_ASSISTANT: [['metrics', 'School metrics'],
    ['#', 'Academics'], ['weekly', 'Weekly grades'], ['matrix', 'Matrix'], ['planning', 'Weekly plans'], ['points', 'Points'], ['recognition', 'Recognition'],
    ['#', 'School'], ['achievements', 'Achievements'], ['people', 'People'], ['invitations', 'Invitations'], ['pickup', 'Pick-up board'], ['notices', 'Notices'],
    ['#', 'Requests'], ['approvals', 'Approvals'], ['investigations', 'Investigations'], ['support', 'Support tickets'], ['notifications', 'Notifications']],
  SCHOOL_IT: [['academic', 'Academic setup'], ['noticesAdmin', 'Notices'], ['support', 'Support tickets'], ['invitations', 'Invitations'], ['approvals', 'Approvals'], ['rules', 'Rules'], ['people', 'People'], ['connection', 'Connection'], ['restores', 'Restores'], ['incidents', 'Incidents'], ['activity', 'Activity log'], ['notifications', 'Notifications']],
  LEADERSHIP: [['overview', 'Overview'], ['schools', 'Schools & achievements'], ['incidents', 'Incidents'], ['support', 'Support tickets'], ['rules', 'Rules'], ['connection', 'Connection'], ['activity', 'Activity log'], ['notifications', 'Notifications']]
};

const lazy = (key, path, map) => async () => {
  if (S[key] === null) { try { const l = await get(path); S[key] = map ? Object.fromEntries(l.map(map)) : l; } catch { S[key] = map ? {} : []; } }
  return S[key];
};
const loadFiles = () => {
  if (['SCHOOL_IT', 'LEADERSHIP', 'EAA_ADMIN'].includes(S.me.role)) return Promise.resolve(S.files = S.files || {}); // counts only for these roles
  return lazy('files', S.me.role === 'TEACHER' ? '/api/files/mine' : '/api/files', f => [f.id, f])();
};
const loadRules = lazy('rules', '/api/rules', r => [r.id, r]);
const loadPeople = () => ['SCHOOL_IT', 'COORDINATOR', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT'].includes(S.me.role) ? lazy('people', '/api/people', p => [p.id, p])() : Promise.resolve(S.people = S.people || {});
const loadSites = lazy('sites', '/api/sites', s => [s.id, s]);
const loadSubjects = lazy('subjects', '/api/subjects');
const loadDocTypes = lazy('docTypes', '/api/doc-types');
const invalidate = (...k) => k.forEach(x => { S[x] = null; });

/** Names of files that are not in the browse list (deleted, or subject not known yet): look them up. */
async function nameMissing(items, idOf) {
  const missing = [...new Set(items.map(idOf))].filter(id => id && !(S.files && S.files[id])).slice(0, 30);
  S.files = S.files || {};
  await Promise.all(missing.map(id => get('/api/files/' + id).then(d => { S.files[id] = d.file; }).catch(() => { })));
}
const fileName = id => (S.files && S.files[id] && S.files[id].name) || 'File';
const personName = id => id === S.me.id ? 'You' : (S.people && S.people[id] && S.people[id].displayName) || '';
const NETWORK_ROLES = ['LEADERSHIP', 'EAA_ADMIN'];
const personSite = p => (p && !NETWORK_ROLES.includes(p.role) && p.siteId) ? siteName(p.siteId) : '';
const siteName = id => (S.sites && S.sites[id] && S.sites[id].name) || '';
/** Text the server keeps in both languages, shown in the language on screen (the switch decides, not the e-mail setting). */
/** "Open" on a link is the verb; elsewhere the dictionary reads it as the status (open issues, open tickets). */
const openWord = () => raw(I18N.lang === 'ar' ? 'فتح' : 'Open');
/** Download links: in the Arabic view the file (PDF or CSV) is written in Arabic too. */
const dl = path => path + (I18N.lang === 'ar' ? (path.includes('?') ? '&' : '?') + 'lang=ar' : '');
const inLang = (en, ar) => (I18N.lang === 'ar' ? ar || en : en || ar) || '';
const say = (en, ar) => I18N.lang === 'ar' && ar ? html`<span lang="ar" dir="rtl">${ar}</span>` : html`${en || ar || ''}`;

// =============================================================== small components
const badge = (cls, text) => html`<span class="badge ${cls}">${text}</span>`;
const statusBadge = s => { const [t, c] = STATUS[s] || [title(s), 'b-neutral']; return badge(c, t); };
const sevBadge = s => badge(s === 'URGENT' ? 'b-urgent' : 'b-normal', title(s));
const sensBadge = s => s ? badge('b-' + s.toLowerCase(), title(s)) : badge('b-neutral', 'Not classified');
const isCoord = () => !!S.me && (S.me.role === 'COORDINATOR' || S.me.role === 'SCHOOL_IT'); // coordinator and School IT share the flat staff look
const cxHeading = h => { h = String(h || ''); return /term|timetable|lesson/i.test(h) ? SPI.calendar : /subject/i.test(h) ? SPI.book : /document|file/i.test(h) ? CO_P.file : /student|class|teach|people|section/i.test(h) ? SPI.users : /request|review/i.test(h) ? CO_P.check : null; };
const cxPath = h => cxHeading(h) || ({ followup: CO_P.clock, academic: SPI.calendar, planning: LIC.clip, people: SPI.users, restores: 'M3 12a9 9 0 109-9M3 4v5h5', notifications: LIC.bell, issues: LIC.warn, issue: LIC.warn, files: CO_P.file, file: CO_P.file, review: CO_P.check, prefs: SPI.gear, noticesAdmin: SPI.megaphone, rules: LIC.shield, sites: LIC.school, connection: LIC.plug, incidents: LIC.warn, activity: SPI.pulse, pickup: SPI.users }[S.route] || CO_P.file);
const isPortal = () => !!S.me && (S.me.role === 'STUDENT' || S.me.role === 'PARENT');
const PAGE_ICON = { mydash: 'calendar', children: 'users', results: 'list', mymaterials: 'folder', notices: 'megaphone', notifications: 'bell', prefs: 'gear', pickup: 'users', homework: 'calendar', quizzes: 'star', chat: 'users' };
const empty = t => isCoord() ? html`<div class="cx-empty">${t}</div>` : isPortal() ? html`<div class="sp-empty"><span class="sp-ic sp-ic-soft">${sIc(PAGE_ICON[S.route] || 'book')}</span>${t}</div>` : html`<div class="empty">${t}</div>`;
const card = (heading, sub, body, extra) => isNewLook() ? eaaCard(heading, sub, body, extra) : isCoord()
  ? html`<section class="cx-card"><div class="cx-ch"><span class="cx-ci">${coIcon(cxPath(heading))}</span><div><h2>${heading}</h2>${sub ? html`<small>${sub}</small>` : ''}</div>${extra ? html`<div class="cx-ex">${extra}</div>` : ''}</div>${body}</section>`
  : isPortal()
  ? html`<section class="sp-card"><div class="sp-card-head"><div class="sp-card-title"><span class="sp-ic">${sIc(PAGE_ICON[S.route] || 'book')}</span><div><h2>${heading}</h2>${sub ? html`<small class="sp-card-sub">${sub}</small>` : ''}</div></div>${extra || ''}</div>${body}</section>`
  : html`<section class="card"><div class="card-head"><div><h2>${heading}</h2>${sub ? html`<small>${sub}</small>` : ''}</div>${extra || ''}</div>${body}</section>`;
const LIC = {
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4', warn: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
  clip: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 012-2h2a2 2 0 012 2M9 5a2 2 0 002 2h2a2 2 0 002-2', clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  check: 'M5 13l4 4L19 7', school: 'M3 10l9-6 9 6M5 10v9h14v-9M9 19v-5h6v5', bell: 'M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0', plug: 'M9 2v6M15 2v6M6 8h12v4a6 6 0 01-12 0zM12 18v4',
  list: 'M9 12l2 2 4-4M5 4h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1z', pause: 'M10 5v14M14 5v14'
};
const lico = p => html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${p}"/></svg>`;
const lkpi = (label, value, unit, tone, ic, sub) => html`<div class="lo-kpi lo-${tone}"><span class="lo-kpi-ic">${lico(LIC[ic])}</span><div class="lo-kpi-body"><div class="lo-kpi-label">${label}</div><div class="lo-kpi-val"><b>${value}</b>${unit ? html`<span>${unit}</span>` : ''}</div><div class="lo-kpi-sub">${sub}</div></div></div>`;
const page = (heading, sub, body, actions, kpis) => isNewLook() ? eaaPage(heading, sub, body, actions, kpis) : isCoord()
  ? html`<div class="co"><header class="co-top"><div class="co-title"><span></span><div><h1>${heading}</h1>${sub ? html`<p>${sub}</p>` : ''}</div></div><div class="co-btns cx-act">${actions || ''}</div></header><div class="cx-stack">${body}</div></div>`
  : isPortal()
  ? html`<section class="sp-pagehead"><i class="sp-hero-glow"></i><span class="sp-ic sp-ic-exam">${sIc(PAGE_ICON[S.route] || 'book')}</span><div class="sp-pagehead-text"><h1>${heading}</h1>${sub ? html`<p>${sub}</p>` : ''}</div><div class="row sp-pagehead-act">${actions || ''}</div></section><div class="sp-stack">${body}</div>`
  : isNetworkRole()
  ? html`<div class="lo lp"><header class="lo-hero lo-hero-sm">${raw(LO_ART)}<div class="lo-hero-top"><div class="lo-hero-text"><h1>${heading}</h1>${sub ? html`<p>${sub}</p>` : ''}</div><div class="lo-hero-tools">${actions || ''}</div></div></header>
    <div class="lo-body">${kpis && kpis.length ? html`<section class="lo-kpis">${kpis.map(k => lkpi(...k))}</section>` : ''}<div class="stack-gap">${body}</div></div></div>`
  : html`<div class="page-top"><div class="page-head"><h1 class="page-title">${heading}</h1>${sub ? html`<p class="page-sub">${sub}</p>` : ''}</div><div class="row">${actions || ''}</div></div><div class="stack-gap">${body}</div>`;
const KPI_IC = { brand: 'warn', amber: 'clock', green: 'check', slate: 'list' };
/** A KPI tile. With {@code go} it is a link: pressing it opens the page with the detail and lights up that page in the sidebar. */
const kpiWrap = (go, inner) => go ? html`<a class="kpi-link" href="${go}" data-kpi="1">${inner}</a>` : inner;
const kpi = (label, value, tone, go) => kpiWrap(go, isCoord() ? html`<div class="cx-k cx-k-${tone || 'brand'}"><span>${lico(LIC[KPI_IC[tone] || 'shield'])}</span><b>${value}</b><small>${label}</small></div>` : html`<div class="card kpi"><span class="kpi-ic t-${tone || 'brand'}">${lico(LIC[KPI_IC[tone] || 'shield'])}</span><span class="kpi-label">${label}</span><span class="kpi-value">${value}</span></div>`);
const field = (label, input) => html`<div class="field"><label>${label}</label>${input}</div>`;
const options = (list, sel, label) => list.map(o => html`<option value="${o}" ${o === sel ? raw('selected') : ''}>${label ? label(o) : title(o)}</option>`);
const seg = (name, items, current) => html`<div class="seg">${items.map(([v, t]) => html`<button type="button" class="seg-btn ${v === current ? 'seg-on' : ''}" data-act="filter" data-name="${name}" data-value="${v}">${t}</button>`)}</div>`;

function table(cols, rows, onclick) {
  if (!rows.length) return empty('Nothing to show.');
  return html`<div class="scroll-x"><table class="t"><thead><tr>${cols.map(c => html`<th>${c}</th>`)}</tr></thead><tbody>${rows.map(r => html`<tr class="${r.go ? 'click' : ''}" ${r.go ? raw(`data-go="${esc(r.go)}"`) : ''}>${r.cells.map(c => html`<td>${c}</td>`)}</tr>`)}</tbody></table></div>`;
}

function issueTable(issues) {
  return table(['Issue', 'File', 'Severity', 'Status', 'Opened'], issues.map(i => ({
    go: '#/issue/' + i.id,
    cells: [say(i.detailEn, i.detailAr), fileName(i.fileId), sevBadge(i.severity), statusBadge(i.status), fmtDate(i.openedAt)]
  })));
}

function sparkline(points) {
  if (!points || points.length < 2) return empty('Not enough history yet.');
  const w = 560, h = 140, p = 8, n = points.length;
  const xy = (pt, i) => [p + i * (w - 2 * p) / (n - 1), h - p - (Math.max(0, Math.min(100, pt.score)) / 100) * (h - 2 * p)];
  const d = points.map((pt, i) => xy(pt, i).map(v => v.toFixed(1)).join(',')).join(' ');
  return html`<svg viewBox="0 0 ${w} ${h}" style="width:100%;max-width:${w}px" role="img" aria-label="Health score over time"><polyline fill="none" stroke="#8A1538" stroke-width="2.5" points="${d}"/></svg><div class="muted">${fmtDate(points[0].date)} to ${fmtDate(points[n - 1].date)}, score 0 to 100</div>`;
}

// =============================================================== screens
const SCREENS = {};

// ---- teacher / everyone: issues
SCREENS.mine = async (arg, q) => {
  const filter = q.get('f') || 'ALL_OPEN';
  const [issues] = await Promise.all([get('/api/issues?filter=' + filter), loadFiles()]);
  await nameMissing(issues, i => i.fileId);
  return page('My issues', 'Files of yours that need a fix. Open one to see the steps.',
    card('Issues', null, html`${seg('f', [['ALL_OPEN', 'Open'], ['URGENT', 'Urgent'], ['CLOSED', 'Closed'], ['ALL', 'All']], filter)}<div style="margin-top:12px">${issues.length ? issueTable(issues) : empty('No issues here. Your files follow the rules.')}</div>`));
};

SCREENS.issues = async (arg, q) => {
  const filter = q.get('f') || 'ALL_OPEN';
  const [issues] = await Promise.all([get('/api/issues?filter=' + filter), loadFiles(), loadRules()]);
  await nameMissing(issues, i => i.fileId);
  return page('Issues', S.me.role === 'PRINCIPAL' ? 'Everything in your school (read-only).' : 'Issues for your subject.',
    card('Issues', null, html`${seg('f', [['ALL_OPEN', 'Open'], ['NEEDS_ME', 'Needs me'], ['URGENT', 'Urgent'], ['CLOSED', 'Closed'], ['ALL', 'All']], filter)}<div style="margin-top:12px">${issueTable(issues)}</div>`),
    html`<a class="btn" href="${dl('/api/exports/issues.csv')}">Download CSV</a>`);
};

// ---- coordinator overview: every open issue in the subject, as flat cards and charts
const coIcon = p => html`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${p}"/></svg>`;
const CO_P = { clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z', check: 'M9 12l2 2 4-4M12 21a9 9 0 100-18 9 9 0 000 18z', pie: 'M21 12a9 9 0 11-9-9v9z', bars: 'M4 20V10M10 20V4M16 20v-7M22 20H2', trend: 'M3 17l6-6 4 4 8-8M15 7h6v6', file: 'M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5', help: 'M3 18v-6a9 9 0 0118 0v6M3 18a2 2 0 002 2h1v-6H5a2 2 0 00-2 2zM21 18a2 2 0 01-2 2h-1v-6h1a2 2 0 012 2z' };
const coHead = (ic, h, sub) => html`<div class="co-ch"><span class="co-ci">${coIcon(CO_P[ic])}</span><div><h2>${h}</h2><div>${sub}</div></div></div>`;
const niceMax = n => Math.max(10, Math.ceil(n / 10) * 10);

SCREENS.dashboard = async (arg, q) => {
  const tab = ['All', 'Urgent', 'Normal'].includes(q.get('sev')) ? q.get('sev') : 'All';
  const [all, counts, review] = await Promise.all([get('/api/issues?filter=ALL'), get('/api/follow-up/counts'), get('/api/files/review-queue').catch(() => []), loadFiles(), loadRules(), loadPeople()]);
  const open = all.filter(i => OPEN.includes(i.status)).sort((a, b) => (b.severity === 'URGENT') - (a.severity === 'URGENT') || new Date(b.openedAt) - new Date(a.openedAt));
  const filtered = open.filter(i => tab === 'All' || (tab === 'Urgent') === (i.severity === 'URGENT'));
  const rows = filtered.slice(0, 10);
  await nameMissing(rows.concat(open.filter(i => i.status === 'OWNER_ASKED_FOR_HELP').slice(0, 5)), i => i.fileId);
  const nOpen = counts.open, nUrgent = counts.urgent, nFixed = counts.fixed, nFollow = counts.needFollowUp;
  const totalIssues = nOpen + nFixed, rate = totalIssues ? Math.round(nFixed / totalIssues * 100) : 0;
  const siteLabel = siteName(S.me.siteId) || 'your school';

  const byRule = {};
  open.forEach(i => { const r = byRule[i.ruleId] = byRule[i.ruleId] || { n: 0, urgent: false }; r.n++; if (i.severity === 'URGENT') r.urgent = true; });
  const types = Object.entries(byRule).sort((a, b) => b[1].n - a[1].n).slice(0, 5);
  const tmax = types.length ? types[0][1].n : 1;
  const ruleName = id => { const r = S.rules[id]; return r && r.effective ? r.effective.name : 'Other'; };

  const dayKey = d => new Date(d).toLocaleDateString();
  const dayCount = {}; open.forEach(i => { const k = dayKey(i.openedAt), e = dayCount[k] = dayCount[k] || { n: 0, t: new Date(i.openedAt).setHours(0, 0, 0, 0) }; e.n++; });
  const days = Object.entries(dayCount).sort((a, b) => a[1].t - b[1].t).slice(-5);
  const dmax = niceMax(Math.max(1, ...days.map(d => d[1].n)));

  const today = new Date(); today.setHours(23, 59, 59, 999);
  const series = Array.from({ length: 7 }, (_, k) => { const end = new Date(today); end.setDate(end.getDate() - (6 - k)); const t = end.getTime();
    return { d: end, open: all.filter(i => new Date(i.openedAt).getTime() <= t && (!i.closedAt || new Date(i.closedAt).getTime() > t)).length, fixed: all.filter(i => i.status === 'FIXED' && i.closedAt && new Date(i.closedAt).getTime() <= t).length }; });
  const step = Math.max(5, Math.ceil(Math.max(...series.map(s => Math.max(s.open, s.fixed))) / 4 / 5) * 5), ymax = step * 4;
  const X = k => 40 + k * 644 / 6, Y = v => 194 - v / ymax * 180;
  const line = (key, c) => html`<polyline points="${series.map((s, k) => X(k).toFixed(1) + ',' + Y(s[key]).toFixed(1)).join(' ')}" fill="none" stroke="${c}" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>${series.map((s, k) => html`<circle cx="${X(k)}" cy="${Y(s[key])}" r="${k === 6 ? 5 : 3.5}" fill="${k === 6 ? c : '#fff'}" stroke="${c}" stroke-width="2"/>`)}`;

  const help = open.filter(i => i.status === 'OWNER_ASKED_FOR_HELP').slice(0, 3);
  const sevPill = i => i.severity === 'URGENT' ? ['Urgent', '#6b1730'] : ['Normal', '#7a4d00'];
  const tabs = ['All', 'Urgent', 'Normal'].map(t => html`<button type="button" role="tab" aria-selected="${t === tab}" class="${t === tab ? 'on' : ''}" data-act="filter" data-name="sev" data-value="${t}">${t}</button>`);
  const wm = (p, cls) => html`<span class="co-wm ${cls || ''}">${coIcon(p)}</span>`;

  return html`<div class="co">
    <header class="co-top"><div class="co-title"><span></span><div><h1>Coordinator overview</h1><p>Every file with an issue across your school, in one place.</p></div></div>
      <div class="co-btns"><a class="co-btn" href="${dl('/api/exports/issues.csv')}">Download CSV</a><a class="co-btn co-btn-fill" href="#/review">Review queue →${review.length ? ' (' + review.length + ')' : ''}</a></div></header>

    <section class="co-row">
      <a class="co-k co-k-open kpi-a" href="#/followup" data-kpi="1"><div class="co-k-top"><span>OPEN ISSUES</span><i>${nOpen ? 'Needs review' : 'All clear'}</i></div><div class="co-k-main"><b>${nOpen}</b><span>files waiting for a decision across ${siteLabel}</span></div></a>
      <a class="co-k co-k-follow kpi-a" href="#/followup" data-kpi="1">${wm(CO_P.clock)}<div class="co-k-top"><span>FOLLOW-UP</span><i>${nFollow ? 'Needs you' : 'All clear'}</i></div><b class="co-k-n">${nFollow}</b><div class="co-k-sub">${nFollow ? 'Owners waiting on you' : 'No owners waiting on you'}</div></a>
      <a class="co-k co-k-fixed kpi-a" href="#/followup" data-kpi="1">${wm(CO_P.check)}<div class="co-k-top"><span>FIXED</span><i>Resolved</i></div><b class="co-k-n">${nFixed}</b><div class="co-k-ring"><div><svg viewBox="0 0 36 36" aria-hidden="true"><circle cx="18" cy="18" r="15.9155" fill="none" stroke="rgba(255,255,255,.22)" stroke-width="4"/><circle cx="18" cy="18" r="15.9155" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-dasharray="${rate} 100"/></svg><b>${rate}%</b></div><span>${I18N.lang === 'ar' ? 'من ' + count(totalIssues, 'issue') + ' تم إصلاحها' : 'of ' + count(totalIssues, 'issue') + ' fixed'}</span></div></a>
    </section>

    <section class="co-row">
      <div class="co-c co-c-sev">${coHead('pie', 'Urgent vs normal', 'Open issues by severity')}
        <div class="co-donut"><svg viewBox="0 0 36 36" aria-hidden="true"><circle cx="18" cy="18" r="14.5" fill="none" stroke="#f0c76a" stroke-width="6"/>${nUrgent ? html`<circle cx="18" cy="18" r="14.5" fill="none" stroke="#ff5a4d" stroke-width="6" stroke-linecap="round" stroke-dasharray="${nOpen ? (nUrgent / nOpen * 100).toFixed(1) : 0} 100"/>` : ''}</svg><div><b>${nOpen}</b><span>open issues</span></div></div>
        <div class="co-tiles"><div><small style="color:#ffb4ad">URGENT</small><b>${nUrgent}</b></div><div><small style="color:#f6d98f">NORMAL</small><b>${Math.max(0, nOpen - nUrgent)}</b></div></div></div>
      <div class="co-c co-c-type">${coHead('bars', 'Issues by type', 'What is wrong with the open files')}
        ${types.length ? types.map(([id, r]) => html`<div class="co-type"><div><span>${ruleName(id)}</span><span><i class="${r.urgent ? 'u' : 'n'}">${r.urgent ? 'Urgent' : 'Normal'}</i><b>${r.n}</b></span></div><div class="co-bar"><div class="${r.urgent ? 'u' : 'n'}" style="width:${(r.n / tmax * 100).toFixed(1)}%"></div></div></div>`) : html`<div class="co-none">No open issues.</div>`}</div>
      <div class="co-c co-c-day">${coHead('trend', 'Opened by day', 'New issues found')}
        ${days.length ? html`<div class="co-days"><div class="co-dy"><span>${dmax}</span><span>${dmax / 2}</span><span>0</span></div><div class="co-dplot"><div class="co-dgrid"><i></i><i></i><i></i></div>${days.map(([k, d], idx) => html`<div class="co-dbar"><b>${d.n}</b><div class="${idx === days.length - 1 && days.length > 1 ? 'hot' : ''}" style="height:${(d.n / dmax * 92).toFixed(1)}%"></div></div>`)}</div></div>
        <div class="co-dx">${days.map(([k, d]) => html`<span>${fmtDayShort(new Date(d.t))}</span>`)}</div>` : html`<div class="co-none">No open issues.</div>`}</div>
    </section>

    <section class="co-line"><div class="co-line-h"><div class="co-line-t"><span>${coIcon(CO_P.trend)}</span><div><h2>Open vs fixed over time</h2><div>Last 7 days</div></div></div>
      <div class="co-chips"><span class="o"><i></i>Open now <b>${nOpen}</b></span><span class="f"><i></i>Fixed <b>${nFixed}</b></span></div></div>
      <svg viewBox="0 0 700 220" role="img" aria-label="Line chart of open and fixed issues over the last 7 days">${[0, 1, 2, 3, 4].map(g => html`<line x1="40" x2="684" y1="${194 - g * 45}" y2="${194 - g * 45}" stroke="${g ? '#e4e1db' : '#c9c6c0'}"/><text x="32" y="${198 - g * 45}" text-anchor="end" font-size="12" fill="#4a4a50">${g * step}</text>`)}${series.map((s, k) => html`<text x="${X(k)}" y="214" text-anchor="middle" font-size="12" fill="#4a4a50">${fmtDayShort(s.d)}</text>`)}${line('open', '#6b1730')}${line('fixed', '#14633b')}</svg></section>

    <section class="co-bottom">
      <div class="co-files"><div class="co-files-h"><div class="co-files-t"><span>${coIcon(CO_P.file)}</span><div><h2>Files with issues</h2><div>Open files that need a decision</div></div></div><div class="co-tabs" role="tablist">${tabs}</div></div>
        <div class="co-files-b"><div class="co-scroll"><div class="co-grid">
          <div class="co-gh"><span>Issue</span><span>File</span><span>Severity</span><span>Status</span><span>Opened</span></div>
          ${rows.length ? rows.map(i => { const [sv, fg] = sevPill(i); return html`<div class="co-gr" data-go="#/issue/${i.id}"><span>${say(i.detailEn, i.detailAr)}</span><span class="co-file">${fileName(i.fileId)}</span><span><em style="color:${fg};border-color:${fg}">${sv}</em></span><span>${statusBadge(i.status)}</span><span class="co-date">${fmtDate(i.openedAt)}</span></div>`; }) : html`<div class="co-none">Nothing here. Your files follow the rules.</div>`}
        </div></div><div class="co-foot"><span>Showing ${rows.length} of ${filtered.length}</span><a href="#/issues">Open full issues list →</a></div></div></div>
      <aside class="co-help"><div class="co-help-h"><div>${coIcon(CO_P.help)}<div><h2>Teacher help requests</h2><small>Owners who asked you for help</small></div></div><span>${help.length}</span></div>
        ${help.length ? help.map(i => { const nm = personName(i.ownerId) || 'Owner', ini = nm.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase(), [sv] = sevPill(i); return html`<div class="co-hc ${i.severity === 'URGENT' ? 'u' : ''}">${wm(CO_P.help, 'co-hwm')}<div class="co-hr"><span>${ini}</span><div><b>${nm}</b><small>Opened ${fmtDate(i.openedAt)}</small></div><i>${sv}</i></div><p>${i.ownerNote || say(i.detailEn, i.detailAr)}</p><em>${fileName(i.fileId)}</em><div class="co-hb"><a href="#/issue/${i.id}">Reply</a><button data-act="coordFixed" data-id="${i.id}">Mark done</button></div><small>Waiting for your reply</small></div>`; }) : html`<div class="co-hc co-hc-none"><b>No help requests</b><p>Nobody is waiting on you right now.</p></div>`}</aside>
    </section></div>`;
};

SCREENS.followup = async () => {
  const [issues, counts] = await Promise.all([get('/api/follow-up'), get('/api/follow-up/counts'), loadFiles()]);
  await nameMissing(issues, i => i.fileId);
  return page('Follow-up', 'Issues waiting for you: owners who disagree, asked for help, or have not acted.',
    html`<div class="kpi-grid">${kpi('Open', counts.open, null, '#/dashboard?sev=All')}${kpi('Urgent', counts.urgent, 'brand', '#/dashboard?sev=Urgent')}${kpi('Need follow-up', counts.needFollowUp, 'amber', '#/followup')}${kpi('Fixed', counts.fixed, 'green', '#/dashboard')}</div>
    ${card('Needs you', null, issues.length ? issueTable(issues) : empty('Nothing needs you right now.'))}`,
    html`<button class="btn" data-act="markAllDone" data-confirm="Mark every follow-up as done?">Mark all done</button>`);
};

SCREENS.issue = async id => {
  const issue = await get('/api/issues/' + id);
  const [history, details] = await Promise.all([
    get(`/api/issues/${id}/history`).catch(() => []),
    get('/api/files/' + issue.fileId).catch(() => null), loadRules(), loadPeople()]);
  const rule = S.rules[issue.ruleId];
  const steps = rule && rule.effective ? inLang(rule.effective.fixStepsEn, rule.effective.fixStepsAr) : '';
  const isOpen = OPEN.includes(issue.status);
  const owner = issue.ownerId === S.me.id;
  const coord = S.me.role === 'COORDINATOR';
  const actions = [];
  if (isOpen && owner) actions.push(html`<button class="btn btn-primary" data-act="fixed" data-id="${id}">I've fixed it</button><button class="btn" data-act="help" data-id="${id}">Ask for help</button>`);
  if (isOpen && coord) actions.push(html`<button class="btn" data-act="keepRule" data-id="${id}">Keep the rule</button><button class="btn" data-act="remind" data-id="${id}">Send reminder</button><button class="btn" data-act="coordFixed" data-id="${id}">Mark fixed</button>`);
  const forms = [];
  if (isOpen && owner) forms.push(card('Not right?', 'If the rule should not apply to this file, tell your coordinator why.', html`<form data-form="disagree" data-id="${id}">${field('Your reason', html`<textarea name="note" required></textarea>`)}<button class="btn">I disagree</button></form>`));
  if (isOpen && coord) {
    forms.push(card('Allow an exception', 'The issue stays quiet until the end date.', html`<form data-form="exception" data-id="${id}">${field('Reason', html`<textarea name="reason" required></textarea>`)}${field('Until', html`<input type="date" name="endsOn" required>`)}<button class="btn">Allow exception</button></form>`));
    forms.push(card('Record a follow-up', null, html`<form data-form="followUpIssue" data-id="${id}">${field('Note', html`<input name="note">`)}<button class="btn">Record follow-up</button></form>`));
  }
  return page(issue.severity === 'URGENT' ? 'Urgent issue' : 'Issue', details ? details.file.name : '',
    html`<div class="row">${statusBadge(issue.status)} ${sevBadge(issue.severity)} <span class="muted">Opened ${fmt(issue.openedAt)}${issue.closedAt ? ' · Closed ' + fmt(issue.closedAt) : ''}</span></div>
    ${card('What is wrong', rule && rule.effective ? 'Rule ' + rule.code + ': ' + rule.effective.name : null, html`<p>${say(issue.detailEn, issue.detailAr)}</p>${rule && rule.effective ? html`<p class="muted">${inLang(rule.effective.whyItMatters, rule.effective.whyItMattersAr)}</p>` : ''}${issue.ownerNote ? html`<div class="callout callout-warn">Owner's note: ${issue.ownerNote}</div>` : ''}`)}
    ${steps ? card('How to fix it', 'Only a re-check closes the issue. Press "I\'ve fixed it" when done.', html`<ol class="steps-list">${steps.split('\n').filter(Boolean).map(s => html`<li>${s}</li>`)}</ol>${actions.length ? html`<div class="row" style="margin-top:16px">${actions}</div>` : ''}`)
      : (actions.length ? card('Actions', null, html`<div class="row">${actions}</div>`) : '')}
    ${forms}
    ${details ? card('The file', null, html`<div class="row">${sensBadge(details.file.sensitivity)} <span>${details.file.folderPath || ''}</span> <a href="#/file/${details.file.id}">Open file details</a></div>`) : ''}
    ${card('History', null, history.length ? html`<ul class="timeline">${history.map(e => html`<li><b>${title(e.type)}</b> ${e.toStatus ? html`→ ${statusBadge(e.toStatus)}` : ''} <span class="muted">${fmt(e.at)} ${personName(e.actorId) ? '· ' + personName(e.actorId) : ''}</span>${e.note ? html`<div>${e.note}</div>` : ''}</li>`)}</ul>` : empty('No history yet.'))}`,
    html`<a class="btn" href="javascript:history.back()">Back</a>`);
};

// ---- files
SCREENS.files = async (arg, q) => {
  const files = Object.values(await loadFiles());
  const mine = S.me.role === 'TEACHER';
  return page(mine ? 'My files' : 'Files', mine ? 'Files you own, with what Hafez understood about each.' : 'Files you may see.',
    card('Files', count(files.length, 'file'), table(['Name', 'Folder', 'Drive', 'Sensitivity', 'Last checked'],
      files.map(f => ({ go: '#/file/' + f.id, cells: [f.name + (f.trashed ? ' (deleted)' : ''), f.folderPath || '', title(f.driveType), sensBadge(f.sensitivity), fmt(f.lastCheckedAt)] })))));
};

SCREENS.file = async id => {
  const d = await get('/api/files/' + id);
  const [types] = await Promise.all([loadDocTypes(), loadFiles(), loadRules()]);
  const f = d.file, p = d.profile;
  const classify = S.me.role === 'COORDINATOR' ? card('Tell Hafez what this is', 'Your answer is saved and the file is checked again.', html`<form data-form="classify" data-id="${id}">
    ${field('Document type', html`<select name="docType">${(types || []).map(t => html`<option ${p && p.docType === t.name ? raw('selected') : ''}>${t.name}</option>`)}</select>`)}
    ${field('Sensitivity', html`<select name="sensitivity">${options(SENS, p ? p.sensitivity : f.sensitivity)}</select>`)}
    <div class="field"><label><input type="checkbox" name="hasStudentData" ${p && p.hasStudentData ? raw('checked') : ''}> Contains student data</label><label><input type="checkbox" name="hasHealthData" ${p && p.hasHealthData ? raw('checked') : ''}> Contains health data</label></div>
    <button class="btn btn-primary">Save</button></form>`) : '';
  return page(f.name, (f.folderPath || '') + ' · ' + title(f.driveType) + (f.trashed ? ' · deleted' : ''),
    html`<div class="row">${sensBadge(f.sensitivity)} <span class="muted">Last checked ${fmt(f.lastCheckedAt)}</span></div>
    ${card('What Hafez understood', null, p ? html`<div class="row"><b>${p.docType}</b> ${sensBadge(p.sensitivity)} <span class="muted">${Math.round(p.confidence * 100)}% sure</span> ${p.needsReview ? badge('b-watch', 'Needs review') : ''}</div>
      <ul>${p.evidence.map(e => html`<li>${e}</li>`)}</ul>` : empty('Not classified yet.'))}
    ${classify}
    ${['TEACHER', 'COORDINATOR'].includes(S.me.role) && !f.trashed ? card('Delete this file', 'Removes it from the register. A principal, coordinator or School IT must approve first. Your Google Drive is never touched.', deleteRequestForm(html`<input type="hidden" name="fileId" value="${id}">`)) : ''}
    ${card('Who can open it', null, table(['Who', 'Type', 'Access', 'Outside'], d.permissions.map(x => ({ cells: [x.grantee, title(x.targetType), title(x.accessLevel), x.external ? badge('b-bad', 'Outside') : 'No'] }))))}
    ${card('Issues on this file', null, d.issues.length ? issueTable(d.issues) : empty('No issues.'))}
    ${card('History', null, d.history.length ? table(['When', 'Drive', 'Folder', 'Permissions'], d.history.map(h => ({ cells: [fmt(h.at), title(h.driveType), h.folderPath || '', h.permissionCount] }))) : empty('No snapshots yet.'))}`,
    html`<a class="btn" href="javascript:history.back()">Back</a>`);
};

SCREENS.review = async () => {
  const [items] = await Promise.all([get('/api/files/review-queue'), loadFiles()]);
  // files whose subject is not known yet are not in the browse list, so read their names directly
  await nameMissing(items, i => i.fileId);
  return page('Review queue', 'Files Hafez could not classify with confidence. Your answer teaches the checker.',
    card('Needs a person', null, table(['File', 'Best guess', 'Sensitivity', 'Confidence', 'Evidence'], items.map(i => ({ go: '#/file/' + i.fileId, cells: [fileName(i.fileId), i.docType || 'Unsure', sensBadge(i.sensitivity), Math.round(i.confidence * 100) + '%', (i.evidence || []).join('; ')] })))));
};

// ---- notifications
/** Bell entries keep both languages; older ones have one title ("Arabic | English" when the person chose both). */
const noteTitle = n => {
  if (n.titleEn || n.titleAr) return inLang(n.titleEn, n.titleAr);
  const both = String(n.title || '').split(' | ');
  if (both.length === 2) return inLang(both[1], both[0]);
  return I18N.lang !== 'ar' && /[؀-ۿ]/.test(n.title || '') ? noteToEnglish(n.title) || n.title : n.title;
};
const noteBody = n => inLang(n.body, n.bodyAr);
SCREENS.notifications = async () => {
  const list = await get('/api/notifications');
  if (isPortal()) return page('Notifications', list.some(n => !n.read) ? list.filter(n => !n.read).length + ' new' : 'You are all caught up.', list.length
    ? html`<div class="sp-notes">${list.map(n => html`<div class="${n.read ? '' : 'new'}" ${n.issueId ? raw(`data-act="openNote" data-id="${n.id}" data-issue="${n.issueId}" style="cursor:pointer"`) : ''}><i></i><div><b>${noteTitle(n)}</b>${n.read ? '' : badge('b-bad', 'New')}<p>${noteBody(n)}</p><small>${fmt(n.createdAt)}</small></div></div>`)}</div>`
    : card('Messages', null, empty('No notifications.')), html`<button class="sp-btn" data-act="readAll">Mark all as read</button>`);
  return page('Notifications', null,
    card('Messages', null, list.length ? html`<ul class="timeline">${list.map(n => html`<li ${n.issueId ? raw(`data-act="openNote" data-id="${n.id}" data-issue="${n.issueId}" style="cursor:pointer"`) : ''}><b>${noteTitle(n)}</b> ${n.read ? '' : badge('b-bad', 'New')} <span class="muted">${fmt(n.createdAt)}</span><div>${noteBody(n)}</div></li>`)}</ul>` : empty('No notifications.')),
    html`<button class="btn" data-act="readAll">Mark all as read</button>`,
    [['Unread', list.filter(n => !n.read).length, '', list.some(n => !n.read) ? 'brand' : 'good', 'bell', 'Need a look'], ['Total', list.length, '', 'slate', 'list', 'Messages']]);
};

// ---- lesson plans
SCREENS.planning = async () => {
  const [subs, subjects] = await Promise.all([get('/api/planning'), loadSubjects(), loadPeople()]);
  const role = S.me.role;
  const subjName = id => (subjects.find(s => s.id === id) || {}).name || '';
  const stBadge = s => badge(s === 'APPROVED' ? 'b-good' : s === 'RETURNED' ? 'b-bad' : 'b-watch', title(s));
  const rows = subs.map(s => ({
    cells: [personName(s.teacherId) || 'Teacher', subjName(s.subjectId), s.grade, fmtDate(s.weekStart), title(s.planType), html`<a href="${s.driveLink}" target="_blank" rel="noopener noreferrer">${openWord()}</a>`, stBadge(s.status),
      role === 'COORDINATOR' && s.status === 'SUBMITTED' ? html`<form data-form="review" data-id="${s.id}" class="row"><input class="input" name="reason" placeholder="Reason if returning"><button class="btn btn-sm" name="decision" value="APPROVED">Approve</button><button class="btn btn-sm btn-danger" name="decision" value="RETURNED">Return</button></form>` : '']
  }));
  const submit = role === 'TEACHER' ? card('Submit this week\'s plan', 'Submitting again for the same week sends a returned plan back for review.', html`<form data-form="submitPlan">
    ${field('Subject', html`<select name="subjectId">${subjects.map(s => html`<option value="${s.id}">${s.name}</option>`)}</select>`)}
    ${field('Grade', html`<input name="grade" required placeholder="e.g. 7B">`)}
    ${field('Week starting (Monday)', html`<input type="date" name="weekStart" value="${monday()}" required>`)}
    ${field('Plan type', html`<select name="planType"><option value="Weekly plan">Weekly plan</option><option value="Lesson plan">Lesson plan</option><option value="Unit plan">Unit plan</option></select>`)}
    ${field('Link to the plan in Drive', html`<input type="url" name="driveLink" required placeholder="https://drive.google.com/…">`)}
    <button class="btn btn-primary">Submit</button></form>`) : '';
  const totals = role === 'PRINCIPAL' ? card('Week totals', null, html`<form data-form="totals" class="row">${field('Week starting', html`<input type="date" name="week" value="${monday()}">`)}<button class="btn">Show</button></form><div id="totals"></div>`) : '';
  return page('Weekly plans', role === 'TEACHER' ? 'Submit your plan for each week; your coordinator reviews it.' : 'Every teacher\'s weekly plan and its review status.',
    html`${submit}${card('Submissions', null, table(['Teacher', 'Subject', 'Grade', 'Week', 'Type', 'Plan', 'Status', ''], rows))}${totals}`);
};

// ---- dashboards
/** Hero illustration: Doha skyline, open book and graduation cap, drawn as one SVG. */
const LO_ART = `<svg class="lo-art" viewBox="0 0 900 300" preserveAspectRatio="xMaxYMax slice" aria-hidden="true"><defs>
<radialGradient id="lg" cx=".62" cy="1" r=".75"><stop offset="0" stop-color="#e7a3b6" stop-opacity=".55"/><stop offset=".45" stop-color="#8f2447" stop-opacity=".3"/><stop offset="1" stop-color="#8f2447" stop-opacity="0"/></radialGradient>
<linearGradient id="ltw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c24a6e"/><stop offset="1" stop-color="#5a1a30"/></linearGradient>
<linearGradient id="lwt" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4a1224"/><stop offset="1" stop-color="#17171a"/></linearGradient>
<linearGradient id="lpg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fbeee6"/><stop offset="1" stop-color="#e7c9c0"/></linearGradient>
<pattern id="lst" width="44" height="44" patternUnits="userSpaceOnUse"><g fill="none" stroke="#e7a3b6" stroke-width="1" opacity=".14"><rect x="9" y="9" width="26" height="26"/><rect x="9" y="9" width="26" height="26" transform="rotate(45 22 22)"/><circle cx="22" cy="22" r="5"/></g></pattern>
<linearGradient id="lskf" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="1"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient><mask id="lskm"><rect width="900" height="262" fill="url(#lskf)"/></mask></defs>
<rect width="900" height="262" fill="url(#lg)"/><rect width="900" height="262" fill="url(#lst)" mask="url(#lskm)"/>
<path d="M815 22A27 27 0 1 0 843 64A21 21 0 1 1 815 22Z" fill="#f4e3d7"/>
<g fill="#f4e3d7"><circle cx="760" cy="40" r="1.6"/><circle cx="700" cy="24" r="1.2"/><circle cx="880" cy="110" r="1.4"/><circle cx="470" cy="30" r="1.3"/><circle cx="300" cy="46" r="1.2"/><circle cx="640" cy="64" r="1.2"/></g>
<g fill="#5a1a30" opacity=".55"><rect x="120" y="190" width="30" height="72"/><rect x="154" y="165" width="24" height="97"/><rect x="182" y="200" width="36" height="62"/><rect x="222" y="180" width="26" height="82"/><rect x="252" y="205" width="40" height="57"/><rect x="650" y="190" width="30" height="72"/><rect x="684" y="170" width="22" height="92"/><rect x="812" y="185" width="34" height="77"/></g>
<g fill="url(#ltw)"><path d="M312 262Q322 200 318 130Q316 96 330 70Q344 96 342 130Q338 200 348 262Z"/><path d="M382 262V128Q382 102 400 96Q418 102 418 128V262Z"/><path d="M508 262L511 150Q497 128 506 96Q520 64 534 96Q543 128 529 150L532 262Z"/><rect x="436" y="128" width="30" height="134"/><rect x="470" y="156" width="26" height="106"/><path d="M556 262V110L588 96V262Z"/><rect x="592" y="140" width="28" height="122"/><path d="M624 262V176L660 150V262Z"/><rect x="262" y="170" width="30" height="92"/><rect x="228" y="214" width="30" height="48"/></g>
<g fill="none" stroke="#e7a3b6" stroke-opacity=".4" stroke-width="1"><path d="M382 140L418 160M382 160L418 180M382 180L418 200M382 200L418 220M418 140L382 160M418 160L382 180M418 180L382 200M418 200L382 220"/><ellipse cx="330" cy="80" rx="12" ry="3"/><path d="M317 150H343M319 190H341"/></g>
<ellipse cx="520" cy="92" rx="11" ry="9" fill="#f4c27a" opacity=".85"/><circle cx="520" cy="92" r="22" fill="#f4c27a" opacity=".18"/><rect x="329" y="40" width="2" height="30" fill="#e7a3b6"/><rect x="399" y="82" width="2" height="14" fill="#e7a3b6"/>
<g stroke="#f4c27a" stroke-width="2" stroke-dasharray="2 7" opacity=".8"><path d="M326 140V255M334 140V255M390 130V255M410 130V255M444 140V255M456 140V255M478 165V255M488 165V255M566 118V255M578 118V255M600 150V255M610 150V255M632 170V255M648 170V255M270 180V255M282 180V255"/></g>
<g><rect x="706" y="224" width="70" height="38" fill="#c24a6e"/><rect x="722" y="206" width="38" height="18" fill="#d9698b"/><path d="M730 206Q741 190 752 206Z" fill="#f4e3d7"/><rect x="778" y="214" width="8" height="48" fill="#a8365c"/><path d="M706 224H776" stroke="#f4e3d7" stroke-opacity=".5"/></g>
<g><rect x="806" y="226" width="84" height="36" fill="#f4e3d7"/><path d="M822 226Q848 196 874 226Z" fill="#e7a3b6"/><rect x="845" y="182" width="2" height="26" fill="#f4e3d7"/><path d="M847 182L862 188L847 194Z" fill="#8f2447"/><g fill="#8f2447"><rect x="814" y="236" width="6" height="26"/><rect x="830" y="236" width="6" height="26"/><rect x="858" y="236" width="6" height="26"/><rect x="874" y="236" width="6" height="26"/><path d="M843 262V246A5 5 0 0 1 853 246V262Z"/></g></g>
<rect x="0" y="262" width="900" height="38" fill="url(#lwt)"/>
<g stroke-linecap="round" stroke-width="2" opacity=".5"><path d="M326 270V292M334 276V294" stroke="#c24a6e"/><path d="M400 270V298" stroke="#e7a3b6"/><path d="M520 270V296" stroke="#f4c27a"/><path d="M300 274H360M470 280H590M700 272H790" stroke="#8f2447"/></g>
<g><path d="M432 266Q500 246 560 266V290Q500 272 432 292Z" fill="url(#lpg)"/><path d="M688 266Q620 246 560 266V290Q620 272 688 292Z" fill="url(#lpg)"/><path d="M560 266V290" stroke="#a8365c" stroke-width="2"/><path d="M432 292Q500 272 560 290Q620 272 688 292V298Q620 280 560 298Q500 280 432 298Z" fill="#6b1730"/><g stroke="#c9a79f" stroke-width="1.2" opacity=".9"><path d="M446 272Q500 258 548 272M446 279Q500 265 548 279M574 272Q620 258 674 272M574 279Q620 265 674 279"/></g></g>
<g transform="translate(588 20) rotate(-10)"><path d="M18 34V52Q50 66 82 52V34L50 46Z" fill="#17171a" stroke="#e7a3b6" stroke-width="1.5"/><polygon points="0,26 50,6 100,26 50,46" fill="#17171a" stroke="#e7a3b6" stroke-width="2"/><path d="M50 26L90 30V56" stroke="#f4c27a" stroke-width="2" fill="none"/><circle cx="90" cy="60" r="4" fill="#f4c27a"/></g>
<g fill="#f4e3d7"><path d="M560 120l3 8 8 3-8 3-3 8-3-8-8-3 8-3z"/><path d="M690 80l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/><path d="M470 70l2 5 5 2-5 2-2 5-2-5-5-2 5-2z"/></g></svg>`;
SCREENS.overview = async (arg, q) => {
  const range = rangeOf(q);
  const [o, incidents, urgentIssues] = await Promise.all([get('/api/dashboard/overview'), get('/api/dashboard/incidents').catch(() => []), get('/api/dashboard/urgent-issues').catch(() => [])]);
  const sites = Object.values(await loadSites());
  const siteOf = id => (S.sites[id] || {}).name || '';
  const rate = r => badge(r === 'GOOD' ? 'b-good' : r === 'WATCH' ? 'b-watch' : 'b-bad', title(r));
  const urgentNow = incidents.filter(i => !i.resolvedAt);
  const waiting = sites.filter(s => s.active && !s.firstCheckAt && true);
  const unscored = sites.filter(s => s.active && !s.scored);

  const tile = (label, value, tone, note, go) => html`<${go ? raw('a') : raw('div')} class="card kpi" ${go ? raw(`href="${esc(go)}" style="text-decoration:none;color:inherit"`) : ''}><span class="kpi-ic t-${tone}"></span><span class="kpi-label">${label}</span><span class="kpi-value">${value}</span>${note ? html`<span class="kpi-foot"><b>${note}</b></span>` : ''}</${go ? raw('a') : raw('div')}>`;

  const firstCheck = waiting.length ? card('Waiting for the first check', 'No check has finished for these schools yet. Numbers appear after EAA IT or School IT runs it.',
    table(['School', 'Domain'], waiting.map(s => ({ cells: [s.name, s.domain] })))) : '';

  const noSchools = unscored.length ? card(o.schools.length ? 'Schools not counted in these numbers' : 'No school is scored yet',
    'The totals below add up only the counted schools. A school that holds files but is not counted leaves the KPIs at zero.',
    html`${table(['School', 'Counted'], unscored.map(s => ({ cells: [s.name, 'No'] })))}`) : '';

  const urgentCount = urgentIssues.length + urgentNow.length;
  const urgentCard = card('Urgent right now', urgentCount ? count(urgentIssues.length, 'issue') + ' ' + trText('open and urgent') + ' · ' + count(urgentNow.length, 'incident') + ' ' + trText('open') : 'Nothing urgent is open.',
    html`${urgentIssues.length ? html`<h3>Urgent issues</h3><p class="muted">Which school and which rule. File names stay with the school.</p>${table(['School', 'Rule', 'Status', 'Open since'], urgentIssues.map(i => ({ cells: [i.siteName, (i.ruleCode ? i.ruleCode + ' · ' : '') + i.ruleName, title(i.status), fmt(i.openedAt)] })))}` : ''}
    ${urgentNow.length ? html`<h3>Incidents</h3>${table(['Incident', 'School', 'What happened', 'Detected', ''], urgentNow.map(i => ({ cells: [badge('b-bad', title(i.type)), siteOf(i.siteId) || '', i.description, fmt(i.detectedAt), ''] })))}` : ''}
    ${urgentCount ? '' : empty('No urgent issues or incidents.')}`,
    html`<a class="card-link" href="#/incidents">All incidents</a>`);

  // ---- leadership dashboard design (hero, KPI tiles, school cards, decisions, health, quick actions)
  await loadRules();
  const IC = {
    shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4',
    warn: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z',
    clip: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 012-2h2a2 2 0 012 2M9 5a2 2 0 002 2h2a2 2 0 002-2',
    clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    check: 'M5 13l4 4L19 7',
    drive: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z',
    bolt: 'M13 3L4 14h7l-1 7 9-11h-7z',
    dl: 'M12 4v11m0 0l-4-4m4 4l4-4M5 20h14',
    list: 'M9 12l2 2 4-4M5 4h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1z'
  };
  const ico = (p, cls) => html`<svg class="${cls || ''}" viewBox="0 0 24 24" aria-hidden="true"><path d="${p}"/></svg>`;
  const schools = [...o.schools].sort((a, b) => a.score - b.score);
  const band = r => r === 'GOOD' ? ['good', '✓ Good'] : r === 'WATCH' ? ['watch', '◆ Watch'] : ['bad', '! Needs attention'];
  const totalFiles = schools.reduce((a, s) => a + s.filesChecked, 0);
  const netScore = totalFiles ? Math.max(0, 100 - schools.reduce((a, s) => a + s.openNormal + 3 * s.openUrgent, 0) / totalFiles * 100) : null;
  const netBand = netScore === null ? null : netScore >= 85 ? 'good' : netScore >= 70 ? 'watch' : 'bad';
  const counts = { good: 0, watch: 0, bad: 0 };
  schools.forEach(s => { counts[band(s.rating)[0]]++; });
  const hour = new Date().getHours();
  const first = (S.me.displayName || '').split(' ')[0];

  const kpi2 = (label, value, unit, tone, ic, sub, sp, go) => html`<${go ? raw('a') : raw('div')} class="lo-kpi lo-${tone}" ${go ? raw(`href="${esc(go)}"`) : ''}><span class="lo-kpi-ic">${ico(ic)}</span><div class="lo-kpi-body"><div class="lo-kpi-label">${label}</div><div class="lo-kpi-val"><b>${value}</b>${unit ? html`<span>${unit}</span>` : ''}</div><div class="lo-kpi-sub">${sub}</div></div>${sp || ''}</${go ? raw('a') : raw('div')}>`;

  const pendingRules = Object.values(S.rules || {}).filter(r => r.pending);
  const decide = false && pendingRules.length ? html`<section class="lo-card" aria-label="Needs your decision"><div class="lo-card-head"><h2>Needs your decision</h2><span class="lo-pill">${pendingRules.length}</span><small>Rule changes waiting for approval</small></div>
    <div class="lo-decisions">${pendingRules.map(r => html`<div class="lo-decision"><div class="lo-dtype">Rule change</div><div class="lo-drule">${r.code} · ${(r.pending || {}).name || ''}</div><div class="lo-dwhat">${inLang((r.pending || {}).whyItMatters, (r.pending || {}).whyItMattersAr) || 'A new version of this rule is waiting to be approved.'}</div>
      <div class="row" style="margin-top:12px"><a class="btn btn-sm" href="#/rules">Review</a><button class="btn btn-sm btn-primary" data-act="approveRule" data-id="${r.id}" data-version="${r.pending.id}">Approve</button></div></div>`)}</div></section>` : '';

  const donut = (() => {
    const total = schools.length; if (!total) return empty('No scored school yet.');
    let off = 0; const col = { good: '#12A66E', watch: '#F2A20C', bad: '#B3261E' };
    const arcs = ['good', 'watch', 'bad'].filter(k => counts[k]).map(k => { const len = counts[k] / total * 100; const a = html`<circle cx="18" cy="18" r="15.9155" stroke="${col[k]}" stroke-dasharray="${len} ${100 - len}" stroke-dashoffset="${-off}"/>`; off += len; return a; });
    return html`<div class="lo-donut-wrap"><div class="lo-donut"><svg viewBox="0 0 36 36" aria-hidden="true"><g fill="none" stroke-width="4"><circle cx="18" cy="18" r="15.9155" stroke="#ECEEF4"/>${arcs}</g></svg><div><b>${netScore === null ? '—' : netScore.toFixed(1)}</b><span>Overall</span></div></div>
      <div class="lo-legend"><div><i style="background:${col.good}"></i><span>Good</span><b>${counts.good}</b></div><div><i style="background:${col.watch}"></i><span>Watch</span><b>${counts.watch}</b></div><div><i style="background:${col.bad}"></i><span>Needs attention</span><b>${counts.bad}</b></div></div></div>`;
  })();

  const schoolCards = schools.length ? html`<div class="lo-schools">${schools.map(s => { const [k, label] = band(s.rating); return html`<a class="lo-school lo-s-${k}" href="#/site/${s.siteId}"><b class="lo-sname">${s.siteName}</b><div class="lo-sscore"><b>${Math.round(s.score)}</b></div>
      <div class="lo-meter"><i style="width:${Math.max(0, Math.min(100, s.score))}%"></i></div><span class="lo-band">${label}</span>
      <div class="lo-sfoot"><span class="${s.openUrgent ? 'lo-red' : ''}">▲ ${s.openUrgent} urgent</span><span>${count(s.filesChecked, 'file')}</span></div></a>`; })}</div>` : empty('No scored school yet.');

  const actions = [['#/rules', 'Review rules' + (pendingRules.length ? ' (' + pendingRules.length + ')' : ''), IC.list], ['#/incidents', 'Incidents' + (urgentNow.length ? ' (' + urgentNow.length + ')' : ''), IC.warn], ['#/activity', 'Activity log', IC.clock]];
  if (S.me.canDownloadWeeklyReport) actions.splice(1, 0, [dl('/api/dashboard/weekly-report.pdf'), 'Weekly report (PDF)', IC.dl]);

  // ---- network trend from each school's snapshots (sums the schools by date)
  const trends = await Promise.all(schools.map(s => get(`/api/dashboard/sites/${s.siteId}/score?days=${range}`).then(r => r.trend || []).catch(() => [])));
  const byDate = {};
  trends.forEach(t => t.forEach(p => { const d = byDate[p.date] = byDate[p.date] || { open: 0, urgent: 0, normal: 0, files: 0 }; d.open += p.openNormal + p.openUrgent; d.urgent += p.openUrgent; d.normal += p.openNormal; d.files += p.filesChecked; }));
  const dates = Object.keys(byDate).sort();
  const series = dates.map(d => byDate[d]);
  const pick = dates.length <= 5 ? dates : [0, 1, 2, 3, 4].map(i => dates[Math.round(i * (dates.length - 1) / 4)]);
  const healthSeries = series.map(d => d.files ? Math.max(0, 100 - (d.normal + 3 * d.urgent) / d.files * 100) : 0);
  const shortD = d => new Date(d).toLocaleDateString(uiLocale(), { month: 'short', day: 'numeric' });
  /** Detailed trend: gridlines, area, line, a dot per day, the latest value called out, and min / max / date range under it. */
  const spark = (vals, dec) => {
    if (!vals || vals.length < 2) return '';
    const dp = dec || 0, W = 220, H = 84, padT = 14, padB = 6, n = vals.length;
    const mn = Math.min(...vals), mx = Math.max(...vals), span = mx - mn || 1;
    const X = i => 6 + i * (W - 12) / (n - 1), Y = v => H - padB - (v - mn) / span * (H - padT - padB);
    const pts = vals.map((v, i) => X(i).toFixed(1) + ',' + Y(v).toFixed(1)).join(' ');
    const li = n - 1, lx = X(li), ly = Y(vals[li]);
    const dots = n <= 16 ? vals.map((v, i) => i === li ? '' : html`<circle cx="${X(i).toFixed(1)}" cy="${Y(v).toFixed(1)}" r="2.4" fill="#fff" stroke="currentColor" stroke-width="1.5"/>`) : '';
    return html`<div class="lo-sp"><svg viewBox="0 0 ${W} ${H}" aria-hidden="true">
      <g stroke="currentColor" stroke-opacity=".14" stroke-dasharray="2 4"><line x1="6" x2="${W - 6}" y1="${padT}" y2="${padT}"/><line x1="6" x2="${W - 6}" y1="${(H + padT - padB) / 2}" y2="${(H + padT - padB) / 2}"/><line x1="6" x2="${W - 6}" y1="${H - padB}" y2="${H - padB}"/></g>
      <polygon points="${pts} ${W - 6},${H - padB} 6,${H - padB}" fill="currentColor" fill-opacity=".15"/>
      <polyline points="${pts}" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>${dots}
      <circle cx="${lx.toFixed(1)}" cy="${ly.toFixed(1)}" r="9" fill="currentColor" fill-opacity=".18"/><circle cx="${lx.toFixed(1)}" cy="${ly.toFixed(1)}" r="4.2" fill="currentColor" stroke="#fff" stroke-width="1.5"/></svg>
      <div class="lo-sp-row"><span>${shortD(dates[0])}</span><b>now ${vals[li].toFixed(dp)}</b></div>
      <div class="lo-sp-row lo-sp-lo"><span>low ${mn.toFixed(dp)}</span><span>high ${mx.toFixed(dp)}</span></div></div>`;
  };
  /** Change since the first recorded day, as a coloured chip (goodUp = higher is better). */
  const chg = (vals, goodUp, dec) => {
    if (!vals || vals.length < 2) return '';
    const d = vals[vals.length - 1] - vals[0], good = d === 0 ? null : (d > 0) === goodUp;
    return html`<span class="lo-chip ${good === null ? '' : good ? 'up' : 'down'}">${d === 0 ? '— no change' : (d > 0 ? '▲ +' : '▼ −') + Math.abs(d).toFixed(dec || 0)}</span> `;
  };
  const niceMax = v => { const m = Math.max(v, 1); const p = Math.pow(10, Math.floor(Math.log10(m))); return Math.ceil(m / p * 2) / 2 * p; };
  const chart = (key, tone, label, h) => {
    const vals = pick.map(d => byDate[d][key]), max = niceMax(Math.max(...vals, 1));
    return html`<div class="lo-chart-title ${tone}"><span>${label}</span><span dir="ltr">${vals[0]} → ${vals[vals.length - 1]}</span></div>
      <div class="lo-chart" style="height:${h}px"><div class="lo-y"><span>${max}</span><span>${max / 2}</span><span>0</span></div>
      <div class="lo-plot"><div class="lo-grid"><i></i><i></i><i></i></div><div class="lo-bars">${vals.map((v, i) => html`<div class="lo-bar ${tone}"><span>${v}</span><i style="height:${(v / max * 88).toFixed(1)}%;opacity:${i === vals.length - 1 ? 1 : .55}"></i></div>`)}</div></div></div>`;
  };
  const tFirst = pick.length ? byDate[pick[0]].open : 0, tLast = pick.length ? byDate[pick[pick.length - 1]].open : 0;
  const trendVerdict = html`<p class="lo-explain"><b>${tLast < tFirst ? 'Improving: ' : tLast > tFirst ? 'Getting worse: ' : 'No change: '}</b>${tLast === tFirst ? 'the same number of issues as at the start.' : 'issues went from ' + tFirst + ' to ' + tLast + ' over this period.'}</p>`;
  const trendCard = html`<div class="lo-card lo-trend"><div class="lo-card-head"><div><h2>Is file health improving?</h2><small>The network's file-health score, 0 to 100 (higher is better). Daily for a month, weekly averages for longer.</small></div>${rangeSeg('overview', range)}</div>
      ${healthChart(dates.map((d, i) => ({ date: d, score: healthSeries[i] })), range)}</div>`;

  const ageOf = t => { const h = Math.max(0, (Date.now() - new Date(t).getTime()) / 3600000); return h < 24 ? Math.max(1, Math.round(h)) + 'h' : Math.round(h / 24) + 'd'; };
  const recent = urgentIssues.length ? html`<div class="lo-table"><div class="lo-tr lo-th"><span>School</span><span>Rule</span><span>Severity</span><span>Status</span><span class="lo-end">Age</span></div>
      ${urgentIssues.slice(0, 8).map(i => html`<a class="lo-tr" href="#/site/${i.siteId}"><b>${i.siteName}</b><span>${i.ruleCode ? i.ruleCode + ' · ' : ''}${i.ruleName}</span><span><em class="lo-sev">Urgent</em></span><span>${title(i.status)}</span><span class="lo-end lo-muted">${ageOf(i.openedAt)}</span></a>`)}</div>` : empty('No urgent issues are open.');
  const incList = urgentNow.length ? urgentNow.slice(0, 5).map(i => html`<div class="lo-inc"><div class="lo-inc-top"><b>${siteOf(i.siteId) || title(i.type)}</b><span>${ageOf(i.detectedAt)}</span></div><div>${i.description}</div><div class="lo-inc-foot"><em class="lo-sev">${title(i.type)}</em></div></div>`) : empty('No open incidents.');

  // ---- open incidents per day (last 14 days) as a solid-fill area chart
  const DAYS_N = 14, endOf = k => { const d = new Date(); d.setHours(23, 59, 59, 999); d.setDate(d.getDate() - (DAYS_N - 1 - k)); return d; };
  const incSeries = Array.from({ length: DAYS_N }, (_, k) => { const e = endOf(k).getTime(); return incidents.filter(i => new Date(i.detectedAt).getTime() <= e && (!i.resolvedAt || new Date(i.resolvedAt).getTime() > e)).length; });
  const incMax = niceMax(Math.max(...incSeries, 2));
  const IW = 300, IH = 120, ix = k => k * IW / (DAYS_N - 1), iy = v => IH - v / incMax * (IH - 8);
  const incLine = incSeries.map((v, k) => (k ? 'L' : 'M') + ix(k).toFixed(1) + ' ' + iy(v).toFixed(1)).join(' ');
  const incChart = html`<div class="lo-area"><div class="lo-area-top"><div><b>${urgentNow.length}</b><span>open now</span></div><span class="lo-area-peak">${'peak'} ${Math.max(...incSeries)} · ${'last'} ${count(DAYS_N, 'day')}</span></div>
    <div class="lo-area-box"><div class="lo-y"><span>${incMax}</span><span>${incMax / 2}</span><span>0</span></div>
    <svg viewBox="0 0 ${IW} ${IH}" preserveAspectRatio="none" role="img" aria-label="Open incidents per day"><line x1="0" x2="${IW}" y1="4" y2="4" class="g"/><line x1="0" x2="${IW}" y1="${IH / 2}" y2="${IH / 2}" class="g"/><path d="${incLine} L${IW} ${IH} L0 ${IH} Z" fill="#C93A2E"/><path d="${incLine}" fill="none" stroke="#8E1B1B" stroke-width="2" vector-effect="non-scaling-stroke"/></svg></div>
    <div class="lo-area-x"><span>${endOf(0).toLocaleDateString(uiLocale(), { month: 'short', day: 'numeric' })}</span><span>Today</span></div></div>`;

  return html`<div class="lo">
    <header class="lo-hero">${raw(LO_ART)}<div class="lo-hero-top"><div class="lo-hero-text"><h1>${hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'}${first ? ', ' + first : ''}</h1>
      ${o.checkedYet ? '' : html`<p>No check has finished yet. Numbers appear after the first check.</p>`}</div>
      <div class="lo-hero-tools"><a class="lo-bell" href="#/notifications" aria-label="Notifications">${ico('M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0')}${S.unread ? html`<span>${S.unread}</span>` : ''}</a>
      ${S.me.canDownloadWeeklyReport ? html`<a class="lo-hero-btn" href="${dl('/api/dashboard/weekly-report.pdf')}">${ico(IC.dl)} Weekly report (PDF)</a>` : ''}</div></div></header>
    <div class="lo-body">
      ${firstCheck}${noSchools}
      <section class="lo-kpis">
        ${kpi2('Overall health', netScore === null ? '—' : Math.round(netScore), netScore === null ? '' : '/100', netBand || 'slate', IC.shield, netScore === null ? 'Waiting for the first check' : html`<b>${netBand === 'good' ? 'Healthy' : netBand === 'watch' ? 'Keep an eye on it' : 'Needs attention'}</b><br>Average score across all schools. 100 means no file problems.`, null, '#/incidents')}
        ${kpi2('Urgent problems', o.urgentOpen, '', o.urgentOpen ? 'bad' : 'good', IC.warn, o.urgentOpen ? html`<b>Need action now</b><br>Serious problems, like sensitive files shared publicly.` : html`<b>All clear</b><br>No serious problems are open.`, null, '#/incidents')}
        ${kpi2('Other open issues', o.open - o.urgentOpen, '', 'slate', IC.clip, html`<b>Being tracked by schools</b><br>Smaller problems that staff are fixing. No action needed from you.`, null, '#/incidents')}
      </section>
      ${decide}
      <section class="lo-row">
        <div class="lo-card lo-c5"><div class="lo-card-head"><div><h2>School performance</h2><small>Each school's score out of 100, worst first. Higher is better: 85+ is good, 70–84 needs watching, below 70 needs attention.</small></div></div>${schoolCards}</div>
        ${trendCard}
        <div class="lo-card lo-c3"><div class="lo-card-head"><h2>Open incidents</h2><span class="lo-pill lo-pill-red">${urgentNow.length}</span><a class="card-link" href="#/incidents">View all →</a></div><small class="lo-sub">Serious events, such as an exam leak or deleted files, that someone must resolve. The chart shows how many were open each day.</small>${incChart}${urgentNow.slice(0, 2).map(i => html`<div class="lo-inc"><div class="lo-inc-top"><b>${siteOf(i.siteId) || title(i.type)}</b><span>${ageOf(i.detectedAt)}</span></div><div class="lo-inc-foot"><em class="lo-sev">${title(i.type)}</em></div></div>`)}</div>
      </section>
      <section class="lo-row">
        <div class="lo-card lo-c5"><div class="lo-card-head"><div><h2>Urgent issues</h2><small>Serious rule breaks that are still open. You see the school and the rule only, never file names.</small></div><a class="card-link" href="#/incidents">All incidents →</a></div>${recent}</div>
        <div class="lo-card lo-c3"><div class="lo-card-head"><div><h2>Network health</h2><small>How many schools are healthy, need watching, or need attention.</small></div></div>${donut}<div class="lo-delta">${count(o.fixedByStaff, 'issue')} ${'fixed by staff'} · ${count(o.filesChecked, 'file')} ${'checked'}</div></div>
        <div class="lo-quick lo-c3"><div class="lo-quick-head"><span>${ico(IC.bolt)}</span><h2>Quick actions</h2></div>${actions.map(([href, l, p]) => html`<a href="${href}"><span class="lo-qi">${ico(p)}</span><span class="lo-ql">${l}</span><span class="lo-qa">→</span></a>`)}</div>
      </section>
    </div></div>`;
};

SCREENS.site = async id => {
  const r = await get(`/api/dashboard/sites/${id}/score?days=30`);
  const s = r.score;
  return page(s.siteName, 'Last 30 days',
    html`<div class="kpi-grid">${kpi('Health score', Math.round(s.score))}${kpi('Files checked', s.filesChecked, 'slate')}${kpi('Open', s.openNormal + s.openUrgent, 'amber', '#/incidents')}${kpi('Urgent', s.openUrgent, 'brand', '#/incidents')}${kpi('Fixed', s.fixed, 'green', '#/activity')}</div>
    ${card('Trend', null, sparkline(r.trend))}`, html`<a class="btn" href="javascript:history.back()">Back</a>`);
};

// ---- principal: My school (dashboard v2)
const PD_SKYLINE = '<svg class="pd-sky" viewBox="0 0 900 300" preserveAspectRatio="xMidYMax slice" aria-hidden="true"><g fill="#8f2447"><rect x="400" y="200" width="30" height="100" opacity=".5"/><path d="M450 300Q460 220 456 140Q454 104 468 76Q482 104 480 140Q476 220 486 300Z" opacity=".8"/><path d="M520 300V138Q520 110 538 104Q556 110 556 138V300Z" opacity=".7"/><path d="M640 300L643 160Q629 138 638 104Q652 70 666 104Q675 138 661 160L664 300Z" opacity=".9"/><rect x="570" y="136" width="30" height="164" opacity=".6"/><path d="M690 300V118L722 104V300Z" opacity=".7"/><rect x="726" y="150" width="28" height="150" opacity=".55"/><rect x="760" y="190" width="34" height="110" opacity=".45"/><rect x="800" y="220" width="70" height="80" opacity=".4"/></g></svg>';
const PD_TYPE_COLOURS = ['#b7791f', '#a11d12', '#6b1730', '#14633b', '#2563eb', '#77777e'];
const pdInitials = n => String(n || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
const pdOpenColour = n => n === 0 ? '#14633b' : n >= 4 ? '#a11d12' : '#b7791f';
const pdShortDate = d => toDate(d).toLocaleDateString(uiLocale(), { month: 'short', day: 'numeric' });

function pdGauge(value) {
  const v = value == null ? 0 : Math.max(0, Math.min(100, value)), good = value != null && value >= 70;
  const colour = good ? '#5fd39a' : '#ff6b5e';
  return html`<div class="pd-gauge"><svg viewBox="0 0 200 118" aria-hidden="true"><path d="M20 100A80 80 0 0 1 180 100" fill="none" stroke="rgba(255,255,255,.16)" stroke-width="16" stroke-linecap="round" pathLength="100"/><path d="M20 100A80 80 0 0 1 180 100" fill="none" stroke="${colour}" stroke-width="16" stroke-linecap="round" pathLength="100" stroke-dasharray="${v} 100"/><text x="100" y="92" text-anchor="middle" font-size="46" font-weight="800" fill="#fff" font-family="Inter,sans-serif">${value == null ? '–' : value}</text><text x="100" y="112" text-anchor="middle" font-size="11" fill="#e4d3d8" font-family="Inter,sans-serif">HEALTH SCORE · OF 100</text></svg>
    <span class="pd-chip ${good ? 'pd-chip-good' : ''}">${value == null ? 'No score yet' : good ? '✓ On track' : '! Needs attention'}</span></div>`;
}

function pdTrend(points) {
  const pts = (points || []).slice(-7);
  if (pts.length < 2) return html`<div class="pd-trend-empty">Not enough history yet.</div>`;
  const n = pts.length, xs = i => 60 + i * (224 / (n - 1)), ys = s => 120 - Math.max(0, Math.min(100, s)) * 1.08;
  const line = pts.map((p, i) => xs(i).toFixed(1) + ',' + ys(p.score).toFixed(1)).join(' ');
  const area = line + ' ' + xs(n - 1).toFixed(1) + ',120 ' + xs(0).toFixed(1) + ',120';
  return html`<svg viewBox="0 0 320 150" role="img" aria-label="Health score trend, ${pdShortDate(pts[0].date)} to ${pdShortDate(pts[n - 1].date)}" class="pd-trend"><defs><linearGradient id="pdtd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff6b5e" stop-opacity=".3"/><stop offset="1" stop-color="#ff6b5e" stop-opacity="0"/></linearGradient></defs>
    <g stroke="rgba(255,255,255,.14)"><line x1="34" x2="310" y1="12" y2="12"/><line x1="34" x2="310" y1="48" y2="48"/><line x1="34" x2="310" y1="84" y2="84"/><line x1="34" x2="310" y1="120" y2="120"/></g>
    <g fill="#cfcfd6" font-size="11" text-anchor="end"><text x="26" y="16">100</text><text x="26" y="52">66</text><text x="26" y="88">33</text><text x="26" y="124">0</text></g>
    <polygon points="${area}" fill="url(#pdtd)"/><polyline points="${line}" fill="none" stroke="#ff8a80" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>
    <g fill="#17171a" stroke="#ff8a80" stroke-width="2.5">${pts.slice(0, -1).map((p, i) => html`<circle cx="${xs(i).toFixed(1)}" cy="${ys(p.score).toFixed(1)}" r="4.5"/>`)}</g>
    <circle cx="${xs(n - 1).toFixed(1)}" cy="${ys(pts[n - 1].score).toFixed(1)}" r="6" fill="#fff" stroke="#ff8a80" stroke-width="3"/>
    <g fill="#cfcfd6" font-size="11" text-anchor="middle">${pts.map((p, i) => html`<text x="${xs(i).toFixed(1)}" y="142">${pdShortDate(p.date)}</text>`)}</g></svg>`;
}

const pdKpi = (label, tag, value, note, pct, cls, icon, go) => html`<a class="pd-kpi kpi-a ${cls}" href="${go}" data-kpi="1"><svg class="pd-kpi-mark" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><path d="${icon}"/></svg>
  <div class="pd-kpi-top"><span>${label}</span><span class="pd-kpi-tag">${tag}</span></div><b>${value}</b>
  <div class="pd-kpi-foot"><div>${note}</div><div class="pd-bar"><div style="width:${Math.max(0, Math.min(100, pct))}%"></div></div></div></a>`;

const pdFixedColour = rate => rate == null ? '#6b6b73' : rate >= 70 ? '#14633b' : rate >= 40 ? '#b7791f' : '#a11d12';
const pdPerson = (p, n, colour, word) => html`<div class="pd-person"><span class="pd-av">${pdInitials(p.displayName)}</span><div class="pd-person-t"><b>${p.displayName}</b><span>${ROLE_LABEL[p.role] || title(p.role)}${personSite(p) ? ' · ' + personSite(p) : ''}</span></div><span class="pd-open"><b style="color:${colour}">${n}</b><div>${word || 'open'}</div></span></div>`;

SCREENS.school = async (arg, q) => {
  const range = rangeOf(q);
  const [counts, openIssues, urgent, rules, allIssues] = await Promise.all([get('/api/follow-up/counts'), get('/api/issues?filter=ALL_OPEN'), get('/api/issues?filter=URGENT'),
    loadRules(), get('/api/issues?filter=ALL').catch(() => []), loadFiles(), loadSites(), loadPeople()]);
  await nameMissing(urgent, i => i.fileId);
  const score = await get(`/api/dashboard/sites/${S.me.siteId}/score?days=${range}`).catch(() => null);
  const health = score ? Math.round(score.score.score) : null;
  const total = counts.open + counts.fixed, pct = n => total ? Math.round(n * 100 / total) : 0;
  const school = siteName(S.me.siteId) || 'My school';

  // open issues per owner (names come from the school's people list)
  const perOwner = {};
  openIssues.forEach(i => { if (i.ownerId) perOwner[i.ownerId] = (perOwner[i.ownerId] || 0) + 1; });
  const staff = Object.values(S.people || {}).filter(p => p.status !== 'LEFT' && p.siteId === S.me.siteId);
  const byRole = r => staff.filter(p => p.role === r).sort((a, b) => (perOwner[b.id] || 0) - (perOwner[a.id] || 0)).slice(0, 6);
  const teachers = byRole('TEACHER');
  // coordinators: issues fixed in their subject; more fixed (as a share of the subject's issues) is better
  const subjectFixed = {}, subjectAll = {};
  allIssues.forEach(i => { if (!i.subjectId) return; subjectAll[i.subjectId] = (subjectAll[i.subjectId] || 0) + 1; if (i.status === 'FIXED') subjectFixed[i.subjectId] = (subjectFixed[i.subjectId] || 0) + 1; });
  const fixedOf = p => subjectFixed[p.subjectId] || 0, rateOf = p => subjectAll[p.subjectId] ? Math.round(fixedOf(p) * 100 / subjectAll[p.subjectId]) : null;
  const coords = staff.filter(p => p.role === 'COORDINATOR').sort((a, b) => fixedOf(b) - fixedOf(a)).slice(0, 6);
  const peopleList = (head, sub, icon, list, coord) => html`<div class="pd-col"><div class="pd-col-h"><span class="pd-tile">${lico(icon)}</span><div><b>${head}</b><div>${sub}</div></div></div>
    <div class="pd-people">${list.length ? list.map(p => coord ? pdPerson(p, fixedOf(p), pdFixedColour(rateOf(p)), 'fixed') : pdPerson(p, perOwner[p.id] || 0, pdOpenColour(perOwner[p.id] || 0))) : html`<div class="pd-none">Nobody yet.</div>`}</div>
    <p class="pd-key">${coord ? html`<b>Fixed</b>: issues fixed in their subject. <i class="g"></i>70% or more of the subject's issues fixed <i class="o"></i>40–69% <i class="r"></i>under 40%`
      : html`<b>Open</b>: files with an issue they still need to fix. <i class="g"></i>none <i class="o"></i>1–3 <i class="r"></i>4 or more`}</p></div>`;

  // open issues grouped by the rule that raised them
  const byRule = {};
  openIssues.forEach(i => { byRule[i.ruleId] = (byRule[i.ruleId] || 0) + 1; });
  const types = Object.entries(byRule).map(([id, n]) => { const r = rules[id] || {}; const v = r.effective || r.pending || {}; return { l: v.name || r.code || 'Other', n }; })
    .sort((a, b) => b.n - a.n).map((t, i) => ({ ...t, c: PD_TYPE_COLOURS[Math.min(i, PD_TYPE_COLOURS.length - 1)] }));

  const urgentRow = i => html`<div class="pd-urgent"><span class="pd-file">${lico('M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5')}</span><div class="pd-urgent-t"><b>${fileName(i.fileId)}</b><span dir="auto">${inLang(i.detailEn, i.detailAr)}</span></div>
    <div class="pd-urgent-r">${statusBadge(i.status)}<span class="pd-date">${fmtDate(i.openedAt)}</span><a class="pd-review" href="#/issue/${i.id}">Review</a></div></div>`;
  const trend = score ? score.trend : [];

  return html`<div class="pd">
  <section class="pd-hero">${raw(PD_SKYLINE)}<div class="pd-hero-t"><div class="pd-eyebrow"><span></span>MY SCHOOL</div><h1>${school}</h1><p>Where your school stands today.</p>
    ${S.me.canDownloadWeeklyReport ? html`<a class="pd-dl" href="${dl('/api/dashboard/weekly-report.pdf')}"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 4v11m0 0l-4-4m4 4l4-4M5 20h14"/></svg>Weekly report (PDF)</a>` : ''}</div>${pdGauge(health)}</section>
  <section class="pd-kpis">${pdKpi('Open', 'Needs review', counts.open, 'Files waiting for a decision', pct(counts.open), 'pd-k-open', LIC.shield, '#/issues?f=ALL_OPEN')}
    ${pdKpi('Urgent', 'Act first', counts.urgent, counts.urgent ? counts.urgent + ' files need action first' : 'Nothing urgent', counts.open ? counts.urgent * 100 / counts.open : 0, 'pd-k-urgent', LIC.warn, '#/issues?f=URGENT')}
    ${pdKpi('Follow-up', counts.needFollowUp ? 'Waiting' : 'All clear', counts.needFollowUp, counts.needFollowUp ? 'Owners waiting on you' : 'No owners waiting on you', counts.open ? counts.needFollowUp * 100 / counts.open : 0, 'pd-k-follow', LIC.clock, '#/issues?f=NEEDS_ME')}
    ${pdKpi('Fixed', 'Resolved', counts.fixed, pct(counts.fixed) + '% of ' + total + ' issues fixed', pct(counts.fixed), 'pd-k-fixed', LIC.check, '#/issues?f=CLOSED')}</section>
  <section class="pd-cols"><div class="pd-left">
    <div class="pd-card"><div class="pd-card-h"><div><span class="pd-tile pd-tile-red">${lico(LIC.warn)}</span><div><h2>Urgent issues</h2><small>${count(urgent.length, 'file')} ${'need action first'}</small></div></div><a href="#/issues">All issues →</a></div>
      <div class="pd-urgents">${urgent.length ? urgent.map(urgentRow) : empty('No urgent issues.')}</div></div>
    ${coords.length || teachers.length ? html`<div class="pd-card"><div class="pd-card-h"><div><div><h2>Teachers and coordinators</h2><small>Who owns the open files</small></div></div></div>
      <div class="pd-cols2">${peopleList('Coordinators', 'Issues fixed in their subject', 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8', coords, true)}${peopleList('Teachers', 'Files with an open issue', 'M2 9l10-5 10 5-10 5zM6 11v5c0 1.5 2.7 3 6 3s6-1.5 6-3v-5', teachers, false)}</div></div>` : ''}</div>
  <div class="pd-right"><div class="pd-card pd-trendcard"><div class="pd-card-h"><div><div><h2>File health over time</h2><small>Score 0 to 100. Higher is better.</small></div></div></div>${rangeSeg('school', range)}${healthChart(trend, range, 420)}</div>
    <div class="pd-card"><h2>Issues by type</h2><small class="pd-sub">${count(openIssues.length, 'file')} ${'open'}</small>
      ${types.length ? html`<div class="pd-seg">${types.map(t => html`<span style="flex:${t.n};background:${t.c}"></span>`)}</div>${types.map(t => html`<div class="pd-leg"><i style="background:${t.c}"></i><span>${t.l}</span><b>${t.n}</b></div>`)}` : empty('No open issues.')}</div></div></section></div>`;
};

SCREENS.incidents = async () => {
  const list = await get('/api/dashboard/incidents');
  const open = list.filter(i => !i.resolvedAt), month = list.filter(i => Date.now() - new Date(i.detectedAt) < 30 * 864e5);
  const item = i => html`<article class="lp-item ${i.resolvedAt ? 'is-done' : 'is-open'}"><span class="lp-dot"></span><div class="lp-item-main"><div class="lp-item-top"><b>${title(i.type)}</b>${i.resolvedAt ? badge('b-good', 'Resolved') : badge('b-bad', 'Open')}</div><p>${i.description}</p><small>Detected ${fmt(i.detectedAt)}${i.resolvedAt ? ' · Resolved ' + fmt(i.resolvedAt) : ''}</small></div>${i.resolvedAt || S.me.role === 'LEADERSHIP' ? '' : html`<button class="btn btn-sm btn-primary" data-act="resolveIncident" data-id="${i.id}">Resolve</button>`}</article>`;
  return page('Incidents', 'Major events: exam leaks, important or bulk deletions, lost connection, urgent issues left open.',
    card('All incidents', list.length + ' recorded', list.length ? html`<div class="lp-list">${[...open, ...list.filter(i => i.resolvedAt)].map(item)}</div>` : empty('No incidents recorded.')), '',
    [['Open now', open.length, '', open.length ? 'bad' : 'good', 'warn', open.length ? 'Need attention' : 'All clear'], ['Resolved', list.length - open.length, '', 'good', 'check', 'Closed out'], ['Last 30 days', month.length, '', 'brand', 'clock', 'Detected'], ['All time', list.length, '', 'slate', 'clip', 'Recorded']]);
};

// ---- rules
SCREENS.rules = async () => {
  invalidate('rules');
  const rules = Object.values(await loadRules());
  const it = S.me.role === 'EAA_ADMIN', canApprove = it, lead = it;
  const acts = r => html`<div class="row">${it ? html`<a class="btn btn-sm" href="#/rule/${r.id}">Edit</a>` : ''}${it ? html`<button class="btn btn-sm" data-act="${r.status === 'ACTIVE' ? 'pauseRule' : 'resumeRule'}" data-id="${r.id}">${r.status === 'ACTIVE' ? 'Pause' : 'Resume'}</button><button class="btn btn-sm btn-danger" data-act="deleteRule" data-id="${r.id}" data-confirm="Delete rule ${r.code}? Its open issues will be closed. Pausing is usually better.">Delete</button>` : ''}${canApprove && r.pending ? html`<button class="btn btn-sm btn-primary" data-act="approveRule" data-id="${r.id}" data-version="${r.pending.id}">Approve new version</button>` : ''}</div>`;
  const rows = rules.map(r => {
    const v = r.effective || r.pending || {};
    return { cells: [r.code, html`<b>${v.name}</b><div class="muted">${inLang(v.whyItMatters, v.whyItMattersAr)}</div>`, title(v.checkType), sevBadge(v.severity), r.status === 'ACTIVE' ? badge('b-good', 'Active') : badge('b-neutral', title(r.status)), r.openIssues, acts(r)] };
  });
  const cards = html`<div class="lp-grid">${rules.map(r => { const v = r.effective || r.pending || {}; return html`<article class="lp-rule ${r.status === 'ACTIVE' ? '' : 'is-off'}"><div class="lp-rule-top"><span class="lp-code">${r.code}</span>${sevBadge(v.severity)}${r.status === 'ACTIVE' ? badge('b-good', 'Active') : badge('b-neutral', title(r.status))}${r.pending ? badge('b-watch', 'New version waiting') : ''}</div>
      <h3>${v.name}</h3><p>${inLang(v.whyItMatters, v.whyItMattersAr)}</p><div class="lp-meta"><span>${title(v.checkType)}</span><span class="${r.openIssues ? 'lo-red' : ''}">${count(r.openIssues, 'issue')} ${'open'}</span></div>${acts(r)}</article>`; })}</div>`;
  const kp = [['Active rules', rules.filter(r => r.status === 'ACTIVE').length, '', 'good', 'shield', 'Checked on every scan'], ['Paused or off', rules.filter(r => r.status !== 'ACTIVE').length, '', 'slate', 'pause', 'Not checking now'], ['Waiting for approval', rules.filter(r => r.pending).length, '', 'brand', 'list', 'New versions'], ['Open issues', rules.reduce((a, r) => a + (r.openIssues || 0), 0), '', 'bad', 'warn', 'Caused by these rules']];
  return page('Rules', 'What Hafez checks. Changing a rule creates a new version that is approved before it applies.',
    lead ? (rules.length ? cards : card('Rules', null, empty('No rules yet.'))) : card('Rules', rules.length + ' rules', table(['Code', 'Rule', 'Check', 'Severity', 'Status', 'Open issues', ''], rows)),
    it ? html`<a class="btn btn-primary" href="#/rule/new">Add a rule</a>` : '', lead ? kp : null);
};

SCREENS.rule = async id => {
  const isNew = id === 'new';
  const r = isNew ? null : (await loadRules())[id] || (invalidate('rules'), (await loadRules())[id]);
  const v = r ? (r.pending || r.effective) : { name: '', whyItMatters: '', whyItMattersAr: '', checkType: 'LOCATION', severity: 'NORMAL', reminderDays: 2, parameter: '', fixStepsEn: '', fixStepsAr: '', docTypes: [], sensitivities: [] };
  return page(isNew ? 'New rule' : 'Edit rule ' + r.code, 'Saving creates a draft version. Preview shows how many files it would affect and the message the owner would read.',
    html`<section class="card"><form data-form="rule" data-id="${id}">
      ${field('Name', html`<input name="name" required value="${v.name}">`)}
      ${field('Why it matters (English)', html`<textarea name="whyItMatters" required>${v.whyItMatters}</textarea>`)}
      ${field('Why it matters (Arabic)', html`<textarea name="whyItMattersAr" dir="rtl" required>${v.whyItMattersAr}</textarea>`)}
      ${field('What to check', html`<select name="checkType">${options(CHECK_TYPES, v.checkType)}</select>`)}
      ${field('Severity', html`<select name="severity">${options(['NORMAL', 'URGENT'], v.severity)}</select>`)}
      ${field('Remind after (days)', html`<input type="number" min="0" name="reminderDays" value="${v.reminderDays}">`)}
      ${field('Parameter (only some checks use one, e.g. an exam date or folder)', html`<input name="parameter" value="${v.parameter || ''}">`)}
      ${field('Document types (comma separated, empty = all)', html`<input name="docTypes" value="${[...(v.docTypes || [])].join(', ')}">`)}
      <div class="field"><label>Sensitivities (none ticked = any)</label><div class="row">${SENS.map(s => html`<label><input type="checkbox" name="sens" value="${s}" ${(v.sensitivities || []).includes(s) ? raw('checked') : ''}> ${title(s)}</label>`)}</div></div>
      ${field('Fix steps (English, one per line)', html`<textarea name="fixStepsEn" required>${v.fixStepsEn}</textarea>`)}
      ${field('Fix steps (Arabic, one per line)', html`<textarea name="fixStepsAr" dir="rtl" required>${v.fixStepsAr}</textarea>`)}
      ${isNew ? html`<div class="field"><label><input type="checkbox" name="turnOn"> Turn on straight away</label>${S.me.role === 'EAA_ADMIN' ? html`<label><input type="checkbox" name="networkWide"> Network-wide rule</label>` : ''}</div>` : ''}
      <div class="row"><button class="btn" name="mode" value="preview">Preview</button><button class="btn btn-primary" name="mode" value="save">Save</button><a class="btn" href="#/rules">Cancel</a></div>
    </form><div id="preview" style="margin-top:16px"></div></section>`);
};

// ---- people
SCREENS.people = async () => {
  invalidate('people');
  const [people, sites] = await Promise.all([loadPeople(), loadSites()]);
  const list = Object.values(people), it = S.me.role === 'SCHOOL_IT', coord = S.me.role === 'COORDINATOR';
  const groups = it ? await get('/api/people/role-groups').catch(() => []) : [];
  const rows = list.map(p => ({ cells: [html`<b>${p.displayName}</b><div class="muted">${p.email}</div>${p.studentCode ? html`<div class="muted mono">${p.studentCode}</div>` : ''}`, personSite(p),
    it && !['LEADERSHIP', 'EAA_ADMIN'].includes(p.role) ? html`<form data-form="person" data-id="${p.id}" class="row"><select name="role">${options(ROLES.filter(x => x !== 'LEADERSHIP' && x !== 'EAA_ADMIN'), p.role, x => ROLE_LABEL[x])}</select><select name="status">${options(['NEW', 'ACTIVE', 'LEAVING', 'LEFT'], p.status)}</select><button class="btn btn-sm">Save</button></form>` : html`${ROLE_LABEL[p.role]} · ${title(p.status)}`,
    p.overridden ? badge('b-watch', 'Overridden') : '',
    html`<div class="row">${it ? html`<button class="btn btn-sm" data-act="welcome" data-id="${p.id}">Send welcome</button><button class="btn btn-sm" data-act="editPerson" data-id="${p.id}">Edit</button>` : ''}${it && p.id !== S.me.id && !['LEADERSHIP', 'EAA_ADMIN', 'SCHOOL_IT'].includes(p.role) ? html`<button class="btn btn-sm btn-danger" data-act="deletePerson" data-id="${p.id}" data-confirm="Delete this person permanently?">Delete</button>` : ''}${coord ? html`<button class="btn btn-sm" data-act="personFollowUp" data-id="${p.id}">Follow up</button>` : ''}</div>`] }));
  return page('People', 'Everyone signs in with their own school account. There are no Hafez passwords.',
    html`${card('Staff', list.length + ' people', table(['Person', 'School', 'Role and status', '', ''], rows))}
    ${it ? card('Add a person', 'They sign in with their own Google account. Use the same e-mail they sign in with.', html`<form data-form="addPerson" class="row"><input class="input" type="email" name="email" placeholder="name@school.edu" required><input class="input" name="name" placeholder="Full name" required><select name="role">${options(['TEACHER', 'COORDINATOR', 'PRINCIPAL', 'ATTENDANCE_OFFICER', 'STUDENT', 'PARENT'], 'TEACHER', x => ROLE_LABEL[x])}</select><select name="studentId" title="Only for a parent: the student they follow"><option value="">Child (for a parent)</option>${list.filter(p => p.role === 'STUDENT').map(p => html`<option value="${p.id}">${p.displayName}</option>`)}</select><button class="btn btn-primary">Add person</button></form>`) : ''}
    ${it ? card('Role groups', 'A directory group gives its members a role at their next sync.', html`${table(['Directory group', 'Role'], groups.map(g => ({ cells: [g.directoryGroup, ROLE_LABEL[g.role]] })))}<form data-form="roleGroup" class="row" style="margin-top:12px"><input class="input" name="directoryGroup" placeholder="group@school.edu" required><select name="role">${options(ROLES.filter(x => x !== 'LEADERSHIP' && x !== 'EAA_ADMIN'), 'TEACHER', x => ROLE_LABEL[x])}</select><button class="btn">Save mapping</button></form>`) : ''}`,
    it ? html`<button class="btn" data-act="syncPeople">Sync directory now</button>` : '');
};

// ---- schools and settings
SCREENS.sites = async () => {
  invalidate('sites');
  const sites = await loadSites();
  const it = S.me.role === 'SCHOOL_IT', lead = S.me.role === 'EAA_ADMIN', net = isNetworkRole();
  const settings = it ? await get('/api/settings').catch(() => []) : [];
  const schools = Object.values(sites);
  const counted = s => html`${s.scored ? 'Yes' : 'No'} ${lead ? html`<button class="btn btn-sm" data-act="toggleScored" data-id="${s.id}" data-value="${!s.scored}">${s.scored ? 'Exclude' : 'Include'}</button>` : ''}`;
  const schoolCards = html`<div class="lp-grid">${schools.map(s => html`<article class="lp-rule ${s.active ? '' : 'is-off'}"><div class="lp-rule-top"><span class="lp-code">${(s.name || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()}</span>${s.active ? badge('b-good', 'Active') : badge('b-neutral', 'Inactive')}${s.scored ? badge('b-internal', 'Counted in KPIs') : badge('b-watch', 'Not counted')}</div><h3>${s.name}</h3><p class="mono">${s.domain}</p>
      <div class="row"><a class="btn btn-sm" href="#/site/${s.id}">View score</a>${lead ? html`<button class="btn btn-sm" data-act="renameSchool" data-id="${s.id}" data-name="${s.name}">Rename</button><button class="btn btn-sm ${s.scored ? '' : 'btn-primary'}" data-act="toggleScored" data-id="${s.id}" data-value="${!s.scored}">${s.scored ? 'Exclude' : 'Include'}</button>`: ''}${lead && !s.headOffice ? html`<button class="btn btn-sm btn-danger" data-act="removeSchool" data-id="${s.id}" data-name="${s.name}">Remove</button>` : ''}</div></article>`)}</div>`;
  return page('Schools & settings', net ? 'The schools in the network and whether they count toward the KPIs.' : null, html`
    ${lead && schools.length ? eaaSchools(schools, lead) : net && schools.length ? schoolCards : card('Schools', null, schools.length ? table(['Name', 'Domain', 'Counted in KPIs', 'Status'], schools.map(s => ({ cells: [html`${s.name} ${lead ? html`<button class="btn btn-sm" data-act="renameSchool" data-id="${s.id}" data-name="${s.name}">Rename</button>` : ''}`, s.domain, counted(s), s.active ? badge('b-good', 'Active') : badge('b-neutral', 'Inactive')] }))) : empty('No school yet. Add the first one below.'))}
    ${lead ? card('Add school', null, html`<form data-form="site" class="row"><input class="input" name="name" placeholder="School name" required><input class="input" name="domain" placeholder="school.edu" required><label><input type="checkbox" name="scored" checked> Counted in the KPIs</label><button class="btn btn-primary">Add school</button></form>`) : ''}
    ${it ? card('Settings', null, table(['Setting', 'Value'], settings.map(s => ({ cells: [s.key, html`<form data-form="setting" data-key="${s.key}" class="row"><input class="input" name="value" value="${s.value}"><button class="btn btn-sm">Save</button></form>`] })))) : ''}`, '', lead ? [['Schools', schools.length, '', 'brand', 'school', 'In the network'], ['Counted in KPIs', schools.filter(x => x.scored).length, '', 'good', 'check', 'Scored'], ['Not counted', schools.filter(x => !x.scored).length, '', 'watch', 'pause', 'Left out of totals'], ['Active', schools.filter(x => x.active).length, '', 'slate', 'shield', 'Connected']] : null);
};

// ---- weekly grade submissions, and the student performance matrix
const weekShift = (d, n) => { const x = new Date(d + 'T00:00:00Z'); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10); };
SCREENS.weekly = async (arg, q) => {
  const week = q.get('week') || '';
  const rows = await get('/api/marks/weekly' + (week ? '?week=' + week : ''));
  const cur = week || new Date().toISOString().slice(0, 10);
  const st = { DONE: ['b-good', 'Done'], IN_PROGRESS: ['b-watch', 'In progress'], NOT_STARTED: ['b-neutral', 'Not started'], OVERDUE: ['b-bad', 'Overdue'] };
  const late = rows.filter(r => r.status === 'OVERDUE').length;
  return page('Weekly grades', 'Who has submitted this week\'s grades. The school week runs Sunday to Thursday.',
    html`<div class="row"><a class="btn btn-sm" href="#/weekly?week=${weekShift(cur, -7)}">← Previous week</a><b>${week ? html`${trText('Week of')} ${fmtDate(week)}` : 'This week'}</b><a class="btn btn-sm" href="#/weekly?week=${weekShift(cur, 7)}">Next week →</a></div>
    ${card(late ? late + ' overdue' : 'Submissions', null, rows.length ? table(['School', 'Section', 'Subject', 'Teacher', 'Submitted', 'Status'], rows.map(r => ({ cells: [r.school, r.section, r.subject, r.teacher, r.submitted + ' / ' + r.assessments, badge(...st[r.status])] }))) : empty('No teaching assignments yet.'))}`);
};

SCREENS.matrix = async (arg, q) => {
  const sections = await get('/api/academic/sections');
  if (!sections.length) return page('Performance matrix', null, empty('No sections yet.'));
  const sid = q.get('s') || sections[0].id;
  const m = await get(`/api/marks/sections/${sid}/matrix`);
  const canStage = S.me.role === 'COORDINATOR';
  return page('Performance matrix', 'Every student against every assessment, with their stage.',
    html`<div class="row">${sectionPicker(sections, sid, 'matrix')}</div>
    ${card('Marks', null, m.columns.length ? table(['Student', 'Stage', ...m.columns.map(c => c.name + ' (' + c.maxMark + ')'), 'Average'],
      m.rows.map(r => ({ cells: [r.name, canStage ? html`<select class="input" data-act="setStage" data-id="${r.studentId}">${['SIJIL', 'HIFZ'].map(x => html`<option value="${x}" ${x === r.stage ? raw('selected') : ''}>${title(x)}</option>`)}</select>` : title(r.stage),
        ...r.values.map(v => v == null ? '–' : v), r.averagePercent == null ? '–' : r.averagePercent + '%'] }))) : empty('No assessments yet.'))}`);
};

// ---- points and recognition
const pointsCard = v => spCard('list', 'Points', html`<p style="font-size:28px;font-weight:700;margin:0 0 8px">${v.total}</p>${v.entries.length ? table(['Points', 'Reason', 'Date'], v.entries.map(e => ({ cells: ['+' + e.amount, e.reason + (e.by ? ' · ' + e.by : ''), fmtDate(e.on)] }))) : empty('No points yet.')}`);

SCREENS.points = async (arg, q) => {
  const sections = await get('/api/academic/sections');
  if (!sections.length) return page('Points', null, empty('No section to show yet.'));
  const sid = q.get('s') || sections[0].id;
  const rows = await get('/api/points/sections/' + sid);
  const canAward = ['TEACHER', 'COORDINATOR'].includes(S.me.role);
  return page('Points', S.me.role === 'PRINCIPAL' ? 'Class points. Teachers give recognition points; you can view them.' : 'Students earn points for homework they finish, quizzes they take and their published exam results. Add recognition with a short reason.',
    html`<div class="row">${sectionPicker(sections, sid, 'points')}</div>
    ${canAward && rows.length ? card('Give points', null, html`<form data-form="award" class="row">
      <select class="input" name="studentId">${rows.map(r => html`<option value="${r.studentId}">${r.name}</option>`)}</select>
      <input class="input" name="amount" type="number" min="1" max="10" value="1" style="width:80px" required>
      <input class="input" name="reason" maxlength="200" placeholder="Reason" required style="min-width:260px">
      <button class="btn btn-primary">Give</button></form>`) : ''}
    ${card('Class points', null, rows.length ? table(['Student', 'Points'], rows.map(r => ({ cells: [r.name, r.total] }))) : empty('No students are enrolled in this section yet.'))}`);
};

SCREENS.recognition = async () => {
  const list = await get('/api/points/recognition');
  return page('Recognition', 'Points across the school: totals, average per student and the top students.',
    list.length ? html`${list.map(sc => card(sc.school, sc.total + ' points · average ' + sc.averagePerStudent + ' per student', sc.top.length ? table(['Top students', 'Points'], sc.top.map(t => ({ cells: [t.name, t.total] }))) : empty('No points yet.')))}` : empty('No schools yet.'));
};

// ---- invitations: the school issues one-time codes (there is no open registration)
SCREENS.invitations = async () => {
  const [list, people] = await Promise.all([get('/api/invitations'), get('/api/people')]);
  const students = people.filter(p => p.role === 'STUDENT');
  const issued = S.issued; S.issued = null;
  const st = { PENDING: 'b-watch', USED: 'b-good', EXPIRED: 'b-neutral', REVOKED: 'b-neutral' };
  return page('Invitations', 'There is no open registration. Give each family a one-time code; it works once and expires in 14 days.',
    html`${issued ? html`<div class="callout callout-info"><b>Code (shown only now):</b> <span class="mono" style="font-size:20px;font-weight:700;letter-spacing:.06em">${issued.code}</span><br>Valid until ${fmtDate(issued.expiresAt)}. Give it to the family; it cannot be shown again.</div>` : ''}
    ${card('Issue a code', null, html`<form data-form="invite">
      ${field('For', html`<select name="kind"><option value="PARENT">A parent (links one or more children)</option><option value="STUDENT">A student (sets their password)</option></select>`)}
      ${field('Student(s)', html`<select name="studentIds" multiple size="6" required>${students.map(p => html`<option value="${p.id}">${p.displayName}</option>`)}</select>`)}
      ${field('Parent e-mail (optional: the code then works only for this address)', html`<input class="input" type="email" name="email">`)}
      <button class="btn btn-primary">Issue code</button></form>`)}
    ${card('Codes', null, list.length ? table(['For', 'Student(s)', 'E-mail', 'Status', 'Expires', ''], list.map(i => ({ cells: [title(i.kind), i.students.join(', '), i.email || '', badge(st[i.status], title(i.status)), fmtDate(i.expiresAt), i.status === 'PENDING' ? html`<button class="btn btn-sm btn-danger" data-act="revokeInvite" data-id="${i.id}">Revoke</button>` : ''] }))) : empty('No codes yet.'))}`);
};

// ---- approvals (four-eyes): what waits for a second person
const APPROVAL_LABEL = { FILE_DELETE: 'Delete file' };
/** Own words for a request's status ("Pending" alone reads as an unused invitation code in Arabic). */
const APPROVAL_STATUS = { PENDING: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Rejected', CANCELLED: 'Withdrawn' };
/** "Request to delete a file": the reason is required; the request waits in Approvals for the principal or a coordinator. */
const deleteRequestForm = fileField => html`<form data-form="deleteFileRequest">${fileField}
  ${field('Why should it be deleted?', html`<textarea name="reason" maxlength="200" required placeholder="For example: an old copy, replaced by the new version"></textarea>`)}
  <button class="btn btn-danger">Send the request</button></form>`;

SCREENS.approvals = async () => {
  const canAsk = ['TEACHER', 'COORDINATOR'].includes(S.me.role);
  const [list, files] = await Promise.all([get('/api/approvals'), canAsk ? loadFiles() : Promise.resolve({})]);
  const deletable = Object.values(files || {}).filter(f => !f.trashed).sort((a, b) => a.name.localeCompare(b.name));
  const askCard = !canAsk ? '' : card('Request to delete a file', 'Write why the file should go. The principal or a coordinator of your school approves it before it is deleted from the register. Your Google Drive is never touched.',
    deletable.length ? deleteRequestForm(field('File', html`<select class="input" name="fileId" required>${deletable.map(f => html`<option value="${f.id}">${f.name}</option>`)}</select>`))
      : empty('You have no files that can be deleted.'));
  const rows = list.map(a => ({ cells: [APPROVAL_LABEL[a.action] || a.action, a.summary, a.requestedBy, fmt(a.requestedAt),
    badge(a.status === 'APPROVED' ? 'b-good' : a.status === 'PENDING' ? 'b-watch' : 'b-neutral', APPROVAL_STATUS[a.status] || title(a.status)),
    a.status === 'PENDING' ? html`<div class="row">${a.canDecide ? html`<button class="btn btn-sm btn-primary" data-act="approval" data-do="approve" data-id="${a.id}">Approve</button><button class="btn btn-sm btn-danger" data-act="approval" data-do="reject" data-id="${a.id}">Reject</button>` : ''}${a.mine ? html`<button class="btn btn-sm" data-act="approval" data-do="cancel" data-id="${a.id}">Withdraw</button>` : ''}</div>` : (a.decidedBy ? 'by ' + a.decidedBy : '')] }));
  return page('Approvals', 'Deleting a file needs a second person. You cannot approve your own request.',
    html`${askCard}${card('Requests', null, list.length ? table(['Action', 'What', 'Requested by', 'When', 'Status', ''], rows) : empty('Nothing is waiting for approval.'))}`);
};

// ---- connection
SCREENS.connection = async (arg, q) => {
  const sites = Object.values(await loadSites()).filter(s => s.active);
  const id = q.get('site') || S.me.siteId || (sites[0] && sites[0].id);
  if (!id) return page('Connection', null, empty('No school yet.'));
  const [st, runs] = await Promise.all([get(`/api/connection/${id}`), get(`/api/connection/${id}/checks`).catch(() => [])]);
  const it = ['SCHOOL_IT', 'EAA_ADMIN'].includes(S.me.role), eaa = S.me.role === 'EAA_ADMIN';
  return page('Connection', 'Hafez reads the school workspace with a read-only account. It can never change a file.',
    html`<div class="row"><label>School <select class="input" data-act="pickSite">${sites.map(s => html`<option value="${s.id}" ${s.id === id ? raw('selected') : ''}>${s.name}</option>`)}</select></label></div>
    ${card('Status', null, html`<div class="row">${st.connectorInstalled ? badge('b-good', 'Connector installed') : badge('b-bad', 'No connector installed')} ${st.checksEnabled ? badge('b-good', 'Checks on') : badge('b-neutral', 'Checks off')} <span class="muted">First check: ${fmt(st.firstCheckAt)}</span></div>
      ${!st.connectorInstalled ? html`<div class="callout callout-warn" style="margin-top:12px">No workspace connector is installed, so no files can be read and no checks can run.</div>` : ''}
      ${it ? html`<div class="row" style="margin-top:12px"><button class="btn" data-act="connTest" data-id="${id}">Test connection</button><button class="btn btn-primary" data-act="connCheck" data-id="${id}">Run a check now</button>${eaa ? html`<button class="btn" data-act="connDisconnect" data-id="${id}" data-confirm="Stop all checks for this school?">Disconnect</button>` : ''}<button class="btn" data-act="connReconnect" data-id="${id}">Reconnect</button></div>` : ''}`)}
    ${card('Recent checks', null, table(['Started', 'Type', 'Status', 'Files', 'Skipped', 'Opened', 'Closed', 'Note'], runs.map(r => ({ cells: [fmt(r.startedAt), title(r.type), badge(r.status === 'SUCCEEDED' ? 'b-good' : r.status === 'FAILED' ? 'b-bad' : 'b-watch', title(r.status)), r.itemsProcessed, r.itemsSkipped, r.issuesOpened, r.issuesClosed, r.note || ''] }))))}`, '',
    [['Connector', st.connectorInstalled ? 'On' : 'Off', '', st.connectorInstalled ? 'good' : 'bad', 'plug', st.connectorInstalled ? 'Read-only account installed' : 'Nothing can be read'], ['Checks', st.checksEnabled ? 'On' : 'Off', '', st.checksEnabled ? 'good' : 'slate', 'shield', 'First check ' + fmtDate(st.firstCheckAt)], ['Files last check', runs[0] ? runs[0].itemsProcessed : 0, '', 'brand', 'clip', runs[0] ? fmt(runs[0].startedAt) : 'No check yet'], ['Failed checks', runs.filter(r => r.status === 'FAILED').length, '', runs.some(r => r.status === 'FAILED') ? 'bad' : 'good', 'warn', 'Of the recent ' + runs.length]]);
};

// ---- audit and restores
SCREENS.activity = async () => {
  const list = await get('/api/activity-log');
  return page('Activity log', 'Every change is recorded in a chain that shows if anyone altered it.',
    card('Entries', null, table(['#', 'When', 'Action', 'About', 'Change'], list.map(e => ({ cells: [e.seq, fmt(e.at), e.action, e.entityType || '', [e.before, e.after].filter(Boolean).join(' → ')] }))), html`<span id="verify"></span>`),
    html`<button class="btn" data-act="verifyLog">Verify the log</button><a class="btn" href="${dl('/api/activity-log/download')}">Download</a>`,
    [['Entries', list.length, '', 'brand', 'list', 'In the chain'], ['Latest', list[0] ? fmtDate(list[0].at) : '—', '', 'slate', 'clock', list[0] ? list[0].action : 'Nothing recorded'], ['Distinct actions', new Set(list.map(e => e.action)).size, '', 'slate', 'clip', 'Kinds of change']]);
};

SCREENS.restores = async () => {
  const [list] = await Promise.all([get('/api/backup/restores'), loadFiles()]);
  const role = S.me.role, requester = ['COORDINATOR', 'PRINCIPAL'].includes(role), approver = ['SCHOOL_IT', 'EAA_ADMIN'].includes(role);
  const files = Object.values(S.files);
  const rows = list.map(r => ({ cells: [r.fileId ? fileName(r.fileId) : title(r.scope), title(r.scope), r.reason, badge(r.status === 'RESTORED' ? 'b-good' : r.status === 'REJECTED' ? 'b-bad' : 'b-watch', title(r.status)), fmt(r.requestedAt),
    approver ? html`<div class="row">${r.status === 'REQUESTED' ? html`<button class="btn btn-sm" data-act="restore" data-do="approve" data-id="${r.id}">Approve</button><button class="btn btn-sm btn-danger" data-act="restore" data-do="reject" data-id="${r.id}">Reject</button>` : ''}${r.status === 'APPROVED' ? html`<button class="btn btn-sm btn-primary" data-act="restore" data-do="complete" data-id="${r.id}">Mark restored</button>` : ''}</div>` : ''] }));
  return page('Restores', 'Ask for a deleted or damaged file to be restored from backup. Two approvals are needed.',
    html`${requester ? card('Request a restore', null, html`<form data-form="restoreRequest">
      ${field('Scope', html`<select name="scope">${options(['FILE', 'SUBJECT_DRIVE', 'WHOLE_SCHOOL'], 'FILE')}</select>`)}
      ${field('File (for a single file)', html`<select name="fileId"><option value="">—</option>${files.map(f => html`<option value="${f.id}">${f.name}</option>`)}</select>`)}
      ${field('Target (drive or folder, if not a single file)', html`<input name="target">`)}
      ${field('Reason', html`<textarea name="reason" required></textarea>`)}
      <button class="btn btn-primary">Request restore</button></form>`) : ''}
    ${card('Requests', null, table(['What', 'Scope', 'Reason', 'Status', 'Requested', ''], rows))}`);
};

// ---- my profile and preferences (every role)
const ALERTS = {
  ISSUE: ['File issues', 'Problems found in files you own or oversee.'],
  REMINDER: ['Reminders', 'Reminders about issues that are still open.'],
  FOLLOW_UP: ['Follow-ups', 'Issues that need a coordinator.'],
  INCIDENT: ['Incidents', 'Serious incidents in your school or the network.'],
  PLANNING: ['Lesson plans', 'Submissions and reviews of lesson plans.'],
  GRADES: ['Weekly grade deadlines', 'A reminder when this week\'s grades are not submitted yet.'],
  NOTICE: ['School notices', 'New notices from your school.'],
  TICKET: ['Support tickets', 'New tickets, replies and status changes.'],
  SYSTEM: ['General updates', 'Welcome messages and published results.'],
  COLLECTION: ['Pick-up', 'A parent has arrived, or a child was released or not released.'],
  MESSAGE: ['Chat messages', 'A new message from a teacher, a student or a parent.'],
  CLASSWORK: ['Homework, quizzes and class notices', 'New homework, a new quiz or a notice from a teacher.'],
  SECURITY: ['Security and account events', 'Role changes, approval requests and locked accounts. Always on.']
};
const fileToBase64 = file => new Promise((resolve, reject) => { const r = new FileReader(); r.onload = () => resolve(String(r.result).split(',')[1] || ''); r.onerror = () => reject(new Error('The file could not be read.')); r.readAsDataURL(file); });
const initialsOf = name => (name || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
const avatar = initials => S.me.hasPhoto
  ? html`<span class="who-av who-photo" style="background-image:url('/api/me/photo?v=${S.photoV || 0}')"></span>`
  : html`<span class="who-av">${initials}</span>`;
SCREENS.prefs = async () => {
  const me = S.me, kinds = me.alertKinds || [], muted = me.muted || [];
  const ask = me.role === 'PARENT' ? await get('/api/ask/status').catch(() => null) : null;
  return page('My profile and preferences', 'Your details, photo, language and which alerts you receive.', html`
    ${card('Profile', null, html`
      <div class="row" style="align-items:center;gap:16px;margin-bottom:12px">${avatar(initialsOf(me.displayName))}
        <div><input type="file" id="photoFile" accept="image/png,image/jpeg"> <small class="muted">PNG or JPEG, at most 500 KB.</small>
          <div class="row" style="margin-top:6px"><button type="button" class="btn btn-sm" data-act="photoSave">Upload photo</button>${me.hasPhoto ? html`<button type="button" class="btn btn-sm btn-danger" data-act="photoRemove">Remove photo</button>` : ''}</div></div></div>
      <form data-form="profile">
        ${field('Name', html`<input class="input" name="displayName" value="${me.displayName}" required minlength="2" maxlength="200">`)}
        ${field('E-mail (from your school account)', html`<input class="input" value="${me.email}" disabled>`)}
        ${field('Phone', html`<input class="input" name="phone" value="${me.phone || ''}" maxlength="30" inputmode="tel">`)}
        <button class="btn btn-primary">Save details</button></form>`)}
    ${card('Messages and alerts', 'Choose the language, and which alerts reach your notifications.', html`<form data-form="prefs">
      ${field('Language of messages', html`<select name="language" data-act="prefLang">${options(['EN', 'AR', 'BOTH'], me.language, x => ({ EN: 'English', AR: 'Arabic', BOTH: 'English and Arabic' }[x]))}</select>`)}
      ${field('How to receive them', html`<select name="digestMode">${options(['PER_ISSUE', 'DAILY'], me.digestMode, x => x === 'DAILY' ? 'One daily summary' : 'One message per issue')}</select>`)}
      <div class="field"><label>Alerts</label>${kinds.map(k => html`<label class="row" style="gap:8px;font-weight:400;align-items:flex-start"><input type="checkbox" name="alert_${k}" ${k !== 'SECURITY' && muted.includes(k) ? '' : raw('checked')} ${k === 'SECURITY' ? raw('disabled') : ''}><span><b>${ALERTS[k][0]}</b><br><small class="muted">${ALERTS[k][1]}</small></span></label>`)}</div>
      <button class="btn btn-primary">Save</button></form>`)}
    ${ask ? card('Ask Hafez', 'The floating helper that answers questions about school. Choose which of your children may use it: when it is off for a child, their Ask Hafez button disappears. It only reads information and never changes anything.',
      ask.children.length ? html`<div class="ah-pref">${ask.children.map(k => html`<label class="row ah-pref-row"><input type="checkbox" data-ask-child="${k.id}" ${k.enabled ? raw('checked') : ''}><span><b>${k.name}</b><br><small class="muted">Can use Ask Hafez</small></span></label>`)}</div>` : empty('No children are linked to your account yet.')) : ''}`);
};

// ---- support tickets: a head teacher writes to EAA head office; EAA triages, replies and closes
const TICKET_CAT = { TECHNICAL: 'Technical problem', ACCOUNTS: 'Accounts and access', ACADEMIC_DATA: 'Marks and academic data', GOOGLE_DRIVE: 'Google Drive', SECURITY: 'Security concern', OTHER: 'Other' };
const TICKET_URG = { LOW: 'Low', NORMAL: 'Normal', HIGH: 'High', URGENT: 'Urgent' };
const ticketStatusBadge = s => badge(s === 'CLOSED' ? 'b-good' : s === 'IN_PROGRESS' ? 'b-watch' : 'b-bad', title(s));
const urgBadge = u => badge(u === 'URGENT' ? 'b-urgent' : u === 'HIGH' ? 'b-bad' : 'b-normal', TICKET_URG[u] || title(u));
const AUD_LABEL = { SCHOOL_IT: 'School IT', LEADERSHIP: 'Leadership', EAA_IT: 'EAA IT' };
const AUD_HINT = { SCHOOL_IT: 'Your school\'s IT team', LEADERSHIP: 'Head office team', EAA_IT: 'Head office IT team' };
const URG_HINT = { LOW: 'Can wait', NORMAL: 'This week', HIGH: 'Soon', URGENT: 'Blocking work' };
const tkCard = (t, network, role) => html`<article class="tk-card tk-u-${t.urgency} ${t.status === 'CLOSED' ? 'tk-closed' : ''}" tabindex="0" role="link" data-go="#/ticket/${t.id}">
  <div><h3>${t.title}</h3><div class="tk-meta"><span class="pk-chip">${TICKET_CAT[t.category] || t.category}</span>${urgBadge(t.urgency)}${role === 'PRINCIPAL' ? html`<span class="pk-chip">${AUD_LABEL[t.audience] || t.audience}</span>` : ''}${network ? html`<span class="pk-chip">${t.school}</span>` : ''}</div></div>
  <div class="tk-side">${ticketStatusBadge(t.status)}<span>${fmt(t.updatedAt)}</span></div>
  <div class="tk-who">${t.createdBy} · ${t.assignedToName ? html`<span>Handled by</span> ${t.assignedToName}` : html`<span>Not assigned yet</span>`}</div></article>`;
SCREENS.support = async (arg, q) => {
  const list = await get('/api/support/tickets');
  const role = S.me.role, network = isNetworkRole();
  const canOpen = ['TEACHER', 'COORDINATOR', 'PRINCIPAL_ASSISTANT', 'ATTENDANCE_OFFICER', 'PRINCIPAL', 'LEADERSHIP'].includes(role);
  const status = q.get('status') || 'ALL';
  const shown = status === 'ALL' ? list : list.filter(t => t.status === status);
  const count = s => list.filter(t => t.status === s).length;
  const bySchool = {};
  list.forEach(t => {
    const r = bySchool[t.school] = bySchool[t.school] || { open: 0, progress: 0, closed: 0, urgent: 0 };
    if (t.status === 'OPEN') r.open++; else if (t.status === 'IN_PROGRESS') r.progress++; else r.closed++;
    if (t.status !== 'CLOSED' && t.urgency === 'URGENT') r.urgent++;
  });
  const targets = role === 'PRINCIPAL' ? ['LEADERSHIP'] : role === 'LEADERSHIP' ? ['EAA_IT'] : ['SCHOOL_IT'];
  const newForm = canOpen ? card('New ticket', role === 'LEADERSHIP' ? 'Leadership has read-only access. Ask EAA IT for any change. Replies and status changes appear here and in your notifications.' : role === 'PRINCIPAL' ? 'Write to Leadership at head office. Replies and status changes appear here and in your notifications.' : 'Ask your School IT for help. Replies and status changes appear here and in your notifications.', html`<form data-form="ticket" class="tk-form tk">
      ${targets.length > 1
        ? html`<span class="lbl">Send to</span><div class="tk-pick">${targets.map((a, i) => html`<label><input type="radio" name="audience" value="${a}" ${i === 0 ? raw('checked') : ''}><b>${AUD_LABEL[a]}</b><small>${AUD_HINT[a]}</small></label>`)}</div>`
        : html`<input type="hidden" name="audience" value="${targets[0]}">`}
      <span class="lbl">How urgent is it?</span>
      <div class="tk-pick tk-urg">${Object.keys(TICKET_URG).map(u => html`<label><input type="radio" name="urgency" value="${u}" ${u === 'NORMAL' ? raw('checked') : ''}><b>${TICKET_URG[u]}</b><small>${URG_HINT[u]}</small></label>`)}</div>
      <span class="lbl">What is it about?</span>
      <select name="category" aria-label="Category">${options(Object.keys(TICKET_CAT), 'TECHNICAL', c => TICKET_CAT[c])}</select>
      <span class="lbl">Title</span>
      <input class="input" name="title" required maxlength="200" placeholder="A short summary of the problem">
      <span class="lbl">Description</span>
      <textarea name="description" required maxlength="4000" placeholder="What happened, and what did you expect?"></textarea>
      <span class="lbl">Attachment (optional)</span>
      <label class="tk-drop"><input type="file" name="attachment" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.txt,.csv"><span class="muted">PDF, image, Word, Excel or text, up to 2 MB</span></label>
      <div class="tk-send"><button class="pk-btn pk-btn-go">Send ticket</button></div></form>`) : '';
  const schools = !network ? '' : card('By school', 'Open work across the network.', Object.keys(bySchool).length
    ? table(['School', 'Open', 'In progress', 'Closed', 'Urgent and unresolved'], Object.keys(bySchool).sort().map(n => ({ cells: [n, bySchool[n].open, bySchool[n].progress, bySchool[n].closed, bySchool[n].urgent ? badge('b-urgent', bySchool[n].urgent) : 0] })))
    : empty('No tickets yet.'));
  const sub = role === 'LEADERSHIP' ? 'Your requests to EAA IT, and the tickets principals wrote to head office (read-only).'
    : network ? 'Tickets from Leadership, and from principals to head office. Open one to assign, reply or close it.'
    : role === 'SCHOOL_IT' ? 'Tickets from staff at your school. Open one to assign, reply or close it.'
    : role === 'PRINCIPAL' ? 'Your tickets to Leadership at head office.'
    : 'Your requests to School IT.';
  return page('Support tickets', sub,
    html`${newForm}${schools}${card('Tickets', null, html`${seg('status', [['ALL', 'All'], ['OPEN', 'Open'], ['IN_PROGRESS', 'In progress'], ['CLOSED', 'Closed']], status)}<div style="margin-top:14px">${shown.length ? html`<div class="tk-list tk">${shown.map(t => tkCard(t, network, role))}</div>` : html`<div class="pk-empty">No tickets here yet.</div>`}</div>`)}`,
    null, [['Open', count('OPEN'), '', count('OPEN') ? 'brand' : 'good', 'warn', 'Waiting'], ['In progress', count('IN_PROGRESS'), '', 'amber', 'clock', 'Being handled'], ['Closed', count('CLOSED'), '', 'good', 'check', 'Resolved']]);
};
SCREENS.ticket = async id => {
  const d = await get('/api/support/tickets/' + id);
  const t = d.ticket;
  const admins = d.canManage ? await get('/api/support/assignees') : [];
  const at = ['OPEN', 'IN_PROGRESS', 'CLOSED'].indexOf(t.status);
  const flow = ['Sent', 'Being handled', 'Resolved'];
  return page(t.title, t.school + ' · ' + (TICKET_CAT[t.category] || t.category) + ' · ' + (AUD_LABEL[t.audience] || t.audience), html`
    ${card('Details', null, html`<div class="tk"><ol class="tk-flow">${flow.map((label, i) => html`<li class="${t.status === 'CLOSED' ? 'shut ' : ''}${i < at ? 'done' : i === at ? 'on' : ''}"><i>${i <= at ? '✓' : i + 1}</i>${label}</li>`)}</ol>
      <div class="tk-facts" style="margin-bottom:12px">${ticketStatusBadge(t.status)} ${urgBadge(t.urgency)}</div>
      <p class="tk-body">${d.description}</p>
      <p class="muted"><span>Opened</span> ${fmt(t.createdAt)} · ${t.createdBy}${t.assignedToName ? html` · <span>Handled by</span> ${t.assignedToName}` : html` · <span>Not assigned yet</span>`}${t.closedAt ? html` · <span>Closed</span> ${fmt(t.closedAt)}` : ''}</p>
      ${t.hasAttachment ? html`<p><a class="pk-btn pk-btn-sm" href="/api/support/tickets/${t.id}/attachment">Download ${t.attachmentName}</a></p>` : ''}</div>`)}
    ${d.canManage ? card('Handle this ticket', null, html`<div class="tk"><form data-form="ticketAssign" data-id="${t.id}" class="tk-manage">
        <div class="grow">${field('Assign to', html`<select name="assigneeId">${admins.map(a => html`<option value="${a.id}" ${a.id === t.assignedTo ? raw('selected') : ''}>${a.name}</option>`)}</select>`)}</div>
        <button class="pk-btn pk-btn-go" style="margin-bottom:12px">Assign</button></form>
      <div class="tk-states">${['OPEN', 'IN_PROGRESS', 'CLOSED'].filter(s => s !== t.status).map(s => html`<button class="pk-btn pk-btn-sm ${s === 'CLOSED' ? 'pk-btn-ok' : ''}" data-act="ticketStatus" data-id="${t.id}" data-status="${s}">${s === 'CLOSED' ? 'Close ticket' : s === 'OPEN' ? 'Reopen' : 'Mark in progress'}</button>`)}</div></div>`) : ''}
    ${card('Conversation', null, d.replies.length ? html`<div class="tk tk-chat">${d.replies.map(r => html`<div class="tk-msg ${r.author === S.me.displayName ? 'mine' : ''}"><div class="by"><b>${r.author}</b> · ${ROLE_LABEL[r.role] || title(r.role)} · ${fmt(r.createdAt)}</div><div class="txt">${r.body}</div></div>`)}</div>` : html`<div class="pk-empty">No replies yet.</div>`)}
    ${d.canReply ? card('Reply', null, html`<form data-form="ticketReply" data-id="${t.id}" class="tk-form tk"><textarea name="body" required maxlength="4000" aria-label="Your reply" placeholder="Write your reply"></textarea><div class="tk-send"><button class="pk-btn pk-btn-go">Send reply</button></div></form>`)
      : card('Reply', null, html`<div class="pk-empty">${t.status === 'CLOSED' ? 'This ticket is closed. Open a new ticket for a new question.' : 'You can read this ticket. Only the sender and the team handling it can reply.'}</div>`)}`,
    html`<a class="btn" href="#/support">Back to tickets</a>`);
};

// ---- student pick-up and collection
const REL_LABEL = { PARENT: 'Parent', DRIVER: 'Driver', RELATIVE: 'Relative', GUARDIAN: 'Guardian', OTHER: 'Other' };
const PK_STATUS = { ARRIVED: ['b-watch', 'Waiting for the school'], VERIFYING: ['b-watch', 'Being checked'], RELEASED: ['b-good', 'Released'], DECLINED: ['b-bad', 'Not released'], CANCELLED: ['b-neutral', 'Cancelled'] };
const pkBadge = s => badge(PK_STATUS[s][0], PK_STATUS[s][1]);
const pkTime = d => d ? new Date(d).toLocaleTimeString(uiLocale(), { hour: '2-digit', minute: '2-digit' }) : '—';
const pkInit = n => (String(n || '?').trim()[0] || '?').toUpperCase();
const pkAgo = d => { const m = Math.max(0, Math.round((Date.now() - new Date(d)) / 60000)); return m < 1 ? 'just now' : m + ' min'; };
/** Re-draws one part of the page every few seconds while the person stays on the pick-up screen. */
function pickupPoll(targetId, fetcher, draw) {
  clearInterval(S.pkTick);
  S.pkTick = setInterval(async () => {
    const el = document.getElementById(targetId);
    if (!el || S.route !== 'pickup') { clearInterval(S.pkTick); return; }
    if (document.hidden) return;
    try { el.innerHTML = draw(await fetcher()).s; } catch (e) { if (e.status === 401) clearInterval(S.pkTick); }
  }, 5000);
}
/** Three steps a parent can follow: arrived, ID checked, released (or not released). */
const pkSteps = st => {
  const rank = { ARRIVED: 1, VERIFYING: 2, RELEASED: 3, DECLINED: 3 }[st] || 0;
  const bad = st === 'DECLINED';
  return html`<ol class="pk-steps">${[['Arrived', 1], ['ID checked', 2], [bad ? 'Not released' : 'Released', 3]].map(([label, n]) => {
    const cls = bad && n === 3 ? 'bad' : n < rank || (n === rank && st === 'RELEASED') ? 'done' : n === rank ? 'on' : '';
    return html`<li class="${cls}"><i>${cls === 'done' ? '✓' : cls === 'bad' ? '✕' : n}</i>${label}</li>`;
  })}</ol>`;
};
const pkToday = c => c.current && c.current.status === 'RELEASED' && c.current.decidedAt && new Date(c.current.decidedAt).toDateString() === new Date().toDateString();
const pkCurrent = c => {
  const r = c.current;
  if (pkToday(c)) return html`<div class="pk-now pk-now-done"><div class="pk-now-top"><span class="pk-av pk-av-sm">${pkInit(c.student)}</span><b>${c.student}</b><span class="pk-chip pk-chip-ok">✓ ${'Picked up'}</span></div>
    <small>${r.collector} (${REL_LABEL[r.relationship] || r.relationship}) · ${pkTime(r.decidedAt)}</small><div class="pk-note pk-note-ok">${'You confirmed the pick-up and accepted responsibility for your child.'}</div></div>`;
  return html`<div class="pk-now"><div class="pk-now-top"><span class="pk-av pk-av-sm">${pkInit(c.student)}</span><b>${c.student}</b>${r ? pkBadge(r.status) : ''}</div>
    ${r ? html`${pkSteps(r.status)}<small>${r.collector} (${REL_LABEL[r.relationship] || r.relationship}) · ${pkTime(r.arrivedAt)}</small>
      ${r.status === 'DECLINED' && r.declineReason ? html`<div class="pk-note">${r.declineReason}</div>` : ''}
      ${r.status === 'RELEASED' ? html`<div class="pk-note" style="background:#E7F7EF;color:#0B7A4F"><span>Released</span> · ${pkTime(r.decidedAt)}</div>` : ''}
      ${r.status === 'ARRIVED' || r.status === 'VERIFYING' ? html`<div><button class="pk-btn pk-btn-sm" data-act="pickupCancel" data-id="${r.id}">Withdraw</button></div>` : ''}`
    : html`<p class="pk-idle">Not picked up yet today.</p>`}</div>`;
};
SCREENS.pickup = async () => S.me.role === 'PARENT' ? parentPickup() : staffPickup();

const pkPerson = k => html`<div class="pk-person"><span class="pk-av pk-av-sm">${pkInit(k.name)}</span><div><b>${k.name}</b><small>${REL_LABEL[k.relationship] || k.relationship} · ID ends ••${k.idLast4}${k.phone ? ' · ' + k.phone : ''}</small></div>
  <button class="pk-btn pk-btn-sm pk-btn-no" data-act="removeCollector" data-id="${k.id}" data-confirm="Remove this collector? They will no longer be released the child.">Remove</button></div>`;
const PK_SUB = 'Approve who may collect your child. Press Pick up when you collect them: it is confirmed at once and the school sees it.';
async function parentPickup() {
  const kids = await get('/api/pickup/mine');
  const live = list => html`<div class="pk-live">${list.map(pkCurrent)}</div>`;
  pickupPoll('pickupMine', () => get('/api/pickup/mine'), live);
  if (!kids.length) return page('Pick-up', PK_SUB, card('Pick-up', null, empty('No children are linked to your account yet.')));
  const early = typeof earlyPickupParent === 'function' ? await earlyPickupParent(kids) : '';   // early pick-up requests (family.js)
  return page('Pick-up', PK_SUB, html`
    ${card('Right now', 'The school sees this at once. It updates by itself.', html`<div id="pickupMine" class="pk">${live(kids)}</div>`)}
    ${kids.map(c => {
      const open = c.current && (c.current.status === 'ARRIVED' || c.current.status === 'VERIFYING'), done = pkToday(c);
      return card(c.student + (c.section ? ' · ' + c.section : ''), 'Choose who is collecting, then press Pick up.', html`<div class="pk">
      <form data-form="arrive" data-student="${c.studentId}" data-name="${c.student}">
        <p class="pk-sub">Who is collecting?</p>
        <div class="pk-who"><label><input type="radio" name="collectorId" value="" checked><span class="pk-av pk-av-sm">${pkInit(S.me.displayName)}</span><span><b>Me</b><small>${S.me.displayName}</small></span></label>
          ${c.collectors.map(k => html`<label><input type="radio" name="collectorId" value="${k.id}"><span class="pk-av pk-av-sm">${pkInit(k.name)}</span><span><b>${k.name}</b><small>${REL_LABEL[k.relationship] || k.relationship}</small></span></label>`)}</div>
        ${done ? html`<p class="pk-note pk-note-ok" style="margin-bottom:10px">✓ ${'Picked up today at'} ${pkTime(c.current.decidedAt)}.</p>` : open ? html`<p class="pk-note" style="margin-bottom:10px">You have already told the school you have arrived for this child.</p>` : ''}
        <button class="pk-btn pk-btn-go pk-btn-block pk-btn-pick" ${open || done ? raw('disabled') : ''}>${done ? '✓ Picked up' : 'Pick up'}</button></form>
      <p class="pk-sub">Approved collectors</p>
      ${c.collectors.length ? html`<div class="pk-grid">${c.collectors.map(pkPerson)}</div>` : html`<div class="pk-empty">No approved collectors yet. You can always collect your child yourself.</div>`}
      <details class="pk-add"><summary>Add a collector</summary>
        <form data-form="collector" data-student="${c.studentId}">
          ${field('Full name', html`<input class="input" name="name" required minlength="2" maxlength="100">`)}
          ${field('Relationship', html`<select class="input" name="relationship">${['DRIVER', 'RELATIVE', 'GUARDIAN', 'OTHER'].map(r => html`<option value="${r}">${REL_LABEL[r]}</option>`)}</select>`)}
          ${field('ID number (only the last four are kept)', html`<input class="input" name="idNumber" required minlength="4" maxlength="30" autocomplete="off">`)}
          ${field('Phone (optional)', html`<input class="input" name="phone" maxlength="30" inputmode="tel">`)}
          <button class="pk-btn pk-btn-go">Add collector</button></form></details>
      ${c.recent.length ? html`<p class="pk-sub">Recent pick-ups</p><ul class="pk-hist">${c.recent.map(r => html`<li><span class="pk-chip pk-chip-ok">Picked up</span><span>${r.collector} (${REL_LABEL[r.relationship] || r.relationship})</span><span>${r.releasedBy}<br>${fmt(r.releasedAt)}</span></li>`)}</ul>` : ''}</div>`);
    })}${early}`);
}

const pkAct = (r, canAct, step) => !canAct ? '' : html`<div class="pk-q-act">${step === 'verify'
  ? html`<button class="pk-btn pk-btn-go" data-act="pickupVerify" data-id="${r.id}">Verify ID</button>`
  : html`<button class="pk-btn pk-btn-ok" data-act="pickupRelease" data-id="${r.id}">Release</button>`}<button class="pk-btn pk-btn-no" data-act="pickupDecline" data-id="${r.id}">Decline</button></div>`;
const pkQueueCard = (r, canAct, step) => html`<article class="pk-q ${step === 'release' ? 'pk-q-ready' : ''}">
  <div class="pk-q-top"><span class="pk-av">${pkInit(r.student)}</span><div><b>${r.student}</b><small>${r.section}</small></div><div class="pk-q-time"><b>${pkTime(r.arrivedAt)}</b>${pkAgo(r.arrivedAt)}</div></div>
  <div class="pk-q-who"><span class="muted">Collector</span><b>${r.collector}</b><span>${REL_LABEL[r.relationship] || r.relationship}${r.phone ? html` · <a href="tel:${r.phone}">${r.phone}</a>` : ''}</span>
    ${r.idLast4 ? html`<span>ID ends <span class="id">••${r.idLast4}</span></span>` : html`<span class="muted">Parent: check the ID at the gate.</span>`}</div>
  ${step === 'release' && r.verifiedBy ? html`<small class="muted">ID checked by ${r.verifiedBy}</small>` : ''}
  ${pkAct(r, canAct, step)}</article>`;
const pickupBoardHtml = b => {
  const done = b.completed.filter(r => r.status === 'RELEASED'), waiting = b.arrivals.concat(b.pending);
  return html`<div class="pk-stats"><div class="pk-stat pk-stat-ok"><b>${done.length}</b><small>Picked up today</small></div>${waiting.length ? html`<div class="pk-stat pk-stat-wn"><b>${waiting.length}</b><small>Older requests waiting</small></div>` : ''}</div>
  ${card('Picked up today', 'Parents confirm the pick-up themselves and accept responsibility. Newest first; it updates by itself.', done.length
    ? html`<div class="pk-green">${done.map(r => html`<article class="pk-gcard"><span class="pk-gcheck" aria-hidden="true">✓</span><div><b>${r.student}</b><small>${r.section}</small>
        <span>${'Collected by'} ${r.collector} (${REL_LABEL[r.relationship] || r.relationship})</span></div><time>${pkTime(r.decidedAt)}</time></article>`)}</div>`
    : html`<div class="pk-empty">Nobody has been picked up yet today.</div>`)}
  ${waiting.length ? card('Older requests', 'From before parents confirmed pick-ups themselves.', html`<div class="pk pk-grid">${b.arrivals.map(r => pkQueueCard(r, b.canAct, 'verify'))}${b.pending.map(r => pkQueueCard(r, b.canAct, 'release'))}</div>`) : ''}`;
};
async function staffPickup() {
  const [board, log, early] = await Promise.all([get('/api/pickup/board'), get('/api/pickup/log'), typeof earlyPickupStaff === 'function' ? earlyPickupStaff() : '']);
  pickupPoll('pickupBoard', () => get('/api/pickup/board'), pickupBoardHtml);
  return page('Pick-up board', 'Live: when a parent presses Pick up, the child shows here in green at once. No approval is needed.',
    html`${early}<div id="pickupBoard" class="stack-gap">${pickupBoardHtml(board)}</div>
    ${card('Pick-up log', 'Every pick-up is kept permanently and cannot be changed, with the parent who confirmed it. The last 30 days are shown.', log.length
      ? table(['Student', 'Class', 'Collected by', 'Confirmed by', 'Date and time'], log.map(r => ({ cells: [r.student, r.section, r.collector + ' (' + (REL_LABEL[r.relationship] || r.relationship) + ')', r.releasedBy, fmt(r.releasedAt)] })))
      : empty('No releases in the last 30 days.'))}`);
}

// ---- school portal: shared pieces
const noticeBlock = n => html`<li><b>${say(n.titleEn, n.titleAr)}</b> <span class="muted">${fmtDate(n.startsAt)} to ${fmtDate(n.endsAt)}${n.audience !== 'ALL' ? ' · ' + title(n.audience) : ''}</span><div>${say(n.bodyEn, n.bodyAr)}</div></li>`;

function weekGrid(lessons, who) {
  if (!lessons.length) return empty('No lessons in the timetable yet.');
  const periods = [...new Set(lessons.map(l => l.period))].sort((a, b) => a - b);
  return html`<div class="scroll-x"><table class="t"><thead><tr><th>Period</th>${DAYS.map(d => html`<th>${d}</th>`)}</tr></thead><tbody>${periods.map(p => html`<tr><td><b>${p}</b></td>${DAYS.map((d, i) => {
    const l = lessons.find(x => x.day === i && x.period === p);
    return html`<td>${l ? html`<div class="lesson"><b>${l.subject}</b><div class="muted">${who === 'teacher' ? l.section : l.teacher}${l.room ? ' · ' + l.room : ''}</div></div>` : ''}</td>`;
  })}</tr>`)}</tbody></table></div>`;
}

const sectionLabel = s => s.grade + s.name;
const sectionPicker = (sections, current, route) => html`<label>Section <select class="input" data-act="pickSection" data-route="${route}">${sections.map(s => html`<option value="${s.id}" ${s.id === current ? raw('selected') : ''}>${sectionLabel(s)} (${count(s.students, 'student')})</option>`)}</select></label>`;

// ---- teacher portal
SCREENS.today = async () => {
  const [t, notes] = await Promise.all([get('/api/portal/teacher'), get('/api/notices').catch(() => [])]);
  const taken = new Set(t.attendanceTaken);
  const next = t.schoolDay ? [] : (await get('/api/academic/timetable').catch(() => [])).filter(l => l.day === 0);   // a day off: show Sunday's lessons
  return page('Today', (t.schoolDay ? DAYS[new Date(t.date + 'T00:00:00').getDay()] || '' : 'Not a school day') + ' · ' + fmtDate(t.date),
    html`${!t.schoolDay && next.length ? card('Next school day: Sunday', 'Today is a day off. Here is what your week starts with.', table(['Period', 'Section', 'Subject', 'Room'], next.sort((a, b) => a.period - b.period).map(l => ({ cells: [l.period, l.section, l.subject, l.room || ''] })))) : card("Today's lessons", 'From the timetable.', t.lessons.length ? table(['Period', 'Section', 'Subject', 'Room', 'Attendance'], t.lessons.map(l => ({ cells: [l.period, l.section, l.subject, l.room || '', taken.has(l.sectionId) ? badge('b-good', 'Taken') : badge('b-watch', 'Not taken yet')] }))) : empty(t.schoolDay ? 'No lessons today.' : 'Attendance and lessons run Sunday to Thursday.'))}
    ${notes.length ? card('Notices', 'From school administration.', html`<ul class="timeline">${notes.map(noticeBlock)}</ul>`, html`<a href="#/notices">View all →</a>`) : ''}
    ${card('My sections', null, t.sections.length ? table(['Section', 'Students', 'Attendance today', ''], t.sections.map(x => ({ cells: [sectionLabel(x), x.students, taken.has(x.id) ? badge('b-good', 'Taken') : badge('b-watch', 'Not taken'), html`<a href="#/attendance?s=${x.id}">${openWord()}</a>`] }))) : empty('You are not assigned to a section yet. The coordinator assigns teachers in Academic setup.'))}`);
};

SCREENS.attendance = async (arg, q) => {
  const sections = await get('/api/academic/sections');
  const t = { date: new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Qatar' }) };
  const canRecord = ['ATTENDANCE_OFFICER', 'SCHOOL_IT'].includes(S.me.role);
  if (!sections.length) return page('Attendance', null, empty('No section to show yet.'));
  const sid = q.get('s') || sections[0].id;
  const roster = await get('/api/attendance/sections/' + sid);
  const opt = (r, v, label) => html`<label style="margin-inline-end:12px"><input type="radio" name="st_${r.studentId}" value="${v}" ${r.status === v ? raw('checked') : ''}> ${label}</label>`;
  if (!canRecord) {
    return page('Attendance', 'Attendance is recorded by the school\'s attendance officer. You can read it here.',
      html`<div class="row">${sectionPicker(sections, sid, 'attendance')} <b>${fmtDate(t.date)}</b></div>
      ${card('Class list', count(roster.length, 'student'), roster.length ? table(['Student', 'Status'], roster.map(r => ({ cells: [r.name, badge(r.status === 'PRESENT' ? 'b-good' : r.status === 'ABSENT' ? 'b-bad' : 'b-watch', title(r.status))] }))) : empty('No students are enrolled in this section yet.'))}`);
  }
  const rosters = await Promise.all(sections.map(x => x.id === sid ? roster : get('/api/attendance/sections/' + x.id)));
  const tally = rows => ({ n: rows.length, p: rows.filter(r => r.status === 'PRESENT').length, a: rows.filter(r => r.status === 'ABSENT').length, l: rows.filter(r => r.status === 'LEAVE').length });
  const all = tally(rosters.flat());
  const dayOk = true;   // attendance can be recorded on any day
  const cur = sections.find(x => x.id === sid);
  const attSeg = r => html`<div class="at-seg" role="radiogroup" aria-label="${r.name}">${[['PRESENT', 'Present', 'p'], ['ABSENT', 'Absent', 'a'], ['LEAVE', 'Leave', 'l']].map(([v, label, c]) => html`<label class="at-${c}"><input type="radio" name="st_${r.studentId}" value="${v}" ${r.status === v ? raw('checked') : ''}><span>${label}</span></label>`)}</div>`;
  const tl = tally(roster);
  return page('Attendance', 'Record today. Everyone starts as present; change only the absences and leave.',
    html`<div class="at">
    ${dayOk ? '' : html`<div class="pk-note" style="background:#FEF3DC;color:#8A5A00">Today is a day off. Attendance is recorded Sunday to Thursday, so you can read the lists but not save.</div>`}
    <div class="pk-stats at-stats4"><div class="pk-stat"><b>${all.n}</b><small>Students</small></div><div class="pk-stat pk-stat-ok"><b>${all.p}</b><small>Present</small></div><div class="pk-stat pk-stat-bd"><b>${all.a}</b><small>Absent</small></div><div class="pk-stat pk-stat-wn"><b>${all.l}</b><small>On leave</small></div></div>
    <div class="at-classes">${sections.map((x, i) => { const c = tally(rosters[i]); return html`<a class="at-class ${x.id === sid ? 'on' : ''}" href="#/attendance?s=${x.id}"><b>${sectionLabel(x)}</b><span>${count(c.n, 'student')}</span>${c.a ? html`<em class="bad">${c.a} absent</em>` : html`<em>All present</em>`}</a>`; })}</div>
    </div>
    ${card('Class ' + (cur ? sectionLabel(cur) : '') + ' · ' + fmtDate(t.date), count(roster.length, 'student'), roster.length ? html`<form data-form="attendance" data-section="${sid}" data-date="${t.date}" class="at">
      <div class="at-bar"><span id="atTally" class="pk-chip pk-chip-ok">${tl.p} ${trText('present')} · ${tl.a} ${trText('absent')} · ${tl.l} ${trText('leave')}</span><button type="button" class="pk-btn pk-btn-sm" data-act="attAllPresent">Mark all present</button></div>
      <div class="at-list">${roster.map(r => html`<div class="at-row"><span class="pk-av pk-av-sm">${pkInit(r.name)}</span><b>${r.name}</b>${attSeg(r)}</div>`)}</div>
      <div class="tk-send"><button class="pk-btn pk-btn-go" ${dayOk ? '' : raw('disabled')}>Save attendance</button></div></form>` : empty('No students are enrolled in this section yet.'))}
    ${card('Pick-up today', 'Parents arriving to collect their children. You can follow it live.', html`<a class="pk-btn pk-btn-go" href="#/pickup">Open the pick-up board</a>`)}`);
};

/** Publish or hide ONE assessment (for example the exam), on its own: the teacher can publish as many as they like. */
const pubButton = a => a.ownPublished ? html`<button class="btn btn-sm btn-danger" data-act="unpublishAssessment" data-id="${a.id}">Unpublish</button>`
  : a.published ? '' : a.submitted ? html`<button class="btn btn-sm btn-primary" data-act="publishAssessment" data-id="${a.id}">Publish these marks</button>` : '';

SCREENS.marks = async (arg) => {
  const list = await get('/api/marks/assessments');
  if (!arg) {
    const [sections, terms] = await Promise.all([get('/api/academic/sections'), get('/api/academic/terms')]);
    // only the sections and subjects this teacher actually teaches, so every choice in the form is allowed
    const mine = (await Promise.all(sections.map(async s => (await get(`/api/academic/sections/${s.id}/assignments`).catch(() => []))
      .filter(a => a.teacherId === S.me.id).map(a => ({ value: s.id + '|' + a.subjectId, label: sectionLabel(s) + ' · ' + a.subject }))))).flat();
    const form = !mine.length ? empty('You are not assigned to a section and subject yet.') : !terms.length ? empty('No term exists yet. School IT creates the terms under Academic setup.') : html`<form data-form="assessment" class="row">
      <select class="input" name="pair">${mine.map(m => html`<option value="${m.value}">${m.label}</option>`)}</select>
      <select class="input" name="term">${terms.map(t => html`<option value="${t.number}">${t.name}</option>`)}</select>
      <input class="input" name="name" placeholder="Name, e.g. Quiz 1" required><input class="input" type="number" name="maxMark" min="1" max="1000" step="0.01" placeholder="Out of" required><button class="btn btn-primary">Add assessment</button></form>`;
    const terms2 = [...new Set(list.map(a => a.sectionId + '|' + a.term))].map(k => { const [sec, term] = k.split('|'); const rows = list.filter(a => a.sectionId === sec && String(a.term) === term); return { sec, term: Number(term), label: rows[0].section, published: rows.some(r => r.termPublished), waiting: rows.filter(r => !r.submitted).length, ready: rows.every(r => r.submitted) }; });
    return page('Marks', 'You set the assessments for the sections and subjects you teach, enter the grades, then submit to lock them.',
      html`${card('New assessment', null, form)}
      ${card('Assessments', null, list.length ? table(['Section', 'Subject', 'Term', 'Assessment', 'Out of', 'Status', ''], list.map(a => ({ go: '#/marks/' + a.id,
        cells: [a.section, a.subject, a.term, a.name, a.maxMark, a.published ? badge('b-good', 'Published') : a.submitted ? badge('b-internal', 'Submitted') : badge('b-watch', 'Open'), pubButton(a)] }))) : empty('No assessments yet. Add the first one above.'))}
      ${card('Publish results', 'You can publish each assessment on its own from the list above (for example the exam), as many as you like. Or publish a whole term at once: it shows a term to the students and parents at once. Every assessment of that term must be submitted first. You can keep adding assessments to a published term: each one appears as soon as you submit its marks.', terms2.length ? table(['Section', 'Term', 'Marks', ''], terms2.map(t => ({ cells: [t.label, t.term, t.published ? html`${badge('b-good', 'Published')}${t.waiting ? html` <span class="muted">${t.waiting + ' waiting for marks'}</span>` : ''}` : t.ready ? badge('b-internal', 'All submitted') : badge('b-watch', 'Waiting for submissions'),
        t.published ? html`<button class="btn btn-sm btn-danger" data-act="unpublishResults" data-section="${t.sec}" data-term="${t.term}">Unpublish</button>` : html`<button class="btn btn-sm btn-primary" data-act="publishResults" data-section="${t.sec}" data-term="${t.term}" ${t.ready ? '' : raw('disabled')}>Ask to publish</button>`] }))) : empty('Nothing to publish yet.'))}`);
  }
  const a = list.find(x => x.id === arg);
  if (!a) throw Object.assign(new Error('That assessment was not found.'), { status: 404 });
  const sheet = await get(`/api/marks/assessments/${arg}/sheet`);
  const locked = a.submitted;
  return page(a.name, `${a.section} · ${a.subject} · term ${a.term} · out of ${a.maxMark}`,
    html`${locked ? html`<div class="callout callout-info">These marks are submitted and locked. ${a.published ? 'The results are published: unpublish them first to change them.' : html`<button class="btn btn-sm" data-act="unlockMarks" data-id="${arg}">Unlock to correct</button>`}</div>` : ''}
    ${card('Marks', null, sheet.length ? html`<form data-form="marks" data-id="${arg}"><table class="t"><thead><tr><th>Student</th><th>Mark (0 to ${a.maxMark})</th><th>Absent</th></tr></thead><tbody>${sheet.map(r => html`<tr data-student="${r.studentId}"><td>${r.name}</td><td><input class="input" type="number" min="0" max="${a.maxMark}" step="0.01" value="${r.value ?? ''}" ${locked ? raw('disabled') : ''}></td><td><input type="checkbox" ${r.absent ? raw('checked') : ''} ${locked ? raw('disabled') : ''}></td></tr>`)}</tbody></table>
      ${locked ? '' : html`<div class="row" style="margin-top:16px"><button class="btn" name="mode" value="save">Save</button><button class="btn btn-primary" name="mode" value="submit">Save and submit</button></div>`}</form>` : empty('No students are enrolled in this section yet.'))}`,
    html`${pubButton(a)} <a class="btn" href="#/marks">Back</a>`);
};

SCREENS.materials = async () => {
  const [list, sections] = await Promise.all([get('/api/materials'), get('/api/academic/sections')]);
  // only the sections and subjects this teacher actually teaches, so every choice in the form is allowed
  const mine = (await Promise.all(sections.map(async s => (await get(`/api/academic/sections/${s.id}/assignments`).catch(() => []))
    .filter(a => a.teacherId === S.me.id).map(a => ({ value: s.id + '|' + a.subjectId, label: sectionLabel(s) + ' · ' + a.subject }))))).flat();
  return page('Study materials', 'Share a link (Google Drive, Dropbox or YouTube) with a section. Students open it from their portal.',
    html`${card('Share a link', null, mine.length ? html`<form data-form="material">
      ${field('Section and subject', html`<select name="pair">${mine.map(m => html`<option value="${m.value}">${m.label}</option>`)}</select>`)}
      ${field('Title', html`<input name="title" required>`)}${field('Link (https://…)', html`<input type="url" name="link" required placeholder="https://drive.google.com/…">`)}
      <button class="btn btn-primary">Share</button></form>` : empty('You are not assigned to a section and subject yet.'))}
    ${card('Shared by you', null, table(['Title', 'Section', 'Subject', 'Link', ''], list.map(m => ({ cells: [m.title, m.section, m.subject, html`<a href="${m.link}" target="_blank" rel="noopener noreferrer">${openWord()}</a>`, html`<button class="btn btn-sm btn-danger" data-act="removeMaterial" data-id="${m.id}" data-confirm="Remove this material?">Remove</button>`] }))))}`);
};

SCREENS.notices = async () => {
  const list = await get('/api/notices');
  if (isPortal()) return page('Notices', 'What your school and your teachers want you to know.', html`${classNoticeBanners(list)}${list.length
    ? html`<div class="sp-notices sp-notices-lg">${list.map(n => html`<div><span class="sp-ic sp-ic-note">${sIc('megaphone')}</span><div><b>${say(n.titleEn, n.titleAr)}</b><small>${fmtDate(n.startsAt)} to ${fmtDate(n.endsAt)} · ${noticeWho(n)}</small><p>${say(n.bodyEn, n.bodyAr)}</p></div></div>`)}</div>`
    : card('Current notices', null, empty('No notices right now.'))}`);
  return page('Notices', null, card('Current notices', null, list.length ? html`<ul class="timeline">${list.map(noticeBlock)}</ul>` : empty('No notices right now.')));
};

// ---- student portal
SCREENS.mydash = async () => {
  const st = await get('/api/enrol/status');
  if (!st.section) return waitingPage(st);
  const [d, pv] = await Promise.all([get('/api/portal/student'), get('/api/points/mine').catch(() => null)]);
  return portalHome(d, { name: st.name, mine: true, sub: `Section ${sectionLabel(d.section)} · ${fmtDate(d.date)} · Student ID ${st.studentCode}`,
    below: pv ? { top: pointsProgress(pv), bottom: pointsCard(pv) } : undefined });
};

async function classRequestCard(studentId, pending) {
  if (pending) return card('Waiting for a class', null, html`<p>The request for <b>${pending}</b> is waiting for School IT to confirm.</p>`);
  const classes = await get('/api/enrol/classes');
  return card('Choose a class', 'Ask for a place in a class. School IT confirms it.', classes.length
    ? html`<form data-form="enrolRequest" data-student="${studentId || ''}" class="row"><select class="input" name="sectionId">${classes.map(c => html`<option value="${c.id}">${c.label}</option>`)}</select><button class="btn btn-primary">Request this class</button></form>`
    : empty('The school has not created a class yet.'));
}

async function waitingPage(st) {
  return page('Welcome, ' + st.name, 'You are not in a class yet.', html`${card('Your student ID', 'Your school office may ask for this number.', html`<p class="mono" style="font-size:26px;font-weight:700;letter-spacing:.06em;margin:0">${st.studentCode}</p>`)}${await classRequestCard(null, st.pendingClass)}`);
}

// ---- the student / parent home screen (same layout for both)
const SPI = {
  check: LIC.check, bell: LIC.bell, list: LIC.list, x: 'M6 6l12 12M18 6L6 18', briefcase: 'M4 8h16v11H4zM9 8V5h6v3',
  star: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z', trend: 'M3 17l6-6 4 4 8-8M15 7h6v6',
  folder: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z', megaphone: 'M3 11v2a1 1 0 001 1h2l5 4V6L6 10H4a1 1 0 00-1 1zM15 9a4 4 0 010 6',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4', moon: 'M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z', users: 'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM22 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8',
  search: 'M21 21l-4.3-4.3M17 11a6 6 0 11-12 0 6 6 0 0112 0z', pulse: 'M3 12h4l2-6 4 12 2-6h6', book: 'M4 5a2 2 0 012-2h13v16H6a2 2 0 00-2 2zM4 19a2 2 0 012-2h13',
  gear: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19 12a7 7 0 00-.1-1.2l2-1.5-2-3.4-2.3.9a7 7 0 00-2-1.2L14.2 3h-4l-.4 2.6a7 7 0 00-2 1.2l-2.3-.9-2 3.4 2 1.5a7 7 0 000 2.4l-2 1.5 2 3.4 2.3-.9a7 7 0 002 1.2l.4 2.6h4l.4-2.6a7 7 0 002-1.2l2.3.9 2-3.4-2-1.5c.1-.4.1-.8.1-1.2z'
};
const sIc = k => lico(SPI[k]);
const markPct = r => Number(r.maxMark) > 0 ? Math.round(Number(r.value) / Number(r.maxMark) * 100) : 0;
Object.assign(SPI, {
  circle: 'M9 12l2 2 4-4M12 21a9 9 0 100-18 9 9 0 000 18z', clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  book: 'M4 19.5A2.5 2.5 0 016.5 17H20V3H6.5A2.5 2.5 0 004 5.5zM4 19.5V21h16', users: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8',
  folder: 'M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2z', calendar: 'M4 5h16v15H4zM4 10h16M9 3v4M15 3v4', x: 'M18 6L6 18M6 6l12 12', briefcase: 'M3 7h18v13H3zM8 7V4h8v3',
  star: 'M12 15l-5 3 1.5-5.5L4 9h5.5L12 3.5 14.5 9H20l-4.5 3.5L17 18z',
  gear: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.7 1.7 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.7 1.7 0 00-1.8-.3 1.7 1.7 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.7 1.7 0 00-1.1-1.5 1.7 1.7 0 00-1.8.3l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1.7 1.7 0 00.3-1.8 1.7 1.7 0 00-1.5-1H3a2 2 0 110-4h.1a1.7 1.7 0 001.5-1.1 1.7 1.7 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.7 1.7 0 001.8.3H9a1.7 1.7 0 001-1.5V3a2 2 0 114 0v.1a1.7 1.7 0 001 1.5 1.7 1.7 0 001.8-.3l.1-.1a2 2 0 112.8 2.8l-.1.1a1.7 1.7 0 00-.3 1.8V9a1.7 1.7 0 001.5 1H21a2 2 0 110 4h-.1a1.7 1.7 0 00-1.5 1z'
});
const SP_SKY = `<svg class="sp-sky" viewBox="0 0 900 300" preserveAspectRatio="xMaxYMax meet" aria-hidden="true"><defs><linearGradient id="hs1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c24a6e" stop-opacity=".55"/><stop offset="1" stop-color="#8f2447" stop-opacity=".18"/></linearGradient><linearGradient id="hs2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8f2447" stop-opacity=".3"/><stop offset="1" stop-color="#8f2447" stop-opacity=".08"/></linearGradient></defs>
<path d="M815 22A27 27 0 1 0 843 64A21 21 0 1 1 815 22Z" fill="#e7a3b6" opacity=".6"/>
<g fill="url(#hs2)"><rect x="90" y="200" width="30" height="100"/><rect x="124" y="176" width="24" height="124"/><rect x="152" y="208" width="36" height="92"/><rect x="192" y="188" width="26" height="112"/><rect x="640" y="196" width="30" height="104"/><rect x="674" y="176" width="22" height="124"/><rect x="760" y="190" width="30" height="110"/></g>
<g fill="url(#hs1)"><path d="M290 300Q300 220 296 140Q294 104 308 76Q322 104 320 140Q316 220 326 300Z"/><path d="M360 300V138Q360 110 378 104Q396 110 396 138V300Z"/><path d="M486 300L489 160Q475 138 484 104Q498 70 512 104Q521 138 507 160L510 300Z"/><rect x="414" y="136" width="30" height="164"/><rect x="448" y="166" width="26" height="134"/><path d="M534 300V118L566 104V300Z"/><rect x="570" y="150" width="28" height="150"/><path d="M602 300V186L638 160V300Z"/><rect x="240" y="180" width="30" height="120"/><rect x="206" y="230" width="30" height="70"/></g>
<g fill="#f4c27a" opacity=".7"><ellipse cx="498" cy="98" rx="9" ry="7"/></g><rect x="307" y="44" width="2" height="30" fill="#c24a6e" opacity=".6"/><rect x="377" y="88" width="2" height="16" fill="#c24a6e" opacity=".6"/>
<g stroke="#fff" stroke-width="2" stroke-dasharray="2 8" opacity=".55"><path d="M304 150V290M312 150V290M368 140V290M388 140V290M422 150V290M434 150V290M544 130V290M556 130V290M578 160V290M588 160V290"/></g>
<g><rect x="690" y="248" width="70" height="52" fill="#c24a6e" opacity=".4"/><rect x="706" y="230" width="38" height="18" fill="#c24a6e" opacity=".5"/><path d="M714 230Q725 214 736 230Z" fill="#e7a3b6" opacity=".7"/></g>
<g><rect x="790" y="250" width="100" height="50" fill="#e7a3b6" opacity=".45"/><path d="M808 250Q840 218 872 250Z" fill="#c24a6e" opacity=".5"/><rect x="838" y="204" width="2" height="26" fill="#c24a6e" opacity=".6"/><path d="M840 204L856 210L840 216Z" fill="#8f2447" opacity=".7"/></g></svg>`;

function examBanner(notices) {
  const n = (notices || []).find(x => !x.sectionId && /exam/i.test(x.titleEn || ''));
  if (!n) return '';
  const now = Date.now(), s = new Date(n.startsAt).getTime(), e = new Date(n.endsAt).getTime();
  if (now > e) return '';
  const live = now >= s, days = Math.max(1, Math.ceil(((live ? e : s) - now) / 864e5));
  return html`<section class="sp-exam"><i class="sp-exam-glow"></i><span class="sp-ic sp-ic-exam">${sIc('calendar')}</span><div class="sp-exam-text"><small>EXAM PERIOD</small><b>${live ? 'Exams are in progress' : 'Exams start soon'}</b><span>${fmtDate(n.startsAt)} to ${fmtDate(n.endsAt)} · ${n.audience === 'ALL' ? 'Everyone' : title(n.audience)}</span></div><div class="sp-exam-days"><b>${days}</b><span>${live ? 'days left' : 'days to go'}</span></div></section>`;
}

function progressChart(results) {
  const by = {};
  results.filter(r => !r.absent && r.value != null).forEach(r => { (by[r.subject] = by[r.subject] || []).push({ label: r.assessment, v: markPct(r) }); });
  const subs = Object.keys(by);
  if (!subs.length) return empty('Results appear here after your school publishes them.');
  const one = (pts, i) => {
    const n = pts.length, xp = k => n === 1 ? 50 : 3 + k * 94 / (n - 1), yp = v => 100 - Math.max(0, Math.min(100, v));
    const line = pts.map((p, k) => `${xp(k).toFixed(2)},${yp(p.v)}`).join(' ');
    return html`<div class="sp-chart-box" data-subject="${i}" ${i ? raw('hidden') : ''}>
      <div class="sp-chart"><div class="sp-y"><span>100%</span><span>75%</span><span>50%</span><span>25%</span><span>0%</span></div>
      <div class="sp-plot"><div class="sp-grid">${[0, 1, 2, 3, 4].map(() => html`<i></i>`)}</div>
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="pg${i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8f2447" stop-opacity=".4"/><stop offset="1" stop-color="#8f2447" stop-opacity="0"/></linearGradient></defs>${n > 1 ? html`<polygon points="${xp(0)},100 ${line} ${xp(n - 1)},100" fill="url(#pg${i})"/>` : ''}<polyline points="${line}" fill="none" stroke="#8f2447" stroke-width="2.5" vector-effect="non-scaling-stroke" stroke-linejoin="round"/></svg>
        ${pts.map((p, k) => html`<span class="sp-dot ${k === n - 1 ? 'on' : ''}" style="left:${xp(k)}%;top:${yp(p.v)}%">${k === n - 1 ? html`<em>${p.v}%</em>` : ''}</span>`)}
        <div class="sp-x">${pts.map((p, k) => html`<span style="left:${xp(k)}%">${p.label}</span>`)}</div></div></div></div>`;
  };
  return html`<div class="sp-tabs">${subs.map((s, i) => html`<button type="button" class="${i ? '' : 'on'}" data-act="spSubject" data-i="${i}">${s}</button>`)}</div>${subs.map((s, i) => one(by[s], i))}`;
}

function attendanceDonut(a) {
  const t = a.present + a.absent + a.leave;
  let off = 0;
  const arc = (n, color) => { if (!n || !t) return ''; const len = 100 * n / t, s = html`<circle cx="18" cy="18" r="15.9155" fill="none" stroke="${color}" stroke-width="3.6" ${a.present === t || n === t ? raw('stroke-linecap="round"') : ''} stroke-dasharray="${len.toFixed(2)} ${(100 - len).toFixed(2)}" stroke-dashoffset="${(-off).toFixed(2)}"/>`; off += len; return s; };
  const p = t ? Math.round(a.present / t * 100) : 0;
  return html`<div class="sp-donut"><svg viewBox="0 0 36 36" aria-hidden="true"><circle cx="18" cy="18" r="15.9155" fill="none" stroke="#eceae6" stroke-width="3.6"/>${arc(a.present, '#14633b')}${arc(a.absent, '#a11d12')}${arc(a.leave, '#b7791f')}</svg><div><b>${t ? p + '%' : '—'}</b><span>Present</span></div></div>
    <div class="sp-legend"><span><i style="background:#14633b"></i>${a.present} present</span><span><i style="background:#a11d12"></i>${a.absent} absent</span><span><i style="background:#b7791f"></i>${a.leave} leave</span></div>`;
}

const spCard = (icon, heading, body, link, id, cls, sub) => html`<section class="sp-card ${cls || ''}" ${id ? raw(`id="${id}"`) : ''}><div class="sp-card-head"><div class="sp-card-title"><span class="sp-ic">${sIc(icon)}</span><h2>${heading}</h2></div>${link || ''}</div>${sub ? html`<p class="sp-sub">${sub}</p>` : ''}${body}</section>`;

/** Grid-based table used by the portal cards: cols is a grid-template-columns value. */
const gTable = (cols, head, rows, cls) => html`<div class="sp-gt ${cls || ''}" style="--cols:${cols}"><div class="sp-gt-h">${head.map(h => html`<span>${h}</span>`)}</div>${rows.map(r => html`<div class="sp-gt-r">${r.map(c => html`<span>${c}</span>`)}</div>`)}</div>`;

function spWeek(lessons) {
  if (!lessons.length) return empty('No lessons in the timetable yet.');
  const periods = [...new Set(lessons.map(l => l.period))].sort((a, b) => a - b);
  return gTable('70px repeat(5,minmax(0,1fr))', ['Period', ...DAYS], periods.map(p => [html`<b>${p}</b>`, ...DAYS.map((d, i) => {
    const l = lessons.find(x => x.day === i && x.period === p);
    return l ? html`<div class="lesson"><b>${l.subject}</b><div>${l.teacher}${l.room ? ' · ' + l.room : ''}</div></div>` : '';
  })]), 'sp-week');
}

function recentActivity(d) {
  const items = [];
  if (d.results.length) items.push(['#6b1730', I18N.lang === 'ar' ? 'نُشرت ' + count(d.results.length, 'result') : d.results.length === 1 ? 'Result published' : d.results.length + ' results published', count(d.results.length, 'result')]);
  d.notices.slice(0, 2).forEach(n => items.push(['#2563eb', 'New notice: ' + inLang(n.titleEn, n.titleAr), fmtDate(n.startsAt)]));
  if (d.attendance.recent.length) { const r = d.attendance.recent[0]; items.push([r.status === 'PRESENT' ? '#14633b' : r.status === 'ABSENT' ? '#a11d12' : '#b7791f', 'Marked ' + title(r.status), fmtDate(r.date)]); }
  if (d.materials.length) { const m = d.materials[0]; items.push(['#b7791f', 'Study material: ' + m.title, m.subject + ' · ' + m.teacher]); }
  return items.length ? html`<div class="sp-act">${items.slice(0, 5).map(([c, t, s]) => html`<div><i style="background:${c}"></i><div><b>${t}</b><small>${s}</small></div></div>`)}</div>` : empty('Nothing yet.');
}

const SP_TONES = {
  good: ['#14633b', '#e3f3ea', 'rgba(20,99,59,.35)'], bad: ['#a11d12', '#fde8e6', 'rgba(161,29,18,.35)'],
  watch: ['#b7791f', '#fbefd0', 'rgba(183,121,31,.35)'], brand: ['#6b1730', '#f6e4ea', 'rgba(107,23,48,.35)']
};
/** go: '#/route' opens that page; 'scroll:id' brings that card into view on this page and makes it glow. */
const spKpi = (tone, ic, v, label, link, go) => { const [c, tile, glow] = SP_TONES[tone]; const tag = go && !go.startsWith('scroll:') ? 'a' : 'div';
  const target = go ? (go.startsWith('scroll:') ? raw(`role="button" tabindex="0" data-act="kpiScroll" data-target="${esc(go.slice(7))}"`) : raw(`href="${esc(go)}" data-kpi="1"`)) : '';
  return html`<${raw(tag)} class="sp-kpi ${go ? 'kpi-a' : ''}" ${target} style="background:linear-gradient(170deg,${tile} 0%,#fff 60%)"><span class="sp-ic sp-ic-kpi" style="background:${c};box-shadow:0 8px 16px ${glow}">${sIc(ic)}</span><b>${v}</b><span class="sp-kpi-l">${label}</span>${link || ''}
  <svg class="sp-spark" viewBox="0 0 100 40" preserveAspectRatio="none" aria-hidden="true"><polygon points="0,40 0,30 25,26 50,32 75,16 100,22 100,40" fill="${c}" fill-opacity=".14"/><polyline points="0,30 25,26 50,32 75,16 100,22" fill="none" stroke="${c}" stroke-width="2" stroke-linejoin="round" vector-effect="non-scaling-stroke"/></svg></${raw(tag)}>`; };

/** o: { name, sub, mine, below: { top, bottom } } */
function portalHome(d, o) {
  const a = d.attendance, mine = o.mine;
  const tot = d.results.filter(r => !r.absent && r.value != null).map(markPct);
  const avg = tot.length ? Math.round(tot.reduce((x, y) => x + y, 0) / tot.length) : null;
  const tiles = (mine
    ? [['list', '#/results', 'My results'], ['folder', '#/mymaterials', 'Study materials'], ['megaphone', '#/notices', 'Notices'], ['bell', '#/notifications', 'Notifications'], ['calendar', null, 'My timetable', 'sp-timetable'], ['gear', '#/prefs', 'My preferences']]
    : [['users', '#/children', 'My children'], ['list', null, 'Results', 'sp-results'], ['megaphone', '#/notices', 'Notices'], ['bell', '#/notifications', 'Notifications'], ['calendar', null, 'Timetable', 'sp-timetable'], ['gear', '#/prefs', 'Preferences']])
    .map(([ic, href, label, target]) => href ? html`<a class="sp-tile" href="${href}"><span class="sp-ic sp-ic-tile">${sIc(ic)}</span>${label}</a>` : html`<button type="button" class="sp-tile" data-act="scrollTo" data-target="${target}"><span class="sp-ic sp-ic-tile">${sIc(ic)}</span>${label}</button>`);
  const heroBtn = mine ? html`<a class="sp-btn" href="#/results">View my results →</a>` : (d.results.length ? html`<button type="button" class="sp-btn" data-act="scrollTo" data-target="sp-results">View results →</button>` : '');
  const last = a.recent[0];
  const days = a.present + a.absent + a.leave, rate = n => days ? Math.round(n / days * 100) + '%' : '—'; // documented = leave (excused), undocumented = absent
  return html`<div class="sp-grid-main"><div class="sp-col">
    ${examBanner(d.notices)}${classNoticeBanners(d.notices)}
    <section class="sp-hero"><i class="sp-hero-glow"></i><i class="sp-hero-orb"></i>${raw(SP_SKY)}<img class="sp-hero-art" src="app/student-hero.png" alt="Student studying on a laptop"><div class="sp-hero-text"><div class="sp-welcome">Welcome back,</div><h1>${o.name}</h1><p>${o.sub}</p>${heroBtn}</div></section>
    ${o.below && o.below.top || ''}
    <section class="sp-kpis">${spKpi('brand', 'pulse', rate(a.absent + a.leave), trText('Absence rate') + ' · ' + ofDays(a.absent + a.leave, days), '', 'scroll:sp-attendance')}${spKpi('good', 'check', rate(a.leave), trText('Documented absence') + ' · ' + ofDays(a.leave, days), '', 'scroll:sp-attendance')}${spKpi('bad', 'x', rate(a.absent), trText('Undocumented absence') + ' · ' + ofDays(a.absent, days), '', 'scroll:sp-attendance')}${spKpi('brand', 'star', avg === null ? '—' : avg + '%', 'Average grade · all assessments', mine ? html`<span class="sp-kpi-a">View results →</span>` : '', mine ? '#/results' : 'scroll:sp-results')}</section>
    <div class="sp-row">
      ${spCard('trend', mine ? 'My progress' : 'Progress', progressChart(d.results), mine ? html`<a class="sp-link" href="#/results">Full report →</a>` : '', null, 'sp-f2', 'Average grade by assessment')}
      ${spCard('circle', 'Attendance', html`${attendanceDonut(a)}${last ? html`<div class="sp-last"><span>${fmtDate(last.date)}</span>${badge(last.status === 'PRESENT' ? 'b-good' : last.status === 'ABSENT' ? 'b-bad' : 'b-watch', title(last.status))}</div>` : ''}`, '', 'sp-attendance', 'sp-f1 sp-att', 'Last days')}
    </div>
    <div class="sp-row">
      ${spCard('moon', d.today.length || d.schoolDay ? "Today's lessons" : 'Next school day: Sunday', (d.today.length ? d.today : d.schoolDay ? [] : d.week.filter(l => l.day === 0).sort((a, b) => a.period - b.period)).length ? gTable('70px 1.2fr 1.2fr 80px', ['Period', 'Subject', 'Teacher', 'Room'], (d.today.length ? d.today : d.week.filter(l => l.day === 0).sort((a, b) => a.period - b.period)).map(l => [l.period, l.subject, l.teacher, l.room || ''])) : html`<div class="sp-empty"><span class="sp-ic sp-ic-soft">${sIc('moon')}</span>${d.schoolDay ? 'No lessons today.' : 'Not a school day. Lessons run Sunday to Thursday.'}</div>`, '', null, 'sp-f3')}
      ${spCard('megaphone', 'Notices', d.notices.length ? html`<div class="sp-notices">${d.notices.map(n => html`<div><span class="sp-ic sp-ic-note">${sIc('megaphone')}</span><div><b>${say(n.titleEn, n.titleAr)}</b><small>${fmtDate(n.startsAt)} to ${fmtDate(n.endsAt)} · ${noticeWho(n)}</small><p>${say(n.bodyEn, n.bodyAr)}</p></div></div>`)}</div>` : empty('No notices right now.'), html`<a class="sp-link" href="#/notices">View all →</a>`, null, 'sp-f3')}
    </div>
    <div class="sp-row">${spCard('book', 'Study materials', d.materials.length ? gTable('1.2fr 1fr 1.3fr 60px', ['Title', 'Subject', 'Teacher', ''], d.materials.map(m => [html`<b>${m.title}</b>`, m.subject, m.teacher, html`<a href="${m.link}" target="_blank" rel="noopener noreferrer">${openWord()}</a>`])) : empty('No materials yet.'), '', null, 'sp-f3')}</div>
    ${spCard('calendar', mine ? 'My timetable' : 'Timetable', html`<div class="sp-scroll">${spWeek(d.week)}</div>`, '', 'sp-timetable')}
    ${o.below && o.below.bottom || ''}
  </div>
  <aside class="sp-rail"><section class="sp-quick"><i class="sp-quick-glow"></i><h2>Quick access</h2><div class="sp-tiles">${tiles}</div></section>
    ${spCard('clock', 'Recent activity', recentActivity(d), html`<a class="sp-link" href="#/notifications">View all →</a>`)}</aside></div>
    <section class="sp-foot"><span class="sp-ic sp-ic-foot">${sIc('users')}</span><div><b>Together for a better tomorrow</b><div>Thank you for being part of our school community.</div></div><a class="sp-learn" href="#/notices">Learn more →</a></section>`;
}

// ---- parent portal: the same home screen, for each linked child
SCREENS.children = async (arg) => {
  const kids = await get('/api/parent/children');
  const addChild = card('Follow another child', 'Enter the invitation code the school gave you.', html`<form data-form="addChild" class="row"><input class="input mono" name="code" placeholder="XXXX-XXXX-XXXX" autocomplete="off" required><button class="btn">Add child</button></form>`);
  if (!kids.length) return page('Welcome', null, html`${card('No child linked yet', null, empty('Ask your school for an invitation code, then enter it above.'))}${addChild}`);
  const id = kids.some(k => k.id === arg) ? arg : kids[0].id;
  const kid = kids.find(k => k.id === id);
  const welcome = 'Welcome, ' + S.me.displayName;
  const switcher = kids.length > 1 ? html`<div class="row" style="margin-bottom:16px">${kids.map(k => html`<a class="btn ${k.id === id ? 'btn-primary' : ''}" href="#/children/${k.id}" style="text-decoration:none">${k.name}</a>`)}</div>` : '';
  if (!kid.section) {
    return page(welcome, kid.name + ' is not in a class yet.', html`${switcher}${await classRequestCard(kid.id, kid.pendingClass)}${addChild}`);
  }
  const [d, pv] = await Promise.all([get(`/api/parent/children/${id}`), get(`/api/points/child/${id}`).catch(() => null)]);
  const terms = [...new Set(d.results.map(r => r.term))];
  const results = terms.length ? html`${terms.map(t => spCard('list', 'Results · term ' + t, resultTable(d.results.filter(r => r.term === t))))}` : spCard('list', 'Results', empty('No results have been published yet.'));
  return portalHome(d, { name: S.me.displayName, mine: false, sub: `Following ${kid.name} · Section ${kid.section} · ${fmtDate(d.date)}`,
    below: { top: switcher, bottom: html`<div id="sp-results" class="sp-stack">${results}</div>${pv ? pointsCard(pv) : ''}${addChild}` } });
};

SCREENS.results = async () => {
  const st = await get('/api/enrol/status');
  if (!st.section) return waitingPage(st);
  const rows = await get('/api/marks/mine');
  const terms = [...new Set(rows.map(r => r.term))];
  return page('My results', 'Your own results, shown after your school publishes them.',
    html`${resultSummary(rows)}${terms.length ? terms.map((t, i) => spCard('list', 'Term ' + t, resultTable(rows.filter(r => r.term === t)), '', 'res-term-' + i, '', count(rows.filter(r => r.term === t).length, 'assessment'))) : card('Results', null, empty('No results have been published yet.'))}`);
};

/** Results as modern rows: subject, assessment, mark and a score bar. */
function resultTable(rows) {
  return gTable('1.2fr 1.2fr .9fr 1.4fr', ['Subject', 'Assessment', 'Mark', 'Score'], rows.map(r => {
    if (r.absent || r.value == null) return [html`<b>${r.subject}</b>`, r.assessment, badge('b-watch', 'Absent'), ''];
    const p = markPct(r), tone = p >= 75 ? 'good' : p >= 50 ? 'watch' : 'bad';
    return [html`<b>${r.subject}</b>`, r.assessment, html`<b>${r.value}</b> / ${r.maxMark}`, html`<div class="sp-score"><div class="sp-bar"><i class="${tone}" style="width:${p}%"></i></div><em>${p}%</em></div>`];
  }));
}
function resultSummary(rows) {
  const done = rows.filter(r => !r.absent && r.value != null).map(markPct);
  if (!done.length) return '';
  const avg = Math.round(done.reduce((x, y) => x + y, 0) / done.length);
  return html`<section class="sp-kpis">${spKpi('brand', 'star', avg + '%', 'Average grade', '', 'scroll:res-term-0')}${spKpi('good', 'check', Math.max(...done) + '%', 'Best result', '', 'scroll:res-term-0')}${spKpi('watch', 'list', done.length, 'Assessments marked', '', 'scroll:res-term-0')}${spKpi('bad', 'trend', [...new Set(rows.map(r => r.subject))].length, 'Subjects', '', 'scroll:res-term-0')}</section>`;
}

SCREENS.mymaterials = async () => {
  const st = await get('/api/enrol/status');
  if (!st.section) return waitingPage(st);
  const list = await get('/api/materials');
  return page('Study materials', 'Shared by your teachers.', list.length
    ? html`<div class="sp-mats">${list.map(m => html`<a class="sp-mat" href="${m.link}" target="_blank" rel="noopener noreferrer"><span class="sp-ic sp-ic-note">${sIc('book')}</span><div><b>${m.title}</b><small>${m.subject} · ${m.teacher}</small><small>Shared ${fmtDate(m.createdAt)}</small></div><span class="sp-open">Open →</span></a>`)}</div>`
    : card('Materials', null, empty('No materials yet.')));
};

// ---- Coordinator: academic setup. School IT: unlock marks, publish results, notices
SCREENS.academic = async (arg, q) => {
  invalidate('people');
  const [sections, terms, subjects, reqs, unenrolled] = await Promise.all([get('/api/academic/sections'), get('/api/academic/terms'), loadSubjects(), get('/api/enrol/requests'), get('/api/enrol/unenrolled'), loadPeople()]);
  const sid = q.get('s') || (sections[0] && sections[0].id);
  const staff = Object.values(S.people).filter(p => ['TEACHER', 'COORDINATOR'].includes(p.role));
  const students = Object.values(S.people).filter(p => p.role === 'STUDENT');
  let detail = '';
  if (sid) {
    const [roster, assigns, lessons] = await Promise.all([get(`/api/academic/sections/${sid}/students`), get(`/api/academic/sections/${sid}/assignments`), get(`/api/academic/timetable?sectionId=${sid}`)]);
    detail = html`
      ${card('Students', roster.length + ' enrolled', html`${table(['Student', 'E-mail'], roster.map(r => ({ cells: [r.name, r.email] })))}
        <form data-form="enrol" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="studentEmail">${unenrolled.map(p => html`<option value="${p.email}">${p.name} · ${p.studentCode || ''} (${p.email})</option>`)}</select><button class="btn">Enrol in this section</button></form>
        ${unenrolled.length ? '' : html`<p class="muted">Every student already has a class. New students appear here once the school has added them.</p>`}`)}
      ${card('Who teaches what', null, html`${table(['Subject', 'Teacher'], assigns.map(a => ({ cells: [a.subject, a.teacher] })))}
        <form data-form="assign" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="subjectId">${subjects.map(x => html`<option value="${x.id}">${x.name}</option>`)}</select><select class="input" name="teacherId">${staff.map(p => html`<option value="${p.id}">${p.displayName}</option>`)}</select><button class="btn">Assign</button></form>`)}
      ${card('Timetable', 'Sunday to Thursday. A section, a teacher or a room cannot be in two places in one period.', html`${weekGrid(lessons, 'teacher')}
        ${lessons.length ? table(['Day', 'Period', 'Subject', 'Teacher', 'Room', ''], lessons.map(l => ({ cells: [DAYS[l.day], l.period, l.subject, l.teacher, l.room || '', html`<button class="btn btn-sm btn-danger" data-act="removeLesson" data-id="${l.id}" data-confirm="Remove this lesson?">Remove</button>`] }))) : ''}
        <form data-form="lesson" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="day">${DAYS.map((d, i) => html`<option value="${i}">${d}</option>`)}</select><input class="input" type="number" name="period" min="1" max="12" placeholder="Period" required><select class="input" name="subjectId">${assigns.map(a => html`<option value="${a.subjectId}">${a.subject}</option>`)}</select><input class="input" name="room" placeholder="Room (optional)"><button class="btn btn-primary">Add lesson</button></form>`)}`;
  }
  const reqCard = reqs.length ? card('Class requests', reqs.length + ' waiting for you', table(['Student', 'ID', 'Class asked for', 'Asked by', ''], reqs.map(r => ({ cells: [r.student, html`<span class="mono">${r.studentCode}</span>`, r.section, r.requestedBy, html`<div class="row"><button class="btn btn-sm btn-primary" data-act="approveReq" data-id="${r.id}">Enrol</button><button class="btn btn-sm" data-act="declineReq" data-id="${r.id}">Decline</button></div>`] })))) : '';
  const termCard = card('Terms', 'Teachers can only set assessments for a term that exists here.', html`${terms.length ? table(['Term', 'Name', 'Starts', 'Ends'], terms.map(t => ({ cells: [t.number, t.name, fmtDate(t.startsOn), fmtDate(t.endsOn)] }))) : empty('No term yet. Add term 1 below.')}
      <form data-form="term" class="row" style="margin-top:12px"><select class="input" name="number"><option>1</option><option>2</option><option>3</option></select><input class="input" name="name" placeholder="Name, e.g. Term 1" required><input class="input" type="date" name="startsOn" required><input class="input" type="date" name="endsOn" required><button class="btn btn-primary">Save term</button></form>`);
  const subjectCard = card('Subjects', subjects.length ? subjects.map(x => x.name).join(', ') : 'No subject yet.', html`<form data-form="subject" class="row"><input class="input" name="name" placeholder="e.g. Mathematics" required><button class="btn">Add subject</button></form>`);
  const typeCard = card('Document types', 'The default sensitivity Hafez gives that kind of document. Add a type or change one.', html`<form data-form="docType" class="row"><input class="input" name="name" placeholder="Type name" required><select name="sensitivity">${options(SENS, 'INTERNAL')}</select><button class="btn">Save</button></form>`);
  return page('Academic setup', 'Terms, subjects, classes and sections, students, teachers and the weekly timetable.',
    html`${reqCard}${termCard}${subjectCard}${typeCard}${card('Classes and sections', null, html`<div class="row">${sections.length ? sectionPicker(sections, sid, 'academic') : ''}</div>
      <form data-form="section" class="row" style="margin-top:12px"><input class="input" type="number" name="grade" min="1" max="12" placeholder="Grade 1 to 12" required><input class="input" name="name" placeholder="Section, e.g. B" required><button class="btn btn-primary">Add class section</button></form>`)}
    ${detail}`);
};

SCREENS.noticesAdmin = async () => {
  const list = await get('/api/notices/manage');
  const now = Date.now();
  const state = n => new Date(n.endsAt) <= now ? badge('b-neutral', 'Ended') : new Date(n.startsAt) > now ? badge('b-watch', 'Scheduled') : badge('b-good', 'Showing');
  return page('Notices', 'A notice shows only between its start and end, and needs its Arabic text.',
    html`${card('New notice', null, html`<form data-form="notice">
      ${field('Title (English)', html`<input name="titleEn" required>`)}${field('Title (Arabic)', html`<input name="titleAr" dir="rtl" required>`)}
      ${field('Text (English)', html`<textarea name="bodyEn" required></textarea>`)}${field('Text (Arabic)', html`<textarea name="bodyAr" dir="rtl" required></textarea>`)}
      <div class="row">${field('Starts', html`<input type="datetime-local" name="startsAt" required>`)}${field('Ends', html`<input type="datetime-local" name="endsAt" required>`)}${field('Shown to', html`<select name="audience"><option value="ALL">Everyone</option><option value="STAFF">Staff</option><option value="STUDENTS">Students</option></select>`)}</div>
      <button class="btn btn-primary">Publish notice</button></form>`)}
    ${card('All notices', null, table(['Title', 'Shown to', 'From', 'To', 'State', ''], list.map(n => ({ cells: [inLang(n.titleEn, n.titleAr), title(n.audience), fmt(n.startsAt), fmt(n.endsAt), state(n), html`<button class="btn btn-sm btn-danger" data-act="removeNotice" data-id="${n.id}" data-confirm="Remove this notice?">Remove</button>`] }))))}`);
};

// =============================================================== classroom: homework, quizzes (Assessments), chat, class notices
const isTeacherRole = () => !!S.me && ['TEACHER', 'COORDINATOR'].includes(S.me.role);
/** The sections and subjects this teacher teaches, so every choice in a form is allowed. */
async function myPairs() {
  const sections = await get('/api/academic/sections');
  return (await Promise.all(sections.map(async s => (await get(`/api/academic/sections/${s.id}/assignments`).catch(() => []))
    .filter(a => a.teacherId === S.me.id).map(a => ({ value: s.id + '|' + a.subjectId, label: sectionLabel(s) + ' · ' + a.subject, sectionId: s.id, section: sectionLabel(s) }))))).flat();
}
const weekStart = d => { const x = toDate(d || new Date()); x.setDate(x.getDate() - x.getDay()); return isoLocal(x); };   // the school week starts on Sunday
const addDays = (d, n) => { const x = toDate(d); x.setDate(x.getDate() + n); return isoLocal(x); };
const nextSchoolDay = () => { const x = new Date(); do { x.setDate(x.getDate() + 1); } while (x.getDay() > 4); return isoLocal(x); };
const SUBJ_COLOURS = ['#8f2447', '#2563eb', '#14633b', '#b7791f', '#7c3aed', '#0e7490', '#c2410c', '#be185d'];
const subjColour = name => { let h = 0; for (const c of String(name || '')) h = (h * 31 + c.charCodeAt(0)) >>> 0; return SUBJ_COLOURS[h % SUBJ_COLOURS.length]; };

// ---- homework: one column per school day, each piece of homework under the day it is due
SCREENS.homework = async (arg, q) => {
  const teacher = isTeacherRole();
  if (S.me.role === 'STUDENT') { const st = await get('/api/enrol/status'); if (!st.section) return waitingPage(st); }
  const today = isoLocal(new Date());
  const from = weekStart(q.get('w') || today), to = addDays(from, 6);
  const [list, pairs] = await Promise.all([get(`/api/homework?from=${from}&to=${to}`), teacher ? myPairs() : Promise.resolve([])]);
  const days = [0, 1, 2, 3, 4].map(i => addDays(from, i));
  [5, 6].forEach(i => { const d = addDays(from, i); if (list.some(h => h.dueOn === d)) days.push(d); });
  const subjects = [...new Set(list.map(h => h.subject))].sort();
  const item = h => html`<article class="hw-item ${h.done ? 'hw-isdone' : ''}" style="--c:${subjColour(h.subject)}"><div class="hw-item-top"><span class="hw-subj">${h.subject}</span>${teacher ? html`<span class="hw-tag">${'Class'} ${h.section}</span>` : ''}${h.children && h.children.length ? html`<span class="hw-tag">${h.children.join(', ')}</span>` : ''}</div>
    <b>${raw(esc(h.title))}</b>${h.details ? html`<p>${raw(esc(h.details))}</p>` : ''}<small>${teacher ? html`${'Set on'} ${fmtDate(h.createdAt)}` : h.teacher}</small>
    ${S.me.role === 'STUDENT' ? html`<button type="button" class="hw-done ${h.done ? 'on' : ''}" data-act="homeworkDone" data-id="${h.id}" data-done="${h.done ? '1' : ''}" aria-pressed="${h.done}">${h.done ? '✓ Done' : 'Mark as done · +5 points'}</button>`
      : S.me.role === 'PARENT' ? (h.done ? html`<span class="hw-donetag">✓ Done</span>` : '')
      : teacher ? html`<span class="hw-count"><b>${h.doneCount}</b> / ${h.classSize} ${'done'}</span>` : ''}
    ${h.mine ? html`<button type="button" class="hw-del" data-act="removeHomework" data-id="${h.id}" data-confirm="Remove this homework?" aria-label="Remove homework">×</button>` : ''}</article>`;
  const dayCol = d => {
    const items = list.filter(h => h.dueOn === d);
    return html`<section class="hw-day ${d === today ? 'hw-today' : ''} ${d < today ? 'hw-past' : ''}"><header><span>${toDate(d).toLocaleDateString(uiLocale(), { weekday: 'long' })}</span><b>${fmtDayShort(d)}</b>${d === today ? html`<em>Today</em>` : ''}<i title="Homework due">${items.length}</i></header>
      <div class="hw-items">${items.length ? items.map(item) : html`<div class="hw-none">No homework due</div>`}</div></section>`;
  };
  const weekNav = html`<div class="hw-nav"><a class="btn btn-sm" href="#/homework?w=${addDays(from, -7)}">← Previous week</a><b>${fmtDayShort(from)} – ${fmtDate(addDays(from, 4))}</b><a class="btn btn-sm" href="#/homework?w=${addDays(from, 7)}">Next week →</a>${from !== weekStart(today) ? html`<a class="btn btn-sm btn-quiet" href="#/homework">This week</a>` : ''}</div>`;
  const summary = html`<div class="hw-sum"><span class="hw-total"><b>${count(list.length, 'homework')}</b> ${'this week'}</span>${subjects.map(s => html`<span class="hw-chip" style="--c:${subjColour(s)}">${s} · ${list.filter(h => h.subject === s).length}</span>`)}</div>`;
  const form = !teacher ? '' : card('Set homework', 'The class and their parents see it on their daily schedule, under the day it is due.', pairs.length ? html`<form data-form="homework" class="hw-form">
      ${field('Class and subject', html`<select name="pair">${pairs.map(m => html`<option value="${m.value}">${m.label}</option>`)}</select>`)}
      ${field('Due on', html`<input type="date" name="dueOn" value="${nextSchoolDay()}" required>`)}
      ${field('Title', html`<input name="title" maxlength="200" required placeholder="e.g. Exercise 4, page 32">`)}
      ${field('Details (optional)', html`<textarea name="details" rows="2" maxlength="4000" placeholder="What to do, what to bring"></textarea>`)}
      <button class="btn btn-primary">Set homework</button></form>` : empty('You are not assigned to a section and subject yet.'));
  return page('Homework', teacher ? 'What you have set, day by day, by the date it is due.' : S.me.role === 'PARENT' ? "Your children's homework for every subject, day by day." : 'Your homework for every subject, day by day, by the date it is due.',
    html`${form}${card('Daily schedule', 'Sunday to Thursday', html`${weekNav}${summary}<div class="hw-week">${days.map(dayCol)}</div>`)}`);
};

// ---- quizzes ("Assessments"): the teacher builds them, the student takes them and is marked at once
const pctOf = (s, t) => t ? Math.round(s * 100 / t) : 0;
const quizGrade = p => p >= 90 ? ['Excellent', 'good'] : p >= 75 ? ['Very good', 'good'] : p >= 60 ? ['Good', 'watch'] : p >= 50 ? ['Pass', 'watch'] : ['Keep practising', 'bad'];
function quizQuestionBlock() {
  const k = (S.qzSeq = (S.qzSeq || 0) + 1);
  return html`<div class="qz-q" data-k="${k}" data-kind="CHOICE"><div class="qz-q-head"><b class="qz-num">Question</b>
      <select class="input qz-kind" name="k${k}" aria-label="Type of question"><option value="CHOICE">Multiple choice</option><option value="TRUEFALSE">True or false</option><option value="SHORT">Short answer</option></select>
      <button type="button" class="btn btn-sm btn-quiet" data-act="removeQuestion">Remove</button></div>
    ${field('Question', html`<textarea name="p${k}" rows="2" maxlength="1000" required></textarea>`)}
    <div class="qz-opts qz-for-choice">${[0, 1, 2, 3].map(j => html`<label class="qz-opt"><input type="radio" name="c${k}" value="${j}" ${j === 0 ? raw('checked') : ''} title="Correct answer"><input class="input" name="o${k}_${j}" maxlength="300" placeholder="${j < 2 ? 'Answer' : 'Answer (optional)'}" ${j < 2 ? raw('required') : ''}></label>`)}
      <small class="muted">Tick the circle next to the correct answer.</small></div>
    <div class="qz-tf qz-for-tf"><label class="qz-choice"><input type="radio" name="t${k}" value="0" checked><span>True</span></label><label class="qz-choice"><input type="radio" name="t${k}" value="1"><span>False</span></label><small class="muted">Tick the correct one.</small></div>
    <div class="qz-for-short">${field('Accepted answers, one per line', html`<textarea name="a${k}" rows="2" maxlength="1000" placeholder="e.g. Doha"></textarea>`)}<small class="muted">Capitals, extra spaces and a full stop at the end do not matter.</small></div>
    ${field('Explanation of the correct answer', html`<textarea name="e${k}" rows="2" maxlength="2000" placeholder="Shown to the student after they send the quiz"></textarea>`)}</div>`;
}
function renumberQuestions() { document.querySelectorAll('#qzQuestions .qz-num').forEach((b, i) => { b.textContent = trText('Question') + ' ' + (i + 1); }); }
function readQuestions(form) {
  return [...form.querySelectorAll('.qz-q')].map(q => {
    const k = q.dataset.k, kind = form.elements['k' + k].value, prompt = form.elements['p' + k].value.trim(), explanation = form.elements['e' + k].value.trim();
    if (kind === 'SHORT') return { prompt, kind, options: [], correctIndex: -1, accepted: form.elements['a' + k].value, explanation };
    if (kind === 'TRUEFALSE') return { prompt, kind, options: ['True', 'False'], correctIndex: Number((q.querySelector(`input[name="t${k}"]:checked`) || {}).value || 0), explanation };
    const chosen = Number((q.querySelector(`input[name="c${k}"]:checked`) || {}).value), opts = [];
    let correct = -1;
    [0, 1, 2, 3].forEach(j => { const v = form.elements[`o${k}_${j}`].value.trim(); if (v) { if (j === chosen) correct = opts.length; opts.push(v); } });
    return { prompt, kind, options: opts, correctIndex: correct, explanation };
  });
}

SCREENS.quizzes = async (arg) => {
  if (S.me.role === 'STUDENT') { const st = await get('/api/enrol/status'); if (!st.section) return waitingPage(st); }
  if (arg) return quizPage(arg);
  const list = await get('/api/quizzes');
  if (isTeacherRole()) {
    const pairs = await myPairs();
    const form = pairs.length ? html`<form data-form="quiz" class="qz-form">
      <div class="qz-grid">${field('Class and subject', html`<select name="pair">${pairs.map(m => html`<option value="${m.value}">${m.label}</option>`)}</select>`)}${field('Title', html`<input name="title" required maxlength="200" placeholder="e.g. Fractions check">`)}</div>
      ${field('Source material', html`<textarea name="sourceMaterial" rows="3" maxlength="4000" placeholder="The chapter, pages or text the questions come from"></textarea>`)}
      ${field('Link to the material (optional)', html`<input type="url" name="sourceLink" placeholder="https://…">`)}
      <div id="qzQuestions">${quizQuestionBlock()}</div>
      <div class="row qz-actions"><button type="button" class="btn" data-act="addQuestion">+ Add question</button><button class="btn btn-primary">Publish quiz</button></div></form>` : empty('You are not assigned to a section and subject yet.');
    return page('Assessments', 'Build a quiz from your material. Students get their grade and the explanations as soon as they send it.',
      html`${card('New quiz', 'Write each question, its answers, tick the correct one and explain why.', form)}
      ${card('Your quizzes', null, table(['Quiz', 'Class', 'Subject', 'Questions', 'Taken by', 'Average', ''], list.map(z => ({ go: '#/quizzes/' + z.id,
        cells: [html`<b>${z.title}</b>`, z.section, z.subject, z.questions, z.attempts, z.averagePercent == null ? '—' : z.averagePercent + '%', html`<button class="btn btn-sm btn-danger" data-act="removeQuiz" data-id="${z.id}" data-confirm="Remove this quiz and its results?">Remove</button>`] }))))}`);
  }
  const todo = list.filter(z => !z.taken), done = list.filter(z => z.taken);
  const avg = done.length ? Math.round(done.reduce((a, z) => a + pctOf(z.score, z.total), 0) / done.length) : null;
  const tile = z => { const p = pctOf(z.score, z.total), tone = quizGrade(p)[1];
    return html`<a class="qz-card ${z.taken ? 'qz-done' : ''}" href="#/quizzes/${z.id}" style="--c:${subjColour(z.subject)}"><span class="qz-subj">${z.subject}</span><b>${raw(esc(z.title))}</b><small>${z.teacher} · ${count(z.questions, 'question')} · ${fmtDate(z.createdAt)}</small>${z.taken ? html`<span class="qz-score qz-${tone}">${z.score}/${z.total} · ${p}%</span>` : html`<span class="qz-go">Start quiz →</span>`}</a>`; };
  return page('Assessments', 'Quizzes from your teachers. You get your grade and the explanations as soon as you send your answers.',
    html`<section class="sp-kpis qz-kpis">${spKpi('brand', 'list', todo.length, 'Waiting for you', '', todo.length ? 'scroll:qz-todo' : '')}${spKpi('good', 'check', done.length, 'Completed', '', done.length ? 'scroll:qz-done' : '')}${spKpi('watch', 'star', avg == null ? '—' : avg + '%', 'Average score', '', done.length ? 'scroll:qz-done' : '')}</section>
    ${spCard('star', 'To do', todo.length ? html`<div class="qz-cards">${todo.map(tile)}</div>` : empty('No quiz is waiting for you.'), '', 'qz-todo')}
    ${spCard('check', 'Completed', done.length ? html`<div class="qz-cards">${done.map(tile)}</div>` : empty('Quizzes you finish appear here with your grade.'), '', 'qz-done')}`);
};

async function quizPage(id) {
  const d = await get('/api/quizzes/' + id), z = d.quiz;
  const sub = html`${z.subject} · ${z.section ? html`${'Class'} ${z.section} · ` : ''}${z.teacher} · ${count(z.questions, 'question')}`;
  const source = d.sourceMaterial || d.sourceLink ? card('Source material', null, html`${d.sourceMaterial ? html`<p class="qz-src">${d.sourceMaterial}</p>` : ''}${d.sourceLink ? html`<a href="${d.sourceLink}" target="_blank" rel="noopener noreferrer">${openWord()} →</a>` : ''}`) : '';
  const back = html`<a class="btn" href="#/quizzes">Back</a>`;
  const student = !z.mine;
  const review = html`<div class="qz-review">${d.questions.map(q => {
    const right = q.right != null ? q.right : q.chosen === q.correctIndex, none = q.kind === 'SHORT' ? !(q.written || '').trim() : q.chosen == null || q.chosen < 0;
    return html`<div class="qz-rq ${student ? (right ? 'ok' : 'no') : ''}"><div class="qz-rq-h"><span class="qz-n">${q.number}</span><b>${q.prompt}</b>${student ? html`<span class="qz-mark">${right ? '✓ Correct' : none ? 'No answer' : '✗ Not quite'}</span>` : ''}</div>
      ${q.kind === 'SHORT' ? html`<ul>${student ? html`<li class="${right ? 'right' : 'wrong'}"><span>${q.written || '—'}</span><em>Your answer</em></li>` : ''}<li class="right"><span>${(q.accepted || '').split('\n').join(' · ')}</span><em>Accepted answers</em></li></ul>`
        : html`<ul>${q.options.map((o, j) => html`<li class="${j === q.correctIndex ? 'right' : ''} ${student && j === q.chosen && j !== q.correctIndex ? 'wrong' : ''}"><span>${o}</span>${j === q.correctIndex ? html`<em>Correct answer</em>` : student && j === q.chosen ? html`<em>Your answer</em>` : ''}</li>`)}</ul>`}
      ${q.explanation ? html`<div class="qz-why"><b>Explanation</b><p>${q.explanation}</p></div>` : ''}</div>`;
  })}</div>`;
  if (z.mine) {
    const avg = d.attempts.length ? Math.round(d.attempts.reduce((a, r) => a + pctOf(r.score, r.total), 0) / d.attempts.length) : null;
    return page(z.title, sub, html`${source}
      ${card('Results', d.attempts.length ? (I18N.lang === 'ar' ? 'أدّاه ' + count(d.attempts.length, 'student') : count(d.attempts.length, 'student') + ' took it') + (avg == null ? '' : ' · average ' + avg + '%') : null,
        d.attempts.length ? table(['Student', 'Score', 'Percent', 'Sent'], d.attempts.map(r => { const p = pctOf(r.score, r.total); return { cells: [r.student, r.score + ' / ' + r.total, badge('b-' + quizGrade(p)[1], p + '%'), fmt(r.submittedAt)] }; })) : empty('Nobody has taken it yet.'))}
      ${card('Questions and answers', 'The correct answer is marked in green.', review)}`,
      html`<button class="btn btn-danger" data-act="removeQuiz" data-id="${z.id}" data-confirm="Remove this quiz and its results?">Remove</button>${back}`);
  }
  if (!z.taken) {
    return page(z.title, sub, html`${source}${spCard('star', 'Your answers', html`<form data-form="takeQuiz" data-id="${id}" data-questions="${d.questions.length}" class="qz-take">
      ${d.questions.map((q, i) => html`<fieldset class="qz-tq"><legend><span class="qz-n">${q.number}</span><span>${q.prompt}</span></legend><div class="qz-choices">${q.kind === 'SHORT' ? html`<input class="input qz-written" name="w${i}" maxlength="300" autocomplete="off" placeholder="Your answer">` : q.options.map((o, j) => html`<label class="qz-choice"><input type="radio" name="a${i}" value="${j}"><span>${o}</span></label>`)}</div></fieldset>`)}
      <div class="qz-sendbar"><span class="muted">You can send it once. Your grade appears straight away.</span><button class="btn btn-primary">Send my answers</button></div></form>`)}`, back);
  }
  const p = pctOf(z.score, z.total), [label, tone] = quizGrade(p), fresh = S.quizJustDone === id, C = 2 * Math.PI * 52;
  S.quizJustDone = null;
  const hero = html`<section class="qz-result qz-${tone} ${fresh ? 'qz-fresh' : ''}"><div class="qz-ring"><svg viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="52" class="bg"/><circle cx="60" cy="60" r="52" class="fg" style="--len:${C.toFixed(1)};--off:${(C * (1 - p / 100)).toFixed(1)}"/></svg><div><b data-count="${p}" data-suffix="%">${p}%</b><span>${z.score} / ${z.total}</span></div></div>
    <div class="qz-result-t"><small>Your grade</small><h2>${label}</h2><p>${I18N.lang === 'ar' ? `أجبت إجابة صحيحة عن ${z.score} من ${z.total} أسئلة.` : `You answered ${z.score} of ${z.total} questions correctly.`} <span>Read the explanations below to see why each answer is right.</span></p></div></section>`;
  return page(z.title, sub, html`${hero}${source}${spCard('check', 'Answers and explanations', review)}`, back);
}

// ---- chat with the teacher
SCREENS.chat = async (arg) => {
  const contacts = await get('/api/chat');
  const id = arg && contacts.some(c => c.id === arg) ? arg : null;
  S.chatWith = id;
  const teacher = isTeacherRole();
  const items = contacts.map(c => html`<a class="ch-contact ${c.id === id ? 'on' : ''}" href="#/chat/${c.id}" data-name="${(c.name + ' ' + c.about).toLowerCase()}"><span class="ch-av">${initialsOf(c.name)}</span><span class="ch-c-t"><b>${c.name}</b><small>${c.about}</small>${c.lastText ? html`<em>${raw(esc(c.lastText))}</em>` : ''}</span>${c.unread ? html`<span class="count">${c.unread}</span>` : c.lastAt ? html`<time>${fmtDayShort(c.lastAt)}</time>` : ''}</a>`);
  const right = id ? html`<div class="ch-thread" id="chatThread" data-id="${id}"><p class="muted" style="padding:20px">Loading…</p></div>`
    : html`<div class="ch-pick"><span class="sp-ic sp-ic-soft">${sIc('users')}</span><b>${contacts.length ? 'Choose someone to write to' : 'Nobody to write to yet'}</b><span>${teacher ? 'Your students and their parents are listed on the left.' : contacts.length ? 'Your teachers are listed with the subjects they teach.' : 'Your teachers appear here once you are in a class.'}</span></div>`;
  return page(teacher ? 'Messages' : 'Chat with teachers', teacher ? 'Write to the students you teach and to their parents.' : S.me.role === 'PARENT' ? "Write to your children's teachers." : 'Ask your teachers a question.',
    html`<div class="ch ${id ? 'ch-open' : ''}"><aside class="ch-list">${contacts.length > 6 ? html`<input class="input ch-filter" id="chFilter" type="search" placeholder="Find a person" autocomplete="off">` : ''}<div class="ch-items">${contacts.length ? items : empty('Nobody yet.')}</div></aside><section class="ch-main">${right}</section></div>`);
};

/** Draws the conversation. full: the whole panel (header, messages, box); otherwise only new messages, so typing is never lost. */
async function drawThread(id, full) {
  const box = document.getElementById('chatThread');
  if (!box || box.dataset.id !== id) return;
  const t = await get('/api/chat/' + id);
  if (!document.getElementById('chatThread') || box.dataset.id !== id) return;
  let lastDay = '';
  const msgs = t.messages.length ? t.messages.map(m => {
    const day = toDate(m.at).toDateString(), sep = day !== lastDay ? html`<div class="ch-day"><span>${fmtDay(m.at)}</span></div>` : '';
    lastDay = day;
    return html`${sep}<div class="ch-m ${m.mine ? 'me' : ''}"><p>${raw(esc(m.body))}</p><time>${toDate(m.at).toLocaleTimeString(uiLocale(), { hour: '2-digit', minute: '2-digit' })}</time></div>`;
  }) : html`<div class="ch-first">${I18N.lang === 'ar' ? 'ابدأ المحادثة مع ' + t.with.name + '.' : 'Say hello to ' + t.with.name + '.'}</div>`;
  const list = document.getElementById('chatMsgs');
  if (full || !list) {
    const w = t.with;
    box.innerHTML = html`<header class="ch-head"><a class="ch-back" href="#/chat" aria-label="Back">←</a><span class="ch-av">${initialsOf(w.name)}</span><div><b>${w.name}</b><small>${ROLE_LABEL[w.role] || ''}${w.about ? ' · ' + w.about : ''}</small></div></header>
      <div class="ch-msgs" id="chatMsgs" data-n="${t.messages.length}">${msgs}</div>
      <form data-form="chat" data-id="${id}" class="ch-send"><textarea name="body" rows="2" maxlength="2000" placeholder="Write a message. Enter sends, Shift+Enter starts a new line." required></textarea><button class="btn btn-primary">Send</button></form>`.s;
  } else if (Number(list.dataset.n) !== t.messages.length) {
    list.innerHTML = html`${msgs}`.s; list.dataset.n = t.messages.length;
  } else return;
  const l = document.getElementById('chatMsgs'); if (l) l.scrollTop = l.scrollHeight;
  const c = document.querySelector(`.ch-contact[href="#/chat/${id}"] .count`); if (c) c.remove();
  refreshChat();
}
setInterval(() => { if (S.me && S.route === 'chat' && S.chatWith && !document.hidden) drawThread(S.chatWith, false).catch(() => { }); }, 8000);
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey && e.target.matches && e.target.matches('.ch-send textarea')) { e.preventDefault(); e.target.form.requestSubmit(); }
});
document.addEventListener('input', e => {
  if (e.target.id !== 'chFilter') return;
  const v = e.target.value.trim().toLowerCase();
  document.querySelectorAll('.ch-contact').forEach(a => { a.hidden = !!v && !a.dataset.name.includes(v); });
});

// ---- class notices: a teacher writes to one class; it shows at the top of their students' and parents' home page
const noticeWho = n => n.sectionId ? html`${n.author ? html`${raw(esc(n.author))} · ` : ''}${'Class'} ${n.section}` : n.audience === 'ALL' ? 'Everyone' : title(n.audience);
function classNoticeBanners(notices) {
  const list = (notices || []).filter(n => n.sectionId);
  if (!list.length) return '';
  return html`<div class="cn-banners">${list.map(n => html`<section class="cn-banner"><i class="cn-glow"></i><span class="sp-ic cn-ic">${sIc('megaphone')}</span><div class="cn-text"><small>${'Class notice'} · ${n.author ? html`${raw(esc(n.author))} · ` : ''}${'Class'} ${n.section}</small><b>${say(n.titleEn, n.titleAr)}</b><p>${say(n.bodyEn, n.bodyAr)}</p></div><span class="cn-until">${'Until'} ${fmtDate(n.endsAt)}</span></section>`)}</div>`;
}
SCREENS.classNotices = async () => {
  const [mine, pairs] = await Promise.all([get('/api/notices/class'), myPairs()]);
  const sections = [...new Map(pairs.map(p => [p.sectionId, p.section])).entries()];
  const now = Date.now();
  const state = n => new Date(n.endsAt) <= now ? badge('b-neutral', 'Ended') : badge('b-good', 'Showing');
  const endDefault = addDays(isoLocal(new Date()), 7) + 'T15:00';
  return page('Class notices', 'A notice for one of your classes appears at the top of the home page of its students and their parents, beside the exam banner.',
    html`${card('New class notice', null, sections.length ? html`<form data-form="classNotice">
      ${field('Class', html`<select name="sectionId">${sections.map(([sid, l]) => html`<option value="${sid}">${l}</option>`)}</select>`)}
      ${field('Title', html`<input name="titleEn" required maxlength="200" placeholder="e.g. Bring your geometry set tomorrow">`)}
      ${field('Message', html`<textarea name="bodyEn" required maxlength="4000"></textarea>`)}
      <details class="cn-ar"><summary>Arabic version (optional)</summary>${field('Title (Arabic)', html`<input name="titleAr" dir="rtl" maxlength="200">`)}${field('Message (Arabic)', html`<textarea name="bodyAr" dir="rtl" maxlength="4000"></textarea>`)}</details>
      ${field('Show until', html`<input type="datetime-local" name="endsAt" value="${endDefault}" required>`)}
      <button class="btn btn-primary">Publish to the class</button></form>` : empty('You are not assigned to a class yet.'))}
    ${card('Your class notices', null, table(['Title', 'Class', 'From', 'Until', 'State', ''], mine.map(n => ({ cells: [inLang(n.titleEn, n.titleAr), n.section, fmt(n.startsAt), fmt(n.endsAt), state(n), html`<button class="btn btn-sm btn-danger" data-act="removeClassNotice" data-id="${n.id}" data-confirm="Remove this notice?">Remove</button>`] }))))}`);
};

// ---- points progression on the student's home page: levels that never run out. Every LEVEL_POINTS points is a new
//      level; the ring and the track show the way through the current level, then start again for the next one.
const LEVEL_POINTS = 50;
const LEVEL_COLOURS = ['#5f9a6e', '#b0702a', '#7d8899', '#c99a0e', '#1f8a9e', '#7c3aed', '#c2410c', '#be185d'];
const levelOf = total => {
  const level = Math.floor(total / LEVEL_POINTS) + 1, start = (level - 1) * LEVEL_POINTS, into = total - start;
  return { level, start, end: start + LEVEL_POINTS, into, pct: Math.round(into * 100 / LEVEL_POINTS), colour: LEVEL_COLOURS[(level - 1) % LEVEL_COLOURS.length] };
};
function pointsProgress(v) {
  const total = Math.max(0, v.total || 0), L = levelOf(total), left = L.end - total;
  const weekAgo = Date.now() - 7 * 864e5, week = (v.entries || []).filter(e => toDate(e.on).getTime() >= weekAgo).reduce((a, e) => a + e.amount, 0);
  // a level reached since the student last looked gets a small celebration
  let up = false;
  try { const k = 'hafez.level.' + S.me.id, seen = Number(localStorage.getItem(k) || 0); up = seen > 0 && L.level > seen; localStorage.setItem(k, String(L.level)); } catch { }
  const C = 2 * Math.PI * 46;
  const star = html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${SPI.star}"/></svg>`;
  const ticks = [1, 2, 3, 4].map(k => html`<span class="pp-stop ${L.into >= k * 10 ? 'on' : ''}" style="--x:${k * 20}%;--c:${L.colour}" title="${L.start + k * 10}"></span>`);
  const ways = [['Homework done', '+' + 5], ['Quiz taken', '+2 to +10'], ['Exam result', 'up to +10']];
  return html`<section class="pp ${up ? 'pp-up' : ''}" style="--lv:${L.colour}" aria-label="${'Points: ' + total + ', level ' + L.level}"><i class="pp-glow"></i>
    ${up ? html`<div class="pp-burst" aria-hidden="true">${[...Array(14)].map((_, k) => html`<i style="--a:${k * 360 / 14}deg;--c:${LEVEL_COLOURS[k % LEVEL_COLOURS.length]}"></i>`)}</div>` : ''}
    <div class="pp-ring"><svg viewBox="0 0 110 110" aria-hidden="true"><circle class="bg" cx="55" cy="55" r="46"/><circle class="fg" cx="55" cy="55" r="46" style="--len:${C.toFixed(1)};--off:${(C * (1 - L.pct / 100)).toFixed(1)}"/></svg>
      <div><b data-count="${total}">${total}</b><span>points</span></div><i class="pp-spark s1"></i><i class="pp-spark s2"></i><i class="pp-spark s3"></i></div>
    <div class="pp-main"><div class="pp-top"><span class="pp-badge">${star}<span>${'Level'} ${L.level}</span></span>${up ? html`<span class="pp-new">Level up!</span>` : ''}${week ? html`<span class="pp-week">+${week} ${'this week'}</span>` : ''}</div>
      <h2>${I18N.lang === 'ar' ? `${count(left, 'point')} للوصول إلى المستوى ${L.level + 1}` : `${count(left, 'point')} to Level ${L.level + 1}`}</h2>
      <p>Earn points by finishing your homework, taking quizzes and doing well in exams.</p>
      <div class="pp-track"><div class="pp-fill" style="--w:${L.pct}%"></div>${ticks}<span class="pp-runner" style="--w:${L.pct}%">${star}</span></div>
      <div class="pp-ends"><span><b>${'Level'} ${L.level}</b><small>${L.start}</small></span><span><b>${'Level'} ${L.level + 1}</b><small>${L.end}</small></span></div>
      <div class="pp-ways">${ways.map(([w, n]) => html`<span><b>${n}</b> ${w}</span>`)}</div></div></section>`;
}

/** After a screen is drawn: count numbers up, number the quiz questions, load an open chat. */
function afterRender() {
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.querySelectorAll('[data-count]').forEach(el => {
    const to = Number(el.dataset.count) || 0, suf = el.dataset.suffix || '';
    if (still || !to) { el.textContent = to + suf; return; }
    const t0 = performance.now(), dur = 1400;
    const step = t => { const k = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - k, 3); el.textContent = Math.round(to * e) + suf; if (k < 1) requestAnimationFrame(step); };
    el.textContent = '0' + suf; requestAnimationFrame(step);
  });
  if (document.getElementById('qzQuestions')) renumberQuestions();
  if (S.route === 'chat' && S.chatWith) drawThread(S.chatWith, true).catch(fail);
}

// =============================================================== actions (buttons)
async function refreshAfter(msg) { if (msg) toast(msg); await render(); }
const ACT = {
  // "My preferences": choosing a language switches the whole app at once and saves it (English and Arabic keeps the English screens)
  prefLang: async (d, sel) => {
    const language = sel.value, form = sel.closest('form');
    S.me = await put('/api/me/preferences', { language, digestMode: form ? form.elements.digestMode.value : S.me.digestMode });
    setLang(language === 'AR' ? 'ar' : 'en');
    await render();
    toast('Saved.');
  },
  setLang: async () => {
    setLang(I18N.lang === 'ar' ? 'en' : 'ar');
    if (S.me) await render(); else showLanding();
  },
  scrollTo: d => { const el = document.getElementById(d.target); if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' }); },
  // test sign-in: pressing a person signs in as them, with their role
  testPick: async d => { await post('/api/auth/test/pick', { id: d.id }); afterAuth(); },
  kpiScroll: d => { const el = document.getElementById(d.target); if (!el) return; el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.classList.remove('kpi-glow'); void el.offsetWidth; el.classList.add('kpi-glow'); },
  addQuestion: () => { const box = document.getElementById('qzQuestions'); if (box) { box.insertAdjacentHTML('beforeend', quizQuestionBlock().s); renumberQuestions(); } },
  removeQuestion: (d, el) => { const q = el.closest('.qz-q'); if (q && document.querySelectorAll('#qzQuestions .qz-q').length > 1) { q.remove(); renumberQuestions(); } },
  homeworkDone: async d => {
    if (d.done) { await del(`/api/homework/${d.id}/done`); await refreshAfter('Marked as not done. Its points were taken back.'); }
    else { await post(`/api/homework/${d.id}/done`); await refreshAfter('Well done! +5 points.'); }
  },
  removeHomework: async d => { await del('/api/homework/' + d.id); await refreshAfter('Homework removed.'); },
  removeQuiz: async d => { await del('/api/quizzes/' + d.id); location.hash = '#/quizzes'; await refreshAfter('Quiz removed.'); },
  removeClassNotice: async d => { await del('/api/notices/' + d.id); await refreshAfter('Notice removed.'); },
  spSubject: (d, t) => {
    const card = t.closest('.sp-card');
    card.querySelectorAll('.sp-tabs button').forEach(b => b.classList.toggle('on', b === t));
    card.querySelectorAll('.sp-chart-box').forEach(b => { b.hidden = b.dataset.subject !== d.i; });
  },
  filter: (d) => { const q = new URLSearchParams(location.hash.split('?')[1] || ''); q.set(d.name, d.value); location.hash = location.hash.split('?')[0] + '?' + q; },
  pickSite: (d, el) => { location.hash = '#/connection?site=' + el.value; },
  fixed: async d => { const r = await post(`/api/issues/${d.id}/fixed`); S.flash = r.fixed ? { ok: 'Checked again: it is fixed. Thank you.' } : { err: 'Not fixed yet: ' + (inLang(r.stillWrongEn, r.stillWrongAr) || 'the file still breaks the rule.') }; await render(); },
  help: async d => { await post(`/api/issues/${d.id}/help`); await refreshAfter('Your coordinator has been asked to help.'); },
  keepRule: async d => { await post(`/api/follow-up/${d.id}/keep-rule`); await refreshAfter('Rule kept. The owner was reminded.'); },
  remind: async d => { await post(`/api/follow-up/${d.id}/remind`); await refreshAfter('Reminder sent.'); },
  coordFixed: async d => { const r = await post(`/api/follow-up/${d.id}/mark-fixed`); S.flash = r.fixed ? { ok: 'Checked again: fixed.' } : { err: 'Still broken: ' + (r.stillWrongEn || '') }; await render(); },
  markAllDone: async () => { const r = await post('/api/follow-up/mark-all-done'); await refreshAfter('Done: ' + JSON.stringify(r)); },
  openNote: async d => { await post(`/api/notifications/${d.id}/read`).catch(() => { }); location.hash = '#/issue/' + d.issue; refreshBell(); },
  readAll: async () => { await post('/api/notifications/read-all'); await refreshAfter('All marked as read.'); refreshBell(); },
  removeLesson: async d => { await del('/api/academic/timetable/' + d.id); await refreshAfter('Lesson removed.'); },
  removeMaterial: async d => { await del('/api/materials/' + d.id); await refreshAfter('Removed.'); },
  removeNotice: async d => { await del('/api/notices/' + d.id); await refreshAfter('Notice removed.'); },
  unlockMarks: async d => { const reason = prompt(trText('Why are you unlocking these marks?')); if (!reason) return; await post(`/api/marks/assessments/${d.id}/unlock`, { reason }); await refreshAfter('Marks unlocked. You can correct them and submit again.'); },
  publishAssessment: async d => { await post(`/api/marks/assessments/${d.id}/publish`); await refreshAfter('Published. Students and parents can see these marks now.'); },
  unpublishAssessment: async d => { const reason = prompt(trText('Why are you unpublishing these results?')); if (!reason) return; await post(`/api/marks/assessments/${d.id}/unpublish`, { reason }); await refreshAfter('Results are hidden again.'); },
  publishResults: async d => { await post(`/api/marks/sections/${d.section}/publish`, { term: Number(d.term) }); await refreshAfter('Results published. Students can see them now.'); },
  unpublishResults: async d => { const reason = prompt(trText('Why are you unpublishing these results?')); if (!reason) return; await post(`/api/marks/sections/${d.section}/unpublish`, { term: Number(d.term), reason }); await refreshAfter('Results are hidden again.'); },
  addPerson: async f => { await post('/api/people', { email: f.email, name: f.name, role: f.role, studentId: f.role === 'PARENT' && f.studentId ? f.studentId : undefined }); invalidate('people'); await refreshAfter(f.email + ' can now sign in.'); },
  landing: async d => { L.view = d.view; L.mode = d.mode || 'login'; if (L.view === 'testname') L.testPeople = await get('/api/auth/test/people').catch(() => null); showLanding(); },
  approveReq: async d => { await post('/api/enrol/requests/' + d.id + '/approve'); await refreshAfter('Student enrolled.'); },
  declineReq: async d => { await post('/api/enrol/requests/' + d.id + '/decline'); await refreshAfter('Request declined.'); },
  renameSchool: async d => { const name = prompt(trText('School name:'), d.name); if (!name || name === d.name) return; await put('/api/sites/' + d.id, { name }); invalidate('sites'); await refreshAfter('Renamed.'); },
  toggleScored: async d => { await put('/api/sites/' + d.id, { scored: d.value === 'true' }); invalidate('sites'); await refreshAfter(d.value === 'true' ? 'Included in the school scores.' : 'No longer scored.'); },
  resolveIncident: async d => { await post(`/api/dashboard/incidents/${d.id}/resolve`); await refreshAfter('Incident resolved.'); },
  pauseRule: async d => { await post(`/api/rules/${d.id}/pause`); await refreshAfter('Rule paused.'); },
  resumeRule: async d => { await post(`/api/rules/${d.id}/resume`); await refreshAfter('Rule resumed.'); },
  deleteRule: async d => { await del(`/api/rules/${d.id}`); await refreshAfter('Rule deleted.'); },
  approveRule: async d => { await post(`/api/rules/${d.id}/versions/${d.version}/approve`); await refreshAfter('Version approved. It applies from the next check.'); },
  syncPeople: async () => { const r = await post('/api/people/sync'); invalidate('people'); await refreshAfter(r.changed + ' people changed.'); },
  welcome: async d => { await post(`/api/people/${d.id}/welcome`); await refreshAfter('Welcome message queued.'); },
  editPerson: async d => {
    const p = S.people[d.id]; if (!p) return;
    const name = prompt(trText('Full name:'), p.displayName); if (name === null) return;
    const email = prompt(trText('Google e-mail (they sign in with this address):'), p.email); if (email === null) return;
    if (name.trim() === p.displayName && email.trim().toLowerCase() === p.email.toLowerCase()) return;
    await patch(`/api/people/${d.id}`, { name: name.trim(), email: email.trim() }); invalidate('people'); await refreshAfter('Saved.');
  },
  deletePerson: async d => { await del(`/api/people/${d.id}`); await refreshAfter('Person deleted.'); },
  personFollowUp: async d => { const note = prompt(trText('Note for the follow-up (optional):')); if (note === null) return; await post(`/api/people/${d.id}/follow-up`, { note }); toast('Follow-up recorded.'); },
  trustType: async d => { await post(`/api/doc-types/${d.id}/trust`); invalidate('docTypes'); await refreshAfter('Type trusted.'); },
  connTest: async d => { const r = await post(`/api/connection/${d.id}/test`); toast((r && r.message) || 'Connection tested.'); },
  connCheck: async d => { const r = await post(`/api/connection/${d.id}/check`); await refreshAfter('Check started. Refresh in a few seconds to see the result.'); },
  connDisconnect: async d => { await post(`/api/connection/${d.id}/disconnect`); await refreshAfter('Google Drive disconnected.'); },
  revokeInvite: async d => { await post(`/api/invitations/${d.id}/revoke`); await refreshAfter('Code revoked.'); },
  setStage: async (d, el) => { await put('/api/marks/students/' + d.id + '/stage', { stage: el.value }); toast('Stage saved.'); },
  removeSchool: async d => { if (!confirm(trText('Remove the school') + ' ' + d.name + '?')) return; await del('/api/sites/' + d.id); await refreshAfter('School removed.'); },
  approval: async d => { const note = d.do === 'reject' ? prompt(trText('Reason (optional):'), '') : null; await post(`/api/approvals/${d.id}/${d.do}`, note ? { note } : {}); await refreshAfter('Done.'); },
  connReconnect: async d => { const r = await post(`/api/connection/${d.id}/reconnect`); await refreshAfter('Reconnected.'); },
  verifyLog: async () => { const r = await get('/api/activity-log/verify'); document.getElementById('verify').innerHTML = loc(r.valid ? `<span class="ok">Log verified: ${r.entriesChecked} entries, none altered.</span>` : `<span class="err">Log broken at entry ${esc(r.firstBrokenSeq)}.</span>`).s; },
  restore: async d => { await post(`/api/backup/restores/${d.id}/${d.do}`); await refreshAfter('Updated.'); },
  photoSave: async () => {
    const file = document.getElementById('photoFile') && document.getElementById('photoFile').files[0];
    if (!file) { toast('Choose a photo first.', true); return; }
    if (file.size > 500 * 1024) { toast('Choose a photo of at most 500 KB.', true); return; }
    S.me = await put('/api/me/photo', { data: await fileToBase64(file) }); S.photoV = Date.now(); await refreshAfter('Photo saved.');
  },
  photoRemove: async () => { S.me = await del('/api/me/photo'); await refreshAfter('Photo removed.'); },
  ticketStatus: async d => { await post(`/api/support/tickets/${d.id}/status`, { status: d.status }); await refreshAfter('Status updated.'); },
  pickupVerify: async d => { await post(`/api/pickup/requests/${d.id}/verify`); await refreshAfter('ID checked. You can release the child now.'); },
  pickupRelease: async d => { await post(`/api/pickup/requests/${d.id}/release`); await refreshAfter('Child released.'); },
  pickupDecline: async d => { const reason = prompt(trText('Why are you declining?')); if (!reason) return; await post(`/api/pickup/requests/${d.id}/decline`, { reason }); await refreshAfter('Request declined.'); },
  attAllPresent: async (d, el) => { const f = el.closest('form'); f.querySelectorAll('.at-seg input[value=PRESENT]').forEach(i => { i.checked = true; }); attTally(f); },
  pickupCancel: async d => { await post(`/api/pickup/requests/${d.id}/cancel`); await refreshAfter('Withdrawn.'); },
  removeCollector: async d => { await del(`/api/pickup/collectors/${d.id}`); await refreshAfter('Collector removed.'); },
  signout: async () => { await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie('XSRF-TOKEN') || '') } }).catch(() => { }); location.href = '/'; }
};

// =============================================================== forms
const csv = s => String(s || '').split(',').map(x => x.trim()).filter(Boolean);
const FORM = {
  deleteFileRequest: async f => {
    await post(`/api/files/${f.fileId}/delete-request`, { reason: f.reason });
    invalidate('files');
    location.hash = '#/approvals';
    await refreshAfter('Sent for approval. The principal or a coordinator must approve before the file is deleted.');
  },
  disagree: async (f, d) => { await post(`/api/issues/${d.id}/disagree`, { note: f.note }); await refreshAfter('Your coordinator will look at it.'); },
  exception: async (f, d) => { await post(`/api/exceptions/issues/${d.id}`, { reason: f.reason, endsOn: f.endsOn }); await refreshAfter('Exception allowed.'); },
  followUpIssue: async (f, d) => { await post(`/api/follow-up/${d.id}/follow-up`, { note: f.note || null }); await refreshAfter('Follow-up recorded.'); },
  classify: async (f, d) => { await put(`/api/files/${d.id}/classification`, { docType: f.docType, sensitivity: f.sensitivity, hasStudentData: !!f.hasStudentData, hasHealthData: !!f.hasHealthData }); await refreshAfter('Saved. The file was checked again.'); },
  submitPlan: async f => { await post('/api/planning', f); await refreshAfter('Plan submitted.'); },
  review: async (f, d, form, submitter) => { await post(`/api/planning/${d.id}/review`, { decision: submitter.value, reason: f.reason || null }); await refreshAfter('Review saved.'); },
  totals: async f => { const t = await get('/api/planning/totals?week=' + f.week); document.getElementById('totals').innerHTML = loc(`<div class="kpi-grid">${[['Submitted', t.submitted], ['Returned', t.returned], ['Approved', t.approved]].map(([a, b]) => `<div class="card kpi"><span class="kpi-label">${a}</span><span class="kpi-value">${esc(b)}</span></div>`).join('')}</div>`).s; },
  rule: async (f, d, form, submitter) => {
    const draft = { name: f.name, whyItMatters: f.whyItMatters, whyItMattersAr: f.whyItMattersAr, checkType: f.checkType, severity: f.severity, reminderDays: Number(f.reminderDays) || 0, parameter: f.parameter || null, fixStepsEn: f.fixStepsEn, fixStepsAr: f.fixStepsAr, docTypes: csv(f.docTypes), sensitivities: [...form.querySelectorAll('[name=sens]:checked')].map(x => x.value) };
    if (submitter.value === 'preview') {
      const p = await post('/api/rules/preview', draft);
      document.getElementById('preview').innerHTML = loc(`<div class="callout callout-info">Would affect <b>${esc(p.wouldBreak)}</b> of ${esc(p.filesChecked)} files (${esc(p.wouldNeedReview)} need review).</div><h3>${esc(p.subjectEn)}</h3><pre style="white-space:pre-wrap">${esc(p.bodyEn)}</pre>`).s;
      return;
    }
    if (d.id === 'new') await post('/api/rules', { draft, networkWide: !!f.networkWide, turnOn: !!f.turnOn }); else await put('/api/rules/' + d.id, draft);
    invalidate('rules'); location.hash = '#/rules'; toast('Rule saved.');
  },
  person: async (f, d) => { await patch(`/api/people/${d.id}`, { role: f.role, status: f.status }); invalidate('people'); await refreshAfter('Saved.'); },
  roleGroup: async f => { await put('/api/people/role-groups', { directoryGroup: f.directoryGroup, role: f.role }); await refreshAfter('Mapping saved.'); },
  site: async f => { await post('/api/sites', { name: f.name, domain: f.domain, scored: !!f.scored, headOffice: false }); invalidate('sites'); await refreshAfter('School added.'); },
  subject: async f => { await post('/api/subjects', { name: f.name }); invalidate('subjects'); await refreshAfter('Subject added.'); },
  docType: async f => { await put('/api/doc-types', { name: f.name, sensitivity: f.sensitivity }); invalidate('docTypes'); await refreshAfter('Saved.'); },
  setting: async (f, d) => { await put('/api/settings', { key: d.key, value: f.value }); await refreshAfter('Saved.'); },
  restoreRequest: async f => { await post('/api/backup/restores', { scope: f.scope, fileId: f.fileId || null, target: f.target || null, reason: f.reason }); await refreshAfter('Restore requested.'); },
  invite: async (f, d, form) => { const ids = [...form.querySelector('[name=studentIds]').selectedOptions].map(o => o.value); S.issued = await post('/api/invitations', { kind: f.kind, studentIds: ids, email: f.email || null }); await render(); },
  award: async f => { await post('/api/points/award', { studentId: f.studentId, amount: Number(f.amount), reason: f.reason }); await refreshAfter('Points given.'); },
  attendance: async (f, d, form) => {
    const statuses = {};
    for (const [k, v] of Object.entries(f)) { if (k.startsWith('st_')) statuses[k.slice(3)] = v; }
    const r = await put('/api/attendance/sections/' + d.section, { date: d.date, statuses, reason: null });
    await refreshAfter(r.saved ? `Attendance saved (${r.saved} changed).` : 'Nothing changed.');
  },
  marks: async (f, d, form, submitter) => {
    const rows = [...form.querySelectorAll('tr[data-student]')].map(tr => {
      const absent = tr.querySelector('input[type=checkbox]').checked, raw = tr.querySelector('input[type=number]').value;
      return { studentId: tr.dataset.student, value: absent || raw === '' ? null : Number(raw), absent };
    }).filter(m => m.absent || m.value !== null);
    if (rows.length) await put(`/api/marks/assessments/${d.id}/marks`, { marks: rows });
    if (submitter.value === 'submit') { await post(`/api/marks/assessments/${d.id}/submit`); await refreshAfter('Marks saved and submitted. They are now locked.'); }
    else await refreshAfter('Marks saved.');
  },
  material: async f => { const [sectionId, subjectId] = f.pair.split('|'); await post('/api/materials', { sectionId, subjectId, title: f.title, link: f.link }); await refreshAfter('Shared with the section.'); },
  term: async f => { await put('/api/academic/terms', { number: Number(f.number), name: f.name, startsOn: f.startsOn, endsOn: f.endsOn }); await refreshAfter('Term saved.'); },
  section: async f => { await post('/api/academic/sections', { grade: Number(f.grade), name: f.name }); await refreshAfter('Section added.'); },
  testSignin: async f => { await post('/api/auth/test/signin', { name: f.name }); afterAuth(); },
  homework: async f => { const [sectionId, subjectId] = f.pair.split('|'); await post('/api/homework', { sectionId, subjectId, title: f.title, details: f.details || '', dueOn: f.dueOn }); location.hash = '#/homework?w=' + weekStart(f.dueOn); await refreshAfter('Homework set. The class and their parents can see it.'); },
  classNotice: async f => { await post('/api/notices/class', { sectionId: f.sectionId, titleEn: f.titleEn, titleAr: f.titleAr || null, bodyEn: f.bodyEn, bodyAr: f.bodyAr || null, startsAt: null, endsAt: new Date(f.endsAt).toISOString() }); await refreshAfter('Notice published. It shows at the top of the class\'s and their parents\' home page.'); },
  quiz: async (f, d, form) => { const [sectionId, subjectId] = f.pair.split('|'); const r = await post('/api/quizzes', { sectionId, subjectId, title: f.title, sourceMaterial: f.sourceMaterial || '', sourceLink: f.sourceLink || null, questions: readQuestions(form) }); toast('Quiz published to the class.'); location.hash = '#/quizzes/' + r.quiz.id; },
  takeQuiz: async (f, d, form) => {
    const n = Number(d.questions), answers = [];
    const written = [];
    for (let i = 0; i < n; i++) { const c = form.querySelector(`input[name="a${i}"]:checked`), w = form.elements['w' + i]; answers.push(c ? Number(c.value) : -1); written.push(w ? w.value : ''); }
    const missing = answers.filter((a, i) => a < 0 && !written[i].trim()).length;
    if (missing && !confirm(I18N.lang === 'ar' ? `${count(missing, 'question')} بلا إجابة. هل تريد الإرسال؟` : (missing === 1 ? 'One question has no answer. Send anyway?' : missing + ' questions have no answer. Send anyway?'))) return;
    await post(`/api/quizzes/${d.id}/submit`, { answers, written });
    S.quizJustDone = d.id; await render();
  },
  chat: async (f, d, form) => { if (!f.body.trim()) return; await post('/api/chat/' + d.id, { body: f.body }); form.reset(); await drawThread(d.id, true); },
  login: async f => { await post('/api/auth/login', { email: f.email, password: f.password }); afterAuth(); },
  redeemStudent: async f => { await post('/api/auth/redeem/student', { code: f.code, password: f.password }); afterAuth(); },
  redeemParent: async f => { await post('/api/auth/redeem/parent', { code: f.code, name: f.name, email: f.email, password: f.password }); afterAuth(); },
  enrolRequest: async (f, d) => { await post('/api/enrol/requests', { studentId: d.student || undefined, sectionId: f.sectionId }); await refreshAfter('Request sent. The coordinator will confirm it.'); },
  addChild: async f => { await post('/api/parent/children', { code: f.code }); await refreshAfter('Child added.'); },
  enrol: async (f, d) => { await post(`/api/academic/sections/${d.section}/students`, { studentEmail: f.studentEmail }); await refreshAfter('Student enrolled.'); },
  assign: async (f, d) => { await put(`/api/academic/sections/${d.section}/assignments`, { subjectId: f.subjectId, teacherId: f.teacherId }); await refreshAfter('Teacher assigned.'); },
  lesson: async (f, d) => { await post('/api/academic/timetable', { sectionId: d.section, subjectId: f.subjectId, day: Number(f.day), period: Number(f.period), room: f.room || null }); await refreshAfter('Lesson added.'); },
  assessment: async f => { const [sectionId, subjectId] = f.pair.split('|'); await post('/api/marks/assessments', { sectionId, subjectId, term: Number(f.term), name: f.name, maxMark: Number(f.maxMark) }); await refreshAfter('Assessment added.'); },
  notice: async f => { await post('/api/notices', { ...f, startsAt: new Date(f.startsAt).toISOString(), endsAt: new Date(f.endsAt).toISOString() }); await refreshAfter('Notice published.'); },
  prefs: async f => {
    const muted = (S.me.alertKinds || []).filter(k => k !== 'SECURITY' && !(('alert_' + k) in f));
    S.me = await put('/api/me/preferences', { language: f.language, digestMode: f.digestMode, muted });
    setLang(f.language === 'AR' ? 'ar' : 'en'); await refreshAfter('Saved.');
  },
  arrive: async (f, d, form) => {
    const who = form.querySelector('input[name=collectorId]:checked'), whoName = who && who.value ? who.closest('label').querySelector('b').textContent : S.me.displayName;
    if (!(await pickupConfirm(d.name, whoName))) return;
    await post('/api/pickup/arrive', { studentId: d.student, collectorId: f.collectorId || null, acknowledged: true });
    await refreshAfter('Picked up. The school can see it now.');
  },
  collector: async (f, d) => { await post(`/api/pickup/children/${d.student}/collectors`, { name: f.name, relationship: f.relationship, idNumber: f.idNumber, phone: f.phone }); await refreshAfter('Collector added.'); },
  profile: async f => { S.me = await put('/api/me/profile', { displayName: f.displayName, phone: f.phone }); await refreshAfter('Details saved.'); },
  ticket: async (f, d, form) => {
    const file = form.querySelector('[name=attachment]').files[0];
    if (file && file.size > 2 * 1024 * 1024) throw new Error('The attachment is too large (at most 2 MB).');
    const t = await post('/api/support/tickets', { audience: f.audience, title: f.title, description: f.description, category: f.category, urgency: f.urgency, attachmentName: file ? file.name : null, attachmentData: file ? await fileToBase64(file) : null });
    toast(f.audience === 'EAA_IT' ? 'Ticket sent to EAA IT.' : f.audience === 'LEADERSHIP' ? 'Ticket sent to Leadership at head office.' : 'Ticket sent to your School IT.'); location.hash = '#/ticket/' + t.id;
  },
  ticketReply: async (f, d) => { await post(`/api/support/tickets/${d.id}/replies`, { body: f.body }); await refreshAfter('Reply sent.'); },
  ticketAssign: async (f, d) => { await post(`/api/support/tickets/${d.id}/assign`, { assigneeId: f.assigneeId }); await refreshAfter('Assigned.'); }
};

document.addEventListener('click', async e => {
  const kp = e.target.closest && e.target.closest('[data-kpi]'); if (kp && kp.getAttribute('href') !== location.hash) S.kpiFlash = true;
  const t = e.target.closest('[data-go],[data-act]');
  if (!t || t.tagName === 'SELECT' || t.tagName === 'OPTION') return;
  if (t.dataset.go) { location.hash = t.dataset.go; return; }
  if (t.dataset.confirm && !confirm(trText(t.dataset.confirm))) return;
  const fn = ACT[t.dataset.act]; if (!fn) return;
  t.disabled = true;
  try { await fn(t.dataset, t); } catch (err) { fail(err); } finally { t.disabled = false; }
});
// landing hero: layers follow the mouse a little (parallax). Skipped when the viewer prefers reduced motion.
let ldRaf = 0;
document.addEventListener('mousemove', e => {
  const hero = e.target.closest && e.target.closest('.ld-hero');
  if (!hero || ldRaf || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  ldRaf = requestAnimationFrame(() => {
    ldRaf = 0;
    const r = hero.getBoundingClientRect();
    hero.style.setProperty('--mx', ((e.clientX - r.left) / r.width * 2 - 1).toFixed(3));
    hero.style.setProperty('--my', ((e.clientY - r.top) / r.height * 2 - 1).toFixed(3));
  });
});
document.addEventListener('mouseout', e => {
  const hero = e.target.closest && e.target.closest('.ld-hero');
  if (hero && !hero.contains(e.relatedTarget)) { hero.style.setProperty('--mx', 0); hero.style.setProperty('--my', 0); }
});
document.addEventListener('input', e => {
  if (e.target.id !== 'spSearch') return;
  const q = e.target.value.trim().toLowerCase();
  document.querySelectorAll('#main table.t tbody tr, #main .sp-gt-r, #main .sp-notices > div, #main .sp-act > div, #main .timeline li').forEach(el => { el.hidden = !!q && !el.textContent.toLowerCase().includes(q); });
});
function attTally(form) {
  const n = v => form.querySelectorAll('.at-seg input[value=' + v + ']:checked').length;
  const el = document.getElementById('atTally');
  if (el) { el.textContent = n('PRESENT') + ' ' + trText('present') + ' · ' + n('ABSENT') + ' ' + trText('absent') + ' · ' + n('LEAVE') + ' ' + trText('leave'); el.className = 'pk-chip ' + (n('ABSENT') ? 'pk-chip-bad' : 'pk-chip-ok'); }
}
document.addEventListener('change', e => {
  if (e.target.matches && e.target.matches('.at-seg input')) attTally(e.target.closest('form'));
  const a = e.target.dataset && e.target.dataset.act;
  if (a === 'pickSite') ACT.pickSite({}, e.target);
  if (a === 'prefLang') ACT.prefLang({}, e.target).catch(fail);
  if (a === 'setStage') ACT.setStage({ id: e.target.dataset.id }, e.target).catch(fail);
  if (a === 'pickSection') location.hash = '#/' + e.target.dataset.route + '?s=' + e.target.value;
});
document.addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.matches && e.target.matches('.tk-card[data-go]')) location.hash = e.target.dataset.go; });

document.addEventListener('submit', async e => {
  const form = e.target.closest('form[data-form]'); if (!form) return;
  e.preventDefault();
  const fn = FORM[form.dataset.form]; if (!fn) return;
  const f = {}; new FormData(form).forEach((v, k) => { f[k] = v; });
  const btn = e.submitter; if (btn) btn.disabled = true;
  try { await fn(f, form.dataset, form, btn || {}); } catch (err) { fail(err); } finally { if (btn) btn.disabled = false; }
});

function fail(err) {
  if (err.status === 401) { location.reload(); return; }
  toast(err.message || 'Something went wrong.', true);
}

// =============================================================== shell, routing, sign-in
/** Doha skyline drawn at the foot of the sidebar. */
const SIDE_SKY = `<div class="side-sky" aria-hidden="true"><div class="side-ok"><i></i>System operational</div><svg viewBox="0 0 254 120" preserveAspectRatio="xMidYMax slice"><defs>
<linearGradient id="sk1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c24a6e"/><stop offset="1" stop-color="#5a1a30"/></linearGradient>
<radialGradient id="sk2" cx=".5" cy="1" r=".9"><stop offset="0" stop-color="#e7a3b6" stop-opacity=".45"/><stop offset="1" stop-color="#8f2447" stop-opacity="0"/></radialGradient></defs>
<rect width="254" height="120" fill="url(#sk2)"/>
<g fill="#5a1a30" opacity=".6"><rect x="6" y="78" width="16" height="34"/><rect x="24" y="66" width="12" height="46"/><rect x="38" y="84" width="18" height="28"/><rect x="196" y="74" width="14" height="38"/><rect x="212" y="62" width="12" height="50"/><rect x="226" y="80" width="22" height="32"/></g>
<g fill="url(#sk1)"><path d="M60 112Q66 84 63 58Q62 40 70 26Q78 40 77 58Q74 84 80 112Z"/><path d="M88 112V56Q88 42 99 38Q110 42 110 56V112Z"/><path d="M122 112L124 70Q115 58 120 40Q128 22 136 40Q141 58 133 70L135 112Z"/><rect x="144" y="52" width="14" height="60"/><rect x="160" y="68" width="14" height="44"/><path d="M176 112V60L192 54V112Z"/></g>
<rect x="69" y="12" width="1.6" height="16" fill="#e7a3b6"/><ellipse cx="128" cy="40" rx="6" ry="5" fill="#f4c27a" opacity=".9"/><circle cx="128" cy="40" r="13" fill="#f4c27a" opacity=".18"/>
<g stroke="#f4c27a" stroke-width="1.6" stroke-dasharray="2 6" opacity=".75"><path d="M67 62V108M73 62V108M94 58V108M104 58V108M148 60V108M153 60V108M165 76V108M170 76V108M181 62V108M187 62V108"/></g>
<path d="M0 112H254V120H0Z" fill="#17171a"/><path d="M96 114Q126 108 156 114" stroke="#8f2447" stroke-width="2" fill="none" opacity=".6"/></svg></div>`;
const ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/></svg>';
const SIDE_HELP = `<div class="side-help"><div class="side-help-card"><b>Need help?</b><small>Contact your school office</small><a href="#/notices">School notices →</a></div><div class="side-ok side-ok-flat"><i></i>System operational</div></div>`;
function portalTopbar(initials) {
  return html`<header class="sp-top"><span class="sp-tag">Building a brighter future together</span>
    <label class="sp-search">${sIc('search')}<input id="spSearch" type="search" placeholder="Search lessons, notices, materials" autocomplete="off"></label>
    <a class="sp-bell" href="#/notifications" aria-label="Notifications">${sIc('bell')}${S.unread ? html`<span>${S.unread}</span>` : ''}</a>
    <button type="button" class="sp-lang" data-act="setLang">${langLabel()}</button><a class="sp-user" href="#/prefs">${avatar(initials)}<span><b>${S.me.displayName}</b><small>${ROLE_LABEL[S.me.role]}</small></span></a></header>`;
}
const langLabel = () => raw(I18N.lang === 'ar' ? 'English' : 'العربية'); // shown in the other language, never translated
function shell(route, body) {
  const nav = NAV[S.me.role] || [];
  route = ({ schoolReport: S.me.role === 'LEADERSHIP' ? 'schools' : 'metrics', investigations: S.me.role === 'PRINCIPAL' ? 'performance' : 'investigations' })[route] || route;
  const initials = (S.me.displayName || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  const portal = S.me.role === 'STUDENT' || S.me.role === 'PARENT';
  if (isNewLook()) return eaaShell(route, body);
  return html`<div class="frame ea-keep">
    ${eaaSide(route)}
    <div class="content">${portal ? portalTopbar(initials) : ''}<main class="page ${portal ? 'sp' : isCoord() ? 'cx' : ''}" id="main">${body}</main></div></div>`;
}

const L = { view: 'home', mode: 'login', dev: false, test: false, schools: null };
function showLanding() { document.getElementById('app').innerHTML = signIn(null, L.dev).s; }
function afterAuth() { S.me = null; location.hash = ''; render(); }

/** Public landing page: shown before sign-in. Section links scroll in place (a #hash would trigger the app router). */
const LD_ICON = {
  users: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8',
  doc: 'M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8zM14 3v5h5M9 14l2 2 4-4', cap: 'M2 9l10-5 10 5-10 5zM6 11v5c0 1.5 2.7 3 6 3s6-1.5 6-3v-5'
};
const ldIc = (p, s, w) => html`<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="${w || 2}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${p}"/></svg>`;
const ldGo = (t, label, cls) => html`<button type="button" class="${cls || 'ld-lnk'}" data-act="scrollTo" data-target="${t}">${label}</button>`;

Object.assign(AR, {
  'Home': 'الرئيسية', 'About': 'عن حافظ', 'Portals': 'البوابات', 'About Hafez': 'عن حافظ',
  'Assalam Schools · Education Above All': 'مدارس السلام · التعليم فوق الجميع', 'Assalam Schools': 'مدارس السلام',
  'One place for your school day': 'مكان واحد ليومك الدراسي',
  'Timetable, attendance, homework, results and pick-up for parents, students and teachers, in Arabic and English.': 'الجدول والحضور والواجبات والنتائج واستلام الطلاب لأولياء الأمور والطلاب والمعلمين، بالعربية والإنجليزية.',
  'Sign in →': 'تسجيل الدخول ←', 'See what is inside': 'تعرّف على المزايا',
  'A caring community of learners': 'مجتمع متعلمين يسوده الاهتمام',
  'Hafez brings families, students and teachers together. Teachers set homework and take attendance, parents see how their children are doing, and everyone can ask questions in Arabic or English.': 'يجمع حافظ الأسر والطلاب والمعلمين. يضع المعلمون الواجبات ويسجلون الحضور، ويتابع أولياء الأمور أحوال أبنائهم، ويمكن للجميع طرح الأسئلة بالعربية أو الإنجليزية.',
  'Everything you see comes from the school\'s own records, and each person only sees what they are allowed to.': 'كل ما تراه مصدره سجلات المدرسة نفسها، ولا يرى كل شخص إلا ما يحق له.',
  'At a glance': 'لمحة سريعة', 'Two languages, one school': 'لغتان، مدرسة واحدة', 'Arabic and English are part of every screen.': 'العربية والإنجليزية جزء من كل شاشة.',
  'Grades 1 to 12': 'من الصف الأول إلى الثاني عشر', 'From the first lesson to university preparation.': 'من أول درس حتى الاستعداد للجامعة.',
  'Families in the loop': 'الأسر على اطلاع', 'Parents follow homework, marks and attendance online.': 'يتابع أولياء الأمور الواجبات والدرجات والحضور عبر الإنترنت.',
  'A portal for every role': 'بوابة لكل دور', 'Parents': 'أولياء الأمور',
  'Follow every child\'s marks, attendance, homework and timetable, and pick them up early with a few taps.': 'تابع درجات كل طفل وحضوره وواجباته وجدوله، واستلمه مبكراً ببضع نقرات.',
  'See your homework, quizzes, timetable, points and levels in one place.': 'شاهد واجباتك واختباراتك وجدولك ونقاطك ومستوياتك في مكان واحد.',
  'Take attendance, set homework and quizzes, publish marks and talk to families.': 'سجّل الحضور، وضع الواجبات والاختبارات، وانشر الدرجات وتواصل مع الأسر.',
  'What is inside': 'ما بداخل حافظ', 'Everything for the school day': 'كل ما يحتاجه اليوم الدراسي',
  'Homework and quizzes': 'الواجبات والاختبارات', 'Set, hand in and mark work online.': 'ضع الأعمال وسلّمها وصحّحها عبر الإنترنت.',
  'Taken in class, seen by families the same day.': 'يُسجَّل في الصف وتراه الأسر في اليوم نفسه.',
  'Every lesson, every day of the week.': 'كل حصة في كل يوم من الأسبوع.',
  'Marks and points': 'الدرجات والنقاط', 'Results, levels and progress over time.': 'النتائج والمستويات والتقدم عبر الوقت.',
  'Pick-up': 'الاستلام', 'Parents take responsibility for early pick-up.': 'يتحمل أولياء الأمور مسؤولية الاستلام المبكر.',
  'A helper that answers questions about school.': 'مساعد يجيب عن أسئلة المدرسة.',
  'Get started': 'ابدأ الآن', 'Sign in to Hafez': 'سجّل الدخول إلى حافظ', 'Use your school account, or the e-mail and password you were given.': 'استخدم حساب المدرسة، أو البريد الإلكتروني وكلمة المرور التي زوّدتك بها المدرسة.',
  'I have an invitation code': 'لدي رمز دعوة', 'Getting access': 'كيف تحصل على الدخول',
  'Sign in with your school account': 'سجّل الدخول بحساب المدرسة', 'Parents and students': 'أولياء الأمور والطلاب', 'Use your e-mail and password': 'استخدم بريدك الإلكتروني وكلمة المرور',
  'New here?': 'جديد هنا؟', 'Activate with the invitation code from your school': 'فعّل حسابك برمز الدعوة من مدرستك', 'Questions': 'أسئلة', 'Ask your school office': 'اسأل مكتب المدرسة',
  'Assalam Schools · Qatar': 'مدارس السلام · قطر',
  '‹ Back to website': 'الرجوع إلى الموقع ›', '‹ Back to sign in': 'الرجوع إلى تسجيل الدخول ›',
  'One place for your school day: timetable, attendance, results and pick-up.': 'مكان واحد ليومك الدراسي: الجدول والحضور والنتائج والاستلام.',
  'Sign in': 'تسجيل الدخول', 'Welcome back. Choose how you sign in.': 'أهلاً بعودتك. اختر طريقة تسجيل الدخول.',
  'Teachers and school staff sign in with their school account.': 'يسجّل المعلمون وموظفو المدرسة الدخول بحساب المدرسة.',
  'Continue with school account': 'المتابعة بحساب المدرسة', 'Parent or student': 'ولي أمر أو طالب',
  'E-mail': 'البريد الإلكتروني', 'Enter your password': 'أدخل كلمة المرور', 'At least 8 characters': '8 أحرف على الأقل',
  'Activate your student account': 'فعّل حساب الطالب', 'Create your parent account': 'أنشئ حساب ولي الأمر'
});
const pbIc = (p, s) => html`<svg width="${s || 18}" height="${s || 18}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${p}"/></svg>`;
const PB = {
  book: 'M4 5a2 2 0 012-2h13v16H6a2 2 0 00-2 2zM4 19a2 2 0 012-2h13M8 7h7',
  globe: 'M12 21a9 9 0 100-18 9 9 0 000 18zM3 12h18M12 3a14 14 0 010 18M12 3a14 14 0 000 18',
  cap: LD_ICON.cap, users: LD_ICON.users, doc: LD_ICON.doc,
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  cal: 'M5 4h14a1 1 0 011 1v15a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1zM4 9h16M8 2v4M16 2v4',
  chart: 'M4 20V10M10 20V4M16 20v-8M22 20H2',
  car: 'M5 17h14M6 17l1.5-6h9L18 17M7 17v2M17 17v2M9 14h.01M15 14h.01',
  chat: 'M21 12a8 8 0 01-11.6 7.1L4 20.5l1.5-4.6A8 8 0 1121 12z',
  key: 'M15 7a4 4 0 11-3.9 4.9L3 20v-3l3-3 2 .5.5-2L11 11a4 4 0 014-4zM16 9h.01',
  mail: 'M4 6h16a1 1 0 011 1v10a1 1 0 01-1 1H4a1 1 0 01-1-1V7a1 1 0 011-1zM3 7l9 6 9-6',
  home: 'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10'
};
const pbLogo = cls => html`<span class="${cls || 'pb-brand'}"><img src="app/eaa-logo.png" alt=""><span><b>Hafez</b><small lang="ar" dir="rtl">حافظ</small></span></span>`;
const pbGo = (t, label, cls) => html`<button type="button" class="${cls || 'pb-link'}" data-act="scrollTo" data-target="${t}">${label}</button>`;
const pbSignIn = (label, cls, view, mode) => html`<button type="button" class="${cls}" data-act="landing" data-view="${view || 'signin'}" data-mode="${mode || 'login'}">${label}</button>`;
const pbGlobe = raw('<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="' + PB.globe + '"/></svg>');

function landingPage() {
  const glance = [[PB.globe, 'Two languages, one school', 'Arabic and English are part of every screen.'], [PB.cap, 'Grades 1 to 12', 'From the first lesson to university preparation.'], [PB.users, 'Families in the loop', 'Parents follow homework, marks and attendance online.']];
  const roles = [['Parents', 'Follow every child\'s marks, attendance, homework and timetable, and pick them up early with a few taps.', 'portal-a'], ['Students', 'See your homework, quizzes, timetable, points and levels in one place.', 'portal-b'], ['Teachers', 'Take attendance, set homework and quizzes, publish marks and talk to families.', 'portal-c']];
  const inside = [[PB.book, 'Homework and quizzes', 'Set, hand in and mark work online.'], [PB.cal, 'Attendance', 'Taken in class, seen by families the same day.'], [PB.clock, 'Timetable', 'Every lesson, every day of the week.'], [PB.chart, 'Marks and points', 'Results, levels and progress over time.'], [PB.car, 'Pick-up', 'Parents take responsibility for early pick-up.'], [PB.chat, 'Ask Hafez', 'A helper that answers questions about school.']];
  return html`<div class="pb" id="pb-top">
  <header class="pb-nav"><button type="button" class="pb-brand-btn" data-act="scrollTo" data-target="pb-top">${pbLogo()}</button>
    <nav class="pb-links">${pbGo('pb-top', 'Home')}${pbGo('pb-about', 'About')}${pbGo('pb-portals', 'Portals')}${pbGo('pb-inside', 'Features')}${pbGo('pb-start', 'Contact')}</nav>
    <button type="button" class="pb-lang" data-act="setLang">${pbGlobe} ${langLabel()}</button>${pbSignIn('Sign in', 'pb-btn pb-btn-sm')}</header>
  <main class="pb-main">
  <section class="pb-hero"><img src="app/assalam-school.jpg" alt=""><div class="pb-hero-in"><span class="pb-badge">Assalam Schools · Education Above All</span><h1>One place for your school day</h1>
    <p>Timetable, attendance, homework, results and pick-up for parents, students and teachers, in Arabic and English.</p>
    <div class="pb-cta">${pbSignIn('Sign in →', 'pb-btn pb-btn-light')}${pbGo('pb-inside', 'See what is inside', 'pb-btn pb-btn-ghost')}</div></div></section>

  <section class="pb-sec pb-about" id="pb-about"><div><small class="pb-eyebrow">About Hafez</small><h2>A caring community of learners</h2>
    <p>Hafez brings families, students and teachers together. Teachers set homework and take attendance, parents see how their children are doing, and everyone can ask questions in Arabic or English.</p>
    <p>Everything you see comes from the school's own records, and each person only sees what they are allowed to.</p></div>
    <aside class="pb-glance"><small>At a glance</small>${glance.map(([p, t, d]) => html`<div><span>${pbIc(p, 18)}</span><div><b>${t}</b><div>${d}</div></div></div>`)}</aside></section>

  <section class="pb-sec" id="pb-portals"><small class="pb-eyebrow">Portals</small><h2>A portal for every role</h2>
    <div class="pb-cards">${roles.map(([t, d, c]) => html`<article class="pb-card"><div class="pb-card-img ${c}"></div><div class="pb-card-b"><b>${t}</b><p>${d}</p>${pbSignIn('Sign in →', 'pb-more')}</div></article>`)}</div></section>

  <section class="pb-sec" id="pb-inside"><small class="pb-eyebrow">What is inside</small><h2>Everything for the school day</h2>
    <div class="pb-notes">${inside.map(([p, t, d]) => html`<div class="pb-note"><span>${pbIc(p, 22)}</span><div><b>${t}</b><div>${d}</div></div></div>`)}</div></section>

  <section class="pb-sec"><div class="pb-band"><div><small>Get started</small><h2>Sign in to Hafez</h2><p>Use your school account, or the e-mail and password you were given.</p></div>
    <div class="pb-cta">${pbSignIn('Sign in →', 'pb-btn pb-btn-light')}${pbSignIn('I have an invitation code', 'pb-btn pb-btn-ghost', 'parent', 'invite')}</div></div></section>

  <section class="pb-sec" id="pb-start"><small class="pb-eyebrow">Contact</small><h2>Getting access</h2>
    <div class="pb-contact"><div class="pb-info">
      <div><span>${pbIc(PB.key, 18)}</span><div><small>Staff</small><b>Sign in with your school account</b></div></div>
      <div><span>${pbIc(PB.mail, 18)}</span><div><small>Parents and students</small><b>Use your e-mail and password</b></div></div>
      <div><span>${pbIc(PB.doc, 18)}</span><div><small>New here?</small><b>Activate with the invitation code from your school</b></div></div>
      <div><span>${pbIc(PB.home, 18)}</span><div><small>Questions</small><b>Ask your school office</b></div></div></div>
      <div class="pb-map"><img src="app/heritage-school.png" alt=""><span>Assalam Schools · Qatar</span></div></div>
    ${L.dev ? html`<p class="pb-dev">Test mode, no e-mail needed: <a href="/dev/login/student?next=/">student</a> · <a href="/dev/login/parent?next=/">parent</a> · <a href="/dev/login">all test roles</a></p>` : ''}</section>
  </main>
  <footer class="pb-foot"><div>${pbLogo('pb-brand pb-brand-w')}<p>Assalam Schools · Education Above All</p></div>
    <div><b>Hafez</b>${pbGo('pb-about', 'About')}${pbGo('pb-inside', 'Features')}</div><div><b>Portals</b>${pbSignIn('Sign in', 'pb-link')}${pbGo('pb-start', 'Contact')}</div></footer></div>`;
}

/** The split sign-in screen: the school on the left, one white card on the right. */
function authShell(card) {
  return html`<div class="au"><aside class="au-art"><img src="app/assalam-school.jpg" alt="">${pbLogo('pb-brand pb-brand-w')}
      <div class="au-cap"><span class="pb-badge">Assalam Schools</span><h2>One place for your school day: timetable, attendance, results and pick-up.</h2></div></aside>
    <section class="au-side"><div class="au-top"><button type="button" class="au-back" data-act="landing" data-view="home">‹ Back to website</button><button type="button" class="pb-lang" data-act="setLang">${pbGlobe} ${langLabel()}</button></div>
      <div class="au-card">${card}</div></section></div>`;
}

function signIn(message, dev) {
  if (dev !== undefined) L.dev = !!dev;
  if (message) {
    return authShell(html`<h1>Your account is not set up yet</h1><p class="au-sub">${message}</p><button class="btn btn-primary au-wide" data-act="signout">Sign out and try another account</button>`);
  }
  if (L.view === 'home') return landingPage();
  const back = html`<p class="au-note"><button type="button" class="pb-more" data-act="landing" data-view="signin" data-mode="login">‹ Back to sign in</button></p>`;
  if (L.view === 'testname') {
    const off = L.testPeople === null;
    const order = ['STUDENT', 'PARENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT', 'ATTENDANCE_OFFICER', 'SCHOOL_IT', 'LEADERSHIP', 'EAA_ADMIN'];
    const people = (L.testPeople || []).slice().sort((a, b) => (order.indexOf(a.role) - order.indexOf(b.role)) || a.name.localeCompare(b.name));
    const who = people.map(x => html`<button type="button" class="ts-person" data-act="testPick" data-id="${x.id}"><span class="ts-av">${initialsOf(x.name)}</span><span class="ts-txt"><b>${x.name}</b><small>${ROLE_LABEL[x.role] || x.role}</small></span><i aria-hidden="true">→</i></button>`);
    return authShell(html`<h1>Test sign-in</h1>
      ${off ? html`<p class="au-sub" style="color:#8E1B1B">Test sign-in is switched off. Start the app with SIJILL_SIGNIN_TEST_MODE=true to use it.</p>` : html`<p class="au-sub">For testing only. Press a person to sign in with their role. There is no password.</p>`}
      ${who.length ? html`<div class="ts-grid">${who}</div>` : ''}
      <details class="ts-type" ${who.length ? '' : raw('open')}><summary>Or type a name</summary><form data-form="testSignin">${field('Name', html`<input class="input" name="name" autocomplete="off" required>`)}<button class="btn btn-primary au-wide">Sign in</button></form></details>${back}`);
  }
  const pw = reg => field('Password', html`<input class="input" type="password" name="password" minlength="${reg ? 8 : 1}" autocomplete="${reg ? 'new-password' : 'current-password'}" placeholder="${reg ? 'At least 8 characters' : 'Enter your password'}" required>`);
  const code = field('Invitation code', html`<input class="input mono" name="code" placeholder="XXXX-XXXX-XXXX" autocomplete="off" required>`);
  if (L.mode === 'invite') {
    const student = L.view === 'student';
    return authShell(html`<h1>${student ? 'Activate your student account' : 'Create your parent account'}</h1>
      <p class="au-sub">${student ? 'Your school gave you a one-time code. Choose a password of at least 8 characters.' : 'Your school gave you a one-time invitation code. It links your children to your own account.'}</p>
      <div class="au-seg"><button type="button" class="${student ? '' : 'on'}" data-act="landing" data-view="parent" data-mode="invite">Parent</button><button type="button" class="${student ? 'on' : ''}" data-act="landing" data-view="student" data-mode="invite">Student</button></div>
      ${student ? html`<form data-form="redeemStudent">${code}${pw(true)}<button class="btn btn-primary au-wide">Activate my account</button></form>`
        : html`<form data-form="redeemParent">${code}${field('Your full name', html`<input class="input" name="name" autocomplete="name" required>`)}${field('Your e-mail', html`<input class="input" type="email" name="email" autocomplete="email" required>`)}${pw(true)}<button class="btn btn-primary au-wide">Create my account</button></form>`}${back}`);
  }
  return authShell(html`<h1>Sign in</h1><p class="au-sub">Welcome back. Choose how you sign in.</p>
    <div class="au-staff"><b>${pbIc(PB.home, 16)} Staff</b><p>Teachers and school staff sign in with their school account.</p>
      <a class="au-google" href="/oauth2/authorization/google">${pbIc(PB.key, 16)} Continue with school account</a></div>
    <div class="au-or"><span>Parent or student</span></div>
    <form data-form="login">${field('E-mail', html`<input class="input" type="email" name="email" autocomplete="email" placeholder="name@example.com" required>`)}${pw(false)}<button class="btn btn-primary au-wide">Sign in</button></form>
    <p class="au-note">Got an invitation? Activate your account as a <button type="button" class="pb-more" data-act="landing" data-view="parent" data-mode="invite">parent</button> or a <button type="button" class="pb-more" data-act="landing" data-view="student" data-mode="invite">student</button>.</p>
    ${L.test ? html`<p class="au-note"><button type="button" class="pb-more" data-act="landing" data-view="testname" data-mode="login">Sign in with a name (testing)</button></p>` : ''}
    ${L.dev ? html`<p class="au-note">Test mode: <a href="/dev/login/student?next=/">student</a> · <a href="/dev/login/parent?next=/">parent</a> · <a href="/dev/login">all roles</a></p>` : ''}`);
}

function problem(e) {
  const detail = e.status ? `The server answered ${e.status}: ${e.message}` : `The browser could not reach the server (${e.message}). If the app was restarting, try again.`;
  return html`<div class="center-screen"><div class="center-card"><div class="card-logo"><img src="app/eaa-logo.png" alt="Assalam School · Education Above All"><span class="brand-name" style="color:var(--ink)">Hafez</span></div>
    <h1 style="margin:0 0 8px;font-size:22px">Could not load your account</h1><p class="muted">${detail}</p>
    ${e.body && e.body.correlationId ? html`<p class="muted mono">Reference: ${e.body.correlationId}</p>` : ''}
    <div class="row"><button class="btn btn-primary" onclick="location.reload()">Try again</button><button class="btn" data-act="signout">Sign out</button></div></div></div>`;
}

const CHAT_ROLES = ['STUDENT', 'PARENT', 'TEACHER', 'COORDINATOR'];
async function refreshChat() {
  if (!S.me || !CHAT_ROLES.includes(S.me.role)) return;
  try {
    const r = await get('/api/chat/unread');
    if (r.unread !== S.chatUnread) { S.chatUnread = r.unread; const c = document.querySelector('.nav-item[href="#/chat"]'); if (c) { const old = c.querySelector('.count'); if (old) old.remove(); if (S.chatUnread) c.insertAdjacentHTML('beforeend', `<span class="count">${S.chatUnread}</span>`); } }
  } catch { }
}
async function refreshBell() { refreshChat(); try { const r = await get('/api/notifications/unread-count'); if (r.unread !== S.unread) { S.unread = r.unread; const c = document.querySelector('.nav-item[href="#/notifications"]'); if (c) { const old = c.querySelector('.count'); if (old) old.remove(); if (S.unread) c.insertAdjacentHTML('beforeend', `<span class="count">${S.unread}</span>`); } } } catch { } }

const home = () => ({ TEACHER: 'today', STUDENT: 'mydash', PARENT: 'children', COORDINATOR: 'dashboard', PRINCIPAL: 'school', PRINCIPAL_ASSISTANT: 'metrics', SCHOOL_IT: 'itdash', LEADERSHIP: 'overview', EAA_ADMIN: 'netoverview', ATTENDANCE_OFFICER: 'attendance' }[S.me.role]);

async function render() {
  const app = document.getElementById('app');
  if (!S.me && typeof AskHafez !== 'undefined') AskHafez.sync();
  if (!S.me) {
    try { S.me = await get('/api/me'); if (!I18N.stored && S.me.language === 'AR' && I18N.lang !== 'ar') setLang('ar', false); }
    catch (e) {
      // 401 with a message = signed in to Google but not set up in Hafez; plain 401 = not signed in;
      // anything else is a real problem, so say what it was instead of pretending to be a sign-in page
      const opts = e.status === 401 && !e.body ? await get('/api/auth/options').catch(() => ({ dev: false })) : {};
      L.test = !!opts.testSignin;
      app.innerHTML = (e.status === 401 && !e.body ? signIn(null, opts.dev) : e.status === 401 ? signIn(e.message) : problem(e)).s;
      return;
    }
  }
  const [hash, query] = (location.hash || '').replace(/^#\/?/, '').split('?');
  const [route, arg] = (hash || home()).split('/');
  const q = new URLSearchParams(query || '');
  S.route = route;
  const studentRoutes = new Set([...(NAV.STUDENT || []).map(x => x[0]), 'prefs']);
  const screen = SCREENS[route];
  if (S.me.role === 'PARENT' && !new Set([...(NAV.PARENT || []).map(x => x[0]), 'prefs']).has(route)) { location.hash = '#/' + home(); return; }
  if (S.me.role === 'STUDENT' && !studentRoutes.has(route)) { location.hash = '#/' + home(); return; }
  if (!screen) { location.hash = '#/' + home(); return; }
  if ((HIDDEN_ROUTES[S.me.role] || []).includes(route)) { location.hash = '#/' + home(); return; }   // screens taken off this role's sidebar stay closed when typed in by hand
  await loadSites(); if (S.unread === 0) refreshBell();
  const seq = S.renderSeq = (S.renderSeq || 0) + 1;   // a slower, earlier screen must not overwrite the one asked for last
  document.getElementById('app').innerHTML = shell(route, html`<p class="muted">Loading…</p>`).s;
  let body;
  try { body = await screen(arg, q); }
  catch (e) { if (e.status === 401) { S.me = null; return render(); } body = html`<div class="err">${e.message}</div>`; }
  if (seq !== S.renderSeq) return;
  const flash = S.flash; S.flash = null;
  const banner = flash ? html`<div class="${flash.ok ? 'ok' : 'err'}" style="margin-bottom:16px">${flash.ok || flash.err}</div>` : '';
  document.getElementById('main').innerHTML = html`${banner}${body}`.s;
  S.kpiFlash = false;
  window.scrollTo(0, 0);
  afterRender();
  if (typeof eaEnhance !== 'undefined') eaEnhance();
  if (typeof AskHafez !== 'undefined') AskHafez.sync();
}

window.addEventListener('hashchange', render);
setInterval(() => { if (S.me) refreshBell(); }, 60000);
fetch('/api/auth/brand').then(r => r.json()).then(b => { BRAND.en = b.en; BRAND.ar = b.ar; document.title = b.en; }).catch(() => {}).finally(render);
