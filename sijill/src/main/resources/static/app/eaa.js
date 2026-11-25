/* EAA IT screens: the maroon sidebar, the page header (date or breadcrumb, title, language, bell, user), white cards with a small
   maroon bar, an Overview home and a Schools table. Everything it shows is read from the same data the older screens used. */
'use strict';

const isEaa = () => !!S.me && S.me.role === 'EAA_ADMIN';
/** The new maroon look: EAA IT and teachers. */
const isNewLook = () => !!S.me && ['EAA_ADMIN', 'TEACHER', 'SCHOOL_IT'].includes(S.me.role);
const isItRole = () => !!S.me && S.me.role === 'SCHOOL_IT';

const EA_NAV = [
  ['netoverview', 'Overview', PB.home], ['sites', 'Schools & settings', LIC.school], ['incidents', 'Incidents', LIC.warn], ['support', 'Support tickets', PB.chat],
  ['rules', 'Rules', LIC.shield], ['connection', 'Connection', LIC.plug], ['approvals', 'Approvals', LIC.check], ['restores', 'Restores', 'M3 12a9 9 0 109-9M3 4v5h5'],
  ['itaccounts', 'School IT accounts', PB.users], ['activity', 'Activity log', LD_ICON.doc], ['notifications', 'Notifications', LIC.bell]
];
const eaIc = p => html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${p}"/></svg>`;
const eaToday = () => new Date().toLocaleDateString(uiLocale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

const EA_ROUTE_IC = {
  today: PB.home, attendance: LIC.list, homework: PB.book, quizzes: LIC.clip, marks: PB.chart, planning: PB.cal, materials: SPI.folder, classNotices: SPI.megaphone,
  chat: PB.chat, points: SPI.star, matrix: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z', pickup: PB.car, mine: LIC.warn, files: SPI.folder,
  notices: SPI.megaphone, approvals: LIC.check, support: PB.chat, notifications: LIC.bell,
  itdash: PB.home, results: 'M9 12l2 2 4-4M7 3h10a2 2 0 012 2v14a2 2 0 01-2 2H7a2 2 0 01-2-2V5a2 2 0 012-2z', timetable: PB.cal, idcards: 'M3 5h18a1 1 0 011 1v12a1 1 0 01-1 1H3a1 1 0 01-1-1V6a1 1 0 011-1zM6 10h5M6 14h3M15 10a2 2 0 100 .01M13 16c0-1.5 4-1.5 4 0', academic: PB.cap, noticesAdmin: SPI.megaphone, invitations: PB.mail, people: PB.users, rules: LIC.shield, connection: LIC.plug,
  restores: 'M3 12a9 9 0 109-9M3 4v5h5', incidents: LIC.warn, activity: LD_ICON.doc
};
/** The sidebar every role uses: maroon, white active pill, an icon on each item and the skyline at the foot. */
function eaaSide(route) {
  if (route === 'school' && S.me.role === 'EAA_ADMIN') route = 'sites';
  const role = S.me.role, teacher = role === 'TEACHER', it = isItRole(), eaa = role === 'EAA_ADMIN', own = teacher || it || eaa;
  const icon = r => EA_ROUTE_IC[r] || LIC.list;
  const items = teacher ? NAV.TEACHER.map(([r, label]) => [r, label, icon(r)])
    : it ? [['itdash', 'Dashboard'], ['people', 'Users & access'], ['academic', 'Academic setup'], ['timetable', 'Timetable'], ['attendance', 'Attendance'], ['marks', 'Marks'], ['results', 'Results'], ['idcards', 'ID cards'], ['noticesAdmin', 'Notices'], ['support', 'Support tickets'],
      ['invitations', 'Invitations'], ['approvals', 'Approvals'], ['rules', 'Rules'], ['connection', 'Connection'], ['restores', 'Restores'], ['incidents', 'Incidents'], ['activity', 'Activity log'], ['notifications', 'Notifications']].map(([r, label]) => [r, label, icon(r)])
    : eaa ? EA_NAV : (NAV[role] || []).map(([r, label]) => [r, label, icon(r)]);
  const link = ([r, label, p]) => r === '#' ? html`<div class="ea-lbl ea-grp">${label}</div>` : html`<a class="nav-item ${route === r ? 'nav-on' : ''} ${route === r && S.kpiFlash ? 'nav-flash' : ''}" href="#/${r}">${eaIc(p)}<span>${label}</span>${r === 'notifications' && S.unread ? html`<span class="count">${S.unread}</span>` : ''}${r === 'chat' && S.chatUnread ? html`<span class="count">${S.chatUnread}</span>` : ''}</a>`;
  return html`<aside class="sidebar ea-side"><a class="ea-brand" href="#/${home()}"><img src="app/eaa-logo.png" alt=""><span><b>Hafez</b><small lang="ar" dir="rtl">حافظ</small></span></a>
      <div class="ea-lbl">${eaa ? 'EAA IT' : ROLE_LABEL[role]}</div>
      ${own ? '' : html`<div class="ea-whoami"><b>${S.me.displayName}</b><small>${personSite(S.me) || ''}</small></div>`}
      <nav class="nav">${items.map(link)}
        <div class="nav-sep"></div>
        <a class="nav-item ${route === 'prefs' ? 'nav-on' : ''}" href="#/prefs">${eaIc(SPI.gear)}<span>My preferences</span></a>
        <button class="nav-item nav-lang" data-act="setLang">${eaIc(PB.globe)}<span>${langLabel()}</span></button>
        <button class="nav-item" data-act="signout">${eaIc('M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9')}<span>Sign out</span></button></nav>${raw(SIDE_SKY)}</aside>`;
}

function eaaShell(route, body) {
  return html`<div class="frame eaa">${eaaSide(route)}<div class="content"><main class="page ea" id="main">${body}</main></div></div>`;
}

const eaTools = () => html`<div class="ea-tools"><button type="button" class="ea-pill" data-act="setLang">${pbGlobe} ${langLabel()}</button>
  <a class="ea-pill ea-bell" href="#/notifications" aria-label="Notifications">${eaIc(LIC.bell)}${S.unread ? html`<span>${S.unread}</span>` : ''}</a>
  <a class="ea-user" href="#/prefs">${avatar((S.me.displayName || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase())}<span><b>${S.me.displayName}</b><small>${ROLE_LABEL[S.me.role]}</small></span></a></div>`;

function eaaPage(heading, sub, body, actions, kpis) {
  const atHome = S.route === 'netoverview' || S.route === 'today' || S.route === 'itdash';
  const crumb = atHome ? eaToday() : S.route === 'school' && isEaa() ? html`<a href="#/sites">Schools &amp; settings</a> › ${heading}` : html`<a href="#/${home()}">${S.me.role === 'TEACHER' ? 'Home' : isItRole() ? 'Dashboard' : 'Overview'}</a> › ${heading}`;
  const tile = ([label, value, unit, tone, ic, note, go]) => {
    const inner = html`<span class="ea-k-ic">${lico(LIC[ic] || LIC.list)}</span><b>${value}${unit ? html`<i>${unit}</i>` : ''}</b><div>${label}</div><small>${note}</small>`;
    return go ? html`<a class="ea-k ea-k-${tone} ea-k-link" href="${go}">${inner}</a>` : html`<div class="ea-k ea-k-${tone}">${inner}</div>`;
  };
  return html`<div class="ea-wrap ${S.route === 'prefs' ? 'ea-cols' : ''}"><header class="ea-head"><div class="ea-title"><small>${crumb}</small><h1>${heading}</h1>${sub ? html`<p>${sub}</p>` : ''}</div>${eaTools()}</header>
    ${actions ? html`<div class="ea-actions">${actions}</div>` : ''}
    ${kpis && kpis.length && !['sites', 'activity', 'prefs', 'itaccounts'].includes(S.route) ? html`<section class="ea-kpis">${kpis.map(tile)}</section>` : ''}
    <div class="ea-stack">${body}</div></div>`;
}

const eaaCard = (heading, sub, body, extra) => html`<section class="ea-card"><div class="ea-ch"><div><h2><i></i>${heading}</h2>${sub ? html`<small>${sub}</small>` : ''}</div>${extra ? html`<div class="ea-cx">${extra}</div>` : ''}</div>${body}</section>`;

const eaCode = s => (s.name || '?').split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
const eaWho = (name, sub) => html`<div class="ea-who"><span class="ea-code">${eaCode({ name })}</span><div><b>${name}</b><small>${sub || ''}</small></div></div>`;
const eaStatus = a => a ? badge('b-good', 'Active') : badge('b-neutral', 'Inactive');

/** A search box plus any number of dropdown filters. Each dropdown names the row attribute it matches (data-attr). */
const eaBar = (table, searchHolder, selects) => html`<div class="ea-toolbar"><label class="ea-search">${sIc('search')}<input type="search" placeholder="${searchHolder}" data-filter="${table}" autocomplete="off"></label>
  ${selects.map(([label, attr, opts]) => html`<label class="ea-sel"><span>${label}</span><select data-fsel="${table}" data-attr="${attr}"><option value="">Any</option>${opts.map(([v, t]) => html`<option value="${v}">${t}</option>`)}</select></label>`)}</div>`;

/** Schools: searchable, filtered by status, with head counts. Pressing a row opens that school. */
function eaaSchools(list) {
  const rows = list.map(s => ({ go: '#/school/' + s.id, status: s.active ? 'active' : 'inactive', cells: [eaWho(s.name, s.domain), eaStatus(s.active), s.principalName || '—', s.students, s.staff, s.scored ? 'Yes' : 'No'] }));
  return html`${eaBar('#eaSchools', 'Search by name or domain', [['Status', 'status', [['active', 'Active'], ['inactive', 'Inactive']]]])}
    ${eaaCard('All schools', null, eaTable('eaSchools', ['School', 'Status', 'Principal', 'Students', 'Staff', 'Counted in KPIs'], rows))}`;
}

/** A table whose rows can be pressed (data-go) and filtered (data-status, data-school ...). */
function eaTable(id, cols, rows) {
  if (!rows.length) return empty('Nothing to show.');
  return html`<div class="scroll-x"><table class="t" id="${id}"><thead><tr>${cols.map(c => html`<th>${c}</th>`)}</tr></thead><tbody>${rows.map(r => html`<tr class="${r.go ? 'click' : ''}" ${r.go ? raw(`data-go="${esc(r.go)}"`) : ''} ${raw(Object.entries(r.attrs || {}).concat(r.status ? [['status', r.status]] : []).map(([k, v]) => `data-${k}="${esc(String(v))}"`).join(' '))}>${r.cells.map(c => html`<td>${c}</td>`)}</tr>`)}</tbody></table></div>`;
}

SCREENS.netoverview = async () => {
  const [incidents, log, list] = await Promise.all([get('/api/dashboard/incidents').catch(() => []), get('/api/network/log?size=4').catch(() => ({ rows: [], total: 0 })), get('/api/network/schools')]);
  const week = await get('/api/network/log?size=1&from=' + new Date(Date.now() - 7 * 864e5).toISOString().slice(0, 10)).then(r => r.total).catch(() => 0);
  const itCount = list.reduce((n, s) => n + s.itAccounts, 0);
  const on = list.filter(s => s.active), counted = list.filter(s => s.scored), open = incidents.filter(i => !i.resolvedAt), waiting = list.filter(s => s.active && !s.firstCheckAt);
  const banner = html`<section class="ea-banner"><img src="app/assalam-school.jpg" alt=""><div class="ea-banner-in"><span class="pb-badge">Assalam Schools network</span>
      <div class="ea-ban-n"><b>${on.length}</b><span>/ ${list.length}</span></div><h2>Schools active</h2><p>${open.length ? 'Incidents need attention' : 'No open incidents'}</p>
      <div class="pb-cta"><a class="pb-btn pb-btn-light" href="#/sites">All schools</a><a class="pb-btn pb-btn-ghost" href="#/incidents">Incidents</a></div></div></section>`;
  const tasks = [
    ...open.slice(0, 3).map(i => html`<a class="ea-task" href="#/incidents"><span>${eaIc(LIC.warn)}</span><div><b>${title(i.type)}</b><small>${fmt(i.detectedAt)}</small></div><i>Review</i></a>`),
    ...waiting.slice(0, 3).map(s => html`<a class="ea-task" href="#/school/${s.id}"><span>${eaIc(LIC.clock)}</span><div><b>${s.name}</b><small>Waiting for the first check</small></div><i>Open</i></a>`)
  ];
  const recent = log.rows.map(e => html`<a class="ea-task" href="#/activity"><span>${eaIc(LD_ICON.doc)}</span><div><b>${e.action}</b><small>${e.actorName || 'System'} · ${fmt(e.at)}</small></div></a>`);
  const glance = list.map(s => ({ go: '#/school/' + s.id, cells: [eaWho(s.name, s.domain), eaStatus(s.active), s.principalName || '—', s.students, s.staff, s.firstCheckAt ? fmtDate(s.firstCheckAt) : '—'] }));
  return page('Network overview', null, html`
    <div class="ea-two">${eaaCard('Open items', null, tasks.length ? html`<div class="ea-tasks">${tasks}</div>` : empty('Nothing needs attention.'), html`<a href="#/incidents">Incidents →</a>`)}
      ${eaaCard('Recent network activity', null, recent.length ? html`<div class="ea-tasks">${recent}</div>` : empty('No activity yet.'), html`<a href="#/activity">Activity log →</a>`)}</div>
    ${eaaCard('Schools at a glance', 'Press a school to see its people and change its status.', eaTable('eaGlance', ['School', 'Status', 'Principal', 'Students', 'Staff', 'First check'], glance), html`<a href="#/sites">All schools →</a>`)}`, banner,
    [['Schools', list.length, '', 'brand', 'school', on.length + ' switched on', '#/sites'], ['Counted in KPIs', counted.length, '', 'good', 'check', 'Scored', '#/sites'],
     ['Open incidents', open.length, '', open.length ? 'bad' : 'good', 'warn', open.length ? 'Need attention' : 'All clear', '#/incidents'], ['School IT accounts', itCount, '', 'slate', 'shield', 'Across the schools', '#/itaccounts'],
     ['Activity this week', week, '', 'watch', 'list', 'Changes recorded', '#/activity']]);
};

// ---- Schools & settings (EAA IT): the list, then one school's page
const eaSitesOld = SCREENS.sites;
SCREENS.sites = async (...a) => {
  if (!isEaa()) return eaSitesOld(...a);
  invalidate('sites');
  const list = await get('/api/network/schools');
  return page('Schools & settings', 'The schools in the network. Press a school to see its people and change its status.', html`${eaaSchools(list)}
    ${card('Add school', null, html`<form data-form="site" class="row"><input class="input" name="name" placeholder="School name" required><input class="input" name="domain" placeholder="school.edu" required><label><input type="checkbox" name="scored" checked> Counted in the KPIs</label><button class="btn btn-primary">Add school</button></form>`)}`);
};

const eaSchoolOld = SCREENS.school;   // the Principal's own home screen has the same route name
SCREENS.school = async (arg, q) => {
  if (!isEaa()) return eaSchoolOld(arg, q);
  const d = await get('/api/network/schools/' + arg), s = d.school;
  const principal = s.principalName ? html`<div class="ea-who"><span class="ea-code">${eaCode({ name: s.principalName })}</span><div><b>${s.principalName}</b><small>${s.principalEmail || ''}</small></div></div>` : empty('No principal yet.');
  return page(s.name, s.domain, html`
    <div class="ea-two">
      ${eaaCard('School status', null, html`<div class="ea-statusbox">${eaStatus(s.active)}<p>${s.active ? 'This school is switched on. Its people can sign in.' : 'This school is switched off.'}</p>
        ${s.headOffice ? html`<small class="muted">The head office cannot be switched off.</small>` : html`<button class="btn ${s.active ? 'btn-danger' : 'btn-primary'}" data-act="netSchoolStatus" data-id="${s.id}" data-value="${!s.active}" data-confirm="${s.active ? 'Switch this school off?' : 'Switch this school on?'}">${s.active ? 'Switch off' : 'Switch on'}</button>`}</div>`)}
      ${eaaCard('Principal', null, principal)}
    </div>
    ${eaaCard('People by role', null, d.roles.length ? eaTable('eaRoles', ['Role', 'People'], d.roles.map(r => ({ cells: [ROLE_LABEL[r.role] || title(r.role), r.count] }))) : empty('No people yet.'))}
    ${eaaCard('School actions', null, html`<div class="row"><a class="btn" href="#/site/${s.id}">View score</a><a class="btn" href="#/itaccounts?school=${s.id}">School IT accounts</a><a class="btn" href="#/activity?school=${s.id}">Activity log</a>
      <button class="btn" data-act="renameSchool" data-id="${s.id}" data-name="${s.name}">Rename</button>
      <button class="btn ${s.scored ? '' : 'btn-primary'}" data-act="toggleScored" data-id="${s.id}" data-value="${!s.scored}">${s.scored ? 'Exclude from KPIs' : 'Include in KPIs'}</button>
      ${s.headOffice ? '' : html`<button class="btn btn-danger" data-act="removeSchool" data-id="${s.id}" data-name="${s.name}">Remove</button>`}</div>`)}`, '',
    [['Students', s.students, '', 'brand', 'users', 'In this school'], ['Staff', s.staff, '', 'slate', 'briefcase', 'Teachers, coordinators and more'], ['Parents', s.parents, '', 'good', 'users', 'Linked to students'], ['School IT accounts', s.itAccounts, '', 'watch', 'shield', 'Active', '#/itaccounts?school=' + s.id]]);
};
LIC.users = LIC.users || PB.users; LIC.briefcase = LIC.briefcase || SPI.briefcase;

// ---- School IT accounts: create, suspend, reactivate, delete
const IT_LABEL = { ACTIVE: 'Active', NEW: 'Invited', LEAVING: 'Leaving', LEFT: 'Suspended' };
const itBadge = st => badge(st === 'ACTIVE' ? 'b-good' : st === 'LEFT' ? 'b-bad' : 'b-neutral', IT_LABEL[st] || title(st));
SCREENS.itaccounts = async (arg, q) => {
  if (!isEaa()) { location.hash = '#/' + home(); return ''; }
  const [list, schools] = await Promise.all([get('/api/network/it-accounts'), get('/api/network/schools')]);
  const rows = list.map(a => ({ attrs: { status: a.status, school: a.siteId }, cells: [eaWho(a.name, a.email), a.siteName, itBadge(a.status),
    html`<div class="ea-row-act">${a.status === 'LEFT' ? html`<button class="btn btn-sm" data-act="itStatus" data-id="${a.id}" data-value="ACTIVE">Reactivate</button>` : html`<button class="btn btn-sm" data-act="itStatus" data-id="${a.id}" data-value="LEFT" data-confirm="Suspend this account? They will no longer be able to sign in.">Suspend</button>`}
      <button class="btn btn-sm btn-danger" data-act="itDelete" data-id="${a.id}" data-confirm="Delete this account permanently?">Delete</button></div>`] }));
  const school = q.get('school') || '';
  return page('School IT accounts', 'Create, suspend or delete the IT account of each school.', html`
    ${eaBar('#eaIt', 'Search by name or e-mail', [['School', 'school', schools.map(s => [s.id, s.name])], ['Status', 'status', [['ACTIVE', 'Active'], ['LEFT', 'Suspended'], ['NEW', 'Invited']]]])}
    ${eaaCard('All School IT accounts', list.length + ' in the network', eaTable('eaIt', ['Account', 'School', 'Status', ''], rows))}
    ${eaaCard('Create a School IT account', 'The person signs in with their school e-mail.', html`<form data-form="itCreate" class="row">
      <select class="input" name="siteId" required>${schools.filter(s => s.active).map(s => html`<option value="${s.id}" ${s.id === school ? raw('selected') : ''}>${s.name}</option>`)}</select>
      <input class="input" name="name" placeholder="Full name" required><input class="input" type="email" name="email" placeholder="name@school.edu" required><button class="btn btn-primary">Create account</button></form>`)}`);
};

// ---- Activity log: who did it, in a period, for a school, an action or an area, with search
const eaLogOld = SCREENS.activity;
SCREENS.activity = async (arg, q) => {
  if (!isEaa() && !isItRole()) return eaLogOld(arg, q);
  const keys = ['from', 'to', 'school', 'action', 'area', 'q'], pageNo = Number(q.get('page') || 0), params = new URLSearchParams();
  keys.forEach(k => { if (q.get(k)) params.set(k, q.get(k)); });
  const val = k => q.get(k) || '';
  const [pg, schools] = await Promise.all([get('/api/network/log?size=50&page=' + pageNo + '&' + params), isEaa() ? get('/api/network/schools') : Promise.resolve([])]);
  const link = n => '#/activity?' + new URLSearchParams([...params.entries(), ['page', n]]);
  const rows = pg.rows.map(e => ({ cells: [fmt(e.at), html`<div class="ea-who"><span class="ea-code">${e.actorName ? eaCode({ name: e.actorName }) : '·'}</span><div><b>${e.actorName || 'System'}</b><small>${e.actorRole ? ROLE_LABEL[e.actorRole] || title(e.actorRole) : 'Automatic'}</small></div></div>`,
    e.action, html`${e.entityType}${e.targetName ? html`<small class="ea-sub">${e.targetName}</small>` : ''}`, e.schoolName || '—', [e.before, e.after].filter(Boolean).join(' → ')] }));
  const sel = (name, list) => html`<label class="ea-sel"><span>${name[1]}</span><select name="${name[0]}"><option value="">Any</option>${list.map(([v, t]) => html`<option value="${v}" ${v === val(name[0]) ? raw('selected') : ''}>${t}</option>`)}</select></label>`;
  const pages = Math.max(1, Math.ceil(pg.total / 50));
  return page('Activity log', isEaa() ? 'Every change, who made it and when. Filter by period, school, action or area.' : 'Every change in your school, who made it and when. Filter by period, action or area.', html`
    <form data-form="eaLog" class="ea-toolbar ea-logbar"><label class="ea-search">${sIc('search')}<input type="search" name="q" value="${val('q')}" placeholder="Search by person, action or detail" autocomplete="off"></label>
      <label class="ea-sel"><span>From</span><input type="date" name="from" value="${val('from')}"></label><label class="ea-sel"><span>To</span><input type="date" name="to" value="${val('to')}"></label>
      ${isEaa() ? sel(['school', 'School'], schools.map(s => [s.id, s.name])) : ''}${sel(['action', 'Action'], pg.actions.map(a => [a, a]))}${sel(['area', 'Area'], pg.areas.map(a => [a, a]))}
      <button class="btn btn-primary">Apply</button><a class="btn" href="#/activity">Clear</a></form>
    ${eaaCard('Events', pg.total + ' found', pg.rows.length ? eaTable('eaLog', ['When', 'Who', 'Action', 'About', 'School', 'Change'], rows) : empty('No events match these filters.'),
      html`<span id="verify"></span><button class="btn btn-sm" data-act="verifyLog">Verify the log</button> <a class="btn btn-sm" href="${dl('/api/activity-log/download')}">Download</a>`)}
    ${pages > 1 ? html`<div class="ea-pager">${pageNo > 0 ? html`<a class="btn btn-sm" href="${link(pageNo - 1)}">‹ Previous</a>` : ''}<span>Page ${pageNo + 1} of ${pages}</span>${pageNo + 1 < pages ? html`<a class="btn btn-sm" href="${link(pageNo + 1)}">Next ›</a>` : ''}</div>` : ''}`);
};

Object.assign(ACT, {
  netSchoolStatus: async d => { await put('/api/network/schools/' + d.id + '/status', { active: d.value === 'true' }); invalidate('sites'); await refreshAfter(d.value === 'true' ? 'School switched on.' : 'School switched off.'); },
  itStatus: async d => { await put('/api/network/it-accounts/' + d.id + '/status', { status: d.value }); await refreshAfter(d.value === 'ACTIVE' ? 'Account reactivated.' : 'Account suspended.'); },
  itDelete: async d => { await del('/api/network/it-accounts/' + d.id); await refreshAfter('Account deleted.'); }
});
Object.assign(FORM, {
  itCreate: async f => { await post('/api/network/it-accounts', { siteId: f.siteId, name: f.name, email: f.email }); await refreshAfter('School IT account created.'); },
  eaLog: async f => { const p = new URLSearchParams(); Object.entries(f).forEach(([k, v]) => { if (v) p.set(k, v); }); location.hash = '#/activity' + (p.toString() ? '?' + p : ''); }
});

// search and dropdown filters for the tables on these screens
function eaFilter(el) {
  const bar = el.closest('.ea-toolbar'); if (!bar) return;
  const box = bar.querySelector('[data-filter]'), table = (box || bar.querySelector('[data-fsel]')).dataset.filter || bar.querySelector('[data-fsel]').dataset.fsel;
  const text = ((box || {}).value || '').trim().toLowerCase(), sels = [...bar.querySelectorAll('[data-fsel]')].filter(x => x.value);
  document.querySelectorAll(table + ' tbody tr').forEach(tr => { tr.hidden = (text && !tr.textContent.toLowerCase().includes(text)) || sels.some(x => tr.dataset[x.dataset.attr] !== x.value); });
}
document.addEventListener('input', e => { if (e.target.matches && e.target.matches('[data-filter]')) eaFilter(e.target); });
document.addEventListener('change', e => { if (e.target.matches && e.target.matches('[data-fsel]')) eaFilter(e.target); });


// ---- Teacher home: the day at a glance
const eaTodayOld = SCREENS.today;
SCREENS.today = async (...a) => {
  if (!S.me || S.me.role !== 'TEACHER') return eaTodayOld(...a);
  const [t, notes] = await Promise.all([get('/api/portal/teacher'), get('/api/notices').catch(() => [])]);
  const taken = new Set(t.attendanceTaken), hour = new Date().getHours(), first = (S.me.displayName || '').split(' ')[0];
  const greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const lessons = t.lessons.slice().sort((x, y) => x.period - y.period), open = lessons.filter(l => !taken.has(l.sectionId)).length;
  const banner = html`<section class="ea-banner"><img src="app/assalam-school.jpg" alt=""><div class="ea-banner-in"><span class="pb-badge">Your day</span>
      <div class="ea-ban-n"><b>${t.schoolDay ? open : '–'}</b></div><h2>${!t.schoolDay ? 'Not a school day' : open ? 'Lessons without attendance yet' : 'Attendance is recorded'}</h2><p>${t.schoolDay ? lessons.length + ' · ' + trText('Lessons today') : trText('Attendance and lessons run Sunday to Thursday.')}</p>
      <div class="pb-cta"><a class="pb-btn pb-btn-light" href="#/attendance">Show attendance</a><a class="pb-btn pb-btn-ghost" href="#/marks">Enter marks</a></div></div></section>`;
  const sched = lessons.length ? html`<div class="ea-tasks">${lessons.map(l => html`<div class="ea-task ea-lesson"><span class="ea-per">${l.period}</span><div><b>${l.section} · ${l.subject}</b><small>${l.room ? (/^room/i.test(l.room) ? l.room : trText('Room') + ' ' + l.room) : ''}</small></div>
      ${taken.has(l.sectionId) ? html`<em class="ea-done">Attendance recorded</em>` : html`<a class="btn btn-sm btn-primary" href="#/attendance?s=${l.sectionId}">Show attendance</a>`}</div>`)}</div>` : empty(t.schoolDay ? 'No lessons today.' : 'Attendance and lessons run Sunday to Thursday.');
  const noteRows = notes.slice(0, 4).map(n => html`<a class="ea-task" href="#/notices"><span>${eaIc(SPI.megaphone)}</span><div><b>${say(n.titleEn, n.titleAr)}</b><small>${fmtDate(n.startsAt || n.createdAt)}</small></div></a>`);
  return page(trText(greet) + ', ' + first, null, html`
    <div class="ea-two">${eaaCard("Today's schedule", null, sched, html`<a href="#/attendance">Attendance →</a>`)}
      ${eaaCard('Notices', null, noteRows.length ? html`<div class="ea-tasks">${noteRows}</div>` : empty('No notices right now.'), html`<a href="#/notices">View all →</a>`)}</div>
    ${eaaCard('My sections', null, t.sections.length ? table(['Section', 'Students', 'Attendance today', ''], t.sections.map(x => ({ cells: [sectionLabel(x), x.students, taken.has(x.id) ? badge('b-good', 'Taken') : badge('b-watch', 'Not taken'), html`<a href="#/attendance?s=${x.id}">${openWord()}</a>`] }))) : empty('You are not assigned to a section yet. The coordinator assigns teachers in Academic setup.'))}`, banner,
    [['Lessons today', lessons.length, '', 'brand', 'clock', t.schoolDay ? 'From the timetable' : 'Day off', '#/attendance'], ['Attendance not recorded', open, '', open ? 'watch' : 'good', 'check', open ? 'The clerk records it' : 'All recorded', '#/attendance'],
     ['My sections', t.sections.length, '', 'slate', 'list', 'Assigned to you', '#/attendance'], ['Notices', notes.length, '', 'good', 'bell', 'From the school', '#/notices']]);
};


// ---- School IT home
SCREENS.itdash = async () => {
  if (!isItRole()) { location.hash = '#/' + home(); return ''; }
  const [people, incidents, approvals, log, sites] = await Promise.all([get('/api/people').catch(() => []), get('/api/dashboard/incidents').catch(() => []), get('/api/approvals').catch(() => []),
    get('/api/network/log?size=4').catch(() => ({ rows: [], total: 0 })), loadSites()]);
  const mine = (sites || {})[S.me.siteId] || {}, students = people.filter(p => p.role === 'STUDENT').length, staff = people.filter(p => !['STUDENT', 'PARENT', 'LEADERSHIP', 'EAA_ADMIN'].includes(p.role)).length;
  const open = incidents.filter(i => !i.resolvedAt), pending = approvals.filter(a => a.status === 'PENDING'), invited = people.filter(p => p.status === 'NEW').length;
  const hour = new Date().getHours(), greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const banner = html`<section class="ea-banner"><img src="app/assalam-school.jpg" alt=""><div class="ea-banner-in"><span class="pb-badge">${mine.name || 'Your school'}</span>
      <div class="ea-ban-n"><b>${open.length + pending.length}</b></div><h2>${open.length + pending.length ? 'Items need your attention' : 'Everything is in order'}</h2><p>${students} · ${trText('Students')} · ${staff} · ${trText('Staff')}</p>
      <div class="pb-cta"><a class="pb-btn pb-btn-light" href="#/people">People</a><a class="pb-btn pb-btn-ghost" href="#/academic">Academic setup</a></div></div></section>`;
  const followUp = [
    ...open.slice(0, 3).map(i => html`<a class="ea-task" href="#/incidents"><span>${eaIc(LIC.warn)}</span><div><b>${title(i.type)}</b><small>${fmt(i.detectedAt)}</small></div><i>Review</i></a>`),
    ...pending.slice(0, 3).map(a => html`<a class="ea-task" href="#/approvals"><span>${eaIc(LIC.check)}</span><div><b>${APPROVAL_LABEL[a.action] || a.action}</b><small>${a.requestedBy}</small></div><i>Review</i></a>`)
  ];
  const quick = [['Invite a user', '#/invitations', PB.mail], ['Academic setup', '#/academic', PB.cap], ['Post a notice', '#/noticesAdmin', SPI.megaphone], ['Rules', '#/rules', LIC.shield]];
  const recent = log.rows.map(e => html`<a class="ea-task" href="#/activity"><span>${eaIc(LD_ICON.doc)}</span><div><b>${e.action}</b><small>${e.actorName || 'System'} · ${fmt(e.at)}</small></div></a>`);
  return page(trText(greet) + ', ' + (S.me.displayName || '').split(' ')[0], null, html`
    <div class="ea-two">${eaaCard('Needs follow-up', null, followUp.length ? html`<div class="ea-tasks">${followUp}</div>` : empty('Nothing needs attention.'))}
      ${eaaCard('Quick actions', null, html`<div class="ea-quick">${quick.map(([t, h, p]) => html`<a href="${h}"><span>${eaIc(p)}</span>${t}</a>`)}</div>`)}</div>
    ${eaaCard('Recent activity', null, recent.length ? html`<div class="ea-tasks">${recent}</div>` : empty('No activity yet.'), html`<a href="#/activity">Activity log →</a>`)}`, banner,
    [['Students', students, '', 'brand', 'users', 'In this school', '#/people'], ['Staff', staff, '', 'slate', 'briefcase', 'Teachers and admins', '#/people'],
     ['Open incidents', open.length, '', open.length ? 'bad' : 'good', 'warn', open.length ? 'Need attention' : 'All clear', '#/incidents'], ['Waiting approvals', pending.length, '', pending.length ? 'watch' : 'good', 'check', 'Need a decision', '#/approvals'],
     ['Not yet welcomed', invited, '', 'watch', 'list', 'Invited, not activated', '#/invitations']]);
};

// ---- long tables get a search box in their card, so any list can be narrowed
function eaEnhance() {
  if (!isNewLook()) return;
  document.querySelectorAll('#main .ea-card').forEach(card => {
    const t = card.querySelector('table.t'); if (!t || card.querySelector('.ea-mini') || t.id) return;
    if (t.querySelectorAll('tbody tr').length < 6) return;
    const head = card.querySelector('.ea-ch'); if (!head) return;
    const box = document.createElement('label'); box.className = 'ea-mini';
    box.innerHTML = '<input type="search" placeholder="' + esc(trText('Search')) + '" autocomplete="off">';
    head.appendChild(box);
    box.querySelector('input').addEventListener('input', ev => { const q = ev.target.value.trim().toLowerCase(); t.querySelectorAll('tbody tr').forEach(tr => { tr.hidden = !!q && !tr.textContent.toLowerCase().includes(q); }); });
  });
}

Object.assign(AR, {
  'Open items': 'عناصر مفتوحة',
  'Network overview': 'نظرة عامة على الشبكة', 'Assalam Schools network': 'شبكة مدارس السلام', 'Schools active': 'مدارس نشطة',
  'Incidents need attention': 'حوادث تحتاج إلى اهتمام', 'No open incidents': 'لا توجد حوادث مفتوحة', 'Nothing needs attention.': 'لا شيء يحتاج إلى اهتمام.',
  'Recent network activity': 'نشاط الشبكة الأخير', 'No activity yet.': 'لا يوجد نشاط بعد.', 'Schools at a glance': 'المدارس بنظرة سريعة', 
  'Incidents →': 'الحوادث ←', 'Activity log →': 'سجل النشاط ←',
  'Search by name or domain': 'ابحث بالاسم أو النطاق', 'Any': 'الكل', 'Activity this week': 'نشاط هذا الأسبوع',
  'Changes recorded': 'تغييرات مسجلة',
  'School IT accounts': 'حسابات تقنية المعلومات بالمدارس', 'Create, suspend or delete the IT account of each school.': 'أنشئ حساب تقنية المعلومات لكل مدرسة أو أوقفه أو احذفه.',
  'Search by name or e-mail': 'ابحث بالاسم أو البريد', 'Account': 'الحساب', 'Invited': 'مدعو', 'Suspended': 'موقوف', 'Suspend': 'إيقاف', 'Reactivate': 'إعادة تفعيل',
  'Suspend this account? They will no longer be able to sign in.': 'إيقاف هذا الحساب؟ لن يتمكن صاحبه من تسجيل الدخول.', 'Delete this account permanently?': 'حذف هذا الحساب نهائياً؟',
  'All School IT accounts': 'كل حسابات تقنية المعلومات بالمدارس', 'Create a School IT account': 'إنشاء حساب تقنية معلومات لمدرسة', 'The person signs in with their school e-mail.': 'يسجّل الشخص الدخول ببريد مدرسته.',
  'Create account': 'إنشاء الحساب', 'School IT account created.': 'تم إنشاء الحساب.', 'Account reactivated.': 'تمت إعادة تفعيل الحساب.', 'Account suspended.': 'تم إيقاف الحساب.', 'Account deleted.': 'تم حذف الحساب.',
  'Search by person, action or detail': 'ابحث بالشخص أو الإجراء أو التفاصيل',
  'The schools in the network. Press a school to see its people and change its status.': 'مدارس الشبكة. اضغط على مدرسة لترى أعدادها وتغيّر حالتها.', 'Press a school to see its people and change its status.': 'اضغط على مدرسة لترى أعدادها وتغيّر حالتها.',
  'School status': 'حالة المدرسة', 'This school is switched on. Its people can sign in.': 'المدرسة مفعّلة ويستطيع أفرادها تسجيل الدخول.', 'This school is switched off.': 'المدرسة موقوفة.', 'The head office cannot be switched off.': 'لا يمكن إيقاف المقر الرئيسي.',
  'Switch off': 'إيقاف', 'Switch on': 'تشغيل', 'Switch this school off?': 'إيقاف هذه المدرسة؟', 'Switch this school on?': 'تشغيل هذه المدرسة؟', 'School switched on.': 'تم تشغيل المدرسة.', 'School switched off.': 'تم إيقاف المدرسة.',
  'People by role': 'الأفراد حسب الدور', 'No people yet.': 'لا يوجد أفراد بعد.', 'No principal yet.': 'لا يوجد مدير بعد.', 'School actions': 'إجراءات المدرسة', 'Exclude from KPIs': 'استبعاد من المؤشرات', 'Include in KPIs': 'إدراج في المؤشرات',
  'In this school': 'في هذه المدرسة', 'Teachers, coordinators and more': 'معلمون ومنسقون وغيرهم', 'Linked to students': 'مرتبطون بالطلاب', 'Across the schools': 'عبر المدارس', 'switched on': 'مفعّلة', 'Principal ': 'المدير ',
  'Every change, who made it and when. Filter by period, school, action or area.': 'كل تغيير ومن أجراه ومتى. صفِّ حسب الفترة أو المدرسة أو الإجراء أو المجال.', 'Area': 'المجال', 'Apply': 'تطبيق', 
  'Events': 'الأحداث', 'System': 'النظام', 'Automatic': 'تلقائي', 'No events match these filters.': 'لا توجد أحداث تطابق هذه المرشحات.', 
  'Your day': 'يومك', 'Lessons without attendance yet': 'حصص لم يُسجَّل حضورها بعد', 'Attendance is recorded': 'الحضور مكتمل',
  'Lessons today': 'حصص اليوم', 'Enter marks': 'إدخال الدرجات', "Today's schedule": 'جدول اليوم', 'Attendance taken': 'تم تسجيل الحضور',
  'Attendance →': 'الحضور ←', 'Attendance to take': 'حضور مطلوب', 'From the timetable': 'من الجدول', 'Day off': 'يوم عطلة', 'Assigned to you': 'مسندة إليك', 'From the school': 'من المدرسة', 'Before end of day': 'قبل نهاية اليوم', 'All taken': 'تم الكل', 
  'Items need your attention': 'عناصر تحتاج إلى اهتمامك', 'Everything is in order': 'كل شيء على ما يرام', 'Invite a user': 'دعوة مستخدم',
  'Post a notice': 'نشر إعلان', 'Waiting approvals': 'موافقات بالانتظار', 'Need a decision': 'تحتاج إلى قرار', 'Not yet welcomed': 'لم يُرحَّب بهم بعد', 'Invited, not activated': 'مدعوون ولم يفعّلوا',
  'Your school': 'مدرستك', 'Teachers and admins': 'معلمون ومسؤولون', 'Every change in your school, who made it and when. Filter by period, action or area.': 'كل تغيير في مدرستك ومن أجراه ومتى. صفِّ حسب الفترة أو الإجراء أو المجال.', 'Search': 'بحث'
});

