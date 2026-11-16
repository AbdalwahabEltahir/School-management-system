/* Hafez interface language: English or Arabic (right to left).
   The app writes its screens in English; when Arabic is chosen, app.js passes every piece of interface text through
   trText() as the screen is built. Only text is translated, never attribute values, so routes, filters and form
   values keep working. People's names, file names and other data pass through unchanged. */
'use strict';

const I18N = { lang: 'en', stored: false };
try {
  const saved = localStorage.getItem('sijill.lang');
  if (saved === 'ar' || saved === 'en') { I18N.lang = saved; I18N.stored = true; }
  else if ((navigator.language || '').toLowerCase().startsWith('ar')) I18N.lang = 'ar';
} catch { /* storage blocked: stay in English */ }

function applyLang() {
  document.documentElement.lang = I18N.lang;
  document.documentElement.dir = I18N.lang === 'ar' ? 'rtl' : 'ltr';
}
function setLang(l, remember) {
  I18N.lang = l === 'ar' ? 'ar' : 'en';
  if (remember !== false) { try { localStorage.setItem('sijill.lang', I18N.lang); I18N.stored = true; } catch { } }
  applyLang();
}
applyLang();

/** Locale for dates: Arabic month and day names, Latin digits so numbers read the same everywhere. */
const uiLocale = () => (I18N.lang === 'ar' ? 'ar-QA-u-nu-latn' : 'en-GB');

