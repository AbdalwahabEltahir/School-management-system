/* School IT admin screens: Users & access (role tabs, search, KPIs), Academic setup in tabs, the Timetable and ID cards.
   Everything is read and written through the endpoints the older screens already used. */
'use strict';

const eaTabs = (base, tabs, cur) => html`<nav class="ea-tabs">${tabs.map(([k, label]) => html`<a class="${k === cur ? 'on' : ''}" href="${base}${k}">${label}</a>`)}</nav>`;
const eaPick = (label, base, list, cur) => html`<label class="ea-sel ea-pick"><span>${label}</span><select data-nav="${base}">${list.map(([v, t]) => html`<option value="${v}" ${v === cur ? raw('selected') : ''}>${t}</option>`)}</select></label>`;
document.addEventListener('change', e => { const el = e.target; if (el.matches && el.matches('select[data-nav]')) location.hash = el.dataset.nav + el.value; });
const eaField = (label, input) => html`<div class="field"><label>${label}</label>${input}</div>`;
const secName = s => s.grade + s.name;

// ============================================================== Users & access
const peopleOld = SCREENS.people;
SCREENS.people = async (arg, q) => {
  if (!isItRole()) return peopleOld(arg, q);
  invalidate('people');
  const [people, sections, invites, groups] = await Promise.all([loadPeople(), get('/api/academic/sections'), get('/api/invitations').catch(() => []), get('/api/people/role-groups').catch(() => [])]);
  const rosters = await Promise.all(sections.map(s => get(`/api/academic/sections/${s.id}/students`).catch(() => []).then(r => [s, r])));
  const classOf = {}; rosters.forEach(([s, r]) => r.forEach(x => { classOf[String(x.email).toLowerCase()] = 'Grade ' + secName(s); }));
  const list = Object.values(people).filter(p => !['LEADERSHIP', 'EAA_ADMIN'].includes(p.role)).sort((a, b) => a.displayName.localeCompare(b.displayName));
  const byRole = {}; list.forEach(p => { byRole[p.role] = (byRole[p.role] || 0) + 1; });
  const roleOrder = ['STUDENT', 'PARENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT', 'ATTENDANCE_OFFICER', 'SCHOOL_IT'].filter(r => byRole[r]);
  const invited = invites.filter(i => i.status === 'PENDING').length, expired = invites.filter(i => i.status === 'EXPIRED').length;
  const statusBadge = s => s === 'ACTIVE' ? badge('b-good', 'Active') : s === 'NEW' ? badge('b-internal', 'Invited') : s === 'LEFT' ? badge('b-neutral', 'Deactivated') : badge('b-watch', title(s));
  const rows = list.map(p => {
    const cls = classOf[String(p.email).toLowerCase()] || '';
    return { attrs: { role: p.role, status: p.status, q: [p.displayName, p.email, p.studentCode, ROLE_LABEL[p.role], personSite(p), cls].join(' ').toLowerCase() },
      cells: [eaWho(p.displayName, p.studentCode || p.email), ROLE_LABEL[p.role] || title(p.role), cls || '—', personSite(p) || '—', statusBadge(p.status),
        html`<details class="ea-more"><summary class="btn btn-sm">Manage</summary><div class="ea-more-box">
          <form data-form="person" data-id="${p.id}" class="row"><select name="role">${options(ROLES.filter(x => x !== 'LEADERSHIP' && x !== 'EAA_ADMIN'), p.role, x => ROLE_LABEL[x])}</select><select name="status">${options(['NEW', 'ACTIVE', 'LEAVING', 'LEFT'], p.status)}</select><button class="btn btn-sm btn-primary">Save</button></form>
          <div class="row"><button class="btn btn-sm" data-act="welcome" data-id="${p.id}">Send welcome</button><button class="btn btn-sm" data-act="editPerson" data-id="${p.id}">Edit</button>${p.id !== S.me.id && p.role !== 'SCHOOL_IT' ? html`<button class="btn btn-sm btn-danger" data-act="deletePerson" data-id="${p.id}" data-confirm="Delete this person permanently?">Delete</button>` : ''}</div></div></details>`] };
  });
  const chips = [['', 'All users', list.length], ...roleOrder.map(r => [r, ROLE_LABEL[r], byRole[r]])];
  return page('Users & access', 'Everyone signs in with their own school account. There are no Hafez passwords.', html`
    <div class="ea-toolbar ea-peoplebar" data-people>
      <div class="ea-chips">${chips.map(([r, t, n], i) => html`<button type="button" class="${i ? '' : 'on'}" data-prole="${r}">${t} <em>${n}</em></button>`)}</div></div>
    <div class="ea-toolbar" data-people><label class="ea-search">${sIc('search')}<input type="search" placeholder="Search by name, role, school or class" data-psearch autocomplete="off"></label>
      <label class="ea-sel"><span>Status</span><select data-pstatus><option value="">Any</option><option value="ACTIVE">Active</option><option value="NEW">Invited</option><option value="LEFT">Deactivated</option></select></label></div>
    ${eaaCard('All users', list.length + ' people', eaTable('eaPeople', ['Name and ID', 'Role', 'Class', 'School', 'Status', ''], rows))}
    ${eaaCard('Add a user', 'They sign in with their own Google account. Use the same e-mail they sign in with.', html`<form data-form="addPerson" class="ea-form">
      ${eaField('E-mail', html`<input class="input" type="email" name="email" placeholder="name@school.edu" required>`)}${eaField('Full name', html`<input class="input" name="name" required>`)}
      ${eaField('Role', html`<select class="input" name="role">${options(['TEACHER', 'COORDINATOR', 'PRINCIPAL', 'ATTENDANCE_OFFICER', 'STUDENT', 'PARENT'], 'TEACHER', x => ROLE_LABEL[x])}</select>`)}
      ${eaField('Child (only for a parent)', html`<select class="input" name="studentId"><option value="">—</option>${list.filter(p => p.role === 'STUDENT').map(p => html`<option value="${p.id}">${p.displayName}</option>`)}</select>`)}
      <button class="btn btn-primary ea-form-go">Add user</button></form>`)}
    ${eaaCard('Role groups', 'A directory group gives its members a role at their next sync.', html`${groups.length ? eaTable('eaGroups', ['Directory group', 'Role'], groups.map(g => ({ cells: [g.directoryGroup, ROLE_LABEL[g.role]] }))) : ''}
      <form data-form="roleGroup" class="ea-form" style="margin-top:12px">${eaField('Directory group', html`<input class="input" name="directoryGroup" placeholder="group@school.edu" required>`)}
      ${eaField('Role', html`<select class="input" name="role">${options(ROLES.filter(x => x !== 'LEADERSHIP' && x !== 'EAA_ADMIN'), 'TEACHER', x => ROLE_LABEL[x])}</select>`)}<button class="btn ea-form-go">Save mapping</button></form>`)}`,
    html`<button class="btn" data-act="syncPeople">Sync directory now</button>`,
    [['Total users', list.length, '', 'brand', 'users', 'In this school'], ['Active', list.filter(p => p.status === 'ACTIVE').length, '', 'good', 'check', 'Can sign in'],
     ['Invited', invited, '', 'slate', 'list', 'Not yet activated', '#/invitations'], ['Invitation expired', expired, '', expired ? 'watch' : 'good', 'clock', 'Resend to activate', '#/invitations'],
     ['Deactivated', list.filter(p => p.status === 'LEFT').length, '', 'bad', 'pause', 'Records kept']]);
};
function eaPeopleFilter() {
  const text = (document.querySelector('[data-psearch]') || {}).value || '', st = (document.querySelector('[data-pstatus]') || {}).value || '', role = (document.querySelector('[data-prole].on') || { dataset: {} }).dataset.prole || '';
  const q = text.trim().toLowerCase();
  document.querySelectorAll('#eaPeople tbody tr').forEach(tr => { tr.hidden = (q && !tr.dataset.q.includes(q)) || (st && tr.dataset.status !== st) || (role && tr.dataset.role !== role); });
}
document.addEventListener('input', e => { if (e.target.matches && e.target.matches('[data-psearch]')) eaPeopleFilter(); });
document.addEventListener('change', e => { if (e.target.matches && e.target.matches('[data-pstatus]')) eaPeopleFilter(); });
document.addEventListener('click', e => { const b = e.target.closest && e.target.closest('[data-prole]'); if (!b) return; document.querySelectorAll('[data-prole]').forEach(x => x.classList.toggle('on', x === b)); eaPeopleFilter(); });

