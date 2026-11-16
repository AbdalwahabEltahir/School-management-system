/* Hafez: school metrics and reports, school achievements, teacher performance (indicators, warnings, investigations),
   clearer trend charts, the head teacher's "at a glance" strip, the student's tasks for today and the points pop.
   Loaded after app.js and uses its helpers (html, page, card, table, get, post, …). */
'use strict';

// =============================================================== shared pieces
/** The key measures, in the order they are shown: field, label, what it measures. */
const MEASURES = [
  ['attendancePct', 'Attendance', 'Students present, last 30 days'],
  ['gradePct', 'Average grade', 'All published marks'],
  ['homeworkPct', 'Homework done', 'Homework marked done, last 30 days'],
  ['quizPct', 'Quiz average', 'Average quiz score'],
  ['weeklyPlansPct', 'Weekly plans', 'Teachers\' weekly plans submitted, last 4 weeks'],
  ['healthScore', 'File health', 'Files kept safely, out of 100']
];
const toneOf = v => v == null ? 'none' : v >= 85 ? 'good' : v >= 70 ? 'watch' : 'bad';
const pctText = v => v == null ? '—' : v + '%';
/** A percentage with a coloured bar, for tables. */
const pctBar = v => html`<div class="mx-bar mx-${toneOf(v)}"><span><i style="width:${v == null ? 0 : Math.max(2, Math.min(100, v))}%"></i></span><b>${pctText(v)}</b></div>`;
/** A big measure tile: value, label and a plain sentence of what it means. */
const measureTile = (m, key, label, hint, go) => {
  const v = m[key], t = toneOf(v);
  const inner = html`<div class="mx-tile mx-${t}"><div class="mx-tile-top"><span>${label}</span><i>${t === 'good' ? 'Strong' : t === 'watch' ? 'Watch' : t === 'bad' ? 'Needs attention' : 'No data yet'}</i></div>
    <b ${v == null ? '' : raw(`data-count="${v}" data-suffix="%"`)}>${pctText(v)}</b><div class="mx-ring"><i style="width:${v == null ? 0 : Math.min(100, v)}%"></i></div><small>${hint}</small></div>`;
  return go ? html`<a class="kpi-link" href="${go}" data-kpi="1">${inner}</a>` : inner;
};
const monthLabel = m => toDate(m + '-01').toLocaleDateString(uiLocale(), { month: 'short' });

// =============================================================== trend charts: clear lines, labelled axes, a range to choose
const RANGES = [[30, '1 month'], [90, '3 months'], [180, '6 months'], [365, '1 year']];
const rangeOf = q => { const r = Number(q && q.get('range')); return RANGES.some(x => x[0] === r) ? r : 90; };
const rangeSeg = (route, current) => html`<div class="tr-range" role="tablist">${RANGES.map(([d, l]) => html`<a role="tab" aria-selected="${d === current}" class="${d === current ? 'on' : ''}" href="#/${route}?range=${d}">${l}</a>`)}</div>`;

/**
 * A line chart. series: [{ name, colour, values: [number|null] }]; labels: one per point. max: top of the scale
 * (100 for percentages). Gaps (null) break the line. Every point has a tooltip; the last value is written out.
 */
