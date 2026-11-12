package qa.sijill.sijill.academic.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.dto.AcademicDtos.MaterialView;
import qa.sijill.sijill.academic.model.Section;
import qa.sijill.sijill.academic.model.StudyMaterial;
import qa.sijill.sijill.academic.repository.StudyMaterialRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Study materials (SMS FR-037): a teacher shares an https link (a Drive, Dropbox or YouTube link) with a section for
 * one subject, and that section's students can open it. The link is stored, never the file, and Hafez never changes
 * the linked file or its sharing (INT-007).
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final StudyMaterialRepository materials;
    private final AcademicAccess access;
    private final AuditPublisher audit;

    @Transactional
    public MaterialView add(CurrentUser user, UUID sectionId, UUID subjectId, String title, String link) {
        Section section = access.section(user, sectionId);
        if (!access.teaches(user, sectionId, subjectId)) {
            throw new ForbiddenException("Only the teacher of this subject for this section can share materials.");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Please give the material a title.");
        }
        String url = link == null ? "" : link.trim();
        if (!isHttps(url)) {
            throw new BadRequestException("The link must start with https://");
        }
        StudyMaterial m = new StudyMaterial();
        m.setOrgId(user.orgId());
        m.setSiteId(user.siteId());
        m.setSectionId(sectionId);
        m.setSubjectId(subjectId);
        m.setTitle(title.trim());
        m.setLink(url);
        m.setCreatedBy(user.personId());
        materials.save(m);
        audit.log(user.orgId(), user.siteId(), ActivityActions.MATERIAL_ADDED).by(user).on("Section", sectionId)
                .subject(subjectId).change(null, m.getTitle() + " for " + AcademicAccess.label(section)).save();
        return views(user.siteId(), List.of(m)).get(0);
    }

    /** A teacher sees what they shared; a student sees their section's; school staff see a section's. */
    @Transactional(readOnly = true)
    public List<MaterialView> list(CurrentUser user, UUID sectionId) {
        List<StudyMaterial> rows;
        if (user.is(Role.STUDENT)) {
            rows = materials.findBySectionIdOrderByCreatedAtDesc(access.studentSection(user).getId());
        } else if (user.isAny(Role.TEACHER, Role.COORDINATOR)) {
            rows = (sectionId == null ? materials.findBySiteIdOrderByCreatedAtDesc(user.siteId())
                    : materials.findBySectionIdOrderByCreatedAtDesc(access.section(user, sectionId).getId())).stream()
                    .filter(m -> access.teaches(user, m.getSectionId(), m.getSubjectId())).toList();
        } else if (user.isAny(Role.SCHOOL_IT, Role.PRINCIPAL)) {
            rows = sectionId == null ? materials.findBySiteIdOrderByCreatedAtDesc(user.siteId())
                    : materials.findBySectionIdOrderByCreatedAtDesc(access.section(user, sectionId).getId());
        } else {
            throw new ForbiddenException("Study materials are not available for your role.");
        }
        return views(user.siteId(), rows);
    }

    @Transactional
    public void remove(CurrentUser user, UUID materialId) {
        StudyMaterial m = materials.findById(materialId).filter(x -> x.getSiteId().equals(user.siteId()))
                .orElseThrow(() -> new NotFoundException("The material"));
        if (!user.is(Role.SCHOOL_IT) && !m.getCreatedBy().equals(user.personId())) {
            throw new ForbiddenException("Only the teacher who shared it, or School IT, can remove a material.");
        }
        materials.delete(m);
    }

    List<MaterialView> views(UUID siteId, List<StudyMaterial> rows) {
        Map<UUID, Section> sectionById = access.sectionsById(siteId);
        Map<UUID, String> subjectNames = access.subjectNames(siteId);
        Map<UUID, String> teachers = access.personNames(rows.stream().map(StudyMaterial::getCreatedBy).toList());
        return rows.stream().map(m -> new MaterialView(m.getId(), m.getSectionId(),
                AcademicAccess.label(sectionById.get(m.getSectionId())), m.getSubjectId(),
                subjectNames.getOrDefault(m.getSubjectId(), ""), m.getTitle(), m.getLink(),
                teachers.getOrDefault(m.getCreatedBy(), ""), m.getCreatedAt())).toList();
    }

    private static boolean isHttps(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
