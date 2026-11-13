package qa.sijill.sijill.pickup.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.pickup.dto.PickupDtos;
import qa.sijill.sijill.pickup.service.PickupService;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Student pick-up and collection. Who may do what is decided in {@link PickupService}. */
@RestController
@RequestMapping("/api/pickup")
@RequiredArgsConstructor
public class PickupController {

    private final PickupService pickup;

    // ---- parent
    @GetMapping("/mine")
    public List<PickupDtos.ChildPickup> mine(CurrentUser user) {
        return pickup.mine(user);
    }

    @GetMapping("/children/{studentId}/collectors")
    public List<PickupDtos.CollectorView> collectors(CurrentUser user, @PathVariable UUID studentId) {
        return pickup.collectorsOf(user, studentId);
    }

    @PostMapping("/children/{studentId}/collectors")
    public PickupDtos.CollectorView addCollector(CurrentUser user, @PathVariable UUID studentId, @RequestBody PickupDtos.CollectorRequest request) {
        return pickup.addCollector(user, studentId, request);
    }

    @DeleteMapping("/collectors/{collectorId}")
    public void removeCollector(CurrentUser user, @PathVariable UUID collectorId) {
        pickup.removeCollector(user, collectorId);
    }

    @PostMapping("/arrive")
    public PickupDtos.RequestView arrive(CurrentUser user, @RequestBody PickupDtos.ArriveRequest request) {
        return pickup.arrive(user, request.studentId(), request.collectorId());
    }

    @PostMapping("/requests/{id}/cancel")
    public PickupDtos.RequestView cancel(CurrentUser user, @PathVariable UUID id) {
        return pickup.cancel(user, id);
    }

    // ---- staff
    @GetMapping("/board")
    public PickupDtos.Board board(CurrentUser user) {
        return pickup.board(user);
    }

    @PostMapping("/requests/{id}/verify")
    public PickupDtos.RequestView verify(CurrentUser user, @PathVariable UUID id) {
        return pickup.verify(user, id);
    }

    @PostMapping("/requests/{id}/release")
    public PickupDtos.RequestView release(CurrentUser user, @PathVariable UUID id) {
        return pickup.release(user, id);
    }

    @PostMapping("/requests/{id}/decline")
    public PickupDtos.RequestView decline(CurrentUser user, @PathVariable UUID id, @RequestBody PickupDtos.DeclineRequest request) {
        return pickup.decline(user, id, request.reason());
    }

    // ---- the chain-of-custody log (staff: the school; parent: their children)
    @GetMapping("/log")
    public List<PickupDtos.ReleaseView> log(CurrentUser user, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return pickup.log(user, from, to);
    }
}
