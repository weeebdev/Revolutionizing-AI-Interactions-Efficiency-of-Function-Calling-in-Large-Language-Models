package com.aipaper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NormalizedDataResult(
        @JsonProperty(required = true, value = "normalizedDate") String normalizedDate,
        @JsonProperty(required = true, value = "normalizedAddress") String normalizedAddress
) {}
