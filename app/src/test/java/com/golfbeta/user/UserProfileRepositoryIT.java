package com.golfbeta.user;

import com.golfbeta.aws.CloudFrontSignedUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserProfileRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.3")
            .withDatabaseName("golfbeta")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // ensure Flyway runs
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired UserProfileRepository repo;

    @MockBean
    CloudFrontSignedUrlService cloudFrontSignedUrlService;

    @Test
    void can_insert_and_read_profile() {
        var p = new UserProfile();
        p.setFirebaseId("uid-123");
        p.setEmail("user@example.com");
        p.setFavouriteColour("teal");
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());

        repo.save(p);

        var fetched = repo.findByFirebaseId("uid-123").orElseThrow();
        assertThat(fetched.getFavouriteColour()).isEqualTo("teal");
    }
}
