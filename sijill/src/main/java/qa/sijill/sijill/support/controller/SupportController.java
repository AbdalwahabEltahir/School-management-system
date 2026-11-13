package qa.sijill.sijill.support.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.support.dto.SupportDtos;
import qa.sijill.sijill.support.model.TicketAttachment;
import qa.sijill.sijill.support.service.SupportService;

/** Support tickets between schools and EAA head office. Who may do what is decided in {@link SupportService}. */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService support;

    @GetMapping("/tickets")
    public List<SupportDtos.TicketView> list(CurrentUser user) {
        return support.list(user);
    }

    @PostMapping("/tickets")
    public SupportDtos.TicketView create(CurrentUser user, @RequestBody SupportDtos.CreateRequest request) {
        return support.create(user, request);
    }

    @GetMapping("/tickets/{id}")
    public SupportDtos.TicketDetail get(CurrentUser user, @PathVariable UUID id) {
        return support.get(user, id);
    }

    @PostMapping("/tickets/{id}/replies")
    public SupportDtos.ReplyView reply(CurrentUser user, @PathVariable UUID id, @RequestBody SupportDtos.ReplyRequest request) {
        return support.reply(user, id, request.body());
    }

    @PostMapping("/tickets/{id}/assign")
    public SupportDtos.TicketView assign(CurrentUser user, @PathVariable UUID id, @RequestBody SupportDtos.AssignRequest request) {
        return support.assign(user, id, request.assigneeId());
    }

    @PostMapping("/tickets/{id}/status")
    public SupportDtos.TicketView status(CurrentUser user, @PathVariable UUID id, @RequestBody SupportDtos.StatusRequest request) {
        return support.setStatus(user, id, request.status());
    }

    @GetMapping("/assignees")
    public List<SupportDtos.Assignee> assignees(CurrentUser user) {
        return support.assignees(user);
    }

    /** The attachment is always a download, never shown inside the page. */
    @GetMapping("/tickets/{id}/attachment")
    public ResponseEntity<byte[]> attachment(CurrentUser user, @PathVariable UUID id) {
        String name = support.get(user, id).ticket().attachmentName();
        TicketAttachment a = support.attachment(user, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name == null ? "attachment" : name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .body(a.getData());
    }
}