// ============================================================== Academic setup, one tab at a time
const AC_TABS = [['terms', 'Sessions & terms'], ['classes', 'Classes & sections'], ['subjects', 'Subjects'], ['teachers', 'Teacher assignments'], ['enrol', 'Student enrolment']];
const sessLabel = s => String(s || '').replace('-', '/');
const academicOld = SCREENS.academic;
SCREENS.academic = async (arg, q) => {
  if (!isItRole()) return academicOld(arg, q);
  const tab = AC_TABS.some(t => t[0] === q.get('tab')) ? q.get('tab') : 'terms';
  const sections = await get('/api/academic/sections');
  const sid = q.get('s') || (sections[0] && sections[0].id);
  const tabs = eaTabs('#/academic?tab=', AC_TABS, tab);
  const secPick = base => sections.length ? eaPick('Section', base, sections.map(s => [s.id, 'Grade ' + secName(s)]), sid) : '';
  let body = '';
  if (tab === 'terms') {
    const ses = await get('/api/academic/sessions');
    const now = new Date(), y0 = now.getMonth() >= 7 ? now.getFullYear() : now.getFullYear() - 1;
    const session = q.get('session') || ses.current || (y0 + '-' + (y0 + 1));
    const list = [...new Set([...ses.all, session])].sort().reverse();
    const terms = await get('/api/academic/terms?session=' + session);
    body = html`<div class="ea-toolbar">${eaPick('Session', '#/academic?tab=terms&session=', list.map(s => [s, sessLabel(s) + (s === ses.current ? ' (current)' : '')]), session)}
        <form data-form="eaNewSession" class="ea-inline"><label class="ea-sel"><span>New session starting</span><input type="number" name="year" min="2000" max="2100" value="${y0 + 1}" required></label><button class="btn btn-sm">Add session</button></form></div>
      ${eaaCard('Terms · ' + sessLabel(session), 'Teachers can only set assessments for a term that exists here.', terms.length ? eaTable('eaTerms', ['Term', 'Name', 'Starts', 'Ends', ''], terms.map(t => ({ cells: [t.number, t.name, fmtDate(t.startsOn), fmtDate(t.endsOn),
        html`<div class="ea-row-act"><button class="btn btn-sm" data-act="eaEditTerm" data-number="${t.number}" data-name="${t.name}" data-starts="${t.startsOn}" data-ends="${t.endsOn}">Edit</button></div>`] }))) : empty('No term in this session yet. Add term 1 below.'))}
      ${eaaCard('Add or edit a term', 'Saving a term number that already exists in this session replaces it.', html`<form data-form="eaTerm" id="eaTermForm" class="ea-form"><input type="hidden" name="session" value="${session}">
        ${eaField('Term', html`<select class="input" name="number"><option>1</option><option>2</option><option>3</option></select>`)}${eaField('Name', html`<input class="input" name="name" placeholder="Term 1" required>`)}
        ${eaField('Starts', html`<input class="input" type="date" name="startsOn" required>`)}${eaField('Ends', html`<input class="input" type="date" name="endsOn" required>`)}<button class="btn btn-primary ea-form-go">Save term</button></form>`)}`;
  } else if (tab === 'classes') {
    const grade = Number(q.get('grade')) || (sections[0] ? sections[0].grade : 1), inGrade = sections.filter(s => s.grade === grade);
    body = html`<div class="ea-split"><aside class="ea-card ea-grades"><div class="ea-ch"><h2><i></i>Classes</h2></div>${Array.from({ length: 12 }, (_, i) => i + 1).map(g => html`<a class="${g === grade ? 'on' : ''}" href="#/academic?tab=classes&grade=${g}">Grade ${g} <em>${sections.filter(s => s.grade === g).length}</em></a>`)}
        <p class="muted">Grades 1 to 12 are fixed. Add sections to a grade.</p></aside>
      ${eaaCard('Grade ' + grade + ' · sections', inGrade.length + ' ' + trText(inGrade.length === 1 ? 'section' : 'sections') + ' · ' + count(inGrade.reduce((n, s) => n + s.students, 0), 'student'), html`<div class="ea-seccards">${inGrade.map(s => html`<div class="ea-sec"><div><b>${secName(s)}</b><span>${count(s.students, 'student')}</span></div>
        <div class="row"><a class="btn btn-sm" href="#/academic?tab=enrol&s=${s.id}">Students</a><a class="btn btn-sm" href="#/academic?tab=teachers&s=${s.id}">Teachers</a><a class="btn btn-sm" href="#/timetable?s=${s.id}">Timetable</a></div></div>`)}
        <form data-form="section" class="ea-sec ea-sec-add"><input type="hidden" name="grade" value="${grade}"><b>Add section</b><input class="input" name="name" placeholder="Section, e.g. B" required><button class="btn btn-primary btn-sm">Add</button></form></div>`)}</div>`;
  } else if (tab === 'subjects') {
    const subjects = await get('/api/subjects');
    body = html`${eaaCard('Subjects', count(subjects.length, 'subject'), subjects.length ? eaTable('eaSubjects', ['Subject', 'Code', 'Grades', 'Max periods / week', ''], subjects.map(x => ({ cells: [html`<b>${x.name}</b>`, x.code || '—', x.gradeFrom + '–' + x.gradeTo, x.maxPeriods,
        html`<div class="ea-row-act"><button class="btn btn-sm" data-act="eaEditSubject" data-id="${x.id}" data-name="${x.name}" data-code="${x.code || ''}" data-from="${x.gradeFrom}" data-to="${x.gradeTo}" data-max="${x.maxPeriods}">Edit</button></div>`] }))) : empty('No subject yet.'),
        html`<a class="btn btn-sm btn-primary" href="#eaSubjectCard" data-act="scrollTo" data-target="eaSubjectCard">＋ Add subject</a>`)}
      <div id="eaSubjectCard">${eaaCard('Add or edit a subject', 'Choose a subject in the table to change it.', html`<form data-form="eaSubject" id="eaSubjectForm" class="ea-form"><input type="hidden" name="id" value="">
        ${eaField('Subject name', html`<input class="input" name="name" placeholder="e.g. Mathematics" required>`)}${eaField('Code', html`<input class="input" name="code" placeholder="MATH" maxlength="12">`)}
        ${eaField('From grade', html`<input class="input" type="number" name="gradeFrom" min="1" max="12" value="1" required>`)}${eaField('To grade', html`<input class="input" type="number" name="gradeTo" min="1" max="12" value="12" required>`)}
        ${eaField('Max periods / week', html`<input class="input" type="number" name="maxPeriods" min="1" max="40" value="5" required>`)}<button class="btn btn-primary ea-form-go">Save subject</button></form>`)}</div>
      ${eaaCard('Document types', 'The default sensitivity Hafez gives that kind of document.', html`<form data-form="docType" class="ea-form">${eaField('Type name', html`<input class="input" name="name" required>`)}${eaField('Sensitivity', html`<select class="input" name="sensitivity">${options(SENS, 'INTERNAL')}</select>`)}<button class="btn ea-form-go">Save</button></form>`)}`;
  } else if (tab === 'teachers') {
    invalidate('people'); await loadPeople();
    const staff = Object.values(S.people).filter(p => ['TEACHER', 'COORDINATOR'].includes(p.role));
    const rows = sid ? await get('/api/schooladmin/assignments?section=' + sid) : [];
    const cur = sections.find(s => s.id === sid);
    body = html`${eaaCard(cur ? 'Grade ' + secName(cur) + ' · teacher assignments' : 'Teacher assignments', null, rows.length ? eaTable('eaAssign', ['Subject', 'Teacher', ''], rows.map(r => ({ cells: [html`<b>${r.subject}</b>`,
      r.teacher ? eaWho(r.teacher, ROLE_LABEL[r.teacherRole] || '') : html`<span class="ea-warn">⚠ Not assigned</span>`,
      html`<details class="ea-more"><summary class="btn btn-sm ${r.teacher ? '' : 'btn-primary'}">${r.teacher ? 'Change' : 'Assign'}</summary><div class="ea-more-box"><form data-form="assign" data-section="${sid}" class="row"><input type="hidden" name="subjectId" value="${r.subjectId}">
        <select class="input" name="teacherId">${staff.map(p => html`<option value="${p.id}" ${p.id === r.teacherId ? raw('selected') : ''}>${p.displayName}</option>`)}</select><button class="btn btn-sm btn-primary">Save</button></form></div></details>`] }))) : empty('Add subjects first, under the Subjects tab.'), secPick('#/academic?tab=teachers&s='))}`;
  } else {
    const [rows, reqs, unenrolled, ses] = await Promise.all([get('/api/schooladmin/enrolments'), get('/api/enrol/requests'), get('/api/enrol/unenrolled'), get('/api/academic/sessions')]);
    const grades = [...new Set(sections.map(s => s.grade))].sort((a, b) => a - b);
    const trs = rows.map(r => ({ attrs: { grade: r.grade, section: r.grade + r.section, q: (r.name + ' ' + r.email + ' ' + (r.studentCode || '') + ' ' + r.grade + r.section).toLowerCase() },
      cells: [eaWho(r.name, r.studentCode || r.email), html`<span class="ea-chip-s">${r.grade}${r.section}</span>`, fmtDate(r.since),
        html`<details class="ea-more"><summary class="btn btn-sm">Move section</summary><div class="ea-more-box"><form data-form="eaMove" class="row"><input type="hidden" name="studentEmail" value="${r.email}">
          <select class="input" name="sectionId">${sections.map(s => html`<option value="${s.id}" ${s.id === r.sectionId ? raw('selected') : ''}>Grade ${secName(s)}</option>`)}</select><button class="btn btn-sm btn-primary">Move</button></form></div></details>`] }));
    body = html`${reqs.length ? eaaCard('Class requests', reqs.length + ' waiting for you', eaTable('eaReqs', ['Student', 'ID', 'Class asked for', 'Asked by', ''], reqs.map(r => ({ cells: [r.student, html`<span class="mono">${r.studentCode}</span>`, r.section, r.requestedBy, html`<div class="ea-row-act"><button class="btn btn-sm btn-primary" data-act="approveReq" data-id="${r.id}">Enrol</button><button class="btn btn-sm" data-act="declineReq" data-id="${r.id}">Decline</button></div>`] })))) : ''}
      ${eaBar('#eaEnrol', 'Search students', [['Class', 'grade', grades.map(g => [String(g), 'Grade ' + g])], ['Section', 'section', sections.map(s => [secName(s), secName(s)])]])}
      ${eaaCard('Student enrolment · ' + sessLabel(ses.current || ''), 'A student is enrolled in one section at a time.', rows.length ? eaTable('eaEnrol', ['Student', 'Section', 'Since', ''], trs) : empty('Nobody is enrolled yet.'),
        html`<a class="btn btn-sm btn-primary" href="#eaEnrolCard" data-act="scrollTo" data-target="eaEnrolCard">＋ Enrol student</a>`)}
      <div id="eaEnrolCard">${eaaCard('Enrol a student', unenrolled.length ? 'Students who do not have a class yet.' : 'Every student already has a class. New students appear here once the school has added them.', unenrolled.length ? html`<form data-form="enrol" data-section="${sid}" class="ea-form">
        ${eaField('Student', html`<select class="input" name="studentEmail">${unenrolled.map(p => html`<option value="${p.email}">${p.name} · ${p.studentCode || ''} (${p.email})</option>`)}</select>`)}
        ${eaField('Section', html`<select class="input" id="eaEnrolSec">${sections.map(s => html`<option value="${s.id}" ${s.id === sid ? raw('selected') : ''}>Grade ${secName(s)}</option>`)}</select>`)}<button class="btn btn-primary ea-form-go">Enrol</button></form>` : '')}</div>`;
  }
  return page('Academic setup', 'Choose what you want to set up. Each tab holds one part of the school year.', html`${tabs}${body}`);
};
Object.assign(FORM, {
  eaNewSession: async f => { const y = Number(f.year); location.hash = '#/academic?tab=terms&session=' + y + '-' + (y + 1); },
  eaTerm: async f => { await put('/api/academic/terms', { session: f.session, number: Number(f.number), name: f.name, startsOn: f.startsOn, endsOn: f.endsOn }); await refreshAfter('Term saved.'); },
  eaSubject: async f => {
    const body = { name: f.name, code: f.code || '', gradeFrom: Number(f.gradeFrom), gradeTo: Number(f.gradeTo), maxPeriods: Number(f.maxPeriods) };
    if (f.id) await put('/api/subjects/' + f.id, body); else await post('/api/subjects', body);
    invalidate('subjects'); await refreshAfter('Subject saved.');
  },
  eaMove: async f => { await post(`/api/academic/sections/${f.sectionId}/students`, { studentEmail: f.studentEmail }); await refreshAfter('Student moved.'); }
});
Object.assign(ACT, {
  eaEditTerm: async d => { const f = document.getElementById('eaTermForm'); if (!f) return; f.elements.number.value = d.number; f.elements.name.value = d.name; f.elements.startsOn.value = d.starts; f.elements.endsOn.value = d.ends; f.scrollIntoView({ behavior: 'smooth', block: 'center' }); },
  eaEditSubject: async d => { const f = document.getElementById('eaSubjectForm'); if (!f) return; f.elements.id.value = d.id; f.elements.name.value = d.name; f.elements.code.value = d.code; f.elements.gradeFrom.value = d.from; f.elements.gradeTo.value = d.to; f.elements.maxPeriods.value = d.max; f.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
});
// the enrol form posts to the section chosen in its own drop-down
document.addEventListener('change', e => { if (e.target.id === 'eaEnrolSec') { const f = e.target.closest('.ea-card') && e.target.closest('.ea-card').querySelector('form[data-form="enrol"]'); if (f) f.dataset.section = e.target.value; } });

// ============================================================== Timetable
const ttColour = name => { let h = 0; for (const c of String(name)) h = (h * 31 + c.charCodeAt(0)) % 6; return ['rose', 'green', 'blue', 'amber', 'violet', 'teal'][h]; };
SCREENS.timetable = async (arg, q) => {
  if (!isItRole()) { location.hash = '#/' + home(); return ''; }
  const sections = await get('/api/academic/sections');
  if (!sections.length) return page('Timetable', 'Which subject is taught in each class, period by period.', empty('Add a class section first, under Academic setup.'));
  const sid = q.get('s') || sections[0].id;
  const [lessons, assigns] = await Promise.all([get(`/api/academic/timetable?sectionId=${sid}`), get(`/api/academic/sections/${sid}/assignments`)]);
  const periods = Array.from({ length: Math.max(7, ...lessons.map(l => l.period)) }, (_, i) => i + 1);
  const cur = sections.find(s => s.id === sid);
  const grid = html`<div class="scroll-x"><table class="ea-tt"><thead><tr><th></th>${periods.map(p => html`<th>Period ${p}</th>`)}</tr></thead><tbody>${DAYS.map((d, di) => html`<tr><th>${d}</th>${periods.map(p => {
    const l = lessons.find(x => x.day === di && x.period === p);
    return html`<td data-ttday="${di}" data-ttper="${p}">${l ? html`<div class="ea-ls ea-ls-${ttColour(l.subject)}"><b>${l.subject}</b><small>${l.teacher}${l.room ? ' · ' + l.room : ''}</small></div>` : html`<div class="ea-ls ea-ls-free">Free</div>`}</td>`;
  })}</tr>`)}</tbody></table></div>`;
  return page('Timetable', 'Which subject is taught in each class, period by period. A class, a teacher or a room cannot be in two places in one period.', html`
    <div class="ea-toolbar">${eaPick('Section', '#/timetable?s=', sections.map(s => [s.id, 'Grade ' + secName(s)]), sid)}</div>
    ${eaaCard('Grade ' + secName(cur) + ' · Sunday to Thursday', 'Press a period to fill in the form below.', grid)}
    ${assigns.length ? eaaCard('Add a lesson', 'The subject must already have a teacher for this section.', html`<form data-form="lesson" data-section="${sid}" class="ea-form" id="eaLessonForm">${eaField('Day', html`<select class="input" name="day">${DAYS.map((d, i) => html`<option value="${i}">${d}</option>`)}</select>`)}
      ${eaField('Period', html`<input class="input" type="number" name="period" min="1" max="12" required>`)}${eaField('Subject', html`<select class="input" name="subjectId">${assigns.map(a => html`<option value="${a.subjectId}">${a.subject} · ${a.teacher}</option>`)}</select>`)}
      ${eaField('Room (optional)', html`<input class="input" name="room">`)}<button class="btn btn-primary ea-form-go">Add lesson</button></form>`) : eaaCard('Add a lesson', null, empty('Assign a teacher to a subject for this section first (Academic setup › Teacher assignments).'))}
    ${lessons.length ? eaaCard('All lessons', count(lessons.length, 'lesson'), eaTable('eaLessons', ['Day', 'Period', 'Subject', 'Teacher', 'Room', ''], lessons.slice().sort((a, b) => a.day - b.day || a.period - b.period).map(l => ({ cells: [DAYS[l.day], l.period, l.subject, l.teacher, l.room || '', html`<button class="btn btn-sm btn-danger" data-act="removeLesson" data-id="${l.id}" data-confirm="Remove this lesson?">Remove</button>`] })))) : ''}`);
};
document.addEventListener('click', e => {
  const td = e.target.closest && e.target.closest('td[data-ttday]'); if (!td) return;
  const f = document.getElementById('eaLessonForm'); if (!f) return;
  f.elements.day.value = td.dataset.ttday; f.elements.period.value = td.dataset.ttper; f.scrollIntoView({ behavior: 'smooth', block: 'center' }); f.elements.subjectId.focus();
});


// ============================================================== Notices: the list on the left, one notice on the right
const noticesOld = SCREENS.noticesAdmin;
SCREENS.noticesAdmin = async (arg, q) => {
  if (!isItRole()) return noticesOld(arg, q);
  const list = await get('/api/notices/manage'), now = Date.now();
  const stateOf = n => new Date(n.endsAt) <= now ? ['b-neutral', 'Ended'] : new Date(n.startsAt) > now ? ['b-watch', 'Scheduled'] : ['b-good', 'Showing'];
  const pick = list.find(n => n.id === q.get('n')) || null;
  const row = n => html`<a class="ea-nrow ${pick && pick.id === n.id ? 'on' : ''}" href="#/noticesAdmin?n=${n.id}"><span>${inLang(n.titleEn, n.titleAr)}</span>${badge(stateOf(n)[0], stateOf(n)[1])}</a>`;
  const nowLocal = new Date(Date.now() - new Date().getTimezoneOffset() * 6e4).toISOString().slice(0, 16);
  const editor = pick ? eaaCard(inLang(pick.titleEn, pick.titleAr), 'Notices cannot be edited once published. Remove it and write a new one.', html`
      <div class="ea-bi"><div><span class="ea-lang">EN</span> <b>English</b>${eaField('Heading', html`<input class="input" value="${pick.titleEn}" readonly>`)}${eaField('Text', html`<textarea class="input" rows="4" readonly>${pick.bodyEn}</textarea>`)}</div>
        <div dir="rtl"><span class="ea-lang ea-lang-ar">AR</span> <b>العربية</b>${eaField('العنوان', html`<input class="input" value="${pick.titleAr || ''}" readonly>`)}${eaField('النص', html`<textarea class="input" rows="4" readonly>${pick.bodyAr || ''}</textarea>`)}</div></div>
      <div class="ea-facts"><div><small>Shown to</small><b>${title(pick.audience)}</b></div><div><small>From</small><b>${fmt(pick.startsAt)}</b></div><div><small>To</small><b>${fmt(pick.endsAt)}</b></div><div><small>State</small>${badge(stateOf(pick)[0], stateOf(pick)[1])}</div></div>
      <div class="row"><a class="btn" href="#/noticesAdmin">＋ New notice</a><button class="btn btn-danger" data-act="removeNotice" data-id="${pick.id}" data-confirm="Remove this notice?">Remove</button></div>`,
      badge(stateOf(pick)[0], stateOf(pick)[1]))
    : eaaCard('New notice · editor', 'A notice shows only between its start and end, and needs its Arabic text.', html`<form data-form="notice" id="eaNoticeForm">
      <div class="ea-bi"><div><span class="ea-lang">EN</span> <b>English</b>${eaField('Heading', html`<input class="input" name="titleEn" required>`)}${eaField('Text', html`<textarea class="input" name="bodyEn" rows="4" required></textarea>`)}</div>
        <div dir="rtl"><span class="ea-lang ea-lang-ar">AR</span> <b>العربية</b>${eaField('العنوان', html`<input class="input" name="titleAr" required>`)}${eaField('النص', html`<textarea class="input" name="bodyAr" rows="4" placeholder="أدخل النص العربي" required></textarea>`)}</div></div>
      <p class="ea-need" id="eaNeedAr" hidden>⚠ Arabic text is required before publishing</p>
      <div class="ea-form">${eaField('Starts', html`<input class="input" type="datetime-local" name="startsAt" value="${nowLocal}" required>`)}${eaField('Ends', html`<input class="input" type="datetime-local" name="endsAt" required>`)}
        ${eaField('Shown to', html`<select class="input" name="audience"><option value="ALL">Everyone</option><option value="STAFF">Staff</option><option value="STUDENTS">Students</option></select>`)}</div>
      <div class="row" style="margin-top:14px"><button type="button" class="btn" data-act="eaNoticePreview">👁 Preview</button><button class="btn btn-primary">➤ Publish notice</button></div>
      <div id="eaNoticePrev" class="ea-nprev" hidden></div></form>`, badge('b-watch', 'Draft'));
  return page('Notices', 'Write a notice in English and Arabic and choose when it shows and to whom.', html`
    <div class="ea-split ea-nsplit"><aside class="ea-card ea-nlist"><div class="ea-ch"><h2><i></i>Notices</h2><a class="btn btn-sm btn-primary" href="#/noticesAdmin">＋ New</a></div>
      ${list.length ? list.map(row) : html`<p class="muted">No notice yet.</p>`}</aside>${editor}</div>`);
};
Object.assign(ACT, { eaNoticePreview: async () => {
  const f = document.getElementById('eaNoticeForm'), box = document.getElementById('eaNoticePrev'); if (!f || !box) return;
  const ar = I18N.lang === 'ar', t = (ar ? f.elements.titleAr.value : f.elements.titleEn.value) || (ar ? f.elements.titleEn.value : f.elements.titleAr.value), b = (ar ? f.elements.bodyAr.value : f.elements.bodyEn.value) || (ar ? f.elements.bodyEn.value : f.elements.bodyAr.value);
  box.hidden = false; box.innerHTML = '<small>' + esc(trText('This is how families will see it')) + '</small><b>' + esc(t || '—') + '</b><p>' + esc(b || '') + '</p>';
} });
document.addEventListener('input', e => {
  const f = e.target.closest && e.target.closest('#eaNoticeForm'); if (!f) return;
  const need = document.getElementById('eaNeedAr'); if (need) need.hidden = !!(f.elements.titleAr.value.trim() && f.elements.bodyAr.value.trim()) || !(f.elements.titleEn.value.trim() || f.elements.bodyEn.value.trim());
});

// ============================================================== Tracking: attendance, marks and results
FORM.eaQuery = async (f, d) => { const p = new URLSearchParams(); Object.entries(f).forEach(([k, v]) => { if (v) p.set(k, v); }); if (d.tab) p.set('tab', d.tab); location.hash = d.base + (p.toString() ? '?' + p : ''); };
const eaFilterForm = (base, tab, fields) => html`<form data-form="eaQuery" data-base="${base}" data-tab="${tab || ''}" class="ea-toolbar ea-logbar">${fields}<button class="btn btn-primary">Apply</button><a class="btn" href="${base}${tab ? '?tab=' + tab : ''}">Clear</a></form>`;
const eaSelF = (name, label, list, cur) => html`<label class="ea-sel"><span>${label}</span><select name="${name}">${list.map(([v, t]) => html`<option value="${v}" ${String(v) === String(cur) ? raw('selected') : ''}>${t}</option>`)}</select></label>`;
const eaDateF = (name, label, val) => html`<label class="ea-sel"><span>${label}</span><input type="date" name="${name}" value="${val || ''}"></label>`;
const eaPct = (n, total) => total ? Math.round(n * 1000 / total) / 10 : 0;
window.EA_CSV = null;
Object.assign(ACT, { eaCsv: async () => {
  const c = window.EA_CSV; if (!c) return;
  const csv = [c.head, ...c.rows].map(r => r.map(v => '"' + String(v ?? '').replace(/"/g, '""') + '"').join(',')).join('\n');
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })); a.download = c.name; document.body.appendChild(a); a.click(); a.remove();
} });
const eaCsvBtn = () => html`<button class="btn btn-sm" data-act="eaCsv">Export CSV</button>`;

const attOld = SCREENS.attendance;
SCREENS.attendance = async (arg, q) => {
  if (!isItRole()) return attOld(arg, q);
  const tab = q.get('tab') === 'daily' ? 'daily' : 'summary', params = new URLSearchParams();
  ['from', 'to', 'grade'].forEach(k => { if (q.get(k)) params.set(k, q.get(k)); });
  const [v, sections] = await Promise.all([get('/api/schooladmin/attendance?' + params), get('/api/academic/sections')]);
  const grades = [...new Set(sections.map(s => s.grade))].sort((a, b) => a - b);
  const P = v.sections.reduce((n, r) => n + r.present, 0), A = v.sections.reduce((n, r) => n + r.absent, 0), L = v.sections.reduce((n, r) => n + r.leave, 0), T = P + A + L;
  const rate = r => eaPct(r.present, r.present + r.absent + r.leave);
  const filters = eaFilterForm('#/attendance', tab, html`${eaSelF('grade', 'Class', [['', 'All grades'], ...grades.map(g => [g, 'Grade ' + g])], q.get('grade') || '')}${eaDateF('from', 'From', v.from)}${eaDateF('to', 'To', v.to)}`);
  const tabsBar = html`<nav class="ea-tabs"><a class="${tab === 'summary' ? 'on' : ''}" href="#/attendance?${new URLSearchParams([...params, ['tab', 'summary']])}">Summary</a><a class="${tab === 'daily' ? 'on' : ''}" href="#/attendance?${new URLSearchParams([...params, ['tab', 'daily']])}">Daily records</a></nav>`;
  let body;
  if (tab === 'summary') {
    window.EA_CSV = { name: 'attendance-by-section.csv', head: ['Section', 'Students', 'Present', 'Absent', 'Leave', 'Rate %'], rows: v.sections.map(r => [r.grade + r.section, r.students, r.present, r.absent, r.leave, rate(r)]) };
    const max = Math.max(1, ...v.sections.map(rate));
    body = html`${eaaCard('Attendance by section', fmtDate(v.from) + ' – ' + fmtDate(v.to), v.sections.length ? html`<div class="ea-bars">${v.sections.map(r => html`<div class="ea-bar"><em>${rate(r)}%</em><i style="height:${Math.max(4, rate(r) / max * 140)}px"></i><span>${r.grade}${r.section}</span></div>`)}</div>` : empty('No sections yet.'))}
      ${eaaCard('Summary by section', null, v.sections.length ? eaTable('eaAttSum', ['Section', 'Students', 'Present', 'Absent', 'Leave', 'Rate'], v.sections.map(r => ({ cells: [html`<b>Grade ${r.grade}${r.section}</b>`, r.students, r.present, r.absent, r.leave, html`<b>${rate(r)}%</b>`] }))) : empty('No sections yet.'), eaCsvBtn())}`;
  } else {
    window.EA_CSV = { name: 'attendance-daily.csv', head: ['Date', 'Section', 'Present', 'Absent', 'Leave'], rows: v.daily.map(r => [r.date, r.grade + r.section, r.present, r.absent, r.leave]) };
    body = eaaCard('Daily records', v.daily.length + ' ' + trText('records'), v.daily.length ? eaTable('eaAttDay', ['Date', 'Section', 'Present', 'Absent', 'Leave'], v.daily.map(r => ({ cells: [fmtDate(r.date), html`<b>Grade ${r.grade}${r.section}</b>`, r.present, r.absent, r.leave] }))) : empty('No attendance was recorded in this period.'), eaCsvBtn());
  }
  return page('Attendance', 'Attendance is recorded by the school\'s attendance clerk. You can follow it here.', html`${tabsBar}${filters}${body}
    <p class="ea-note">Rates are the share of present, absent and leave records in the period.</p>`, '',
    [['Present', eaPct(P, T) + '%', '', 'good', 'check', 'Of all records'], ['Absent', eaPct(A, T) + '%', '', A ? 'bad' : 'good', 'warn', 'Of all records'], ['On leave', eaPct(L, T) + '%', '', 'watch', 'clock', 'Of all records'], ['Records', T, '', 'slate', 'list', 'In this period']]);
};

const MK_TABS = [['submitted', 'Submitted marks'], ['assessments', 'Assessments'], ['awaiting', 'Awaiting entry']];
const mkBadge = st => st === 'SUBMITTED' ? badge('b-good', 'Submitted') : st === 'DRAFT' ? badge('b-internal', 'Draft') : st === 'NOT_ENTERED' ? badge('b-watch', 'Not entered') : '—';
const marksOld = SCREENS.marks;
SCREENS.marks = async (arg, q) => {
  if (!isItRole()) return marksOld(arg, q);
  const tab = MK_TABS.some(t => t[0] === q.get('tab')) ? q.get('tab') : 'submitted', params = new URLSearchParams();
  ['term', 'grade'].forEach(k => { if (q.get(k)) params.set(k, q.get(k)); });
  const [v, sections] = await Promise.all([get('/api/schooladmin/marks?' + params), get('/api/academic/sections')]);
  const grades = [...new Set(sections.map(s => s.grade))].sort((a, b) => a - b);
  const tabsBar = html`<nav class="ea-tabs">${MK_TABS.map(([k, t]) => html`<a class="${k === tab ? 'on' : ''}" href="#/marks?${new URLSearchParams([...params, ['tab', k]])}">${t}</a>`)}</nav>`;
  const filters = eaFilterForm('#/marks', tab, html`${eaSelF('term', 'Term', [[1, 'Term 1'], [2, 'Term 2'], [3, 'Term 3']], v.term)}${eaSelF('grade', 'Class', [['', 'All grades'], ...grades.map(g => [g, 'Grade ' + g])], q.get('grade') || '')}`);
  let body;
  if (tab === 'submitted') {
    body = eaaCard('Term ' + v.term + ' · submission status', 'Marks are locked when a teacher submits them.', v.rows.length ? eaTable('eaMarks', ['Section', ...v.subjects.map(s => s.name)], v.rows.map(r => ({ cells: [html`<b>Grade ${r.grade}${r.section}</b>`, ...r.cells.map(c => mkBadge(c.status))] }))) : empty('No teacher is assigned to a subject yet.'));
  } else if (tab === 'assessments') {
    body = eaaCard('Assessments · Term ' + v.term, count(v.assessments.length, 'assessment'), v.assessments.length ? eaTable('eaAss', ['Section', 'Subject', 'Assessment', 'Out of', 'Entered', 'Status', ''], v.assessments.map(a => ({ cells: [a.section, a.subject, html`<b>${a.name}</b>`, a.maxMark, a.entered + ' / ' + a.students,
      a.published ? badge('b-good', 'Published') : a.submitted ? badge('b-good', 'Submitted') : a.entered ? badge('b-internal', 'In progress') : badge('b-neutral', 'Not started'),
      a.submitted && !a.published ? html`<div class="ea-row-act"><button class="btn btn-sm" data-act="unlockMarks" data-id="${a.id}">Unlock</button></div>` : ''] }))) : empty('No assessment has been set for this term yet.'));
  } else {
    const waiting = []; v.rows.forEach(r => r.cells.forEach((c, i) => { if (c.status && c.status !== 'SUBMITTED') waiting.push({ r, c, s: v.subjects[i] }); }));
    body = eaaCard('Awaiting entry · Term ' + v.term, waiting.length + ' ' + trText('waiting'), waiting.length ? eaTable('eaAwait', ['Section', 'Subject', 'Status'], waiting.map(w => ({ cells: [html`<b>Grade ${w.r.grade}${w.r.section}</b>`, w.s.name, mkBadge(w.c.status)] }))) : empty('Every teacher has submitted their marks.'));
  }
  return page('Marks', 'Follow which teachers have entered and submitted their marks.', html`${tabsBar}${filters}${body}`, '',
    [['Submitted', v.submitted, '', 'good', 'check', 'Locked by teachers'], ['Draft', v.draft, '', 'slate', 'list', 'Entered, not submitted'], ['Not entered', v.notEntered, '', v.notEntered ? 'watch' : 'good', 'clock', 'Waiting for teachers'],
     ['Sections ready to publish', v.ready, '', 'brand', 'school', 'Review and publish', '#/results?term=' + v.term]]);
};

const resultsOld = SCREENS.results;
SCREENS.results = async (arg, q) => {
  if (!isItRole()) return resultsOld(arg, q);
  const tab = q.get('tab') === 'publication' ? 'publication' : 'readiness', params = new URLSearchParams();
  ['term', 'grade'].forEach(k => { if (q.get(k)) params.set(k, q.get(k)); });
  const [v, sections] = await Promise.all([get('/api/schooladmin/results?' + params), get('/api/academic/sections')]);
  const grades = [...new Set(sections.map(s => s.grade))].sort((a, b) => a - b);
  const tabsBar = html`<nav class="ea-tabs"><a class="${tab === 'readiness' ? 'on' : ''}" href="#/results?${new URLSearchParams([...params, ['tab', 'readiness']])}">Section readiness</a><a class="${tab === 'publication' ? 'on' : ''}" href="#/results?${new URLSearchParams([...params, ['tab', 'publication']])}">Publication</a></nav>`;
  const filters = eaFilterForm('#/results', tab, html`${eaSelF('term', 'Term', [[1, 'Term 1'], [2, 'Term 2'], [3, 'Term 3']], v.term)}${eaSelF('grade', 'Class', [['', 'All grades'], ...grades.map(g => [g, 'Grade ' + g])], q.get('grade') || '')}`);
  const stateText = r => r.status === 'PUBLISHED' ? html`<span class="ea-st ea-st-good">✓ ${trText('Published')} ${fmtDate(r.publishedAt)}</span>` : r.status === 'READY' ? html`<span class="ea-st ea-st-lock">🔒 ${trText('All marks locked')}</span>` : html`<span class="ea-st ea-st-wait">◷ ${trText('Marks not complete')} · ${r.submitted}/${r.subjects}</span>`;
  const act = r => r.status === 'PUBLISHED' ? html`<button class="btn btn-sm" data-act="unpublishResults" data-section="${r.sectionId}" data-term="${v.term}">Unpublish</button>`
    : r.status === 'READY' ? html`<button class="btn btn-sm btn-primary" data-act="publishResults" data-section="${r.sectionId}" data-term="${v.term}" data-confirm="Publish these results? Students and parents will see them.">Publish</button>` : html`<span class="muted">Not ready</span>`;
  const body = tab === 'readiness'
    ? eaaCard('Term ' + v.term + ' results by section', null, v.rows.length ? eaTable('eaRes', ['Section', 'Status', 'Students', ''], v.rows.map(r => ({ cells: [html`<b>Grade ${r.grade}${r.section}</b>`, stateText(r), r.students, html`<div class="ea-row-act">${act(r)}</div>`] }))) : empty('No sections yet.'))
    : eaaCard('Published results · Term ' + v.term, null, v.rows.some(r => r.status === 'PUBLISHED') ? eaTable('eaPub', ['Section', 'Published', ''], v.rows.filter(r => r.status === 'PUBLISHED').map(r => ({ cells: [html`<b>Grade ${r.grade}${r.section}</b>`, fmtDate(r.publishedAt), html`<div class="ea-row-act"><button class="btn btn-sm" data-act="unpublishResults" data-section="${r.sectionId}" data-term="${v.term}">Unpublish</button></div>`] }))) : empty('Nothing is published for this term yet.'));
  return page('Results', 'Publish a section\'s results when every subject has submitted its marks.', html`${tabsBar}${filters}${body}`, '',
    [['Published', v.published, '', 'good', 'check', 'Families can see these'], ['Ready to publish', v.ready, '', 'brand', 'shield', 'All marks locked'], ['Marks not complete', v.incomplete, '', v.incomplete ? 'watch' : 'good', 'clock', 'Waiting for teachers']]);
};

// ============================================================== ID cards
let eaId = { mode: 'section', fields: { photo: true, name: true, id: true, cls: true, session: true, dob: false }, accent: '#8A1538' };
try { Object.assign(eaId, JSON.parse(localStorage.getItem('eaIdCards') || '{}')); } catch { /* defaults */ }
const eaIdSave = () => { try { localStorage.setItem('eaIdCards', JSON.stringify({ fields: eaId.fields, accent: eaId.accent })); } catch { /* optional */ } };
const eaSession = () => { const d = new Date(), y = d.getFullYear(); return d.getMonth() >= 7 ? y + '–' + (y + 1) : (y - 1) + '–' + y; };
function eaCardHtml(p, cls, schoolName) {
  const f = eaId.fields, ini = initialsOf(p.displayName || p.name);
  return `<div class="idc" style="--ac:${esc(eaId.accent)}"><div class="idc-l"><img src="app/eaa-logo.png" alt=""><b>${esc(schoolName || '')}</b></div>
    <div class="idc-r">${f.photo ? `<span class="idc-av">${esc(ini)}</span>` : ''}<div class="idc-t">${f.name ? `<b>${esc(p.displayName || p.name)}</b>` : ''}${f.id ? `<small>${esc(p.studentCode || p.email || '')}</small>` : ''}
    <div class="idc-tags">${f.cls && cls ? `<span>${esc(cls)}</span>` : ''}${f.session ? `<span>${esc(eaSession())}</span>` : ''}</div></div>
    <footer>${esc(ROLE_LABEL[p.role] || '')} · ${esc(schoolName || '')}</footer></div></div>`;
}
SCREENS.idcards = async () => {
  if (!isItRole()) { location.hash = '#/' + home(); return ''; }
  const [people, sections, sites] = await Promise.all([loadPeople(), get('/api/academic/sections'), loadSites()]);
  const rosters = await Promise.all(sections.map(s => get(`/api/academic/sections/${s.id}/students`).catch(() => []).then(r => [s, r])));
  const classOf = {}, byEmail = {}; Object.values(people).forEach(p => { byEmail[String(p.email).toLowerCase()] = p; });
  rosters.forEach(([s, r]) => r.forEach(x => { classOf[String(x.email).toLowerCase()] = 'Grade ' + secName(s); }));
  window.EA_ID = { people: Object.values(people), classOf, rosters, school: ((sites || {})[S.me.siteId] || {}).name || '' };
  const list = window.EA_ID.people.filter(p => !['LEADERSHIP', 'EAA_ADMIN'].includes(p.role)).sort((a, b) => a.displayName.localeCompare(b.displayName));
  const sample = list.find(p => p.role === 'STUDENT') || list[0];
  const toggles = [['photo', 'Photo'], ['name', 'Full name'], ['id', 'School ID'], ['cls', 'Class and section'], ['session', 'Session'], ['dob', 'Date of birth']];
  const roles = ['STUDENT', 'PARENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL', 'PRINCIPAL_ASSISTANT', 'ATTENDANCE_OFFICER', 'SCHOOL_IT'];
  return page('ID cards', 'Choose what goes on the card, then print one card, a whole section or a role group.', html`
    <div class="ea-two ea-idgrid">
      ${eaaCard('Template', null, html`<div class="ea-fields">${toggles.map(([k, t]) => html`<label class="ea-switch"><span>${t}</span><input type="checkbox" data-idf="${k}" ${eaId.fields[k] ? raw('checked') : ''} ${k === 'dob' ? raw('disabled') : ''}><i></i></label>`)}</div>
        <p class="muted" style="margin:6px 0 0">Date of birth is not stored in Hafez, so it cannot be printed.</p>
        <div class="ea-form" style="margin-top:12px">${eaField('Accent colour', html`<input class="input" type="color" id="eaAccent" value="${eaId.accent}">`)}</div>`)}
      <div class="ea-stack">
        ${eaaCard('Live preview', null, html`<div class="ea-idprev" id="eaIdPrev"></div>`)}
        ${eaaCard('Print', null, html`<div class="ea-chips ea-idmodes"><button type="button" data-idmode="one" class="${eaId.mode === 'one' ? 'on' : ''}">One person</button><button type="button" data-idmode="section" class="${eaId.mode === 'section' ? 'on' : ''}">A section</button><button type="button" data-idmode="role" class="${eaId.mode === 'role' ? 'on' : ''}">A role group</button></div>
          <div data-idpane="one" ${eaId.mode === 'one' ? '' : raw('hidden')}>${eaField('Person', html`<select class="input" id="eaIdOne">${list.map(p => html`<option value="${p.id}">${p.displayName} · ${ROLE_LABEL[p.role]}</option>`)}</select>`)}</div>
          <div data-idpane="section" ${eaId.mode === 'section' ? '' : raw('hidden')}>${eaField('Section', html`<select class="input" id="eaIdSec">${sections.map(s => html`<option value="${s.id}">Grade ${secName(s)} · ${count(s.students, 'student')}</option>`)}</select>`)}</div>
          <div data-idpane="role" ${eaId.mode === 'role' ? '' : raw('hidden')}>${eaField('Role group', html`<select class="input" id="eaIdRole">${roles.map(r => html`<option value="${r}">${ROLE_LABEL[r]}</option>`)}</select>`)}</div>
          <p class="muted" id="eaIdNote"></p>
          <button class="btn btn-primary ea-wide" data-act="idPrint">Generate PDF</button>
          <small class="muted">Opens the print window: choose "Save as PDF". Cards show initials, because photos are kept on each person's own profile.</small>`)}
      </div></div>`);
};
window.eaIdSample = () => {
  const d = window.EA_ID; if (!d) return null;
  return d.people.find(p => p.role === 'STUDENT') || d.people[0];
};
function eaIdDraw() {
  const d = window.EA_ID, box = document.getElementById('eaIdPrev'); if (!d || !box) return;
  const p = eaIdSample(); box.innerHTML = p ? eaCardHtml(p, d.classOf[String(p.email).toLowerCase()], d.school) : '';
}
function eaIdPeople() {
  const d = window.EA_ID, v = id => (document.getElementById(id) || {}).value;
  if (eaId.mode === 'one') return d.people.filter(p => p.id === v('eaIdOne'));
  if (eaId.mode === 'role') return d.people.filter(p => p.role === v('eaIdRole'));
  const hit = d.rosters.find(([s]) => s.id === v('eaIdSec')); const emails = new Set((hit ? hit[1] : []).map(x => String(x.email).toLowerCase()));
  return d.people.filter(p => emails.has(String(p.email).toLowerCase()));
}
document.addEventListener('change', e => {
  const t = e.target; if (!t.matches) return;
  if (t.matches('[data-idf]')) { eaId.fields[t.dataset.idf] = t.checked; eaIdSave(); eaIdDraw(); }
  if (t.id === 'eaAccent') { eaId.accent = t.value; eaIdSave(); eaIdDraw(); }
});
document.addEventListener('click', e => {
  const m = e.target.closest && e.target.closest('[data-idmode]'); if (!m) return;
  eaId.mode = m.dataset.idmode;
  document.querySelectorAll('[data-idmode]').forEach(x => x.classList.toggle('on', x === m));
  document.querySelectorAll('[data-idpane]').forEach(x => { x.hidden = x.dataset.idpane !== eaId.mode; });
});
Object.assign(ACT, {
  idPrint: async () => {
    const d = window.EA_ID, who = eaIdPeople();
    if (!who.length) { toast('There is nobody to print for this choice.', true); return; }
    const missing = who.length;
    let sheet = document.getElementById('eaIdSheet'); if (sheet) sheet.remove();
    sheet = document.createElement('div'); sheet.id = 'eaIdSheet';
    sheet.innerHTML = who.map(p => eaCardHtml(p, d.classOf[String(p.email).toLowerCase()], d.school)).join('');
    document.body.appendChild(sheet);
    const note = document.getElementById('eaIdNote'); if (note) note.textContent = count(missing, 'card') + ' ready.';
    window.print();
    setTimeout(() => sheet.remove(), 1000);
  }
});
const eaAfterIt = typeof eaEnhance === 'function' ? eaEnhance : null;
eaEnhance = function () { if (eaAfterIt) eaAfterIt(); if (document.getElementById('eaIdPrev')) eaIdDraw(); };

Object.assign(AR, {
  'Sessions & terms': 'الأعوام والفصول', 'New session starting': 'عام جديد يبدأ في', 'Add session': 'إضافة عام', 'Add or edit a term': 'إضافة فصل أو تعديله', 'Saving a term number that already exists in this session replaces it.': 'حفظ رقم فصل موجود في هذا العام يستبدله.',
  'No term in this session yet. Add term 1 below.': 'لا توجد فصول في هذا العام بعد. أضف الفصل الأول أدناه.', 'Max periods / week': 'الحد الأقصى للحصص أسبوعياً', 'Grades': 'الصفوف', 'Add or edit a subject': 'إضافة مادة أو تعديلها',
  'Choose a subject in the table to change it.': 'اختر مادة من الجدول لتعديلها.', 'From grade': 'من الصف', 'To grade': 'إلى الصف', 'Save subject': 'حفظ المادة', 'Not assigned': 'غير مسندة', 
  'Add subjects first, under the Subjects tab.': 'أضف المواد أولاً من تبويب المواد.', 'Search students': 'ابحث عن طالب', 'Since': 'منذ', 'Move section': 'نقل إلى شعبة', 'Move': 'نقل', 'Student moved.': 'تم نقل الطالب.',
  'A student is enrolled in one section at a time.': 'يُسجَّل الطالب في شعبة واحدة في كل مرة.', 'Nobody is enrolled yet.': 'لا يوجد طلاب مسجلون بعد.', 'Students who do not have a class yet.': 'الطلاب الذين ليس لهم صف بعد.',
  'Enrol student': 'تسجيل طالب', 'Student enrolment': 'تسجيل الطلاب', 'Subject saved.': 'تم حفظ المادة.', 
  'Summary': 'الملخص', 'Daily records': 'السجلات اليومية', 'All grades': 'كل الصفوف', 'Attendance by section': 'الحضور حسب الشعبة', 'Summary by section': 'الملخص حسب الشعبة',
  'Rate': 'النسبة', 'Records': 'السجلات', 'In this period': 'في هذه الفترة', 'Of all records': 'من كل السجلات', 'records': 'سجلات', 'waiting': 'بالانتظار',
  'Attendance is recorded by the school\'s attendance clerk. You can follow it here.': 'يسجّل كاتب الحضور الحضور في المدرسة. يمكنك متابعته هنا.', 'Rates are the share of present, absent and leave records in the period.': 'النسب هي حصة سجلات الحاضر والغائب والإجازة في الفترة.',
  'No attendance was recorded in this period.': 'لم يُسجَّل حضور في هذه الفترة.', 'Export CSV': 'تصدير CSV', 'Submitted marks': 'الدرجات المسلّمة', 'Awaiting entry': 'بانتظار الإدخال', 
  'Follow which teachers have entered and submitted their marks.': 'تابع من أدخل درجاته من المعلمين وسلّمها.', 'Not entered': 'لم تُدخل', 'Locked by teachers': 'قفلها المعلمون', 'Entered, not submitted': 'أُدخلت ولم تُسلَّم',
  'Waiting for teachers': 'بانتظار المعلمين', 'Sections ready to publish': 'شعب جاهزة للنشر', 'Review and publish': 'راجع وانشر', 'Marks are locked when a teacher submits them.': 'تُقفل الدرجات عند تسليم المعلم لها.', 'Unlock': 'فتح القفل',
  'Entered': 'المُدخل', 'Every teacher has submitted their marks.': 'سلّم كل المعلمين درجاتهم.', 'No teacher is assigned to a subject yet.': 'لم يُسند معلم إلى مادة بعد.',
  'No assessment has been set for this term yet.': 'لم يُوضع تقييم لهذا الفصل بعد.', 'Section readiness': 'جاهزية الشعب', 'Publication': 'النشر', 'Publish a section\'s results when every subject has submitted its marks.': 'انشر نتائج الشعبة عندما تسلّم كل مادة درجاتها.',
  'Ready to publish': 'جاهزة للنشر', 'All marks locked': 'كل الدرجات مقفلة', 'Marks not complete': 'الدرجات غير مكتملة', 'Families can see these': 'تراها الأسر', 'Not ready': 'غير جاهزة',
  'Publish these results? Students and parents will see them.': 'نشر هذه النتائج؟ سيراها الطلاب وأولياء الأمور.', 'Nothing is published for this term yet.': 'لم يُنشر شيء لهذا الفصل بعد.', 'Published results': 'النتائج المنشورة', 
});
Object.assign(AR, {
  'New notice · editor': 'إعلان جديد · المحرر', 
  'Write a notice in English and Arabic and choose when it shows and to whom.': 'اكتب إعلاناً بالإنجليزية والعربية واختر متى يظهر ولمن.', 'Notices cannot be edited once published. Remove it and write a new one.': 'لا يمكن تعديل الإعلان بعد نشره. احذفه واكتب إعلاناً جديداً.',
  'Heading': 'العنوان', 'Text': 'النص', 'No notice yet.': 'لا توجد إعلانات بعد.',
  'Arabic text is required before publishing': 'النص العربي مطلوب قبل النشر', 'This is how families will see it': 'هكذا ستراه الأسر', 
});
Object.assign(AR, {
  'Users & access': 'المستخدمون والصلاحيات', 'Search by name, role, school or class': 'ابحث بالاسم أو الدور أو المدرسة أو الصف', 'All users': 'كل المستخدمين', 'Name and ID': 'الاسم والرقم', 'Manage': 'إدارة',
  'Deactivated': 'معطّل', 'Total users': 'إجمالي المستخدمين', 'Can sign in': 'يمكنهم تسجيل الدخول', 'Not yet activated': 'لم يُفعَّل بعد', 'Records kept': 'السجلات محفوظة',
  'Add a user': 'إضافة مستخدم', 'Add user': 'إضافة مستخدم', 'Child (only for a parent)': 'الابن (لولي الأمر فقط)', 'Choose what you want to set up. Each tab holds one part of the school year.': 'اختر ما تريد إعداده. كل تبويب يحمل جزءاً من العام الدراسي.',
  'Classes & sections': 'الصفوف والشعب', 'Teacher assignments': 'إسناد المعلمين', 'Student enrolment': 'تسجيل الطلاب', 'Add or change a term': 'إضافة فصل أو تعديله',
  'Saving a term number that exists replaces it.': 'حفظ رقم فصل موجود يستبدله.', 'Classes': 'الصفوف', 'Grades 1 to 12 are fixed. Add sections to a grade.': 'الصفوف من 1 إلى 12 ثابتة. أضف شعباً إلى الصف.',
  'Add section': 'إضافة شعبة', 'Add a subject': 'إضافة مادة', 'Subject name': 'اسم المادة', 
  'Assign a teacher': 'إسناد معلم', 'Students in this section': 'طلاب هذه الشعبة', 'Enrol a student': 'تسجيل طالب', 
  'No student in this section yet.': 'لا يوجد طلاب في هذه الشعبة بعد.', 'No teacher assigned yet.': 'لم يُسند معلم بعد.', 
  'Which subject is taught in each class, period by period. A class, a teacher or a room cannot be in two places in one period.': 'أي مادة تُدرَّس في كل صف وفي كل حصة. لا يمكن أن يكون الصف أو المعلم أو القاعة في مكانين في الحصة نفسها.',
  'Press a period to fill in the form below.': 'اضغط على حصة لتعبئة النموذج أدناه.', 'Add a lesson': 'إضافة حصة', 'All lessons': 'كل الحصص', 'Free': 'فارغة', 
  'The subject must already have a teacher for this section.': 'يجب أن يكون للمادة معلم في هذه الشعبة.', 'ID cards': 'بطاقات الهوية', 'Choose what goes on the card, then print one card, a whole section or a role group.': 'اختر ما يظهر على البطاقة ثم اطبع بطاقة واحدة أو شعبة كاملة أو مجموعة دور.',
  'Template': 'القالب', 'Live preview': 'معاينة مباشرة', 'Print': 'طباعة', 'Photo': 'الصورة', 'School ID': 'رقم المدرسة', 'Class and section': 'الصف والشعبة', 'Session': 'العام', 'Date of birth': 'تاريخ الميلاد',
  'Date of birth is not stored in Hafez, so it cannot be printed.': 'تاريخ الميلاد غير محفوظ في حافظ لذا لا يمكن طباعته.', 'Accent colour': 'اللون المميز', 'One person': 'شخص واحد', 'A section': 'شعبة', 'A role group': 'مجموعة دور',
  'Role group': 'مجموعة الدور', 'Generate PDF': 'إنشاء PDF', 'Opens the print window: choose "Save as PDF". Cards show initials, because photos are kept on each person\'s own profile.': 'يفتح نافذة الطباعة: اختر «حفظ بصيغة PDF». تظهر الأحرف الأولى لأن الصور محفوظة في ملف كل شخص.',
  'There is nobody to print for this choice.': 'لا أحد للطباعة بحسب هذا الاختيار.'
});
