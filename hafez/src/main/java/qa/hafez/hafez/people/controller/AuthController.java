package qa.hafez.hafez.people.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.people.dto.PeopleDtos;
import qa.hafez.hafez.people.service.PeopleService;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * "Who am I" and "My preferences" (FR-11.2). Sign-in itself is handled by Spring Security (OpenID
 * Connect against the school account); this controller only exposes the signed-in person.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class AuthController {

    private final PeopleService people;

    /** The signed-in person: name, role, school, language and summary mode. */
    @GetMapping
    public PeopleDtos.MeView me(CurrentUser user) {
        return PeopleDtos.MeView.from(people.me(user));
    }

    /** Saves message language and per-issue / daily summary at once. */
    @PutMapping("/preferences")
    public PeopleDtos.MeView updatePreferences(CurrentUser user, @RequestBody PeopleDtos.PreferencesRequest request) {
        return PeopleDtos.MeView.from(people.updatePreferences(user, request.language(), request.digestMode()));
    }
}
