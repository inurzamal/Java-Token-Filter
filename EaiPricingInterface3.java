import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EaiPricingInterface {

    private static final String RATE_XPATH = "//EAIPricingResponse/Body/BaseRateResultSet/BaseRate [WhichBaseRateEntry='Final']/BaseRate";
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

    public EaiPricingResponse getRate(EaiPricingRequest_Celws request) {
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

        xmlUtil.addElement(sb, "LoanClass", request.getLoan
