package com.example.muneoserver.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.muneoserver.domain.user.domain.AuthProvider;
import com.example.muneoserver.domain.user.domain.UserRole;
import com.example.muneoserver.domain.user.dto.admin.activity.AdminStoredActivityResponse;
import com.example.muneoserver.domain.user.service.admin.AdminUserActivityService;
import com.example.muneoserver.global.dto.ApiResponse;
import com.example.muneoserver.global.dto.PageResponse;
import com.example.muneoserver.global.security.auth.AuthUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminUserActivityControllerTest {

    @Mock
    private AdminUserActivityService service;

    @InjectMocks
    private AdminUserActivityController controller;

    private final AuthUser admin = new AuthUser(
            1L,
            "admin@example.com",
            AuthProvider.LOCAL,
            true,
            UserRole.ADMIN
    );

    @Test
    void activityResponseDisablesCaching() {
        PageResponse<AdminStoredActivityResponse> page = new PageResponse<>(
                List.of(),
                0,
                10,
                0,
                0,
                true,
                true
        );
        when(service.getEstimates(admin, 42L, PageRequest.of(0, 10))).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<AdminStoredActivityResponse>>> response =
                controller.getEstimates(admin, 42L, 0, 10);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isEqualTo(page);
    }
}
