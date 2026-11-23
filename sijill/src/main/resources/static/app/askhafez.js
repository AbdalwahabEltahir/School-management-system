/* Ask Hafez: the floating helper for teachers, parents and students. It lives outside the app's page area, so it stays
   open while the person moves between screens. It only reads: the answers come from /api/ask, which looks things up as the
   signed-in person. A parent can switch it off for each child under Preferences. */
'use strict';

const AskHafez = (() => {
  const TXT = {
    en: {
      open: 'Ask Hafez', title: 'Ask Hafez', sub: 'Your questions about school, answered from Hafez.', placeholder: 'Type your question…', send: 'Send', close: 'Close',
      clear: 'New chat', thinking: 'Looking it up…', sources: 'From', helpful: 'Helpful', notHelpful: 'Not helpful', thanks: 'Thanks for the feedback.',
      note: 'Ask Hafez only reads. It cannot change anything. Questions are saved in the activity log.', error: 'Sorry, something went wrong. Please try again.',
      hello: { PARENT: 'Hello! Ask me about your children\'s marks, attendance, homework, timetable or points, or how to pick them up.',
        STUDENT: 'Hello! Ask me about your homework, quizzes, timetable, marks or points.',
        TEACHER: 'Hello! Ask me about homework due, who has not handed it in, your timetable, or how to do things in Hafez.' },
      chips: { PARENT: ['Is there homework due tomorrow?', 'How do I pick up my child early?', 'How are my children doing this term?'],
        STUDENT: ['What do I have on Sunday?', 'How many points to the next level?', 'Is there homework due tomorrow?'],
        TEACHER: ['Who has not handed in homework this week?', 'What is my timetable tomorrow?', 'What homework is due this week?'] }
    },
    ar: {
      open: 'اسأل حافظ', title: 'اسأل حافظ', sub: 'أسئلتك عن المدرسة، والإجابة من حافظ.', placeholder: 'اكتب سؤالك…', send: 'إرسال', close: 'إغلاق',
      clear: 'محادثة جديدة', thinking: 'جارٍ البحث…', sources: 'المصدر', helpful: 'مفيدة', notHelpful: 'غير مفيدة', thanks: 'شكراً على ملاحظتك.',
      note: 'اسأل حافظ يقرأ فقط ولا يستطيع تغيير أي شيء. تُحفظ الأسئلة في سجل النشاط.', error: 'عذراً، حدث خطأ. حاول مرة أخرى.',
      hello: { PARENT: 'مرحباً! اسألني عن درجات أبنائك وحضورهم وواجباتهم وجدولهم ونقاطهم، أو كيف تستلمهم.',
        STUDENT: 'مرحباً! اسألني عن واجباتك واختباراتك وجدولك ودرجاتك ونقاطك.',
        TEACHER: 'مرحباً! اسألني عن الواجبات المستحقة ومن لم يسلّمها وجدولك وكيفية استخدام حافظ.' },
      chips: { PARENT: ['هل يوجد واجب مستحق غداً؟', 'كيف أستلم ابني مبكراً؟', 'كيف حال أبنائي هذا الفصل؟'],
        STUDENT: ['ماذا لدي يوم الأحد؟', 'كم نقطة أحتاج للمستوى التالي؟', 'هل يوجد واجب مستحق غداً؟'],
        TEACHER: ['من لم يسلّم الواجب هذا الأسبوع؟', 'ما جدولي غداً؟', 'ما الواجبات المستحقة هذا الأسبوع؟'] }
    }
  };
  const t = () => TXT[I18N.lang === 'ar' ? 'ar' : 'en'];
  const esc_ = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const ICON = '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 01-11.6 7.1L4 20.5l1.5-4.6A8 8 0 1121 12z"/><path d="M9 11h.01M12 11h.01M15 11h.01"/></svg>';

  let me = null, root = null, open = false, busy = false, lastStatusAt = 0, lastUser = null, convo = [];

  const remove = () => { if (root) { root.remove(); root = null; } open = false; };

  function build() {
    root = document.createElement('div');
    root.className = 'ah-root';
    root.innerHTML = `<button type="button" class="ah-fab" aria-haspopup="dialog" aria-expanded="false"><span class="ah-fab-ic">${ICON}</span><span class="ah-fab-tx"></span></button>
      <section class="ah-panel" role="dialog" aria-modal="false" hidden>
        <header class="ah-head"><span class="ah-head-ic">${ICON}</span><div><b class="ah-title"></b><small class="ah-sub"></small></div>
          <button type="button" class="ah-icon-btn ah-clear" aria-label=""></button><button type="button" class="ah-icon-btn ah-x" aria-label="">✕</button></header>
        <div class="ah-log" aria-live="polite"></div>
        <div class="ah-chips"></div>
        <form class="ah-form" autocomplete="off"><input class="ah-input" type="text" maxlength="500" required><button class="ah-send" type="submit"></button></form>
        <p class="ah-note"></p></section>`;
    document.body.appendChild(root);
    root.querySelector('.ah-fab').addEventListener('click', () => toggle());
    root.querySelector('.ah-x').addEventListener('click', () => toggle(false));
    root.querySelector('.ah-clear').addEventListener('click', () => { convo = []; draw(); });
    root.querySelector('.ah-form').addEventListener('submit', e => { e.preventDefault(); const i = root.querySelector('.ah-input'); const q = i.value.trim(); if (q) { i.value = ''; ask(q); } });
    root.addEventListener('keydown', e => { if (e.key === 'Escape' && open) toggle(false); });
    root.querySelector('.ah-log').addEventListener('click', e => {
      const b = e.target.closest('[data-rate]'); if (b) rate(b);
      const l = e.target.closest('a.ah-link'); if (l && matchMedia('(max-width: 640px)').matches) toggle(false);
    });
    root.querySelector('.ah-chips').addEventListener('click', e => { const c = e.target.closest('[data-q]'); if (c) ask(c.dataset.q); });
  }

  function labels() {
    const x = t();
    root.querySelector('.ah-fab-tx').textContent = x.open;
    root.querySelector('.ah-title').textContent = x.title;
    root.querySelector('.ah-sub').textContent = x.sub;
    root.querySelector('.ah-input').placeholder = x.placeholder;
    root.querySelector('.ah-send').textContent = x.send;
    root.querySelector('.ah-note').textContent = x.note;
    root.querySelector('.ah-clear').textContent = '↺';
    root.querySelector('.ah-clear').setAttribute('aria-label', x.clear);
    root.querySelector('.ah-clear').title = x.clear;
    root.querySelector('.ah-x').setAttribute('aria-label', x.close);
    root.querySelector('.ah-panel').setAttribute('aria-label', x.title);
  }

  function draw() {
    if (!root) return;
    const x = t(), log = root.querySelector('.ah-log');
    const hello = `<div class="ah-msg ah-bot"><div class="ah-bubble" dir="auto">${esc_(x.hello[me.role] || x.hello.STUDENT)}</div></div>`;
    log.innerHTML = hello + convo.map(m => {
      if (m.me) return `<div class="ah-msg ah-me"><div class="ah-bubble" dir="auto">${esc_(m.text)}</div></div>`;
      const links = (m.links || []).length ? `<div class="ah-links"><small>${esc_(x.sources)}:</small> ${m.links.map(l => `<a class="ah-link" href="${esc_(l.href)}">${esc_(l.label)}</a>`).join('')}</div>` : '';
      const rating = m.id ? (m.rated ? `<small class="ah-thanks">${esc_(x.thanks)}</small>` : `<div class="ah-rate"><button type="button" data-rate="1" data-id="${m.id}" aria-label="${esc_(x.helpful)}" title="${esc_(x.helpful)}">👍</button><button type="button" data-rate="0" data-id="${m.id}" aria-label="${esc_(x.notHelpful)}" title="${esc_(x.notHelpful)}">👎</button></div>`) : '';
      return `<div class="ah-msg ah-bot ${m.bad ? 'ah-bad' : ''}"><div class="ah-bubble" dir="${m.lang === 'ar' ? 'rtl' : m.lang === 'en' ? 'ltr' : 'auto'}">${esc_(m.text)}</div>${links}${rating}</div>`;
    }).join('') + (busy ? `<div class="ah-msg ah-bot"><div class="ah-bubble ah-typing">${esc_(x.thinking)}</div></div>` : '');
    const last = [...convo].reverse().find(m => !m.me && m.suggestions);
    const chips = (last ? last.suggestions : x.chips[me.role] || x.chips.STUDENT) || [];
    root.querySelector('.ah-chips').innerHTML = chips.map(c => `<button type="button" class="ah-chip" data-q="${esc_(c)}" dir="auto">${esc_(c)}</button>`).join('');
    log.scrollTop = log.scrollHeight;
  }

  async function ask(q) {
    if (busy) return;
    convo.push({ me: true, text: q }); busy = true; draw();
    try {
      const r = await post('/api/ask', { question: q });
      convo.push({ text: r.answer, links: r.links, suggestions: r.suggestions, id: r.id, lang: r.language });
    } catch (e) {
      if (e.status === 401) { location.reload(); return; }
      if (e.status === 403) { lastStatusAt = 0; await sync(); return; }
      convo.push({ text: e.message || t().error, bad: true });
    } finally { busy = false; draw(); }
  }

  async function rate(btn) {
    const m = convo.find(x => x.id === btn.dataset.id); if (!m) return;
    m.rated = true; draw();
    try { await post(`/api/ask/${m.id}/rating`, { up: btn.dataset.rate === '1' }); } catch { /* the thumbs are optional */ }
  }

  function toggle(force) {
    if (!root) return;
    open = force === undefined ? !open : force;
    root.classList.toggle('ah-open', open);
    root.querySelector('.ah-panel').hidden = !open;
    root.querySelector('.ah-fab').setAttribute('aria-expanded', String(open));
    if (open) { draw(); setTimeout(() => root && root.querySelector('.ah-input').focus(), 50); }
  }

  /** Called after every screen is drawn: shows the button for the people who may have it, removes it for everyone else. */
  async function sync() {
    if (typeof S === 'undefined' || !S.me) { remove(); lastUser = null; convo = []; return; }
    if (lastUser !== S.me.id) { lastUser = S.me.id; convo = []; lastStatusAt = 0; remove(); }
    if (!['PARENT', 'STUDENT', 'TEACHER'].includes(S.me.role)) { remove(); return; }
    if (Date.now() - lastStatusAt > 60000 || !root) {
      lastStatusAt = Date.now();
      let st; try { st = await get('/api/ask/status'); } catch { return; }
      if (!S.me) return;
      if (!st.available) { remove(); return; }
    }
    me = S.me;
    if (!root) build();
    labels();
    if (open) draw();
  }

  return { sync };
})();

