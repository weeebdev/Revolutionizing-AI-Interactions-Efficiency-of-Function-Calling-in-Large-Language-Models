package com.aipaper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserProfileResult(
        @JsonProperty(required = true, value = "email") String email,
        @JsonProperty(required = true, value = "firstName") String firstName,
        @JsonProperty(required = true, value = "lastName") String lastName,
        @JsonProperty(value = "phone") String phone,
        @JsonProperty(value = "address") String address
) {}
