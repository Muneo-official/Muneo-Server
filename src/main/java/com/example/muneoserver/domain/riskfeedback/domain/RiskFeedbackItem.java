package com.example.muneoserver.domain.riskfeedback.domain;

import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "risk_feedback_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_risk_feedback_item",
                columnNames = {"feedback_id", "item_id"}
        ),
        indexes = @Index(name = "idx_risk_feedback_item_verdict", columnList = "verdict")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskFeedbackItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private RiskFeedback feedback;

    @Column(name = "item_id", nullable = false, length = 255)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RiskFeedbackVerdict verdict;

    RiskFeedbackItem(RiskFeedback feedback, String itemId, RiskFeedbackVerdict verdict) {
        this.feedback = feedback;
        this.itemId = itemId;
        this.verdict = verdict;
    }

    void changeVerdict(RiskFeedbackVerdict verdict) {
        this.verdict = verdict;
    }
}
