/* Hafez interface language: English or Arabic (right to left).
   The app writes its screens in English; when Arabic is chosen, app.js passes every piece of interface text through
   trText() as the screen is built. Only text is translated, never attribute values, so routes, filters and form
   values keep working. People's names, file names and other data pass through unchanged. */
'use strict';

const I18N = { lang: 'en', stored: false };
try {
  const saved = localStorage.getItem('hafez.lang');
  if (saved === 'ar' || saved === 'en') { I18N.lang = saved; I18N.stored = true; }
  else if ((navigator.language || '').toLowerCase().startsWith('ar')) I18N.lang = 'ar';
} catch { /* storage blocked: stay in English */ }

function applyLang() {
  document.documentElement.lang = I18N.lang;
  document.documentElement.dir = I18N.lang === 'ar' ? 'rtl' : 'ltr';
}
function setLang(l, remember) {
  I18N.lang = l === 'ar' ? 'ar' : 'en';
  if (remember !== false) { try { localStorage.setItem('hafez.lang', I18N.lang); I18N.stored = true; } catch { } }
  applyLang();
}
applyLang();

/** Locale for dates: Arabic month and day names, Latin digits so numbers read the same everywhere. */
const uiLocale = () => (I18N.lang === 'ar' ? 'ar-QA-u-nu-latn' : undefined);

const AR = {
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
const AR_RULES = [
  [/^(\d+) of (\d+) days$/, (a, b) => `${a} من ${b} أيام`],
  [/^Welcome, parent of (.+)$/, n => 'مرحباً، وليّ أمر ' + n.replace(/ and /g, ' و')],
  [/^Welcome, (.+)$/, n => 'مرحباً، ' + n],
  [/^Parent of (.+)$/, n => 'وليّ أمر ' + n],
  [/^Section (.+)$/, n => 'الشعبة ' + n],
  [/^Student ID (.+)$/, n => 'رقم الطالب ' + n],
  [/^Request failed \((\d+)\)$/, n => `تعذّر تنفيذ الطلب (${n})`],
  [/^Rule (\S+): (.+)$/, (c, n) => `القاعدة ${c}: ${n}`],
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
  [/^Attendance saved \((.+)\)\.?$/, s => `تم حفظ الحضور (${s})`],
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
const BRAND = { en: 'Hafez', ar: 'حافظ' };   // replaced at start-up by /api/auth/brand (hafez.brand in application.yml)
function brandify(s) { return (BRAND.en === 'Hafez' && BRAND.ar === 'حافظ') || s == null ? s : String(s).replace(/Hafez/g, BRAND.en).replace(/حافظ/g, BRAND.ar); }
function trText(s) { return brandify(trTextRaw(s)); }
function trTextRaw(s) {
  if (I18N.lang !== 'ar' || s == null) return s;
  s = String(s);
  const m = s.match(/^(\s*)([\s\S]*?)(\s*)$/);
  if (m[2] === ',') return m[1] + '،' + m[3];
  if (!m[2] || !/[A-Za-z→]/.test(m[2])) return s;
  return m[1] + trCore(m[2].replace(/\s+/g, ' ')) + m[3];
}
