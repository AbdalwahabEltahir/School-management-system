/* Sijill web app: one page, plain JavaScript, talks to the real /api endpoints.
   What each person may see or do is decided by the server; this file only chooses which screens to offer. */
'use strict';

// =============================================================== helpers
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
class Raw { constructor(s) { this.s = s; } }
const raw = s => new Raw(s);
const val = v => v instanceof Raw ? v.s : Array.isArray(v) ? v.map(val).join('') : esc(v);
const html = (strs, ...vals) => raw(strs.reduce((a, s, i) => a + s + (i < vals.length ? val(vals[i]) : ''), ''));
const fmt = d => d ? new Date(d).toLocaleString() : '—';
const fmtDate = d => d ? new Date(d).toLocaleDateString() : '—';
const cookie = n => (document.cookie.split('; ').find(c => c.startsWith(n + '=')) || '').split('=')[1];
const iso = d => d.toISOString().slice(0, 10);
const monday = () => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return iso(d); };
const title = s => String(s || '').toLowerCase().replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());

function toast(msg, bad) {
  const t = document.getElementById('toast');
  t.textContent = msg; t.hidden = false; t.style.background = bad ? '#8E1B1B' : '';
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
const ROLE_LABEL = { TEACHER: 'Teacher', COORDINATOR: 'Coordinator', PRINCIPAL: 'Principal', SCHOOL_IT: 'School IT', LEADERSHIP: 'Leadership', STUDENT: 'Student' };
const OPEN = ['OWNER_NOTIFIED', 'REMINDER_SENT', 'NEEDS_FOLLOW_UP', 'NEEDS_REVIEW', 'OWNER_DISAGREES', 'OWNER_ASKED_FOR_HELP'];
const STATUS = {
  OWNER_NOTIFIED: ['Owner notified', 'b-internal'], REMINDER_SENT: ['Reminder sent', 'b-watch'], NEEDS_FOLLOW_UP: ['Needs follow-up', 'b-bad'],
  NEEDS_REVIEW: ['Needs review', 'b-watch'], OWNER_DISAGREES: ['Owner disagrees', 'b-watch'], OWNER_ASKED_FOR_HELP: ['Asked for help', 'b-watch'],
  FIXED: ['Fixed', 'b-good'], ALLOWED_EXCEPTION: ['Allowed exception', 'b-neutral'], RULE_PAUSED: ['Rule paused', 'b-neutral'], CLOSED_RULE_REMOVED: ['Rule removed', 'b-neutral']
};
const CHECK_TYPES = ['LOCATION', 'PUBLIC_LINK', 'OUTSIDE_SHARING', 'INTERNAL_ACCESS', 'DUPLICATES', 'SHARING_BEFORE_DATE', 'OWNER_LEAVING', 'GENERIC_OWNER', 'DELETED_OR_TRASHED'];
const SENS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];
const ROLES = ['TEACHER', 'COORDINATOR', 'PRINCIPAL', 'SCHOOL_IT', 'LEADERSHIP', 'STUDENT'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday'];

const NAV = {
  TEACHER: [['today', 'Today'], ['attendance', 'Attendance'], ['marks', 'Marks'], ['materials', 'Study materials'], ['mine', 'My issues'], ['files', 'My files'], ['planning', 'Lesson plans'], ['notices', 'Notices'], ['notifications', 'Notifications']],
  STUDENT: [['mydash', 'My day'], ['results', 'My results'], ['mymaterials', 'Study materials'], ['notices', 'Notices'], ['notifications', 'Notifications']],
  COORDINATOR: [['academic', 'Academic setup'], ['followup', 'Follow-up'], ['issues', 'All issues'], ['review', 'Review queue'], ['files', 'Files'], ['planning', 'Plan review'], ['people', 'People'], ['restores', 'Restores'], ['notifications', 'Notifications']],
  PRINCIPAL: [['school', 'My school'], ['issues', 'Issues'], ['files', 'Files'], ['planning', 'Lesson plans'], ['restores', 'Restores'], ['notifications', 'Notifications']],
  SCHOOL_IT: [['assessments', 'Assessments & results'], ['noticesAdmin', 'Notices'], ['rules', 'Rules'], ['people', 'People'], ['sites', 'Schools & settings'], ['connection', 'Connection'], ['restores', 'Restores'], ['incidents', 'Incidents'], ['activity', 'Activity log'], ['notifications', 'Notifications']],
  LEADERSHIP: [['overview', 'Overview'], ['incidents', 'Incidents'], ['rules', 'Rules'], ['sites', 'Schools & settings'], ['connection', 'Connection'], ['activity', 'Activity log'], ['notifications', 'Notifications']]
};

const lazy = (key, path, map) => async () => {
  if (S[key] === null) { try { const l = await get(path); S[key] = map ? Object.fromEntries(l.map(map)) : l; } catch { S[key] = map ? {} : []; } }
  return S[key];
};
const loadFiles = () => {
  if (['SCHOOL_IT', 'LEADERSHIP'].includes(S.me.role)) return Promise.resolve(S.files = S.files || {}); // counts only for these roles
  return lazy('files', S.me.role === 'TEACHER' ? '/api/files/mine' : '/api/files', f => [f.id, f])();
};
const loadRules = lazy('rules', '/api/rules', r => [r.id, r]);
const loadPeople = () => ['SCHOOL_IT', 'COORDINATOR'].includes(S.me.role) ? lazy('people', '/api/people', p => [p.id, p])() : Promise.resolve(S.people = S.people || {});
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
const siteName = id => (S.sites && S.sites[id] && S.sites[id].name) || '';
/** Issue text in the person's language setting: English, Arabic, or both. */
const say = (en, ar) => {
  const l = S.me && S.me.language;
  if (l === 'AR' && ar) return html`<span lang="ar" dir="rtl">${ar}</span>`;
  if (l === 'BOTH' && ar) return html`${en}<br><span lang="ar" dir="rtl" class="muted">${ar}</span>`;
  return html`${en}`;
};

// =============================================================== small components
const badge = (cls, text) => html`<span class="badge ${cls}">${text}</span>`;
const statusBadge = s => { const [t, c] = STATUS[s] || [title(s), 'b-neutral']; return badge(c, t); };
const sevBadge = s => badge(s === 'URGENT' ? 'b-urgent' : 'b-normal', title(s));
const sensBadge = s => s ? badge('b-' + s.toLowerCase(), title(s)) : badge('b-neutral', 'Not classified');
const empty = t => html`<div class="empty">${t}</div>`;
const card = (heading, sub, body, extra) => html`<section class="card"><div class="card-head"><div><h2>${heading}</h2>${sub ? html`<small>${sub}</small>` : ''}</div>${extra || ''}</div>${body}</section>`;
const page = (heading, sub, body, actions) => html`<div class="page-top"><div class="page-head"><h1 class="page-title">${heading}</h1>${sub ? html`<p class="page-sub">${sub}</p>` : ''}</div><div class="row">${actions || ''}</div></div><div class="stack-gap">${body}</div>`;
const kpi = (label, value, tone) => html`<div class="card kpi"><span class="kpi-ic t-${tone || 'brand'}"></span><span class="kpi-label">${label}</span><span class="kpi-value">${value}</span></div>`;
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
    html`<a class="btn" href="/api/exports/issues.csv">Download CSV</a>`);
};

SCREENS.followup = async () => {
  const [issues, counts] = await Promise.all([get('/api/follow-up'), get('/api/follow-up/counts'), loadFiles()]);
  await nameMissing(issues, i => i.fileId);
  return page('Follow-up', 'Issues waiting for you: owners who disagree, asked for help, or have not acted.',
    html`<div class="kpi-grid">${kpi('Open', counts.open)}${kpi('Urgent', counts.urgent, 'brand')}${kpi('Need follow-up', counts.needFollowUp, 'amber')}${kpi('Fixed', counts.fixed, 'green')}</div>
    ${card('Needs you', null, issues.length ? issueTable(issues) : empty('Nothing needs you right now.'))}`,
    html`<button class="btn" data-act="markAllDone" data-confirm="Mark every follow-up as done?">Mark all done</button>`);
};