const AR = {
  'Invitations': 'الدعوات', 'Invitation code': 'رمز الدعوة', 'I have an invitation code': 'لدي رمز دعوة', 'Activate my account': 'تفعيل حسابي', 'Create my account': 'إنشاء حسابي',
  'Your school gave you a one-time code. Choose a password of at least 8 characters.': 'أعطتك مدرستك رمزاً لمرة واحدة. اختر كلمة مرور من 8 أحرف على الأقل.',
  'Your school gave you a one-time invitation code. It links your children to your own account.': 'أعطتك مدرستك رمز دعوة لمرة واحدة. يربط أبناءك بحسابك الخاص.',
  'Enter the invitation code the school gave you.': 'أدخل رمز الدعوة الذي أعطتك إياه المدرسة.', 'Ask your school for an invitation code, then enter it above.': 'اطلب من مدرستك رمز دعوة ثم أدخله أعلاه.',
  'Your school office may ask for this number.': 'قد يطلب منك مكتب المدرسة هذا الرقم.',
  'There is no open registration. Give each family a one-time code; it works once and expires in 14 days.': 'لا يوجد تسجيل مفتوح. أعطِ كل أسرة رمزاً لمرة واحدة يعمل مرة واحدة وينتهي بعد 14 يوماً.',
  'Issue a code': 'إصدار رمز', 'Issue code': 'إصدار الرمز', 'Codes': 'الرموز', 'No codes yet.': 'لا توجد رموز بعد.', 'Code revoked.': 'تم إلغاء الرمز.', 'Revoke': 'إلغاء',
  'Pending': 'بانتظار الاستخدام', 'Used': 'مستخدم', 'Expired': 'منتهي', 'Revoked': 'ملغى',
  'Points': 'النقاط', 'Points given.': 'تم منح النقاط.', 'Give points': 'منح نقاط', 'Give': 'منح', 'Class points': 'نقاط الصف', 'Reason': 'السبب', 'Recognition': 'التقدير', 'No points yet.': 'لا توجد نقاط بعد.',
  'Each present day earns one point automatically. Add recognition with a short reason.': 'كل يوم حضور يمنح نقطة تلقائياً. أضف تقديراً مع سبب مختصر.',
  'Points across the school: totals, average per student and the top students.': 'النقاط في المدرسة: المجموع والمتوسط لكل طالب وأفضل الطلاب.', 'Top students': 'أفضل الطلاب',
  'Attendance is recorded by the school\'s attendance officer. You can read it here.': 'يسجّل الحضور مسؤول الحضور في المدرسة. يمكنك الاطلاع عليه هنا.',
  'Record today. Everyone starts as present; change only the absences and leave. Each present day earns the student a point.': 'سجّل اليوم. يبدأ الجميع حاضرين؛ غيّر الغياب والإجازات فقط. كل يوم حضور يمنح الطالب نقطة.', 'No section to show yet.': 'لا توجد شعبة لعرضها بعد.',
  'Weekly grades': 'درجات الأسبوع', 'Performance matrix': 'مصفوفة الأداء', 'Matrix': 'المصفوفة', 'Stage': 'المرحلة', 'Average': 'المعدل', 'Sijil': 'سجل', 'Hifz': 'حفظ',
  'Who has submitted this week\'s grades. The school week runs Sunday to Thursday.': 'من سلّم درجات هذا الأسبوع. الأسبوع الدراسي من الأحد إلى الخميس.',
  'Every student against every assessment, with their stage.': 'كل طالب مقابل كل تقييم مع مرحلته.', 'Teacher': 'المعلم', 'Submitted': 'سُلّمت', 'Done': 'مكتمل', 'In progress': 'قيد التنفيذ', 'Not started': 'لم يبدأ', 'Overdue': 'متأخر',
  '← Previous week': '→ الأسبوع السابق', 'Next week →': 'الأسبوع التالي ←', 'This week': 'هذا الأسبوع', 'Ask to publish': 'طلب النشر', 'Stage saved.': 'تم حفظ المرحلة.',
  'Sent for approval. A principal, coordinator or School IT must approve before students see the results.': 'أُرسل للموافقة. يجب أن يوافق المدير أو المنسق أو تقنية المدرسة قبل أن يرى الطلاب النتائج.',
  'Students see a term only after a second person approves it. Every assessment of that term must be submitted first.': 'لا يرى الطلاب الفصل إلا بعد موافقة شخص ثانٍ. يجب تسليم كل تقييمات الفصل أولاً.',
  'Approvals': 'الموافقات', 'Sensitive actions need a second person. You cannot approve your own request.': 'الإجراءات الحساسة تحتاج إلى شخص ثانٍ. لا يمكنك الموافقة على طلبك.',
  'Requests': 'الطلبات', 'Nothing is waiting for approval.': 'لا شيء بانتظار الموافقة.', 'Requested by': 'طلبه', 'Approve': 'موافقة', 'Reject': 'رفض', 'Withdraw': 'سحب', 'Remove': 'إزالة',
  'Publish marks': 'نشر الدرجات', 'Remove school': 'إزالة مدرسة', 'Disconnect Drive': 'فصل Drive', 'Change role': 'تغيير دور',
  'EAA administrator': 'مسؤول الهيئة', 'Attendance officer': 'مسؤول الحضور', 'Done.': 'تم.', 'Reason (optional):': 'السبب (اختياري):',
  'Sent for approval. A second EAA administrator must approve before Google Drive is disconnected.': 'أُرسل للموافقة. يجب أن يوافق مسؤول آخر في الهيئة قبل فصل Google Drive.',
  'Sent for approval. A second EAA administrator must approve the removal.': 'أُرسل للموافقة. يجب أن يوافق مسؤول آخر في الهيئة على الإزالة.',
  // ---------------------------------------------------------------- shell, navigation, roles
  'School file register': 'سجل ملفات المدرسة', 'SCHOOL FILE REGISTER': 'سجل ملفات المدرسة',
  'System operational': 'النظام يعمل', 'Loading…': 'جارٍ التحميل…', 'My preferences': 'تفضيلاتي', 'Preferences': 'التفضيلات', 'Sign out': 'تسجيل الخروج',
  'Teacher': 'معلم', 'Coordinator': 'منسق', 'Principal': 'مدير المدرسة', 'School IT': 'تقنية المعلومات بالمدرسة', 'Leadership': 'القيادة', 'Student': 'طالب', 'Parent': 'وليّ أمر',
  'TEACHER': 'معلم', 'COORDINATOR': 'منسق', 'PRINCIPAL': 'مدير المدرسة', 'SCHOOL_IT': 'تقنية المعلومات', 'LEADERSHIP': 'القيادة', 'STUDENT': 'طالب', 'PARENT': 'وليّ أمر',
  'Today': 'اليوم', 'Attendance': 'الحضور', 'Marks': 'الدرجات', 'Study materials': 'المواد الدراسية', 'My issues': 'مشكلاتي', 'My files': 'ملفاتي', 'Lesson plans': 'خطط الدروس',
  'Notices': 'الإعلانات', 'Notifications': 'الإشعارات', 'My children': 'أبنائي', 'My day': 'يومي', 'My results': 'نتائجي', 'Academic setup': 'الإعداد الأكاديمي',
  'Follow-up': 'المتابعة', 'FOLLOW-UP': 'المتابعة', 'Follow up': 'متابعة', 'All issues': 'كل المشكلات', 'Review queue': 'قائمة المراجعة', 'Files': 'الملفات', 'Plan review': 'مراجعة الخطط',
  'People': 'الأشخاص', 'Restores': 'الاستعادة', 'My school': 'مدرستي', 'Issues': 'المشكلات', 'Rules': 'القواعد', 'Schools & settings': 'المدارس والإعدادات',
  'Connection': 'الاتصال', 'Incidents': 'الحوادث', 'Activity log': 'سجل النشاط', 'Overview': 'نظرة عامة', 'Dashboard': 'لوحة المعلومات',
  'Building a brighter future together': 'نبني معاً مستقبلاً أكثر إشراقاً', 'Search lessons, notices, materials': 'ابحث في الدروس والإعلانات والمواد',
  'Need help?': 'تحتاج مساعدة؟', 'Contact your school office': 'تواصل مع إدارة مدرستك', 'School notices →': 'إعلانات المدرسة →',

  // ---------------------------------------------------------------- common words and buttons
  'Save': 'حفظ', 'Cancel': 'إلغاء', 'Back': 'رجوع', 'Edit': 'تعديل', 'Delete': 'حذف', 'Remove': 'إزالة', 'Add': 'إضافة', 'Open': 'فتح', 'Open →': 'فتح →', 'Close': 'إغلاق',
  'Yes': 'نعم', 'No': 'لا', 'On': 'مفعّل', 'Off': 'متوقف', 'All': 'الكل', 'None': 'لا شيء', 'Other': 'أخرى', 'New': 'جديد', 'new': 'جديد', 'Show': 'عرض', 'Showing': 'عرض', 'of': 'من',
  'Submit': 'إرسال', 'Approve': 'اعتماد', 'Reject': 'رفض', 'Return': 'إعادة', 'Accept': 'قبول', 'Decline': 'رفض', 'Assign': 'تعيين', 'Publish': 'نشر', 'Unpublish': 'إلغاء النشر',
  'Pause': 'إيقاف مؤقت', 'Resume': 'استئناف', 'Rename': 'إعادة تسمية', 'Reply': 'رد', 'Resolve': 'حلّ', 'Review': 'مراجعة', 'Preview': 'معاينة', 'Download': 'تنزيل',
  'Download CSV': 'تنزيل CSV', 'Share': 'مشاركة', 'Change': 'تغيير', 'Continue': 'متابعة', 'Try again': 'حاول مرة أخرى', 'Include': 'تضمين', 'Exclude': 'استبعاد',
  'Name': 'الاسم', 'Title': 'العنوان', 'Type': 'النوع', 'Code': 'الرمز', 'Status': 'الحالة', 'State': 'الحالة', 'Note': 'ملاحظة', 'Reason': 'السبب', 'Value': 'القيمة', 'Setting': 'الإعداد',
  'Settings': 'الإعدادات', 'Who': 'من', 'What': 'ماذا', 'When': 'متى', 'From': 'من', 'To': 'إلى', 'Until': 'حتى', 'Day': 'اليوم', 'Week': 'الأسبوع', 'Total': 'الإجمالي',
  'Link': 'الرابط', 'Plan': 'الخطة', 'Role': 'الدور', 'Person': 'الشخص', 'Owner': 'المالك', 'Scope': 'النطاق', 'Score': 'الدرجة', 'Trend': 'الاتجاه', 'Latest': 'الأحدث',
  'Entries': 'السجلات', 'Action': 'الإجراء', 'Actions': 'الإجراءات', 'About': 'بخصوص', 'Started': 'بدأ', 'Ended': 'انتهى', 'Recorded': 'مسجّل', 'Detected': 'اكتُشف',
  'Requested': 'مطلوب', 'Requests': 'الطلبات', 'Scheduled': 'مجدول', 'Skipped': 'تم تخطيه', 'Counted': 'محتسب', 'Not counted': 'غير محتسب', 'Scored': 'مقيّم',
  'Active': 'نشط', 'Inactive': 'غير نشط', 'Connected': 'متصل', 'Unsure': 'غير متأكد', 'Unread': 'غير مقروء', 'You': 'أنت', 'Check': 'الفحص', 'Checks': 'الفحوصات',
  'Arabic': 'العربية', 'English': 'الإنجليزية', 'English and Arabic': 'الإنجليزية والعربية', 'Everyone': 'الجميع', 'Staff': 'الموظفون', 'Students': 'الطلاب',
  'Something went wrong.': 'حدث خطأ ما.', 'Not signed in': 'لم يتم تسجيل الدخول', 'Saved.': 'تم الحفظ.', 'Updated.': 'تم التحديث.', 'Removed.': 'تمت الإزالة.', 'Renamed.': 'تمت إعادة التسمية.',
  'Nothing to show.': 'لا يوجد ما يُعرض.', 'Nothing yet.': 'لا شيء حتى الآن.', 'Nothing changed.': 'لم يتغير شيء.', 'Nothing recorded': 'لم يُسجّل شيء', '— no change': '— دون تغيير',
  'View all →': 'عرض الكل →', 'Learn more': 'اعرف المزيد', 'Learn more →': 'اعرف المزيد →', 'Week starting': 'بداية الأسبوع', 'Week starting (Monday)': 'بداية الأسبوع (الاثنين)',
  'Sunday': 'الأحد', 'Monday': 'الاثنين', 'Tuesday': 'الثلاثاء', 'Wednesday': 'الأربعاء', 'Thursday': 'الخميس', 'Friday': 'الجمعة', 'Saturday': 'السبت',
  'Good morning': 'صباح الخير', 'Good afternoon': 'مساء الخير', 'Good evening': 'مساء الخير',

  // ---------------------------------------------------------------- statuses, severities, enums shown through title()
  'Urgent': 'عاجل', 'Normal': 'عادي', 'URGENT': 'عاجل', 'NORMAL': 'عادي', 'Urgent issue': 'مشكلة عاجلة', 'Issue': 'مشكلة',
  'Owner notified': 'تم إبلاغ المالك', 'Reminder sent': 'تم إرسال تذكير', 'Needs follow-up': 'تحتاج متابعة', 'Needs review': 'تحتاج مراجعة', 'Owner disagrees': 'المالك غير موافق',
  'Asked for help': 'طلب المساعدة', 'Fixed': 'تم الإصلاح', 'FIXED': 'تم الإصلاح', 'Allowed exception': 'استثناء مسموح', 'Rule paused': 'القاعدة موقوفة', 'Rule removed': 'القاعدة محذوفة',
  'Closed': 'مغلقة', 'Resolved': 'تم الحل', 'Approved': 'معتمد', 'Returned': 'مُعاد', 'Submitted': 'مُرسل', 'Not submitted': 'لم يُرسل', 'Rejected': 'مرفوض', 'Restored': 'تمت الاستعادة',
  'Failed': 'فشل', 'Succeeded': 'نجح', 'Running': 'قيد التشغيل', 'Queued': 'في الانتظار', 'Draft': 'مسودة', 'Paused': 'موقوف', 'Deleted': 'محذوف',
  'Public': 'عام', 'Internal': 'داخلي', 'Confidential': 'سري', 'Restricted': 'مقيّد', 'Not classified': 'غير مصنّف',
  'Present': 'حاضر', 'Absent': 'غائب', 'Leave': 'إجازة', 'Daily': 'يومي', 'Per issue': 'لكل مشكلة', 'Weekly': 'أسبوعي',
  'Shared': 'مشترك', 'Personal': 'شخصي', 'My drive': 'ملفاتي في Drive', 'Subject drive': 'Drive المادة', 'Shared drive': 'Drive مشترك', 'Personal drive': 'Drive شخصي',
  'Viewer': 'مشاهد', 'Commenter': 'معلّق', 'Editor': 'محرر', 'Anyone with link': 'أي شخص لديه الرابط', 'External user': 'مستخدم خارجي', 'Internal user': 'مستخدم داخلي',
  'Internal group': 'مجموعة داخلية', 'All staff': 'جميع الموظفين', 'User': 'مستخدم', 'Group': 'مجموعة', 'Domain': 'النطاق', 'Anyone': 'أي شخص',
  'Location': 'الموقع', 'Public link': 'رابط عام', 'Outside sharing': 'مشاركة خارجية', 'Internal access': 'وصول داخلي', 'Duplicates': 'نسخ مكررة',
  'Sharing before date': 'المشاركة قبل موعد', 'Owner leaving': 'مغادرة المالك', 'Generic owner': 'مالك عام', 'Deleted or trashed': 'محذوف أو في سلة المهملات',
  'Exam leak': 'تسريب امتحان', 'Important deletion': 'حذف ملف مهم', 'Bulk deletion': 'حذف جماعي', 'Urgent unresolved': 'مشكلة عاجلة دون حل', 'Connection lost': 'انقطاع الاتصال',
  'Opened': 'فُتحت', 'Status changed': 'تغيّرت الحالة', 'Follow up recorded': 'سُجّلت متابعة', 'Still broken': 'ما زالت المشكلة قائمة', 'Feedback saved': 'حُفظت الملاحظة', 'Reassigned': 'أُعيد تعيينها',
  'First check': 'الفحص الأول', 'Full check': 'فحص كامل', 'Change feed check': 'فحص التغييرات', 'Manual check': 'فحص يدوي', 'Hourly backup': 'نسخ احتياطي كل ساعة', 'Nightly backup': 'نسخ احتياطي ليلي',
  'New status': 'حالة جديدة', 'Leaving': 'يغادر', 'Left': 'غادر', 'Good': 'جيد', 'Watch': 'تحت المراقبة', 'Needs attention': 'يحتاج اهتماماً', 'Need attention': 'تحتاج اهتماماً',
  '✓ Good': '✓ جيد', '◆ Watch': '◆ تحت المراقبة', '! Needs attention': '! يحتاج اهتماماً', '▲ Open urgent': '▲ عاجلة مفتوحة',
  'File': 'ملف', 'Folder': 'مجلد', 'Drive': 'Drive', 'Whole school': 'المدرسة كاملة', 'Subject drive scope': 'Drive المادة',

  // activity log action codes
  'ISSUE_OPENED': 'فتح مشكلة', 'ISSUE_STATUS_CHANGED': 'تغيير حالة مشكلة', 'ISSUE_REASSIGNED': 'إعادة تعيين مشكلة', 'EXCEPTION_GRANTED': 'منح استثناء', 'EXCEPTION_ENDED': 'انتهاء استثناء',
  'INCIDENT_RAISED': 'تسجيل حادثة', 'INCIDENT_RESOLVED': 'حل حادثة', 'FILE_CLASSIFIED': 'تصنيف ملف', 'CHECK_RUN': 'تشغيل فحص', 'RULE_CREATED': 'إنشاء قاعدة', 'RULE_UPDATED': 'تحديث قاعدة',
  'RULE_APPROVED': 'اعتماد قاعدة', 'RULE_PAUSED': 'إيقاف قاعدة', 'RULE_RESUMED': 'استئناف قاعدة', 'RULE_DELETED': 'حذف قاعدة', 'SETTING_CHANGED': 'تغيير إعداد', 'SITE_CREATED': 'إضافة مدرسة',
  'SITE_UPDATED': 'تحديث مدرسة', 'SITE_REMOVED': 'إزالة مدرسة', 'SUBJECT_CHANGED': 'تغيير مادة', 'DOC_TYPE_CHANGED': 'تغيير نوع مستند', 'ROLE_GROUP_CHANGED': 'تغيير مجموعة دور',
  'PERSON_UPDATED': 'تحديث شخص', 'PERSON_ADDED': 'إضافة شخص', 'PERSON_DELETED': 'حذف شخص', 'ENROLMENT_REQUESTED': 'طلب تسجيل', 'ENROLMENT_REQUEST_DECIDED': 'البت في طلب تسجيل', 'PERSON_FOLLOWED_UP': 'متابعة شخص',
  'DIRECTORY_SYNCED': 'مزامنة الدليل', 'WELCOME_SENT': 'إرسال رسالة ترحيب', 'PLANNING_SUBMITTED': 'إرسال خطة', 'PLANNING_REVIEWED': 'مراجعة خطة', 'RESTORE_REQUESTED': 'طلب استعادة',
  'RESTORE_APPROVED': 'اعتماد استعادة', 'RESTORE_REJECTED': 'رفض استعادة', 'RESTORE_COMPLETED': 'اكتمال استعادة', 'EXPORT_CREATED': 'إنشاء تصدير', 'SECTION_CREATED': 'إنشاء شعبة',
  'TERM_SAVED': 'حفظ فصل دراسي', 'STUDENT_ENROLLED': 'تسجيل طالب', 'TEACHER_ASSIGNED': 'تعيين معلم', 'TIMETABLE_CHANGED': 'تغيير الجدول', 'ATTENDANCE_RECORDED': 'تسجيل حضور',
  'ATTENDANCE_CHANGED': 'تعديل حضور', 'ASSESSMENT_CREATED': 'إنشاء تقييم', 'MARK_ENTERED': 'إدخال درجة', 'MARKS_SUBMITTED': 'إرسال الدرجات', 'MARKS_UNLOCKED': 'فتح الدرجات للتعديل',
  'RESULTS_PUBLISHED': 'نشر النتائج', 'RESULTS_UNPUBLISHED': 'إلغاء نشر النتائج', 'NOTICE_PUBLISHED': 'نشر إعلان', 'NOTICE_REMOVED': 'حذف إعلان', 'MATERIAL_ADDED': 'إضافة مادة دراسية',

  // ---------------------------------------------------------------- landing page and sign in
  'Features': 'المزايا', 'Solution': 'الحل', 'Sign in': 'تسجيل الدخول', 'SIGN IN': 'تسجيل الدخول', 'Contact': 'تواصل معنا', 'Access Hafez →': 'الدخول إلى حافظ →',
  'Keep every school file safe, findable and owned': 'حافظ على كل ملف مدرسي آمناً وسهل الوصول وله مالك',
  'A read-only checker that keeps your school files safe, findable and owned — and helps you fix issues quickly.': 'أداة فحص للقراءة فقط تحافظ على ملفات مدرستك آمنة وسهلة الوصول ولها مالك، وتساعدك على إصلاح المشكلات بسرعة.',
  'Explore Hafez →': 'استكشف حافظ →', 'Read-only': 'للقراءة فقط', 'Your files stay safe': 'ملفاتك تبقى آمنة', 'Issues fixed in minutes': 'إصلاح المشكلات في دقائق',
  'Check & Fix': 'افحص وأصلح', 'Spot issues and fix them within minutes.': 'اكتشف المشكلات وأصلحها خلال دقائق.', 'Manage Access': 'إدارة الصلاحيات',
  'Control who can view, edit and share files.': 'تحكّم في من يمكنه عرض الملفات وتعديلها ومشاركتها.', 'Stay Compliant': 'التزم بالسياسات',
  'Keep records secure and follow school policies.': 'حافظ على أمن السجلات واتبع سياسات المدرسة.', 'Support Better Education': 'ادعم تعليماً أفضل', 'Focus on what matters, students.': 'ركّز على ما يهم: الطلاب.',
  'The Hafez Solution': 'حل حافظ', 'Built for Schools, Designed for Impact': 'صُمّم للمدارس، ليُحدث الأثر',
  'Hafez helps your school manage important files with confidence, supporting a safer and more efficient learning environment.': 'يساعد حافظ مدرستك على إدارة الملفات المهمة بثقة، دعماً لبيئة تعلم أكثر أماناً وكفاءة.',
  'Discover more →': 'اكتشف المزيد →', 'Incidents ': 'الحوادث', 'Reports': 'التقارير', 'Schools': 'المدارس', 'Total files': 'إجمالي الملفات', 'Active users': 'المستخدمون النشطون',
  'Compliance': 'الامتثال', 'Avg. fix time': 'متوسط وقت الإصلاح', '2 min': 'دقيقتان', 'Recent activity': 'النشاط الأخير', 'Policy updated by Admin': 'حدّث المسؤول السياسة',
  'File access granted': 'تم منح صلاحية الوصول لملف', '2m ago': 'قبل دقيقتين', '5m ago': 'قبل 5 دقائق',
  'Welcome to Hafez': 'مرحباً بك في حافظ', 'Choose how you want to sign in.': 'اختر طريقة تسجيل الدخول.', 'Recommended': 'موصى به', 'Login with Google': 'الدخول عبر Google',
  'For school related emails': 'لحسابات البريد المدرسية', 'PORTAL': 'بوابة', 'Sign in or register': 'سجّل الدخول أو أنشئ حساباً',
  'Together, we build brighter futures.': 'معاً نبني مستقبلاً أكثر إشراقاً.', 'Through innovative solutions like Hafez, schools support quality education for every child.': 'بفضل حلول مبتكرة مثل حافظ، تدعم المدارس تعليماً عالي الجودة لكل طفل.',
  'Our impact →': 'أثرنا →', 'Qatar · Phase 1': 'قطر · المرحلة الأولى', 'Qatar school campus': 'حرم مدرسي في قطر', 'Student studying on a laptop': 'طالب يدرس على حاسوب محمول',
  'Test mode, no e-mail needed:': 'وضع الاختبار، دون بريد إلكتروني:', 'all test roles': 'كل أدوار الاختبار', 'student': 'طالب', 'parent': 'وليّ أمر',
  'Student portal': 'بوابة الطالب', 'Parent portal': 'بوابة وليّ الأمر', 'Register': 'إنشاء حساب', 'Register as a parent': 'التسجيل كوليّ أمر', 'E-mail': 'البريد الإلكتروني',
  'Password': 'كلمة المرور', 'Full name': 'الاسم الكامل', 'Your full name': 'اسمك الكامل', 'Your e-mail': 'بريدك الإلكتروني', 'School': 'المدرسة', 'Student ID': 'رقم الطالب',
  '10-digit student ID': 'رقم الطالب من 10 أرقام', 'At least 8 characters. After you register you get a student ID to give your parents.': 'ثمانية أحرف على الأقل. بعد التسجيل تحصل على رقم طالب تعطيه لوالديك.',
  "Use your own e-mail and password. The student ID is on your child's home screen. Each parent registers separately with the same ID.": 'استخدم بريدك وكلمة مرورك. رقم الطالب موجود في الشاشة الرئيسية لابنك. يسجّل كل وليّ أمر بشكل منفصل بالرقم نفسه.',
  'Your account is not set up yet': 'لم يتم إعداد حسابك بعد', 'Sign out and try another account': 'سجّل الخروج وجرّب حساباً آخر', 'Could not load your account': 'تعذّر تحميل حسابك',
  'The server answered': 'ردّ الخادم', 'The browser could not reach the server (': 'تعذّر على المتصفح الوصول إلى الخادم (', '). If the app was restarting, try again.': '). إذا كان التطبيق يُعاد تشغيله، فحاول مرة أخرى.',
  'Reference:': 'المرجع:',

  // ---------------------------------------------------------------- student and parent portal
  'Welcome back,': 'مرحباً بعودتك،', 'Welcome,': 'مرحباً،', 'Welcome': 'مرحباً', 'View my results →': 'عرض نتائجي →', 'View results →': 'عرض النتائج →', 'Full report →': 'التقرير الكامل →',
  'EXAM PERIOD': 'فترة الامتحانات', 'Exams are in progress': 'الامتحانات جارية', 'Exams start soon': 'الامتحانات تبدأ قريباً', 'days left': 'أيام متبقية', 'days to go': 'أيام حتى البدء',
  'Absence rate': 'نسبة الغياب', 'Documented absence': 'غياب بعذر', 'Undocumented absence': 'غياب بدون عذر', 'Average grade': 'متوسط الدرجات', 'Average grade · all assessments': 'متوسط الدرجات · كل التقييمات',
  'My progress': 'تقدّمي', 'Progress': 'التقدّم', 'Average grade by assessment': 'متوسط الدرجات حسب التقييم', 'Results appear here after your school publishes them.': 'تظهر النتائج هنا بعد أن تنشرها مدرستك.',
  'Last days': 'الأيام الأخيرة', 'Today\'s lessons': 'دروس اليوم', "Today's lessons": 'دروس اليوم', 'No lessons today.': 'لا توجد دروس اليوم.', 'Not a school day. Lessons run Sunday to Thursday.': 'ليس يوماً دراسياً. الدروس من الأحد إلى الخميس.',
  'No notices right now.': 'لا توجد إعلانات حالياً.', 'No materials yet.': 'لا توجد مواد بعد.', 'My timetable': 'جدولي', 'Timetable': 'الجدول', 'No lessons in the timetable yet.': 'لا توجد دروس في الجدول بعد.',
  'Quick access': 'وصول سريع', 'Results': 'النتائج', 'Together for a better tomorrow': 'معاً من أجل غدٍ أفضل', 'Thank you for being part of our school community.': 'شكراً لكونك جزءاً من مجتمعنا المدرسي.',
  'Learn more →  ': 'اعرف المزيد →', 'Result published': 'نُشرت نتيجة', 'results published': 'نتائج منشورة', 'published result': 'نتيجة منشورة', 'published results': 'نتائج منشورة',
  'Period': 'الحصة', 'Subject': 'المادة', 'Room': 'القاعة', 'Mark': 'الدرجة', 'Assessment': 'التقييم', 'Assessments': 'التقييمات', 'Term': 'الفصل الدراسي', 'Terms': 'الفصول الدراسية',
  'Best result': 'أفضل نتيجة', 'Assessments marked': 'تقييمات مصحّحة', 'Subjects': 'المواد', 'No results have been published yet.': 'لم تُنشر أي نتائج بعد.',
  'Your own results, shown after your school publishes them.': 'نتائجك الخاصة، تظهر بعد أن تنشرها مدرستك.', 'Shared by your teachers.': 'شاركها معلموك.', 'Materials': 'المواد',
  'What your school wants you to know.': 'ما تريد مدرستك أن تعرفه.', 'Current notices': 'الإعلانات الحالية', 'You are all caught up.': 'لا جديد لديك.', 'Mark all as read': 'تحديد الكل كمقروء',
  'Messages': 'الرسائل', 'No notifications.': 'لا توجد إشعارات.', 'Choose how Hafez talks to you.': 'اختر كيف يتواصل معك حافظ.', 'Language of messages': 'لغة الرسائل',
  'How to receive them': 'طريقة استلامها', 'One daily summary': 'ملخص يومي واحد', 'One message per issue': 'رسالة لكل مشكلة', 'Your student ID': 'رقم الطالب الخاص بك',
  'Give this ID to your parents. They use it, with their own e-mail and password, to register and follow you.': 'أعطِ هذا الرقم لوالديك. يستخدمانه مع بريدهما وكلمة مرورهما للتسجيل ومتابعتك.',
  'You are not in a class yet.': 'لم تُسجّل في صف بعد.', 'Waiting for a class': 'بانتظار صف', 'The request for': 'طلب الالتحاق بـ', 'is waiting for the coordinator to confirm.': 'بانتظار تأكيد المنسق.',
  'Choose a class': 'اختر صفاً', 'Ask for a place in a class. The coordinator confirms it.': 'اطلب مقعداً في صف. يؤكده المنسق.', 'Request this class': 'اطلب هذا الصف',
  'The school has not created a class yet.': 'لم تُنشئ المدرسة أي صف بعد.', 'Follow another child': 'متابعة طفل آخر', 'Enter their student ID.': 'أدخل رقم الطالب الخاص به.',
  'Add child': 'إضافة طفل', 'No child linked yet': 'لا يوجد طفل مرتبط بعد', 'Add your child with their student ID.': 'أضف طفلك برقم الطالب الخاص به.', 'is not in a class yet.': 'لم يُسجّل في صف بعد.',
  'Child added.': 'تمت إضافة الطفل.', 'Request sent. The coordinator will confirm it.': 'تم إرسال الطلب. سيؤكده المنسق.', 'All marked as read.': 'تم تحديد الكل كمقروء.',
  'Shown to': 'موجّه إلى', 'New notice': 'إعلان جديد', 'New notice:': 'إعلان جديد:', 'Study material:': 'مادة دراسية:', 'Marked': 'سُجّل',

  // ---------------------------------------------------------------- teacher screens
  'From the timetable.': 'من الجدول الدراسي.', 'My sections': 'شعبي', 'Section': 'الشعبة', 'Attendance today': 'حضور اليوم', 'Taken': 'تم أخذه', 'Not taken': 'لم يُؤخذ',
  'Take attendance': 'أخذ الحضور', 'Not a school day': 'ليس يوماً دراسياً', 'From school administration.': 'من إدارة المدرسة.',
  'You are not assigned to a section yet. The coordinator assigns teachers in Academic setup.': 'لم يتم تعيينك لشعبة بعد. يعيّن المنسق المعلمين من الإعداد الأكاديمي.',
  'You are not assigned to a section yet.': 'لم يتم تعيينك لشعبة بعد.', 'You are not assigned to a section and subject yet.': 'لم يتم تعيينك لشعبة ومادة بعد.',
  'Class list': 'قائمة الصف', 'Save attendance': 'حفظ الحضور', 'No students are enrolled in this section yet.': 'لا يوجد طلاب مسجلون في هذه الشعبة بعد.',
  'Attendance and lessons run Sunday to Thursday.': 'الحضور والدروس من الأحد إلى الخميس.',
  'You set the assessments for the sections and subjects you teach, enter the grades, then submit to lock them.': 'تحدد التقييمات للشعب والمواد التي تدرّسها، وتُدخل الدرجات، ثم ترسلها لقفلها.',
  'New assessment': 'تقييم جديد', 'Add assessment': 'إضافة تقييم', 'Name, e.g. Quiz 1': 'الاسم، مثل: اختبار قصير 1', 'Out of': 'من أصل', 'Assessment added.': 'تمت إضافة التقييم.',
  'No assessments yet. Add the first one above.': 'لا توجد تقييمات بعد. أضف أول تقييم أعلاه.', 'No term exists yet. The coordinator creates the terms under Academic setup.': 'لا يوجد فصل دراسي بعد. ينشئ المنسق الفصول من الإعداد الأكاديمي.',
  'Section and subject': 'الشعبة والمادة', 'Publish results': 'نشر النتائج', 'Students see a term only after you publish it. Every assessment of that term must be submitted first.': 'لا يرى الطلاب الفصل إلا بعد نشره. يجب إرسال كل تقييمات الفصل أولاً.',
  'All submitted': 'تم إرسال الكل', 'Waiting for submissions': 'بانتظار الإرسال', 'Nothing to publish yet.': 'لا شيء للنشر بعد.', 'Results are hidden again.': 'أُخفيت النتائج مجدداً.',
  'Published. Students were told.': 'تم النشر وأُبلغ الطلاب.', 'Published': 'منشور', 'These marks are submitted and locked.': 'هذه الدرجات مُرسلة ومقفلة.', 'Unlock to correct': 'فتح للتصحيح',
  'Save and submit': 'حفظ وإرسال', 'Marks saved.': 'تم حفظ الدرجات.', 'Marks saved and submitted. They are now locked.': 'تم حفظ الدرجات وإرسالها، وهي الآن مقفلة.',
  'Marks unlocked. You can correct them and submit again.': 'فُتحت الدرجات. يمكنك تصحيحها وإرسالها مجدداً.', 'Why are you unlocking these marks?': 'لماذا تفتح هذه الدرجات؟',
  'Why are you unpublishing these results?': 'لماذا تلغي نشر هذه النتائج؟', 'That assessment was not found.': 'لم يتم العثور على هذا التقييم.',
  'The results are published: unpublish the term first to change them.': 'النتائج منشورة: ألغِ نشر الفصل أولاً لتعديلها.', 'Mark (0 to': 'الدرجة (0 إلى',
  'Share a link (Google Drive, Dropbox or YouTube) with a section. Students open it from their portal.': 'شارك رابطاً (Google Drive أو Dropbox أو YouTube) مع شعبة. يفتحه الطلاب من بوابتهم.',
  'Share a link': 'مشاركة رابط', 'Shared by you': 'شاركتها أنت', 'Shared with the section.': 'تمت المشاركة مع الشعبة.', 'Remove this material?': 'إزالة هذه المادة؟',
  'Files of yours that need a fix. Open one to see the steps.': 'ملفاتك التي تحتاج إصلاحاً. افتح أحدها لترى الخطوات.', 'Needs me': 'تحتاجني', 'All time': 'كل الأوقات',
  'No issues here. Your files follow the rules.': 'لا مشكلات هنا. ملفاتك تتبع القواعد.', 'Files you own, with what Hafez understood about each.': 'الملفات التي تملكها، وما فهمه حافظ عن كل منها.',
  'Your weekly plans and their review status.': 'خططك الأسبوعية وحالة مراجعتها.', "Submit this week's plan": 'إرسال خطة هذا الأسبوع', 'Submitting again for the same week sends a returned plan back for review.': 'الإرسال مجدداً للأسبوع نفسه يعيد الخطة المُعادة للمراجعة.',
  'Grade': 'الصف', 'Plan type': 'نوع الخطة', 'e.g. WEEKLY': 'مثل: أسبوعية', 'Link to the plan in Drive': 'رابط الخطة في Drive', 'Plan submitted.': 'تم إرسال الخطة.',
  'Submissions': 'الخطط المرسلة', 'Plans submitted for review.': 'خطط مرسلة للمراجعة.', 'Reason if returning': 'السبب في حال الإعادة', 'Review saved.': 'تم حفظ المراجعة.', 'Week totals': 'إجماليات الأسبوع',

  // ---------------------------------------------------------------- issues and files
  'What is wrong': 'ما المشكلة', 'How to fix it': 'طريقة الإصلاح', 'Only a re-check closes the issue. Press "I\'ve fixed it" when done.': 'لا تُغلق المشكلة إلا بإعادة الفحص. اضغط «أصلحتها» عند الانتهاء.',
  "I've fixed it": 'أصلحتها', 'Ask for help': 'اطلب المساعدة', 'Not right?': 'ليست صحيحة؟', 'If the rule should not apply to this file, tell your coordinator why.': 'إذا كانت القاعدة لا تنطبق على هذا الملف، فأخبر المنسق بالسبب.',
  'Your reason': 'سببك', 'I disagree': 'لا أوافق', 'Keep the rule': 'الإبقاء على القاعدة', 'Send reminder': 'إرسال تذكير', 'Mark fixed': 'تحديد كمُصلح',
  'Allow an exception': 'السماح باستثناء', 'The issue stays quiet until the end date.': 'تبقى المشكلة صامتة حتى تاريخ الانتهاء.', 'Allow exception': 'السماح بالاستثناء',
  'Record a follow-up': 'تسجيل متابعة', 'Record follow-up': 'تسجيل المتابعة', 'The file': 'الملف', 'Open file details': 'فتح تفاصيل الملف', 'History': 'السجل',
  'No history yet.': 'لا يوجد سجل بعد.', "Owner's note:": 'ملاحظة المالك:', 'Rule': 'القاعدة', 'Severity': 'الخطورة', 'Last checked': 'آخر فحص', 'Sensitivity': 'الحساسية',
  'What Hafez understood': 'ما فهمه حافظ', 'Not classified yet.': 'لم يُصنّف بعد.', 'Tell Hafez what this is': 'أخبر حافظ ما هذا', 'Your answer is saved and the file is checked again.': 'تُحفظ إجابتك ويُعاد فحص الملف.',
  'Document type': 'نوع المستند', 'Contains student data': 'يحتوي على بيانات طلاب', 'Contains health data': 'يحتوي على بيانات صحية', 'Who can open it': 'من يمكنه فتحه',
  'Access': 'الصلاحية', 'Outside': 'خارجي', 'Issues on this file': 'المشكلات في هذا الملف', 'No issues.': 'لا توجد مشكلات.', 'No snapshots yet.': 'لا توجد لقطات بعد.', 'Permissions': 'الصلاحيات',
  'Files you may see.': 'الملفات التي يمكنك رؤيتها.', '(deleted)': '(محذوف)', '· deleted': '· محذوف', 'Files Hafez could not classify with confidence. Your answer teaches the checker.': 'ملفات لم يتمكن حافظ من تصنيفها بثقة. إجابتك تعلّم أداة الفحص.',
  'Needs a person': 'يحتاج شخصاً', 'Best guess': 'أفضل تخمين', 'Confidence': 'الثقة', 'Evidence': 'الأدلة', '% sure': '% متأكد', 'Saved. The file was checked again.': 'تم الحفظ وأُعيد فحص الملف.',
  'Checked again: it is fixed. Thank you.': 'أُعيد الفحص: تم الإصلاح. شكراً لك.', 'Checked again: fixed.': 'أُعيد الفحص: تم الإصلاح.', 'the file still breaks the rule.': 'ما زال الملف يخالف القاعدة.',
  'Your coordinator has been asked to help.': 'طُلبت المساعدة من المنسق.', 'Your coordinator will look at it.': 'سيطّلع عليها المنسق.', 'Exception allowed.': 'تم السماح بالاستثناء.',
  'Rule kept. The owner was reminded.': 'أُبقيت القاعدة وتم تذكير المالك.', 'Reminder sent.': 'تم إرسال التذكير.', 'Follow-up recorded.': 'تم تسجيل المتابعة.', 'Note for the follow-up (optional):': 'ملاحظة للمتابعة (اختياري):',
  'Type trusted.': 'تم اعتماد النوع.', 'Everything in your school (read-only).': 'كل ما في مدرستك (للقراءة فقط).', 'Issues for your subject.': 'مشكلات مادتك.',

  // ---------------------------------------------------------------- coordinator
  'Coordinator overview': 'نظرة عامة للمنسق', 'Every file with an issue across your school, in one place.': 'كل ملف فيه مشكلة في مدرستك، في مكان واحد.', 'Review queue →': 'قائمة المراجعة →',
  'OPEN ISSUES': 'المشكلات المفتوحة', 'files waiting for a decision across': 'ملفات بانتظار قرار في', 'your school': 'مدرستك', 'All clear': 'لا شيء معلّق', 'Needs you': 'تحتاجك',
  'Owners waiting on you': 'ملاك بانتظارك', 'No owners waiting on you': 'لا يوجد ملاك بانتظارك', 'of': 'من', 'issues fixed': 'مشكلة تم إصلاحها', 'Urgent vs normal': 'العاجلة مقابل العادية',
  'Open issues by severity': 'المشكلات المفتوحة حسب الخطورة', 'open issues': 'مشكلة مفتوحة', 'open issue': 'مشكلة مفتوحة', 'Issues by type': 'المشكلات حسب النوع',
  'What is wrong with the open files': 'ما المشكلة في الملفات المفتوحة', 'No open issues.': 'لا توجد مشكلات مفتوحة.', 'Opened by day': 'المفتوحة حسب اليوم', 'New issues found': 'مشكلات جديدة مكتشفة',
  'Open vs fixed over time': 'المفتوحة مقابل المُصلحة عبر الزمن', 'Last 7 days': 'آخر 7 أيام', 'Last 30 days': 'آخر 30 يوماً', 'Files with issues': 'ملفات فيها مشكلات',
  'Open files that need a decision': 'ملفات مفتوحة تحتاج قراراً', 'Nothing here. Your files follow the rules.': 'لا شيء هنا. ملفاتك تتبع القواعد.', 'Open full issues list →': 'فتح القائمة الكاملة للمشكلات →',
  'Teacher help requests': 'طلبات مساعدة المعلمين', 'Owners who asked you for help': 'ملاك طلبوا مساعدتك', 'Mark done': 'تحديد كمنجز', 'Waiting for your reply': 'بانتظار ردك',
  'No help requests': 'لا توجد طلبات مساعدة', 'Nobody is waiting on you right now.': 'لا أحد ينتظرك الآن.', 'Line chart of open and fixed issues over the last 7 days': 'مخطط خطي للمشكلات المفتوحة والمُصلحة خلال آخر 7 أيام',
  'Issues waiting for you: owners who disagree, asked for help, or have not acted.': 'مشكلات بانتظارك: ملاك غير موافقين أو طلبوا المساعدة أو لم يتصرفوا.', 'Mark all done': 'تحديد الكل كمنجز',
  'Mark every follow-up as done?': 'تحديد كل المتابعات كمنجزة؟', 'Need follow-up': 'تحتاج متابعة', 'Open issues': 'المشكلات المفتوحة', 'Nothing needs you right now.': 'لا شيء يحتاجك الآن.',
  'Terms, subjects, classes and sections, students, teachers and the weekly timetable.': 'الفصول الدراسية والمواد والصفوف والشعب والطلاب والمعلمون والجدول الأسبوعي.',
  'Class requests': 'طلبات الصفوف', 'waiting for you': 'بانتظارك', 'ID': 'الرقم', 'Class asked for': 'الصف المطلوب', 'Asked by': 'مقدّم الطلب', 'Enrol': 'تسجيل',
  'Teachers can only set assessments for a term that exists here.': 'لا يمكن للمعلمين إنشاء تقييمات إلا لفصل دراسي موجود هنا.', 'Starts': 'يبدأ', 'Ends': 'ينتهي', 'Name, e.g. Term 1': 'الاسم، مثل: الفصل الأول',
  'Save term': 'حفظ الفصل', 'No term yet. Add term 1 below.': 'لا يوجد فصل بعد. أضف الفصل الأول أدناه.', 'Term saved.': 'تم حفظ الفصل.', 'No subject yet.': 'لا توجد مادة بعد.',
  'e.g. Mathematics': 'مثل: الرياضيات', 'Add subject': 'إضافة مادة', 'Subject added.': 'تمت إضافة المادة.', 'Document types': 'أنواع المستندات',
  'The default sensitivity Hafez gives that kind of document. Add a type or change one.': 'الحساسية الافتراضية التي يعطيها حافظ لهذا النوع من المستندات. أضف نوعاً أو غيّر أحدها.', 'Type name': 'اسم النوع',
  'Classes and sections': 'الصفوف والشعب', 'Grade 1 to 12': 'الصف من 1 إلى 12', 'Section, e.g. B': 'الشعبة، مثل: ب', 'Add class section': 'إضافة شعبة', 'Section added.': 'تمت إضافة الشعبة.',
  'enrolled': 'مسجّل', 'Enrol in this section': 'تسجيل في هذه الشعبة', 'Every student already has a class. New students appear here after they register.': 'لكل طالب صف بالفعل. يظهر الطلاب الجدد هنا بعد تسجيلهم.',
  'Student enrolled.': 'تم تسجيل الطالب.', 'Request declined.': 'تم رفض الطلب.', 'Who teaches what': 'من يدرّس ماذا', 'Teacher assigned.': 'تم تعيين المعلم.',
  'Sunday to Thursday. A section, a teacher or a room cannot be in two places in one period.': 'من الأحد إلى الخميس. لا يمكن أن تكون الشعبة أو المعلم أو القاعة في مكانين في الحصة نفسها.',
  'Room (optional)': 'القاعة (اختياري)', 'Add lesson': 'إضافة درس', 'Lesson added.': 'تمت إضافة الدرس.', 'Lesson removed.': 'تمت إزالة الدرس.', 'Remove this lesson?': 'إزالة هذا الدرس؟',
  'Mapping saved.': 'تم حفظ الربط.', 'Save mapping': 'حفظ الربط',

  // ---------------------------------------------------------------- school IT
  'A notice shows only between its start and end, and needs its Arabic text.': 'يظهر الإعلان فقط بين بدايته ونهايته، ويحتاج إلى نصه العربي.', 'Title (English)': 'العنوان (بالإنجليزية)',
  'Title (Arabic)': 'العنوان (بالعربية)', 'Text (English)': 'النص (بالإنجليزية)', 'Text (Arabic)': 'النص (بالعربية)', 'Publish notice': 'نشر الإعلان', 'All notices': 'كل الإعلانات',
  'Notice published.': 'تم نشر الإعلان.', 'Notice removed.': 'تمت إزالة الإعلان.', 'Remove this notice?': 'إزالة هذا الإعلان؟',
  'What Hafez checks. Changing a rule creates a new version that is approved before it applies.': 'ما يفحصه حافظ. تعديل القاعدة ينشئ نسخة جديدة تُعتمد قبل تطبيقها.', 'Add a rule': 'إضافة قاعدة',
  'rules': 'قواعد', 'No rules yet.': 'لا توجد قواعد بعد.', 'New rule': 'قاعدة جديدة', 'Edit rule': 'تعديل القاعدة', 'Approve new version': 'اعتماد النسخة الجديدة', 'New version waiting': 'نسخة جديدة بانتظار الاعتماد',
  'A new version of this rule is waiting to be approved.': 'نسخة جديدة من هذه القاعدة بانتظار الاعتماد.', 'Version approved. It applies from the next check.': 'تم اعتماد النسخة، وتُطبّق من الفحص القادم.',
  'Saving creates a draft version. Preview shows how many files it would affect and the message the owner would read.': 'الحفظ ينشئ نسخة مسودة. تُظهر المعاينة عدد الملفات المتأثرة والرسالة التي سيقرؤها المالك.',
  'What to check': 'ما يُفحص', 'Parameter (only some checks use one, e.g. an exam date or folder)': 'معامل (تستخدمه بعض الفحوصات فقط، مثل تاريخ امتحان أو مجلد)',
  'Document types (comma separated, empty = all)': 'أنواع المستندات (مفصولة بفواصل، فارغ = الكل)', 'Sensitivities (none ticked = any)': 'الحساسيات (عدم التحديد = أي)',
  'Why it matters (English)': 'لماذا يهم (بالإنجليزية)', 'Why it matters (Arabic)': 'لماذا يهم (بالعربية)', 'Fix steps (English, one per line)': 'خطوات الإصلاح (بالإنجليزية، خطوة في كل سطر)',
  'Fix steps (Arabic, one per line)': 'خطوات الإصلاح (بالعربية، خطوة في كل سطر)', 'Remind after (days)': 'التذكير بعد (أيام)', 'Turn on straight away': 'التفعيل فوراً', 'Would affect': 'سيؤثر على',
  'Rule saved.': 'تم حفظ القاعدة.', 'Rule deleted.': 'تم حذف القاعدة.', 'Rule paused.': 'تم إيقاف القاعدة.', 'Rule resumed.': 'تم استئناف القاعدة.', 'Network-wide rule': 'قاعدة على مستوى الشبكة',
  'Overridden': 'مُعدّلة', 'Checked on every scan': 'تُفحص في كل مسح', 'Active rules': 'القواعد النشطة', 'Paused or off': 'موقوفة أو معطلة', 'New versions': 'النسخ الجديدة', 'Waiting for approval': 'بانتظار الاعتماد',
  'Add a person': 'إضافة شخص', 'Add person': 'إضافة شخص', 'They sign in with their own Google account. Use the same e-mail they sign in with.': 'يسجّلون الدخول بحساب Google الخاص بهم. استخدم البريد نفسه الذي يدخلون به.',
  'name@school.edu': 'name@school.edu', 'Child (for a parent)': 'الطفل (لوليّ الأمر)', 'Only for a parent: the student they follow': 'لوليّ الأمر فقط: الطالب الذي يتابعه',
  'Role and status': 'الدور والحالة', 'Send welcome': 'إرسال ترحيب', 'Welcome message queued.': 'رسالة الترحيب في قائمة الإرسال.', 'Sync directory now': 'مزامنة الدليل الآن', 'people': 'شخص',
  'Role groups': 'مجموعات الأدوار', 'A directory group gives its members a role at their next sync.': 'تمنح مجموعة الدليل أعضاءها دوراً عند المزامنة التالية.', 'Directory group': 'مجموعة الدليل',
  'group@school.edu': 'group@school.edu', 'Everyone signs in with their own school account. There are no Hafez passwords.': 'يسجّل الجميع الدخول بحسابهم المدرسي. لا توجد كلمات مرور لحافظ.',
  'The schools in the network and whether they count toward the KPIs.': 'المدارس في الشبكة وما إذا كانت تُحتسب في مؤشرات الأداء.', 'School name': 'اسم المدرسة', 'School name:': 'اسم المدرسة:',
  'Add school': 'إضافة مدرسة', 'School added.': 'تمت إضافة المدرسة.', 'No school yet.': 'لا توجد مدرسة بعد.', 'No school yet. Add the first one below.': 'لا توجد مدرسة بعد. أضف أول مدرسة أدناه.',
  'Counted in KPIs': 'محتسبة في المؤشرات', 'Counted in the KPIs': 'محتسبة في المؤشرات', 'Include in the KPIs': 'تضمين في المؤشرات', 'Left out of totals': 'مستبعدة من الإجماليات',
  'Included in the school scores.': 'مضمّنة في درجات المدرسة.', 'No longer scored.': 'لم تعد مقيّمة.', 'Stop all checks for this school?': 'إيقاف كل الفحوصات لهذه المدرسة؟',
  'Hafez reads the school workspace with a read-only account. It can never change a file.': 'يقرأ حافظ مساحة عمل المدرسة بحساب للقراءة فقط، ولا يمكنه تغيير أي ملف.',
  'Connector': 'الموصّل', 'Connector installed': 'الموصّل مثبّت', 'Read-only account installed': 'حساب القراءة فقط مثبّت', 'No connector installed': 'لا يوجد موصّل مثبّت',
  'No workspace connector is installed, so no files can be read and no checks can run.': 'لا يوجد موصّل لمساحة العمل، لذا لا يمكن قراءة الملفات ولا تشغيل الفحوصات.', 'Nothing can be read': 'لا يمكن قراءة شيء',
  'Test connection': 'اختبار الاتصال', 'Connection tested.': 'تم اختبار الاتصال.', 'Disconnect': 'قطع الاتصال', 'Reconnect': 'إعادة الاتصال', 'Reconnected.': 'تمت إعادة الاتصال.',
  'Disconnected. No checks will run until you reconnect.': 'تم قطع الاتصال. لن تُشغّل أي فحوصات حتى تعيد الاتصال.', 'Run a check now': 'تشغيل فحص الآن', 'Run the first check': 'تشغيل الفحص الأول',
  'Check started. Refresh in a few seconds to see the result.': 'بدأ الفحص. حدّث الصفحة بعد ثوانٍ لترى النتيجة.', 'Recent checks': 'الفحوصات الأخيرة', 'Checks on': 'الفحوصات مفعّلة', 'Checks off': 'الفحوصات متوقفة',
  'First check:': 'الفحص الأول:', 'Files last check': 'ملفات آخر فحص', 'Files checked': 'الملفات المفحوصة', 'files checked': 'ملف مفحوص', 'Failed checks': 'فحوصات فاشلة',
  'Not checking now': 'لا يوجد فحص الآن', 'Waiting for the first check': 'بانتظار الفحص الأول', 'Of the recent': 'من الأخيرة',
  'Ask for a deleted or damaged file to be restored from backup. Two approvals are needed.': 'اطلب استعادة ملف محذوف أو تالف من النسخة الاحتياطية. يلزم اعتمادان.', 'Request a restore': 'طلب استعادة',
  'Request restore': 'طلب الاستعادة', 'Restore requested.': 'تم طلب الاستعادة.', 'Mark restored': 'تحديد كمُستعاد', 'File (for a single file)': 'الملف (لملف واحد)', 'Target (drive or folder, if not a single file)': 'الهدف (Drive أو مجلد، إن لم يكن ملفاً واحداً)',
  'Major events: exam leaks, important or bulk deletions, lost connection, urgent issues left open.': 'الأحداث الكبرى: تسريب امتحانات، حذف مهم أو جماعي، انقطاع الاتصال، مشكلات عاجلة دون حل.',
  'All incidents': 'كل الحوادث', 'recorded': 'مسجّلة', 'No incidents recorded.': 'لا توجد حوادث مسجّلة.', 'Incident': 'حادثة', 'Incident resolved.': 'تم حل الحادثة.', 'Open since': 'مفتوحة منذ',
  'What happened': 'ما الذي حدث', 'Open incidents': 'الحوادث المفتوحة', 'open incident': 'حادثة مفتوحة', 'open incidents': 'حوادث مفتوحة', 'No open incidents.': 'لا توجد حوادث مفتوحة.', 'None open': 'لا شيء مفتوح',
  'Every change is recorded in a chain that shows if anyone altered it.': 'يُسجّل كل تغيير في سلسلة تكشف أي تلاعب بها.', 'Verify the log': 'التحقق من السجل', 'Log verified:': 'تم التحقق من السجل:',
  'entries, none altered.': 'سجلاً، دون أي تلاعب.', 'Log broken at entry': 'السجل معطوب عند السجل رقم', 'In the chain': 'في السلسلة', 'Distinct actions': 'إجراءات مختلفة', 'Kinds of change': 'أنواع التغيير',

  // ---------------------------------------------------------------- principal and leadership
  'Where your school stands today.': 'وضع مدرستك اليوم.', 'Health score': 'درجة السلامة', 'Health score over time': 'درجة السلامة عبر الزمن', 'Network health': 'سلامة الشبكة', 'Network health score': 'درجة سلامة الشبكة',
  'Not enough history yet.': 'لا يوجد سجل كافٍ بعد.', 'Urgent issues': 'المشكلات العاجلة', 'Urgent right now': 'العاجل الآن', 'open urgent issue': 'مشكلة عاجلة مفتوحة', 'open urgent issues': 'مشكلات عاجلة مفتوحة',
  'No urgent issues are open.': 'لا توجد مشكلات عاجلة مفتوحة.', 'No urgent issues.': 'لا توجد مشكلات عاجلة.', 'No urgent issues or incidents.': 'لا توجد مشكلات أو حوادث عاجلة.', 'Nothing urgent is open.': 'لا يوجد شيء عاجل مفتوح.',
  'Open urgent': 'عاجلة مفتوحة', 'Open now': 'مفتوحة الآن', 'open now': 'مفتوحة الآن', 'Total open issues': 'إجمالي المشكلات المفتوحة', 'Weekly report (PDF)': 'التقرير الأسبوعي (PDF)',
  'Across every scored school. Totals only: Leadership never sees file names or contents.': 'عبر كل المدارس المقيّمة. إجماليات فقط: لا ترى القيادة أسماء الملفات أو محتواها.',
  'All schools →': 'كل المدارس →', 'All incidents →': 'كل الحوادث →', 'School performance': 'أداء المدارس', 'Schools by health band': 'المدارس حسب مستوى السلامة', 'Issues trend': 'اتجاه المشكلات',
  'Open issues across the scored schools': 'المشكلات المفتوحة في المدارس المقيّمة', 'Open issues across the scored schools, last 30 days': 'المشكلات المفتوحة في المدارس المقيّمة، آخر 30 يوماً',
  'Open incidents per day': 'الحوادث المفتوحة يومياً', 'Major events open per day': 'الأحداث الكبرى المفتوحة يومياً', 'Quick actions': 'إجراءات سريعة', 'Review rules': 'مراجعة القواعد',
  'Overall health': 'السلامة العامة', 'Healthy': 'سليمة', 'Keep an eye on it': 'تحتاج متابعة', 'Urgent problems': 'مشكلات عاجلة', 'Need action now': 'تحتاج إجراءً الآن', 'All clear': 'لا شيء عاجل',
  'Average score across all schools. 100 means no file problems.': 'متوسط الدرجات في كل المدارس. 100 تعني لا مشكلات في الملفات.', 'Serious problems, like sensitive files shared publicly.': 'مشكلات خطيرة، مثل مشاركة ملفات حساسة علناً.', 'No serious problems are open.': 'لا توجد مشكلات خطيرة مفتوحة.',
  'Smaller problems that staff are fixing. No action needed from you.': 'مشكلات أصغر يعالجها الموظفون. لا يلزمك أي إجراء.', 'Are problems going down?': 'هل تقل المشكلات؟', 'Open issues': 'مشكلات مفتوحة',
  'Each bar is one day: how many issues were open across all schools. Shorter bars are better.': 'كل عمود يمثل يوماً: عدد المشكلات المفتوحة في كل المدارس. كلما قصر العمود كان أفضل.',
  "Each school's score out of 100, worst first. Higher is better: 85+ is good, 70–84 needs watching, below 70 needs attention.": 'درجة كل مدرسة من 100، الأضعف أولاً. الأعلى أفضل: 85 فأكثر جيدة، 70–84 تحتاج متابعة، وأقل من 70 تحتاج اهتماماً.',
  'Serious events, such as an exam leak or deleted files, that someone must resolve. The chart shows how many were open each day.': 'أحداث خطيرة، مثل تسرب امتحان أو حذف ملفات، يجب أن يعالجها أحد. يوضح الرسم عدد المفتوح كل يوم.',
  'Serious rule breaks that are still open. You see the school and the rule only, never file names.': 'مخالفات خطيرة للقواعد ما زالت مفتوحة. ترى المدرسة والقاعدة فقط، ولا ترى أسماء الملفات.',
  'How many schools are healthy, need watching, or need attention.': 'كم مدرسة سليمة أو تحتاج متابعة أو اهتماماً.',
  'Other open issues': 'مشكلات مفتوحة أخرى', 'Being tracked by schools': 'تتابعها المدارس',
  'Rule changes waiting for approval': 'تغييرات قواعد بانتظار الاعتماد', 'Rule change': 'تغيير قاعدة', 'Needs your decision': 'تحتاج قرارك', 'Caused by these rules': 'بسبب هذه القواعد',
  'No check has finished for these schools yet. Numbers appear after it.': 'لم يكتمل أي فحص لهذه المدارس بعد. تظهر الأرقام بعده.', 'No check has finished yet. Numbers appear after the first check.': 'لم يكتمل أي فحص بعد. تظهر الأرقام بعد الفحص الأول.',
  'No check yet': 'لا يوجد فحص بعد', 'No school is scored yet': 'لا توجد مدرسة مقيّمة بعد', 'No scored school yet.': 'لا توجد مدرسة مقيّمة بعد.', 'Schools not counted in these numbers': 'مدارس غير محتسبة في هذه الأرقام',
  'The totals below add up only the counted schools. A school that holds files but is not counted leaves the KPIs at zero.': 'تجمع الإجماليات أدناه المدارس المحتسبة فقط. المدرسة غير المحتسبة لا تؤثر في المؤشرات.',
  'Worst first. Score = 100 − ((open normal + 3 × open urgent) ÷ files × 100)': 'الأسوأ أولاً. الدرجة = 100 − ((العادية المفتوحة + 3 × العاجلة المفتوحة) ÷ الملفات × 100)',
  'Which school and which rule. File names stay with the school.': 'أي مدرسة وأي قاعدة. تبقى أسماء الملفات لدى المدرسة.', 'School, rule and status only. File names stay with the school.': 'المدرسة والقاعدة والحالة فقط. تبقى أسماء الملفات لدى المدرسة.',
  'The chart appears once a few days of history have been recorded.': 'يظهر المخطط بعد تسجيل بضعة أيام من السجل.', 'View score': 'عرض الدرجة', 'Overall': 'الإجمالي', 'Listed below': 'مدرجة أدناه',
  'In the network': 'في الشبكة', 'Closed out': 'تم إغلاقها', 'fixed by staff': 'أصلحها الموظفون', 'files in personal drives': 'ملف في Drive شخصي', 'issues fixed by staff ·': 'مشكلة أصلحها الموظفون ·',
  ', score 0 to 100': '، الدرجة من 0 إلى 100', 'files (': 'ملفات (', 'need review).': 'تحتاج مراجعة).', 'changed).': 'تغيّرت).', 'Age': 'العمر', 'Need a look': 'تحتاج نظرة',
};

