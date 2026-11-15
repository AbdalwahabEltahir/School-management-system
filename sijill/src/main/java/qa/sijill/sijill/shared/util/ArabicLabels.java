package qa.sijill.sijill.shared.util;

import java.util.Map;

/**
 * Arabic names for the codes that appear in downloads (activity-log actions, record types, issue statuses,
 * severities, incident types). The same words the Arabic screens use (static/app/i18n.js), so a file downloaded
 * from the Arabic view reads like the screen it came from. Unknown codes are returned unchanged.
 */
public final class ArabicLabels {

    private ArabicLabels() {}

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("ASSESSMENT_CREATED", "إنشاء تقييم"),
            Map.entry("ATTENDANCE_CHANGED", "تعديل حضور"),
            Map.entry("ATTENDANCE_RECORDED", "تسجيل حضور"),
            Map.entry("ApprovalRequest", "طلب موافقة"),
            Map.entry("APPROVAL_REQUESTED", "طلب موافقة"),
            Map.entry("Assessment", "التقييم"),
            Map.entry("BULK_DELETION", "حذف ملفات كثيرة"),
            Map.entry("CHECK_RUN", "تشغيل فحص"),
            Map.entry("CONNECTION_LOST", "انقطاع الاتصال"),
            Map.entry("Collector", "المستلم"),
            Map.entry("DIRECTORY_SYNCED", "مزامنة الدليل"),
            Map.entry("DOC_TYPE_CHANGED", "تغيير نوع مستند"),
            Map.entry("DocType", "نوع مستند"),
            Map.entry("ENROLMENT_REQUESTED", "طلب تسجيل"),
            Map.entry("ENROLMENT_REQUEST_DECIDED", "البت في طلب تسجيل"),
            Map.entry("EXAM_LEAK", "تسريب اختبار"),
            Map.entry("EXCEPTION_ENDED", "انتهاء استثناء"),
            Map.entry("EXCEPTION_GRANTED", "منح استثناء"),
            Map.entry("EXPORT_CREATED", "إنشاء تصدير"),
            Map.entry("Exception", "استثناء"),
            Map.entry("Export", "تصدير"),
            Map.entry("FILE_CLASSIFIED", "تصنيف ملف"),
            Map.entry("FILE_DELETED", "حذف ملف"),
            Map.entry("File", "ملف"),
            Map.entry("IMPORTANT_DELETION", "حذف ملف مهم"),
            Map.entry("INCIDENT_RAISED", "تسجيل حادثة"),
            Map.entry("INCIDENT_RESOLVED", "حل حادثة"),
            Map.entry("ISSUE_OPENED", "فتح مشكلة"),
            Map.entry("ISSUE_REASSIGNED", "إعادة تعيين مشكلة"),
            Map.entry("ISSUE_STATUS_CHANGED", "تغيير حالة مشكلة"),
            Map.entry("Incident", "حادثة"),
            Map.entry("Invitation", "دعوة"),
            Map.entry("Issue", "مشكلة"),
            Map.entry("JobRun", "تشغيل مهمة"),
            Map.entry("MARKS_SUBMITTED", "إرسال الدرجات"),
            Map.entry("MARKS_UNLOCKED", "فتح الدرجات للتعديل"),
            Map.entry("MARK_ENTERED", "إدخال درجة"),
            Map.entry("MATERIAL_ADDED", "إضافة مادة دراسية"),
            Map.entry("NORMAL", "عادي"),
            Map.entry("NOTICE_PUBLISHED", "نشر إعلان"),
            Map.entry("NOTICE_REMOVED", "حذف إعلان"),
            Map.entry("Notice", "إعلان"),
            Map.entry("PERSON_ADDED", "إضافة شخص"),
            Map.entry("PERSON_DELETED", "حذف شخص"),
            Map.entry("PERSON_FOLLOWED_UP", "متابعة شخص"),
            Map.entry("PERSON_UPDATED", "تحديث شخص"),
            Map.entry("PICKUP_ARRIVED", "وصول للاستلام"),
            Map.entry("PICKUP_CANCELLED", "إلغاء الاستلام"),
            Map.entry("PICKUP_COLLECTOR_ADDED", "إضافة مستلم"),
            Map.entry("PICKUP_COLLECTOR_REMOVED", "إزالة مستلم"),
            Map.entry("PICKUP_DECLINED", "رفض التسليم"),
            Map.entry("PICKUP_RELEASED", "تسليم طالب"),
            Map.entry("PICKUP_VERIFIED", "التحقق من المستلم"),
            Map.entry("PLANNING_REVIEWED", "مراجعة خطة"),
            Map.entry("PLANNING_SUBMITTED", "إرسال خطة"),
            Map.entry("PROFILE_UPDATED", "تحديث الملف الشخصي"),
            Map.entry("Person", "الشخص"),
            Map.entry("PickupRequest", "طلب استلام"),
            Map.entry("PlanningSubmission", "خطة درس"),
            Map.entry("RESTORE_APPROVED", "اعتماد استعادة"),
            Map.entry("RESTORE_COMPLETED", "اكتمال استعادة"),
            Map.entry("RESTORE_REJECTED", "رفض استعادة"),
            Map.entry("RESTORE_REQUESTED", "طلب استعادة"),
            Map.entry("RESULTS_PUBLISHED", "نشر النتائج"),
            Map.entry("RESULTS_UNPUBLISHED", "إلغاء نشر النتائج"),
            Map.entry("ROLE_GROUP_CHANGED", "تغيير مجموعة دور"),
            Map.entry("RULE_APPROVED", "اعتماد قاعدة"),
            Map.entry("RULE_CREATED", "إنشاء قاعدة"),
            Map.entry("RULE_DELETED", "حذف قاعدة"),
            Map.entry("RULE_PAUSED", "إيقاف قاعدة"),
            Map.entry("RULE_RESUMED", "استئناف قاعدة"),
            Map.entry("RULE_UPDATED", "تحديث قاعدة"),
            Map.entry("Report", "تقرير"),
            Map.entry("RestoreRequest", "طلب استعادة"),
            Map.entry("RoleGroup", "مجموعة أدوار"),
            Map.entry("Rule", "القاعدة"),
            Map.entry("SECTION_CREATED", "إنشاء شعبة"),
            Map.entry("SETTING_CHANGED", "تغيير إعداد"),
            Map.entry("SITE_CREATED", "إضافة مدرسة"),
            Map.entry("SITE_REMOVED", "إزالة مدرسة"),
            Map.entry("SITE_UPDATED", "تحديث مدرسة"),
            Map.entry("STUDENT_ENROLLED", "تسجيل طالب"),
            Map.entry("STUDENT_RECORD_VIEWED", "عرض سجل طالب"),
            Map.entry("SUBJECT_CHANGED", "تغيير مادة"),
            Map.entry("SchoolFile", "ملف مدرسي"),
            Map.entry("Section", "الشعبة"),
            Map.entry("Setting", "الإعداد"),
            Map.entry("Site", "مدرسة"),
            Map.entry("Subject", "المادة"),
            Map.entry("SupportTicket", "تذكرة دعم"),
            Map.entry("TEACHER_ASSIGNED", "تعيين معلم"),
            Map.entry("TERM_SAVED", "حفظ فصل دراسي"),
            Map.entry("TICKET_ASSIGNED", "إسناد تذكرة"),
            Map.entry("TICKET_CREATED", "إنشاء تذكرة"),
            Map.entry("TICKET_REPLIED", "رد على تذكرة"),
            Map.entry("TICKET_STATUS_CHANGED", "تغيير حالة تذكرة"),
            Map.entry("TIMETABLE_CHANGED", "تغيير الجدول"),
            Map.entry("Term", "الفصل الدراسي"),
            Map.entry("URGENT", "عاجل"),
            Map.entry("URGENT_UNRESOLVED", "مشكلة عاجلة لم تُحل"),
            Map.entry("WELCOME_SENT", "إرسال رسالة ترحيب"));

    /** Issue statuses, as the status badges show them (a status reads differently from the action of the same name). */
    private static final Map<String, String> ISSUE_STATUS = Map.of(
            "OWNER_NOTIFIED", "تم إبلاغ المالك", "REMINDER_SENT", "تم إرسال تذكير", "NEEDS_FOLLOW_UP", "تحتاج متابعة",
            "NEEDS_REVIEW", "تحتاج مراجعة", "OWNER_DISAGREES", "المالك غير موافق", "OWNER_ASKED_FOR_HELP", "طلب المساعدة",
            "FIXED", "تم الإصلاح", "ALLOWED_EXCEPTION", "استثناء مسموح", "RULE_PAUSED", "القاعدة موقوفة",
            "CLOSED_RULE_REMOVED", "القاعدة محذوفة");

    /** The Arabic name of an issue status, or the code itself when there is none. */
    public static String issueStatus(Object status) {
        return status == null ? null : ISSUE_STATUS.getOrDefault(status.toString(), status.toString());
    }

    /** The Arabic name of a code, or the code itself when there is none. */
    public static String of(Object code) {
        if (code == null) {
            return null;
        }
        String key = code.toString();
        return LABELS.getOrDefault(key, key);
    }
}
