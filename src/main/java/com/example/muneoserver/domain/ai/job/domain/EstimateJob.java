package com.example.muneoserver.domain.ai.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "estimate_jobs",
        indexes = {
                @Index(name = "idx_estimate_jobs_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_estimate_jobs_status_created", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateJob {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstimateJobStatus status;

    @Column(name = "request_payload", nullable = false, columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "result_payload", columnDefinition = "TEXT")
    private String resultPayload;

    @Column(name = "error_payload", columnDefinition = "TEXT")
    private String errorPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    private EstimateJob(UUID id, Long userId, String requestPayload, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.status = EstimateJobStatus.PENDING;
        this.requestPayload = requestPayload;
        this.createdAt = createdAt;
    }

    public static EstimateJob pending(Long userId, String requestPayload) {
        return new EstimateJob(UUID.randomUUID(), userId, requestPayload, Instant.now());
    }

    public boolean start(Instant now) {
        if (status != EstimateJobStatus.PENDING) {
            return false;
        }
        status = EstimateJobStatus.PROCESSING;
        startedAt = now;
        return true;
    }

    public void succeed(String resultPayload, Instant now) {
        if (status != EstimateJobStatus.PROCESSING) {
            return;
        }
        status = EstimateJobStatus.SUCCEEDED;
        this.resultPayload = resultPayload;
        this.errorPayload = null;
        completedAt = now;
    }

    public void fail(String errorPayload, Instant now) {
        if (status != EstimateJobStatus.PROCESSING) {
            return;
        }
        status = EstimateJobStatus.FAILED;
        this.resultPayload = null;
        this.errorPayload = errorPayload;
        completedAt = now;
    }
}
