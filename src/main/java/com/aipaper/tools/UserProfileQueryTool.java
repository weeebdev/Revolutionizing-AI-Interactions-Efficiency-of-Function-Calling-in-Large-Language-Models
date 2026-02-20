package com.aipaper.tools;

import com.aipaper.entity.UserProfile;
import com.aipaper.repository.UserProfileRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class UserProfileQueryTool {

    private final UserProfileRepository repository;

    public UserProfileQueryTool(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "Look up a user profile in the database by their email address. " +
            "Returns the user's email, first name, last name, phone number, and address.")
    public String findUserByEmail(
            @ToolParam(description = "The exact email address to search for") String email) {
        return repository.findByEmail(email)
                .map(this::formatProfile)
                .orElse("{\"error\": \"User not found for email: " + email + "\"}");
    }

    private String formatProfile(UserProfile u) {
        return String.format(
                "{\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\"}",
                u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone() != null ? u.getPhone() : "",
                u.getAddress() != null ? u.getAddress() : "");
    }
}
