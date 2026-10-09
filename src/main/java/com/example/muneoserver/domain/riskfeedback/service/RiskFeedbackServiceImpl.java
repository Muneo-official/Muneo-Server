package com.example.muneoserver.domain.riskfeedback.service;

import com.example.muneoserver.domain.riskfeedback.document.RiskReportDocument;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback.ItemValue;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackReason;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackItemRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackResponse;
import com.example.muneoserver.domain.riskfeedback.repository.RiskFeedbackRepository;
import com.example.muneoserver.domain.riskfeedback.repository.RiskReportReader;
import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RiskFeedbackServiceImpl implements RiskFeedbackService {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_REASONS = 3;
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int MAX_ITEM_REVIEWS = 100;
    private static final int MAX_ITEM_ID_LENGTH = 255;

    private final RiskFeedbackRepository riskFeedbackRepository;
    private final RiskReportReader riskReportReader;
    private final UserRepository userRepository;

    @Override
    public void put(AuthUser authUser, String reportId, RiskFeedbackRequest request) {
        Long userId = requireUserId(authUser);
        RiskReportDocument report = getOwnedReport(userId, reportId);
        String canonicalReportId = report.getId().toHexString();
        NormalizedFeedback normalized = validateAndNormalize(request, report.resolveItemIds());
        User user = getActiveUserForUpdate(userId);

        RiskFeedback feedback = riskFeedbackRepository.findByUser_IdAndReportId(userId, canonicalReportId)
                .orElseGet(() -> RiskFeedback.create(user, canonicalReportId, report.resolveVersion()));
        feedback.replace(
                normalized.schemaVersion(),
                normalized.rating(),
                normalized.reasons(),
                normalized.comment(),
                normalized.itemReviews()
        );
        riskFeedbackRepository.save(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskFeedbackResponse> get(AuthUser authUser, String reportId) {
        Long userId = requireUserId(authUser);
        RiskReportDocument report = getOwnedReport(userId, reportId);
        return riskFeedbackRepository.findByUser_IdAndReportId(userId, report.getId().toHexString())
                .map(RiskFeedbackResponse::from);
    }

    @Override
    public void delete(AuthUser authUser, String reportId) {
        Long userId = requireUserId(authUser);
        RiskReportDocument report = getOwnedReport(userId, reportId);
        deleteByUserIdAndReportId(userId, report.getId().toHexString());
    }

    @Override
    public void deleteByUserId(Long userId) {
        riskFeedbackRepository.deleteAll(riskFeedbackRepository.findAllByUser_Id(userId));
    }

    @Override
    public void deleteByUserIdAndReportId(Long userId, String reportId) {
        String canonicalReportId = parseObjectId(reportId).toHexString();
        riskFeedbackRepository.findByUser_IdAndReportId(userId, canonicalReportId)
                .ifPresent(riskFeedbackRepository::delete);
    }

    private Long requireUserId(AuthUser authUser) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.id();
    }

    private RiskReportDocument getOwnedReport(Long userId, String reportId) {
        ObjectId objectId = parseObjectId(reportId);
        RiskReportDocument report;
        try {
            report = riskReportReader.findById(objectId)
                    .orElseThrow(() -> new CommonException(ErrorCode.RISK_REPORT_NOT_FOUND));
        } catch (CommonException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new CommonException(ErrorCode.RISK_REPORT_STORE_UNAVAILABLE);
        }

        if (!String.valueOf(userId).equals(report.getUserId())) {
            throw new CommonException(ErrorCode.RISK_REPORT_ACCESS_FORBIDDEN);
        }
        return report;
    }

    private ObjectId parseObjectId(String reportId) {
        if (reportId == null || !ObjectId.isValid(reportId)) {
            throw new CommonException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("reportId", "reportId 형식이 올바르지 않습니다.")
            );
        }
        return new ObjectId(reportId);
    }

    private User getActiveUserForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private NormalizedFeedback validateAndNormalize(RiskFeedbackRequest request, Set<String> reportItemIds) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            throw new CommonException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("request", "피드백 요청은 필수입니다.")
            );
        }

        if (request.schemaVersion() == null || request.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            errors.put("schema_version", "schema_version은 1이어야 합니다.");
        }
        if (request.rating() == null) {
            errors.put("rating", "rating은 필수입니다.");
        }

        List<RiskFeedbackReason> reasons = normalizeReasons(request, errors);
        String comment = normalizeComment(request.comment(), errors);
        List<ItemValue> itemReviews = normalizeItemReviews(request.itemReviews(), reportItemIds, errors);

        if (!errors.isEmpty()) {
            ErrorCode errorCode = errors.containsKey("item_reviews.not_found")
                    ? ErrorCode.UNPROCESSABLE_ENTITY
                    : ErrorCode.VALIDATION_FAILED;
            throw new CommonException(errorCode, errors);
        }

        return new NormalizedFeedback(
                request.schemaVersion(),
                request.rating(),
                List.copyOf(reasons),
                comment,
                List.copyOf(itemReviews)
        );
    }

    private List<RiskFeedbackReason> normalizeReasons(
            RiskFeedbackRequest request,
            Map<String, String> errors
    ) {
        if (request.reasons() == null) {
            return List.of();
        }

        List<RiskFeedbackReason> reasons = new ArrayList<>(new LinkedHashSet<>(request.reasons()));
        if (reasons.contains(null)) {
            errors.put("reasons", "reasons에는 null을 포함할 수 없습니다.");
            return reasons;
        }
        if (reasons.size() > MAX_REASONS) {
            errors.put("reasons", "reasons는 최대 3개까지 선택할 수 있습니다.");
        }
        if (request.rating() != null && reasons.stream().anyMatch(reason -> !reason.supports(request.rating()))) {
            errors.put("reasons", "rating과 일치하는 이유만 선택할 수 있습니다.");
        }
        return reasons;
    }

    private String normalizeComment(String comment, Map<String, String> errors) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.strip();
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            errors.put("comment", "comment는 최대 500자까지 입력할 수 있습니다.");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private List<ItemValue> normalizeItemReviews(
            List<RiskFeedbackItemRequest> itemReviews,
            Set<String> reportItemIds,
            Map<String, String> errors
    ) {
        if (itemReviews == null) {
            return List.of();
        }
        if (itemReviews.size() > MAX_ITEM_REVIEWS) {
            errors.put("item_reviews", "item_reviews는 최대 100개까지 입력할 수 있습니다.");
        }

        List<ItemValue> normalized = new ArrayList<>();
        Set<String> itemIds = new HashSet<>();
        List<String> unknownItemIds = new ArrayList<>();
        for (int index = 0; index < itemReviews.size(); index++) {
            RiskFeedbackItemRequest itemReview = itemReviews.get(index);
            if (itemReview == null) {
                errors.put("item_reviews[" + index + "]", "item review는 null일 수 없습니다.");
                continue;
            }

            String itemId = itemReview.itemId() == null ? null : itemReview.itemId().strip();
            if (itemId == null || itemId.isEmpty()) {
                errors.put("item_reviews[" + index + "].item_id", "item_id는 필수입니다.");
            } else if (itemId.length() > MAX_ITEM_ID_LENGTH) {
                errors.put("item_reviews[" + index + "].item_id", "item_id는 최대 255자까지 입력할 수 있습니다.");
            } else {
                if (!itemIds.add(itemId)) {
                    errors.put("item_reviews", "동일한 item_id를 중복해서 입력할 수 없습니다.");
                }
                if (!reportItemIds.contains(itemId)) {
                    unknownItemIds.add(itemId);
                }
            }
            if (itemReview.verdict() == null) {
                errors.put("item_reviews[" + index + "].verdict", "verdict는 필수입니다.");
            }

            if (itemId != null && !itemId.isEmpty() && itemReview.verdict() != null) {
                normalized.add(new ItemValue(itemId, itemReview.verdict()));
            }
        }

        if (!unknownItemIds.isEmpty()) {
            errors.put(
                    "item_reviews.not_found",
                    "리포트에 포함되지 않은 item_id가 있습니다: " + String.join(", ", unknownItemIds)
            );
        }
        return normalized;
    }

    private record NormalizedFeedback(
            int schemaVersion,
            com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating rating,
            List<RiskFeedbackReason> reasons,
            String comment,
            List<ItemValue> itemReviews
    ) {
    }
}