Object.assign(AR, { 'normal': 'عادية', 'urgent': 'عاجلة', 'Needs follow up': 'تحتاج متابعة', 'high': 'الأعلى', 'low': 'الأدنى', 'now': 'الآن', 'peak': 'الذروة',
  'last': 'آخر', 'days': 'أيام', 'last 14 days': 'آخر 14 يوماً', 'to': 'إلى', 'and': 'و', 'Opened': 'فُتحت', 'Closed': 'أُغلقت', 'present': 'حاضر', 'absent': 'غائب', 'leave': 'إجازة',
  'Record today. Everyone starts as present; change only the absences and leave. You can correct the same day only.': 'سجّل حضور اليوم. يبدأ الجميع حاضرين؛ غيّر الغياب والإجازة فقط. يمكنك التصحيح في اليوم نفسه فقط.',
  'file': 'ملف', 'files': 'ملفات', 'students': 'طلاب', 'assessment': 'تقييم', 'assessments': 'تقييمات', 'rule': 'قاعدة', 'recorded': 'مسجّلة', 'people': 'شخص', 'result': 'نتيجة', 'open urgent issue': 'مشكلة عاجلة مفتوحة', 'open incident': 'حادثة مفتوحة' });

/** Phrases built in code around numbers or names: [pattern, Arabic builder]. A builder returns null to pass. */
/** Support tickets, profile and alert settings. Only adds words that have no translation yet. */
(function () {
  const more = {
    'Support tickets': 'تذاكر الدعم', 'New ticket': 'تذكرة جديدة', 'Send ticket': 'إرسال التذكرة', 'Tickets': 'التذاكر', 'Back to tickets': 'العودة إلى التذاكر',
    'Write to EAA head office. Replies and status changes appear here and in your notifications.': 'اكتب إلى المكتب الرئيسي. تظهر الردود وتغيّر الحالة هنا وفي إشعاراتك.',
    'Your school\'s requests to EAA head office.': 'طلبات مدرستك إلى المكتب الرئيسي.',
    'Requests from every school. Open one to assign, reply or close it.': 'طلبات من جميع المدارس. افتح تذكرة لإسنادها أو الرد عليها أو إغلاقها.',
    'Technical problem': 'مشكلة تقنية', 'Accounts and access': 'الحسابات والصلاحيات', 'Marks and academic data': 'الدرجات والبيانات الأكاديمية', 'Google Drive': 'جوجل درايف',
    'Security concern': 'مسألة أمنية', 'Other': 'أخرى', 'Category': 'التصنيف', 'Urgency': 'الأهمية', 'Low': 'منخفضة', 'Normal': 'عادية', 'High': 'عالية', 'Urgent': 'عاجلة',
    'Title': 'العنوان', 'Description': 'الوصف', 'Status': 'الحالة', 'Assigned to': 'مسندة إلى', 'Updated': 'آخر تحديث', 'School': 'المدرسة',
    'Attachment (optional: PDF, image, Word, Excel or text, up to 2 MB)': 'مرفق (اختياري: PDF أو صورة أو Word أو Excel أو نص، حتى 2 ميغابايت)',
    'Open': 'مفتوحة', 'In progress': 'قيد المعالجة', 'Closed': 'مغلقة', 'All': 'الكل', 'By school': 'حسب المدرسة', 'Open work across the network.': 'العمل المفتوح في الشبكة.',
    'Urgent and unresolved': 'عاجلة وغير محلولة', 'Waiting for head office': 'بانتظار المكتب الرئيسي', 'Being handled': 'قيد المتابعة', 'Resolved': 'تم حلها', 'No tickets yet.': 'لا توجد تذاكر بعد.',
    'Handled by': 'يتولاها', 'Not assigned yet': 'لم تُسند بعد',
    'Details': 'التفاصيل', 'Handle this ticket': 'معالجة هذه التذكرة', 'Assign to': 'إسناد إلى', 'Assign': 'إسناد', 'Close ticket': 'إغلاق التذكرة', 'Reopen': 'إعادة فتح', 'Mark in progress': 'تحديد كقيد المعالجة',
    'Conversation': 'المحادثة', 'No replies yet.': 'لا توجد ردود بعد.', 'Reply': 'رد', 'Your reply': 'ردّك', 'Send reply': 'إرسال الرد',
    'This ticket is closed. Open a new ticket for a new question.': 'هذه التذكرة مغلقة. افتح تذكرة جديدة لسؤال جديد.', 'Leadership can read tickets but not reply.': 'تستطيع القيادة قراءة التذاكر دون الرد.',
    'Ticket sent to EAA head office.': 'أُرسلت التذكرة إلى المكتب الرئيسي.', 'Reply sent.': 'تم إرسال الرد.', 'Status updated.': 'تم تحديث الحالة.', 'Assigned.': 'تم الإسناد.',
    'My profile and preferences': 'ملفي الشخصي وتفضيلاتي', 'Your details, photo, language and which alerts you receive.': 'بياناتك وصورتك ولغتك والتنبيهات التي تصلك.',
    'Profile': 'الملف الشخصي', 'Upload photo': 'رفع صورة', 'Remove photo': 'إزالة الصورة', 'PNG or JPEG, at most 500 KB.': 'PNG أو JPEG، حتى 500 كيلوبايت.',
    'Name': 'الاسم', 'E-mail (from your school account)': 'البريد الإلكتروني (من حساب مدرستك)', 'Phone': 'الهاتف', 'Save details': 'حفظ البيانات',
    'Messages and alerts': 'الرسائل والتنبيهات', 'Choose the language, and which alerts reach your notifications.': 'اختر اللغة والتنبيهات التي تصل إلى إشعاراتك.', 'Alerts': 'التنبيهات',
    'File issues': 'مشكلات الملفات', 'Problems found in files you own or oversee.': 'مشكلات في ملفات تملكها أو تشرف عليها.', 'Reminders': 'التذكيرات', 'Reminders about issues that are still open.': 'تذكيرات بالمشكلات التي ما زالت مفتوحة.',
    'Follow-ups': 'المتابعات', 'Issues that need a coordinator.': 'مشكلات تحتاج إلى منسّق.', 'Incidents': 'الحوادث', 'Serious incidents in your school or the network.': 'حوادث خطيرة في مدرستك أو الشبكة.',
    'Lesson plans': 'خطط الدروس', 'Submissions and reviews of lesson plans.': 'تسليم خطط الدروس ومراجعتها.', 'Weekly grade deadlines': 'مواعيد الدرجات الأسبوعية', 'A reminder when this week\'s grades are not submitted yet.': 'تذكير عندما لا تُسلَّم درجات هذا الأسبوع بعد.',
    'School notices': 'إعلانات المدرسة', 'New notices from your school.': 'إعلانات جديدة من مدرستك.', 'New tickets, replies and status changes.': 'تذاكر وردود وتغيّر حالة جديدة.',
    'General updates': 'تحديثات عامة', 'Welcome messages and published results.': 'رسائل الترحيب والنتائج المنشورة.', 'Security and account events': 'أحداث الأمان والحساب',
    'Role changes, approval requests and locked accounts. Always on.': 'تغيير الأدوار وطلبات الموافقة والحسابات المقفلة. مفعّلة دائماً.',
    'Details saved.': 'تم حفظ البيانات.', 'Photo saved.': 'تم حفظ الصورة.', 'Photo removed.': 'تمت إزالة الصورة.', 'Choose a photo first.': 'اختر صورة أولاً.', 'Choose a photo of at most 500 KB.': 'اختر صورة بحجم أقصاه 500 كيلوبايت.',
    'The attachment is too large (at most 2 MB).': 'المرفق كبير جداً (الحد الأقصى 2 ميغابايت).'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
})();

/** Student pick-up. Only adds words that have no translation yet. */
(function () {
  const more = {
    'Pick-up': 'الاستلام', 'Pick-up board': 'لوحة الاستلام',
    'Approve who may collect your child and tell the school when you arrive.': 'حدّد من يحق له استلام طفلك وأخبر المدرسة عند وصولك.',
    'Right now': 'الآن', 'The school sees this at once. It updates by itself.': 'تراه المدرسة فوراً ويتحدث تلقائياً.',
    'You have not told the school you are here.': 'لم تُخبر المدرسة بوصولك.', 'Waiting for the school': 'بانتظار المدرسة', 'Being checked': 'قيد التحقق',
    'Released': 'تم التسليم', 'Not released': 'لم يتم التسليم', 'Cancelled': 'ملغى', 'Withdraw': 'سحب الطلب',
    'Approved collectors, and the button for when you arrive.': 'المخوَّلون بالاستلام، وزر الوصول.', 'Who is collecting?': 'من سيستلم؟', 'Me': 'أنا',
    'I\'ve arrived': 'لقد وصلت', 'Name': 'الاسم', 'Relationship': 'صلة القرابة', 'ID ends': 'آخر أرقام الهوية', 'Phone': 'الهاتف', 'Remove': 'إزالة',
    'Remove this collector? They will no longer be released the child.': 'إزالة هذا الشخص؟ لن يُسلَّم الطفل إليه بعد الآن.',
    'No approved collectors yet. You can always collect your child yourself.': 'لا يوجد مخوَّلون بعد. يمكنك دائماً استلام طفلك بنفسك.',
    'Add a collector': 'إضافة مخوَّل بالاستلام', 'Full name': 'الاسم الكامل', 'Driver': 'سائق', 'Relative': 'قريب', 'Guardian': 'وصي', 'Parent': 'ولي أمر', 'Other': 'آخر',
    'ID number (only the last four are kept)': 'رقم الهوية (يُحفظ آخر أربعة أرقام فقط)', 'Phone (optional)': 'الهاتف (اختياري)', 'Add collector': 'إضافة',
    'Recent releases': 'آخر عمليات التسليم', 'Collected by': 'استلمه', 'By staff member': 'بواسطة الموظف', 'When': 'الوقت',
    'No children are linked to your account yet.': 'لا يوجد أبناء مرتبطون بحسابك بعد.',
    'Live view, read only. It refreshes every few seconds.': 'عرض مباشر للقراءة فقط. يتحدث كل بضع ثوانٍ.',
    'Live: it refreshes every few seconds. Check the collector\'s ID, then release the child.': 'مباشر: يتحدث كل بضع ثوانٍ. تحقق من هوية المستلم ثم سلّم الطفل.',
    'Arrivals': 'الواصلون', 'Parents who have arrived and are waiting.': 'أولياء أمور وصلوا وينتظرون.', 'Pending pickups': 'عمليات استلام معلّقة',
    'ID checked against the approved list. Release when the child is handed over.': 'تم التحقق من الهوية مع القائمة المعتمدة. سلّم الطفل عند تسليمه.',
    'Completed today': 'المكتملة اليوم', 'Student': 'الطالب', 'Class': 'الصف', 'Collector': 'المستلم', 'Arrived': 'وصل', 'Result': 'النتيجة', 'By': 'بواسطة', 'Verify ID': 'تحقق من الهوية',
    'Release': 'تسليم', 'Decline': 'رفض', 'Nobody has arrived.': 'لم يصل أحد.', 'Nothing is being checked.': 'لا شيء قيد التحقق.', 'Nothing released yet today.': 'لم يُسلَّم أحد اليوم بعد.',
    'Release log': 'سجل التسليم', 'Chain of custody: every release is kept permanently and cannot be changed. The last 30 days are shown.': 'سلسلة الحيازة: يُحفظ كل تسليم بشكل دائم ولا يمكن تغييره. تُعرض آخر 30 يوماً.',
    'Released by': 'سلّمه', 'Date and time': 'التاريخ والوقت', 'No releases in the last 30 days.': 'لا توجد عمليات تسليم في آخر 30 يوماً.',
    'Why are you declining?': 'ما سبب الرفض؟', 'ID checked. You can release the child now.': 'تم التحقق من الهوية. يمكنك تسليم الطفل الآن.', 'Child released.': 'تم تسليم الطفل.',
    'Request declined.': 'تم رفض الطلب.', 'Withdrawn.': 'تم السحب.', 'Collector removed.': 'تمت إزالة المخوَّل.', 'Collector added.': 'تمت إضافة المخوَّل.',
    'The school has been told you have arrived.': 'تم إبلاغ المدرسة بوصولك.', 'A parent has arrived, or a child was released or not released.': 'وصول ولي أمر، أو تسليم طفل أو عدم تسليمه.'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
})();

/** Tickets to School IT or Leadership, file deletion, points. Only adds words that have no translation yet. */
(function () {
  const more = {
    'School IT': 'تقنية المعلومات بالمدرسة', 'Leadership': 'القيادة', 'Send to': 'إرسال إلى', 'From': 'من', 'To': 'إلى',
    'Write to your School IT, or to Leadership at head office. Replies and status changes appear here and in your notifications.': 'اكتب إلى تقنية المعلومات بمدرستك أو إلى القيادة في المكتب الرئيسي. تظهر الردود وتغيّر الحالة هنا وفي إشعاراتك.',
    'Ask your School IT for help. Replies and status changes appear here and in your notifications.': 'اطلب المساعدة من تقنية المعلومات بمدرستك. تظهر الردود وتغيّر الحالة هنا وفي إشعاراتك.',
    'Tickets that principals wrote to Leadership. Open one to assign, reply or close it.': 'تذاكر كتبها المديرون إلى القيادة. افتح تذكرة لإسنادها أو الرد عليها أو إغلاقها.',
    'Tickets from staff at your school. Open one to assign, reply or close it.': 'تذاكر من موظفي مدرستك. افتح تذكرة لإسنادها أو الرد عليها أو إغلاقها.',
    'Every ticket at your school, and the ones you wrote to Leadership.': 'كل تذاكر مدرستك، والتي كتبتها إلى القيادة.',
    'Your requests to School IT.': 'طلباتك إلى تقنية المعلومات بالمدرسة.', 'Waiting': 'بالانتظار',
    'You can read this ticket. Only the sender and the team handling it can reply.': 'يمكنك قراءة هذه التذكرة. يرد عليها المُرسِل والفريق المسؤول فقط.',
    'Class points. Teachers give recognition points; you can view them.': 'نقاط الصف. يمنح المعلمون نقاط التقدير ويمكنك الاطلاع عليها.',
    'Publishing shows a term to the students at once. Every assessment of that term must be submitted first.': 'النشر يُظهر الفصل للطلاب فوراً. يجب تسليم كل تقييمات الفصل أولاً.',
    'Results published. Students can see them now.': 'تم نشر النتائج. يستطيع الطلاب رؤيتها الآن.',
    'Google Drive disconnected.': 'تم فصل جوجل درايف.', 'Remove the school': 'إزالة المدرسة', 'School removed.': 'تمت إزالة المدرسة.',
    'Deleting a file needs a second person. You cannot approve your own request.': 'حذف ملف يحتاج إلى شخص ثانٍ. لا يمكنك الموافقة على طلبك بنفسك.',
    'Delete file': 'حذف ملف', 'Withdrawn': 'مسحوب', 'Delete this file': 'حذف هذا الملف',
    'Removes it from the register. A principal, coordinator or School IT must approve first. Your Google Drive is never touched.': 'يزيله من السجل. يجب أن يوافق مدير المدرسة أو المنسّق أو تقنية المعلومات أولاً. لا يتم المساس بجوجل درايف أبداً.',
    'Ask to delete': 'طلب الحذف', 'Ask to delete this file?': 'هل تطلب حذف هذا الملف؟',
    'Sent for approval. A principal, coordinator or School IT must approve before the file is deleted.': 'أُرسل للموافقة. يجب أن يوافق مدير المدرسة أو المنسّق أو تقنية المعلومات قبل حذف الملف.'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
})();

/** Pick-up and ticket card screens. Only adds words that have no translation yet. */
(function () {
  const more = {
    'Your school\'s IT team': 'فريق تقنية المعلومات بمدرستك', 'Head office team': 'فريق المكتب الرئيسي',
    'Can wait': 'يمكن الانتظار', 'This week': 'هذا الأسبوع', 'Soon': 'قريباً', 'Blocking work': 'يعطّل العمل',
    'How urgent is it?': 'ما مدى الاستعجال؟', 'What is it about?': 'ما موضوع التذكرة؟', 'Title': 'العنوان', 'Description': 'الوصف',
    'A short summary of the problem': 'ملخص قصير للمشكلة', 'What happened, and what did you expect?': 'ماذا حدث، وماذا كنت تتوقع؟',
    'Attachment (optional)': 'مرفق (اختياري)', 'PDF, image, Word, Excel or text, up to 2 MB': 'PDF أو صورة أو Word أو Excel أو نص، حتى 2 ميجابايت',
    'Send ticket': 'إرسال التذكرة', 'Send reply': 'إرسال الرد', 'Write your reply': 'اكتب ردك', 'Your reply': 'ردك',
    'No tickets here yet.': 'لا توجد تذاكر هنا بعد.', 'Sent': 'أُرسلت', 'Handled by': 'يتولاها', 'Not assigned yet': 'لم تُسند بعد',
    'Ticket sent to Leadership at head office.': 'أُرسلت التذكرة إلى القيادة في المكتب الرئيسي.', 'Ticket sent to your School IT.': 'أُرسلت التذكرة إلى تقنية المعلومات بمدرستك.',
    'Choose who is collecting, then press the button when you arrive.': 'اختر من سيستلم الطفل، ثم اضغط الزر عند وصولك.',
    'Who is collecting?': 'من سيستلم؟', 'Approved collectors': 'المخوَّلون بالاستلام', 'Arrived': 'وصل', 'ID checked': 'تم التحقق من الهوية',
    'You have already told the school you have arrived for this child.': 'لقد أبلغت المدرسة بوصولك لهذا الطفل بالفعل.',
    'Ready to release': 'جاهز للتسليم', 'Waiting': 'بالانتظار', 'just now': 'الآن', 'min': 'د', 'ID ends': 'آخر أرقام الهوية',
    'Parent: check the ID at the gate.': 'ولي الأمر: تحقق من الهوية عند البوابة.', 'ID checked by': 'تحقق من الهوية:', 'Collector': 'المستلم',
    'EAA IT': 'تقنية المعلومات في الهيئة', 'Attendance Clerk': 'كاتب الحضور', 'Students': 'الطلاب', 'On leave': 'في إجازة', 'All present': 'الجميع حاضر',
    'Mark all present': 'تسجيل الجميع حاضراً', 'Save attendance': 'حفظ الحضور', 'Pick-up today': 'استلام اليوم',
    'Parents arriving to collect their children. You can follow it live.': 'أولياء أمور يصلون لاستلام أبنائهم. يمكنك متابعة ذلك مباشرة.',
    'Open the pick-up board': 'افتح لوحة الاستلام', 'Not taken yet': 'لم يُسجَّل بعد',
    'Next school day: Sunday': 'يوم الدراسة القادم: الأحد', 'Today is a day off. Here is what your week starts with.': 'اليوم عطلة. هذا ما يبدأ به أسبوعك.',
    'Today is a day off. Attendance is recorded Sunday to Thursday, so you can read the lists but not save.': 'اليوم عطلة. يُسجَّل الحضور من الأحد إلى الخميس، لذا يمكنك قراءة القوائم دون الحفظ.'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
})();

/** Screens that still showed English in Arabic: the principal's school page, invitations, the network overview,
 *  the activity log, the starter rules and document types, incident texts. Only adds words that have no translation yet. */
(function () {
  const more = {
    // principal: My school
    'MY SCHOOL': 'مدرستي', 'HEALTH SCORE · OF 100': 'درجة السلامة · من 100', 'No score yet': 'لا توجد درجة بعد', '✓ On track': '✓ على المسار الصحيح',
    'On track': 'على المسار الصحيح', '! Needs attention': '! يحتاج اهتماماً', 'Not enough history yet.': 'لا يوجد سجل كافٍ بعد.',
    'Files waiting for a decision': 'ملفات بانتظار قرار', 'Act first': 'ابدأ بها', 'files need action first': 'ملفات تحتاج إجراءً أولاً',
    'Nothing urgent': 'لا شيء عاجل', 'Teachers and coordinators': 'المعلمون والمنسقون', 'Who owns the open files': 'من يملك الملفات المفتوحة',
    'Coordinators': 'المنسقون', 'Review and approve fixes': 'يراجعون الإصلاحات ويعتمدونها', 'Teachers': 'المعلمون',
    'Own the files with issues': 'يملكون الملفات التي فيها مشكلات', 'Nobody yet.': 'لا أحد بعد.', 'open': 'مفتوحة',
    'Score 0 to 100': 'الدرجة من 0 إلى 100', 'open files': 'ملفات مفتوحة', 'Issues by type': 'المشكلات حسب النوع', 'Other': 'أخرى',
    // network overview
    'Improving:': 'تحسّن:', 'Getting worse:': 'تراجع:', 'No change:': 'بلا تغيير:', 'the same number of issues as at the start.': 'عدد المشكلات نفسه كما في البداية.',
    // invitations
    'For': 'لمن', 'A parent (links one or more children)': 'وليّ أمر (يربط طفلاً أو أكثر)', 'A student (sets their password)': 'طالب (يختار كلمة مروره)',
    'Student(s)': 'الطالب (الطلاب)', 'Expires': 'ينتهي', 'Code (shown only now):': 'الرمز (يظهر الآن فقط):', 'Valid until': 'صالح حتى',
    '. Give it to the family; it cannot be shown again.': '. أعطه للأسرة؛ لا يمكن عرضه مرة أخرى.',
    'Parent e-mail (optional: the code then works only for this address)': 'بريد وليّ الأمر (اختياري: يعمل الرمز عندها لهذا العنوان فقط)',
    // other screens
    'Publishing shows a term to the students and parents at once. Every assessment of that term must be submitted first. You can keep adding assessments to a published term: each one appears as soon as you submit its marks.': 'النشر يُظهر الفصل للطلاب وأولياء الأمور فوراً. يجب تسليم كل تقييمات الفصل أولاً. يمكنك مواصلة إضافة تقييمات إلى فصل منشور: يظهر كل تقييم بمجرد تسليم درجاته.',
    'waiting for marks': 'بانتظار الدرجات',
    'Publish these marks': 'نشر هذه الدرجات', 'Unpublish': 'إلغاء النشر',
    'Published. Students and parents can see these marks now.': 'تم النشر. يستطيع الطلاب وأولياء الأمور رؤية هذه الدرجات الآن.',
    'Results are hidden again.': 'أُخفيت النتائج مجدداً.', 'The results are published: unpublish them first to change them.': 'النتائج منشورة: ألغِ نشرها أولاً لتعديلها.',
    'You can publish each assessment on its own from the list above (for example the exam), as many as you like. Or publish a whole term at once: it shows a term to the students and parents at once. Every assessment of that term must be submitted first. You can keep adding assessments to a published term: each one appears as soon as you submit its marks.': 'يمكنك نشر كل تقييم على حدة من القائمة أعلاه (مثل الاختبار)، وبقدر ما تشاء. أو انشر فصلاً كاملاً دفعة واحدة: فيظهر الفصل للطلاب وأولياء الأمور فوراً. يجب تسليم كل تقييمات الفصل أولاً. يمكنك مواصلة إضافة تقييمات إلى فصل منشور: يظهر كل تقييم بمجرد تسليم درجاته.',
    'ASSESSMENT_PUBLISHED': 'نشر تقييم', 'ASSESSMENT_UNPUBLISHED': 'إلغاء نشر تقييم',
    'Request to delete a file': 'طلب حذف ملف', 'Why should it be deleted?': 'لماذا يجب حذفه؟', 'Send the request': 'إرسال الطلب',
    'For example: an old copy, replaced by the new version': 'مثال: نسخة قديمة حلّت محلها نسخة جديدة',
    'Write why the file should go. The principal or a coordinator of your school approves it before it is deleted from the register. Your Google Drive is never touched.': 'اكتب سبب حذف الملف. يوافق مدير المدرسة أو أحد المنسقين قبل حذفه من السجل. لا يُمَسّ Google Drive الخاص بك أبداً.',
    'You have no files that can be deleted.': 'لا توجد لديك ملفات يمكن حذفها.',
    'Sent for approval. The principal or a coordinator must approve before the file is deleted.': 'أُرسل الطلب للموافقة. يجب أن يوافق مدير المدرسة أو أحد المنسقين قبل حذف الملف.',
    'Delete file': 'حذف ملف', 'Withdrawn': 'مسحوب', 'This file is already deleted.': 'هذا الملف محذوف بالفعل.',
    'changed': 'تغييرات', 'Date': 'التاريخ', 'Present at school': 'حاضر في المدرسة', 'Link (https://…)': 'الرابط (https://…)', 'points': 'نقطة', 'overdue': 'متأخرة',
    'No teaching assignments yet.': 'لا توجد إسنادات تدريس بعد.', 'No sections yet.': 'لا توجد شعب بعد.', 'No assessments yet.': 'لا توجد تقييمات بعد.',
    'No schools yet.': 'لا توجد مدارس بعد.', 'The file could not be read.': 'تعذّرت قراءة الملف.', 'Person deleted.': 'تم حذف الشخص.',
    'Delete this person permanently?': 'حذف هذا الشخص نهائياً؟', 'ID ends ••': 'آخر أرقام الهوية ••',
    'Every student already has a class. New students appear here once the school has added them.': 'لكل طالب صف بالفعل. يظهر الطلاب الجدد هنا بعد أن تضيفهم المدرسة.',
    'Test sign-in': 'دخول تجريبي', 'Sign in with a name (testing)': 'الدخول بالاسم (للتجربة)', 'Or pick a person:': 'أو اختر شخصاً:',
    'For testing only. Type a name, for example Huda, to sign in with that person\'s role. There is no password.': 'للتجربة فقط. اكتب اسماً، مثل هدى، للدخول بدور ذلك الشخص. لا توجد كلمة مرور.',
    'Test sign-in is switched off. Start the app with SIJILL_SIGNIN_TEST_MODE=true to use it.': 'الدخول التجريبي متوقف. شغّل التطبيق مع SIJILL_SIGNIN_TEST_MODE=true لاستخدامه.',
    'Manual': 'يدوي', 'Unknown': 'غير معروف', 'Verifying': 'قيد التحقق', 'Declined': 'مرفوض',
    // activity log: actions and what they are about
    'POINTS_AWARDED': 'منح نقاط', 'APPROVAL_REQUESTED': 'طلب موافقة', 'activity-log (masked)': 'سجل النشاط (بلا أسماء)', 'issues (masked)': 'المشكلات (بلا أسماء)', 'weekly-report': 'التقرير الأسبوعي', 'weekly-report (ar)': 'التقرير الأسبوعي (عربي)', 'STUDENT_RECORD_VIEWED': 'عرض سجل طالب', 'TICKET_CREATED': 'إنشاء تذكرة', 'TICKET_REPLIED': 'رد على تذكرة', 'TICKET_ASSIGNED': 'إسناد تذكرة',
    'TICKET_STATUS_CHANGED': 'تغيير حالة تذكرة', 'PROFILE_UPDATED': 'تحديث الملف الشخصي', 'FILE_DELETED': 'حذف ملف',
    'PICKUP_COLLECTOR_ADDED': 'إضافة مستلم', 'PICKUP_COLLECTOR_REMOVED': 'إزالة مستلم', 'PICKUP_ARRIVED': 'وصول للاستلام',
    'PICKUP_VERIFIED': 'التحقق من المستلم', 'PICKUP_RELEASED': 'تسليم طالب', 'PICKUP_DECLINED': 'رفض التسليم', 'PICKUP_CANCELLED': 'إلغاء الاستلام',
    'ApprovalRequest': 'طلب موافقة', 'DocType': 'نوع مستند', 'Exception': 'استثناء', 'Export': 'تصدير', 'Invitation': 'دعوة', 'JobRun': 'تشغيل مهمة',
    'Notice': 'إعلان', 'PickupRequest': 'طلب استلام', 'PlanningSubmission': 'خطة درس', 'Report': 'تقرير', 'RestoreRequest': 'طلب استعادة',
    'RoleGroup': 'مجموعة أدوار', 'SchoolFile': 'ملف مدرسي', 'Site': 'مدرسة', 'SupportTicket': 'تذكرة دعم',
    'marks sheet': 'كشف الدرجات', 'parent view': 'عرض وليّ الأمر',
    'OWNER_NOTIFIED': 'تم إبلاغ المالك', 'REMINDER_SENT': 'أُرسل تذكير', 'NEEDS_FOLLOW_UP': 'تحتاج متابعة', 'NEEDS_REVIEW': 'تحتاج مراجعة',
    'OWNER_DISAGREES': 'المالك غير موافق', 'OWNER_ASKED_FOR_HELP': 'طلب المساعدة', 'FIXED': 'تم الإصلاح', 'ALLOWED_EXCEPTION': 'استثناء مسموح',
    'RULE_PAUSED': 'القاعدة متوقفة', 'CLOSED_RULE_REMOVED': 'القاعدة محذوفة', 'OPEN': 'مفتوحة', 'IN_PROGRESS': 'قيد المعالجة', 'CLOSED': 'مغلقة',
    'PENDING': 'بانتظار الموافقة', 'APPROVED': 'تمت الموافقة', 'REJECTED': 'مرفوض', 'CANCELLED': 'ملغى', 'ACTIVE': 'فعّالة', 'PAUSED': 'متوقفة', 'DRAFT': 'مسودة',
    // document types (starter list)
    'Mark book': 'سجل الدرجات', 'Student report': 'تقرير طالب', 'Behaviour record': 'سجل السلوك', 'Medical record': 'سجل طبي',
    'Special-needs record': 'سجل الاحتياجات الخاصة', 'Contact list': 'قائمة جهات الاتصال', 'Exam': 'اختبار', 'Policy': 'سياسة', 'Lesson plan': 'خطة درس',
    'Worksheet': 'ورقة عمل', 'Circular': 'تعميم', 'Meeting minutes': 'محضر اجتماع',
    // starter rule names (their explanations already come in Arabic from the server)
    'School files in the school shared drive': 'ملفات المدرسة في المساحة المشتركة للمدرسة', 'No public links on sensitive files': 'لا روابط عامة على الملفات الحساسة',
    'Restricted files stay inside': 'الملفات المقيّدة تبقى في الداخل', 'Student records in Student Records': 'سجلات الطلاب في مساحة سجلات الطلاب',
    'Only the right people open Restricted files': 'الملفات المقيّدة للأشخاص المعنيين فقط', 'No exam sharing before the exam': 'لا مشاركة للاختبار قبل موعده',
    'One official copy per policy': 'نسخة رسمية واحدة لكل سياسة', 'Leavers move school files first': 'المغادرون ينقلون ملفات المدرسة أولاً',
    'No files owned by shared accounts': 'لا ملفات تملكها حسابات مشتركة', 'No unapproved deletion of important files': 'لا حذف للملفات المهمة دون موافقة',
    // incidents
    'An exam file was shared before its date.': 'تمت مشاركة ملف اختبار قبل موعده.', 'An important file was deleted or moved to the trash.': 'حُذف ملف مهم أو نُقل إلى سلة المحذوفات.',
    'An urgent issue is still open after one school day.': 'ما زالت مشكلة عاجلة مفتوحة بعد يوم دراسي.',
    // common subject names
    'Mathematics': 'الرياضيات', 'Science': 'العلوم', 'English': 'اللغة الإنجليزية', 'Arabic': 'اللغة العربية', 'Islamic Studies': 'التربية الإسلامية',
    'Physics': 'الفيزياء', 'Chemistry': 'الكيمياء', 'Biology': 'الأحياء', 'History': 'التاريخ', 'Geography': 'الجغرافيا', 'Computing': 'الحوسبة',
    'Art': 'الفنون', 'Quran': 'القرآن الكريم', 'Physical Education': 'التربية البدنية', 'Social Studies': 'الدراسات الاجتماعية'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
  AR['Open'] = 'مفتوحة';   // the status (open issues, open tickets); links that open something say "فتح" themselves
})();

/** Messages the server sends back (errors, refusals). The toast and flash boxes pass them through the same lookup. */
(function () {
  const more = {
    'A different person must approve this change (two-person approval).': 'يجب أن يوافق شخص آخر على هذا التغيير (موافقة شخصين).',
    'A different person must approve this. You cannot approve your own request.': 'يجب أن يوافق شخص آخر على هذا. لا يمكنك الموافقة على طلبك.',
    'A notice needs its Arabic title and text before it can be published.': 'يحتاج الإعلان إلى عنوان ونص بالعربية قبل نشره.',
    'A request for this student is already waiting for the coordinator.': 'يوجد طلب لهذا الطالب بانتظار المنسق بالفعل.',
    'A school with this domain already exists.': 'توجد مدرسة بهذا النطاق بالفعل.',
    'A second, different person must approve this restore.': 'يجب أن يوافق شخص ثانٍ مختلف على هذه الاستعادة.',
    'Assessments are not available for your role.': 'التقييمات غير متاحة لدورك.',
    'Assign a teacher to this subject for the section first.': 'أسند معلماً لهذه المادة في الشعبة أولاً.',
    'Attendance cannot be recorded for a future day.': 'لا يمكن تسجيل الحضور ليوم قادم.',
    'Attendance is recorded by the school\'s attendance officer. Teachers can only read it.': 'يسجّل الحضور كاتب الحضور في المدرسة. يمكن للمعلمين الاطلاع عليه فقط.',
    'Attendance summaries are not available for your role.': 'ملخصات الحضور غير متاحة لدورك.',
    'Backup status is not available for your role.': 'حالة النسخ الاحتياطي غير متاحة لدورك.',
    'Check runs are not available for your role.': 'عمليات الفحص غير متاحة لدورك.',
    'Choose Teacher, Coordinator, Principal, Attendance Clerk, Student or Parent.': 'اختر معلماً أو منسقاً أو مدير مدرسة أو كاتب حضور أو طالباً أو وليّ أمر.',
    'Choose a section.': 'اختر شعبة.', 'Choose at least one student.': 'اختر طالباً واحداً على الأقل.',
    'Choose how this person is related to your child.': 'اختر صلة هذا الشخص بطفلك.', 'Choose someone from the list.': 'اختر شخصاً من القائمة.',
    'Choose the student this parent follows.': 'اختر الطالب الذي يتابعه وليّ الأمر هذا.', 'Choose the student.': 'اختر الطالب.',
    'Choose yourself or someone on your approved list.': 'اختر نفسك أو شخصاً من قائمة المخوَّلين.',
    'Class points are for school staff.': 'نقاط الصف لموظفي المدرسة.', 'Class requests are for students and parents.': 'طلبات الصف للطلاب وأولياء الأمور.',
    'Enter a phone number using digits, spaces, + ( ) and - only.': 'أدخل رقم هاتف بالأرقام والمسافات و+ ( ) و- فقط.',
    'Enter the collector\'s ID number (at least 4 letters or digits). Only the last four are kept.': 'أدخل رقم هوية المستلم (4 أحرف أو أرقام على الأقل). تُحفظ آخر أربعة فقط.',
    'Enter the collector\'s full name.': 'أدخل الاسم الكامل للمستلم.', 'Every student must belong to your school.': 'يجب أن ينتمي كل طالب إلى مدرستك.',
    'Exceptions are not available for your role.': 'الاستثناءات غير متاحة لدورك.',
    'Give a short reason, so the parent knows what to do.': 'اكتب سبباً مختصراً ليعرف وليّ الأمر ما يجب فعله.',
    'Incidents are not available for your role.': 'الحوادث غير متاحة لدورك.',
    'Leadership and School IT accounts cannot be deleted here.': 'لا يمكن حذف حسابات القيادة وتقنية المعلومات بالمدرسة من هنا.',
    'Notices are shown to the school\'s own people.': 'تظهر الإعلانات لأفراد المدرسة فقط.',
    'Only Leadership or an EAA administrator can do this.': 'القيادة أو تقنية المعلومات في الهيئة فقط يمكنهم ذلك.',
    'Only School IT and Leadership can verify the log.': 'تقنية المعلومات بالمدرسة والقيادة فقط يمكنهم التحقق من السجل.',
    'Only School IT can unmask an export, and it needs a reason.': 'تقنية المعلومات بالمدرسة فقط يمكنها كشف بيانات التصدير، ويلزم ذكر سبب.',
    'Only School IT of this school can do this.': 'تقنية المعلومات في هذه المدرسة فقط يمكنها ذلك.',
    'Only School IT, Leadership and EAA administrators assign tickets.': 'تقنية المعلومات بالمدرسة والقيادة وتقنية المعلومات في الهيئة فقط يسندون التذاكر.',
    'Only a paused rule can be resumed.': 'لا يمكن استئناف إلا قاعدة متوقفة.',
    'Only a principal can write to Leadership. Write to your School IT instead.': 'مدير المدرسة فقط يمكنه مراسلة القيادة. راسل تقنية المعلومات في مدرستك بدلاً من ذلك.',
    'Only a student can be enrolled in a section.': 'لا يُسجَّل في الشعبة إلا الطلاب.',
    'Only a teacher or a coordinator can verify and release a child.': 'المعلم أو المنسق فقط يمكنه التحقق من المستلم وتسليم الطفل.',
    'Only an active rule can be paused.': 'لا يمكن إيقاف إلا قاعدة فعّالة.', 'Only network-wide rules need approval.': 'قواعد الشبكة فقط تحتاج إلى موافقة.',
    'Only teachers award points. The principal can view them.': 'المعلمون فقط يمنحون النقاط. يمكن لمدير المدرسة الاطلاع عليها.',
    'Only the file\'s owner or the subject coordinator can ask to delete a file.': 'مالك الملف أو منسق المادة فقط يمكنه طلب حذف الملف.',
    'Only the owner of the file can do this.': 'مالك الملف فقط يمكنه ذلك.', 'Only the person who asked can withdraw this.': 'صاحب الطلب فقط يمكنه سحبه.',
    'Only the subject coordinator can do this.': 'منسق المادة فقط يمكنه ذلك.', 'Only the subject coordinator can review this plan.': 'منسق المادة فقط يمكنه مراجعة هذه الخطة.',
    'Only the subject coordinator or School IT can end this exception.': 'منسق المادة أو تقنية المعلومات بالمدرسة فقط يمكنهما إنهاء هذا الاستثناء.',
    'Only the teacher of this subject for this section can create its assessments.': 'معلم هذه المادة في هذه الشعبة فقط يمكنه إنشاء تقييماتها.',
    'Only the teacher of this subject for this section can enter or submit marks.': 'معلم هذه المادة في هذه الشعبة فقط يمكنه إدخال الدرجات أو تسليمها.',
    'Only the teacher of this subject for this section can share materials.': 'معلم هذه المادة في هذه الشعبة فقط يمكنه مشاركة المواد.',
    'Only the teacher who shared it, or School IT, can remove a material.': 'المعلم الذي شاركها أو تقنية المعلومات بالمدرسة فقط يمكنه إزالة المادة.',
    'Planning files are not available for your role.': 'ملفات التخطيط غير متاحة لدورك.', 'Please choose a class.': 'يرجى اختيار صف.',
    'Please choose a start date and an end date after it.': 'يرجى اختيار تاريخ بداية وتاريخ نهاية بعده.', 'Please choose a teacher of your school.': 'يرجى اختيار معلم من مدرستك.',
    'Please choose an end date that is today or later.': 'يرجى اختيار تاريخ نهاية اليوم أو بعده.', 'Please choose one of your school\'s subjects.': 'يرجى اختيار مادة من مواد مدرستك.',
    'Please choose the document type and the sensitivity.': 'يرجى اختيار نوع المستند ودرجة الحساسية.',
    'Please fill in the grade, week, type and the drive link.': 'يرجى تعبئة الصف والأسبوع والنوع ورابط المساحة.',
    'Please give a grade from 1 to 12 and a section name.': 'يرجى إدخال صف من 1 إلى 12 واسم الشعبة.', 'Please give a reason for the exception.': 'يرجى ذكر سبب الاستثناء.',
    'Please give a short reason (up to 200 characters).': 'يرجى ذكر سبب مختصر (حتى 200 حرف).', 'Please give a valid e-mail address.': 'يرجى إدخال بريد إلكتروني صحيح.',
    'Please give the directory group and the role.': 'يرجى إدخال مجموعة الدليل والدور.', 'Please give the material a title.': 'يرجى إعطاء المادة عنواناً.',
    'Please give the person\'s name.': 'يرجى إدخال اسم الشخص.', 'Please give the school a name and its workspace domain.': 'يرجى إدخال اسم المدرسة ونطاق مساحة عملها.',
    'Please give the setting name and value.': 'يرجى إدخال اسم الإعداد وقيمته.', 'Please give the subject a name.': 'يرجى إعطاء المادة اسماً.',
    'Please give the type a name and a sensitivity.': 'يرجى إعطاء النوع اسماً ودرجة حساسية.', 'Please give your full name.': 'يرجى إدخال اسمك الكامل.',
    'Please keep the reason under 500 characters.': 'يرجى أن يكون السبب أقل من 500 حرف.', 'Please name the assessment.': 'يرجى تسمية التقييم.',
    'Please name the drive to restore.': 'يرجى تسمية المساحة المطلوب استعادتها.', 'Please name the term.': 'يرجى تسمية الفصل الدراسي.',
    'Please say what to restore and why.': 'يرجى ذكر ما تريد استعادته والسبب.', 'Please sign in with your school account.': 'يرجى تسجيل الدخول بحساب مدرستك.',
    'Please tell the teacher why the plan is returned.': 'يرجى إخبار المعلم بسبب إعادة الخطة.', 'Please tell us why this looks wrong.': 'يرجى إخبارنا لماذا يبدو هذا خطأ.',
    'Please write the reason for changing attendance after the day.': 'يرجى كتابة سبب تعديل الحضور بعد انقضاء اليوم.',
    'Please write the reason for unlocking the marks.': 'يرجى كتابة سبب فتح الدرجات.', 'Please write the reason for unpublishing the results.': 'يرجى كتابة سبب إلغاء نشر النتائج.',
    'Recognition analytics are for school leadership.': 'تحليلات التقدير لقيادة المدرسة.', 'Reminder days must be 1, 2, 3 or 5.': 'يجب أن تكون أيام التذكير 1 أو 2 أو 3 أو 5.',
    'Restore requests are not available for your role.': 'طلبات الاستعادة غير متاحة لدورك.', 'School days are Sunday to Thursday.': 'أيام الدراسة من الأحد إلى الخميس.',
    'Sections are not available for your role.': 'الشعب غير متاحة لدورك.', 'Students and parents cannot see the class list.': 'لا يمكن للطلاب وأولياء الأمور رؤية قائمة الصف.',
    'Study materials are not available for your role.': 'المواد الدراسية غير متاحة لدورك.', 'Teaching assignments are not available for your role.': 'إسنادات التدريس غير متاحة لدورك.',
    'Terms are not available for your role.': 'الفصول الدراسية غير متاحة لدورك.',
    'That e-mail already has an account. Sign in, or ask the school office.': 'لهذا البريد حساب بالفعل. سجّل الدخول أو راجع مكتب المدرسة.',
    'That person cannot sign in.': 'لا يمكن لهذا الشخص تسجيل الدخول.', 'That subject does not belong to this school.': 'هذه المادة لا تتبع هذه المدرسة.',
    'The activity log is not available for your role.': 'سجل النشاط غير متاح لدورك.', 'The attachment could not be read.': 'تعذّرت قراءة المرفق.',
    'The attachment is empty.': 'المرفق فارغ.', 'The attachment needs a file name.': 'يحتاج المرفق إلى اسم ملف.',
    'The connection is switched off. Reconnect it first.': 'الاتصال متوقف. أعد الاتصال أولاً.',
    'The connection test failed. Please check the connection settings.': 'فشل اختبار الاتصال. يرجى مراجعة إعدادات الاتصال.',
    'The end of a notice must be after its start.': 'يجب أن تكون نهاية الإعلان بعد بدايته.', 'The head office cannot be removed.': 'لا يمكن إزالة المكتب الرئيسي.',
    'The invitation is for a parent or a student.': 'الدعوة لوليّ أمر أو طالب.', 'The link must start with https://': 'يجب أن يبدأ الرابط بـ https://',
    'The maximum mark must be more than 0 and at most 1000.': 'يجب أن تكون الدرجة القصوى أكبر من 0 وألا تتجاوز 1000.',
    'The owner has not disagreed with this issue.': 'لم يعترض المالك على هذه المشكلة.', 'The password must be at least 8 characters.': 'يجب أن تتكون كلمة المرور من 8 أحرف على الأقل.',
    'The performance matrix is for school staff.': 'مصفوفة الأداء لموظفي المدرسة.', 'The period must be from 1 to 12.': 'يجب أن تكون الحصة من 1 إلى 12.',
    'The photo could not be read.': 'تعذّرت قراءة الصورة.', 'The photo must be a PNG or JPEG image.': 'يجب أن تكون الصورة بصيغة PNG أو JPEG.',
    'The pick-up board is for teachers, coordinators, the principal, School IT and the attendance clerk.': 'لوحة الاستلام للمعلمين والمنسقين ومدير المدرسة وتقنية المعلومات بالمدرسة وكاتب الحضور.',
    'The restore must be approved first.': 'يجب الموافقة على الاستعادة أولاً.',
    'The results of this term are published. Unpublish them first, then unlock the marks.': 'نتائج هذا الفصل منشورة. ألغِ نشرها أولاً ثم افتح الدرجات.',
    'The school score is not available for your role.': 'درجة المدرسة غير متاحة لدورك.', 'The school workspace is not connected yet.': 'مساحة عمل المدرسة غير متصلة بعد.',
    'The stage must be SIJIL or HIFZ.': 'يجب أن تكون المرحلة سجل أو حفظ.', 'The term must be 1, 2 or 3.': 'يجب أن يكون الفصل 1 أو 2 أو 3.',
    'The term must end after it starts.': 'يجب أن ينتهي الفصل بعد بدايته.', 'The ticket already has this status.': 'التذكرة في هذه الحالة بالفعل.',
    'The timetable is not available for your role.': 'الجدول غير متاح لدورك.', 'The weekly grade overview is for school leadership.': 'ملخص الدرجات الأسبوعية لقيادة المدرسة.',
    'The weekly report is available to principals and Leadership.': 'التقرير الأسبوعي متاح لمديري المدارس والقيادة.',
    'The welcome message was already sent.': 'أُرسلت رسالة الترحيب بالفعل.', 'There are no students in this section yet.': 'لا يوجد طلاب في هذه الشعبة بعد.',
    'These are a student\'s own points.': 'هذه نقاط الطالب نفسه.', 'These marks are already submitted.': 'سُلّمت هذه الدرجات بالفعل.',
    'These marks are not submitted, so there is nothing to unlock.': 'هذه الدرجات لم تُسلَّم، فلا يوجد ما يُفتح.',
    'These marks are submitted and locked. Ask School IT to unlock them to make a change.': 'هذه الدرجات مسلّمة ومقفلة. اطلب من تقنية المعلومات بالمدرسة فتحها لإجراء تغيير.',
    'These results are already published.': 'هذه النتائج منشورة بالفعل.', 'This belongs to another school.': 'هذا يتبع مدرسة أخرى.',
    'This exception has already ended.': 'انتهى هذا الاستثناء بالفعل.', 'This incident is already resolved.': 'تم حل هذه الحادثة بالفعل.',
    'This invitation was issued for a different e-mail address.': 'صدرت هذه الدعوة لبريد إلكتروني آخر.', 'This is already waiting for approval.': 'هذا بانتظار الموافقة بالفعل.',
    'This is for parents.': 'هذا لأولياء الأمور.', 'This is the student\'s home.': 'هذه صفحة الطالب.', 'This is the student\'s own attendance.': 'هذا حضور الطالب نفسه.',
    'This is the teacher\'s home.': 'هذه صفحة المعلم.', 'This issue is already closed.': 'هذه المشكلة مغلقة بالفعل.',
    'This kind of attachment is not accepted. Use PDF, PNG, JPG, DOCX, XLSX, TXT or CSV.': 'هذا النوع من المرفقات غير مقبول. استخدم PDF أو PNG أو JPG أو DOCX أو XLSX أو TXT أو CSV.',
    'This parent is no longer linked to the child. Decline the request.': 'لم يعد وليّ الأمر هذا مرتبطاً بالطفل. ارفض الطلب.',
    'This person is no longer on the approved list. Decline the request.': 'لم يعد هذا الشخص في قائمة المخوَّلين. ارفض الطلب.',
    'This plan is not waiting for review.': 'هذه الخطة ليست بانتظار المراجعة.', 'This plan was already submitted.': 'سُلّمت هذه الخطة بالفعل.',
    'This request is already being checked or is finished.': 'هذا الطلب قيد التحقق أو انتهى بالفعل.', 'This request is already finished.': 'انتهى هذا الطلب بالفعل.',
    'This request is no longer waiting for approval.': 'لم يعد هذا الطلب بانتظار الموافقة.', 'This request was already decided.': 'تم البت في هذا الطلب بالفعل.',
    'This rule has been deleted.': 'حُذفت هذه القاعدة.', 'This rule has no approved version yet.': 'لا توجد نسخة معتمدة من هذه القاعدة بعد.',
    'This student is already in a class.': 'هذا الطالب في صف بالفعل.', 'This version is already approved.': 'هذه النسخة معتمدة بالفعل.',
    'Too many tries. Please wait a few minutes and try again.': 'محاولات كثيرة. يرجى الانتظار بضع دقائق ثم المحاولة مجدداً.',
    'Too many wrong passwords. Please try again in 15 minutes.': 'كلمات مرور خاطئة كثيرة. يرجى المحاولة بعد 15 دقيقة.',
    'Unknown action.': 'إجراء غير معروف.', 'Unknown kind of alert.': 'نوع تنبيه غير معروف.', 'Verify the collector\'s ID first.': 'تحقق من هوية المستلم أولاً.',
    'You can award points only to students you teach.': 'يمكنك منح النقاط لطلابك فقط.', 'You cannot delete your own account.': 'لا يمكنك حذف حسابك.',
    'You cannot reply to this ticket.': 'لا يمكنك الرد على هذه التذكرة.',
    'You do not need to add yourself. Choose yourself when you arrive.': 'لا داعي لإضافة نفسك. اختر نفسك عند وصولك.',
    'You do not teach this section.': 'أنت لا تدرّس هذه الشعبة.', 'You do not teach this subject to this section.': 'أنت لا تدرّس هذه المادة لهذه الشعبة.',
    'Your child is not in a class yet. Ask the school office.': 'طفلك ليس في صف بعد. راجع مكتب المدرسة.',
    'Your name must be between 2 and 200 characters.': 'يجب أن يكون اسمك بين 2 و200 حرف.', 'Your role cannot approve this.': 'لا يمكن لدورك الموافقة على هذا.',
    'Your role cannot open a support ticket.': 'لا يمكن لدورك فتح تذكرة دعم.', 'Your role does not use support tickets.': 'دورك لا يستخدم تذاكر الدعم.',
    'Your role is not allowed to do this.': 'دورك لا يسمح بهذا.', 'Your role is not allowed to resolve incidents.': 'دورك لا يسمح بحل الحوادث.',
    'Your role sees counts only, not individual files.': 'يرى دورك الأعداد فقط، لا الملفات منفردة.',
    'Your role sees counts only, not individual issues.': 'يرى دورك الأعداد فقط، لا المشكلات منفردة.',
    'Your section': 'شعبتك', 'Your section (you are not enrolled in a section yet)': 'شعبتك (لم تُسجَّل في شعبة بعد)',
    // "<thing> was not found."
    'A person with this e-mail in your school': 'شخص بهذا البريد في مدرستك', 'The assessment': 'التقييم', 'The attachment': 'المرفق', 'The backup run': 'عملية النسخ الاحتياطي',
    'The child': 'الطفل', 'The class': 'الصف', 'The collector': 'المستلم', 'The document type': 'نوع المستند', 'The exception': 'الاستثناء', 'The incident': 'الحادثة',
    'The invitation': 'الدعوة', 'The issue': 'المشكلة', 'The lesson': 'الحصة', 'The material': 'المادة الدراسية', 'The notice': 'الإعلان', 'The notification': 'الإشعار',
    'The person': 'الشخص', 'The plan': 'الخطة', 'The published results': 'النتائج المنشورة', 'The request': 'الطلب', 'The restore request': 'طلب الاستعادة',
    'The rule': 'القاعدة', 'The rule version': 'نسخة القاعدة', 'The school': 'المدرسة', 'The section': 'الشعبة', 'The student': 'الطالب', 'The subject': 'المادة',
    'The ticket': 'التذكرة', 'Your account': 'حسابك'
  };
  for (const k of Object.keys(more)) if (!(k in AR)) AR[k] = more[k];
})();

/** Bell titles the server writes. New entries carry both languages; older ones carry only the person's chosen language,
 *  so these pairs translate them both ways (Arabic screen: English → Arabic; English screen: Arabic → English). */
const NOTE_PAIRS = [
  ['Your results are published', 'نتائجك منشورة الآن'], ['Weekly grades are due', 'موعد تسليم الدرجات الأسبوعية'],
  ['Your request was approved', 'تمت الموافقة على طلبك'], ['Your request was rejected', 'تم رفض طلبك'],
  ['The owner thinks this looks wrong', 'المالك يرى أن الملاحظة غير صحيحة'], ['The owner asked for help', 'المالك يطلب المساعدة'],
  ['The coordinator kept the rule for your file; please review the steps', 'أبقى المنسق القاعدة على ملفك؛ يرجى مراجعة الخطوات'],
  ['A file needs your review', 'ملف بحاجة إلى مراجعتك'], ['A file needs your attention', 'ملف يحتاج إلى انتباهك'],
  ['Urgent: a file needs your attention', 'عاجل: ملف يحتاج إلى انتباهك'], ['Urgent: a file needs follow-up', 'عاجل: ملف يحتاج إلى متابعة'],
  ['Urgent alert in your school', 'تنبيه عاجل في مدرستك'], ['A new file issue in your school', 'ملاحظة جديدة على ملف في مدرستك'],
  ['Alert: a Restricted file with student or health data is exposed', 'تنبيه: ملف مقيّد يحتوي بيانات طلاب أو صحة مكشوف'],
  ['Urgent: a file needs action', 'تنبيه عاجل: ملف يحتاج إلى معالجة'], ['Reminder: a file still needs your attention', 'تذكير: ملف ما زال يحتاج إلى انتباهك'],
  ['Needs follow-up', 'يحتاج إلى متابعة'], ['The file still needs a fix', 'ما زال الملف بحاجة إلى تصحيح'],
  ['A request needs your approval', 'طلب بانتظار موافقتك'], ['Your account was locked', 'تم قفل حسابك مؤقتاً'], ['Your role was changed', 'تم تغيير دورك'],
  ['Welcome to Hafez', 'مرحبًا بك في حافظ'], ['A new plan is waiting for your review', 'خطة جديدة بانتظار مراجعتك'],
  ['Your plan was approved', 'تمت الموافقة على خطتك'], ['Your plan was returned', 'أُعيدت خطتك للتعديل'],
  ['Reminder: next week\'s plan is not submitted yet', 'تذكير: لم تُرسل خطة الأسبوع القادم'], ['New reply on your ticket', 'رد جديد على تذكرتك'],
  ['A support ticket was assigned to you', 'تذكرة دعم أُسندت إليك'], ['Your ticket is being handled', 'تذكرتك قيد المعالجة'],
  ['Your ticket changed status', 'تغيّرت حالة تذكرتك'],
  ['Major incident: an exam was shared before its date', 'حادثة مهمة: تمت مشاركة اختبار قبل موعده'],
  ['Major incident: an important file was deleted', 'حادثة مهمة: تم حذف ملف مهم'],
  ['Major incident: many files were deleted in a short time', 'حادثة مهمة: حُذفت ملفات كثيرة في وقت قصير'],
  ['Major incident: an urgent issue is still open', 'حادثة مهمة: ما زالت مشكلة عاجلة مفتوحة'],
  ['Major incident: the connection to the workspace is lost', 'حادثة مهمة: انقطع الاتصال بمساحة العمل']
];
/** Titles with a name or date in them ("%" is that part, kept as it is). */
const NOTE_FORMS = [['New notice: %', 'إشعار جديد: %'], ['A parent has arrived for %', 'وصل ولي أمر الطالب %'], ['New support ticket from %', 'تذكرة دعم جديدة من %'],
  ['New reply from %', 'رد جديد من %'], ['An exception was allowed for your file until %', 'تم السماح باستثناء لملفك حتى %'],
  ['% was not released', 'لم يتم تسليم %'], ['% was released', '% تم تسليمه']];
for (const [en, ar] of NOTE_PAIRS) if (!(en in AR)) AR[en] = ar;
function formPart(tpl, s) { const [a, b] = tpl.split('%'); return s.length > a.length + b.length && s.startsWith(a) && s.endsWith(b) ? s.slice(a.length, s.length - b.length) : null; }
/** An older Arabic-only bell title in English, or null when it is not one the server writes. */
function noteToEnglish(s) {
  s = String(s || '').trim().replace(new RegExp(BRAND.ar, 'g'), 'حافظ');
  const pair = NOTE_PAIRS.find(p => p[1] === s); if (pair) return brandify(pair[0]);
  for (const [en, ar] of NOTE_FORMS) { const x = formPart(ar, s); if (x != null) return en.replace('%', x); }
  return null;
}

const DAY_AR = { Sunday: 'الأحد', Monday: 'الاثنين', Tuesday: 'الثلاثاء', Wednesday: 'الأربعاء', Thursday: 'الخميس' };
const ROLE_AR = { TEACHER: 'معلم', COORDINATOR: 'منسق', PRINCIPAL: 'مدير المدرسة', SCHOOL_IT: 'تقنية المعلومات بالمدرسة', LEADERSHIP: 'القيادة', EAA_ADMIN: 'تقنية المعلومات في الهيئة', ATTENDANCE_OFFICER: 'كاتب الحضور', STUDENT: 'طالب', PARENT: 'وليّ أمر' };

const AR_RULES = [
  [/^(.+) was not found\.$/, w => { const t = lookup(w); return t == null ? null : `لم يُعثر على ${t}.`; }],
  [/^(\d+)% of (\d+) issues fixed$/, (a, b) => `تم إصلاح ${a}% من ${b} مشكلة`],
  [/^average ([\d.]+) per student$/, n => `بمتوسط ${n} لكل طالب`],
  [/^issues went from (\d+) to (\d+) over this period\.$/, (a, b) => `انتقل عدد المشكلات من ${a} إلى ${b} خلال هذه الفترة.`],
  [/^Health score trend, (.+) to (.+)$/, (a, b) => `اتجاه درجة السلامة، من ${a} إلى ${b}`],
  [/^Delete rule (\S+)\? Its open issues will be closed\. Pausing is usually better\.$/, c => `حذف القاعدة ${c}؟ ستُغلق مشكلاتها المفتوحة. الإيقاف المؤقت أفضل عادةً.`],
  [/^(\d+) files were deleted within an hour\.$/, n => `حُذف ${n} ملفاً خلال ساعة.`],
  // composed server messages (marks, timetable, points, pick-up)
  [/^(\d+) student\(s\) have no mark yet\. Enter a mark or Absent for everyone, then submit\.$/, n => `${n} من الطلاب بلا درجة بعد. أدخل درجة أو «غائب» للجميع ثم سلّم.`],
  [/^Marks are not submitted yet for: (.+)\.$/, l => `لم تُسلَّم الدرجات بعد لـ: ${l}.`],
  [/^There are no assessments for term (\d+) of section (.+)\.$/, (t, s) => `لا توجد تقييمات للفصل ${t} في الشعبة ${s}.`],
  [/^Term (\d+) has not been created yet\. Ask the coordinator to create it\.$/, t => `الفصل ${t} لم يُنشأ بعد. اطلب من المنسق إنشاءه.`],
  [/^Section (.+) already exists\.$/, s => `الشعبة ${s} موجودة بالفعل.`],
  [/^Section (.+) already has (.+) in period (\d+) on (\w+)\.$/, (s, sub, p, d) => `للشعبة ${s} حصة ${trCore(sub)} في الحصة ${p} يوم ${DAY_AR[d] || d}.`],
  [/^(.+) already teaches another section in period (\d+) on (\w+)\.$/, (t, p, d) => `${t} يدرّس شعبة أخرى في الحصة ${p} يوم ${DAY_AR[d] || d}.`],
  [/^Room (.+) is already booked in period (\d+) on (\w+)\.$/, (r, p, d) => `القاعة ${r} محجوزة في الحصة ${p} يوم ${DAY_AR[d] || d}.`],
  [/^Some of these students are not in section (.+)\.$/, s => `بعض هؤلاء الطلاب ليسوا في الشعبة ${s}.`],
  [/^Points must be between 1 and (\d+)\.$/, n => `يجب أن تكون النقاط بين 1 و${n}.`],
  [/^You can approve at most (\d+) collectors for one child\. Remove one first\.$/, n => `يمكنك اعتماد ${n} مستلمين على الأكثر لكل طفل. أزل واحداً أولاً.`],
  [/^This is too long \(at most (\d+) characters\)\.$/, n => `هذا طويل جداً (${n} حرفاً على الأكثر).`],
  [/^Nothing was saved\. (.+)\.$/, x => `لم يُحفظ شيء. ${x}.`],
  [/^open → submitted: (.+)$/, n => `مفتوح → مُرسل: ${n}`],
  [/^Room (.+)$/, n => 'القاعة ' + n],
  [/^(?:(.+): )?Delete the file "(.+)" from the register(?: — reason: ([\s\S]*))?$/, (who, f, why) => (who ? who + ': ' : '') + `حذف الملف «${f}» من السجل` + (why ? ' — السبب: ' + why : '')],
  [/^([A-Z_]+) → ([A-Z_]+)$/, (a, b) => { const x = lookup(a), y = lookup(b); return x == null && y == null ? null : `${x ?? a} → ${y ?? b}`; }],
  [/^Collector: (.+) \(([a-z]+)\)\. Open the Pick-up board to verify and release\.$/, (n, r) => `المستلم: ${n} (${lookup(r[0].toUpperCase() + r.slice(1)) || r}). افتح لوحة الاستلام للتحقق والتسليم.`],
  // a list of known words ("English, Mathematics, Science"): translated only when every item is known
  [/^([^,]+(?:, [^,]+)+)$/, l => { const parts = l.split(', ').map(lookup); return parts.includes(null) ? null : parts.join('، '); }],
  [/^out of ([\d.]+)$/, n => 'من ' + n],
  [/^Week of (.+)$/, d => 'أسبوع ' + d],
  [/^added (Sunday|Monday|Tuesday|Wednesday|Thursday) period (\d+)$/, (d, n) => `أُضيفت حصة ${DAY_AR[d]} رقم ${n}`],
  [/^attendance (\d{4}-\d\d-\d\d)$/, d => 'الحضور ' + d],
  [/^(\S+@\S+) as ([A-Z_]+)$/, (e, r) => ROLE_AR[r] ? `${e} بدور ${ROLE_AR[r]}` : null],
  [/^(R\d+) ([A-Z_]+)$/, (c, s) => { const t = lookup(s); return t == null ? null : `${c} ${t}`; }],
  [/^(.+) \(([A-Za-z][A-Za-z -]*)\)$/, (w, x) => { const t = lookup(x); return t == null ? null : `${w} (${t})`; }],
  [/^(\d+) of (\d+) days$/, (a, b) => `${a} من ${b} أيام`],
  [/^Following (.+)$/, n => 'تتابع ' + n],
  [/^Welcome, (.+)$/, n => 'مرحباً، ' + n],
  [/^Parent of (.+)$/, n => 'وليّ أمر ' + n],
  [/^Section (.+)$/, n => 'الشعبة ' + n],
  [/^Student ID (.+)$/, n => 'رقم الطالب ' + n],
  [/^Request failed \((\d+)\)$/, n => `تعذّر تنفيذ الطلب (${n})`],
  [/^Rule (\S+): (.+)$/, (c, n) => `القاعدة ${c}: ${trCore(n)}`],
  [/^Edit rule (.+)$/, c => 'تعديل القاعدة ' + c],
  [/^Delete rule (.+)\?$/, c => `حذف القاعدة ${c}؟`],
  [/^New notice: (.+)$/, n => 'إعلان جديد: ' + n],
  [/^Marked (.+)$/, s => 'سُجّل: ' + trCore(s)],
  [/^Study material: (.+)$/, s => 'مادة دراسية: ' + s],
  [/^Results · term (\d+)$/, n => 'النتائج · الفصل ' + n],
  [/^Term (\d+)$/, n => 'الفصل ' + n],
  [/^term (\d+)$/, n => 'الفصل ' + n],
  [/^Of the recent (.+)$/, n => 'من آخر ' + n],
  [/^Review rules \((\d+)\)$/, n => `مراجعة القواعد (${n})`],
  [/^Review queue → \((\d+)\)$/, n => `قائمة المراجعة → (${n})`],
  [/^First check (.+)$/, d => 'الفحص الأول ' + d],
  [/^Not fixed yet: (.+)$/, s => 'لم يُصلح بعد: ' + trCore(s)],
  [/^Still broken: (.*)$/, s => 'ما زالت المشكلة قائمة: ' + s],
  [/^Done: (.+)$/, s => 'تم: ' + s],
  [/^(.+) can now sign in\.$/, e => e + ' يمكنه الآن تسجيل الدخول.'],
  [/^(\d+) people changed\.$/, n => `تغيّرت بيانات ${n} شخص.`],
  [/^Attendance saved \((.+)\)\.?$/, s => `تم حفظ الحضور (${trCore(s)})`],
  [/^Opened (.+)$/, d => 'فُتحت ' + d],
  [/^Shared (.+)$/, d => 'شوركت ' + d],
  [/^(\d+) new$/, n => `${n} جديدة`],
  [/^(Good morning|Good afternoon|Good evening), (.+)$/, (g, n) => AR[g] + '، ' + n],
  [/^, (.+)$/, n => '، ' + n],
  [/^(.+) \((\d+)\)$/, (w, n) => { const t = lookup(w); return t == null ? null : `${t} (${n})`; }],
  [/^last (\d+) days$/, n => `آخر ${n} يوماً`],
  [/^([a-z]+) ([\d.]+)$/, (w, n) => { const t = lookup(w); return t == null ? null : `${t} ${n}`; }],
  [/^(\d+) (.+?)$/, (n, rest) => { const t = lookup(rest) ?? lookup(rest.replace(/s$/, '')); return t == null ? null : `${n} ${t}`; }],
];
for (const [en, ar] of NOTE_FORMS) AR_RULES.push([{ [Symbol.match]: s => { const x = formPart(en, s); return x == null ? null : [s, x]; } }, x => ar.replace('%', x)]);

function lookup(c) {
  if (Object.prototype.hasOwnProperty.call(AR, c)) return AR[c];
  const lead = c.match(/^([·•]\s*)(.+)$/);
  if (lead && Object.prototype.hasOwnProperty.call(AR, lead[2])) return lead[1] + AR[lead[2]];
  const tail = c.match(/^(.+?)\s*([:,.·+]|→)$/);
  if (tail && Object.prototype.hasOwnProperty.call(AR, tail[1])) return AR[tail[1]] + ({ '→': ' →', ',': '،', '·': ' ·', '+': ' +' }[tail[2]] || tail[2]);
  return null;
}

function trCore(c) {
  let r = lookup(c);
  // a leading marker ("▲ 1 urgent", "· deleted") stays and the rest is translated
  if (r == null) { const mk = c.match(/^([▲◆✓!·•]\s*)(.+)$/); if (mk) { const rest = trCore(mk[2]); if (rest !== mk[2]) r = mk[1] + rest; } }
  // a line made of parts ("Section 3C · date · Student ID X") is translated part by part, so no rule swallows it whole
  if (r == null && c.includes(' · ')) { const joined = c.split(' · ').map(p => trCore(p.trim())).join(' · '); if (joined !== c) r = joined; }
  if (r == null) for (const [re, f] of AR_RULES) { const m = c.match(re); if (m) { const x = f(...m.slice(1)); if (x != null) { r = x; break; } } }
  return (r == null ? c : r).replace(/→/g, '←');
}

/** Translates one piece of interface text, keeping the whitespace around it. English mode returns it unchanged. */
const BRAND = { en: 'Hafez', ar: 'حافظ' };   // replaced at start-up by /api/auth/brand (sijill.brand in application.yml)
function brandify(s) { return (BRAND.en === 'Hafez' && BRAND.ar === 'حافظ') || s == null ? s : String(s).replace(/Hafez/g, BRAND.en).replace(/حافظ/g, BRAND.ar); }
function trText(s) {
  const r = brandify(trTextRaw(s));
  // Arabic text from the server (rule steps, messages) can name the app in Latin letters: show the Arabic name
  return I18N.lang === 'ar' && r != null && /[؀-ۿ]/.test(r) ? String(r).replace(/Hafez/g, BRAND.ar) : r;
}
function trTextRaw(s) {
  if (I18N.lang !== 'ar' || s == null) return s;
  s = String(s);
  const m = s.match(/^(\s*)([\s\S]*?)(\s*)$/);
  if (m[2] === ',') return m[1] + '،' + m[3];
  if (!m[2] || !/[A-Za-z→]/.test(m[2])) return s;
  return m[1] + trCore(m[2].replace(/\s+/g, ' ')) + m[3];
}

// Homework, quizzes (Assessments), chat with the teacher, class notices, points progression, school names
Object.assign(AR, {
  '1st Assalam School': 'مدرسة السلم الأولى', '2nd Assalam School': 'مدرسة السلم الثانية', '3rd Assalam School': 'مدرسة السلم الثالثة',
  '4th Assalam School': 'مدرسة السلم الرابعة', '5th Assalam School': 'مدرسة السلم الخامسة',
  'Homework': 'الواجبات', 'Set homework': 'إضافة واجب', 'Daily schedule': 'الجدول اليومي', 'Sunday to Thursday': 'من الأحد إلى الخميس',
  'Due on': 'تاريخ التسليم', 'Details (optional)': 'التفاصيل (اختياري)', 'Class and subject': 'الصف والمادة', 'No homework due': 'لا يوجد واجب',
  'Week of': 'أسبوع', 'piece of homework this week': 'واجب هذا الأسبوع', 'pieces of homework this week': 'واجبات هذا الأسبوع', 'Today': 'اليوم',
  'Remove this homework?': 'حذف هذا الواجب؟', 'Homework removed.': 'تم حذف الواجب.', 'Homework set. The class and their parents can see it.': 'تمت إضافة الواجب. يراه الصف وأولياء أمورهم.',
  'The class and their parents see it on their daily schedule, under the day it is due.': 'يراه الصف وأولياء أمورهم في جدولهم اليومي تحت يوم التسليم.',
  'What you have set, day by day, by the date it is due.': 'ما أضفته يوماً بيوم حسب تاريخ التسليم.',
  "Your children's homework for every subject, day by day.": 'واجبات أبنائك في كل المواد يوماً بيوم.',
  'Your homework for every subject, day by day, by the date it is due.': 'واجباتك في كل المواد يوماً بيوم حسب تاريخ التسليم.',
  'Assessments': 'التقييمات', 'New quiz': 'اختبار جديد', 'Your quizzes': 'اختباراتك', 'Quiz': 'الاختبار', 'Questions': 'الأسئلة', 'Taken by': 'أدّاه',
  'Question': 'السؤال', 'question': 'سؤال', 'questions': 'أسئلة', 'Answer': 'إجابة', 'Answer (optional)': 'إجابة (اختياري)', '+ Add question': '+ إضافة سؤال', 'Publish quiz': 'نشر الاختبار',
  'Source material': 'المادة المرجعية', 'Link to the material (optional)': 'رابط المادة (اختياري)', 'Explanation of the correct answer': 'شرح الإجابة الصحيحة',
  'Tick the circle next to the correct answer.': 'اختر الدائرة بجانب الإجابة الصحيحة.', 'Explanation': 'الشرح', 'Correct answer': 'الإجابة الصحيحة', 'Your answer': 'إجابتك',
  '✓ Correct': '✓ صحيح', '✗ Not quite': '✗ غير صحيح', 'No answer': 'بدون إجابة', 'Your grade': 'درجتك', 'Answers and explanations': 'الإجابات والشرح',
  'Excellent': 'ممتاز', 'Very good': 'جيد جداً', 'Good': 'جيد', 'Pass': 'ناجح', 'Keep practising': 'واصل التدريب',
  'Your answers': 'إجاباتك', 'Send my answers': 'إرسال إجاباتي', 'You can send it once. Your grade appears straight away.': 'يمكنك الإرسال مرة واحدة. تظهر درجتك فوراً.',
  'Read the explanations below to see why each answer is right.': 'اقرأ الشرح أدناه لتعرف سبب صحة كل إجابة.',
  'To do': 'المطلوب', 'Completed': 'المكتملة', 'Waiting for you': 'بانتظارك', 'Average score': 'متوسط الدرجات', 'Start quiz →': 'ابدأ الاختبار ←',
  'No quiz is waiting for you.': 'لا يوجد اختبار بانتظارك.', 'Quizzes you finish appear here with your grade.': 'تظهر هنا الاختبارات التي تنهيها مع درجتك.',
  'Quizzes from your teachers. You get your grade and the explanations as soon as you send your answers.': 'اختبارات من معلميك. تحصل على درجتك والشرح فور إرسال إجاباتك.',
  'Build a quiz from your material. Students get their grade and the explanations as soon as they send it.': 'أنشئ اختباراً من مادتك. يحصل الطلاب على درجاتهم والشرح فور الإرسال.',
  'Write each question, its answers, tick the correct one and explain why.': 'اكتب كل سؤال وإجاباته، واختر الصحيحة واشرح السبب.',
  'Questions and answers': 'الأسئلة والإجابات', 'The correct answer is marked in green.': 'الإجابة الصحيحة مظللة بالأخضر.', 'Nobody has taken it yet.': 'لم يؤدّه أحد بعد.',
  'Results': 'النتائج', 'Sent': 'أُرسل', 'Percent': 'النسبة', 'Score': 'الدرجة', 'Quiz published to the class.': 'تم نشر الاختبار للصف.', 'Quiz removed.': 'تم حذف الاختبار.',
  'Remove this quiz and its results?': 'حذف هذا الاختبار ونتائجه؟',
  'Chat with teachers': 'محادثة المعلمين', 'Messages': 'الرسائل', 'Send': 'إرسال', 'Find a person': 'ابحث عن شخص', 'Nobody yet.': 'لا أحد بعد.',
  'Choose someone to write to': 'اختر من تريد مراسلته', 'Nobody to write to yet': 'لا يوجد من تراسله بعد',
  'Write a message. Enter sends, Shift+Enter starts a new line.': 'اكتب رسالة. Enter للإرسال وShift+Enter لسطر جديد.',
  'Write to the students you teach and to their parents.': 'راسل الطلاب الذين تدرّسهم وأولياء أمورهم.', "Write to your children's teachers.": 'راسل معلمي أبنائك.',
  'Ask your teachers a question.': 'اطرح سؤالاً على معلميك.', 'Your students and their parents are listed on the left.': 'يظهر طلابك وأولياء أمورهم في القائمة.',
  'Your teachers are listed with the subjects they teach.': 'يظهر معلموك مع المواد التي يدرّسونها.', 'Your teachers appear here once you are in a class.': 'يظهر معلموك هنا بعد تسجيلك في صف.',
  'Class notices': 'إشعارات الصف', 'New class notice': 'إشعار صف جديد', 'Your class notices': 'إشعارات صفوفك', 'Class': 'الصف', 'Message': 'الرسالة', 'Show until': 'يظهر حتى',
  'Arabic version (optional)': 'النسخة العربية (اختياري)', 'Publish to the class': 'نشر للصف', 'Showing': 'معروض', 'Ended': 'منتهٍ', 'Notice removed.': 'تم حذف الإشعار.',
  'A notice for one of your classes appears at the top of the home page of its students and their parents, beside the exam banner.': 'يظهر إشعار الصف أعلى الصفحة الرئيسية لطلابه وأولياء أمورهم بجانب لافتة الاختبارات.',
  "Notice published. It shows at the top of the class's and their parents' home page.": 'تم نشر الإشعار. يظهر أعلى الصفحة الرئيسية للصف وأولياء أمورهم.',
  'What your school and your teachers want you to know.': 'ما تريد مدرستك ومعلموك أن تعرفه.', 'You are not assigned to a class yet.': 'لم يتم تعيينك لصف بعد.',
  'points': 'نقاط', 'Top level reached!': 'وصلت إلى أعلى مستوى!', 'Starter': 'مبتدئ', 'Bronze': 'برونزي', 'Silver': 'فضي', 'Gold': 'ذهبي', 'Platinum': 'بلاتيني', 'Diamond': 'ماسي',
  'Each day you are present earns a point; teachers add more for great work.': 'كل يوم حضور يمنحك نقطة، ويضيف المعلمون المزيد للعمل المتميز.',
  'You have reached the highest level. Keep it up!': 'وصلت إلى أعلى مستوى. استمر!',
  'Test sign-in': 'الدخول التجريبي', 'Or type a name': 'أو اكتب اسماً', 'For testing only. Press a person to sign in with their role. There is no password.': 'للتجربة فقط. اضغط على شخص للدخول بدوره. لا توجد كلمة مرور.',
  'Chat messages': 'رسائل المحادثة', 'Homework, quizzes and class notices': 'الواجبات والاختبارات وإشعارات الصف'
,
  'Until': 'حتى', 'Class notice': 'إشعار الصف', 'this week': 'هذا الأسبوع', 'Set on': 'أُضيف في'
});
Object.assign(AR, { 'EAA IT': 'تقنية المعلومات في الهيئة', 'Head office IT team': 'فريق تقنية المعلومات في المكتب الرئيسي', 'Waiting for the first check': 'بانتظار الفحص الأول',
  'Your requests to EAA IT, and the tickets principals wrote to head office (read-only).': 'طلباتك إلى تقنية المعلومات في الهيئة، والتذاكر التي كتبها المديرون إلى المكتب الرئيسي (للاطلاع فقط).' });
Object.assign(AR, { 'Ticket sent to EAA IT.': 'أُرسلت التذكرة إلى تقنية المعلومات في الهيئة.', 'Leadership has read-only access. Ask EAA IT for any change. Replies and status changes appear here and in your notifications.': 'القيادة لديها صلاحية الاطلاع فقط. اطلب أي تغيير من تقنية المعلومات في الهيئة. تظهر الردود وتغييرات الحالة هنا وفي إشعاراتك.' });
Object.assign(AR, {
  'Level': 'المستوى', 'Level up!': 'مستوى جديد!', 'Homework done': 'إنجاز واجب', 'Quiz taken': 'أداء اختبار قصير', 'Exam result': 'نتيجة اختبار',
  '+2 to +10': '+2 إلى +10', 'up to +10': 'حتى +10', 'done': 'أنجزوا', '✓ Done': '✓ تم', 'Mark as done · +5 points': 'تم الإنجاز · +5 نقاط',
  'Earn points by finishing your homework, taking quizzes and doing well in exams.': 'اكسب النقاط بإنجاز واجباتك وأداء الاختبارات القصيرة والتفوق في الاختبارات.',
  'Well done! +5 points.': 'أحسنت! +5 نقاط.', 'Marked as not done. Its points were taken back.': 'أُلغي الإنجاز وسُحبت نقاطه.',
  'Students earn points for homework they finish, quizzes they take and their published exam results. Add recognition with a short reason.': 'يكسب الطلاب النقاط بإنجاز الواجبات وأداء الاختبارات القصيرة ونتائج الاختبارات المنشورة. أضف تقديراً مع سبب مختصر.',
  'Record today. Everyone starts as present; change only the absences and leave.': 'سجّل اليوم. يبدأ الجميع حاضرين؛ غيّر الغياب والإجازات فقط.'
});

// School metrics, achievements, teacher performance, trends, sidebar groups, today's tasks
Object.assign(AR, {
  'Teaching': 'التدريس', 'Students and families': 'الطلاب والأسر', 'My files': 'ملفاتي', 'School': 'المدرسة', 'Academics': 'الشؤون الأكاديمية', 'Staff': 'الكادر', 'Files': 'الملفات', 'Requests': 'الطلبات',
  'Weekly plans': 'الخطط الأسبوعية', 'Weekly plan review': 'مراجعة الخطط الأسبوعية', 'School metrics': 'مؤشرات المدرسة', 'Achievements': 'الإنجازات', 'Teacher performance': 'أداء المعلمين',
  'Investigations': 'التحقيقات', 'Schools & achievements': 'المدارس والإنجازات', 'Schools and achievements': 'المدارس والإنجازات',
  'Attendance': 'الحضور', 'Average grade': 'متوسط الدرجات', 'Homework done': 'الواجبات المنجزة', 'Quiz average': 'متوسط الاختبارات القصيرة', 'File health': 'سلامة الملفات',
  'Students present, last 30 days': 'الطلاب الحاضرون، آخر 30 يوماً', 'All published marks': 'كل الدرجات المنشورة', 'Homework marked done, last 30 days': 'الواجبات المنجزة، آخر 30 يوماً',
  'Average quiz score': 'متوسط درجات الاختبارات القصيرة', "Teachers' weekly plans submitted, last 4 weeks": 'الخطط الأسبوعية المسلّمة، آخر 4 أسابيع', 'Files kept safely, out of 100': 'حفظ الملفات بأمان، من 100',
  'Strong': 'قوي', 'Watch': 'يحتاج متابعة', 'Needs attention': 'يحتاج اهتماماً', 'No data yet': 'لا توجد بيانات بعد', 'Overall': 'الإجمالي', 'Report': 'التقرير', 'Open report': 'فتح التقرير',
  'Outstanding schools': 'المدارس المتميزة', 'Ranked by the average of their key percentages.': 'مرتبة حسب متوسط نسبها الرئيسية.', 'All schools': 'كل المدارس',
  'Key percentages by school': 'النسب الرئيسية لكل مدرسة', 'Green 85% and above, amber 70–84%, red below 70%. Open a school for its full report.': 'الأخضر 85% فأكثر، والبرتقالي 70–84%، والأحمر أقل من 70%. افتح مدرسة لتقريرها الكامل.',
  'Achievements across the network': 'الإنجازات في الشبكة', 'Latest achievements': 'أحدث الإنجازات', 'No achievements recorded yet.': 'لم تُسجّل إنجازات بعد.',
  "Every school's key percentages, achievements and a report for each.": 'النسب الرئيسية لكل مدرسة وإنجازاتها وتقرير لكل منها.', 'Average across schools': 'المتوسط بين المدارس', 'Published marks': 'الدرجات المنشورة',
  'Last 30 days': 'آخر 30 يوماً', 'Submitted, last 4 weeks': 'المسلّمة، آخر 4 أسابيع', 'Month by month': 'شهراً بشهر', 'The last twelve months. Hover a point for its value.': 'آخر اثني عشر شهراً. مرّر المؤشر على نقطة لمعرفة قيمتها.',
  'Outstanding students': 'الطلاب المتميزون', 'Most points from homework, quizzes and exams.': 'أكثر النقاط من الواجبات والاختبارات.', 'students': 'طلاب', 'teachers': 'معلمون', 'student points': 'نقاط الطلاب', 'achievements': 'إنجازات', 'achievement': 'إنجاز', 'pts': 'نقطة',
  'School report': 'تقرير المدرسة', 'Print or save as PDF': 'طباعة أو حفظ PDF', 'Printable report': 'تقرير للطباعة', "Your school's key percentages and how they changed month by month.": 'النسب الرئيسية لمدرستك وتغيّرها شهراً بشهر.',
  'Record an achievement': 'تسجيل إنجاز', 'What kind of achievement?': 'ما نوع الإنجاز؟', 'Title': 'العنوان', 'Date': 'التاريخ', 'What was achieved': 'ما الذي تحقق', 'Impact (who benefited, numbers)': 'الأثر (المستفيدون والأرقام)',
  'Evidence link (optional)': 'رابط الدليل (اختياري)', 'Add to our achievements': 'إضافة إلى إنجازاتنا', 'Our achievements': 'إنجازاتنا', 'No achievements yet. Add the first one above.': 'لا توجد إنجازات بعد. أضف أول إنجاز أعلاه.',
  'Recognition points': 'نقاط التقدير', 'Evidence': 'الدليل', 'Remove this achievement?': 'حذف هذا الإنجاز؟', 'Achievement removed.': 'تم حذف الإنجاز.',
  "Achievement added. Leadership can see it in your school's report.": 'تمت إضافة الإنجاز. تراه القيادة في تقرير مدرستك.',
  "The school's achievements against EAA's success measures, and the recognition points they earned.": 'إنجازات المدرسة وفق مؤشرات النجاح في الهيئة ونقاط التقدير التي حققتها.',
  'Facilities and innovation': 'المرافق والابتكار', 'Academic results': 'النتائج الأكاديمية', 'Student competitions and awards': 'مسابقات الطلاب وجوائزهم', 'Community and partnerships': 'المجتمع والشراكات',
  'Staff development': 'تطوير الكادر', 'Wellbeing and inclusion': 'الرفاه والدمج', 'Other': 'أخرى',
  'Record an indicator': 'تسجيل مؤشر', 'Teacher': 'المعلم', 'About': 'بخصوص', 'What happened': 'ما الذي حدث', 'Record indicator': 'تسجيل المؤشر', 'Indicators': 'المؤشرات', 'Warnings': 'الإنذارات', 'Warning': 'إنذار',
  'Indicator': 'مؤشر', 'Investigation': 'تحقيق', 'Status': 'الحالة', 'Clear': 'لا شيء', 'Warned': 'مُنذر', 'Under investigation': 'قيد التحقيق', 'Open investigations': 'التحقيقات المفتوحة',
  'Teachers': 'المعلمون', 'Indicators count towards the next warning.': 'تُحتسب المؤشرات نحو الإنذار التالي.', 'Indicators towards the next warning': 'المؤشرات نحو الإنذار التالي',
  'Record an indicator when a teacher is not delivering as expected. 3 indicators make 1 warning; 3 warnings open an investigation automatically.': 'سجّل مؤشراً عندما لا يؤدي المعلم كما هو متوقع. 3 مؤشرات = إنذار واحد؛ و3 إنذارات تفتح تحقيقاً تلقائياً.',
  'You record it, with what happened': 'تسجّله مع ما حدث', 'The teacher is told': 'يُبلَّغ المعلم', 'Opens by itself; you and the subject coordinator are involved': 'يُفتح تلقائياً؛ وتشارك فيه أنت ومنسق المادة',
  'Weekly plans late or missing': 'خطط أسبوعية متأخرة أو مفقودة', 'Marks or grades late': 'تأخر الدرجات', 'Lesson delivery': 'تقديم الدروس', 'Attendance or punctuality': 'الحضور والانضباط', 'Professional conduct': 'السلوك المهني',
  'People involved': 'المشاركون', 'Why it was opened': 'سبب فتحه', 'Notes': 'الملاحظات', 'Add note': 'إضافة ملاحظة', 'Add a note': 'أضف ملاحظة', 'Add': 'إضافة', 'Close the investigation': 'إغلاق التحقيق',
  'Close with this outcome': 'الإغلاق بهذه النتيجة', 'Outcome': 'النتيجة', 'Investigations you are involved in.': 'التحقيقات التي تشارك فيها.', 'You are not involved in any investigation.': 'لا تشارك في أي تحقيق.',
  'Note added.': 'أُضيفت الملاحظة.', 'Added. They were told.': 'تمت الإضافة وإبلاغه.', 'Investigation closed.': 'أُغلق التحقيق.', 'Third warning: an investigation was opened.': 'الإنذار الثالث: فُتح تحقيق.',
  'Your school at a glance': 'مدرستك في لمحة', 'Key percentages in plain numbers. Press one for the details.': 'النسب الرئيسية بأرقام واضحة. اضغط على أي منها للتفاصيل.', 'All metrics': 'كل المؤشرات', 'All': 'الكل',
  'open investigations': 'تحقيقات مفتوحة', 'Is file health improving?': 'هل تتحسن سلامة الملفات؟', 'File health over time': 'سلامة الملفات عبر الزمن', 'Score 0 to 100. Higher is better.': 'الدرجة من 0 إلى 100. الأعلى أفضل.',
  '1 month': 'شهر', '3 months': '3 أشهر', '6 months': '6 أشهر', '1 year': 'سنة', '▲ Improving': '▲ يتحسن', '▼ Getting worse': '▼ يتراجع', '● Steady': '● مستقر', 'from': 'من', 'to': 'إلى', 'over this period.': 'خلال هذه الفترة.',
  'File health score': 'درجة سلامة الملفات', 'Not enough history yet. The chart fills in as days pass.': 'لا يوجد سجل كافٍ بعد. يمتلئ الرسم مع مرور الأيام.',
  "Today's tasks": 'مهام اليوم', 'All done. Well done!': 'أنجزت كل شيء. أحسنت!', 'thing to do': 'مهمة', 'things to do': 'مهام', 'Due today': 'التسليم اليوم', 'Quiz': 'اختبار قصير',
  'Multiple choice': 'اختيار من متعدد', 'True or false': 'صح أو خطأ', 'Short answer': 'إجابة قصيرة', 'True': 'صح', 'False': 'خطأ', 'Accepted answers, one per line': 'الإجابات المقبولة، كل إجابة في سطر',
  'Capitals, extra spaces and a full stop at the end do not matter.': 'لا تهم الأحرف الكبيرة أو المسافات الزائدة أو النقطة في النهاية.', 'Your answer': 'إجابتك', 'Accepted answers': 'الإجابات المقبولة', 'Tick the correct one.': 'اختر الإجابة الصحيحة.',
  'Submit your plan for each week; your coordinator reviews it.': 'سلّم خطتك لكل أسبوع؛ ويراجعها منسقك.', "Every teacher's weekly plan and its review status.": 'الخطة الأسبوعية لكل معلم وحالة مراجعتها.',
  "Points from homework, quizzes and exams: each school's total, average per student and its outstanding students.": 'النقاط من الواجبات والاختبارات: مجموع كل مدرسة ومتوسط الطالب وطلابها المتميزون.'
});
Object.assign(AR, {
  'Pick up': 'استلام', '✓ Picked up': '✓ تم الاستلام', 'Picked up': 'تم الاستلام', 'Picked up today': 'تم استلامهم اليوم', 'Picked up today at': 'تم الاستلام اليوم الساعة',
  'Confirm the pick-up': 'تأكيد الاستلام', 'Collected by': 'استلمه', 'Cancel': 'إلغاء', 'Not picked up yet today.': 'لم يتم الاستلام اليوم بعد.',
  'I have read this and I accept responsibility for my child from now on.': 'قرأت ذلك وأتحمّل مسؤولية طفلي من الآن.',
  'You confirmed the pick-up and accepted responsibility for your child.': 'أكدت الاستلام وتحمّلت مسؤولية طفلك.', 'Picked up. The school can see it now.': 'تم الاستلام. تراه المدرسة الآن.',
  'Choose who is collecting, then press Pick up.': 'اختر من سيستلم ثم اضغط استلام.', 'Recent pick-ups': 'آخر عمليات الاستلام', 'Nobody has been picked up yet today.': 'لم يُستلم أي طالب اليوم بعد.',
  'Approve who may collect your child. Press Pick up when you collect them: it is confirmed at once and the school sees it.': 'حدّد من يحق له استلام طفلك. اضغط استلام عند الاستلام؛ يُؤكَّد فوراً وتراه المدرسة.',
  'Live: when a parent presses Pick up, the child shows here in green at once. No approval is needed.': 'مباشر: عندما يضغط ولي الأمر استلام، يظهر الطالب هنا باللون الأخضر فوراً. لا حاجة لموافقة.',
  'Parents confirm the pick-up themselves and accept responsibility. Newest first; it updates by itself.': 'يؤكد أولياء الأمور الاستلام بأنفسهم ويتحمّلون المسؤولية. الأحدث أولاً؛ يتحدّث تلقائياً.',
  'Pick-up log': 'سجل الاستلام', 'Confirmed by': 'أكّده', 'Older requests': 'طلبات سابقة', 'Older requests waiting': 'طلبات سابقة بانتظار',
  'From before parents confirmed pick-ups themselves.': 'من قبل أن يؤكد أولياء الأمور الاستلام بأنفسهم.',
  'Every pick-up is kept permanently and cannot be changed, with the parent who confirmed it. The last 30 days are shown.': 'تُحفظ كل عملية استلام بشكل دائم ولا يمكن تغييرها، مع ولي الأمر الذي أكّدها. تُعرض آخر 30 يوماً.'
});

// Short Arabic for cards and tiles: written to fit the space, not translated word for word
ROLE_AR.PRINCIPAL_ASSISTANT = 'وكيل المدرسة';
Object.assign(AR, {
  'Principal Assistant': 'وكيل المدرسة',
  // measure tiles
  'Strong': 'جيد', 'Watch': 'متوسط', 'Needs attention': 'ضعيف', 'No data yet': 'لا بيانات',
  'Attendance': 'الحضور', 'Average grade': 'متوسط الدرجات', 'Homework done': 'الواجبات', 'Quiz average': 'الاختبارات القصيرة', 'Weekly plans': 'الخطط الأسبوعية', 'File health': 'سلامة الملفات',
  'Students present, last 30 days': 'آخر 30 يوماً', 'All published marks': 'الدرجات المنشورة', 'Homework marked done, last 30 days': 'المنجزة، آخر 30 يوماً',
  'Average quiz score': 'متوسط النتائج', "Teachers' weekly plans submitted, last 4 weeks": 'المسلّمة، آخر 4 أسابيع', 'Files kept safely, out of 100': 'الدرجة من 100',
  'Your school at a glance': 'مدرستك في لمحة', 'Key percentages in plain numbers. Press one for the details.': 'اضغط على أي مؤشر لعرض تفاصيله.',
  "Your school's key percentages and how they changed month by month.": 'مؤشرات مدرستك وتغيّرها شهرياً.', 'Month by month': 'شهرياً',
  'The last twelve months. Hover a point for its value.': 'آخر 12 شهراً.',
  // coordinator overview cards
  'OPEN ISSUES': 'مشكلات مفتوحة', 'FOLLOW-UP': 'المتابعة', 'FIXED': 'تم الإصلاح', 'Needs review': 'للمراجعة', 'Needs you': 'تحتاجك', 'All clear': 'لا شيء',
  'Resolved': 'محلولة', 'Owners waiting on you': 'بانتظارك', 'No owners waiting on you': 'لا أحد بانتظارك',
  // principal staff card
  'Issues fixed in their subject': 'المشكلات المحلولة في مادتهم', 'Files with an open issue': 'ملفات فيها مشكلة مفتوحة', 'fixed': 'محلولة', 'open': 'مفتوحة',
  'Fixed': 'المحلولة', 'Open': 'المفتوحة',
  ": issues fixed in their subject.": ': المشكلات المحلولة في مادتهم.', "70% or more of the subject's issues fixed": '70% فأكثر', '40–69%': '40–69%', 'under 40%': 'أقل من 40%',
  ': files with an issue they still need to fix.': ': ملفات ما زال عليهم إصلاحها.', 'none': 'لا شيء', '1–3': '1–3', '4 or more': '4 فأكثر',
  // support and setup moves
  'Write to Leadership at head office. Replies and status changes appear here and in your notifications.': 'راسل القيادة في المكتب الرئيسي. تظهر الردود والتحديثات هنا وفي إشعاراتك.',
  'Your tickets to Leadership at head office.': 'تذاكرك إلى القيادة في المكتب الرئيسي.',
  'No term exists yet. School IT creates the terms under Academic setup.': 'لا يوجد فصل دراسي بعد. تنشئه تقنية المعلومات من الإعداد الأكاديمي.',
  'is waiting for School IT to confirm.': 'بانتظار تأكيد تقنية المعلومات.', 'Ask for a place in a class. School IT confirms it.': 'اطلب مكاناً في صف. تؤكده تقنية المعلومات.'
});

// Wording review: school register, and buttons that say what they do
Object.assign(AR, {
  'Mark as done · +5 points': 'أنجزتُ الواجب · +5 نقاط',          // a button: "I've done it", not "done"
  'Pass': 'مقبول', 'Keep practising': 'يحتاج إلى تحسين',             // the grade scale schools use: ممتاز، جيد جداً، جيد، مقبول
  'Clear': 'لا ملاحظات',                                             // a teacher's status, not "nothing"
  'Quiz taken': 'إكمال اختبار قصير',
  'Tick the circle next to the correct answer.': 'ضع علامة على الدائرة بجانب الإجابة الصحيحة.',
  'Tick the correct one.': 'حدّد الإجابة الصحيحة.',
  'Indicator': 'ملاحظة أداء', 'Indicators': 'ملاحظات الأداء', 'Record an indicator': 'تسجيل ملاحظة أداء', 'Record indicator': 'تسجيل الملاحظة',
  'Indicators towards the next warning': 'الملاحظات قبل الإنذار التالي', 'Indicators count towards the next warning.': 'كل 3 ملاحظات تصبح إنذاراً.',
  'You record it, with what happened': 'تسجّلها مع وصف ما حدث'
});

// ---------------------------------------------------------------- counted nouns: "12 students" / "12 طالباً"
/** English singular and plural, and the Arabic forms: [singular, dual, plural (3–10), accusative singular (11–99), gender]. */
const COUNTED = {
  student: ['student', 'students', 'طالب', 'طالبان', 'طلاب', 'طالباً', 'm'],
  teacher: ['teacher', 'teachers', 'معلم', 'معلمان', 'معلمين', 'معلماً', 'm'],
  question: ['question', 'questions', 'سؤال', 'سؤالان', 'أسئلة', 'سؤالاً', 'm'],
  achievement: ['achievement', 'achievements', 'إنجاز', 'إنجازان', 'إنجازات', 'إنجازاً', 'm'],
  point: ['point', 'points', 'نقطة', 'نقطتان', 'نقاط', 'نقطة', 'f'],
  file: ['file', 'files', 'ملف', 'ملفان', 'ملفات', 'ملفاً', 'm'],
  issue: ['issue', 'issues', 'مشكلة', 'مشكلتان', 'مشكلات', 'مشكلة', 'f'],
  incident: ['incident', 'incidents', 'حادثة', 'حادثتان', 'حوادث', 'حادثة', 'f'],
  assessment: ['assessment', 'assessments', 'تقييم', 'تقييمان', 'تقييمات', 'تقييماً', 'm'],
  day: ['day', 'days', 'يوم', 'يومان', 'أيام', 'يوماً', 'm'],
  result: ['result', 'results', 'نتيجة', 'نتيجتان', 'نتائج', 'نتيجة', 'f'],
  homework: ['piece of homework', 'pieces of homework', 'واجب', 'واجبان', 'واجبات', 'واجباً', 'm'],
  task: ['thing to do', 'things to do', 'مهمة', 'مهمتان', 'مهام', 'مهمة', 'f'],
  person: ['person', 'people', 'شخص', 'شخصان', 'أشخاص', 'شخصاً', 'm'],
  school: ['school', 'schools', 'مدرسة', 'مدرستان', 'مدارس', 'مدرسة', 'f'],
  note: ['note', 'notes', 'ملاحظة', 'ملاحظتان', 'ملاحظات', 'ملاحظة', 'f'],
  warning: ['warning', 'warnings', 'إنذار', 'إنذاران', 'إنذارات', 'إنذاراً', 'm'],
  week: ['week', 'weeks', 'أسبوع', 'أسبوعان', 'أسابيع', 'أسبوعاً', 'm'],
  ticket: ['ticket', 'tickets', 'تذكرة', 'تذكرتان', 'تذاكر', 'تذكرة', 'f']
};
/** A number with its noun, grammatical in the language on screen. 1 → "طالب واحد", 2 → "طالبان", 3–10 → "3 طلاب", 11–99 → "12 طالباً", 100 → "100 طالب". */
function count(n, key) {
  const f = COUNTED[key];
  n = Number(n) || 0;
  if (!f) return String(n);
  if (I18N.lang !== 'ar') return n + ' ' + (n === 1 ? f[0] : f[1]);
  const [, , one, two, few, many, g] = f;
  if (n === 0) return '0 ' + few;
  if (n === 1) return one + (g === 'f' ? ' واحدة' : ' واحد');
  if (n === 2) return two;
  const r = n % 100;
  if (n < 100) return n + ' ' + (n <= 10 ? few : many);
  return n + ' ' + (r <= 2 ? one : r <= 10 ? few : many);
}

/** "13 of 85 days" / "13 من 85 يوماً" */
const ofDays = (n, days) => I18N.lang === 'ar' ? n + ' من ' + count(days, 'day') : n + ' of ' + count(days, 'day');
Object.assign(AR, {
  'open and urgent': 'مفتوحة وعاجلة', 'took it': 'أدّوه', 'fixed by staff': 'أصلحها الكادر', 'checked': 'تم فحصها', 'need action first': 'تحتاج إجراءً أولاً',
  'Absence rate': 'نسبة الغياب', 'Documented absence': 'غياب بعذر', 'Undocumented absence': 'غياب بدون عذر', 'Student points': 'نقاط الطلاب',
  'average per student': 'متوسط الطالب', 'Done': 'المنجز', 'last': 'آخر', 'with notes': 'لديهم ملاحظات'
});

// ---------------------------------------------------------------- interface text that still showed in English
Object.assign(AR, {
  'Due': 'التسليم', 'Class': 'الصف', 'Weekly plan': 'خطة أسبوعية', 'Lesson plan': 'خطة درس', 'Unit plan': 'خطة وحدة',
  'A new message from a teacher, a student or a parent.': 'رسالة جديدة من معلم أو طالب أو ولي أمر.',
  'New homework, a new quiz or a notice from a teacher.': 'واجب أو اختبار قصير أو إشعار جديد من المعلم.',
  'Message (Arabic)': 'الرسالة (بالعربية)', 'Title (Arabic)': 'العنوان (بالعربية)',
  "The network's file-health score, 0 to 100 (higher is better). Daily for a month, weekly averages for longer.": 'درجة سلامة الملفات في الشبكة من 0 إلى 100 (الأعلى أفضل). يومياً لمدة شهر، ومتوسط أسبوعي للفترات الأطول.',
  'Tickets from Leadership, and from principals to head office. Open one to assign, reply or close it.': 'تذاكر القيادة وتذاكر المديرين إلى المكتب الرئيسي. افتح أي تذكرة لإسنادها أو الرد عليها أو إغلاقها.',
  "Fill in the school template. Each kind of achievement is worth recognition points for the school; Leadership sees it in its report.": 'املأ نموذج المدرسة. لكل نوع من الإنجازات نقاط تقدير للمدرسة، وتطّلع عليه القيادة في تقريرها.',
  'A new learning space or technology, for example a VR room or a science lab.': 'مرفق تعليمي أو تقنية جديدة، مثل غرفة واقع افتراضي أو مختبر علوم.',
  'Results that improved, a subject that outperformed the target.': 'تحسّن في النتائج، أو مادة تجاوزت هدفها.',
  'Prizes and places students won at school, national or international level.': 'جوائز ومراكز حققها الطلاب محلياً أو وطنياً أو دولياً.',
  'Work with parents, the community, universities or companies.': 'تعاون مع أولياء الأمور أو المجتمع أو الجامعات أو الشركات.',
  'Training, certification or recognition of teachers and staff.': 'تدريب الكادر أو اعتماده أو تكريمه.',
  'Programmes for student wellbeing, attendance or inclusion.': 'برامج لرفاه الطلاب أو انتظامهم أو دمجهم.',
  'Anything else worth recognising.': 'أي إنجاز آخر يستحق التقدير.',
  // placeholders
  'e.g. Opened a VR room': 'مثال: افتتاح غرفة واقع افتراضي', 'A few sentences about it': 'بضع جمل عن الإنجاز', 'e.g. 240 students use it every week': 'مثال: يستخدمها 240 طالباً أسبوعياً',
  'https://… photos, certificate or news': 'https://… صور أو شهادة أو خبر', 'Facts only: what, when': 'الوقائع فقط: ماذا ومتى',
  'e.g. Exercise 4, page 32': 'مثال: التمرين 4، صفحة 32', 'What to do, what to bring': 'المطلوب وما يجب إحضاره', 'e.g. Fractions check': 'مثال: اختبار الكسور',
  'The chapter, pages or text the questions come from': 'الفصل أو الصفحات أو النص الذي أُخذت منه الأسئلة', 'e.g. Doha': 'مثال: الدوحة',
  'Shown to the student after they send the quiz': 'يظهر للطالب بعد إرسال الاختبار', 'e.g. Bring your geometry set tomorrow': 'مثال: أحضروا أدوات الهندسة غداً',
  'Answer': 'إجابة', 'Your answer': 'إجابتك'
});
AR_RULES.push(
  [/^(.+) \(parent confirmed\)$/, n => `${n} (أكّده ولي الأمر)`],
  [/^(.+) \(parent\)$/, n => `${n} (ولي الأمر)`],
  [/^Homework: (.+?)( — .+)?$/, (s, t) => `واجب: ${trTextRaw(s)}${t || ''}`],
  [/^Quiz: (.+) \((\d+)\/(\d+)\)$/, (q, a, b) => `اختبار قصير: ${q} (${a}/${b})`],
  [/^(\d+) questions?\. Open Assessments to take it\.$/, n => `${count(Number(n), 'question')}. افتح التقييمات لأدائه.`],
  [/^Class ([0-9]+[A-Za-z]?)$/, c => `الصف ${c}`],
  [/^Due (\d{4}-\d{2}-\d{2})\. ?(.*)$/, (d, rest) => `موعد التسليم ${new Date(d + 'T00:00:00').toLocaleDateString(uiLocale(), { day: 'numeric', month: 'long' })}. ${rest}`.trim()]
);

// ---------------------------------------------------------------- activity log: newer actions and the details the server writes
Object.assign(AR, {
  'TEACHER_INDICATOR': 'تسجيل ملاحظة أداء', 'TEACHER_WARNING': 'إصدار إنذار', 'INVESTIGATION_OPENED': 'فتح تحقيق', 'INVESTIGATION_CLOSED': 'إغلاق تحقيق',
  'MATERIAL_ADDED': 'إضافة مادة دراسية', 'POINTS_AWARDED': 'منح نقاط', 'ASSESSMENT_PUBLISHED': 'نشر تقييم', 'ASSESSMENT_UNPUBLISHED': 'إلغاء نشر تقييم',
  'investigation opened': 'فُتح تحقيق', 'performance matrix': 'مصفوفة الأداء', 'open': 'مفتوحة', 'closed': 'مغلقة'
});
AR_RULES.push(
  [/^warning (\d+)$/, n => `إنذار ${n}`],
  [/^(.+) picked up by (.+); parent accepted responsibility$/, (s, c) => `استلم ${c} الطالب ${s}، وتحمّل ولي الأمر المسؤولية`],
  [/^(EAA_IT|SCHOOL_IT|LEADERSHIP) \/ (LOW|NORMAL|HIGH|URGENT): (.+)$/, (to, u, t) =>
    `${{ EAA_IT: 'تقنية المعلومات في الهيئة', SCHOOL_IT: 'تقنية المعلومات بالمدرسة', LEADERSHIP: 'القيادة' }[to]} / ${{ LOW: 'منخفضة', NORMAL: 'عادية', HIGH: 'مرتفعة', URGENT: 'عاجلة' }[u]}: ${t}`],
  [/^(.+) \(term (\d+), max ([\d.]+)\)$/, (a, t, m) => `${a} (الفصل ${t}، من ${m})`],
  [/^(.+) \(term (\d+)\)$/, (a, t) => `${a} (الفصل ${t})`],
  [/^(.+) for (\d+[A-Za-z])$/, (a, c) => `${a} للصف ${c}`]
);