function lineChart({ labels, series, max = 100, unit = '%', height = 240, width }) {
  // the drawing is about as wide as the screen shows it, so its text stays a normal size on any screen
  width = width || Math.max(640, Math.min(1500, (window.innerWidth || 1200) - 380));
  height = Math.round(height * Math.max(1, width / 900) * 0.9) || height;
  const n = labels.length;
  if (n < 2 || !series.some(s => s.values.filter(v => v != null).length >= 1)) return empty('Not enough history yet. The chart fills in as days pass.');
  const W = width, H = height, L = 44, R = 56, T = 14, B = 30;
  const x = i => L + i * (W - L - R) / (n - 1), y = v => T + (1 - Math.max(0, Math.min(max, v)) / max) * (H - T - B);
  const ticks = [0, .25, .5, .75, 1].map(f => Math.round(max * f));
  const every = Math.max(1, Math.ceil(n / 8));
  // end-of-line labels never sit on top of each other: each keeps at least 15 units from the one above
  const ends = series.map((s, si) => { const vs = s.values.map((v, i) => v == null ? null : [i, v]).filter(Boolean); const l = vs[vs.length - 1]; return l ? { si, y: y(l[1]) } : null; }).filter(Boolean).sort((a, b) => a.y - b.y);
  const labelY = {};
  ends.forEach((e, k) => { labelY[e.si] = k && e.y - labelY[ends[k - 1].si] < 15 ? labelY[ends[k - 1].si] + 15 : e.y; });
  const paths = series.map((s, si) => {
    let d = '', open = false;
    s.values.forEach((v, i) => { if (v == null) { open = false; return; } d += (open ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1) + ' '; open = true; });
    const pts = s.values.map((v, i) => v == null ? null : [i, v]).filter(Boolean);
    const last = pts[pts.length - 1];
    const area = si === 0 && pts.length > 1 ? html`<path d="${'M' + x(pts[0][0]).toFixed(1) + ' ' + (H - B) + ' ' + pts.map(([i, v]) => 'L' + x(i).toFixed(1) + ' ' + y(v).toFixed(1)).join(' ') + ' L' + x(last[0]).toFixed(1) + ' ' + (H - B) + ' Z'}" fill="${s.colour}" fill-opacity=".1" stroke="none"/>` : '';
    const dots = pts.length <= 60 ? pts.map(([i, v]) => html`<circle cx="${x(i).toFixed(1)}" cy="${y(v).toFixed(1)}" r="${i === last[0] ? 5 : 3}" fill="${i === last[0] ? s.colour : '#fff'}" stroke="${s.colour}" stroke-width="2"><title>${s.name + ' · ' + labels[i] + ': ' + Math.round(v) + unit}</title></circle>`) : '';
    return html`${area}<path d="${d}" fill="none" stroke="${s.colour}" stroke-width="2.6" stroke-linejoin="round" stroke-linecap="round" class="tr-line"/>${dots}
      ${last ? html`<text x="${(x(last[0]) + 9).toFixed(1)}" y="${((labelY[si] != null ? labelY[si] : y(last[1])) + 4).toFixed(1)}" class="tr-last" fill="${s.colour}">${Math.round(last[1]) + unit}</text>` : ''}`;
  });
  return html`<div class="tr"><svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${series.map(s => s.name).join(', ')}">
    ${ticks.map(t => html`<line x1="${L}" x2="${W - R}" y1="${y(t).toFixed(1)}" y2="${y(t).toFixed(1)}" class="tr-grid"/><text x="${L - 8}" y="${(y(t) + 4).toFixed(1)}" class="tr-y">${t}${unit}</text>`)}
    ${labels.map((l, i) => i % every === 0 || i === n - 1 ? html`<text x="${x(i).toFixed(1)}" y="${H - 8}" class="tr-x">${l}</text>` : '')}
    ${paths}</svg>
    ${series.length > 1 ? html`<div class="tr-legend">${series.map(s => html`<span><i style="background:${s.colour}"></i>${s.name}</span>`)}</div>` : ''}</div>`;
}

/** File-health score over time: daily for a month, weekly averages for longer ranges. points: [{date, score}] */
function healthChart(points, range, width) {
  const pts = (points || []).slice().sort((a, b) => a.date < b.date ? -1 : 1);
  let labels, values;
  if (range <= 31) {
    labels = pts.map(p => fmtDayShort(p.date)); values = pts.map(p => p.score);
  } else {
    const weeks = {};
    pts.forEach(p => { const k = weekStart(p.date); (weeks[k] = weeks[k] || []).push(p.score); });
    const keys = Object.keys(weeks).sort();
    labels = keys.map(k => fmtDayShort(k)); values = keys.map(k => weeks[k].reduce((a, b) => a + b, 0) / weeks[k].length);
  }
  const first = values.find(v => v != null), last = values[values.length - 1];
  const verdict = values.length >= 2 ? html`<p class="tr-verdict tr-${last > first + 1 ? 'up' : last < first - 1 ? 'down' : 'flat'}"><b>${last > first + 1 ? '▲ Improving' : last < first - 1 ? '▼ Getting worse' : '● Steady'}</b> ${'from'} ${Math.round(first)} ${'to'} ${Math.round(last)} ${'over this period.'}</p>` : '';
  return html`${verdict}${lineChart({ labels, series: [{ name: 'File health score', colour: '#8f2447', values }], max: 100, unit: '', width: width || 560, height: width && width < 500 ? 230 : 240 })}`;
}

// =============================================================== Leadership: every school, outstanding schools, achievements
const medal = i => ['🥇', '🥈', '🥉'][i] || '';
function outstandingCard(schools, link) {
  const ranked = schools.filter(s => s.overall != null).slice(0, 3);
  if (!ranked.length) return '';
  return card('Outstanding schools', 'Ranked by the average of their key percentages.', html`<div class="os-podium">${ranked.map((s, i) => html`<a class="os-place os-p${i + 1}" href="#/schoolReport/${s.siteId}">
      <span class="os-medal" aria-hidden="true">${medal(i)}</span><b>${s.school}</b><span class="os-score" data-count="${s.overall}" data-suffix="%">${s.overall}%</span>
      <small>${count(s.achievements, 'achievement')} · ${count(s.achievementPoints, 'point')}</small><i>${'Open report'} →</i></a>`)}</div>`,
    link ? html`<a class="card-link" href="#/schools">${'All schools'} →</a>` : '');
}

