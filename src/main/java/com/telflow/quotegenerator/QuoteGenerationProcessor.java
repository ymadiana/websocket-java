package com.telflow.quotegenerator;

import biz.dgit.schemas.telflow.cim.v3.BusinessInteraction;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotification;
import biz.dgit.schemas.telflow.cim.v3.CustomerOrderRequestParameter;
import biz.dgit.schemas.telflow.cim.v3.LifecycleStateLifecycleStateType;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionRequest;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionResponse;
import biz.dgit.schemas.telflow.cim.v3.ManageEntityNotification;
import biz.dgit.schemas.telflow.cim.v3.Note;
import biz.dgit.schemas.telflow.cim.v3.NoteNoteStakeholderType;
import biz.dgit.schemas.telflow.cim.v3.Notes;
import biz.dgit.schemas.telflow.cim.v3.NotificationProperties;
import biz.dgit.schemas.telflow.cim.v3.NotificationProperty;
import biz.dgit.schemas.telflow.cim.v3.NotificationTemplate;
import biz.dgit.schemas.telflow.cim.v3.OperationResponse;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRole;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRolePartyInteractionRoleType;
import biz.dgit.schemas.telflow.cim.v3.PartyRole;
import biz.dgit.schemas.telflow.cim.v3.PartyRolePartyRoleType;
import biz.dgit.schemas.telflow.cim.v3.QuoteSummary;

import com.telflow.cim.converter.CimConverter;
import com.telflow.cim.util.CimDateUtil;
import com.telflow.factory.common.exception.FabricException;
import com.telflow.factory.common.helper.FabricHelper;

import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build a request for On Demand Notification for Quote Generation
 */
public class QuoteGenerationProcessor {

    private static final String MEDIATYPE_APPLICATION_XML = "application/xml";

    private static final transient Logger LOG = LoggerFactory.getLogger(QuoteGenerationProcessor.class);

    private static final Set<LifecycleStateLifecycleStateType> END_STATES = EnumSet.of(
        LifecycleStateLifecycleStateType.END_FAILURE,
        LifecycleStateLifecycleStateType.END_SUCCESS,
        LifecycleStateLifecycleStateType.DISCARDED);

    private static final String DEFAULT_VERSION = "1";

    private FabricHelper fabricHelper;

    private String notificationTemplateId;

    private CimConverter cimConverter;

    /**
     * Creates a new Quote Generation Processor
     * @param fhelper Utility to send request to fabric
     * @param notificationTemplateId Notification Template ID to use.
     * @param converter {@code CimConverter}
     */
    public QuoteGenerationProcessor(FabricHelper fhelper, String notificationTemplateId,
            CimConverter converter) {
        this.fabricHelper = fhelper;
        this.notificationTemplateId = notificationTemplateId;
        this.cimConverter = converter;
    }

    /**
     * Processes {@code BusinessInteractionNotification} and creates quote document
     * attachment on the BusinessInteraction
     * @param notification {@code BusinessInteractionNotification}
     * @throws IOException {@code IOException}
     */
    public void process(BusinessInteractionNotification notification) throws IOException {
        ManageEntityNotification notifyNotification = new ManageEntityNotification()
            .withUser(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM))
            .withEntity(getBusinessInteraction(notification.getBusinessInteraction()))
            .withProperties(buildProperties(notification))
            .withTemplate(new NotificationTemplate().withID(this.notificationTemplateId));

        Exception exception = sendNotification(notifyNotification);

