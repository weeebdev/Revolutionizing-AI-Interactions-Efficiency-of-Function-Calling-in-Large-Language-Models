package com.aipaper.service.scenario.llm;

import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.exception.SchemaValidationException;
import com.aipaper.service.LlmProvider;
import com.aipaper.service.LlmRoutingService;
import com.aipaper.validation.LlmResponseValidator;
import org.springframework.stereotype.Service;

@Service
public class LlmDataNormalizationService {

    private final LlmRoutingService routingService;
    private final LlmResponseValidator validator;

    public LlmDataNormalizationService(LlmRoutingService routingService,
                                       LlmResponseValidator validator) {
        this.routingService = routingService;
        this.validator = validator;
    }

    public NormalizedDataResult normalize(LlmProvider provider, NormalizationRequest request) {
        String prompt = String.format(
                "Normalize the following data and return a JSON object with exactly two fields: " +
                "\"normalizedDate\" and \"normalizedAddress\".\n\n" +
                "Rules:\n" +
                "- normalizedDate: Convert the date to ISO-8601 format (yyyy-MM-dd).\n" +
                "- normalizedAddress: Capitalize words properly, expand abbreviations " +
                "(st→Street, ave→Avenue, blvd→Boulevard, dr→Drive, ln→Lane, rd→Road, " +
                "apt→Apartment, ste→Suite), and keep state codes as 2-letter uppercase.\n\n" +
                "Input date: %s\n" +
                "Input address: %s",
                request.rawDate(), request.rawAddress());

        try {
            NormalizedDataResult result = routingService.getClient(provider)
                    .prompt()
                    .user(prompt)
                    .call()
                    .entity(NormalizedDataResult.class);

            validator.validate(result);
            return result;

        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationException(
                    "LLM normalization failed: " + e.getMessage(), e);
        }
    }
}
