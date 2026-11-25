/* Family engagement screens: students at risk (low-grade warnings), absence notes, trips and events, early pick-up
   requests and parent-teacher meetings. Everything is decided by the server; this file only draws and sends. */
'use strict';

// ---- sidebar entries (added next to the pages they belong with) and their icons
(() => {
  const add = (role, after, items) => {
    const nav = NAV[role]; if (!nav) return;
    const i = nav.findIndex(x => x[0] === after);
    nav.splice(i < 0 ? nav.length : i + 1, 0, ...items);
  };
  add('PARENT', 'homework', [['absences', 'Absence notes'], ['trips', 'Trips & events'], ['meetings', 'Teacher meetings']]);
  add('TEACHER', 'chat', [['meetings', 'Meetings'], ['atrisk', 'Students at risk'], ['trips', 'Trips & events']]);
  add('COORDINATOR', 'points', [['atrisk', 'Students at risk']]);
  add('COORDINATOR', 'pickup', [['trips', 'Trips & events']]);
  add('PRINCIPAL', 'points', [['atrisk', 'Students at risk']]);
  add('PRINCIPAL', 'achievements', [['trips', 'Trips & events']]);
  add('PRINCIPAL_ASSISTANT', 'points', [['atrisk', 'Students at risk']]);
  add('PRINCIPAL_ASSISTANT', 'pickup', [['absences', 'Absence notes'], ['trips', 'Trips & events']]);
  add('ATTENDANCE_OFFICER', 'attendance', [['absences', 'Absence notes']]);
  Object.assign(EA_ROUTE_IC, { atrisk: LIC.warn, absences: LIC.clip, trips: 'M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z', meetings: PB.cal });
  Object.assign(PAGE_ICON, { absences: 'calendar', trips: 'star', meetings: 'users' });
})();

Object.assign(COUNTED, { request: ['request', 'requests', 'طلب', 'طلبان', 'طلبات', 'طلباً', 'm'], excuse: ['note', 'notes', 'عذر', 'عذران', 'أعذار', 'عذراً', 'm'] });
const FM_CAT = { ILLNESS: 'Illness', MEDICAL: 'Medical appointment', FAMILY: 'Family matter', TRAVEL: 'Travel', OTHER: 'Other' };
const FM_ST = { PENDING: ['b-watch', 'Waiting for review'], ACCEPTED: ['b-good', 'Accepted'], REJECTED: ['b-bad', 'Not accepted'], CANCELLED: ['b-neutral', 'Withdrawn'],
  APPROVED: ['b-good', 'Approved'], DECLINED: ['b-bad', 'Declined'], BOOKED: ['b-good', 'Booked'] };
const fmBadge = s => badge((FM_ST[s] || ['b-neutral'])[0], (FM_ST[s] || [s, title(s)])[1]);
const FM_MODE = { IN_PERSON: 'In person', ONLINE: 'Online', PHONE: 'By phone' };
const fmDays = (a, b) => !b || a === b ? fmtDate(a) : html`${fmtDate(a)} – ${fmtDate(b)}`;
const fmHm = t => String(t || '').slice(0, 5);
const fmToday = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Qatar' });
const fmPct = n => Math.round(Number(n));

// =============================================================== 1. students at risk
SCREENS.atrisk = async () => {
  const b = await get('/api/at-risk');
  const high = b.open.filter(f => f.level === 'HIGH').length, told = b.open.filter(f => f.parentsToldAt).length;
  const level = f => f.level === 'HIGH' ? badge('b-bad', fmPct(f.averagePct) + '%') : badge('b-watch', fmPct(f.averagePct) + '%');
  const follow = f => html`${f.note ? html`<div class="fm-note"><b>${f.notedBy || ''}</b> ${f.note}</div>` : ''}${f.parentsToldAt ? html`<small class="muted">Parents told ${fmt(f.parentsToldAt)}</small>` : ''}${!f.note && !f.parentsToldAt ? html`<span class="muted">No follow-up yet</span>` : ''}`;
  const actions = f => !f.canAct ? '' : html`<details class="fm-more"><summary>Follow up</summary>
    <form data-form="fmRiskNote" data-id="${f.id}"><textarea class="input" name="note" rows="2" maxlength="1000" placeholder="What is being done to help (extra lessons, a call home)" required>${f.note || ''}</textarea><button class="btn btn-sm">Save note</button></form>
    <form data-form="fmRiskTell" data-id="${f.id}"><input class="input" name="message" maxlength="500" placeholder="A line for the parents (optional)"><button class="btn btn-sm btn-primary">${f.parentsToldAt ? 'Tell the parents again' : 'Tell the parents'}</button></form></details>`;
  const kpis = [['At risk', b.open.length, '', b.open.length ? 'bad' : 'good', 'warn', 'Below ' + b.passLine + '% in a subject'], ['High risk', high, '', high ? 'bad' : 'good', 'warn', 'Below ' + b.highLine + '%'],
    ['Parents told', told, '', 'slate', 'bell', 'Of the open warnings'], ['Improved', b.improved.length, '', 'good', 'check', 'In the last 30 days']];
  return page('Students at risk', `Flagged automatically when a student's average in a subject falls below ${b.passLine}% this term (high risk below ${b.highLine}%). It updates every time marks are submitted, and clears by itself when the average recovers.`,
    html`${isNewLook() ? '' : html`<div class="kpi-grid">${kpis.map(k => kpi(k[0], k[1], k[3] === 'bad' ? 'brand' : k[3] === 'good' ? 'green' : 'slate'))}</div>`}
    ${card('Open warnings', count(b.open.length, 'student') + ' · lowest average first', b.open.length
      ? table(['Student', 'Class', 'Subject', 'Average', 'Lowest', 'Absent (30 days)', 'Follow-up', ''], b.open.map(f => ({ cells: [html`<b>${f.student}</b>`, f.section, f.subject, level(f), fmPct(f.lowestPct) + '%', f.absences30, follow(f), actions(f)] })))
      : empty('No student is below the pass line. Well done!'))}
    ${card('Recently improved', 'Warnings that cleared by themselves in the last 30 days.', b.improved.length
      ? table(['Student', 'Class', 'Subject', 'Average now', 'Cleared'], b.improved.map(f => ({ cells: [f.student, f.section, f.subject, badge('b-good', fmPct(f.averagePct) + '%'), fmtDate(f.resolvedAt)] })))
      : empty('Nothing yet.'))}`, '', kpis);
};

