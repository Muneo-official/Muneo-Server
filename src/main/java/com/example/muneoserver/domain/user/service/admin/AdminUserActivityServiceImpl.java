package com.example.muneoserver.domain.user.service.admin;

import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.example.muneoserver.domain.riskfeedback.repository.RiskFeedbackRepository;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminRiskFeedbackResponse;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.domain.user.repository.AdminUserActivityMongoRepository;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserActivityServiceImpl implements AdminUserActivityService {

    private final UserRepository userRepository;
    private final AdminUserActivityMongoRepository adminUserActivityMongoRepository;
    private final RiskFeedbackRepository riskFeedbackRepository;

    @Override
    public PageResponse<AdminStoredActivityResponse> getEstimates(
            AuthUser authUser,
            Long userId,
            Pageable pageable
    ) {
        validateRequest(authUser, userId);
        logLookup(authUser, userId, "estimates", pageable);

        try {
            return PageResponse.from(adminUserActivityMongoRepository
                    .findEstimatesByUserId(String.valueOf(userId), pageRequest(pageable))
                    .map(AdminStoredActivityResponse::from));
        } catch (DataAccessException e) {
            throw new CommonException(ErrorCode.ADMIN_ACTIVITY_STORE_UNAVAILABLE);
        }
    }

    @Override
    public PageResponse<AdminStoredActivityResponse> getRiskReports(
            AuthUser authUser,
            Long userId,
            Pageable pageable
    ) {
        validateRequest(authUser, userId);
        logLookup(authUser, userId, "risk-reports", pageable);

        try {
            return PageResponse.from(adminUserActivityMongoRepository
                    .findRiskReportsByUserId(String.valueOf(userId), pageRequest(pageable))
                    .map(AdminStoredActivityResponse::from));
        } catch (DataAccessException e) {
            throw new CommonException(ErrorCode.ADMIN_ACTIVITY_STORE_UNAVAILABLE);
        }
    }

    @Override
    public PageResponse<AdminRiskFeedbackResponse> getFeedback(
            AuthUser authUser,
            Long userId,
            Pageable pageable
    ) {
        validateRequest(authUser, userId);
        logLookup(authUser, userId, "feedback", pageable);

        Page<RiskFeedback> feedback;
        try {
            feedback = riskFeedbackRepository.findAllByUser_Id(userId, pageRequest(pageable));
        } catch (DataAccessException e) {
            throw new CommonException(ErrorCode.ADMIN_ACTIVITY_STORE_UNAVAILABLE);
        }
        return PageResponse.from(feedback.map(AdminRiskFeedbackResponse::from));
    }

    private void validateRequest(AuthUser authUser, Long userId) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
        if (authUser.role() != UserRole.ADMIN) {
            throw new CommonException(ErrorCode.ADMIN_ACCESS_FORBIDDEN);
        }
        if (!userRepository.existsById(userId)) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Pageable pageRequest(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private void logLookup(AuthUser authUser, Long userId, String type, Pageable pageable) {
        log.info(
                "Admin activity lookup adminId={} targetUserId={} type={} page={} size={}",
                authUser.id(),
                userId,
                type,
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
    }
}
