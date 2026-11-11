package com.example.muneoserver.domain.ai.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrialProjectionTest {
    private static final Map<String, Object> NO_INPUT = Map.of();

    @Test
    void analysisRevealsHighestRiskSectionAndOnlyNamesTheRest() {
        Map<String, Object> preview = TrialStore.project("analysis", NO_INPUT, TrialFixtures.object(TrialFixtures.ANALYSIS));
        assertThat(preview).containsEntry("totalItems", 2);
        assertThat(preview.get("counts")).isEqualTo(Map.of("누락", 1, "중복", 1, "불분명", 0));

        // 첫 번째 공정(중복 1건)이 아니라 누락이 있는 철거 공정을 대표로 공개한다.
        assertThat(preview.get("sections")).isEqualTo(List.of(
                Map.of("id", "0", "display_name", "도배 공사", "itemCount", 1, "locked", true,
                        "counts", Map.of("누락", 0, "중복", 1, "불분명", 0)),
                Map.of("id", "1", "display_name", "철거 공사", "itemCount", 1, "locked", false,
                        "counts", Map.of("누락", 1, "중복", 0, "불분명", 0))));

        Map<String, Object> report = TrialStore.object(preview.get("report"));
        assertThat(report).containsOnlyKeys("subtitle_fields", "construction_info", "process_sections");
        Map<String, Object> revealed = TrialStore.object(TrialStore.list(report.get("process_sections")).getFirst());
        assertThat(revealed).containsEntry("id", "1").containsEntry("display_name", "철거 공사");
        assertThat(TrialStore.object(TrialStore.list(revealed.get("items")).getFirst()))
                .containsOnlyKeys("status", "title", "description", "guide")
                .containsEntry("title", "폐기물 처리비");
        assertThat(TrialStore.object(preview.get("firstItem"))).containsEntry("title", "폐기물 처리비");
        assertThat(preview.toString()).doesNotContain("전체 결과 전용", "private_note");
    }

    @Test
    void analysisWithOnlyNormalItemsStillRevealsTheLargestSection() {
        Map<String, Object> report = Map.of("process_sections", List.of(
                Map.of("display_name", "도배 공사", "items", List.of(Map.of("status", "정상", "title", "a"))),
                Map.of("display_name", "마루 공사", "items", List.of(
                        Map.of("status", "정상", "title", "b"), Map.of("status", "정상", "title", "c")))));
        Map<String, Object> preview = TrialStore.project("analysis", NO_INPUT, Map.of("report", report));
        assertThat(TrialStore.list(preview.get("sections")).stream().map(TrialStore::object)
                .map(section -> section.get("locked"))).containsExactly(true, false);
    }

    @Test
    void estimateRevealsMostEvidencedProcessAndHidesLockedAmounts() {
        Map<String, Object> input = TrialFixtures.object(TrialFixtures.ESTIMATE_INPUT);
        Map<String, Object> preview = TrialStore.project("estimate", input, TrialFixtures.object(TrialFixtures.ESTIMATE));
        assertThat(preview).containsEntry("revealedProcess", "도배").containsEntry("input", input)
                .containsEntry("totalProcesses", 2);
        assertThat(preview.get("lockedProcesses"))
                .isEqualTo(List.of(Map.of("name", "마루", "itemCount", 1, "priced", true)));
        assertThat(preview.get("firstProcess")).isEqualTo(Map.of("name", "도배", "min", 1000000, "max", 2000000));

        Map<String, Object> result = TrialStore.object(preview.get("result"));
        assertThat(result).doesNotContainKeys("총_견적_범위", "참고_사례", "검색_쿼리", "estimate_token");
        assertThat(result).containsEntry("참고_사례_수", 12).containsEntry("보정_적용", List.of("노후 건물 +8%"));
        assertThat(TrialStore.object(result.get("공종별_단가_범위"))).containsOnlyKeys("도배");
        List<?> items = TrialStore.list(TrialStore.object(result.get("공종별_항목_명세")).get("도배"));
        assertThat(items).hasSize(2);
        assertThat(TrialStore.object(items.getFirst())).containsOnlyKeys("description", "amount_range", "등장_사례_수");
        assertThat(preview.toString()).doesNotContain("전체 결과 전용", "2200000", "3300000", "5000000");
    }

    @Test
    void insufficientProcessStaysVisibleWithoutBeingRevealed() {
        Map<String, Object> result = Map.of(
                "선택_공종", List.of("도어", "도배"),
                "데이터_부족_공종", List.of("도어"),
                "공종별_단가_범위", Map.of("도배", Map.of("최소", 1, "중간", 2, "최대", 3)),
                "공종별_항목_명세", Map.of("도어", List.of()));
        Map<String, Object> preview = TrialStore.project("estimate", NO_INPUT, result);
        assertThat(preview).containsEntry("revealedProcess", "도배").containsEntry("lockedProcesses", List.of());
        assertThat(TrialStore.object(TrialStore.object(preview.get("result")).get("공종별_항목_명세")))
                .containsOnlyKeys("도어");
    }

    @Test
    void emptyReportIsValidButMalformedUpstreamIsRejected() {
        assertThat(TrialStore.project("analysis", NO_INPUT, Map.of("report", Map.of("process_sections", List.of()))))
                .containsEntry("totalItems", 0).containsEntry("firstItem", null).containsEntry("sections", List.of());
        assertThat(TrialStore.project("estimate", NO_INPUT, Map.of("선택_공종", List.of())))
                .containsEntry("revealedProcess", null).containsEntry("firstProcess", null);
        assertThatThrownBy(() -> TrialStore.project("analysis", NO_INPUT, Map.of("detail", "error")))
                .isInstanceOf(IllegalStateException.class);
    }
}
