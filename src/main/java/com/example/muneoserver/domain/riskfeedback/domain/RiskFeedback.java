package com.example.muneoserver.domain.riskfeedback.domain;

import com.example.muneoserver.domain.shared.BaseTimeEntity;
import com.example.muneoserver.domain.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "risk_feedback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_risk_feedback_user_report",
                columnNames = {"user_id", "report_id"}
        ),
        indexes = @Index(
                name = "idx_risk_feedback_rating_created_at",
                columnList = "rating, created_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "report_id", nullable = false, length = 24)
    private String reportId;

    @Column(name = "report_version", nullable = false, length = 64)
    private String reportVersion;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskFeedbackRating rating;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "risk_feedback_reasons",
            joinColumns = @JoinColumn(name = "feedback_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_risk_feedback_reason",
                    columnNames = {"feedback_id", "reason"}
            ),
            indexes = @Index(name = "idx_risk_feedback_reason", columnList = "reason")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private Set<RiskFeedbackReason> reasons = new LinkedHashSet<>();

    @Column(length = 500)
    private String comment;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<RiskFeedbackItem> itemReviews = new ArrayList<>();

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    private RiskFeedback(User user, String reportId, String reportVersion) {
        this.user = user;
        this.reportId = reportId;
        this.reportVersion = reportVersion;
    }

    public static RiskFeedback create(User user, String reportId, String reportVersion) {
        return new RiskFeedback(user, reportId, reportVersion);
    }

    public void replace(
            int schemaVersion,
            RiskFeedbackRating rating,
            Collection<RiskFeedbackReason> reasons,
            String comment,
            Collection<ItemValue> items
    ) {
        this.schemaVersion = schemaVersion;
        this.rating = rating;
        this.reasons.clear();
        this.reasons.addAll(reasons);
        this.comment = comment;
        replaceItems(items);
    }

    private void replaceItems(Collection<ItemValue> items) {
        Set<String> requestedItemIds = items.stream()
                .map(ItemValue::itemId)
                .collect(Collectors.toSet());
        itemReviews.removeIf(item -> !requestedItemIds.contains(item.getItemId()));

        Map<String, RiskFeedbackItem> existingItems = itemReviews.stream()
                .collect(Collectors.toMap(RiskFeedbackItem::getItemId, Function.identity()));
        for (ItemValue item : items) {
            RiskFeedbackItem existing = existingItems.get(item.itemId());
            if (existing == null) {
                itemReviews.add(new RiskFeedbackItem(this, item.itemId(), item.verdict()));
            } else {
                existing.changeVerdict(item.verdict());
            }
        }
    }

    public record ItemValue(String itemId, RiskFeedbackVerdict verdict) {
    }
}
