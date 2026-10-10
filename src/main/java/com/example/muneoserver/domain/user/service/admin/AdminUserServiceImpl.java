package com.example.muneoserver.domain.user.service.admin;

import com.example.muneoserver.domain.user.domain.User;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.dto.admin.AdminUserResponse;
import com.example.muneoserver.domain.user.dto.admin.AdminUserRoleUpdateRequest;
import com.example.muneoserver.domain.user.dto.admin.AdminUserSearchCondition;
import com.example.muneoserver.domain.user.dto.admin.AdminUserUpdateRequest;
import com.example.muneoserver.domain.user.repository.UserRepository;
import com.example.muneoserver.domain.riskfeedback.service.RiskFeedbackService;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.error.exception.CommonException;
import com.example.muneoserver.global.error.exception.ErrorCode;
import com.example.muneoserver.global.security.auth.AuthUser;
import com.example.muneoserver.global.security.redis.RefreshTokenStore;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final RiskFeedbackService riskFeedbackService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(AuthUser authUser, AdminUserSearchCondition condition, Pageable pageable) {
        validateAdmin(authUser);

        Page<AdminUserResponse> users = userRepository.findAll(toSpecification(condition), pageable)
                .map(AdminUserResponse::from);
        return PageResponse.from(users);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(AuthUser authUser, Long userId) {
        validateAdmin(authUser);
        return AdminUserResponse.from(getUserById(userId));
    }

    @Override
    public AdminUserResponse updateUser(AuthUser authUser, Long userId, AdminUserUpdateRequest request) {
        validateAdmin(authUser);

        User user = getUserById(userId);
        if (!Objects.equals(user.getEmail(), request.email()) && userRepository.existsByEmailAndIdNot(request.email(), userId)) {
            throw new CommonException(ErrorCode.MEMBER_ALREADY_EXISTS, "이미 사용중인 이메일입니다.");
        }

        user.updateByAdmin(request.email(), request.name(), request.phoneNumber(), request.birthDate());
        return AdminUserResponse.from(user);
    }

    @Override
    public AdminUserResponse updateUserRole(AuthUser authUser, Long userId, AdminUserRoleUpdateRequest request) {
        validateAdmin(authUser);
        validateNotSelf(authUser, userId);

        User user = getUserById(userId);
        if (user.getRole() == request.role()) {
            throw new CommonException(ErrorCode.USER_ROLE_ALREADY_ASSIGNED);
        }

        user.changeRole(request.role());
        return AdminUserResponse.from(user);
    }

    @Override
    public void deleteUser(AuthUser authUser, Long userId) {
        validateAdmin(authUser);
        validateNotSelf(authUser, userId);

        User user = getUserById(userId);
        if (user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_ALREADY_DELETED);
        }

        riskFeedbackService.deleteByUserId(user.getId());
        user.withdraw();
        refreshTokenStore.delete(user.getId());
    }

    @Override
    public AdminUserResponse restoreUser(AuthUser authUser, Long userId) {
        validateAdmin(authUser);

        User user = getUserById(userId);
        if (!user.isDeleted()) {
            throw new CommonException(ErrorCode.USER_NOT_DELETED);
        }

        user.restore();
        return AdminUserResponse.from(user);
    }

    private Specification<User> toSpecification(AdminUserSearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (condition.keyword() != null && !condition.keyword().isBlank()) {
                String keyword = condition.keyword().trim();
                String keywordPattern = "%" + keyword.toLowerCase() + "%";
                String phonePattern = "%" + keyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), keywordPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), keywordPattern),
                        criteriaBuilder.like(root.get("phoneNumber"), phonePattern)
                ));
            }

            if (condition.role() != null) {
                predicates.add(criteriaBuilder.equal(root.get("role"), condition.role()));
            }

            if (condition.deleted() != null) {
                predicates.add(criteriaBuilder.equal(root.get("deleted"), condition.deleted()));
            }

            if (condition.authProvider() != null) {
                predicates.add(criteriaBuilder.equal(root.get("authProvider"), condition.authProvider()));
            }

            if (condition.emailVerified() != null) {
                predicates.add(criteriaBuilder.equal(root.get("emailVerified"), condition.emailVerified()));
            }

            if (condition.profileCompleted() != null) {
                predicates.add(criteriaBuilder.equal(root.get("profileCompleted"), condition.profileCompleted()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateAdmin(AuthUser authUser) {
        if (authUser == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        if (authUser.role() != UserRole.ADMIN) {
            throw new CommonException(ErrorCode.ADMIN_ACCESS_FORBIDDEN);
        }
    }

    private void validateNotSelf(AuthUser authUser, Long userId) {
        if (Objects.equals(authUser.id(), userId)) {
            throw new CommonException(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN);
        }
    }
}
