package com.example.muneoserver.domain.user.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.dto.admin.AdminUserResponse;
import com.example.muneoserver.domain.user.dto.admin.AdminUserRoleUpdateRequest;
import com.example.muneoserver.domain.user.dto.admin.AdminUserUpdateRequest;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.domain.riskfeedback.service.RiskFeedbackService;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import com.example.muneoserver.global.security.redis.RefreshTokenStore;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private RiskFeedbackService riskFeedbackService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private final AuthUser adminUser = new AuthUser(1L, "admin@example.com", AuthProvider.LOCAL, true, UserRole.ADMIN);

    @Test
    void updateUserRoleRejectsSelfAction() {
        AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest(UserRole.USER);

        assertThatThrownBy(() -> adminUserService.updateUserRole(adminUser, adminUser.id(), request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN);
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateUserRoleRejectsSameRole() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest(UserRole.USER);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.updateUserRole(adminUser, user.getId(), request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.USER_ROLE_ALREADY_ASSIGNED);
                });
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void deleteUserWithdrawsUserAndDeletesRefreshToken() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        adminUserService.deleteUser(adminUser, user.getId());

        assertThat(user.isDeleted()).isTrue();
        verify(riskFeedbackService).deleteByUserId(user.getId());
        verify(refreshTokenStore).delete(user.getId());
    }

    @Test
    void deleteUserRejectsAlreadyDeletedUser() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        user.withdraw();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.deleteUser(adminUser, user.getId()))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_DELETED);
                });

        verify(refreshTokenStore, never()).delete(user.getId());
    }

    @Test
    void restoreUserRestoresDeletedUser() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        user.withdraw();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserResponse response = adminUserService.restoreUser(adminUser, user.getId());

        assertThat(response.deleted()).isFalse();
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void restoreUserRejectsActiveUser() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.restoreUser(adminUser, user.getId()))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_DELETED);
                });
    }

    @Test
    void updateUserRejectsDuplicatedEmail() {
        User user = createUser(2L, "user@example.com", UserRole.USER);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "duplicated@example.com",
                "사용자",
                "010-1234-5678",
                LocalDate.of(2000, 1, 1)
        );

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot(request.email(), user.getId())).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.updateUser(adminUser, user.getId(), request))
                .isInstanceOf(CommonException.class)
                .satisfies(exception -> {
                    CommonException commonException = (CommonException) exception;
                    assertThat(commonException.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXISTS);
                });
    }

    private User createUser(Long id, String email, UserRole role) {
        User user = User.create(email, "encoded-password", "사용자", "010-1234-5678", LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}