// =============================================================== 2. absence notes
SCREENS.absences = async () => S.me.role === 'PARENT' ? parentAbsences() : staffAbsences();

async function parentAbsences() {
  const d = await get('/api/absences/mine');
  if (!d.children.length) return page('Absence notes', null, card('Absence notes', null, empty('No children are linked to your account yet.')));
  const today = fmToday();
  const form = html`<form data-form="fmExcuse" id="fmExcuseForm" class="fm-form">
    ${field('Child', html`<select class="input" name="studentId">${d.children.map(c => html`<option value="${c.id}">${c.name}${c.section ? ' · ' + c.section : ''}</option>`)}</select>`)}
    <div class="fm-two">${field('First day', html`<input class="input" type="date" name="fromDate" value="${today}" required>`)}${field('Last day (optional)', html`<input class="input" type="date" name="toDate">`)}</div>
    ${field('Reason', html`<select class="input" name="category">${Object.entries(FM_CAT).map(([k, v]) => html`<option value="${k}">${v}</option>`)}</select>`)}
    ${field('Details (optional, needed for Other)', html`<textarea class="input" name="details" rows="2" maxlength="1000"></textarea>`)}
    <button class="btn btn-primary">Send the note</button></form>`;
  return page('Absence notes', 'Tell the school why your child was, or will be, away. The attendance office reviews it; an accepted note turns the absence into an excused one.',
    html`${d.unexplained.length ? card('Absences without a reason', 'Press Explain to fill in the note below.', html`<div class="fm-list">${d.unexplained.map(u => html`<div class="fm-row"><span class="pk-av pk-av-sm">${pkInit(u.student)}</span><div><b>${u.student}</b><small>${fmtDay(u.date)}</small></div>
        <button type="button" class="btn btn-sm btn-primary" data-act="fmExplain" data-student="${u.studentId}" data-date="${u.date}">Explain</button></div>`)}</div>`) : ''}
    ${card('Send an absence note', 'Up to 30 days back, or up to 60 days ahead for a planned absence.', form)}
    ${card('Notes you sent', null, d.notes.length ? table(['Child', 'Days', 'Reason', 'Status', 'School reply', ''], d.notes.map(n => ({ cells: [n.student, fmDays(n.fromDate, n.toDate),
      html`${FM_CAT[n.category]}${n.details ? html`<div class="muted">${n.details}</div>` : ''}`, fmBadge(n.status), n.reviewedBy ? html`${n.reviewedBy}${n.reviewNote ? html`<div class="muted">${n.reviewNote}</div>` : ''}` : '—',
      n.status === 'PENDING' ? html`<button class="btn btn-sm" data-act="fmExcuseCancel" data-id="${n.id}" data-confirm="Withdraw this note?">Withdraw</button>` : ''] }))) : empty('You have not sent any notes yet.'))}`);
}

async function staffAbsences() {
  const b = await get('/api/absences/board');
  const pend = n => html`<article class="fm-req"><div class="fm-req-top"><span class="pk-av">${pkInit(n.student)}</span><div><b>${n.student}</b><small>${n.section} · ${'from'} ${n.parent}</small></div></div>
    <p><b>${fmDays(n.fromDate, n.toDate)}</b> · ${FM_CAT[n.category]}${n.details ? html` · ${n.details}` : ''}</p><small class="muted">Sent ${fmt(n.createdAt)}</small>
    ${b.canReview ? html`<form data-form="fmExcuseReject" data-id="${n.id}" class="fm-act"><input class="input" name="note" maxlength="500" placeholder="Reason for the parent (needed to reject)" required>
      <div class="fm-btns"><button class="btn btn-sm btn-danger">Reject</button><button type="button" class="btn btn-sm btn-primary" data-act="fmExcuseAccept" data-id="${n.id}">Accept</button></div></form>` : ''}</article>`;
  return page('Absence notes', 'Notes sent by parents. Accepting one turns the absent days in it into excused leave, and any of those days still to come is excused when attendance is taken.',
    html`${card('Waiting for review', count(b.pending.length, 'excuse'), b.pending.length ? html`<div class="fm-grid">${b.pending.map(pend)}</div>` : empty('No notes are waiting.'))}
    ${card('Excused today', 'Accepted notes that cover today.', b.today.length ? table(['Student', 'Class', 'Days', 'Reason'], b.today.map(n => ({ cells: [n.student, n.section, fmDays(n.fromDate, n.toDate), FM_CAT[n.category]] }))) : empty('Nobody is excused today.'))}
    ${card('Recently reviewed', null, b.recent.length ? table(['Student', 'Class', 'Days', 'Reason', 'Decision', 'Reviewed by'], b.recent.map(n => ({ cells: [n.student, n.section, fmDays(n.fromDate, n.toDate), FM_CAT[n.category], fmBadge(n.status), html`${n.reviewedBy || ''}${n.reviewNote ? html`<div class="muted">${n.reviewNote}</div>` : ''}`] }))) : empty('Nothing reviewed yet.'))}`);
}

