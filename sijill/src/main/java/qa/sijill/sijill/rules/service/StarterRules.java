package qa.sijill.sijill.rules.service;

import java.util.List;
import java.util.Set;

import qa.sijill.sijill.rules.model.CheckType;
import qa.sijill.sijill.shared.model.Sensitivity;
import qa.sijill.sijill.shared.model.Severity;

/**
 * The ten starter rules R1 – R10 (proposal 5.3), as <b>data</b>. They are copied into a new
 * organisation by {@code OrganisationSetupService} and from then on are edited in the app like any
 * other rule (configuration, not code). Reminder days are school days; "same day" is 1.
 *
 * <p>The document-type names used in the scope must match the names in {@link #DOC_TYPES}.
 */
public final class StarterRules {

    private StarterRules() {}

    /** A starter rule: its code (R1…R10) and content. All starter rules are network-wide. */
    public record Starter(String code, RuleService.Draft draft) {}

    /**
     * The 14 document types with their default sensitivity (FR-8.4). The names are illustrative and
     * should be confirmed with the schools; School IT can rename them or change a sensitivity in one click.
     */
    public static final List<DocTypeSeed> DOC_TYPES = List.of(
            new DocTypeSeed("Mark book", Sensitivity.RESTRICTED),
            new DocTypeSeed("Student report", Sensitivity.RESTRICTED),
            new DocTypeSeed("Behaviour record", Sensitivity.RESTRICTED),
            new DocTypeSeed("Medical record", Sensitivity.RESTRICTED),
            new DocTypeSeed("Special-needs record", Sensitivity.RESTRICTED),
            new DocTypeSeed("Contact list", Sensitivity.RESTRICTED),
            new DocTypeSeed("Exam", Sensitivity.CONFIDENTIAL),
            new DocTypeSeed("Policy", Sensitivity.INTERNAL),
            new DocTypeSeed("Lesson plan", Sensitivity.INTERNAL),
            new DocTypeSeed("Worksheet", Sensitivity.INTERNAL),
            new DocTypeSeed("Circular", Sensitivity.PUBLIC),
            new DocTypeSeed("Meeting minutes", Sensitivity.INTERNAL),
            new DocTypeSeed("Timetable", Sensitivity.PUBLIC),
            new DocTypeSeed("Other", Sensitivity.INTERNAL));

    public record DocTypeSeed(String name, Sensitivity sensitivity) {}

    /** Student-record document types: where rule R4 requires them to be in "Student Records". */
    private static final Set<String> STUDENT_RECORD_TYPES = Set.of(
            "Mark book", "Student report", "Behaviour record", "Medical record", "Special-needs record", "Contact list");

    private static final String FIXED_EN = "3. Press \"I've fixed it\" so Sijill can re-check the file.";
    private static final String FIXED_AR = "٣. اضغط «أصلحته» ليعيد Sijill فحص الملف.";