        // Get and update BI
        updateNotification(notification.getBusinessInteraction().getID(), exception, true);
    }

    /**
     * Get BI that have ContractSummary version.
     * When the version is empty, update the BI first and return the updated one.
     *
     * @param bi initial BI from notification.
     * @return updated BI, if ContractSummary version is empty, otherwise initial BI.
     * @throws IOException {@code IOException}
     */
    private BusinessInteraction getBusinessInteraction(BusinessInteraction bi) throws IOException {
        return updateNotification(bi.getID(), null, false);
    }

    private static NotificationProperties buildProperties(BusinessInteractionNotification notification) {
        String businessInteractionId = notification.getBusinessInteraction().getID();
        String commerciallyResponsiblePartyRoleId = getCommerciallyResponsible(notification);

        // Build Quote #
        String fileName = String.format("%s Quote v%s.pdf",
            businessInteractionId, getQuoteVersion(notification.getBusinessInteraction()));

        return new NotificationProperties().withProperty(
            new NotificationProperty()
                .withKey("draftProperty").withValue("false"),
            new NotificationProperty()
                .withKey("fileName").withValue(fileName),
            new NotificationProperty()
                .withKey("businessInteractionId").withValue(businessInteractionId),
            new NotificationProperty()
                .withKey("commerciallyResponsiblePartyRoleId").withValue(commerciallyResponsiblePartyRoleId)
        );
    }

    private static String getCommerciallyResponsible(BusinessInteractionNotification notification) {
        for (PartyInteractionRole role :
            notification.getBusinessInteraction().getBusinessInteractionInvolves().getPartyInteractionRole()) {
            if (PartyInteractionRolePartyInteractionRoleType.COMMERCIALLY_RESPONSIBLE
                .equals(role.getInteractionRoleType())) {
                return role.getPartyRole().getID();
            }
        }

        return null;
    }

    private static String getQuoteVersion(BusinessInteraction bi) {
        if (bi == null || bi.getQuote() == null) {
            return DEFAULT_VERSION;
        }

        String version = bi.getQuote().getVersion();

        if (StringUtils.isEmpty(version)) {
            return DEFAULT_VERSION;
        }

        int currentInt = Integer.parseInt(version);
        currentInt++;

        return String.valueOf(currentInt);
    }

    private Exception sendNotification(ManageEntityNotification manageEntityNotification) {
        OperationResponse notifyResponse = null;
        try {
            String response = this.fabricHelper.post(this.cimConverter.marshal(manageEntityNotification),
                "OnDemandNotification", MEDIATYPE_APPLICATION_XML);
            notifyResponse = this.cimConverter.unmarshal(response, OperationResponse.class);
        } catch (Exception e) {
            return e;
        }
        return checkForNotifyErrors(notifyResponse);
    }

    private BusinessInteraction updateNotification(String businessInteractionId, Exception exception,
        boolean updateException) throws IOException {
        BusinessInteraction bi = getBusinessInteraction(businessInteractionId);
        return updateBusinessInteraction(bi, exception, updateException);
    }

    private BusinessInteraction getBusinessInteraction(String businessInteractionId) throws IOException {
        ManageBusinessInteractionRequest mbir = new ManageBusinessInteractionRequest()
            .withUser(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM))
            .withRequestParameter(new CustomerOrderRequestParameter().withIncludeInventory(true))
            .withBusinessInteraction(new BusinessInteraction().withID(businessInteractionId));

        ManageBusinessInteractionResponse response =
            callFabric("GetBusinessInteraction", this.cimConverter.marshal(mbir));
        checkForFabricErrors(response);

        return response.getBusinessInteraction();
    }

    @SuppressWarnings("checkstyle:methodlength")
    private BusinessInteraction updateBusinessInteraction(BusinessInteraction bi, Exception exception,
        boolean updateException) throws IOException {

        BusinessInteraction newBi = new BusinessInteraction().withID(bi.getID());

        if (exception != null) {
            LOG.info("Updating Business Interaction with exception: {}", exception);
            newBi.withNotes(new Notes().withNote(new Note()
                .withName("Automated Note").withDescription("Failed to generate quote: " + exception.getMessage())
                .withCreatorName("Quote Generator").withNoteType(NoteNoteStakeholderType.CUSTOMER_FACING)));
        } else {
            if (updateException) {
                return null;
            }

            QuoteSummary summary = bi.getQuote();
            // Not priced or too late to update contract summary, don't do anything.
            if (summary == null ||
                bi.getInteractionStatus() != null && END_STATES.contains(bi.getInteractionStatus().getType())) {
                return null;
            }
            
            newBi.withQuote(summary.withVersion(updateContractVersion(summary.getVersion()))
                .withQuotedDate(CimDateUtil.toXMLGregorianCalendar(new Date())));
        }

        ManageBusinessInteractionRequest mbir = new ManageBusinessInteractionRequest()
            .withUser(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM))
            .withRequestParameter(new CustomerOrderRequestParameter().withIncludeInventory(true))
            .withBusinessInteraction(newBi);

        try {
            ManageBusinessInteractionResponse response =
                    callFabric("UpdateBusinessInteraction", this.cimConverter.marshal(mbir));
            checkForFabricErrors(response);
            return response.getBusinessInteraction();
        } catch (FabricException fex) {
            // Check for empty error message
            if (StringUtils.isEmpty(fex.getCode()) && StringUtils.startsWith(fex.getMessage(),
                    "No public message on fabric exception:")) {
                // Fabric Helper detected an error when none happened, return the message as successful.
                if (exception != null) {
                    return bi.withNotes(newBi.getNotes());
                }

                return bi.withQuote(newBi.getQuote());
            }
            throw fex;
        }
    }

    private static String updateContractVersion(String currentVersion) {
        if (StringUtils.isEmpty(currentVersion)) {
            return DEFAULT_VERSION;
        }

        int currentInt = Integer.parseInt(currentVersion);
        currentInt++;

        return Integer.toString(currentInt);
    }

    private ManageBusinessInteractionResponse callFabric(String action, String request) throws IOException {
        String response = this.fabricHelper.post(request, action, MEDIATYPE_APPLICATION_XML);
        return this.cimConverter.unmarshal(response, ManageBusinessInteractionResponse.class);
    }

    private static void checkForFabricErrors(ManageBusinessInteractionResponse response) {
        if (response == null) {
            throw new RuntimeException("No response was returned from Fabric.");
        }

        if (response.getException() != null) {
            throw new RuntimeException(
                String.format("Fabric returned error code %s with message: %s",
                    response.getException().getCode(), response.getException().getPublicMessage()));
        }
    }

    private static Exception checkForNotifyErrors(OperationResponse response) {
        if (response == null) {
            return new Exception("No response from Notify");
        }
        if (response.getException() != null) {
            return new Exception(String.format("Notify returned error code %s with message: %s",
                response.getException().getCode(), response.getException().getPublicMessage()));
        }
        return null;
    }
}