SCREENS.issue = async id => {
  const issue = await get('/api/issues/' + id);
  const [history, details] = await Promise.all([
    get(`/api/issues/${id}/history`).catch(() => []),
    get('/api/files/' + issue.fileId).catch(() => null), loadRules(), loadPeople()]);
  const rule = S.rules[issue.ruleId];
  const steps = rule && rule.effective ? (S.me.language === 'AR' ? rule.effective.fixStepsAr : rule.effective.fixStepsEn) || rule.effective.fixStepsEn : '';
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
    ${card('What is wrong', rule && rule.effective ? 'Rule ' + rule.code + ': ' + rule.effective.name : null, html`<p>${say(issue.detailEn, issue.detailAr)}</p>${rule && rule.effective ? html`<p class="muted">${rule.effective.whyItMatters}</p>` : ''}${issue.ownerNote ? html`<div class="callout callout-warn">Owner's note: ${issue.ownerNote}</div>` : ''}`)}
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
  return page(mine ? 'My files' : 'Files', mine ? 'Files you own, with what Sijill understood about each.' : 'Files you may see.',
    card('Files', files.length + ' file' + (files.length === 1 ? '' : 's'), table(['Name', 'Folder', 'Drive', 'Sensitivity', 'Last checked'],
      files.map(f => ({ go: '#/file/' + f.id, cells: [f.name + (f.trashed ? ' (deleted)' : ''), f.folderPath || '', title(f.driveType), sensBadge(f.sensitivity), fmt(f.lastCheckedAt)] })))));
};

SCREENS.file = async id => {
  const d = await get('/api/files/' + id);
  const [types] = await Promise.all([loadDocTypes(), loadFiles(), loadRules()]);
  const f = d.file, p = d.profile;
  const classify = S.me.role === 'COORDINATOR' ? card('Tell Sijill what this is', 'Your answer is saved and the file is checked again.', html`<form data-form="classify" data-id="${id}">
    ${field('Document type', html`<select name="docType">${(types || []).map(t => html`<option ${p && p.docType === t.name ? raw('selected') : ''}>${t.name}</option>`)}</select>`)}
    ${field('Sensitivity', html`<select name="sensitivity">${options(SENS, p ? p.sensitivity : f.sensitivity)}</select>`)}
    <div class="field"><label><input type="checkbox" name="hasStudentData" ${p && p.hasStudentData ? raw('checked') : ''}> Contains student data</label><label><input type="checkbox" name="hasHealthData" ${p && p.hasHealthData ? raw('checked') : ''}> Contains health data</label></div>
    <button class="btn btn-primary">Save</button></form>`) : '';
  return page(f.name, (f.folderPath || '') + ' · ' + title(f.driveType) + (f.trashed ? ' · deleted' : ''),
    html`<div class="row">${sensBadge(f.sensitivity)} <span class="muted">Last checked ${fmt(f.lastCheckedAt)}</span></div>
    ${card('What Sijill understood', null, p ? html`<div class="row"><b>${p.docType}</b> ${sensBadge(p.sensitivity)} <span class="muted">${Math.round(p.confidence * 100)}% sure</span> ${p.needsReview ? badge('b-watch', 'Needs review') : ''}</div>
      <ul>${p.evidence.map(e => html`<li>${e}</li>`)}</ul>` : empty('Not classified yet.'))}
    ${classify}
    ${card('Who can open it', null, table(['Who', 'Type', 'Access', 'Outside'], d.permissions.map(x => ({ cells: [x.grantee, title(x.targetType), title(x.accessLevel), x.external ? badge('b-bad', 'Outside') : 'No'] }))))}
    ${card('Issues on this file', null, d.issues.length ? issueTable(d.issues) : empty('No issues.'))}
    ${card('History', null, d.history.length ? table(['When', 'Drive', 'Folder', 'Permissions'], d.history.map(h => ({ cells: [fmt(h.at), title(h.driveType), h.folderPath || '', h.permissionCount] }))) : empty('No snapshots yet.'))}`,
    html`<a class="btn" href="javascript:history.back()">Back</a>`);
};

SCREENS.review = async () => {
  const [items] = await Promise.all([get('/api/files/review-queue'), loadFiles()]);
  // files whose subject is not known yet are not in the browse list, so read their names directly
  await nameMissing(items, i => i.fileId);
  return page('Review queue', 'Files Sijill could not classify with confidence. Your answer teaches the checker.',
    card('Needs a person', null, table(['File', 'Best guess', 'Sensitivity', 'Confidence', 'Evidence'], items.map(i => ({ go: '#/file/' + i.fileId, cells: [fileName(i.fileId), i.docType || 'Unsure', sensBadge(i.sensitivity), Math.round(i.confidence * 100) + '%', (i.evidence || []).join('; ')] })))));
};

// ---- notifications
SCREENS.notifications = async () => {
  const list = await get('/api/notifications');
  return page('Notifications', null,
    card('Messages', null, list.length ? html`<ul class="timeline">${list.map(n => html`<li ${n.issueId ? raw(`data-act="openNote" data-id="${n.id}" data-issue="${n.issueId}" style="cursor:pointer"`) : ''}><b>${n.title}</b> ${n.read ? '' : badge('b-bad', 'New')} <span class="muted">${fmt(n.createdAt)}</span><div>${n.body}</div></li>`)}</ul>` : empty('No notifications.')),
    html`<button class="btn" data-act="readAll">Mark all as read</button>`);
};

// ---- lesson plans
SCREENS.planning = async () => {
  const [subs, subjects] = await Promise.all([get('/api/planning'), loadSubjects(), loadPeople()]);
  const role = S.me.role;
  const subjName = id => (subjects.find(s => s.id === id) || {}).name || '';
  const stBadge = s => badge(s === 'APPROVED' ? 'b-good' : s === 'RETURNED' ? 'b-bad' : 'b-watch', title(s));
  const rows = subs.map(s => ({
    cells: [personName(s.teacherId) || 'Teacher', subjName(s.subjectId), s.grade, fmtDate(s.weekStart), title(s.planType), html`<a href="${s.driveLink}" target="_blank" rel="noopener noreferrer">Open</a>`, stBadge(s.status),
      role === 'COORDINATOR' && s.status === 'SUBMITTED' ? html`<form data-form="review" data-id="${s.id}" class="row"><input class="input" name="reason" placeholder="Reason if returning"><button class="btn btn-sm" name="decision" value="APPROVED">Approve</button><button class="btn btn-sm btn-danger" name="decision" value="RETURNED">Return</button></form>` : '']
  }));
  const submit = role === 'TEACHER' ? card('Submit this week\'s plan', 'Submitting again for the same week sends a returned plan back for review.', html`<form data-form="submitPlan">
    ${field('Subject', html`<select name="subjectId">${subjects.map(s => html`<option value="${s.id}">${s.name}</option>`)}</select>`)}
    ${field('Grade', html`<input name="grade" required placeholder="e.g. 7B">`)}
    ${field('Week starting (Monday)', html`<input type="date" name="weekStart" value="${monday()}" required>`)}
    ${field('Plan type', html`<input name="planType" required placeholder="e.g. WEEKLY">`)}
    ${field('Link to the plan in Drive', html`<input type="url" name="driveLink" required placeholder="https://drive.google.com/…">`)}
    <button class="btn btn-primary">Submit</button></form>`) : '';
  const totals = role === 'PRINCIPAL' ? card('Week totals', null, html`<form data-form="totals" class="row">${field('Week starting', html`<input type="date" name="week" value="${monday()}">`)}<button class="btn">Show</button></form><div id="totals"></div>`) : '';
  return page('Lesson plans', role === 'TEACHER' ? 'Your weekly plans and their review status.' : 'Plans submitted for review.',
    html`${submit}${card('Submissions', null, table(['Teacher', 'Subject', 'Grade', 'Week', 'Type', 'Plan', 'Status', ''], rows))}${totals}`);
};

// ---- dashboards
SCREENS.overview = async () => {
  const [o, incidents, urgentIssues] = await Promise.all([get('/api/dashboard/overview'), get('/api/dashboard/incidents').catch(() => []), get('/api/dashboard/urgent-issues').catch(() => [])]);
  const sites = Object.values(await loadSites());
  const siteOf = id => (S.sites[id] || {}).name || '';
  const rate = r => badge(r === 'GOOD' ? 'b-good' : r === 'WATCH' ? 'b-watch' : 'b-bad', title(r));
  const urgentNow = incidents.filter(i => !i.resolvedAt);
  const waiting = sites.filter(s => s.active && !s.firstCheckAt && true);
  const unscored = sites.filter(s => s.active && !s.scored);

  const tile = (label, value, tone, note, go) => html`<${go ? raw('a') : raw('div')} class="card kpi" ${go ? raw(`href="${esc(go)}" style="text-decoration:none;color:inherit"`) : ''}><span class="kpi-ic t-${tone}"></span><span class="kpi-label">${label}</span><span class="kpi-value">${value}</span>${note ? html`<span class="kpi-foot"><b>${note}</b></span>` : ''}</${go ? raw('a') : raw('div')}>`;

  const firstCheck = waiting.length ? card('Run the first check', 'No check has finished for these schools yet. Numbers appear after it.',
    table(['School', 'Domain', ''], waiting.map(s => ({ cells: [s.name, s.domain, html`<button class="btn btn-primary btn-sm" data-act="connCheck" data-id="${s.id}">Run the first check</button>`] })))) : '';

  const noSchools = unscored.length ? card(o.schools.length ? 'Schools not counted in these numbers' : 'No school is scored yet',
    'The totals below add up only the counted schools. A school that holds files but is not counted leaves the KPIs at zero.',
    html`${table(['School', 'Counted', ''], unscored.map(s => ({ cells: [s.name, 'No', html`<button class="btn btn-sm btn-primary" data-act="toggleScored" data-id="${s.id}" data-value="true">Include in the KPIs</button>`] })))}`) : '';

  const urgentCount = urgentIssues.length + urgentNow.length;
  const urgentCard = card('Urgent right now', urgentCount ? urgentIssues.length + ' open urgent issue' + (urgentIssues.length === 1 ? '' : 's') + ' · ' + urgentNow.length + ' open incident' + (urgentNow.length === 1 ? '' : 's') : 'Nothing urgent is open.',
    html`${urgentIssues.length ? html`<h3>Urgent issues</h3><p class="muted">Which school and which rule. File names stay with the school.</p>${table(['School', 'Rule', 'Status', 'Open since'], urgentIssues.map(i => ({ cells: [i.siteName, (i.ruleCode ? i.ruleCode + ' · ' : '') + i.ruleName, title(i.status), fmt(i.openedAt)] })))}` : ''}
    ${urgentNow.length ? html`<h3>Incidents</h3>${table(['Incident', 'School', 'What happened', 'Detected', ''], urgentNow.map(i => ({ cells: [badge('b-bad', title(i.type)), siteOf(i.siteId) || '', i.description, fmt(i.detectedAt), html`<button class="btn btn-sm" data-act="resolveIncident" data-id="${i.id}">Resolve</button>`] })))}` : ''}
    ${urgentCount ? '' : empty('No urgent issues or incidents.')}`,
    html`<a class="card-link" href="#/incidents">All incidents</a>`);

  return page('Network overview', o.checkedYet ? 'Across every scored school. Totals only: Leadership never sees file names or contents.' : 'No check has finished yet. Numbers appear after the first check.',
    html`${firstCheck}${noSchools}
    <div class="kpi-grid">
      ${tile('Files checked', o.filesChecked, 'slate', o.schools.length + ' scored school' + (o.schools.length === 1 ? '' : 's'))}
      ${tile('Open issues', o.open, 'brand', (o.open - o.urgentOpen) + ' normal')}
      ${tile('Urgent', o.urgentOpen, 'brand', o.urgentOpen ? 'Listed below' : 'None open')}
      ${tile('Need follow-up', o.needFollowUp, 'amber', 'Waiting for a coordinator')}
      ${tile('Fixed by staff', o.fixedByStaff, 'green', 'Closed by a re-check')}
      ${tile('Files in personal drives', o.personalDriveFiles, 'slate', 'Rule R1')}
    </div>
    ${urgentCard}
    ${card('Schools', 'Health score = 100 minus (open normal + 3 x open urgent) / files checked. Good 85+, Watch 70-84, Needs attention below 70.',
      o.schools.length ? table(['School', 'Health score', 'Files checked', 'Open', 'Urgent', 'Follow-up', 'Fixed'], o.schools.map(s => ({ go: '#/site/' + s.siteId, cells: [s.siteName, html`<b>${Math.round(s.score)}</b> ${rate(s.rating)}`, s.filesChecked, s.openNormal + s.openUrgent, s.openUrgent, s.needFollowUp, s.fixed] }))) : empty('No scored school yet.'))}`,
    S.me.canDownloadWeeklyReport ? html`<a class="btn" href="/api/dashboard/weekly-report.pdf">Weekly report (PDF)</a>` : '');
};

SCREENS.site = async id => {
  const r = await get(`/api/dashboard/sites/${id}/score?days=30`);
  const s = r.score;
  return page(s.siteName, 'Last 30 days',
    html`<div class="kpi-grid">${kpi('Health score', Math.round(s.score))}${kpi('Files checked', s.filesChecked, 'slate')}${kpi('Open', s.openNormal + s.openUrgent, 'amber')}${kpi('Urgent', s.openUrgent, 'brand')}${kpi('Fixed', s.fixed, 'green')}</div>
    ${card('Trend', null, sparkline(r.trend))}`, html`<a class="btn" href="javascript:history.back()">Back</a>`);
};

SCREENS.school = async () => {
  const [counts, issues] = await Promise.all([get('/api/follow-up/counts'), get('/api/issues?filter=URGENT'), loadFiles(), loadSites()]);
  const score = await get(`/api/dashboard/sites/${S.me.siteId}/score?days=30`).catch(() => null);
  return page(siteName(S.me.siteId) || 'My school', 'Where your school stands today.',
    html`<div class="kpi-grid">${kpi('Open', counts.open)}${kpi('Urgent', counts.urgent, 'brand')}${kpi('Need follow-up', counts.needFollowUp, 'amber')}${kpi('Fixed', counts.fixed, 'green')}${score ? kpi('Health score', Math.round(score.score.score), 'slate') : ''}</div>
    ${score ? card('Trend', null, sparkline(score.trend)) : ''}
    ${card('Urgent issues', null, issues.length ? issueTable(issues) : empty('No urgent issues.'), html`<a class="card-link" href="#/issues">All issues</a>`)}`,
    S.me.canDownloadWeeklyReport ? html`<a class="btn" href="/api/dashboard/weekly-report.pdf">Weekly report (PDF)</a>` : '');
};

SCREENS.incidents = async () => {
  const list = await get('/api/dashboard/incidents');
  return page('Incidents', 'Major events: exam leaks, important or bulk deletions, lost connection, urgent issues left open.',
    card('Incidents', null, table(['Type', 'What happened', 'Detected', 'Status', ''], list.map(i => ({ cells: [title(i.type), i.description, fmt(i.detectedAt), i.resolvedAt ? badge('b-good', 'Resolved') : badge('b-bad', 'Open'), i.resolvedAt ? '' : html`<button class="btn btn-sm" data-act="resolveIncident" data-id="${i.id}">Resolve</button>`] })))));
};

// ---- rules
SCREENS.rules = async () => {
  invalidate('rules');
  const rules = Object.values(await loadRules());
  const it = S.me.role === 'SCHOOL_IT', canApprove = ['SCHOOL_IT', 'LEADERSHIP'].includes(S.me.role);
  const rows = rules.map(r => {
    const v = r.effective || r.pending || {};
    return { cells: [r.code, html`<b>${v.name}</b><div class="muted">${v.whyItMatters || ''}</div>`, title(v.checkType), sevBadge(v.severity), r.status === 'ACTIVE' ? badge('b-good', 'Active') : badge('b-neutral', title(r.status)), r.openIssues,
      html`<div class="row">${it ? html`<a class="btn btn-sm" href="#/rule/${r.id}">Edit</a>` : ''}${it ? html`<button class="btn btn-sm" data-act="${r.status === 'ACTIVE' ? 'pauseRule' : 'resumeRule'}" data-id="${r.id}">${r.status === 'ACTIVE' ? 'Pause' : 'Resume'}</button><button class="btn btn-sm btn-danger" data-act="deleteRule" data-id="${r.id}" data-confirm="Delete rule ${r.code}? Its open issues will be closed. Pausing is usually better.">Delete</button>` : ''}${canApprove && r.pending ? html`<button class="btn btn-sm btn-primary" data-act="approveRule" data-id="${r.id}" data-version="${r.pending.id}">Approve new version</button>` : ''}</div>`] };
  });
  return page('Rules', 'What Sijill checks. Changing a rule creates a new version that is approved before it applies.',
    card('Rules', rules.length + ' rules', table(['Code', 'Rule', 'Check', 'Severity', 'Status', 'Open issues', ''], rows)),
    it ? html`<a class="btn btn-primary" href="#/rule/new">Add a rule</a>` : '');
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
      ${isNew ? html`<div class="field"><label><input type="checkbox" name="turnOn"> Turn on straight away</label>${S.me.role === 'LEADERSHIP' ? html`<label><input type="checkbox" name="networkWide"> Network-wide rule</label>` : ''}</div>` : ''}
      <div class="row"><button class="btn" name="mode" value="preview">Preview</button><button class="btn btn-primary" name="mode" value="save">Save</button><a class="btn" href="#/rules">Cancel</a></div>
    </form><div id="preview" style="margin-top:16px"></div></section>`);
};

// ---- people
SCREENS.people = async () => {
  invalidate('people');
  const [people, sites] = await Promise.all([loadPeople(), loadSites()]);
  const list = Object.values(people), it = S.me.role === 'SCHOOL_IT', coord = S.me.role === 'COORDINATOR';
  const groups = it ? await get('/api/people/role-groups').catch(() => []) : [];
  const rows = list.map(p => ({ cells: [html`<b>${p.displayName}</b><div class="muted">${p.email}</div>`, siteName(p.siteId),
    it ? html`<form data-form="person" data-id="${p.id}" class="row"><select name="role">${options(ROLES, p.role, x => ROLE_LABEL[x])}</select><select name="status">${options(['NEW', 'ACTIVE', 'LEAVING', 'LEFT'], p.status)}</select><button class="btn btn-sm">Save</button></form>` : html`${ROLE_LABEL[p.role]} · ${title(p.status)}`,
    p.overridden ? badge('b-watch', 'Overridden') : '',
    html`<div class="row">${it ? html`<button class="btn btn-sm" data-act="welcome" data-id="${p.id}">Send welcome</button>` : ''}${coord ? html`<button class="btn btn-sm" data-act="personFollowUp" data-id="${p.id}">Follow up</button>` : ''}</div>`] }));
  return page('People', 'Everyone signs in with their own school account. There are no Sijill passwords.',
    html`${card('Staff', list.length + ' people', table(['Person', 'School', 'Role and status', '', ''], rows))}
    ${it ? card('Add a person', 'They sign in with their own Google account. Use the same e-mail they sign in with.', html`<form data-form="addPerson" class="row"><input class="input" type="email" name="email" placeholder="name@school.edu" required><input class="input" name="name" placeholder="Full name" required><select name="role">${options(['TEACHER', 'COORDINATOR', 'PRINCIPAL', 'STUDENT'], 'TEACHER', x => ROLE_LABEL[x])}</select><button class="btn btn-primary">Add person</button></form>`) : ''}
    ${it ? card('Role groups', 'A directory group gives its members a role at their next sync.', html`${table(['Directory group', 'Role'], groups.map(g => ({ cells: [g.directoryGroup, ROLE_LABEL[g.role]] })))}<form data-form="roleGroup" class="row" style="margin-top:12px"><input class="input" name="directoryGroup" placeholder="group@school.edu" required><select name="role">${options(ROLES, 'TEACHER', x => ROLE_LABEL[x])}</select><button class="btn">Save mapping</button></form>`) : ''}`,
    it ? html`<button class="btn" data-act="syncPeople">Sync directory now</button>` : '');
};

// ---- schools and settings
SCREENS.sites = async () => {
  invalidate('sites');
  const sites = await loadSites();
  const it = S.me.role === 'SCHOOL_IT', lead = S.me.role === 'LEADERSHIP';
  const settings = it ? await get('/api/settings').catch(() => []) : [];
  const schools = Object.values(sites);
  const counted = s => html`${s.scored ? 'Yes' : 'No'} ${lead ? html`<button class="btn btn-sm" data-act="toggleScored" data-id="${s.id}" data-value="${!s.scored}">${s.scored ? 'Exclude' : 'Include'}</button>` : ''}`;
  return page('Schools & settings', null, html`
    ${card('Schools', null, schools.length ? table(['Name', 'Domain', 'Counted in KPIs', 'Status'], schools.map(s => ({ cells: [html`${s.name} ${lead ? html`<button class="btn btn-sm" data-act="renameSchool" data-id="${s.id}" data-name="${s.name}">Rename</button>` : ''}`, s.domain, counted(s), s.active ? badge('b-good', 'Active') : badge('b-neutral', 'Inactive')] }))) : empty('No school yet. Add the first one below.'))}
    ${lead ? card('Add school', null, html`<form data-form="site" class="row"><input class="input" name="name" placeholder="School name" required><input class="input" name="domain" placeholder="school.edu" required><label><input type="checkbox" name="scored" checked> Counted in the KPIs</label><button class="btn btn-primary">Add school</button></form>`) : ''}
    ${it ? card('Settings', null, table(['Setting', 'Value'], settings.map(s => ({ cells: [s.key, html`<form data-form="setting" data-key="${s.key}" class="row"><input class="input" name="value" value="${s.value}"><button class="btn btn-sm">Save</button></form>`] })))) : ''}`);
};

// ---- connection
SCREENS.connection = async (arg, q) => {
  const sites = Object.values(await loadSites()).filter(s => s.active);
  const id = q.get('site') || S.me.siteId || (sites[0] && sites[0].id);
  if (!id) return page('Connection', null, empty('No school yet.'));
  const [st, runs] = await Promise.all([get(`/api/connection/${id}`), get(`/api/connection/${id}/checks`).catch(() => [])]);
  const it = ['SCHOOL_IT', 'LEADERSHIP'].includes(S.me.role);
  return page('Connection', 'Sijill reads the school workspace with a read-only account. It can never change a file.',
    html`<div class="row"><label>School <select class="input" data-act="pickSite">${sites.map(s => html`<option value="${s.id}" ${s.id === id ? raw('selected') : ''}>${s.name}</option>`)}</select></label></div>
    ${card('Status', null, html`<div class="row">${st.connectorInstalled ? badge('b-good', 'Connector installed') : badge('b-bad', 'No connector installed')} ${st.checksEnabled ? badge('b-good', 'Checks on') : badge('b-neutral', 'Checks off')} <span class="muted">First check: ${fmt(st.firstCheckAt)}</span></div>
      ${!st.connectorInstalled ? html`<div class="callout callout-warn" style="margin-top:12px">No workspace connector is installed, so no files can be read and no checks can run.</div>` : ''}
      ${it ? html`<div class="row" style="margin-top:12px"><button class="btn" data-act="connTest" data-id="${id}">Test connection</button><button class="btn btn-primary" data-act="connCheck" data-id="${id}">Run a check now</button><button class="btn" data-act="connDisconnect" data-id="${id}" data-confirm="Stop all checks for this school?">Disconnect</button><button class="btn" data-act="connReconnect" data-id="${id}">Reconnect</button></div>` : ''}`)}
    ${card('Recent checks', null, table(['Started', 'Type', 'Status', 'Files', 'Skipped', 'Opened', 'Closed', 'Note'], runs.map(r => ({ cells: [fmt(r.startedAt), title(r.type), badge(r.status === 'SUCCEEDED' ? 'b-good' : r.status === 'FAILED' ? 'b-bad' : 'b-watch', title(r.status)), r.itemsProcessed, r.itemsSkipped, r.issuesOpened, r.issuesClosed, r.note || ''] }))))}`);
};

// ---- audit and restores
SCREENS.activity = async () => {
  const list = await get('/api/activity-log');
  return page('Activity log', 'Every change is recorded in a chain that shows if anyone altered it.',
    card('Entries', null, table(['#', 'When', 'Action', 'About', 'Change'], list.map(e => ({ cells: [e.seq, fmt(e.at), e.action, e.entityType || '', [e.before, e.after].filter(Boolean).join(' → ')] }))), html`<span id="verify"></span>`),
    html`<button class="btn" data-act="verifyLog">Verify the log</button><a class="btn" href="/api/activity-log/download">Download</a>`);
};

SCREENS.restores = async () => {
  const [list] = await Promise.all([get('/api/backup/restores'), loadFiles()]);
  const role = S.me.role, requester = ['COORDINATOR', 'PRINCIPAL'].includes(role), approver = ['SCHOOL_IT', 'LEADERSHIP'].includes(role);
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

SCREENS.prefs = async () => page('My preferences', null, card('Messages', null, html`<form data-form="prefs">
  ${field('Language of messages', html`<select name="language">${options(['EN', 'AR', 'BOTH'], S.me.language, x => ({ EN: 'English', AR: 'Arabic', BOTH: 'English and Arabic' }[x]))}</select>`)}
  ${field('How to receive them', html`<select name="digestMode">${options(['PER_ISSUE', 'DAILY'], S.me.digestMode, x => x === 'DAILY' ? 'One daily summary' : 'One message per issue')}</select>`)}
  <button class="btn btn-primary">Save</button></form>`));

// ---- school portal: shared pieces
const noticeBlock = n => html`<li><b>${say(n.titleEn, n.titleAr)}</b> <span class="muted">${fmtDate(n.startsAt)} to ${fmtDate(n.endsAt)}${n.audience !== 'ALL' ? ' · ' + title(n.audience) : ''}</span><div>${say(n.bodyEn, n.bodyAr)}</div></li>`;

function weekGrid(lessons, who) {
  if (!lessons.length) return empty('No lessons in the timetable yet.');
  const periods = [...new Set(lessons.map(l => l.period))].sort((a, b) => a - b);
  return html`<div class="scroll-x"><table class="t"><thead><tr><th>Period</th>${DAYS.map(d => html`<th>${d}</th>`)}</tr></thead><tbody>${periods.map(p => html`<tr><td><b>${p}</b></td>${DAYS.map((d, i) => {
    const l = lessons.find(x => x.day === i && x.period === p);
    return html`<td>${l ? html`<b>${l.subject}</b><div class="muted">${who === 'teacher' ? l.section : l.teacher}${l.room ? ' · ' + l.room : ''}</div>` : ''}</td>`;
  })}</tr>`)}</tbody></table></div>`;
}

const sectionLabel = s => s.grade + s.name;
const sectionPicker = (sections, current, route) => html`<label>Section <select class="input" data-act="pickSection" data-route="${route}">${sections.map(s => html`<option value="${s.id}" ${s.id === current ? raw('selected') : ''}>${sectionLabel(s)} (${s.students} student${s.students === 1 ? '' : 's'})</option>`)}</select></label>`;

// ---- teacher portal
SCREENS.today = async () => {
  const t = await get('/api/portal/teacher');
  const taken = new Set(t.attendanceTaken);
  return page('Today', (t.schoolDay ? DAYS[new Date(t.date + 'T00:00:00').getDay()] || '' : 'Not a school day') + ' · ' + t.date,
    html`${card("Today's lessons", 'From the timetable.', t.lessons.length ? table(['Period', 'Section', 'Subject', 'Room', 'Attendance'], t.lessons.map(l => ({ cells: [l.period, l.section, l.subject, l.room || '', taken.has(l.sectionId) ? badge('b-good', 'Taken') : html`<a class="btn btn-sm" href="#/attendance?s=${l.sectionId}">Take attendance</a>`] }))) : empty(t.schoolDay ? 'No lessons today.' : 'Attendance and lessons run Sunday to Thursday.'))}
    ${card('My sections', null, t.sections.length ? table(['Section', 'Students', 'Attendance today', ''], t.sections.map(x => ({ cells: [sectionLabel(x), x.students, taken.has(x.id) ? badge('b-good', 'Taken') : badge('b-watch', 'Not taken'), html`<a href="#/attendance?s=${x.id}">Open</a>`] }))) : empty('You are not assigned to a section yet. The coordinator assigns teachers in Academic setup.'))}`);
};

SCREENS.attendance = async (arg, q) => {
  const [sections, t] = await Promise.all([get('/api/academic/sections'), get('/api/portal/teacher')]);
  if (!sections.length) return page('Attendance', null, empty('You are not assigned to a section yet.'));
  const sid = q.get('s') || sections[0].id;
  const roster = await get('/api/attendance/sections/' + sid);
  const opt = (r, v, label) => html`<label style="margin-inline-end:12px"><input type="radio" name="st_${r.studentId}" value="${v}" ${r.status === v ? raw('checked') : ''}> ${label}</label>`;
  return page('Attendance', 'Record today. Everyone starts as present; change only the absences and leave. You can correct the same day only.',
    html`<div class="row">${sectionPicker(sections, sid, 'attendance')} <b>${t.date}</b></div>
    ${card('Class list', roster.length + ' student' + (roster.length === 1 ? '' : 's'), roster.length ? html`<form data-form="attendance" data-section="${sid}" data-date="${t.date}">
      <table class="t"><thead><tr><th>Student</th><th>Status</th></tr></thead><tbody>${roster.map(r => html`<tr><td>${r.name}</td><td>${opt(r, 'PRESENT', 'Present')}${opt(r, 'ABSENT', 'Absent')}${opt(r, 'LEAVE', 'Leave')}</td></tr>`)}</tbody></table>
      <div class="row" style="margin-top:16px"><button class="btn btn-primary">Save attendance</button></div></form>` : empty('No students are enrolled in this section yet.'))}`);
};

SCREENS.marks = async (arg) => {
  const list = await get('/api/marks/assessments');
  if (!arg) {
    const [sections, terms] = await Promise.all([get('/api/academic/sections'), get('/api/academic/terms')]);
    // only the sections and subjects this teacher actually teaches, so every choice in the form is allowed
    const mine = (await Promise.all(sections.map(async s => (await get(`/api/academic/sections/${s.id}/assignments`).catch(() => []))
      .filter(a => a.teacherId === S.me.id).map(a => ({ value: s.id + '|' + a.subjectId, label: sectionLabel(s) + ' · ' + a.subject }))))).flat();
    const form = !mine.length ? empty('You are not assigned to a section and subject yet.') : !terms.length ? empty('No term exists yet. The coordinator creates the terms under Academic setup.') : html`<form data-form="assessment" class="row">
      <select class="input" name="pair">${mine.map(m => html`<option value="${m.value}">${m.label}</option>`)}</select>
      <select class="input" name="term">${terms.map(t => html`<option value="${t.number}">${t.name}</option>`)}</select>
      <input class="input" name="name" placeholder="Name, e.g. Quiz 1" required><input class="input" type="number" name="maxMark" min="1" max="1000" step="0.01" placeholder="Out of" required><button class="btn btn-primary">Add assessment</button></form>`;
    return page('Marks', 'You set the assessments for the sections and subjects you teach, enter the grades, then submit to lock them.',
      html`${card('New assessment', null, form)}
      ${card('Assessments', null, list.length ? table(['Section', 'Subject', 'Term', 'Assessment', 'Out of', 'Status'], list.map(a => ({ go: '#/marks/' + a.id,
        cells: [a.section, a.subject, a.term, a.name, a.maxMark, a.published ? badge('b-good', 'Published') : a.submitted ? badge('b-internal', 'Submitted') : badge('b-watch', 'Open')] }))) : empty('No assessments yet. Add the first one above.'))}`);
  }
  const a = list.find(x => x.id === arg);
  if (!a) throw Object.assign(new Error('That assessment was not found.'), { status: 404 });
  const sheet = await get(`/api/marks/assessments/${arg}/sheet`);
  const locked = a.submitted;
  return page(a.name, `${a.section} · ${a.subject} · term ${a.term} · out of ${a.maxMark}`,
    html`${locked ? html`<div class="callout callout-info">These marks are submitted and locked. Ask School IT to unlock them if something must change.</div>` : ''}
    ${card('Marks', null, sheet.length ? html`<form data-form="marks" data-id="${arg}"><table class="t"><thead><tr><th>Student</th><th>Mark (0 to ${a.maxMark})</th><th>Absent</th></tr></thead><tbody>${sheet.map(r => html`<tr data-student="${r.studentId}"><td>${r.name}</td><td><input class="input" type="number" min="0" max="${a.maxMark}" step="0.01" value="${r.value ?? ''}" ${locked ? raw('disabled') : ''}></td><td><input type="checkbox" ${r.absent ? raw('checked') : ''} ${locked ? raw('disabled') : ''}></td></tr>`)}</tbody></table>
      ${locked ? '' : html`<div class="row" style="margin-top:16px"><button class="btn" name="mode" value="save">Save</button><button class="btn btn-primary" name="mode" value="submit">Save and submit</button></div>`}</form>` : empty('No students are enrolled in this section yet.'))}`,
    html`<a class="btn" href="#/marks">Back</a>`);
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
    ${card('Shared by you', null, table(['Title', 'Section', 'Subject', 'Link', ''], list.map(m => ({ cells: [m.title, m.section, m.subject, html`<a href="${m.link}" target="_blank" rel="noopener noreferrer">Open</a>`, html`<button class="btn btn-sm btn-danger" data-act="removeMaterial" data-id="${m.id}" data-confirm="Remove this material?">Remove</button>`] }))))}`);
};

SCREENS.notices = async () => {
  const list = await get('/api/notices');
  return page('Notices', null, card('Current notices', null, list.length ? html`<ul class="timeline">${list.map(noticeBlock)}</ul>` : empty('No notices right now.')));
};

// ---- student portal
SCREENS.mydash = async () => {
  const d = await get('/api/portal/student');
  const a = d.attendance;
  return page('My day', `Section ${sectionLabel(d.section)} · ${d.date}`,
    html`<div class="kpi-grid">${kpi('Present', a.present, 'green')}${kpi('Absent', a.absent, 'brand')}${kpi('On leave', a.leave, 'amber')}${kpi('Published results', d.results.length, 'slate')}</div>
    ${card("Today's lessons", d.schoolDay ? null : 'Not a school day', d.today.length ? table(['Period', 'Subject', 'Teacher', 'Room'], d.today.map(l => ({ cells: [l.period, l.subject, l.teacher, l.room || ''] }))) : empty(d.schoolDay ? 'No lessons today.' : 'Lessons run Sunday to Thursday.'))}
    ${card('Notices', null, d.notices.length ? html`<ul class="timeline">${d.notices.map(noticeBlock)}</ul>` : empty('No notices right now.'))}
    ${card('My timetable', null, weekGrid(d.week, 'student'))}
    ${card('My attendance', 'Last days', a.recent.length ? table(['Date', 'Status'], a.recent.map(r => ({ cells: [fmtDate(r.date), badge(r.status === 'PRESENT' ? 'b-good' : r.status === 'ABSENT' ? 'b-bad' : 'b-watch', title(r.status))] }))) : empty('Nothing recorded yet.'))}
    ${card('Study materials', null, d.materials.length ? table(['Title', 'Subject', 'Teacher', ''], d.materials.map(m => ({ cells: [m.title, m.subject, m.teacher, html`<a href="${m.link}" target="_blank" rel="noopener noreferrer">Open</a>`] }))) : empty('No materials yet.'))}`);
};

SCREENS.results = async () => {
  const rows = await get('/api/marks/mine');
  const terms = [...new Set(rows.map(r => r.term))];
  return page('My results', 'Your own results, shown after your school publishes them.',
    terms.length ? html`${terms.map(t => card('Term ' + t, null, table(['Subject', 'Assessment', 'Mark'], rows.filter(r => r.term === t).map(r => ({ cells: [r.subject, r.assessment, r.absent ? 'Absent' : html`<b>${r.value}</b> / ${r.maxMark}`] })))))}`
      : card('Results', null, empty('No results have been published yet.')));
};

SCREENS.mymaterials = async () => {
  const list = await get('/api/materials');
  return page('Study materials', 'Shared by your teachers.', card('Materials', null, list.length ? table(['Title', 'Subject', 'Teacher', 'Shared', ''], list.map(m => ({ cells: [m.title, m.subject, m.teacher, fmtDate(m.createdAt), html`<a href="${m.link}" target="_blank" rel="noopener noreferrer">Open</a>`] }))) : empty('No materials yet.')));
};

// ---- Coordinator: academic setup. School IT: unlock marks, publish results, notices
SCREENS.academic = async (arg, q) => {
  invalidate('people');
  const [sections, terms, subjects] = await Promise.all([get('/api/academic/sections'), get('/api/academic/terms'), loadSubjects(), loadPeople()]);
  const sid = q.get('s') || (sections[0] && sections[0].id);
  const staff = Object.values(S.people).filter(p => ['TEACHER', 'COORDINATOR'].includes(p.role));
  const students = Object.values(S.people).filter(p => p.role === 'STUDENT');
  let detail = '';
  if (sid) {
    const [roster, assigns, lessons] = await Promise.all([get(`/api/academic/sections/${sid}/students`), get(`/api/academic/sections/${sid}/assignments`), get(`/api/academic/timetable?sectionId=${sid}`)]);
    detail = html`
      ${card('Students', roster.length + ' enrolled', html`${table(['Student', 'E-mail'], roster.map(r => ({ cells: [r.name, r.email] })))}
        <form data-form="enrol" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="studentEmail">${students.map(p => html`<option value="${p.email}">${p.displayName} (${p.email})</option>`)}</select><button class="btn">Enrol in this section</button></form>
        ${students.length ? '' : html`<p class="muted">No student accounts yet. Add students under People first (role Student).</p>`}`)}
      ${card('Who teaches what', null, html`${table(['Subject', 'Teacher'], assigns.map(a => ({ cells: [a.subject, a.teacher] })))}
        <form data-form="assign" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="subjectId">${subjects.map(x => html`<option value="${x.id}">${x.name}</option>`)}</select><select class="input" name="teacherId">${staff.map(p => html`<option value="${p.id}">${p.displayName}</option>`)}</select><button class="btn">Assign</button></form>`)}
      ${card('Timetable', 'Sunday to Thursday. A section, a teacher or a room cannot be in two places in one period.', html`${weekGrid(lessons, 'teacher')}
        ${lessons.length ? table(['Day', 'Period', 'Subject', 'Teacher', 'Room', ''], lessons.map(l => ({ cells: [DAYS[l.day], l.period, l.subject, l.teacher, l.room || '', html`<button class="btn btn-sm btn-danger" data-act="removeLesson" data-id="${l.id}" data-confirm="Remove this lesson?">Remove</button>`] }))) : ''}
        <form data-form="lesson" data-section="${sid}" class="row" style="margin-top:12px"><select class="input" name="day">${DAYS.map((d, i) => html`<option value="${i}">${d}</option>`)}</select><input class="input" type="number" name="period" min="1" max="12" placeholder="Period" required><select class="input" name="subjectId">${assigns.map(a => html`<option value="${a.subjectId}">${a.subject}</option>`)}</select><input class="input" name="room" placeholder="Room (optional)"><button class="btn btn-primary">Add lesson</button></form>`)}`;
  }
  const termCard = card('Terms', 'Teachers can only set assessments for a term that exists here.', html`${terms.length ? table(['Term', 'Name', 'Starts', 'Ends'], terms.map(t => ({ cells: [t.number, t.name, fmtDate(t.startsOn), fmtDate(t.endsOn)] }))) : empty('No term yet. Add term 1 below.')}
      <form data-form="term" class="row" style="margin-top:12px"><select class="input" name="number"><option>1</option><option>2</option><option>3</option></select><input class="input" name="name" placeholder="Name, e.g. Term 1" required><input class="input" type="date" name="startsOn" required><input class="input" type="date" name="endsOn" required><button class="btn btn-primary">Save term</button></form>`);
  const subjectCard = card('Subjects', subjects.length ? subjects.map(x => x.name).join(', ') : 'No subject yet.', html`<form data-form="subject" class="row"><input class="input" name="name" placeholder="e.g. Mathematics" required><button class="btn">Add subject</button></form>`);
  const typeCard = card('Document types', 'The default sensitivity Sijill gives that kind of document. Add a type or change one.', html`<form data-form="docType" class="row"><input class="input" name="name" placeholder="Type name" required><select name="sensitivity">${options(SENS, 'INTERNAL')}</select><button class="btn">Save</button></form>`);
  return page('Academic setup', 'Terms, subjects, classes and sections, students, teachers and the weekly timetable.',
    html`${termCard}${subjectCard}${typeCard}${card('Classes and sections', null, html`<div class="row">${sections.length ? sectionPicker(sections, sid, 'academic') : ''}</div>
      <form data-form="section" class="row" style="margin-top:12px"><input class="input" type="number" name="grade" min="1" max="12" placeholder="Grade 1 to 12" required><input class="input" name="name" placeholder="Section, e.g. B" required><button class="btn btn-primary">Add class section</button></form>`)}
    ${detail}`);
};

SCREENS.assessments = async (arg, q) => {
  const list = await get('/api/marks/assessments');
  const terms = [...new Set(list.map(a => a.sectionId + '|' + a.term))].map(k => { const [sec, term] = k.split('|'); const rows = list.filter(a => a.sectionId === sec && String(a.term) === term); return { sec, term: Number(term), label: rows[0].section, published: rows.every(r => r.published), ready: rows.every(r => r.submitted) }; });
  return page('Assessments & results', 'Teachers set the assessments and enter the grades. Here you see who has submitted, unlock marks with a reason, and publish a term to its students.',
    html`${card('Assessments', null, list.length ? table(['Section', 'Subject', 'Term', 'Assessment', 'Out of', 'Status', ''], list.map(a => ({ cells: [a.section, a.subject, a.term, a.name, a.maxMark,
      a.published ? badge('b-good', 'Published') : a.submitted ? badge('b-internal', 'Submitted') : badge('b-watch', 'Open'),
      a.submitted && !a.published ? html`<button class="btn btn-sm" data-act="unlockMarks" data-id="${a.id}">Unlock</button>` : ''] }))) : empty('No assessments yet.'))}
    ${card('Publish results', 'Students see a term only after you publish it. Every assessment of that term must be submitted first.', terms.length ? table(['Section', 'Term', 'Marks', ''], terms.map(t => ({ cells: [t.label, t.term, t.published ? badge('b-good', 'Published') : t.ready ? badge('b-internal', 'All submitted') : badge('b-watch', 'Waiting for teachers'),
      t.published ? html`<button class="btn btn-sm btn-danger" data-act="unpublishResults" data-section="${t.sec}" data-term="${t.term}">Unpublish</button>` : html`<button class="btn btn-sm btn-primary" data-act="publishResults" data-section="${t.sec}" data-term="${t.term}" ${t.ready ? '' : raw('disabled')}>Publish</button>`] }))) : empty('Nothing to publish yet.'))}`);
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
    ${card('All notices', null, table(['Title', 'Shown to', 'From', 'To', 'State', ''], list.map(n => ({ cells: [n.titleEn, title(n.audience), fmt(n.startsAt), fmt(n.endsAt), state(n), html`<button class="btn btn-sm btn-danger" data-act="removeNotice" data-id="${n.id}" data-confirm="Remove this notice?">Remove</button>`] }))))}`);
};

// =============================================================== actions (buttons)
async function refreshAfter(msg) { if (msg) toast(msg); await render(); }
const ACT = {
  filter: (d) => { const q = new URLSearchParams(location.hash.split('?')[1] || ''); q.set(d.name, d.value); location.hash = location.hash.split('?')[0] + '?' + q; },
  pickSite: (d, el) => { location.hash = '#/connection?site=' + el.value; },
  fixed: async d => { const r = await post(`/api/issues/${d.id}/fixed`); S.flash = r.fixed ? { ok: 'Checked again: it is fixed. Thank you.' } : { err: 'Not fixed yet: ' + (S.me.language === 'AR' && r.stillWrongAr ? r.stillWrongAr : r.stillWrongEn || 'the file still breaks the rule.') }; await render(); },
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
  unlockMarks: async d => { const reason = prompt('Why are you unlocking these marks? The teacher will be told.'); if (!reason) return; await post(`/api/marks/assessments/${d.id}/unlock`, { reason }); await refreshAfter('Marks unlocked. The teacher was told.'); },
  publishResults: async d => { await post(`/api/marks/sections/${d.section}/publish`, { term: Number(d.term) }); await refreshAfter('Published. Students were told.'); },
  unpublishResults: async d => { const reason = prompt('Why are you unpublishing these results?'); if (!reason) return; await post(`/api/marks/sections/${d.section}/unpublish`, { term: Number(d.term), reason }); await refreshAfter('Results are hidden again.'); },
  addPerson: async f => { await post('/api/people', { email: f.email, name: f.name, role: f.role }); invalidate('people'); await refreshAfter(f.email + ' can now sign in.'); },
  renameSchool: async d => { const name = prompt('School name:', d.name); if (!name || name === d.name) return; await put('/api/sites/' + d.id, { name }); invalidate('sites'); await refreshAfter('Renamed.'); },
  toggleScored: async d => { await put('/api/sites/' + d.id, { scored: d.value === 'true' }); invalidate('sites'); await refreshAfter(d.value === 'true' ? 'Included in the school scores.' : 'No longer scored.'); },
  resolveIncident: async d => { await post(`/api/dashboard/incidents/${d.id}/resolve`); await refreshAfter('Incident resolved.'); },
  pauseRule: async d => { await post(`/api/rules/${d.id}/pause`); await refreshAfter('Rule paused.'); },
  resumeRule: async d => { await post(`/api/rules/${d.id}/resume`); await refreshAfter('Rule resumed.'); },
  deleteRule: async d => { await del(`/api/rules/${d.id}`); await refreshAfter('Rule deleted.'); },
  approveRule: async d => { await post(`/api/rules/${d.id}/versions/${d.version}/approve`); await refreshAfter('Version approved. It applies from the next check.'); },
  syncPeople: async () => { const r = await post('/api/people/sync'); invalidate('people'); await refreshAfter(r.changed + ' people changed.'); },
  welcome: async d => { await post(`/api/people/${d.id}/welcome`); await refreshAfter('Welcome message queued.'); },
  personFollowUp: async d => { const note = prompt('Note for the follow-up (optional):'); if (note === null) return; await post(`/api/people/${d.id}/follow-up`, { note }); toast('Follow-up recorded.'); },
  trustType: async d => { await post(`/api/doc-types/${d.id}/trust`); invalidate('docTypes'); await refreshAfter('Type trusted.'); },
  connTest: async d => { const r = await post(`/api/connection/${d.id}/test`); toast((r && r.message) || 'Connection tested.'); },
  connCheck: async d => { const r = await post(`/api/connection/${d.id}/check`); await refreshAfter('Check started. Refresh in a few seconds to see the result.'); },
  connDisconnect: async d => { const r = await post(`/api/connection/${d.id}/disconnect`); await refreshAfter('Disconnected. No checks will run until you reconnect.'); },
  connReconnect: async d => { const r = await post(`/api/connection/${d.id}/reconnect`); await refreshAfter('Reconnected.'); },
  verifyLog: async () => { const r = await get('/api/activity-log/verify'); document.getElementById('verify').innerHTML = r.valid ? `<span class="ok">Log verified: ${r.entriesChecked} entries, none altered.</span>` : `<span class="err">Log broken at entry ${esc(r.firstBrokenSeq)}.</span>`; },
  restore: async d => { await post(`/api/backup/restores/${d.id}/${d.do}`); await refreshAfter('Updated.'); },
  signout: async () => { await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie('XSRF-TOKEN') || '') } }).catch(() => { }); location.href = '/'; }
};

// =============================================================== forms
const csv = s => String(s || '').split(',').map(x => x.trim()).filter(Boolean);
const FORM = {
  disagree: async (f, d) => { await post(`/api/issues/${d.id}/disagree`, { note: f.note }); await refreshAfter('Your coordinator will look at it.'); },
  exception: async (f, d) => { await post(`/api/exceptions/issues/${d.id}`, { reason: f.reason, endsOn: f.endsOn }); await refreshAfter('Exception allowed.'); },
  followUpIssue: async (f, d) => { await post(`/api/follow-up/${d.id}/follow-up`, { note: f.note || null }); await refreshAfter('Follow-up recorded.'); },
  classify: async (f, d) => { await put(`/api/files/${d.id}/classification`, { docType: f.docType, sensitivity: f.sensitivity, hasStudentData: !!f.hasStudentData, hasHealthData: !!f.hasHealthData }); await refreshAfter('Saved. The file was checked again.'); },
  submitPlan: async f => { await post('/api/planning', f); await refreshAfter('Plan submitted.'); },
  review: async (f, d, form, submitter) => { await post(`/api/planning/${d.id}/review`, { decision: submitter.value, reason: f.reason || null }); await refreshAfter('Review saved.'); },
  totals: async f => { const t = await get('/api/planning/totals?week=' + f.week); document.getElementById('totals').innerHTML = `<div class="kpi-grid">${[['Submitted', t.submitted], ['Returned', t.returned], ['Approved', t.approved]].map(([a, b]) => `<div class="card kpi"><span class="kpi-label">${a}</span><span class="kpi-value">${esc(b)}</span></div>`).join('')}</div>`; },
  rule: async (f, d, form, submitter) => {
    const draft = { name: f.name, whyItMatters: f.whyItMatters, whyItMattersAr: f.whyItMattersAr, checkType: f.checkType, severity: f.severity, reminderDays: Number(f.reminderDays) || 0, parameter: f.parameter || null, fixStepsEn: f.fixStepsEn, fixStepsAr: f.fixStepsAr, docTypes: csv(f.docTypes), sensitivities: [...form.querySelectorAll('[name=sens]:checked')].map(x => x.value) };
    if (submitter.value === 'preview') {
      const p = await post('/api/rules/preview', draft);
      document.getElementById('preview').innerHTML = `<div class="callout callout-info">Would affect <b>${esc(p.wouldBreak)}</b> of ${esc(p.filesChecked)} files (${esc(p.wouldNeedReview)} need review).</div><h3>${esc(p.subjectEn)}</h3><pre style="white-space:pre-wrap">${esc(p.bodyEn)}</pre>`;
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
  enrol: async (f, d) => { await post(`/api/academic/sections/${d.section}/students`, { studentEmail: f.studentEmail }); await refreshAfter('Student enrolled.'); },
  assign: async (f, d) => { await put(`/api/academic/sections/${d.section}/assignments`, { subjectId: f.subjectId, teacherId: f.teacherId }); await refreshAfter('Teacher assigned.'); },
  lesson: async (f, d) => { await post('/api/academic/timetable', { sectionId: d.section, subjectId: f.subjectId, day: Number(f.day), period: Number(f.period), room: f.room || null }); await refreshAfter('Lesson added.'); },
  assessment: async f => { const [sectionId, subjectId] = f.pair.split('|'); await post('/api/marks/assessments', { sectionId, subjectId, term: Number(f.term), name: f.name, maxMark: Number(f.maxMark) }); await refreshAfter('Assessment added.'); },
  notice: async f => { await post('/api/notices', { ...f, startsAt: new Date(f.startsAt).toISOString(), endsAt: new Date(f.endsAt).toISOString() }); await refreshAfter('Notice published.'); },
  prefs: async f => { S.me = await put('/api/me/preferences', { language: f.language, digestMode: f.digestMode }); await refreshAfter('Saved.'); }
};

document.addEventListener('click', async e => {
  const t = e.target.closest('[data-go],[data-act]');
  if (!t || t.tagName === 'SELECT' || t.tagName === 'OPTION') return;
  if (t.dataset.go) { location.hash = t.dataset.go; return; }
  if (t.dataset.confirm && !confirm(t.dataset.confirm)) return;
  const fn = ACT[t.dataset.act]; if (!fn) return;
  t.disabled = true;
  try { await fn(t.dataset, t); } catch (err) { fail(err); } finally { t.disabled = false; }
});
document.addEventListener('change', e => {
  const a = e.target.dataset && e.target.dataset.act;
  if (a === 'pickSite') ACT.pickSite({}, e.target);
  if (a === 'pickSection') location.hash = '#/' + e.target.dataset.route + '?s=' + e.target.value;
});
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
const ICON = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/></svg>';
function shell(route, body) {
  const nav = NAV[S.me.role] || [];
  const initials = (S.me.displayName || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  return html`<div class="frame">
    <aside class="sidebar"><div class="brand"><div class="brand-row"><span class="brand-mark"></span><span class="brand-name">Sijill</span></div><div class="brand-sub"><span class="brand-ar" lang="ar" dir="rtl">سجل</span></div><div class="brand-tag">School file register</div></div>
      <div class="who"><b>${S.me.displayName}</b>${ROLE_LABEL[S.me.role]}${siteName(S.me.siteId) ? ' · ' + siteName(S.me.siteId) : ''}</div>
      <nav class="nav">${nav.map(([r, label]) => html`<a class="nav-item ${route === r ? 'nav-on' : ''}" href="#/${r}" style="text-decoration:none">${raw(ICON)}<span>${label}</span>${r === 'notifications' && S.unread ? html`<span class="count">${S.unread}</span>` : ''}</a>`)}
        <div class="nav-sep"></div><a class="nav-item ${route === 'prefs' ? 'nav-on' : ''}" href="#/prefs" style="text-decoration:none">${raw(ICON)}<span>My preferences</span></a>
        <button class="nav-item" data-act="signout">${raw(ICON)}<span>Sign out</span></button></nav></aside>
    <div class="content"><main class="page" id="main">${body}</main></div></div>`;
}

function signIn(message) {
  return html`<div class="center-screen"><div class="center-card"><div class="brand-row" style="margin-bottom:18px"><span class="brand-mark"></span><span class="brand-name" style="color:var(--ink)">Sijill</span></div>
    <h1 style="margin:0 0 8px;font-size:22px">${message ? 'Your account is not set up yet' : 'Sign in'}</h1>
    <p class="muted">${message || 'Use your school Google account. Sijill has no passwords of its own.'}</p>
    ${message ? html`<button class="btn" data-act="signout">Sign out and try another account</button>` : html`<a class="btn btn-primary" href="/oauth2/authorization/google">Continue with Google</a>`}</div></div>`;
}

function problem(e) {
  const detail = e.status ? `The server answered ${e.status}: ${e.message}` : `The browser could not reach the server (${e.message}). If the app was restarting, try again.`;
  return html`<div class="center-screen"><div class="center-card"><div class="brand-row" style="margin-bottom:18px"><span class="brand-mark"></span><span class="brand-name" style="color:var(--ink)">Sijill</span></div>
    <h1 style="margin:0 0 8px;font-size:22px">Could not load your account</h1><p class="muted">${detail}</p>
    ${e.body && e.body.correlationId ? html`<p class="muted mono">Reference: ${e.body.correlationId}</p>` : ''}
    <div class="row"><button class="btn btn-primary" onclick="location.reload()">Try again</button><button class="btn" data-act="signout">Sign out</button></div></div></div>`;
}

async function refreshBell() { try { const r = await get('/api/notifications/unread-count'); if (r.unread !== S.unread) { S.unread = r.unread; const c = document.querySelector('.nav-item[href="#/notifications"]'); if (c) { const old = c.querySelector('.count'); if (old) old.remove(); if (S.unread) c.insertAdjacentHTML('beforeend', `<span class="count">${S.unread}</span>`); } } } catch { } }

const home = () => ({ TEACHER: 'today', STUDENT: 'mydash', COORDINATOR: 'academic', PRINCIPAL: 'school', SCHOOL_IT: 'assessments', LEADERSHIP: 'overview' }[S.me.role]);

async function render() {
  const app = document.getElementById('app');
  if (!S.me) {
    try { S.me = await get('/api/me'); }
    catch (e) {
      // 401 with a message = signed in to Google but not set up in Sijill; plain 401 = not signed in;
      // anything else is a real problem, so say what it was instead of pretending to be a sign-in page
      app.innerHTML = (e.status === 401 && !e.body ? signIn(null) : e.status === 401 ? signIn(e.message) : problem(e)).s;
      return;
    }
  }
  const [hash, query] = (location.hash || '').replace(/^#\/?/, '').split('?');
  const [route, arg] = (hash || home()).split('/');
  const q = new URLSearchParams(query || '');
  const studentRoutes = new Set([...(NAV.STUDENT || []).map(x => x[0]), 'prefs']);
  const screen = SCREENS[route];
  if (S.me.role === 'STUDENT' && !studentRoutes.has(route)) { location.hash = '#/' + home(); return; }
  if (!screen) { location.hash = '#/' + home(); return; }
  await loadSites(); if (S.unread === 0) refreshBell();
  document.getElementById('app').innerHTML = shell(route, html`<p class="muted">Loading…</p>`).s;
  let body;
  try { body = await screen(arg, q); }
  catch (e) { if (e.status === 401) { S.me = null; return render(); } body = html`<div class="err">${e.message}</div>`; }
  const flash = S.flash; S.flash = null;
  const banner = flash ? html`<div class="${flash.ok ? 'ok' : 'err'}" style="margin-bottom:16px">${flash.ok || flash.err}</div>` : '';
  document.getElementById('main').innerHTML = html`${banner}${body}`.s;
  window.scrollTo(0, 0);
}

window.addEventListener('hashchange', render);
setInterval(() => { if (S.me) refreshBell(); }, 60000);
render();
