package com.aipaper.service.scenario.traditional;

import com.aipaper.dto.UserProfileResult;
import com.aipaper.entity.UserProfile;
import com.aipaper.repository.UserProfileRepository;
import com.aipaper.service.scenario.DataRetrievalService;
import org.springframework.stereotype.Service;

@Service
public class TraditionalDataRetrievalService implements DataRetrievalService {

    private final UserProfileRepository repository;

    public TraditionalDataRetrievalService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserProfileResult fetchUserByEmail(String email) {
        UserProfile user = repository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        return new UserProfileResult(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAddress());
    }
}
