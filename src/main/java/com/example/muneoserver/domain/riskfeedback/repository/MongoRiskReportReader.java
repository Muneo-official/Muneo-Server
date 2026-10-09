package com.example.muneoserver.domain.riskfeedback.repository;

import com.example.muneoserver.domain.riskfeedback.document.RiskReportDocument;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoRiskReportReader implements RiskReportReader {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<RiskReportDocument> findById(ObjectId id) {
        return Optional.ofNullable(mongoTemplate.findById(id, RiskReportDocument.class));
    }
}
