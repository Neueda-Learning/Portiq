package com.portiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks on the security filter chain, driven through the real application context.
 * These are the assertions that would catch a future change quietly reopening one of these holes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=an-integration-test-secret-of-at-least-32-bytes",
        "app.owner.username=owner",
        "app.owner.password=integration-test-password",
        // Off by default here so unrelated assertions are not tripped by the shared counter; the
        // one test that cares turns its own expectations around the login bucket.
        "app.security.rate-limit.enabled=false",
        "spring.h2.console.enabled=false"
})
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void logIn() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("integration-test-password");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void anUnauthenticatedApiCallGets401WithAJsonBody() throws Exception {
        // 401 specifically, not 403: the frontend only clears a stale token and redirects to the
        // login page on 401, so a stateless chain's default 403 left the UI stuck.
        mockMvc.perform(get("/api/holdings"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aValidTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"));
    }

    @Test
    void aTamperedTokenIsRejected() throws Exception {
        String tampered = token.substring(0, token.length() - 2) + "xy";

        mockMvc.perform(get("/api/holdings").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutStopsTheTokenWorking() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theWhoAmIEndpointRequiresAToken() throws Exception {
        // It used to sit under a blanket permitAll on /api/auth/**, where an unauthenticated call
        // dereferenced a null Authentication and came back as a 500.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void biometricRegistrationRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/auth/webauthn/registration/options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyResponseCarriesTheHardeningHeaders() throws Exception {
        mockMvc.perform(get("/api/holdings").header("Authorization", "Bearer " + token))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void anErrorResponseAlsoCarriesTheHeaders() throws Exception {
        mockMvc.perform(get("/api/holdings"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void theH2ConsoleIsNotReachableWhenDisabled() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBadPasswordSaysNothingAboutWhetherTheUserExists() throws Exception {
        String unknownUser = messageForLogin("no-such-user", "whatever");
        String wrongPassword = messageForLogin("owner", "wrong-password");

        assertThat(unknownUser)
                .as("the two failures must be indistinguishable, or the response enumerates usernames")
                .isEqualTo(wrongPassword);
    }

    private String messageForLogin(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText();
    }
}
