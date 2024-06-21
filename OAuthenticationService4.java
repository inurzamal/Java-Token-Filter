import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import reactor.core.publisher.Mono;

@Component
public class OAuthenticationService {

    @Value("${OATH_AUTHORIZATION}")
    private String authString;

    @Value("${OATH_SERVICE_END_POINT_URL}")
    private String authUrl;

    private WebClient webClient;
    private String accessToken;
    private Instant expiryTime;
    private final ReentrantLock lock = new ReentrantLock();

    @PostConstruct
    private void init() {
        this.webClient = WebClient.builder().baseUrl(authUrl).build();
    }

    public Mono<String> getOathAccessToken() {
        lock.lock();
        try {
            if (accessToken == null || Instant.now().isAfter(expiryTime)) {
                return webClient.post()
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                        .doOnNext(response -> {
                            accessToken = response.get("access_token");
                            int expiresIn = Integer.parseInt(response.get("expires_in"));
                            expiryTime = Instant.now().plusSeconds(expiresIn - 60);  // Subtract 60 seconds to be safe
                        })
                        .map(response -> accessToken)
                        .onErrorMap(WebClientResponseException.class, ex -> new RuntimeException("Error fetching token", ex));
            } else {
                return Mono.just(accessToken);
            }
        } finally {
            lock.unlock();
        }
    }
}
