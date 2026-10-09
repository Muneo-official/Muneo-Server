package com.example.muneoserver.domain.riskfeedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.riskfeedback.document.RiskReportDocument;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedback;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackRating;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackReason;
import com.example.muneoserver.domain.riskfeedback.domain.RiskFeedbackVerdict;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackItemRequest;
import com.example.muneoserver.domain.riskfeedback.dto.RiskFeedbackRequest;
import com.example.muneoserver.domain.riskfeedback.repository.RiskFeedbackRepository;
import com.example.muneoserver.domain.riskfeedback.repository.RiskReportReader;
import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RiskFeedbackServiceImplTest {

    private static final String REPORT_ID = "507f1f77bcf86cd799439011";

    @Mock
    private RiskFeedbackRepository riskFeedbackRepository;

    @Mock
    private RiskReportReader riskReportReader;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RiskFeedbackServiceImpl riskFeedbackService;

    private final AuthUser authUser = new AuthUser(
            42L,
            "user@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.USER
    );

    private User user;
    private RiskReportDocument report;

    @BeforeEach
    void setUp() {
        user = User.create(
                "user@example.com",
                "encoded-password",
                "사용자",
                "010-1234-5678",
                LocalDate.of(2000, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", 42L);

        report = new RiskReportDocument();
        ReflectionTestUtils.setField(report, "id", new ObjectId(REPORT_ID));
        ReflectionTestUtils.setField(report, "userId", "42");
        ReflectionTestUtils.setField(report, "result", Map.of(
                "report", Map.of(
                        "process_sections", List.of(
                                Map.of("items", List.of(
                                        Map.of("title", "철거 누락"),
                                        Map.of("id", "fixed-item", "title", "폐기물 처리")
                                ))
                        )
                )
        ));
    }

    @Test
    void putCreatesNormalizedFeedbackAfterCheckingMongoOwnership() {
        when(riskReportReader.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report));
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(riskFeedbackRepository.findByUser_IdAndReportId(42L, REPORT_ID)).thenReturn(Optional.empty());

        RiskFeedbackRequest request = new RiskFeedbackRequest(
                1,
                RiskFeedbackRating.BAD,
                List.of(
                        RiskFeedbackReason.INCORRECT_RESULT,
                        RiskFeedbackReason.INCORRECT_RESULT,
                        RiskFeedbackReason.UNCLEAR_EXPLANATION
                ),
                "  설명이 실제 내용과 달라요.  ",
                List.of(
                        new RiskFeedbackItemRequest(" 0_0 ", RiskFeedbackVerdict.CONFIRMED_ISSUE),
                        new RiskFeedbackItemRequest("fixed-item", RiskFeedbackVerdict.NOT_ISSUE)
                )
        );

        riskFeedbackService.put(authUser, REPORT_ID, request);

        ArgumentCaptor<RiskFeedback> captor = ArgumentCaptor.forClass(RiskFeedback.class);
        verify(riskFeedbackRepository).save(captor.capture());
        RiskFeedback saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getReportId()).isEqualTo(REPORT_ID);
        assertThat(saved.getReportVersion()).isEqualTo(REPORT_ID);
        assertThat(saved.getReasons()).containsExactly(
                RiskFeedbackReason.INCORRECT_RESULT,
                RiskFeedbackReason.UNCLEAR_EXPLANATION
        );
        assertThat(saved.getComment()).isEqualTo("설명이 실제 내용과 달라요.");
        assertThat(saved.getItemReviews())
                .extracting(item -> item.getItemId())
                .containsExactly("0_0", "fixed-item");
    }

    @Test
    void putRejectsReasonThatDoesNotMatchRating() {
        when(riskReportReader.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report));
        RiskFeedbackRequest request = new RiskFeedbackRequest(
                1,
                RiskFeedbackRating.GOOD,
                List.of(RiskFeedbackReason.INCORRECT_RESULT),
                null,
                List.of()
        );

        assertThatThrownBy(() -> riskFeedbackService.put(authUser, REPORT_ID, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(commonException.getErrors())
                            .containsEntry("reasons", "rating과 일치하는 이유만 선택할 수 있습니다.");
                });

        verifyNoInteractions(userRepository);
        verify(riskFeedbackRepository, never()).save(any());
    }

    @Test
    void putRejectsItemIdThatIsNotInReport() {
        when(riskReportReader.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report));
        RiskFeedbackRequest request = new RiskFeedbackRequest(
                1,
                RiskFeedbackRating.BAD,
                List.of(),
                null,
                List.of(new RiskFeedbackItemRequest("unknown-item", RiskFeedbackVerdict.NOT_ISSUE))
        );

        assertThatThrownBy(() -> riskFeedbackService.put(authUser, REPORT_ID, request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
                    assertThat(commonException.getErrors()).containsKey("item_reviews.not_found");
                });

        verifyNoInteractions(userRepository);
    }

    @Test
    void putRejectsReportOwnedByAnotherUser() {
        ReflectionTestUtils.setField(report, "userId", "99");
        when(riskReportReader.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> riskFeedbackService.put(authUser, REPORT_ID, validRequest()))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.RISK_REPORT_ACCESS_FORBIDDEN));

        verifyNoInteractions(userRepository, riskFeedbackRepository);
    }

    @Test
    void getReturnsEmptyWhenFeedbackWasNotWritten() {
        when(riskReportReader.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report));
        when(riskFeedbackRepository.findByUser_IdAndReportId(42L, REPORT_ID)).thenReturn(Optional.empty());

        assertThat(riskFeedbackService.get(authUser, REPORT_ID)).isEmpty();
    }

    @Test
    void invalidReportIdIsRejectedBeforeMongoCall() {
        assertThatThrownBy(() -> riskFeedbackService.put(authUser, "invalid-id", validRequest()))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verifyNoInteractions(riskReportReader, userRepository, riskFeedbackRepository);
    }

    @Test
    void mongoFailureIsReportedAsServiceUnavailable() {
        when(riskReportReader.findById(new ObjectId(REPORT_ID)))
                .thenThrow(new DataAccessResourceFailureException("mongo unavailable"));

        assertThatThrownBy(() -> riskFeedbackService.put(authUser, REPORT_ID, validRequest()))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> assertThat(((CommonException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.RISK_REPORT_STORE_UNAVAILABLE));
    }

    private RiskFeedbackRequest validRequest() {
        return new RiskFeedbackRequest(
                1,
                RiskFeedbackRating.GOOD,
                List.of(RiskFeedbackReason.FOUND_RISKS),
                null,
                List.of()
        );
    }
}
