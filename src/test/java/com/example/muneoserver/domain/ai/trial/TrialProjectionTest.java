package com.example.muneoserver.domain.ai.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrialProjectionTest {
    @Test
    void analysisOnlyExposesOneAllowlistedItemAndComputedCounts() {
        Map<String, Object> preview = TrialStore.project("analysis", TrialFixtures.object(TrialFixtures.ANALYSIS));
        assertThat(preview).containsEntry("totalItems", 2);
        assertThat(preview.get("counts")).isEqualTo(Map.of("누락", 1, "중복", 1, "불분명", 0));
        assertThat(TrialStore.object(preview.get("firstItem"))).containsOnlyKeys("process", "status", "title", "description", "guide");
        assertThat(preview.toString()).doesNotContain("전체 결과 전용", "private_note", "입력 업체");
    }

    @Test
    void estimateOnlyExposesFirstProcessRange() {
        Map<String, Object> preview = TrialStore.project("estimate", TrialFixtures.object(TrialFixtures.ESTIMATE));
        assertThat(preview.get("firstProcess")).isEqualTo(Map.of("name", "도배", "min", 1000000, "max", 2000000));
        assertThat(preview).containsEntry("totalProcesses", 2);
        assertThat(preview.toString()).doesNotContain("마루", "검색_쿼리", "총_견적");
    }

    @Test
    void emptyReportIsValidButMalformedUpstreamIsRejected() {
        assertThat(TrialStore.project("analysis", Map.of("report", Map.of("process_sections", List.of()))))
                .containsEntry("totalItems", 0).containsEntry("firstItem", null);
        assertThatThrownBy(() -> TrialStore.project("analysis", Map.of("detail", "error")))
                .isInstanceOf(IllegalStateException.class);
    }
}
