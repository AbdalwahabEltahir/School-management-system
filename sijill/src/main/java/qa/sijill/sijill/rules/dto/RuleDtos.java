package qa.sijill.sijill.rules.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import qa.sijill.sijill.rules.model.RuleVersion;
import qa.sijill.sijill.rules.service.RuleQueryService;
import qa.sijill.sijill.rules.service.RuleService;

/** Request and response shapes of the rules API. */
public final class RuleDtos {

    private RuleDtos() {}

    public record VersionView(UUID id, int versionNumber, String name, String whyItMatters, String whyItMattersAr,
                              String checkType, String severity, int reminderDays, String parameter,
                              String fixStepsEn, String fixStepsAr, Set<String> docTypes, Set<String> sensitivities,
                              boolean approved, Instant approvedAt) {
        public static VersionView from(RuleVersion v) {
            return v == null ? null : new VersionView(v.getId(), v.getVersionNumber(), v.getName(), v.getWhyItMatters(),
                    v.getWhyItMattersAr(), v.getCheckType().name(), v.getSeverity().name(), v.getReminderDays(),
                    v.getParameter(), v.getFixStepsEn(), v.getFixStepsAr(), Set.copyOf(v.getDocTypes()),
                    v.getSensitivities().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
                    v.isApproved(), v.getApprovedAt());
        }
    }

    /** A rule, the version in force, a version waiting for approval (network-wide rules), and open issues. */
    public record RuleView(UUID id, String code, String status, boolean networkWide, VersionView effective,
                           VersionView pending, long openIssues) {
        public static RuleView from(RuleQueryService.RuleEntry e) {
            return new RuleView(e.rule().getId(), e.rule().getCode(), e.rule().getStatus().name(),
                    e.rule().isNetworkWide(), VersionView.from(e.effective()), VersionView.from(e.pending()),
                    e.openIssues());
        }
    }

    /** Live preview while editing: how many files would break the rule, and the message as the owner would read it. */
    public record PreviewView(int filesChecked, int wouldBreak, int wouldNeedReview, String subjectEn, String bodyEn,
                              String subjectAr, String bodyAr) {
        public static PreviewView from(RuleQueryService.Preview p) {
            return new PreviewView(p.impact().filesChecked(), p.impact().wouldBreak(), p.impact().wouldNeedReview(),
                    p.messageEn().subject(), p.messageEn().body(), p.messageAr().subject(), p.messageAr().body());
        }
    }

    /** Create a rule: school-level, or a network-wide proposal that needs a second person's approval. */
    public record CreateRuleRequest(@NotNull @Valid RuleService.Draft draft, boolean networkWide, boolean turnOn) {}
}
