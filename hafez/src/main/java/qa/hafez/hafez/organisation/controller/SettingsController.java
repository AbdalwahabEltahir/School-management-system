package qa.hafez.hafez.organisation.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.organisation.dto.OrganisationDtos;
import qa.hafez.hafez.organisation.service.SettingsService;
import qa.hafez.hafez.organisation.service.SiteService;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * Ordinary configuration (FR-8.3 – FR-8.5): sites, subjects, document types and settings. The risky
 * connection actions are in {@code ConnectionController}; backup in {@code BackupController}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteService sites;
    private final SettingsService settings;

    // ---- sites ----
    @GetMapping("/sites")
    public List<OrganisationDtos.SiteView> sites(CurrentUser user) {
        return sites.list(user).stream().map(OrganisationDtos.SiteView::from).toList();
    }

    @PostMapping("/sites")
    public OrganisationDtos.SiteView createSite(CurrentUser user, @RequestBody OrganisationDtos.SiteRequest request) {
        return OrganisationDtos.SiteView.from(sites.create(user, request.toCommand()));
    }

    @PutMapping("/sites/{siteId}")
    public OrganisationDtos.SiteView updateSite(CurrentUser user, @PathVariable UUID siteId,
                                                @RequestBody OrganisationDtos.SiteRequest request) {
        return OrganisationDtos.SiteView.from(sites.update(user, siteId, request.toCommand()));
    }

    @DeleteMapping("/sites/{siteId}")
    public void removeSite(CurrentUser user, @PathVariable UUID siteId) {
        sites.remove(user, siteId);
    }

    // ---- subjects ----
    @GetMapping("/subjects")
    public List<OrganisationDtos.SubjectView> subjects(CurrentUser user) {
        return settings.listSubjects(user).stream().map(OrganisationDtos.SubjectView::from).toList();
    }

    @PostMapping("/subjects")
    public OrganisationDtos.SubjectView addSubject(CurrentUser user, @Valid @RequestBody OrganisationDtos.SubjectRequest request) {
        return OrganisationDtos.SubjectView.from(settings.saveSubject(user, null, request.name(), request.coordinatorId()));
    }

    @PutMapping("/subjects/{subjectId}")
    public OrganisationDtos.SubjectView updateSubject(CurrentUser user, @PathVariable UUID subjectId,
                                                      @Valid @RequestBody OrganisationDtos.SubjectRequest request) {
        return OrganisationDtos.SubjectView.from(settings.saveSubject(user, subjectId, request.name(), request.coordinatorId()));
    }

    // ---- document types ----
    @GetMapping("/doc-types")
    public List<OrganisationDtos.DocTypeView> docTypes(CurrentUser user) {
        return settings.listDocTypes(user).stream().map(OrganisationDtos.DocTypeView::from).toList();
    }

    /** Adds a type or changes its default sensitivity in one click. */
    @PutMapping("/doc-types")
    public OrganisationDtos.DocTypeView saveDocType(CurrentUser user, @Valid @RequestBody OrganisationDtos.DocTypeRequest request) {
        return OrganisationDtos.DocTypeView.from(settings.saveDocType(user, request.name(), request.sensitivity()));
    }

    /** The checker may trust the type once it has labelled examples. */
    @PostMapping("/doc-types/{docTypeId}/trust")
    public OrganisationDtos.DocTypeView trust(CurrentUser user, @PathVariable UUID docTypeId) {
        return OrganisationDtos.DocTypeView.from(settings.trustDocType(user, docTypeId));
    }

    // ---- settings ----
    @GetMapping("/settings")
    public List<OrganisationDtos.SettingView> settings(CurrentUser user) {
        return settings.listSettings(user).stream().map(OrganisationDtos.SettingView::from).toList();
    }

    @PutMapping("/settings")
    public OrganisationDtos.SettingView change(CurrentUser user, @Valid @RequestBody OrganisationDtos.SettingRequest request) {
        return OrganisationDtos.SettingView.from(settings.change(user, request.key(), request.value()));
    }
}
