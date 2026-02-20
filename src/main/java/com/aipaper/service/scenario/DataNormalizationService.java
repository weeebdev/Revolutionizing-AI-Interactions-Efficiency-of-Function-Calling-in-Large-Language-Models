package com.aipaper.service.scenario;

import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;

public interface DataNormalizationService {

    NormalizedDataResult normalize(NormalizationRequest request);
}
