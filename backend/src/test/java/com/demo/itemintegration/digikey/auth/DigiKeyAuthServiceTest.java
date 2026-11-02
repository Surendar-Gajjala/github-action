package com.demo.itemintegration.digikey.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.demo.itemintegration.digikey.config.DigiKeyProperties;
import com.demo.itemintegration.digikey.exception.DigiKeyApiException;

class DigiKeyAuthServiceTest {

    private static final String TOKEN_URL = "https://api.digikey.com/v1/oauth2/token";

    private DigiKeyProperties props(String clientId, String secret) {
        return new DigiKeyProperties("https://api.digikey.com", clientId, secret, "IN", "en", "INR", "resistor",
                Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    private record Fixture(DigiKeyAuthService auth, MockRestServiceServer server) {
    }

    private Fixture fixture(DigiKeyProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.digikey.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new DigiKeyAuthService(builder.build(), properties), server);
    }

    @Test
    void cachesTheTokenAcrossCalls() {
        Fixture f = fixture(props("id", "secret"));
        f.server().expect(once(), requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":600}", MediaType.APPLICATION_JSON));

        assertThat(f.auth().getAccessToken()).isEqualTo("abc");
        assertThat(f.auth().getAccessToken()).isEqualTo("abc"); // served from cache, no 2nd HTTP call

        f.server().verify();
    }

    @Test
    void requestsAFreshTokenOnceTheCachedOneExpires() {
        Fixture f = fixture(props("id", "secret"));
        // expires_in 0 minus the 60s buffer => already expired, so each call re-requests.
        f.server().expect(times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":0}", MediaType.APPLICATION_JSON));

        f.auth().getAccessToken();
        f.auth().getAccessToken();

        f.server().verify();
    }

    @Test
    void withoutCredentialsItIsNotConfigured() {
        Fixture f = fixture(props("", ""));

        assertThatThrownBy(() -> f.auth().getAccessToken())
                .isInstanceOf(DigiKeyApiException.class)
                .extracting(e -> ((DigiKeyApiException) e).getReason())
                .isEqualTo(DigiKeyApiException.Reason.NOT_CONFIGURED);
    }
}