// =============================================================== 3. trips and events
SCREENS.trips = async (arg) => {
  if (arg && S.me.role !== 'PARENT') return tripDetail(arg);
  const p = await get('/api/events');
  if (S.me.role === 'PARENT') return parentTrips(p);
  const sections = p.canCreate ? (S.me.role === 'TEACHER' ? [...new Map((await myPairs()).map(x => [x.sectionId, x.section]))].map(([id, label]) => ({ id, label }))
    : (await get('/api/academic/sections')).map(s => ({ id: s.id, label: sectionLabel(s) }))) : [];
  const today = fmToday();
  const form = !p.canCreate ? '' : card('New trip or event', 'Every parent of the classes you choose is asked for permission at once.', sections.length ? html`<form data-form="fmEvent" class="fm-form">
    ${field('Title', html`<input class="input" name="title" required maxlength="200" placeholder="e.g. Science museum trip">`)}
    ${field('Details for parents', html`<textarea class="input" name="details" rows="3" maxlength="4000" placeholder="What to bring, transport, supervision"></textarea>`)}
    <div class="fm-two">${field('Place', html`<input class="input" name="location" maxlength="200">`)}${field('Cost (QAR, optional)', html`<input class="input" type="number" name="cost" min="0" step="0.01">`)}</div>
    <div class="fm-three">${field('Day', html`<input class="input" type="date" name="date" min="${today}" required>`)}${field('From', html`<input class="input" type="time" name="startTime" value="08:00" required>`)}${field('To', html`<input class="input" type="time" name="endTime" value="12:00" required>`)}</div>
    ${field('Parents answer by', html`<input class="input" type="date" name="consentDeadline" min="${today}" required>`)}
    <div class="field"><label>Classes</label><div class="fm-checks">${sections.map(s => html`<label><input type="checkbox" name="sec_${s.id}" value="${s.id}"> ${s.label}</label>`)}</div></div>
    <button class="btn btn-primary">Ask the parents</button></form>` : empty('You are not assigned to a class yet.'));
  const row = e => ({ go: '#/trips/' + e.id, cells: [html`<b>${e.title}</b>${e.location ? html`<div class="muted">${e.location}</div>` : ''}`, fmt(e.startsAt), e.sections.join(', '), e.organiser,
    e.cancelled ? badge('b-neutral', 'Cancelled') : html`<span class="fm-tally"><i class="ok">${e.approved}</i><i class="no">${e.declined}</i><i>${e.waiting}</i></span>`, e.cancelled ? '' : e.open ? html`${'Until'} ${fmtDate(e.consentDeadline)}` : badge('b-internal', 'Answers closed')] });
  const cols = ['Trip or event', 'When', 'Classes', 'Organiser', 'Approved · declined · waiting', 'Answers'];
  return page('Trips & events', 'Ask parents for permission online and follow every answer.',
    html`${form}${card('Coming up', null, p.upcoming.length ? table(cols, p.upcoming.map(row)) : empty('No trips or events coming up.'))}
    ${p.past.length ? card('Recent', 'The last 30 days.', table(cols, p.past.map(row))) : ''}`);
};

async function tripDetail(id) {
  const d = await get('/api/events/' + id), e = d.event;
  const canChange = e.mine || S.me.role === 'PRINCIPAL_ASSISTANT';
  return page(e.title, html`${fmt(e.startsAt)} – ${pkTime(e.endsAt)}${e.location ? ' · ' + e.location : ''}`,
    html`${e.cancelled ? html`<div class="callout callout-info">Cancelled: ${e.cancelReason}</div>` : ''}
    ${card('Details', html`${'Classes'}: ${e.sections.join(', ')} · ${'Organiser'}: ${e.organiser}`, html`<p class="fm-pre">${e.details || '—'}</p>${e.cost != null ? html`<p><b>Cost</b>: ${e.cost} QAR</p>` : ''}<p><b>Parents answer by</b>: ${fmtDate(e.consentDeadline)}</p>`)}
    <div class="kpi-grid">${kpi('Approved', e.approved, 'green')}${kpi('Declined', e.declined, 'brand')}${kpi('Waiting', e.waiting, 'amber')}</div>
    ${card('Answers', count(e.students, 'student'), table(['Student', 'Answer', 'Note from the parent', 'Answered by', 'When'], d.roster.map(r => ({ cells: [html`<b>${r.student}</b>`, r.decision ? fmBadge(r.decision) : badge('b-watch', 'Waiting'), r.note || '', r.decidedBy || '', r.decidedAt ? fmt(r.decidedAt) : ''] }))),
      canChange && e.open && e.waiting ? html`<button class="btn btn-sm btn-primary" data-act="fmRemind" data-id="${e.id}">Remind ${e.waiting} waiting</button>` : '')}
    ${canChange && !e.cancelled ? card('Cancel', 'Every parent is told.', html`<form data-form="fmEventCancel" data-id="${e.id}" class="row"><input class="input" name="reason" maxlength="300" placeholder="Why it is cancelled" required><button class="btn btn-danger">Cancel it</button></form>`) : ''}`,
    html`<a class="btn" href="#/trips">Back</a>`);
}

function parentTrips(p) {
  const stmt = inLang(p.statementEn, p.statementAr);
  const childBox = (e, c) => html`<div class="fm-child"><div class="fm-child-top"><span class="pk-av pk-av-sm">${pkInit(c.student)}</span><b>${c.student}</b>${c.decision ? fmBadge(c.decision) : badge('b-watch', 'Your answer is needed')}</div>
    ${c.decidedAt ? html`<small class="muted">${c.decidedBy} · ${fmt(c.decidedAt)}</small>` : ''}
    ${e.open ? html`<form data-form="fmConsent" data-id="${e.id}" data-student="${c.studentId}" class="fm-form">
      <label class="fm-agree"><input type="checkbox" name="agreed" ${c.decision === 'APPROVED' ? raw('checked') : ''}> <span>${stmt}</span></label>
      <input class="input" name="note" maxlength="500" placeholder="A note for the teacher (allergies, medicine), optional" value="${c.note || ''}">
      <div class="row"><button class="btn btn-sm btn-primary" name="decision" value="APPROVED">${c.decision === 'APPROVED' ? 'Approved ✓' : 'Approve'}</button><button class="btn btn-sm" name="decision" value="DECLINED">${c.decision === 'DECLINED' ? 'Declined' : 'Decline'}</button></div></form>`
      : e.cancelled ? '' : html`<small class="muted">Answers are closed.</small>`}</div>`;
  const ev = e => card(e.title, html`${fmt(e.startsAt)} – ${pkTime(e.endsAt)}${e.location ? ' · ' + e.location : ''}`, html`${e.cancelled ? html`<div class="callout callout-info">Cancelled: ${e.cancelReason}</div>` : ''}
    <p class="fm-pre">${e.details || ''}</p><p class="muted">${e.cost != null ? html`${'Cost'}: ${e.cost} QAR · ` : ''}${'Answer by'} ${fmtDate(e.consentDeadline)} · ${e.organiser}</p>
    <div class="fm-children">${e.children.map(c => childBox(e, c))}</div>`);
  return page('Trips & events', 'Give or refuse permission for your children to take part. You can change your answer until the deadline.',
    html`${p.upcoming.length ? p.upcoming.map(ev) : card('Coming up', null, empty('No trips or events need your answer.'))}${p.past.length ? card('Recent', null, table(['Trip or event', 'When', 'Answer'], p.past.map(e => ({ cells: [e.title, fmt(e.startsAt), e.children.map(c => html`${c.student}: ${c.decision ? fmBadge(c.decision) : '—'} `)] })))) : ''}`);
}

