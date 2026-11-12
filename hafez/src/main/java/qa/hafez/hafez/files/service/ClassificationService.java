package qa.hafez.hafez.files.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.files.event.FileEvents;
import qa.hafez.hafez.files.integration.FileClassifier;
import qa.hafez.hafez.files.model.FileProfile;
import qa.hafez.hafez.files.model.SchoolFile;
import qa.hafez.hafez.files.repository.FileProfileRepository;
import qa.hafez.hafez.files.repository.SchoolFileRepository;
import qa.hafez.hafez.organisation.model.SettingKeys;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.organisation.service.SettingsService;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * Stage 3 of the pipeline, "understanding": stores what the classifier decided about a file and handles
 * the human answer when it is unsure (FR-2.3).
 *
 * <ul>
 *   <li>Below the school's confidence threshold a file is flagged <b>Needs review</b>: a person decides.</li>
 *   <li>A profile a coordinator set by hand is never overwritten by the classifier.</li>
 *   <li>After a coordinator classifies a file, {@link FileEvents.Classified} makes the rule engine
 *       re-check it at once.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ClassificationService {

    /** A coordinator's answer for an unclear file. */
    public record ClassifyCommand(String docType, UUID subjectId, Sensitivity sensitivity,
                                  boolean hasStudentData, boolean hasHealthData, java.time.LocalDate examDate) {}

    private final SchoolFileRepository files;
    private final FileProfileRepository profiles;
    private final SubjectRepository subjects;
    private final SettingsService settings;
    private final AuditPublisher audit;
    private final ApplicationEventPublisher publisher;
    private final AccessPolicy policy;

    /** Stores the classifier's profile, flagging the file for review when confidence is low. */
    @Transactional
    public FileProfile apply(SchoolFile file, FileClassifier.Classification c) {
        FileProfile profile = profileFor(file);
        if (profile.getClassifiedBy() != null && !profile.isNeedsReview()) {
            return profile; // the coordinator's answer wins
        }
        double threshold = settings.getDouble(file.getSiteId(), SettingKeys.CONFIDENCE_THRESHOLD,
                SettingKeys.CONFIDENCE_THRESHOLD_DEFAULT);
        profile.setDocType(c.docType());
        profile.setHasStudentData(c.hasStudentData());
        profile.setHasHealthData(c.hasHealthData());
        profile.setSensitivity(c.sensitivity());
        profile.setConfidence(c.confidence());
        profile.setModelVersion(c.modelVersion());
        profile.setNeedsReview(c.confidence() < threshold);
        profile.getEvidence().clear();
        profile.getEvidence().addAll(c.evidence() == null ? List.of() : c.evidence());
        profiles.save(profile);
        if (c.examDate() != null && file.getExamDate() == null) {
            file.setExamDate(c.examDate()); // a date set by a coordinator is never overwritten by the checker
        }
        file.setSensitivity(profile.getSensitivity());
        return profile;
    }

    /** The file could not be read: it goes to "Needs review". A Restricted file is never reported fine. */
    @Transactional
    public FileProfile markUnreadable(SchoolFile file) {
        FileProfile profile = profileFor(file);
        profile.setNeedsReview(true);
        profile.setConfidence(0);
        profile.getEvidence().clear();
        profile.getEvidence().add("The file could not be read; a person needs to classify it.");
        return profiles.save(profile);
    }

    /** A coordinator answers a "Needs review" file. The file is re-checked straight away (via an event). */
    @Transactional
    public FileProfile classifyManually(CurrentUser user, UUID fileId, ClassifyCommand cmd) {
        SchoolFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("The file"));
        policy.requireView(user, file.scope(), "The file");
        policy.requireRole(user, Role.COORDINATOR);
        policy.requireCoordinator(user, file.scope());
        if (cmd.docType() == null || cmd.docType().isBlank() || cmd.sensitivity() == null) {
            throw new BadRequestException("Please choose the document type and the sensitivity.");
        }
        if (cmd.subjectId() != null) {
            Subject subject = subjects.findById(cmd.subjectId())
                    .filter(s -> s.getSiteId().equals(file.getSiteId()))
                    .orElseThrow(() -> new BadRequestException("That subject does not belong to this school."));
            file.setSubjectId(subject.getId());
        }

        FileProfile profile = profileFor(file);
        String before = profile.getDocType() + "/" + profile.getSensitivity();
        profile.setDocType(cmd.docType().trim());
        profile.setSubjectId(file.getSubjectId());
        profile.setSensitivity(cmd.sensitivity());
        profile.setHasStudentData(cmd.hasStudentData());
        profile.setHasHealthData(cmd.hasHealthData());
        if (cmd.examDate() != null) {
            file.setExamDate(cmd.examDate());
        }
        profile.setConfidence(1.0);
        profile.setNeedsReview(false);
        profile.setClassifiedBy(user.personId());
        profile.setModelVersion("manual");
        profile.getEvidence().clear();
        profile.getEvidence().add("Classified by the subject coordinator");
        profiles.save(profile);
        file.setSensitivity(cmd.sensitivity());

        audit.log(file.getOrgId(), file.getSiteId(), ActivityActions.FILE_CLASSIFIED)
                .by(user).on("File", file.getId()).subject(file.getSubjectId())
                .change(before, profile.getDocType() + "/" + profile.getSensitivity()).save();
        publisher.publishEvent(new FileEvents.Classified(file.getId()));
        return profile;
    }

    /** Files waiting for the coordinator to classify (FR-2.3). */
    @Transactional(readOnly = true)
    public List<FileProfile> reviewQueue(CurrentUser user) {
        policy.requireRole(user, Role.COORDINATOR);
        if (user.subjectId() == null) {
            return profiles.findBySiteIdAndNeedsReviewTrue(user.siteId());
        }
        List<FileProfile> queue = new ArrayList<>(
                profiles.findBySiteIdAndSubjectIdAndNeedsReviewTrue(user.siteId(), user.subjectId()));
        queue.addAll(profiles.findBySiteIdAndSubjectIdIsNullAndNeedsReviewTrue(user.siteId()));
        return queue;
    }

    private FileProfile profileFor(SchoolFile file) {
        return profiles.findByFileId(file.getId()).orElseGet(() -> {
            FileProfile p = new FileProfile();
            p.setOrgId(file.getOrgId());
            p.setSiteId(file.getSiteId());
            p.setFileId(file.getId());
            p.setSubjectId(file.getSubjectId());
            return p;
        });
    }
}