SCREENS.schools = async () => {
  const n = await get('/api/metrics/network');
  const rows = n.schools.map(s => ({ go: '#/schoolReport/' + s.siteId, cells: [html`<b>${s.school}</b>`, ...MEASURES.map(([k]) => pctBar(s[k])), html`<b class="mx-overall mx-t-${toneOf(s.overall)}">${pctText(s.overall)}</b>`, html`${count(s.achievements, 'achievement')} <small class="muted">(${count(s.achievementPoints, 'point')})</small>`, html`<a href="#/schoolReport/${s.siteId}">${'Report'} →</a>`] }));
  const maxCat = Math.max(1, ...n.byCategory.map(c => c.count));
  const recent = (await get('/api/achievements/all').catch(() => [])).slice(0, 8);
  return page('Schools and achievements', 'Every school\'s key percentages, achievements and a report for each.',
    html`${outstandingCard(n.schools)}
    ${card('Key percentages by school', 'Green 85% and above, amber 70–84%, red below 70%. Open a school for its full report.', table(['School', ...MEASURES.map(m => m[1]), 'Overall', 'Achievements', ''], rows))}
    <div class="mx-two">${card('Achievements across the network', count(n.achievements, 'achievement') + ' · ' + count(n.achievementPoints, 'point'), html`<div class="ac-bars">${n.byCategory.map(c => html`<div><span>${c.label}</span><i><b style="width:${(c.count * 100 / maxCat).toFixed(0)}%"></b></i><em>${c.count}</em></div>`)}</div>`)}
      ${card('Latest achievements', null, recent.length ? html`<div class="ac-list">${recent.map(achievementItem)}</div>` : empty('No achievements recorded yet.'))}</div>`, '',
    [['Attendance', pctText(n.attendancePct), '', toneOf(n.attendancePct) === 'bad' ? 'bad' : 'good', 'check', 'Average across schools'],
     ['Average grade', pctText(n.gradePct), '', 'brand', 'list', 'Published marks'], ['Homework done', pctText(n.homeworkPct), '', 'slate', 'clip', 'Last 30 days'],
     ['Weekly plans', pctText(n.weeklyPlansPct), '', 'brand', 'clock', 'Submitted, last 4 weeks'], ['Achievements', n.achievements, '', 'good', 'shield', n.achievementPoints + ' points']]);
};

/** Text a school or teacher wrote: shown as written, never run through the translator. */
const dt = s => raw(esc(s == null ? '' : s));
const achievementItem = a => html`<article class="ac-item ac-${a.category.toLowerCase()}"><span class="ac-pts">+${a.points}</span><div><small>${a.categoryLabel} · ${fmtDate(a.achievedOn)}${S.me.role === 'LEADERSHIP' ? html` · <b>${a.school}</b>` : ''}</small>
  <b>${dt(a.title)}</b>${a.description ? html`<p>${dt(a.description)}</p>` : ''}${a.impact ? html`<p class="ac-impact">${dt(a.impact)}</p>` : ''}${a.evidenceLink ? html`<a href="${a.evidenceLink}" target="_blank" rel="noopener noreferrer">${'Evidence'} →</a>` : ''}</div>
  ${S.me.role === 'PRINCIPAL' && a.siteId === S.me.siteId ? html`<button type="button" class="hw-del" data-act="removeAchievement" data-id="${a.id}" data-confirm="Remove this achievement?" aria-label="Remove">×</button>` : ''}</article>`;

/** One school's report (Leadership: any school; principal and coordinators: their own). */
function reportBody(r) {
  const m = r.metrics;
  const maxCat = Math.max(1, ...r.byCategory.map(c => c.count));
  return html`<section class="mx-tiles">${MEASURES.map(([k, l, h]) => measureTile(m, k, l, h))}</section>
    <div class="mx-facts"><span>${count(m.students, 'student')}</span><span>${count(m.teachers, 'teacher')}</span><span>${'Student points'}: <b>${m.studentPoints}</b></span><span>${count(m.achievements, 'achievement')} (${count(m.achievementPoints, 'point')})</span>${m.overall != null ? html`<span class="mx-overall-chip mx-t-${toneOf(m.overall)}">${'Overall'} <b>${m.overall}%</b></span>` : ''}</div>
    ${card('Month by month', 'The last twelve months. Hover a point for its value.', lineChart({ labels: r.months.map(x => monthLabel(x.month)), series: [
      { name: 'Attendance', colour: '#14633b', values: r.months.map(x => x.attendancePct) }, { name: 'Average grade', colour: '#8f2447', values: r.months.map(x => x.gradePct) },
      { name: 'Homework done', colour: '#2563eb', values: r.months.map(x => x.homeworkPct) }] }))}
    <div class="mx-two">${card('Achievements', r.achievements.length + ' recorded', r.achievements.length ? html`<div class="ac-bars ac-bars-sm">${r.byCategory.filter(c => c.count).map(c => html`<div><span>${c.label}</span><i><b style="width:${(c.count * 100 / maxCat).toFixed(0)}%"></b></i><em>${c.count}</em></div>`)}</div><div class="ac-list">${r.achievements.slice(0, 6).map(achievementItem)}</div>` : empty('No achievements recorded yet.'))}
      ${card('Outstanding students', 'Most points from homework, quizzes and exams.', starStudents(r.topStudents.map(t => ({ name: t.name, sub: t.section ? html`${'Class'} ${t.section}` : '', total: t.points }))))}</div>`;
}