// =============================================================== 4. early pick-up (on the pick-up page)
/** Parent: request an early pick-up for a child, and the requests already sent. Drawn under the pick-up cards. */
async function earlyPickupParent(kids) {
  if (!kids.length) return '';
  const mine = await get('/api/early-pickups/mine').catch(() => []);
  const today = fmToday();
  const form = html`<form data-form="fmEarly" class="fm-form">
    ${field('Child', html`<select class="input" name="studentId">${kids.map(c => html`<option value="${c.studentId}">${c.student}</option>`)}</select>`)}
    <div class="fm-two">${field('Day', html`<input class="input" type="date" name="date" min="${today}" value="${today}" required>`)}${field('Time', html`<input class="input" type="time" name="time" min="06:30" max="15:00" value="11:00" required>`)}</div>
    ${field('Who is collecting', html`<select class="input" name="collectorId"><option value="">${S.me.displayName} (me)</option>${kids.filter(c => c.collectors.length).map(c => html`<optgroup label="${c.student}">${c.collectors.map(k => html`<option value="${k.id}">${k.name} · ${REL_LABEL[k.relationship] || k.relationship}</option>`)}</optgroup>`)}</select>`)}
    ${field('Reason', html`<input class="input" name="reason" maxlength="300" placeholder="e.g. Doctor's appointment" required>`)}
    <button class="pk-btn pk-btn-go">Request early pick-up</button></form>`;
  return card('Early pick-up', 'Ask ahead to collect your child before the end of the day. The school approves it and tells the class teacher; on the day, press Pick up as usual.', html`${form}
    ${mine.length ? table(['Child', 'Day and time', 'Collector', 'Reason', 'Status', ''], mine.map(r => ({ cells: [r.student, html`${fmtDate(r.date)} · ${fmHm(r.time)}`, r.collector, r.reason,
      html`${fmBadge(r.status)}${r.pickedUp ? html` ${badge('b-good', 'Picked up')}` : ''}${r.declineReason ? html`<div class="muted">${r.declineReason}</div>` : ''}`,
      r.status === 'PENDING' || r.status === 'APPROVED' ? html`<button class="btn btn-sm" data-act="fmEarlyCancel" data-id="${r.id}" data-confirm="Cancel this early pick-up?">Cancel</button>` : ''] }))) : ''}`);
}

/** Staff: waiting requests (to approve), today's early pick-ups and the coming ones. Drawn above the board. */
async function earlyPickupStaff() {
  const b = await get('/api/early-pickups/board').catch(() => null);
  if (!b) return '';
  const who = r => html`<b>${r.student}</b> <span class="muted">${r.section}</span>`;
  const coll = r => html`${r.collector} (${REL_LABEL[r.relationship] || r.relationship})`;
  const decide = r => !b.canDecide ? '' : html`<form data-form="fmEarlyDecline" data-id="${r.id}" class="fm-act"><input class="input" name="reason" maxlength="300" placeholder="Reason for the parent (needed to decline)" required>
    <div class="fm-btns"><button class="btn btn-sm btn-danger">Decline</button><button type="button" class="btn btn-sm btn-primary" data-act="fmEarlyApprove" data-id="${r.id}">Approve</button></div></form>`;
  if (!b.pending.length && !b.today.length && !b.upcoming.length) return card('Early pick-ups', 'Requests from parents to collect a child early.', empty('No early pick-ups requested.'));
  return html`${b.pending.length ? card('Early pick-ups waiting for approval', count(b.pending.length, 'request'), table(['Student', 'Day and time', 'Collector', 'Reason', 'Parent', ''], b.pending.map(r => ({ cells: [who(r), html`${fmtDate(r.date)} · <b>${fmHm(r.time)}</b>`, coll(r), r.reason, r.parent, decide(r)] })))) : ''}
    ${card('Early pick-ups today', 'Approved. Have the child ready at the time shown.', b.today.length ? table(['Time', 'Student', 'Collector', 'Reason', ''], b.today.map(r => ({ cells: [html`<b>${fmHm(r.time)}</b>`, who(r), coll(r), r.reason, r.pickedUp ? badge('b-good', 'Picked up') : badge('b-watch', 'Expected')] }))) : empty('None today.'))}
    ${b.upcoming.length ? card('Coming early pick-ups', null, table(['Day and time', 'Student', 'Collector', 'Reason'], b.upcoming.map(r => ({ cells: [html`${fmtDate(r.date)} · ${fmHm(r.time)}`, who(r), coll(r), r.reason] })))) : ''}`;
}

// =============================================================== 5. parent-teacher meetings
SCREENS.meetings = async () => S.me.role === 'PARENT' ? parentMeetings() : teacherMeetings();

const fmDayKey = d => new Date(d).toLocaleDateString('en-CA', { timeZone: 'Asia/Qatar' });
function byDay(list, at) { const m = new Map(); list.forEach(x => { const k = fmDayKey(at(x)); if (!m.has(k)) m.set(k, []); m.get(k).push(x); }); return [...m]; }

