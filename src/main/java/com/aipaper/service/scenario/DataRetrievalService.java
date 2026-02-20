package com.aipaper.service.scenario;

import com.aipaper.dto.UserProfileResult;

public interface DataRetrievalService {

    UserProfileResult fetchUserByEmail(String email);
}
