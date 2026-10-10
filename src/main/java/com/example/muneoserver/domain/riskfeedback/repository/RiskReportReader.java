package com.example.muneoserver.domain.riskfeedback.repository;

import com.example.muneoserver.domain.riskfeedback.document.RiskReportDocument;
import java.util.Optional;
import org.bson.types.ObjectId;

public interface RiskReportReader {

    Optional<RiskReportDocument> findById(ObjectId id);
}
