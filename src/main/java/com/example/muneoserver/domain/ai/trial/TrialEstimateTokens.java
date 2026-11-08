package com.example.muneoserver.domain.ai.trial;

import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** AI가 발급한 임시 저장 토큰을 체험 세션에서 로그인 계정으로 이전한다. */
@Service
@RequiredArgsConstructor
public class TrialEstimateTokens {
    private static final String COLLECTION = "pending_estimates";
    private final MongoTemplate mongo;

    static String guestId(String session) { return "trial:" + session; }

    public void extend(String session, Object result) {
        Query query = guestToken(session, result);
        // Redis 결과와 같은 30분 동안 저장할 수 있다. 기존 회원 토큰 정책은 그대로 둔다.
        long matched = mongo.updateFirst(query,
                new Update().set("expires_at", Date.from(Instant.now().plus(TrialStore.TTL))), COLLECTION)
                .getMatchedCount();
        if (matched != 1) throw new IllegalStateException("체험 가견적 저장 토큰을 확인하지 못했습니다.");
    }

    public void claim(String session, Long userId, Object result) {
        // Redis 소유권을 획득한 계정만 호출한다. 소비된 토큰을 다시 만들지 않는다.
        mongo.updateFirst(guestToken(session, result),
                new Update().set("user_id", String.valueOf(userId)), COLLECTION);
    }

    private Query guestToken(String session, Object result) {
        Object token = TrialStore.object(result).get("estimate_token");
        if (!(token instanceof String value) || !TrialStore.validToken(value))
            throw new IllegalStateException("가견적 저장 토큰을 받지 못했습니다.");
        return Query.query(Criteria.where("_id").is(value).and("user_id").is(guestId(session)));
    }
}