// ---- Preferences > Ask Hafez (parents): a switch for each child, saved at once
Object.assign(AR, {
  'Ask Hafez': 'اسأل حافظ',
  'The floating helper that answers questions about school. Choose which of your children may use it: when it is off for a child, their Ask Hafez button disappears. It only reads information and never changes anything.':
    'المساعد العائم الذي يجيب عن أسئلة المدرسة. اختر أي أبنائك يمكنه استخدامه: عند إيقافه لطفل يختفي زر «اسأل حافظ» عنده. يقرأ المعلومات فقط ولا يغيّر أي شيء.',
  'Can use Ask Hafez': 'يمكنه استخدام «اسأل حافظ»',
  'Ask Hafez is on for this child.': 'تم تشغيل «اسأل حافظ» لهذا الطفل.',
  'Ask Hafez is off for this child. Their button is gone.': 'تم إيقاف «اسأل حافظ» لهذا الطفل. اختفى الزر عنده.'
});
document.addEventListener('change', e => {
  const el = e.target, id = el && el.dataset && el.dataset.askChild;
  if (!id) return;
  put('/api/ask/children/' + id, { enabled: el.checked })
    .then(() => toast(el.checked ? 'Ask Hafez is on for this child.' : 'Ask Hafez is off for this child. Their button is gone.'))
    .catch(err => { el.checked = !el.checked; fail(err); });
});
