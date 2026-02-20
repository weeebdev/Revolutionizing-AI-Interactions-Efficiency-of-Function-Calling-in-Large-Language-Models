package com.aipaper.config;

import com.aipaper.entity.UserProfile;
import com.aipaper.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedDatabase(UserProfileRepository repo) {
        return args -> {
            if (repo.count() > 0) {
                log.info("Database already seeded — skipping initialization");
                return;
            }

            log.info("Seeding database with sample user profiles...");

            repo.save(new UserProfile(
                    "alice.johnson@example.com", "Alice", "Johnson",
                    "+1-555-0101", "742 Evergreen Terrace, Springfield, IL 62704"));

            repo.save(new UserProfile(
                    "bob.smith@example.com", "Bob", "Smith",
                    "+1-555-0102", "221B Baker Street, London, UK NW1 6XE"));

            repo.save(new UserProfile(
                    "carol.williams@example.com", "Carol", "Williams",
                    "+1-555-0103", "350 Fifth Avenue, New York, NY 10118"));

            repo.save(new UserProfile(
                    "david.brown@example.com", "David", "Brown",
                    "+1-555-0104", "1600 Pennsylvania Ave, Washington, DC 20500"));

            repo.save(new UserProfile(
                    "eve.davis@example.com", "Eve", "Davis",
                    "+1-555-0105", "1 Infinite Loop, Cupertino, CA 95014"));

            log.info("Seeded {} user profiles", repo.count());
        };
    }
}