async function teacherMeetings() {
  const t = await get('/api/meetings/teacher');
  const today = fmToday();
  const form = html`<form data-form="fmSlots" class="fm-form">
    <div class="fm-three">${field('Day', html`<input class="input" type="date" name="date" min="${today}" value="${today}" required>`)}${field('From', html`<input class="input" type="time" name="from" value="12:00" required>`)}${field('To', html`<input class="input" type="time" name="to" value="13:00" required>`)}</div>
    <div class="fm-three">${field('Each meeting', html`<select class="input" name="minutes">${[10, 15, 20, 30, 45].map(m => html`<option value="${m}" ${m === 15 ? raw('selected') : ''}>${m} ${'minutes'}</option>`)}</select>`)}
      ${field('How', html`<select class="input" name="mode">${Object.entries(FM_MODE).map(([k, v]) => html`<option value="${k}">${v}</option>`)}</select>`)}${field('Room, link or phone', html`<input class="input" name="place" maxlength="300" placeholder="Room 12, or https://meet…">`)}</div>
    <button class="btn btn-primary">Open these slots</button></form>`;
  const slot = s => html`<div class="fm-slot ${s.booked ? 'on' : ''}"><b>${pkTime(s.startsAt)}</b><span>${s.minutes} min · ${FM_MODE[s.mode]}${s.place ? ' · ' + s.place : ''}</span>
    ${s.booked ? html`<div class="fm-slot-who"><b>${s.parent}</b> · ${s.student}<small>${s.topic}</small></div>
      <details class="fm-more"><summary>Cancel</summary><form data-form="fmSlotRemove" data-id="${s.id}" class="row"><input class="input" name="reason" maxlength="300" placeholder="Why, for the parent" required><button class="btn btn-sm btn-danger">Cancel meeting</button></form></details>`
      : html`<span class="muted">Free</span><button class="btn btn-sm" data-act="fmSlotRemove" data-id="${s.id}">Remove</button>`}</div>`;
  return page('Meetings', 'Open times when parents can book a meeting with you. They see only the free slots; a slot can be booked by one family.',
    html`${card('Open meeting slots', 'The time is cut into meetings of the length you choose.', form)}
    ${card('Your slots', `${t.booked} booked · ${t.free} free`, t.slots.length ? byDay(t.slots, s => s.startsAt).map(([day, list]) => html`<h3 class="fm-day">${fmtDay(day)}</h3><div class="fm-slots">${list.map(slot)}</div>`) : empty('No slots open yet.'))}`);
}

async function parentMeetings() {
  const d = await get('/api/meetings/parent');
  const upcoming = d.bookings.filter(b => b.status === 'BOOKED' && new Date(b.startsAt) > new Date());
  const book = (c, t) => !t.free.length ? html`<p class="muted">No free times right now. You can send a message in Chat with teachers.</p>` : html`<form data-form="fmBook" data-student="${c.studentId}" class="fm-form">
    <div class="fm-pick">${byDay(t.free, s => s.startsAt).map(([day, list]) => html`<div><small>${fmtDay(day)}</small><div class="fm-times">${list.map(s => html`<label><input type="radio" name="slotId" value="${s.id}" required><span>${pkTime(s.startsAt)}<i>${FM_MODE[s.mode]}</i></span></label>`)}</div></div>`)}</div>
    <input class="input" name="topic" maxlength="500" placeholder="What would you like to talk about?" required><button class="btn btn-sm btn-primary">Book this time</button></form>`;
  return page('Teacher meetings', "Book a meeting with your child's teachers at a time they have opened.",
    html`${card('Your meetings', null, d.bookings.length ? table(['When', 'Teacher', 'Child', 'How', 'Topic', 'Status', ''], d.bookings.map(b => ({ cells: [html`<b>${fmt(b.startsAt)}</b>`, b.teacher, b.student,
      html`${FM_MODE[b.mode]}${b.place ? html`<div class="muted">${b.place.startsWith('https://') ? html`<a href="${b.place}" target="_blank" rel="noopener noreferrer">${'Join link'}</a>` : b.place}</div>` : ''}`, b.topic,
      html`${fmBadge(b.status)}${b.cancelReason ? html`<div class="muted">${b.cancelReason}</div>` : ''}`,
      upcoming.includes(b) ? html`<button class="btn btn-sm" data-act="fmBookingCancel" data-id="${b.id}" data-confirm="Cancel this meeting?">Cancel</button>` : ''] }))) : empty('No meetings booked yet.'))}
    ${d.children.length ? d.children.map(c => card(c.student + ' · ' + c.section, "Your child's teachers and their free times.", c.teachers.length
      ? html`<div class="fm-teachers">${c.teachers.map(t => html`<div class="fm-teacher"><div class="fm-child-top"><span class="pk-av pk-av-sm">${pkInit(t.teacher)}</span><div><b>${t.teacher}</b><small>${t.subjects}</small></div></div>${book(c, t)}</div>`)}</div>`
      : empty('No teachers are assigned to this class yet.'))) : card('Teachers', null, empty('No children in a class are linked to your account yet.'))}`);
}

