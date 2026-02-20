package com.aipaper.service.scenario.llm;

import com.aipaper.dto.UserProfileResult;
import com.aipaper.exception.SchemaValidationException;
import com.aipaper.repository.UserProfileRepository;
import com.aipaper.service.LlmProvider;
import com.aipaper.service.LlmRoutingService;
import com.aipaper.tools.UserProfileQueryTool;
import com.aipaper.validation.LlmResponseValidator;
import org.springframework.stereotype.Service;

@Service
public class LlmDataRetrievalService {

    private final LlmRoutingService routingService;
    private final UserProfileRepository userProfileRepository;
    private final LlmResponseValidator validator;

    public LlmDataRetrievalService(LlmRoutingService routingService,
                                   UserProfileRepository userProfileRepository,
                                   LlmResponseValidator validator) {
        this.routingService = routingService;
        this.userProfileRepository = userProfileRepository;
        this.validator = validator;
    }

    public UserProfileResult fetchUserByEmail(LlmProvider provider, String email) {
        String prompt = String.format(
                "Look up the user profile for email address: %s. " +
                "Use the findUserByEmail tool to query the database, then return the result as JSON " +
                "with exactly these fields: email, firstName, lastName, phone, address.", email);

        try {
            UserProfileResult result = routingService.getClient(provider)
                    .prompt()
                    .user(prompt)
                    .tools(new UserProfileQueryTool(userProfileRepository))
                    .call()
                    .entity(UserProfileResult.class);

            validator.validate(result);
            return result;

        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationException(
                    "LLM data retrieval failed for email " + email + ": " + e.getMessage(), e);
        }
    }
}
