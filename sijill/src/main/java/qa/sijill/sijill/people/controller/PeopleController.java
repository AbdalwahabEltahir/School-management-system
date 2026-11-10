package qa.sijill.sijill.people.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.people.dto.PeopleDtos;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * The People screen (FR-11.5): list, School IT overrides, welcome messages, directory sync, coordinator
 * follow-ups, and the directory-group → role mapping. Permissions are enforced in {@code PeopleService}.
 */
@RestController
@RequestMapping("/api/people")
@RequiredArgsConstructor
public class PeopleController {

    private final PeopleService people;

    @GetMapping
    public List<PeopleDtos.PersonView> list(CurrentUser user) {
        return people.list(user).stream().map(PeopleDtos.PersonView::from).toList();
    }

    /** School IT adds a person so they can sign in with their own account. */
    @PostMapping
    public PeopleDtos.PersonView add(CurrentUser user, @Valid @RequestBody PeopleDtos.AddPersonRequest request) {
        return PeopleDtos.PersonView.from(people.add(user, request.email(), request.name(), request.role(), request.subjectId()));
    }

    /** School IT changes role, status, subject or the generic-account flag (an override). */
    @PatchMapping("/{personId}")
    public PeopleDtos.PersonView update(CurrentUser user, @PathVariable UUID personId,
                                        @RequestBody PeopleDtos.UpdatePersonRequest request) {
        return PeopleDtos.PersonView.from(people.update(user, personId, request.toCommand()));
    }

    @PostMapping("/{personId}/welcome")
    public PeopleDtos.PersonView sendWelcome(CurrentUser user, @PathVariable UUID personId) {
        return PeopleDtos.PersonView.from(people.sendWelcome(user, personId));
    }

    /** A coordinator marks a person as followed up. */
    @PostMapping("/{personId}/follow-up")
    public void followUp(CurrentUser user, @PathVariable UUID personId, @RequestBody PeopleDtos.FollowUpRequest request) {
        people.recordFollowUp(user, personId, request.note());
    }

    /** Reads the school's directory (read-only) and updates the people table. */
    @PostMapping("/sync")
    public PeopleDtos.SyncResult sync(CurrentUser user) {
        return new PeopleDtos.SyncResult(people.syncDirectory(user));
    }

    @GetMapping("/role-groups")
    public List<PeopleDtos.RoleGroupView> roleGroups(CurrentUser user) {
        return people.listRoleGroups(user).stream().map(PeopleDtos.RoleGroupView::from).toList();
    }

    @PutMapping("/role-groups")
    public PeopleDtos.RoleGroupView saveRoleGroup(CurrentUser user, @Valid @RequestBody PeopleDtos.RoleGroupRequest request) {
        return PeopleDtos.RoleGroupView.from(people.saveRoleGroup(user, request.directoryGroup(), request.role()));
    }
}
