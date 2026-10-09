package com.example.muneoserver.domain.user.service.account;

import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.dto.UserResponse;
import com.example.muneoserver.domain.user.dto.email.EmailCodeSendRequest;
import com.example.muneoserver.domain.user.dto.email.EmailCodeVerifyRequest;
import com.example.muneoserver.domain.user.dto.password.PasswordResetRequest;
import com.example.muneoserver.domain.user.dto.profile.LocalProfileUpdateRequest;
import com.example.muneoserver.domain.user.dto.profile.SocialProfileUpdateRequest;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.domain.riskfeedback.service.RiskFeedbackService;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.mail.MailSenderService;
import com.example.muneoserver.global.security.auth.AuthUser;
import com.example.muneoserver.global.security.config.SecurityProperties;
import com.example.muneoserver.global.security.redis.RefreshTokenStore;
import com.example.muneoserver.global.security.redis.email.EmailCodeStore;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAccountServiceImpl implements UserAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final EmailCodeStore emailCodeStore;
    private final MailSenderService mailSenderService;
    private final SecurityProperties securityProperties;
    private final RiskFeedbackService riskFeedbackService;

    @Override
    public UserResponse updateLocalProfile(AuthUser authUser, LocalProfileUpdateRequest request) {
        User user = getActiveUser(authUser);
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new CommonException(ErrorCode.SOCIAL_USER_PROFILE_RESTRICTION);
        }

        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmailAndIdNot(request.email(), user.getId())) {
            throw new CommonException(ErrorCode.MEMBER_ALREADY_EXISTS, "이미 사용중인 이메일입니다.");
        }

        user.updateLocalProfile(request.email(), request.name(), request.phoneNumber(), request.birthDate());

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.newPasswordConfirm() == null || !request.newPassword().equals(request.newPasswordConfirm())) {
                throw new CommonException(ErrorCode.VALIDATION_FAILED, "비밀번호 확인이 일치하지 않습니다.");
            }
            user.changePassword(passwordEncoder.encode(request.newPassword()));
        }

        return UserResponse.from(user);
    }

    @Override
    public UserResponse updateSocialProfile(AuthUser authUser, SocialProfileUpdateRequest request) {
        User user = getActiveUser(authUser);
        if (user.getAuthProvider() == AuthProvider.LOCAL) {
            throw new CommonException(ErrorCode.LOCAL_USER_PROFILE_RESTRICTION);
        }

        user.updateSocialProfile(request.name(), request.phoneNumber(), request.birthDate());
        return UserResponse.from(user);
    }

    @Override
    public void withdraw(AuthUser authUser) {
        User user = getActiveUser(authUser);
        riskFeedbackService.deleteByUserId(user.getId());
        user.withdraw();
        refreshTokenStore.delete(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public void sendEmailVerificationCode(EmailCodeSendRequest request) {
        ensureEmailFeatureEnabled();

        User user = userRepository.findByEmailAndAuthProvider(request.email(), AuthProvider.LOCAL)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        String code = generateCode();
        emailCodeStore.saveVerificationCode(request.email(), code, securityProperties.email().verificationCodeExpiration());
        mailSenderService.send(request.email(), "[Muneo] 이메일 인증 코드", "인증 코드: " + code);
    }

    @Override
    public void verifyEmailCode(EmailCodeVerifyRequest request) {
        ensureEmailFeatureEnabled();

        User user = userRepository.findByEmailAndAuthProvider(request.email(), AuthProvider.LOCAL)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        String savedCode = emailCodeStore.findVerificationCode(request.email())
                .orElseThrow(() -> new CommonException(ErrorCode.EMAIL_VERIFICATION_CODE_EXPIRED));

        if (!savedCode.equals(request.code())) {
            throw new CommonException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        user.markEmailVerified();
        emailCodeStore.deleteVerificationCode(request.email());
    }

    @Override
    @Transactional(readOnly = true)
    public void sendPasswordResetCode(EmailCodeSendRequest request) {
        ensureEmailFeatureEnabled();

        User user = userRepository.findByEmailAndAuthProvider(request.email(), AuthProvider.LOCAL)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        String code = generateCode();
        emailCodeStore.saveResetCode(request.email(), code, securityProperties.email().passwordResetCodeExpiration());
        mailSenderService.send(request.email(), "[Muneo] 비밀번호 재설정 코드", "비밀번호 재설정 코드: " + code);
    }

    @Override
    public void resetPassword(PasswordResetRequest request) {
        ensureEmailFeatureEnabled();

        User user = userRepository.findByEmailAndAuthProvider(request.email(), AuthProvider.LOCAL)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new CommonException(ErrorCode.VALIDATION_FAILED, "비밀번호 확인이 일치하지 않습니다.");
        }

        String savedCode = emailCodeStore.findResetCode(request.email())
                .orElseThrow(() -> new CommonException(ErrorCode.PASSWORD_RESET_CODE_EXPIRED));

        if (!savedCode.equals(request.code())) {
            throw new CommonException(ErrorCode.PASSWORD_RESET_CODE_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        emailCodeStore.deleteResetCode(request.email());
    }

    private String generateCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private void ensureEmailFeatureEnabled() {
        if (!securityProperties.email().enabled()) {
            throw new CommonException(ErrorCode.EMAIL_FEATURE_DISABLED);
        }
    }

    private User getActiveUser(AuthUser authUser) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        return user;
    }
}
