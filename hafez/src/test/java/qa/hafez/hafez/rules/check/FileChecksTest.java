package qa.hafez.hafez.rules.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import qa.hafez.hafez.files.model.FilePermission;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.people.model.Person;
import qa.hafez.hafez.people.model.PersonStatus;
import qa.hafez.hafez.rules.model.RuleVersion;
import qa.hafez.hafez.workspace.AccessLevel;
import qa.hafez.hafez.workspace.DriveType;
import qa.hafez.hafez.workspace.ShareTargetType;

/** The structural checks, one behaviour each. Entities are plain objects here: no Spring, no database. */
class FileChecksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    private static CheckContext ctx(SchoolFile file, List<FilePermission> perms, Person owner, String parameter) {
        RuleVersion rule = new RuleVersion();
        rule.setParameter(parameter);
        return new CheckContext(file, perms, null, owner, rule, TODAY);
    }

    private static FilePermission grant(ShareTargetType type, AccessLevel level) {
        FilePermission p = new FilePermission();
        p.setTargetType(type);
        p.setAccessLevel(level);
        p.setExternal(type == ShareTargetType.EXTERNAL_USER);
        return p;
    }

    private static SchoolFile file(DriveType drive, String folder) {
        SchoolFile f = new SchoolFile();
        f.setDriveType(drive);
        f.setFolderPath(folder);
        return f;
    }

    // ---- R1 / R4: location ----

    @Test
    void aSchoolFileInAPersonalDriveBreaksR1() {
        assertThat(new LocationCheck().evaluate(ctx(file(DriveType.PERSONAL, null), List.of(), null, null)).broken()).isTrue();
        assertThat(new LocationCheck().evaluate(ctx(file(DriveType.SHARED, "Maths"), List.of(), null, null)).broken()).isFalse();
    }

    @Test
    void aStudentRecordMustBeInTheRequiredFolderR4() {
        var check = new LocationCheck();
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, "Maths/Term 1"), List.of(), null, "Student Records")).broken()).isTrue();
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, "student records/Grade 5"), List.of(), null, "Student Records")).broken()).isFalse();
        assertThat(check.evaluate(ctx(file(DriveType.PERSONAL, "Student Records"), List.of(), null, "Student Records")).broken()).isTrue();
    }

    // ---- R2 / R3: sharing ----

    @Test
    void aPublicLinkBreaksR2() {
        var perms = List.of(grant(ShareTargetType.ANYONE_WITH_LINK, AccessLevel.VIEWER));
        assertThat(new PublicLinkCheck().evaluate(ctx(file(DriveType.SHARED, null), perms, null, null)).broken()).isTrue();
        assertThat(new PublicLinkCheck().evaluate(ctx(file(DriveType.SHARED, null),
                List.of(grant(ShareTargetType.INTERNAL_USER, AccessLevel.EDITOR)), null, null)).broken()).isFalse();
    }

    @Test
    void sharingOutsideTheNetworkBreaksR3AndOnlyTheCountIsReported() {
        var perms = List.of(grant(ShareTargetType.EXTERNAL_USER, AccessLevel.VIEWER), grant(ShareTargetType.EXTERNAL_USER, AccessLevel.EDITOR));
        CheckOutcome outcome = new OutsideSharingCheck().evaluate(ctx(file(DriveType.SHARED, null), perms, null, null));
        assertThat(outcome.broken()).isTrue();
        assertThat(outcome.detailEn()).contains("2");
    }

    // ---- R5: internal access ----

    @Test
    void openingARestrictedFileToAllStaffBreaksR5() {
        var perms = List.of(grant(ShareTargetType.ALL_STAFF, AccessLevel.VIEWER));
        assertThat(new InternalAccessCheck().evaluate(ctx(file(DriveType.SHARED, null), perms, null, null)).broken()).isTrue();
    }

    @Test
    void theNamedPeopleLimitIsOptionalAndIgnoresTheOwner() {
        var perms = List.of(grant(ShareTargetType.INTERNAL_USER, AccessLevel.OWNER),
                grant(ShareTargetType.INTERNAL_USER, AccessLevel.VIEWER), grant(ShareTargetType.INTERNAL_USER, AccessLevel.VIEWER));
        var check = new InternalAccessCheck();
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, null), perms, null, null)).broken()).isFalse();   // no limit
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, null), perms, null, "1")).broken()).isTrue();     // 2 > 1
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, null), perms, null, "2")).broken()).isFalse();    // 2 <= 2
        assertThat(check.evaluate(ctx(file(DriveType.SHARED, null), perms, null, "not a number")).broken()).isFalse();
    }

    // ---- R6: exam sharing before its date ----

    @Test
    void anExamSharedWidelyBeforeItsDateBreaksR6() {
        SchoolFile exam = file(DriveType.SHARED, null);
        exam.setExamDate(TODAY.plusDays(3));
        var perms = List.of(grant(ShareTargetType.EXTERNAL_USER, AccessLevel.VIEWER));
        assertThat(new SharingBeforeDateCheck().evaluate(ctx(exam, perms, null, null)).broken()).isTrue();
    }

    @Test
    void anExamIsFineOnOrAfterItsDateOrWhenSharedOnlyWithNamedStaff() {
        SchoolFile exam = file(DriveType.SHARED, null);
        var wide = List.of(grant(ShareTargetType.ANYONE_WITH_LINK, AccessLevel.VIEWER));
        exam.setExamDate(TODAY);
        assertThat(new SharingBeforeDateCheck().evaluate(ctx(exam, wide, null, null)).broken()).isFalse();
        exam.setExamDate(TODAY.plusDays(3));
        assertThat(new SharingBeforeDateCheck().evaluate(ctx(exam, List.of(grant(ShareTargetType.INTERNAL_USER, AccessLevel.EDITOR)), null, null)).broken()).isFalse();
    }

    // ---- R8 / R9 / R10 ----

    @Test
    void aLeaversPersonalFilesBreakR8ButSharedFilesDoNot() {
        Person leaver = new Person();
        leaver.setStatus(PersonStatus.LEAVING);
        assertThat(new OwnerLeavingCheck().evaluate(ctx(file(DriveType.PERSONAL, null), List.of(), leaver, null)).broken()).isTrue();
        assertThat(new OwnerLeavingCheck().evaluate(ctx(file(DriveType.SHARED, null), List.of(), leaver, null)).broken()).isFalse();
        leaver.setStatus(PersonStatus.LEFT); // someone who has already left still counts
        assertThat(new OwnerLeavingCheck().evaluate(ctx(file(DriveType.PERSONAL, null), List.of(), leaver, null)).broken()).isTrue();
        leaver.setStatus(PersonStatus.ACTIVE);
        assertThat(new OwnerLeavingCheck().evaluate(ctx(file(DriveType.PERSONAL, null), List.of(), leaver, null)).broken()).isFalse();
    }

    @Test
    void aFileOwnedByASharedAccountBreaksR9() {
        Person shared = new Person();
        shared.setGeneric(true);
        assertThat(new GenericOwnerCheck().evaluate(ctx(file(DriveType.SHARED, null), List.of(), shared, null)).broken()).isTrue();
        assertThat(new GenericOwnerCheck().evaluate(ctx(file(DriveType.SHARED, null), List.of(), new Person(), null)).broken()).isFalse();
        assertThat(new GenericOwnerCheck().evaluate(ctx(file(DriveType.SHARED, null), List.of(), null, null)).broken()).isFalse();
    }

    @Test
    void aTrashedFileBreaksR10() {
        SchoolFile f = file(DriveType.SHARED, null);
        assertThat(new DeletedOrTrashedCheck().evaluate(ctx(f, List.of(), null, null)).broken()).isFalse();
        f.setTrashedAt(java.time.Instant.now());
        assertThat(new DeletedOrTrashedCheck().evaluate(ctx(f, List.of(), null, null)).broken()).isTrue();
    }

    // ---- every check explains itself in both languages ----

    @Test
    void everyBrokenOutcomeHasEnglishAndArabicText() {
        CheckOutcome o = new PublicLinkCheck().evaluate(ctx(file(DriveType.SHARED, null),
                List.of(grant(ShareTargetType.ANYONE_WITH_LINK, AccessLevel.VIEWER)), null, null));
        assertThat(o.detailEn()).isNotBlank();
        assertThat(o.detailAr()).isNotBlank();
    }
}
