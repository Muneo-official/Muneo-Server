package com.example.muneoserver.domain.user.repository;

import com.example.muneoserver.domain.user.document.AdminStoredActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserActivityMongoRepository {

    Page<AdminStoredActivity> findEstimatesByUserId(String userId, Pageable pageable);

    Page<AdminStoredActivity> findRiskReportsByUserId(String userId, Pageable pageable);
}