SCREENS.schoolReport = async (arg) => {
  const r = await get('/api/metrics/report' + (arg ? '?siteId=' + arg : ''));
  return page(r.metrics.school, 'School report · ' + fmtDate(new Date()), reportBody(r),
    html`<button type="button" class="btn" onclick="window.print()">Print or save as PDF</button>${S.me.role === 'LEADERSHIP' ? html`<a class="btn" href="#/schools">Back</a>` : ''}`);
};

// =============================================================== Principal and coordinators: metrics, achievements
SCREENS.metrics = async () => {
  const r = await get('/api/metrics/report');
  const perf = S.me.role === 'PRINCIPAL' ? await get('/api/performance/teachers').catch(() => []) : [];
  const flagged = perf.filter(t => t.pendingIndicators || t.warnings || t.openInvestigationId);
  return page('School metrics', 'Your school\'s key percentages and how they changed month by month.', html`${reportBody(r)}
    ${S.me.role === 'PRINCIPAL' ? card('Teacher performance', flagged.length ? (I18N.lang === 'ar' ? 'لدى ' + count(flagged.length, 'teacher') + ' ملاحظات أداء' : count(flagged.length, 'teacher') + ' with notes') : 'No indicators recorded.', flagged.length
      ? table(['Teacher', 'Indicators', 'Warnings', ''], flagged.map(t => ({ go: '#/performance/' + t.teacherId, cells: [t.name, dots(t.pendingIndicators, 3), dots(t.warnings, 3, 'w'), t.openInvestigationId ? badge('b-bad', 'Under investigation') : ''] })))
      : empty('Record an indicator from Teacher performance when a teacher is not delivering as expected.'), html`<a class="card-link" href="#/performance">${'Teacher performance'} →</a>`) : ''}`,
    html`<a class="btn" href="#/schoolReport">Printable report</a>`);
};

SCREENS.achievements = async () => {
  const [tpl, list] = await Promise.all([get('/api/achievements/template'), get('/api/achievements')]);
  const total = list.reduce((a, x) => a + x.points, 0);
  const canAdd = ['PRINCIPAL', 'PRINCIPAL_ASSISTANT'].includes(S.me.role);
  const form = canAdd ? card('Record an achievement', 'Fill in the school template. Each kind of achievement is worth recognition points for the school; Leadership sees it in its report.', html`<form data-form="achievement" class="ac-form">
    <span class="lbl">What kind of achievement?</span>
    <div class="ac-kinds">${tpl.map((c, i) => html`<label class="ac-kind ac-${c.key.toLowerCase()}"><input type="radio" name="category" value="${c.key}" ${i === 0 ? raw('checked') : ''}><b>${c.label}</b><small>${c.hint}</small><em>+${count(c.points, 'point')}</em></label>`)}</div>
    <div class="qz-grid">${field('Title', html`<input name="title" required maxlength="200" placeholder="e.g. Opened a VR room">`)}${field('Date', html`<input type="date" name="achievedOn" value="${isoLocal(new Date())}" required>`)}</div>
    ${field('What was achieved', html`<textarea name="description" rows="3" maxlength="4000" placeholder="A few sentences about it"></textarea>`)}
    ${field('Impact (who benefited, numbers)', html`<input name="impact" maxlength="1000" placeholder="e.g. 240 students use it every week">`)}
    ${field('Evidence link (optional)', html`<input type="url" name="evidenceLink" placeholder="https://… photos, certificate or news">`)}
    <button class="btn btn-primary">Add to our achievements</button></form>`) : '';
  return page('Achievements', 'The school\'s achievements against EAA\'s success measures, and the recognition points they earned.',
    html`<section class="ac-hero"><div><small>${'Recognition points'}</small><b data-count="${total}">${total}</b><span>${count(list.length, 'achievement')}</span></div>
      <div class="ac-hero-cats">${tpl.map(c => { const n = list.filter(a => a.category === c.key).length; return html`<span class="${n ? 'on' : ''}"><b>${n}</b>${c.label}</span>`; })}</div></section>
    ${form}${card('Our achievements', null, list.length ? html`<div class="ac-list">${list.map(achievementItem)}</div>` : empty('No achievements yet. Add the first one above.'))}`);
};

