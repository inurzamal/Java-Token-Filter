/*
 * This AuthenticationFilter class, handling Bearer token authentication, 
 * validating the token via a Ping service, and setting up the security context for authenticated users. 
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String SUB = "sub";
    public static final String CLIENT_ID = "client_id";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";
    public static final String OPTIONS = "OPTIONS";
    public static final String ORIGIN = "Origin";
    public static final String SCOPE = "scope";
    public static final String HLFUS = "HLFUS";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final TypeReference<HashMap<String, Object>> valueTypeRef = new TypeReference<>() {};

    @Autowired
    private RestTemplate restTemplate;

    @Value("${application.config.ping.clientid}")
    private String clientId;

    @Value("${application.config.ping.pingurl}")
    private String pingUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException {
        final String authorizationHeaderValue = request.getHeader(AUTHORIZATION);

        if (authorizationHeaderValue != null && authorizationHeaderValue.startsWith(BEARER)) {
            String token = authorizationHeaderValue.substring(7); // Remove "Bearer "

            LOGGER.info(String.format("$$$ filter.doFilter token = %s.", token));

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add(CLIENT_ID, clientId);

            // Create PingRequest object
            PingRequest pingRequest = new PingRequest();
            pingRequest.setToken(token);
            pingRequest.setUrl(pingUrl);

            // Perform POST request to Ping service
            ResponseEntity<String> result = restTemplate.postForEntity(pingRequest.getUrl(), new HttpEntity<>(requestBody), String.class);

            // Validate response and set security context
            validateAndSetContext(request, response, result);

            filterChain.doFilter(request, response);
        } else {
            LOGGER.info("Throwing Unauthorized exception as there is no token in header");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        }
    }

    private void validateAndSetContext(HttpServletRequest request, HttpServletResponse response, ResponseEntity<String> result) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, Object> userinfo = mapper.readValue(result.getBody(), valueTypeRef);

        LOGGER.info(String.format("Ping request response = %s.", userinfo));

        if (userinfo.get(SUB) == null || !userinfo.get(SCOPE).toString().contains(HLFUS)) {
            LOGGER.info("Throwing Unauthorized exception as invalid token is received.");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        }

        // Create UserPrinciple object
        UserPrinciple userDetails = new UserPrinciple(userinfo.get(SUB).toString(), request.getHeader("username"));

        // Set authentication context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean isPreflightRequest(HttpServletRequest request) {
        boolean isActuatorURI = request.getRequestURI().contains("/actuator/");
        boolean isCorsRequest = OPTIONS.equalsIgnoreCase(request.getMethod()) && request.getHeader(ORIGIN) != null;
        LOGGER.info("Is Request is Preflight: " + (isCorsRequest || isActuatorURI));
        return isCorsRequest || isActuatorURI;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPreflightRequest(request);
    }

    // Inner class representing PingRequest
    private static class PingRequest {
        private String token;
        private String url;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    // Inner class representing UserPrinciple
    private static class UserPrinciple {
        private String username; // Assuming username is userId
        private String userId;

        public UserPrinciple(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
