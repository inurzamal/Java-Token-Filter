/*
 * This class is to generate access token or Bearer token. 
 * we make a Rest call to an URL and provide clientId & clientSecret (i.e. username & password) in headers and we get response, 
 * we extract token from the response.
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class OAuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthenticationService.class);

    @Value("${OATH_AUTHORIZATION}")
    private String authString; // OATH_AUTHORIZATION=clientID:clientSecret

    @Value("${OATH_SERVICE_END_POINT_URL}")
    private String authUrl; // OATH_SERVICE_END_POINT_URL=https://xyz..

    private RestTemplate restTemplate = new RestTemplate();

    public String getOathAccessToken() {
        String accessToken = null;

        try {
            String base64Creds = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = createHeaders(base64Creds);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    authUrl, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RestClientException("No access token received from auth service");
            }

            accessToken = response.getBody().get("access_token");

            if (accessToken == null) {
                throw new RestClientException("Access token key is missing in the response");
            }

        } catch (RestClientException e) {
            LOGGER.error("Oath Service Exception:", e);
            throw e;
        }

        return accessToken;
    }

    private HttpHeaders createHeaders(String base64Creds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        headers.add("Content-Type", "application/x-www-form-urlencoded");
        return headers;
    }
}
