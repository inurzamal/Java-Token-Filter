@Service
public class EaiPricingInterface {

    private static final String RATE_XPATH = "//EAIPricingResponse/Body/BaseRateResultSet/BaseRate [WhichBaseRateEntry='Final']/BaseRate";
    private static final String STATUSCODE_XPATH = "//EAIPricingResponse/Body/ReturnCode";
    private static final String STATUSMSG_XPATH = "//EAIPricingResponse/Body/ErrorMessage";
    private static final String XMLHDR = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String EXCLUSION_MSG_XPATH = "//EAIPricingResponse/Body/ExclusionResultSet/Exclusion";

    private final XMLUtil xmlUtil;
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

    public Mono<EaiPricingResponse> getRate(EaiPricingRequest_Celws request) {
        StringBuffer sb = xmlUtil.startXmlDocument(XMLHDR);
        xmlUtil.openElement(sb, "EAIPricingRequest");

        // Construct the XML request
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
            return oAuthenticationService.getOathAccessToken()
                    .flatMap(token -> {
                        return Mono.fromCallable(() -> {
                            String result = soapServiceInvoker.invokeService(eaiURL, sb.toString(), new Properties() {{
                                put(CleaConstants.CONTENT_TYPE, "text/xml");
                                put("Authorization", "Bearer " + token);
                            }});

                            result = StringUtil.removeString(result, "xmlns=\"http://HEQAPPAZPHX01.wellsfargo.com/\"");

                            Document doc = xmlUtil.getXMLDocument(result);

                            log.info("EaiPricingInterface: CELWS Pricing Response: {}", doc.asXML());

                            EaiPricingResponse response = new EaiPricingResponse();
                            response.setRate(xmlUtil.getNodeValue(doc, RATE_XPATH));
                            response.setStatusCode(xmlUtil.getNodeValue(doc, STATUSCODE_XPATH));
                            response.setStatusMsg(xmlUtil.getNodeValue(doc, STATUSMSG_XPATH));
                            response.setExclusionMsg(xmlUtil.getNodeValue(doc, EXCLUSION_MSG_XPATH));

                            if (request.isCaptureXml() && response != null) {
                                response.setXml(result);
                            }

                            return response;
                        });
                    })
                    .doOnError(e -> log.error("Error processing EAI pricing request", e));
        } else {
            log.error("EAI URL not configured.");
            return Mono.error(new IllegalStateException("EAI URL not configured."));
        }
    }
}
