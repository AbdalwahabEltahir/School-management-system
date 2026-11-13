package qa.sijill.sijill.people.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.people.dto.PeopleDtos;
import qa.sijill.sijill.people.service.PeopleService;
import qa.sijill.sijill.shared.security.CurrentUser;

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
        return PeopleDtos.MeView.from(people.updatePreferences(user, request.language(), request.digestMode(), request.muted()));
    }

    /** Personal details: name and contact number. */
    @PutMapping("/profile")
    public PeopleDtos.MeView updateProfile(CurrentUser user, @RequestBody PeopleDtos.ProfileRequest request) {
        return PeopleDtos.MeView.from(people.updateProfile(user, request.displayName(), request.phone()));
    }

    @PutMapping("/photo")
    public PeopleDtos.MeView setPhoto(CurrentUser user, @RequestBody PeopleDtos.PhotoRequest request) {
        return PeopleDtos.MeView.from(people.setPhoto(user, request.data()));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/photo")
    public PeopleDtos.MeView clearPhoto(CurrentUser user) {
        return PeopleDtos.MeView.from(people.clearPhoto(user));
    }

    /** The person's own photo (never anyone else's). */
    @GetMapping("/photo")
    public org.springframework.http.ResponseEntity<byte[]> photo(CurrentUser user) {
        var p = people.me(user);
        if (p.getPhoto() == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(p.getPhotoType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-cache")
                .body(p.getPhoto());
    }
}
