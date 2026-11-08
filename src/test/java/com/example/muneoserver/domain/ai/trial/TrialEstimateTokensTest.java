package com.example.muneoserver.domain.ai.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class TrialEstimateTokensTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final TrialEstimateTokens tokens = new TrialEstimateTokens(mongo);
    private final String session = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private final String token = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private final Map<String, Object> result = Map.of("estimate_token", token);

    @Test
    void extendsOnlyMatchingGuestTokenForThirtyMinutes() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("pending_estimates")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        Instant before = Instant.now();
        tokens.extend(session, result);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq("pending_estimates"));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", token)
                .containsEntry("user_id", "trial:" + session);
        Date expiry = (Date) ((Map<?, ?>) update.getValue().getUpdateObject().get("$set")).get("expires_at");
        assertThat(expiry.toInstant()).isBetween(before.plusSeconds(1799), Instant.now().plusSeconds(1801));
    }

    @Test
    void claimTransfersOnlyGuestOwnerWithoutRecreatingConsumedToken() {
        tokens.claim(session, 42L, result);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq("pending_estimates"));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", token)
                .containsEntry("user_id", "trial:" + session);
        Map<?, ?> fields = (Map<?, ?>) update.getValue().getUpdateObject().get("$set");
        assertThat(fields).hasSize(1);
        assertThat(fields.get("user_id")).isEqualTo("42");
    }

    @Test
    void missingGuestTokenCannotBecomeSuccessfulPreview() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("pending_estimates")))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThatThrownBy(() -> tokens.extend(session, result)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedTokenDoesNotReachMongo() {
        assertThatThrownBy(() -> tokens.extend(session, Map.of())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mongo);
    }
}