// =============================================================== Principal: teacher performance and investigations
const dots = (n, of, kind) => html`<span class="pf-dots pf-${kind || 'i'}" title="${n + ' / ' + of}">${[...Array(of)].map((_, i) => html`<i class="${i < n ? 'on' : ''}"></i>`)}<em>${n} / ${of}</em></span>`;

SCREENS.performance = async (arg) => {
  if (arg) return teacherPage(arg);
  const [list, cats, inv] = await Promise.all([get('/api/performance/teachers'), get('/api/performance/categories'), get('/api/performance/investigations')]);
  const open = inv.filter(i => i.status === 'OPEN');
  return page('Teacher performance', 'Record an indicator when a teacher is not delivering as expected. 3 indicators make 1 warning; 3 warnings open an investigation automatically.',
    html`<section class="pf-rule"><div><b>1</b><span>Indicator</span><small>You record it, with what happened</small></div><i>×3 →</i><div><b>1</b><span>Warning</span><small>The teacher is told</small></div><i>×3 →</i><div><b>!</b><span>Investigation</span><small>Opens by itself; you and the subject coordinator are involved</small></div></section>
    ${card('Record an indicator', null, list.length ? html`<form data-form="indicator" class="pf-form">
      ${field('Teacher', html`<select name="teacherId">${list.map(t => html`<option value="${t.teacherId}">${t.name}${t.subject ? ' · ' + t.subject : ''}</option>`)}</select>`)}
      ${field('About', html`<select name="category">${Object.entries(cats).map(([k, v]) => html`<option value="${k}">${v}</option>`)}</select>`)}
      ${field('What happened', html`<textarea name="note" rows="2" maxlength="2000" required placeholder="Facts only: what, when"></textarea>`)}
      <button class="btn btn-primary">Record indicator</button></form>` : empty('No teachers yet.'))}
    ${open.length ? card('Open investigations', open.length + ' open', table(['Teacher', 'Opened', 'People involved', 'Notes', ''], open.map(i => ({ go: '#/investigations/' + i.id, cells: [i.teacher, fmtDate(i.openedAt), i.members, i.notes, badge('b-bad', 'Open')] })))) : ''}
    ${card('Teachers', 'Indicators count towards the next warning.', table(['Teacher', 'Subject', 'Indicators', 'Warnings', 'Status', ''], list.map(t => ({ go: '#/performance/' + t.teacherId,
      cells: [html`<b>${t.name}</b>`, t.subject, dots(t.pendingIndicators, 3), dots(t.warnings, 3, 'w'), t.openInvestigationId ? badge('b-bad', 'Under investigation') : t.warnings ? badge('b-watch', 'Warned') : t.pendingIndicators ? badge('b-watch', 'Indicators') : badge('b-good', 'Clear'), html`<a href="#/performance/${t.teacherId}">${openWord()}</a>`] }))))}`);
};

async function teacherPage(id) {
  const r = await get('/api/performance/teachers/' + id), t = r.standing;
  return page(t.name, (t.subject ? t.subject + ' · ' : '') + ROLE_LABEL[t.role], html`
    <section class="pf-sum"><div><small>Indicators towards the next warning</small>${dots(t.pendingIndicators, 3)}</div><div><small>Warnings</small>${dots(t.warnings, 3, 'w')}</div>
      <div><small>Status</small>${t.openInvestigationId ? html`<a href="#/investigations/${t.openInvestigationId}">${badge('b-bad', 'Under investigation')}</a>` : t.warnings ? badge('b-watch', 'Warned') : badge('b-good', 'Clear')}</div></section>
    ${card('Indicators', r.indicators.length + ' recorded', r.indicators.length ? html`<ul class="pf-tl">${r.indicators.map(i => html`<li class="${i.warningNumber ? 'in-w' : ''}"><b>${i.categoryLabel}</b>${i.warningNumber ? html` <span class="badge b-watch">${'Warning'} ${i.warningNumber}</span>` : ''}<p>${i.note}</p><small>${fmt(i.at)} · ${i.by || ''}</small></li>`)}</ul>` : empty('No indicators.'))}
    ${r.investigations.length ? card('Investigations', null, table(['Opened', 'Status', ''], r.investigations.map(i => ({ go: '#/investigations/' + i.id, cells: [fmtDate(i.openedAt), badge(i.status === 'OPEN' ? 'b-bad' : 'b-neutral', title(i.status)), html`<a href="#/investigations/${i.id}">${openWord()}</a>`] })))) : ''}`,
    html`<a class="btn" href="#/performance">Back</a>`);
}

