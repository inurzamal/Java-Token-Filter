/*
*
The EaiPricingInterface class is a service component in a Spring application designed to interact with an external SOAP service. 
The primary function of this class is to generate an XML request based on input parameters, send this request to the SOAP service, 
and then process the XML response received from the service to build and return a response object (EaiPricingResponse).
*
*/ 


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Properties;

@Service
public class EaiPricingInterface {

    private static final String RATE_XPATH = "//EAIPricingResponse/Body/BaseRateResultSet/BaseRate[WhichBaseRateEntry='Final']/BaseRate";
    private static final String STATUSCODE_XPATH = "//EAIPricingResponse/Body/ReturnCode";
    private static final String STATUSMSG_XPATH = "//EAIPricingResponse/Body/ErrorMessage";
    private static final String XMLHDR = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String EXCLUSION_MSG_XPATH = "//EAIPricingResponse/Body/ExclusionResultSet/Exclusion";

    private XMLUtil xmlUtil;
    private static final Logger log = LoggerFactory.getLogger(EaiPricingInterface.class);

    @Autowired
    private ProcessControlHandler control;

    @Autowired
    private OAuthenticationService oAuthenticationService;

    @Autowired
    private SoapServiceInvoker soapServiceInvoker;

    @PostConstruct
    private void init() {
        this.xmlUtil = XMLUtil.getInstance();
    }

    public EaiPricingResponse getRate(EaiPricingRequest_Celws request) throws Exception {
        StringBuffer sb = xmlUtil.startXmlDocument(XMLHDR);
        xmlUtil.openElement(sb, "EAIPricingRequest");

        // Start XML request information
        xmlUtil.openElement(sb, "Header");
        xmlUtil.addElement(sb, "CorrelationId", request.getAccountNumber());
        xmlUtil.addElement(sb, "requestorID", request.getRequestorId());
        xmlUtil.addElement(sb, "CreatorId", "CLEAHEQ");
        xmlUtil.addElement(sb, "AuthorizationId", "NONE");
        xmlUtil.addElement(sb, "ApplicationCode", "BT");
        xmlUtil.addElement(sb, "ChannelCode", "INT");
        xmlUtil.addElement(sb, "AccountingUnit");
        xmlUtil.addElement(sb, "CreationTimestamp", new Date(), "yyyyMMddHHmmssSSS");
        xmlUtil.addElement(sb, "MessageType", "RQST");
        xmlUtil.addElement(sb, "Timeout", 30000);
        xmlUtil.closeElement(sb, "Header");

        // Start Body of Document
        xmlUtil.openElement(sb, "Body");
        xmlUtil.addElement(sb, "AmortizationTerm", request.getTerm());
        xmlUtil.addElement(sb, "AmortizationType", request.getAmortizationType());
        xmlUtil.addElement(sb, "AutoPay", request.isAutopay());
        xmlUtil.addElement(sb, "Employee", request.isEmployee());
        xmlUtil.addElement(sb, "Premier", request.isPremier());
        xmlUtil.addElement(sb, "CLTV", request.getCltv());
        xmlUtil.addElement(sb, "CreditGrade", request.getCreditGrade());
        xmlUtil.addElement(sb, "Fico1", request.getFico1());
        xmlUtil.addElement(sb, "Fico2", request.getFico2());
        xmlUtil.addElement(sb, "FicoScore", request.getFicoScore());
        xmlUtil.addElement(sb, "LienPosition", request.getLienPosition());

        if (request.getNoteAmount() >= 0.01f && request.getNoteAmount() < 1.0f) {
            xmlUtil.addElement(sb, "LoanAmount", 1);
        } else {
            xmlUtil.addElement(sb, "LoanAmount", StringUtil.toInt(request.getNoteAmount()));
        }

        xmlUtil.addElement(sb, "LoanClass", request.getLoanClass());
        xmlUtil.addElement(sb, "Occupancy", request.getOccupancy());
        xmlUtil.addElement(sb, "PrePayYears", request.getPrePayYears());
        xmlUtil.addElement(sb, "Product", request.getProduct());
        xmlUtil.addElement(sb, "ProductFeature", request.getProductFeature());
        xmlUtil.addElement(sb, "PropertyType", request.getPropertyType());
        xmlUtil.addElement(sb, "QuoteDate", request.getQuoteDate(), "yyyyMMddHHmmssSSS");
        xmlUtil.addElement(sb, "PropertyState", request.getPropertyState());
        xmlUtil.addElement(sb, "PricingState", request.getPricingState());
        xmlUtil.addElement(sb, "ChannelSource", request.getChannelSource());
        xmlUtil.addElement(sb, "MarketSource", request.getMarketSource());

        // P0506594 MarginAdjuster and Rate Adjuster updated
        xmlUtil.addElement(sb, "MiscMarginAdjuster", "");
        xmlUtil.addElement(sb, "MiscRateAdjuster", request.getRateAdjusters());
        xmlUtil.addElement(sb, "CallerId", "42");
        xmlUtil.closeElement(sb, "Body");
        xmlUtil.closeElement(sb, "EAIPricingRequest");

        if (request.isCaptureXml()) {
            request.setXml(sb.toString());
        }
        log.info("CELWS Pricing Request: {}", sb);

        String eaiURL = StringUtils.trimToNull(control.getControlValue(ProcessControlConstants.EAI_URL));
        if (eaiURL != null) {
            log.debug("R2.19:: EaiPricingInterface: URL in getRate: EaiPricingRequest_Celws:: {}", eaiURL);
            try {
                String result = soapServiceInvoker.invokeService(eaiURL, sb.toString(), new Properties() {
                    {
                        put(CleaConstants.CONTENT_TYPE, "text/xml");
                        put("Authorization", getBearerToken());
                    }
                });

                result = StringUtil.removeString(result, "xmlns=\"http://HEQAPPAZPHX01.wellsfargo.com/\"");

                Document doc = xmlUtil.getXMLDocument(result);

                log.info("EaiPricingInterface: CELWS Pricing Response: {}", doc.asXML());

                EaiPricingResponse rsp = buildResponse(doc);

                if (request.isCaptureXml() && rsp != null) {
                    rsp.setXml(result);
                }

                return rsp;

            } catch (Exception e) {
                log.error("EaiPricingInterface: Exception in getRate: EaiPricingRequest_Celws::", e);
                EaiPricingResponse err = new EaiPricingResponse();
                err.setStatusMessage(e.getMessage());
                return err;
            }

        } else {
            log.error("Else in getRate: EaiPricingRequest_Celws");
            throw new ApplicationException("Error in getRate as EAI_URL is NULL/Empty");
        }
    }

    private String getBearerToken() {
        return "Bearer " + oAuthenticationService.getOathAccessToken();
    }
}
