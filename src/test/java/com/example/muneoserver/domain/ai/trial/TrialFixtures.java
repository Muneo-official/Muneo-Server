package com.example.muneoserver.domain.ai.trial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

final class TrialFixtures {
    static final String ANALYSIS = """
        {"report":{"title":"테스트 진단","subtitle_fields":{"company_name":"입력 업체","pyeong":25,"analyzed_date":"2026-11-05"},
        "construction_info":{"space_type":"아파트","region":"서울","building_age":"10~20년","floor":5,"elevator":true,"room_count":3},
        "cards":{},"process_sections":[{"process":"철거","display_name":"철거 공사","items":[
        {"status":"누락","title":"폐기물 처리비","description":"처리비 확인 필요","guide":"처리비가 포함되나요?","private_note":"공개되면 안 되는 내부 필드"},
        {"status":"중복","title":"전체 결과 전용 항목","description":"전체 결과 전용 설명","guide":"전체 결과 전용 질문"}]}],
        "summary":{"total_risk_items":2,"chips":{"누락":1,"중복":1,"불분명":0}}}}
        """;
    static final String ESTIMATE = """
        {"estimate_token":"12345678-1234-1234-1234-123456789abc","선택_공종":["도배","마루"],"시공범위":"전체","총_견적_범위":{"최소":3000000,"중간":4000000,"최대":5000000},
        "공종별_단가_범위":{"도배":{"최소":1000000,"중간":1500000,"최대":2000000},"마루":{"최소":2000000,"중간":2500000,"최대":3000000}},
        "공종별_항목_명세":{},"보정_적용":[],"참고_사례_수":1,"참고_사례":[],"검색_쿼리":"전체 결과 전용 쿼리"}
        """;
    static final String ESTIMATE_INPUT = """
        {"공종":["도배","마루"],"평수":25,"지역":"서울","시공범위":"전체","공간유형":"아파트","방개수":3,
        "건물연식":"10~20년","자재등급":"중급","철거여부":"있음","층수":5,"엘리베이터":"있음","트럭접근":"있음","거주중공사":"아니오","공사시기":"봄"}
        """;
    static Map<String, Object> object(String json) {
        try { return new ObjectMapper().readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
