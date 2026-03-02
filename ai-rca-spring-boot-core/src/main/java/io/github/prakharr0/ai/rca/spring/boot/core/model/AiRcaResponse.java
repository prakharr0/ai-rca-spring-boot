package io.github.prakharr0.ai.rca.spring.boot.core.model;

import java.util.List;

public record AiRcaResponse(
        double analysisConfidence,
        List<String> missingInformation,
        List<RootCause> rootCauses
) {}