SCREENS.investigations = async (arg) => {
  if (!arg) {
    const list = await get('/api/performance/investigations');
    return page('Investigations', 'Investigations you are involved in.', card('Investigations', null, list.length
      ? table(['Teacher', 'Opened', 'Status', 'People', 'Notes'], list.map(i => ({ go: '#/investigations/' + i.id, cells: [i.teacher, fmtDate(i.openedAt), badge(i.status === 'OPEN' ? 'b-bad' : 'b-neutral', title(i.status)), i.members, i.notes] })))
      : empty('You are not involved in any investigation.')));
  }
  const d = await get('/api/performance/investigations/' + arg), s = d.summary;
  return page('Investigation: ' + s.teacher, 'Opened ' + fmtDate(s.openedAt) + (s.closedAt ? ' · closed ' + fmtDate(s.closedAt) : ''), html`
    <div class="mx-two">${card('People involved', null, html`<ul class="pf-people">${d.members.map(m => html`<li><span class="ch-av">${initialsOf(m.name)}</span><b>${m.name}</b><small>${ROLE_LABEL[m.role] || m.role}</small></li>`)}</ul>
      ${d.canManage && d.canAdd.length ? html`<form data-form="invMember" data-id="${s.id}" class="row" style="margin-top:10px"><select class="input" name="personId">${d.canAdd.map(m => html`<option value="${m.personId}">${m.name} · ${ROLE_LABEL[m.role] || m.role}</option>`)}</select><button class="btn">Add</button></form>` : ''}`)}
      ${card('Why it was opened', d.warnings.length + ' warnings', html`<ul class="pf-tl">${d.indicators.map(i => html`<li class="in-w"><b>${i.categoryLabel}</b> <span class="badge b-watch">${'Warning'} ${i.warningNumber}</span><p>${i.note}</p><small>${fmtDate(i.at)}</small></li>`)}</ul>`)}</div>
    ${card('Notes', null, html`<ul class="pf-notes">${d.notes.map(n => html`<li><b>${n.author}</b><small>${fmt(n.at)}</small><p>${n.body}</p></li>`)}</ul>
      ${s.status === 'OPEN' ? html`<form data-form="invNote" data-id="${s.id}"><textarea name="body" rows="2" maxlength="4000" required placeholder="Add a note"></textarea><button class="btn">Add note</button></form>` : ''}`)}
    ${s.status === 'OPEN' && d.canManage ? card('Close the investigation', 'Write the outcome. The teacher is told it is closed.', html`<form data-form="invClose" data-id="${s.id}"><textarea name="outcome" rows="3" maxlength="4000" required></textarea><button class="btn btn-primary" data-confirm="Close this investigation?">Close with this outcome</button></form>`)
      : d.outcome ? card('Outcome', null, html`<p>${d.outcome}</p>`) : ''}`,
    html`<a class="btn" href="#/${S.me.role === 'PRINCIPAL' ? 'performance' : 'investigations'}">Back</a>`);
};

// =============================================================== Outstanding students: a podium that stands out
function starStudents(list) {
  if (!list || !list.length) return empty('No points yet.');
  const top = list.slice(0, 3), rest = list.slice(3);
  const order = top.length === 3 ? [1, 0, 2] : top.map((_, i) => i);
  return html`<div class="ss-podium">${order.map(i => top[i] ? html`<div class="ss-place ss-p${i + 1}" style="--d:${i * 0.15}s"><span class="ss-medal" aria-hidden="true">${medal(i)}</span><span class="ss-av">${initialsOf(top[i].name)}</span><b>${top[i].name}</b>${top[i].sub ? html`<small>${top[i].sub}</small>` : ''}<em data-count="${top[i].total}">${top[i].total}</em><span class="ss-step">${i + 1}</span></div>` : '')}</div>
    ${rest.length ? html`<ol class="ss-rest" start="4">${rest.map(r => html`<li><b>${r.name}</b>${r.sub ? html`<small>${r.sub}</small>` : ''}<em>${r.total}</em></li>`)}</ol>` : ''}`;
}

// recognition page: the top students as a podium
{
  const base = SCREENS.recognition;
  SCREENS.recognition = async () => {
    const list = await get('/api/points/recognition');
    return page('Recognition', 'Points from homework, quizzes and exams: each school\'s total, average per student and its outstanding students.',
      list.length ? html`${list.map(sc => card(sc.school, count(sc.total, 'point') + ' · ' + trText('average per student') + ' ' + sc.averagePerStudent, starStudents(sc.top.map(t => ({ name: t.name, total: t.total })))))}` : empty('No schools yet.'));
  };
  void base;
}

