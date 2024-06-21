import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OAuthenticationService {

    @Value("${OATH_AUTHORIZATION}")
    private String authString;

    @Value("${OATH_SERVICE_END_POINT_URL}")
    private String authUrl;

    private final WebClient webClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthenticationService.class);

    private final AtomicReference<String> cachedToken = new AtomicReference<>(null);
    private final AtomicReference<Instant> tokenExpirationTime = new AtomicReference<>(null);

    public OAuthenticationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(authUrl).build();
    }

    public Mono<String> getOathAccessToken() {
        if (cachedToken.get() != null && tokenExpirationTime.get() != null && Instant.now().isBefore(tokenExpirationTime.get())) {
            LOGGER.info("Using cached token");
            return Mono.just(cachedToken.get());
        }

        String base64Creds = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));

        return webClient.post()
                .header("Authorization", "Basic " + base64Creds)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .doOnNext(response -> {
                    if (response == null || !response.containsKey("access_token")) {
                        throw new WebClientResponseException("No access token received from auth service", 500, "Internal Server Error", null, null, null);
                    }
                    cachedToken.set(response.get("access_token"));
                    int expiresIn = Integer.parseInt(response.get("expires_in")) - 60; // Subtract 60 seconds to account for potential delays
                    tokenExpirationTime.set(Instant.now().plusSeconds(expiresIn));
                    LOGGER.info("Fetched new token");
                })
                .map(response -> response.get("access_token"))
                .doOnError(e -> LOGGER.error("Oath Service Exception:", e));
    }
}
