package qa.hafez.hafez.academic.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.service.MaterialService;
import qa.hafez.hafez.academic.service.NoticeService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Notices and study materials of the school portal. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService notices;
    private final MaterialService materials;

    @GetMapping("/notices")
    public List<AcademicDtos.NoticeView> current(CurrentUser user) {
        return notices.current(user);
    }

    @GetMapping("/notices/manage")
    public List<AcademicDtos.NoticeView> manage(CurrentUser user) {
        return notices.manage(user);
    }

    @PostMapping("/notices")
    public AcademicDtos.NoticeView create(CurrentUser user, @Valid @RequestBody AcademicDtos.NoticeRequest request) {
        return notices.create(user, request);
    }

    @DeleteMapping("/notices/{noticeId}")
    public void remove(CurrentUser user, @PathVariable UUID noticeId) {
        notices.remove(user, noticeId);
    }

    @GetMapping("/materials")
    public List<AcademicDtos.MaterialView> materials(CurrentUser user,
                                                     @org.springframework.web.bind.annotation.RequestParam(required = false) UUID sectionId) {
        return materials.list(user, sectionId);
    }

    @PostMapping("/materials")
    public AcademicDtos.MaterialView addMaterial(CurrentUser user, @Valid @RequestBody AcademicDtos.MaterialRequest request) {
        return materials.add(user, request.sectionId(), request.subjectId(), request.title(), request.link());
    }

    @DeleteMapping("/materials/{materialId}")
    public void removeMaterial(CurrentUser user, @PathVariable UUID materialId) {
        materials.remove(user, materialId);
    }
}
