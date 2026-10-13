package com.example.muneoserver.domain.ai.job.repository;

import com.example.muneoserver.domain.ai.job.domain.EstimateJob;
import com.example.muneoserver.domain.ai.job.domain.EstimateJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstimateJobRepository extends JpaRepository<EstimateJob, UUID> {

    Optional<EstimateJob> findByIdAndUserId(UUID id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EstimateJob j where j.id = :id")
    Optional<EstimateJob> findByIdForUpdate(@Param("id") UUID id);

    @Query("select j.id from EstimateJob j where j.status = :status order by j.createdAt asc")
    List<UUID> findIdsByStatus(
            @Param("status") EstimateJobStatus status,
            Pageable pageable
    );

    @Query("""
            select j.id
            from EstimateJob j
            where j.status = :status and j.startedAt < :startedBefore
            order by j.startedAt asc
            """)
    List<UUID> findIdsByStatusAndStartedBefore(
            @Param("status") EstimateJobStatus status,
            @Param("startedBefore") Instant startedBefore,
            Pageable pageable
    );
}