// =============================================================== actions and forms
Object.assign(ACT, {
  fmExplain: d => {
    const f = document.getElementById('fmExcuseForm'); if (!f) return;
    f.elements.studentId.value = d.student; f.elements.fromDate.value = d.date; f.elements.toDate.value = '';
    f.scrollIntoView({ behavior: 'smooth', block: 'center' }); f.elements.category.focus();
  },
  fmExcuseCancel: async d => { await post(`/api/absences/${d.id}/cancel`); await refreshAfter('Note withdrawn.'); },
  fmExcuseAccept: async d => { await post(`/api/absences/${d.id}/review`, { decision: 'ACCEPTED' }); await refreshAfter('Accepted. The absent days are excused and the parent was told.'); },
  fmRemind: async d => { const r = await post(`/api/events/${d.id}/remind`); await refreshAfter(r.reminded + ' ' + trText('parents reminded.')); },
  fmEarlyCancel: async d => { await post(`/api/early-pickups/${d.id}/cancel`); await refreshAfter('Early pick-up cancelled.'); },
  fmEarlyApprove: async d => { await post(`/api/early-pickups/${d.id}/approve`); await refreshAfter('Approved. The parent and the class teachers were told.'); },
  fmSlotRemove: async d => { await post(`/api/meetings/slots/${d.id}/remove`, {}); await refreshAfter('Slot removed.'); },
  fmBookingCancel: async d => { await post(`/api/meetings/bookings/${d.id}/cancel`, {}); await refreshAfter('Meeting cancelled. The teacher was told.'); }
});
Object.assign(FORM, {
  fmRiskNote: async (f, d) => { await post(`/api/at-risk/${d.id}/note`, { note: f.note }); await refreshAfter('Note saved.'); },
  fmRiskTell: async (f, d) => { await post(`/api/at-risk/${d.id}/tell-parents`, { message: f.message || null }); await refreshAfter('The parents were told.'); },
  fmExcuse: async f => { await post('/api/absences', { studentId: f.studentId, fromDate: f.fromDate, toDate: f.toDate || null, category: f.category, details: f.details || '' }); await refreshAfter('Note sent. The attendance office will review it.'); },
  fmExcuseReject: async (f, d) => { await post(`/api/absences/${d.id}/review`, { decision: 'REJECTED', note: f.note }); await refreshAfter('Rejected. The parent was told why.'); },
  fmEvent: async f => {
    const sectionIds = Object.keys(f).filter(k => k.startsWith('sec_')).map(k => f[k]);
    if (!sectionIds.length) throw new Error(trText('Choose at least one class.'));
    const r = await post('/api/events', { title: f.title, details: f.details || '', location: f.location || '', date: f.date, startTime: f.startTime, endTime: f.endTime,
      cost: f.cost === '' || f.cost == null ? null : Number(f.cost), consentDeadline: f.consentDeadline, sectionIds });
    location.hash = '#/trips/' + r.event.id; await refreshAfter('Created. The parents were asked for permission.');
  },
  fmEventCancel: async (f, d) => { await post(`/api/events/${d.id}/cancel`, { reason: f.reason }); await refreshAfter('Cancelled. The parents were told.'); },
  fmConsent: async (f, d, form, btn) => {
    const decision = btn.value || 'APPROVED';
    await post(`/api/events/${d.id}/consent`, { studentId: d.student, decision, note: f.note || '', agreed: !!f.agreed });
    await refreshAfter(decision === 'APPROVED' ? 'Thank you. Your permission was sent.' : 'Your answer was sent.');
  },
  fmEarly: async f => { await post('/api/early-pickups', { studentId: f.studentId, date: f.date, time: f.time, collectorId: f.collectorId || null, reason: f.reason }); await refreshAfter('Request sent. You will be told when the school approves it.'); },
  fmEarlyDecline: async (f, d) => { await post(`/api/early-pickups/${d.id}/decline`, { reason: f.reason }); await refreshAfter('Declined. The parent was told why.'); },
  fmSlots: async f => { await post('/api/meetings/slots', { date: f.date, from: f.from, to: f.to, minutes: Number(f.minutes), mode: f.mode, place: f.place || '' }); await refreshAfter('Slots opened. Parents can book them now.'); },
  fmSlotRemove: async (f, d) => { await post(`/api/meetings/slots/${d.id}/remove`, { reason: f.reason }); await refreshAfter('Meeting cancelled. The parent was told.'); },
  fmBook: async (f, d) => { await post('/api/meetings/book', { slotId: f.slotId, studentId: d.student, topic: f.topic }); await refreshAfter('Booked. The teacher was told.'); }
});
Object.assign(ALERTS, {
  AT_RISK: ['Students at risk', "A student's average in a subject fell below the pass line, or a teacher's message about your child's progress."],
  FAMILY: ['Family requests', 'Absence notes, trip and event permission and parent-teacher meetings.']
});

