package qa.hafez.hafez.academic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import qa.hafez.hafez.shared.model.BaseEntity;

/** A dated bilingual notice for staff, students or everyone (FR-004). */
@Getter @Setter
@Entity
@Table(name = "notices")
public class Notice extends BaseEntity {

    @Column(name = "title_en", nullable = false, length = 200)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 200)
    private String titleAr;

    @Column(name = "body_en", nullable = false, length = 4000)
    private String bodyEn;

    @Column(name = "body_ar", nullable = false, length = 4000)
    private String bodyAr;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 10)
    private NoticeAudience audience;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
