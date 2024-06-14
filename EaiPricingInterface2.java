/*
EaiPricingInterface class to make a REST call instead of a SOAP call, we'll need to make several adjustments. The major changes will involve:

Using RestTemplate to make the REST call instead of SoapServiceInvoker.
Building the request body as a JSON string instead of XML.
Setting the appropriate headers for a REST call.
Parsing the JSON response instead of XML.
Here's how the modified EaiPricingInterface class might look:
*/

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Map;

@Service
public class EaiPricingInterface {

    private static final Logger log = LoggerFactory.getLogger(EaiPricingInterface.class);

    @Autowired
    private ProcessControlHandler control;

    @Autowired
    private OAuthenticationService oAuthenticationService;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    private void init() {
        // Any initialization if needed
    }

    public EaiPricingResponse getRate(EaiPricingRequest_Celws request) throws Exception {
        try {
            // Convert the request object to a JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = objectMapper.writeValueAsString(request);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", getBearerToken());

            // Prepare the request entity
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // Retrieve the URL from the database
            String eaiURL = StringUtils.trimToNull(control.getControlValue(ProcessControlConstants.EAI_URL));
            if (eaiURL == null) {
                log.error("Else in getRate: EaiPricingRequest_Celws");
                throw new ApplicationException("Error in getRate as EAI_URL is NULL/Empty");
            }

            log.debug("R2.19:: EaiPricingInterface: URL in getRate: EaiPricingRequest_Celws:: {}", eaiURL);

            // Make the REST call
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    eaiURL, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            // Process the response
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody == null) {
                throw new RestClientException("No response received from REST service");
            }

            // Log the response
            log.info("EaiPricingInterface: CELWS Pricing Response: {}", responseBody);

            // Build the EaiPricingResponse from the response body
            EaiPricingResponse response = buildResponse(responseBody);

            if (request.isCaptureXml() && response != null) {
                response.setJsonResponse(responseBody.toString());
            }

            return response;

        } catch (RestClientException e) {
            log.error("EaiPricingInterface: Exception in getRate: EaiPricingRequest_Celws::", e);
            EaiPricingResponse err = new EaiPricingResponse();
            err.setStatusMessage(e.getMessage());
            return err;
        }
    }

    private String getBearerToken() {
        return "Bearer " + oAuthenticationService.getOathAccessToken();
    }

    private EaiPricingResponse buildResponse(Map<String, Object> responseBody) {
        // Convert the response body map to EaiPricingResponse object
        EaiPricingResponse response = new EaiPricingResponse();
        // Assuming the map keys and types match the EaiPricingResponse fields
        // Set the fields accordingly, for example:
        response.setStatusCode((String) responseBody.get("statusCode"));
        response.setStatusMessage((String) responseBody.get("statusMessage"));
        // Add other fields as necessary
        return response;
    }
}