// ---- Arabic
Object.assign(AR, {
  'Absence notes': 'أعذار الغياب', 'Trips & events': 'الرحلات والفعاليات', 'Teacher meetings': 'مواعيد المعلمين', 'Meetings': 'المواعيد', 'Students at risk': 'الطلاب المتعثرون',
  'Illness': 'مرض', 'Medical appointment': 'موعد طبي', 'Family matter': 'ظرف عائلي', 'Travel': 'سفر',
  'Waiting for review': 'بانتظار المراجعة', 'Accepted': 'مقبول', 'Not accepted': 'غير مقبول', 'Withdrawn': 'مسحوب', 'Approved': 'تمت الموافقة', 'Declined': 'مرفوض', 'Booked': 'محجوز',
  'In person': 'حضورياً', 'Online': 'عن بُعد', 'By phone': 'بالهاتف',
  'Follow up': 'متابعة', 'Save note': 'حفظ الملاحظة', 'Tell the parents': 'إبلاغ ولي الأمر', 'Tell the parents again': 'إبلاغ ولي الأمر مرة أخرى',
  'What is being done to help (extra lessons, a call home)': 'ما يُتخذ للمساعدة (حصص إضافية، اتصال بالأسرة)', 'A line for the parents (optional)': 'سطر لولي الأمر (اختياري)',
  'Parents told': 'تم إبلاغ ولي الأمر', 'No follow-up yet': 'لا متابعة بعد', 'At risk': 'متعثرون', 'High risk': 'تعثر شديد', 'Improved': 'تحسّنوا',
  'Of the open warnings': 'من الإنذارات المفتوحة', 'In the last 30 days': 'في آخر 30 يوماً', 'Open warnings': 'الإنذارات المفتوحة', 'lowest average first': 'الأقل معدلاً أولاً',
  'Average': 'المعدل', 'Lowest': 'الأدنى', 'Absent (30 days)': 'الغياب (30 يوماً)', 'Follow-up': 'المتابعة', 'No student is below the pass line. Well done!': 'لا يوجد طالب دون خط النجاح. أحسنتم!',
  'Recently improved': 'تحسّنوا مؤخراً', 'Warnings that cleared by themselves in the last 30 days.': 'إنذارات زالت تلقائياً في آخر 30 يوماً.', 'Average now': 'المعدل الآن', 'Cleared': 'زال في', 'Nothing yet.': 'لا شيء بعد.',
  'Tell the school why your child was, or will be, away. The attendance office reviews it; an accepted note turns the absence into an excused one.': 'أخبر المدرسة بسبب غياب طفلك أو غيابه المقبل. يراجع مكتب الحضور العذر، والعذر المقبول يجعل الغياب بعذر.',
  'Absences without a reason': 'غياب بلا سبب', 'Press Explain to fill in the note below.': 'اضغط «توضيح» لتعبئة العذر أدناه.', 'Explain': 'توضيح',
  'Send an absence note': 'إرسال عذر غياب', 'Up to 30 days back, or up to 60 days ahead for a planned absence.': 'حتى 30 يوماً مضت، أو حتى 60 يوماً قادمة لغياب مخطط له.',
  'Child': 'الطفل', 'First day': 'اليوم الأول', 'Last day (optional)': 'اليوم الأخير (اختياري)', 'Reason': 'السبب', 'Details (optional, needed for Other)': 'التفاصيل (اختياري، مطلوب لـ«آخر»)', 'Send the note': 'إرسال العذر',
  'Notes you sent': 'الأعذار المرسلة', 'Days': 'الأيام', 'School reply': 'رد المدرسة', 'Withdraw': 'سحب', 'Withdraw this note?': 'سحب هذا العذر؟', 'You have not sent any notes yet.': 'لم ترسل أي عذر بعد.',
  'No children are linked to your account yet.': 'لا يوجد أطفال مرتبطون بحسابك بعد.',
  'Notes sent by parents. Accepting one turns the absent days in it into excused leave, and any of those days still to come is excused when attendance is taken.': 'أعذار أرسلها أولياء الأمور. قبول العذر يجعل أيام الغياب فيه غياباً بعذر، وأي يوم قادم منها يُعذر عند تسجيل الحضور.',
  'No notes are waiting.': 'لا توجد أعذار بانتظار المراجعة.', 'Excused today': 'معذورون اليوم', 'Accepted notes that cover today.': 'أعذار مقبولة تشمل اليوم.', 'Nobody is excused today.': 'لا يوجد معذورون اليوم.',
  'Recently reviewed': 'رُوجعت مؤخراً', 'Decision': 'القرار', 'Reviewed by': 'راجعه', 'Nothing reviewed yet.': 'لم تتم مراجعة شيء بعد.', 'Accept': 'قبول', 'Reject': 'رفض', 'Reason for the parent': 'السبب لولي الأمر', 'Reason for the parent (needed to reject)': 'السبب لولي الأمر (مطلوب عند الرفض)', 'Reason for the parent (needed to decline)': 'السبب لولي الأمر (مطلوب عند الرفض)', 'Sent': 'أُرسل', 'from': 'من',
  'New trip or event': 'رحلة أو فعالية جديدة', 'Every parent of the classes you choose is asked for permission at once.': 'يُطلب الإذن فوراً من كل أولياء أمور الصفوف التي تختارها.',
  'Title': 'العنوان', 'Details for parents': 'تفاصيل لأولياء الأمور', 'What to bring, transport, supervision': 'ما يحضرونه، النقل، الإشراف', 'Place': 'المكان', 'Cost (QAR, optional)': 'التكلفة (ريال، اختياري)',
  'Day': 'اليوم', 'From': 'من', 'To': 'إلى', 'Parents answer by': 'آخر موعد لرد أولياء الأمور', 'Classes': 'الصفوف', 'Ask the parents': 'اطلب إذن أولياء الأمور', 'e.g. Science museum trip': 'مثال: رحلة إلى متحف العلوم',
  'You are not assigned to a class yet.': 'لم يتم إسنادك إلى صف بعد.', 'Trip or event': 'الرحلة أو الفعالية', 'When': 'الموعد', 'Organiser': 'المنظم', 'Approved · declined · waiting': 'موافقة · رفض · بانتظار', 'Answers': 'الردود',
  'Until': 'حتى', 'Answers closed': 'أُغلقت الردود', 'Cancelled': 'ملغاة', 'Ask parents for permission online and follow every answer.': 'اطلب إذن أولياء الأمور عبر الإنترنت وتابع كل رد.',
  'Coming up': 'القادمة', 'No trips or events coming up.': 'لا توجد رحلات أو فعاليات قادمة.', 'Recent': 'الأخيرة', 'The last 30 days.': 'آخر 30 يوماً.',
  'Details': 'التفاصيل', 'Cost': 'التكلفة', 'Waiting': 'بانتظار', 'Note from the parent': 'ملاحظة ولي الأمر', 'Answered by': 'أجاب', 'Remind': 'تذكير', 'waiting': 'بانتظار الرد',
  'Cancel': 'إلغاء', 'Every parent is told.': 'يُبلَّغ كل أولياء الأمور.', 'Why it is cancelled': 'سبب الإلغاء', 'Cancel it': 'إلغاؤها', 'Cancel this trip or event?': 'إلغاء هذه الرحلة أو الفعالية؟', 'Back': 'رجوع',
  'Your answer is needed': 'نحتاج إلى ردك', 'A note for the teacher (allergies, medicine), optional': 'ملاحظة للمعلم (حساسية، دواء)، اختياري', 'Approve': 'موافقة', 'Approved ✓': 'تمت الموافقة ✓', 'Decline': 'رفض', 'Answers are closed.': 'أُغلقت الردود.',
  'Answer by': 'الرد قبل', 'Give or refuse permission for your children to take part. You can change your answer until the deadline.': 'وافق أو ارفض مشاركة أطفالك. يمكنك تغيير ردك حتى الموعد النهائي.',
  'No trips or events need your answer.': 'لا توجد رحلات أو فعاليات تحتاج إلى ردك.', 'Answer': 'الرد',
  'Early pick-up': 'الاستلام المبكر', 'Ask ahead to collect your child before the end of the day. The school approves it and tells the class teacher; on the day, press Pick up as usual.': 'اطلب مسبقاً استلام طفلك قبل نهاية اليوم. توافق المدرسة وتبلغ معلم الصف؛ وفي اليوم اضغط زر الاستلام كالمعتاد.',
  'Time': 'الوقت', 'Who is collecting': 'من سيستلم', "e.g. Doctor's appointment": 'مثال: موعد طبيب', 'Request early pick-up': 'طلب استلام مبكر', 'Day and time': 'اليوم والوقت', 'Collector': 'المستلم', 'Status': 'الحالة',
  'Picked up': 'تم الاستلام', 'Cancel this early pick-up?': 'إلغاء هذا الاستلام المبكر؟', 'Early pick-ups waiting for approval': 'طلبات استلام مبكر بانتظار الموافقة', 'Parent': 'وليّ الأمر',
  'Early pick-ups today': 'الاستلام المبكر اليوم', 'Approved. Have the child ready at the time shown.': 'تمت الموافقة. جهّز الطالب في الوقت المحدد.', 'None today.': 'لا شيء اليوم.', 'Expected': 'متوقع',
  'Coming early pick-ups': 'استلام مبكر قادم', 'Early pick-ups': 'الاستلام المبكر', 'Requests from parents to collect a child early.': 'طلبات أولياء الأمور لاستلام الطالب مبكراً.', 'No early pick-ups requested.': 'لا توجد طلبات استلام مبكر.',
  'Each meeting': 'مدة كل موعد', 'minutes': 'دقيقة', 'How': 'الطريقة', 'Room, link or phone': 'القاعة أو الرابط أو الهاتف', 'Room 12, or https://meet…': 'القاعة 12، أو https://meet…', 'Open these slots': 'فتح هذه المواعيد',
  'Free': 'متاح', 'Remove': 'إزالة', 'Why, for the parent': 'السبب لولي الأمر', 'Cancel meeting': 'إلغاء الموعد',
  'Open times when parents can book a meeting with you. They see only the free slots; a slot can be booked by one family.': 'افتح أوقاتاً يحجز فيها أولياء الأمور موعداً معك. يرون المواعيد المتاحة فقط، ويُحجز الموعد لأسرة واحدة.',
  'Open meeting slots': 'فتح مواعيد للقاء', 'The time is cut into meetings of the length you choose.': 'يُقسَّم الوقت إلى مواعيد بالمدة التي تختارها.', 'Your slots': 'مواعيدك', 'No slots open yet.': 'لا توجد مواعيد مفتوحة بعد.',
  'No free times right now. You can send a message in Chat with teachers.': 'لا توجد أوقات متاحة الآن. يمكنك إرسال رسالة في محادثة المعلمين.', 'What would you like to talk about?': 'ما الذي تود مناقشته؟', 'Book this time': 'احجز هذا الموعد',
  "Book a meeting with your child's teachers at a time they have opened.": 'احجز موعداً مع معلمي طفلك في وقت فتحوه.', 'Your meetings': 'مواعيدك', 'Teacher': 'المعلم', 'Topic': 'الموضوع', 'Join link': 'رابط الانضمام',
  'Cancel this meeting?': 'إلغاء هذا الموعد؟', 'No meetings booked yet.': 'لا توجد مواعيد محجوزة بعد.', "Your child's teachers and their free times.": 'معلمو طفلك وأوقاتهم المتاحة.', 'No teachers are assigned to this class yet.': 'لم يُسند معلمون لهذا الصف بعد.',
  'Teachers': 'المعلمون', 'No children in a class are linked to your account yet.': 'لا يوجد أطفال في صف مرتبطون بحسابك بعد.',
  'Note withdrawn.': 'تم سحب العذر.', 'Accepted. The absent days are excused and the parent was told.': 'تم القبول. أصبحت أيام الغياب بعذر وأُبلغ ولي الأمر.', 'parents reminded.': 'من أولياء الأمور تم تذكيرهم.',
  'Early pick-up cancelled.': 'أُلغي الاستلام المبكر.', 'Approved. The parent and the class teachers were told.': 'تمت الموافقة وأُبلغ ولي الأمر ومعلمو الصف.', 'Slot removed.': 'أُزيل الموعد.', 'Meeting cancelled. The teacher was told.': 'أُلغي الموعد وأُبلغ المعلم.',
  'Note saved.': 'حُفظت الملاحظة.', 'The parents were told.': 'تم إبلاغ ولي الأمر.', 'Note sent. The attendance office will review it.': 'أُرسل العذر وسيراجعه مكتب الحضور.', 'Rejected. The parent was told why.': 'تم الرفض وأُبلغ ولي الأمر بالسبب.',
  'Choose at least one class.': 'اختر صفاً واحداً على الأقل.', 'Created. The parents were asked for permission.': 'تم الإنشاء وطُلب إذن أولياء الأمور.', 'Cancelled. The parents were told.': 'أُلغيت وأُبلغ أولياء الأمور.',
  'Thank you. Your permission was sent.': 'شكراً لك. أُرسلت موافقتك.', 'Your answer was sent.': 'أُرسل ردك.', 'Request sent. You will be told when the school approves it.': 'أُرسل الطلب وسنبلغك عند موافقة المدرسة.',
  'Declined. The parent was told why.': 'تم الرفض وأُبلغ ولي الأمر بالسبب.', 'Slots opened. Parents can book them now.': 'فُتحت المواعيد ويمكن لأولياء الأمور حجزها الآن.', 'Meeting cancelled. The parent was told.': 'أُلغي الموعد وأُبلغ ولي الأمر.', 'Booked. The teacher was told.': 'تم الحجز وأُبلغ المعلم.',
  'Family requests': 'طلبات الأسر', 'Absence notes, trip and event permission and parent-teacher meetings.': 'أعذار الغياب، وأذونات الرحلات والفعاليات، ومواعيد أولياء الأمور مع المعلمين.',
  "A student's average in a subject fell below the pass line, or a teacher's message about your child's progress.": 'انخفض معدل طالب في مادة دون خط النجاح، أو رسالة من المعلم عن تقدم طفلك.'
});
