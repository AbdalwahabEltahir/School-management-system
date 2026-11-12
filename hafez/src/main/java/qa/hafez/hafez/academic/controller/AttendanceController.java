package qa.hafez.hafez.academic.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.hafez.hafez.academic.dto.AcademicDtos;
import qa.hafez.hafez.academic.service.AcademicAccess;
import qa.hafez.hafez.academic.service.AttendanceService;
import qa.hafez.hafez.shared.security.CurrentUser;

/** Attendance: a teacher records their section's day, a student reads their own record, School IT summarises. */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendance;
    private final AcademicAccess access;

    @GetMapping("/sections/{sectionId}")
    public List<AcademicDtos.RosterRow> roster(CurrentUser user, @PathVariable UUID sectionId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendance.roster(user, sectionId, date == null ? access.today() : date);
    }

    @PutMapping("/sections/{sectionId}")
    public Map<String, Integer> record(CurrentUser user, @PathVariable UUID sectionId,
                                       @Valid @RequestBody AcademicDtos.AttendanceRequest request) {
        return Map.of("saved", attendance.record(user, sectionId, request.date(), request.statuses(), request.reason()));
    }

    @GetMapping("/sections/{sectionId}/summary")
    public List<AcademicDtos.StudentAttendance> summary(CurrentUser user, @PathVariable UUID sectionId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendance.summary(user, sectionId, from, to);
    }

    @GetMapping("/mine")
    public AcademicDtos.AttendanceSummary mine(CurrentUser user) {
        return attendance.mine(user);
    }
}