// =============================================================== head teacher: the school at a glance, on top of "My school"
{
  const base = SCREENS.school;
  SCREENS.school = async (arg, q) => {
    const [body, m, rec] = await Promise.all([base(arg, q), get('/api/metrics/school').catch(() => null), get('/api/points/recognition').catch(() => [])]);
    if (!m) return body;
    const top = (rec[0] && rec[0].top) || [];
    const glance = html`<section class="gl"><div class="gl-head"><div><h2>Your school at a glance</h2><small>Key percentages in plain numbers. Press one for the details.</small></div><a class="card-link" href="#/metrics">${'All metrics'} →</a></div>
      <div class="mx-tiles">${MEASURES.map(([k, l, h]) => measureTile(m, k, l, h, k === 'healthScore' ? '#/issues' : k === 'weeklyPlansPct' ? '#/planning' : '#/metrics'))}</div>
      <div class="gl-row"><a class="gl-chip" href="#/achievements" data-kpi="1">${count(m.achievements, 'achievement')} · ${count(m.achievementPoints, 'point')}</a><a class="gl-chip" href="#/recognition" data-kpi="1">${'Student points'}: <b>${m.studentPoints}</b></a>${m.openInvestigations ? html`<a class="gl-chip gl-bad" href="#/performance" data-kpi="1">${'Open investigations'}: <b>${m.openInvestigations}</b></a>` : ''}</div></section>
      ${top.length ? card('Outstanding students', 'Most points from homework, quizzes and exams.', starStudents(top.slice(0, 5).map(t => ({ name: t.name, total: t.total }))), html`<a class="card-link" href="#/recognition">${'All'} →</a>`) : ''}`;
    const s = body.s, at = s.indexOf('<section class="pd-kpis">');
    return at < 0 ? html`${glance}${body}` : raw(s.slice(0, at) + glance.s + s.slice(at));
  };
}

// =============================================================== Leadership overview: outstanding schools on top
{
  const base = SCREENS.overview;
  SCREENS.overview = async (arg, q) => {
    const [body, n] = await Promise.all([base(arg, q), get('/api/metrics/network').catch(() => null)]);
    if (!n) return body;
    const block = html`<div class="lo-outstanding">${outstandingCard(n.schools, true)}</div>`;
    const s = body.s, at = s.indexOf('<section class="lo-row">');
    return at < 0 ? body : raw(s.slice(0, at) + block.s + s.slice(at));
  };
}

// =============================================================== student: today's tasks on top of "My day"
{
  const base = SCREENS.mydash;
  SCREENS.mydash = async (arg, q) => {
    const today = isoLocal(new Date());
    const [body, hw, qz] = await Promise.all([base(arg, q), get(`/api/homework?from=${today}&to=${addDays(today, 2)}`).catch(() => []), get('/api/quizzes').catch(() => [])]);
    const tasks = hw.filter(h => !h.done), quizzes = qz.filter(z => !z.taken);
    const done = hw.filter(h => h.done).length;
    const block = html`<section class="tt"><div class="tt-head"><span class="sp-ic">${sIc('check')}</span><div><h2>Today's tasks</h2><small>${tasks.length + quizzes.length ? count(tasks.length + quizzes.length, 'task') : 'All done. Well done!'}${done ? ' · ' + trText('Done') + ': ' + done : ''}</small></div><a class="sp-link" href="#/homework">${'Homework'} →</a></div>
      ${tasks.length + quizzes.length ? html`<div class="tt-list">${tasks.map(h => html`<div class="tt-item" style="--c:${subjColour(h.subject)}"><span class="tt-kind">${h.subject}</span><b>${h.title}</b><small>${h.dueOn === today ? 'Due today' : html`${'Due'} ${fmtDay(h.dueOn)}`}</small><button type="button" class="hw-done" data-act="homeworkDone" data-id="${h.id}" data-done="">${'Mark as done · +5 points'}</button></div>`)}
        ${quizzes.map(z => html`<a class="tt-item tt-quiz" style="--c:${subjColour(z.subject)}" href="#/quizzes/${z.id}"><span class="tt-kind">${'Quiz'} · ${z.subject}</span><b>${z.title}</b><small>${count(z.questions, 'question')}</small><span class="qz-go">Start quiz →</span></a>`)}</div>` : ''}</section>`;
    const s = body.s, at = s.indexOf('<section class="sp-hero">');
    return at < 0 ? body : raw(s.slice(0, at) + block.s + s.slice(at));
  };
}

// =============================================================== points pop: the student sees the points they earned since last time
{
  const base = pointsProgress;
  // eslint-disable-next-line no-global-assign
  pointsProgress = v => {
    let gained = 0;
    try { const k = 'hafez.points.' + S.me.id, seen = localStorage.getItem(k); if (seen !== null) gained = (v.total || 0) - Number(seen); localStorage.setItem(k, String(v.total || 0)); } catch { }
    const out = base(v);
    return gained > 0 ? raw(out.s.replace('<div class="pp-ring">', `<div class="pp-ring"><span class="pp-pop" aria-live="polite">+${gained}</span>`)) : out;
  };
}

