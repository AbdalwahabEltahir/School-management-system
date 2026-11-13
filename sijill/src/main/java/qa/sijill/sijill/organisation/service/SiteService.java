package qa.sijill.sijill.organisation.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.organisation.repository.SiteRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ConflictException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.AccessPolicy;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Sites: the schools and the head office (FR-8.3).
 *
 * <ul>
 *   <li><b>Add / remove a site</b> is a network-level decision: Leadership only. A new site shows
 *       "Waiting for first check" until its first check has run. Removing a site keeps its history and
 *       never touches its drive; the head office cannot be removed.</li>
 *   <li><b>Edit</b> a site (name, principal, coordinator, counted in scores): School IT of that school,
 *       or Leadership.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SiteService {

    /** Fields of a site that can be changed. Null fields are left as they are. */
    public record SiteCommand(String name, String domain, Boolean headOffice, Boolean scored,
                              UUID principalId, UUID coordinatorId) {}

    /** Test installations only: lets Leadership score the head office, so a one-site setup has a dashboard. */
    @org.springframework.beans.factory.annotation.Value("${sijill.scoring.allow-head-office:false}")
    private boolean allowHeadOfficeScoring;

    private final SiteRepository sites;
    private final AuditPublisher audit;
    private final AccessPolicy policy;

    /** Leadership sees every site; everybody else sees their own. */
    @Transactional(readOnly = true)
    public List<Site> list(CurrentUser user) {
        if (user.isNetwork()) {
            return sites.findByOrgIdOrderByName(user.orgId());
        }
        return sites.findById(user.siteId()).map(List::of).orElse(List.of());
    }

    @Transactional
    public Site create(CurrentUser user, SiteCommand cmd) {
        policy.requireRole(user, Role.EAA_ADMIN);
        if (cmd.name() == null || cmd.name().isBlank() || cmd.domain() == null || cmd.domain().isBlank()) {
            throw new BadRequestException("Please give the school a name and its workspace domain.");
        }
        if (sites.findByDomainIgnoreCase(cmd.domain().trim()).isPresent()) {
            throw new ConflictException("A school with this domain already exists.");
        }
        Site site = new Site();
        site.setOrgId(user.orgId());
        site.setName(cmd.name().trim());
        site.setDomain(cmd.domain().trim().toLowerCase());
        site.setHeadOffice(Boolean.TRUE.equals(cmd.headOffice()));
        site.setScored(!site.isHeadOffice() && !Boolean.FALSE.equals(cmd.scored())); // head office is unscored
        site.setPrincipalId(cmd.principalId());
        site.setCoordinatorId(cmd.coordinatorId());
        sites.save(site);
        audit.log(site.getOrgId(), user.siteId(), ActivityActions.SITE_CREATED).by(user).on("Site", site.getId())
                .change(null, site.getName()).save();
        return site;
    }

    @Transactional
    public Site update(CurrentUser user, UUID siteId, SiteCommand cmd) {
        Site site = sites.findById(siteId).filter(s -> s.getOrgId().equals(user.orgId()))
                .orElseThrow(() -> new NotFoundException("The school"));
        policy.requireRole(user, Role.EAA_ADMIN);
        String before = site.getName();
        if (cmd.name() != null && !cmd.name().isBlank()) {
            site.setName(cmd.name().trim());
        }
        // Leadership may say that the "head office" is really a school (a test setup has only one workspace).
        // It then counts in the KPIs and the planning review like any school.
        if (Boolean.FALSE.equals(cmd.headOffice()) && site.isHeadOffice()) {
            policy.requireRole(user, Role.EAA_ADMIN);
            site.setHeadOffice(false);
            site.setScored(cmd.scored() == null || cmd.scored());
        } else if (cmd.scored() != null) {
            if (site.isHeadOffice() && !allowHeadOfficeScoring) {
                throw new ConflictException("The head office is not scored. Turn on sijill.scoring.allow-head-office "
                        + "for a test installation that has no school site yet.");
            }
            site.setScored(cmd.scored());
        }
        if (cmd.principalId() != null) {
            site.setPrincipalId(cmd.principalId());
        }
        if (cmd.coordinatorId() != null) {
            site.setCoordinatorId(cmd.coordinatorId());
        }
        audit.log(site.getOrgId(), user.siteId(), ActivityActions.SITE_UPDATED).by(user).on("Site", site.getId())
                .change(before, site.getName()).save();
        return site;
    }

    /**
     * Removes a site from use (EAA administrators only, logged). History is kept and the drive is untouched.
     */
    @Transactional
    public void remove(CurrentUser user, UUID siteId) {
        policy.requireRole(user, Role.EAA_ADMIN);
        Site site = sites.findById(siteId).filter(s -> s.getOrgId().equals(user.orgId()))
                .orElseThrow(() -> new NotFoundException("The school"));
        if (site.isHeadOffice()) {
            throw new ConflictException("The head office cannot be removed.");
        }
        executeRemove(user, siteId);
    }

    /** Carries out an approved removal (called by the approval handler; permissions were checked there). */
    @Transactional
    public void executeRemove(CurrentUser approver, UUID siteId) {
        Site site = sites.findById(siteId).filter(s -> s.getOrgId().equals(approver.orgId()))
                .orElseThrow(() -> new NotFoundException("The school"));
        site.setActive(false);
        audit.log(site.getOrgId(), approver.siteId(), ActivityActions.SITE_REMOVED).by(approver).on("Site", site.getId())
                .change(site.getName(), "removed").save();
    }
}
