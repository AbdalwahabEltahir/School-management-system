package qa.sijill.sijill.askhafez;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.askhafez.AskHafezDtos.AskRequest;
import qa.sijill.sijill.askhafez.AskHafezDtos.ChildSetting;
import qa.sijill.sijill.askhafez.AskHafezDtos.RatingRequest;
import qa.sijill.sijill.askhafez.AskHafezDtos.Reply;
import qa.sijill.sijill.askhafez.AskHafezDtos.Status;
import qa.sijill.sijill.askhafez.AskHafezDtos.SwitchRequest;
import qa.sijill.sijill.shared.security.CurrentUser;

/** The floating "Ask Hafez" helper. */
@RestController
@RequestMapping("/api/ask")
@RequiredArgsConstructor
public class AskHafezController {

    private final AskHafezService service;

    @GetMapping("/status")
    public Status status(CurrentUser user) {
        return service.status(user);
    }

    @PostMapping
    public Reply ask(CurrentUser user, @RequestBody AskRequest request) {
        return service.ask(user, request.question());
    }

    @PostMapping("/{id}/rating")
    public void rate(CurrentUser user, @PathVariable UUID id, @RequestBody RatingRequest request) {
        service.rate(user, id, Boolean.TRUE.equals(request.up()));
    }

    @PutMapping("/children/{studentId}")
    public List<ChildSetting> setChild(CurrentUser user, @PathVariable UUID studentId, @RequestBody SwitchRequest request) {
        return service.setChild(user, studentId, Boolean.TRUE.equals(request.enabled()));
    }
}
