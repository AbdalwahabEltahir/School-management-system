package qa.sijill.sijill.network;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.network.NetworkDtos.CreateItRequest;
import qa.sijill.sijill.network.NetworkDtos.ItAccount;
import qa.sijill.sijill.network.NetworkDtos.ItStatusRequest;
import qa.sijill.sijill.network.NetworkDtos.LogPage;
import qa.sijill.sijill.network.NetworkDtos.School;
import qa.sijill.sijill.network.NetworkDtos.SchoolDetail;
import qa.sijill.sijill.network.NetworkDtos.StatusRequest;
import qa.sijill.sijill.shared.security.CurrentUser;

/** The EAA IT network screens: schools with head counts, School IT accounts, and the searchable log. */
@RestController
@RequestMapping("/api/network")
@RequiredArgsConstructor
public class NetworkController {

    private final NetworkService service;

    @GetMapping("/schools")
    public List<School> schools(CurrentUser user) {
        return service.schools(user);
    }

    @GetMapping("/schools/{id}")
    public SchoolDetail school(CurrentUser user, @PathVariable UUID id) {
        return service.school(user, id);
    }

    @PutMapping("/schools/{id}/status")
    public SchoolDetail setSchoolStatus(CurrentUser user, @PathVariable UUID id, @RequestBody StatusRequest request) {
        return service.setSchoolActive(user, id, Boolean.TRUE.equals(request.active()));
    }

    @GetMapping("/it-accounts")
    public List<ItAccount> itAccounts(CurrentUser user) {
        return service.itAccounts(user);
    }

    @PostMapping("/it-accounts")
    public ItAccount createIt(CurrentUser user, @RequestBody CreateItRequest request) {
        return service.createIt(user, request);
    }

    @PutMapping("/it-accounts/{id}/status")
    public ItAccount setItStatus(CurrentUser user, @PathVariable UUID id, @RequestBody ItStatusRequest request) {
        return service.setItStatus(user, id, request.status());
    }

    @DeleteMapping("/it-accounts/{id}")
    public void deleteIt(CurrentUser user, @PathVariable UUID id) {
        service.deleteIt(user, id);
    }

    @GetMapping("/log")
    public LogPage log(CurrentUser user,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) UUID school, @RequestParam(required = false) String action,
                       @RequestParam(required = false) String area, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.log(user, from, to, school, action, area, q, page, size);
    }
}
