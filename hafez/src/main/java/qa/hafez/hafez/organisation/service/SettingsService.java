package qa.hafez.hafez.organisation.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.organisation.model.DocType;
import qa.hafez.hafez.organisation.model.Setting;
import qa.hafez.hafez.organisation.model.Subject;
import qa.hafez.hafez.organisation.repository.DocTypeRepository;
import qa.hafez.hafez.organisation.repository.SettingRepository;
import qa.hafez.hafez.organisation.repository.SubjectRepository;
import qa.hafez.hafez.shared.events.ActivityActions;
import qa.hafez.hafez.shared.events.AuditPublisher;
import qa.hafez.hafez.shared.exception.BadRequestException;
import qa.hafez.hafez.shared.exception.NotFoundException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.model.Sensitivity;
import qa.hafez.hafez.shared.security.AccessPolicy;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * Per-school configuration (FR-8.3 – FR-8.5, design principle 7: configuration, not code): settings,
 * subjects and document types. Other modules read settings through the typed getters
 * ({@link #getInt}, {@link #getDouble}); School IT changes them through the user-facing methods, which
 * are audited ("every setting change is logged", FR-5.3).
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingRepository settings;
    private final SubjectRepository subjects;
    private final DocTypeRepository docTypes;
    private final AuditPublisher audit;
    private final AccessPolicy policy;

    // ================================================================== typed reads (for other modules)

    @Transactional(readOnly = true)
    public String get(UUID siteId, String key, String defaultValue) {
        return settings.findBySiteIdAndSettingKey(siteId, key).map(Setting::getSettingValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public double getDouble(UUID siteId, String key, String defaultValue) {
        try {
            return Double.parseDouble(get(siteId, key, defaultValue));
        } catch (NumberFormatException e) {
            return Double.parseDouble(defaultValue); // a bad value must never stop a check
        }
    }

    @Transactional(readOnly = true)
    public int getInt(UUID siteId, String key, String defaultValue) {
        try {
            return Integer.parseInt(get(siteId, key, defaultValue));
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaultValue);
        }
    }

    // ================================================================== settings (School IT)

    @Transactional(readOnly = true)
    public List<Setting> listSettings(CurrentUser user) {
        policy.requireRole(user, Role.SCHOOL_IT);
        return settings.findBySiteId(user.siteId());
    }

    /** Creates or replaces a setting of the user's school; the change is audited. */
    @Transactional
    public Setting change(CurrentUser user, String key, String value) {
        policy.requireRole(user, Role.SCHOOL_IT);
        if (key == null || key.isBlank() || value == null) {
            throw new BadRequestException("Please give the setting name and value.");
        }
        Setting setting = settings.findBySiteIdAndSettingKey(user.siteId(), key).orElseGet(() -> {
            Setting s = new Setting();
            s.setOrgId(user.orgId());
            s.setSiteId(user.siteId());
            s.setSettingKey(key);
            return s;
        });
        String before = setting.getSettingValue();
        setting.setSettingValue(value);
        settings.save(setting);
        audit.log(user.orgId(), user.siteId(), ActivityActions.SETTING_CHANGED).by(user)
                .on("Setting", setting.getId()).change(key + "=" + before, key + "=" + value).save();
        return setting;
    }

    // ================================================================== subjects

    /** Subjects of the user's school (every role can read them: they appear in drop-downs). */
    @Transactional(readOnly = true)
    public List<Subject> listSubjects(CurrentUser user) {
        return subjects.findBySiteIdOrderByName(user.siteId());
    }

    /** Adds a subject, or changes its name/coordinator when {@code subjectId} is given. The coordinator only. */
    @Transactional
    public Subject saveSubject(CurrentUser user, UUID subjectId, String name, UUID coordinatorId) {
        policy.requireRole(user, Role.COORDINATOR);
        Subject subject;
        if (subjectId == null) {
            if (name == null || name.isBlank()) {
                throw new BadRequestException("Please give the subject a name.");
            }
            subject = new Subject();
            subject.setOrgId(user.orgId());
            subject.setSiteId(user.siteId());
        } else {
            subject = subjects.findById(subjectId).filter(s -> s.getSiteId().equals(user.siteId()))
                    .orElseThrow(() -> new NotFoundException("The subject"));
        }
        if (name != null && !name.isBlank()) {
            subject.setName(name.trim());
        }
        if (coordinatorId != null) {
            subject.setCoordinatorId(coordinatorId);
        }
        subjects.save(subject);
        audit.log(user.orgId(), user.siteId(), ActivityActions.SUBJECT_CHANGED).by(user)
                .on("Subject", subject.getId()).change(null, subject.getName()).save();
        return subject;
    }

    // ================================================================== document types

    @Transactional(readOnly = true)
    public List<DocType> listDocTypes(CurrentUser user) {
        return docTypes.findByOrgIdOrderByName(user.orgId());
    }

    /**
     * Adds a document type or changes its default sensitivity in one click (FR-8.4). A <i>new</i> type is
     * not trusted by the checker until it has labelled examples ({@link #trustDocType}).
     */
    @Transactional
    public DocType saveDocType(CurrentUser user, String name, Sensitivity sensitivity) {
        policy.requireRole(user, Role.COORDINATOR);
        if (name == null || name.isBlank() || sensitivity == null) {
            throw new BadRequestException("Please give the type a name and a sensitivity.");
        }
        DocType type = docTypes.findByOrgIdAndNameIgnoreCase(user.orgId(), name.trim()).orElseGet(() -> {
            DocType d = new DocType();
            d.setOrgId(user.orgId());
            d.setName(name.trim());
            d.setTrusted(false);
            return d;
        });
        String before = type.getDefaultSensitivity() == null ? null : type.getDefaultSensitivity().name();
        type.setDefaultSensitivity(sensitivity);
        docTypes.save(type);
        audit.log(user.orgId(), user.siteId(), ActivityActions.DOC_TYPE_CHANGED).by(user)
                .on("DocType", type.getId()).change(before, sensitivity.name()).save();
        return type;
    }

    /** Marks a type as trusted once it has labelled examples. */
    @Transactional
    public DocType trustDocType(CurrentUser user, UUID docTypeId) {
        policy.requireRole(user, Role.COORDINATOR);
        DocType type = docTypes.findById(docTypeId).filter(d -> d.getOrgId().equals(user.orgId()))
                .orElseThrow(() -> new NotFoundException("The document type"));
        type.setTrusted(true);
        audit.log(user.orgId(), user.siteId(), ActivityActions.DOC_TYPE_CHANGED).by(user)
                .on("DocType", type.getId()).change("untrusted", "trusted").save();
        return type;
    }
}
