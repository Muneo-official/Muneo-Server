package com.example.muneoserver.domain.riskfeedback.repository;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskFeedbackRepository extends JpaRepository<RiskFeedback, Long> {

    Optional<RiskFeedback> findByUser_IdAndReportId(Long userId, String reportId);

    List<RiskFeedback> findAllByUser_Id(Long userId);

    Page<RiskFeedback> findAllByUser_Id(Long userId, Pageable pageable);
}
