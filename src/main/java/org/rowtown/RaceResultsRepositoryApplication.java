package org.rowtown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the Race Results Repository.
 *
 * This microservice provides version-controlled storage and management for
 * rowing regatta timing data based on the Timing Data Interchange EMF schema.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class RaceResultsRepositoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaceResultsRepositoryApplication.class, args);
    }
}