// =============================================================== actions and forms
Object.assign(ACT, {
  removeAchievement: async d => { await del('/api/achievements/' + d.id); await refreshAfter('Achievement removed.'); }
});
Object.assign(FORM, {
  achievement: async f => { await post('/api/achievements', { category: f.category, title: f.title, description: f.description || '', impact: f.impact || '', achievedOn: f.achievedOn, evidenceLink: f.evidenceLink || null }); await refreshAfter('Achievement added. Leadership can see it in your school\'s report.'); },
  indicator: async (f, d, form) => {
    const r = await post('/api/performance/indicators', { teacherId: f.teacherId, category: f.category, note: f.note });
    if (r.investigationId) { toast('Third warning: an investigation was opened.'); location.hash = '#/investigations/' + r.investigationId; return; }
    await refreshAfter(r.warningIssued ? 'Indicator recorded. That made a warning (' + r.warnings + ' / 3); the teacher is told.' : 'Indicator recorded (' + r.pendingIndicators + ' / 3 towards a warning).');
  },
  invNote: async (f, d) => { await post(`/api/performance/investigations/${d.id}/notes`, { body: f.body }); await refreshAfter('Note added.'); },
  invMember: async (f, d) => { await post(`/api/performance/investigations/${d.id}/members`, { personId: f.personId }); await refreshAfter('Added. They were told.'); },
  invClose: async (f, d) => { await post(`/api/performance/investigations/${d.id}/close`, { outcome: f.outcome }); await refreshAfter('Investigation closed.'); }
});

// quiz builder: the question type decides which answer fields show and which are required
document.addEventListener('change', e => {
  if (!e.target.matches || !e.target.matches('.qz-kind')) return;
  const q = e.target.closest('.qz-q'), k = q.dataset.k, kind = e.target.value, f = e.target.form;
  q.dataset.kind = kind;
  [0, 1].forEach(j => { f.elements[`o${k}_${j}`].required = kind === 'CHOICE'; });
  f.elements['a' + k].required = kind === 'SHORT';
});

// =============================================================== pick-up: the parent accepts responsibility before it is recorded
const PICKUP_ACK = {
  en: 'I confirm that I am collecting my child, or that I sent the person named. By pressing Pick up I accept that from this moment the school is no longer responsible for my child, and the responsibility for the pick-up is mine.',
  ar: 'أؤكد أنني أستلم طفلي أو أنني أرسلت الشخص المذكور. بالضغط على زر الاستلام أقرّ بأن المدرسة تُخلي مسؤوليتها عن طفلي من هذه اللحظة، وأن مسؤولية الاستلام تقع عليّ.'
};
/** A message the parent must accept before the pick-up is recorded. Resolves true when they confirm. */
function pickupConfirm(child, collector) {
  return new Promise(resolve => {
    const ar = I18N.lang === 'ar';
    const box = document.createElement('div');
    box.className = 'pkm';
    box.innerHTML = html`<div class="pkm-card" role="dialog" aria-modal="true" aria-labelledby="pkmT">
      <span class="pkm-ic" aria-hidden="true">!</span>
      <h2 id="pkmT">${'Confirm the pick-up'}</h2>
      <p class="pkm-who"><b>${child}</b> · ${'Collected by'} ${collector}</p>
      <div class="pkm-text" lang="${ar ? 'ar' : 'en'}" dir="${ar ? 'rtl' : 'ltr'}">${raw(esc(ar ? PICKUP_ACK.ar : PICKUP_ACK.en))}</div>
      <label class="pkm-check"><input type="checkbox" id="pkmOk"><span>${'I have read this and I accept responsibility for my child from now on.'}</span></label>
      <div class="pkm-act"><button type="button" class="btn" id="pkmNo">Cancel</button><button type="button" class="btn btn-primary pkm-go" id="pkmYes" disabled>${'Pick up'}</button></div></div>`.s;
    document.body.appendChild(box);
    const yes = box.querySelector('#pkmYes'), ok = box.querySelector('#pkmOk');
    const close = v => { box.remove(); document.removeEventListener('keydown', esc_); resolve(v); };
    const esc_ = e => { if (e.key === 'Escape') close(false); };
    ok.addEventListener('change', () => { yes.disabled = !ok.checked; });
    yes.addEventListener('click', () => close(true));
    box.querySelector('#pkmNo').addEventListener('click', () => close(false));
    box.addEventListener('click', e => { if (e.target === box) close(false); });
    document.addEventListener('keydown', esc_);
    ok.focus();
  });
}