    public static List<Starter> all() {
        return List.of(
                new Starter("R1", new RuleService.Draft(
                        "School files in the school shared drive",
                        "Files in a personal drive are lost when the owner leaves and are not backed up with the school's files.",
                        "الملفات في المساحة الشخصية تضيع عند مغادرة صاحبها ولا تُحفظ مع ملفات المدرسة.",
                        CheckType.LOCATION, Severity.NORMAL, 2, null,
                        "1. Open the file.\n2. Move it to the right folder of your school's shared drive.\n" + FIXED_EN,
                        "١. افتح الملف.\n٢. انقله إلى المجلد المناسب في المساحة المشتركة لمدرستك.\n" + FIXED_AR,
                        Set.of(), Set.of())),

                new Starter("R2", new RuleService.Draft(
                        "No public links on sensitive files",
                        "Anyone who has the link can read the file, even people outside the school.",
                        "أي شخص لديه الرابط يستطيع قراءة الملف، حتى من خارج المدرسة.",
                        CheckType.PUBLIC_LINK, Severity.URGENT, 1, null,
                        "1. Open the file's sharing settings.\n2. Change \"Anyone with the link\" to \"Restricted\" (named people only).\n" + FIXED_EN,
                        "١. افتح إعدادات مشاركة الملف.\n٢. غيّر «أي شخص لديه الرابط» إلى «مقيّد» (أشخاص محددون فقط).\n" + FIXED_AR,
                        Set.of(), Set.of(Sensitivity.CONFIDENTIAL, Sensitivity.RESTRICTED))),

                new Starter("R3", new RuleService.Draft(
                        "Restricted files stay inside",
                        "Restricted files must never be shared with people outside the network.",
                        "يجب ألا تُشارك الملفات المقيّدة مع أشخاص من خارج الشبكة.",
                        CheckType.OUTSIDE_SHARING, Severity.URGENT, 1, null,
                        "1. Open the file's sharing settings.\n2. Remove everyone who is outside the network.\n" + FIXED_EN,
                        "١. افتح إعدادات مشاركة الملف.\n٢. أزل كل من هو خارج الشبكة.\n" + FIXED_AR,
                        Set.of(), Set.of(Sensitivity.RESTRICTED))),

                new Starter("R4", new RuleService.Draft(
                        "Student records in Student Records",
                        "Student records in teaching folders can be opened by every teacher.",
                        "سجلات الطلاب الموجودة في مجلدات التدريس يستطيع كل معلم فتحها.",
                        CheckType.LOCATION, Severity.NORMAL, 2, "Student Records",
                        "1. Open the file.\n2. Move it to the \"Student Records\" drive.\n" + FIXED_EN,
                        "١. افتح الملف.\n٢. انقله إلى مساحة «سجلات الطلاب».\n" + FIXED_AR,
                        STUDENT_RECORD_TYPES, Set.of())),

                new Starter("R5", new RuleService.Draft(
                        "Only the right people open Restricted files",
                        "Restricted files should be open only to the few people who need them.",
                        "يجب أن تكون الملفات المقيّدة متاحة فقط للأشخاص القلائل الذين يحتاجون إليها.",
                        CheckType.INTERNAL_ACCESS, Severity.NORMAL, 2, null,
                        "1. Open the file's sharing settings.\n2. Remove \"all staff\" and anyone who does not need the file.\n" + FIXED_EN,
                        "١. افتح إعدادات مشاركة الملف.\n٢. أزل «جميع العاملين» وكل من لا يحتاج إلى الملف.\n" + FIXED_AR,
                        Set.of(), Set.of(Sensitivity.RESTRICTED))),

                new Starter("R6", new RuleService.Draft(
                        "No exam sharing before the exam",
                        "An exam shared before its date can leak and invalidate the exam.",
                        "الاختبار الذي يُشارك قبل موعده قد يتسرب ويُبطل الاختبار.",
                        CheckType.SHARING_BEFORE_DATE, Severity.URGENT, 1, null,
                        "1. Open the exam's sharing settings.\n2. Remove public links, outside people and \"all staff\" until the exam date.\n" + FIXED_EN,
                        "١. افتح إعدادات مشاركة الاختبار.\n٢. أزل الروابط العامة ومن هم خارج الشبكة و«جميع العاملين» حتى موعد الاختبار.\n" + FIXED_AR,
                        Set.of("Exam"), Set.of())),

                new Starter("R7", new RuleService.Draft(
                        "One official copy per policy",
                        "Staff may follow the wrong copy when several copies of a policy exist.",
                        "قد يتبع العاملون النسخة الخطأ عندما توجد عدة نسخ من السياسة نفسها.",
                        CheckType.DUPLICATES, Severity.NORMAL, 3, null,
                        "1. Find the official copy of the policy.\n2. Delete or archive this extra copy.\n" + FIXED_EN,
                        "١. حدّد النسخة الرسمية من السياسة.\n٢. احذف هذه النسخة الزائدة أو انقلها إلى الأرشيف.\n" + FIXED_AR,
                        Set.of("Policy"), Set.of())),

                new Starter("R8", new RuleService.Draft(
                        "Leavers move school files first",
                        "School files left in a leaver's personal drive are lost when their account is closed.",
                        "الملفات المدرسية المتروكة في المساحة الشخصية لمن يغادر تضيع عند إغلاق حسابه.",
                        CheckType.OWNER_LEAVING, Severity.NORMAL, 2, null,
                        "1. Open the file.\n2. Move it to the school shared drive before the last working day.\n" + FIXED_EN,
                        "١. افتح الملف.\n٢. انقله إلى المساحة المشتركة للمدرسة قبل آخر يوم عمل.\n" + FIXED_AR,
                        Set.of(), Set.of())),

                new Starter("R9", new RuleService.Draft(
                        "No files owned by shared accounts",
                        "When a shared account owns a file, nobody is personally responsible for it and the logs cannot say who did what.",
                        "عندما يملك حساب مشترك ملفًا فلا يوجد شخص مسؤول عنه ولا تستطيع السجلات تحديد من فعل ماذا.",
                        CheckType.GENERIC_OWNER, Severity.NORMAL, 2, null,
                        "1. Ask the owner of the shared account to transfer the file to a named person.\n2. Agree who will look after it.\n" + FIXED_EN,
                        "١. اطلب من صاحب الحساب المشترك نقل ملكية الملف إلى شخص محدد.\n٢. اتفقوا على من سيتولى رعايته.\n" + FIXED_AR,
                        Set.of(), Set.of())),

                new Starter("R10", new RuleService.Draft(
                        "No unapproved deletion of important files",
                        "Important files that are deleted may be lost for good. Sijill only alerts; recovery is from the trash or the backup.",
                        "قد تضيع الملفات المهمة المحذوفة نهائيًا. Sijill يكتفي بالتنبيه، ويكون الاسترداد من سلة المحذوفات أو النسخ الاحتياطي.",
                        CheckType.DELETED_OR_TRASHED, Severity.URGENT, 1, null,
                        "1. If the deletion was a mistake, restore the file from the trash.\n2. If it is not in the trash, ask your coordinator to request a restore from the backup.\n" + FIXED_EN,
                        "١. إذا كان الحذف بالخطأ فاستعد الملف من سلة المحذوفات.\n٢. إذا لم تجده فيها فاطلب من المنسق طلب استرداده من النسخ الاحتياطي.\n" + FIXED_AR,
                        Set.of("Exam", "Policy"), Set.of(Sensitivity.RESTRICTED))));
    }
}
