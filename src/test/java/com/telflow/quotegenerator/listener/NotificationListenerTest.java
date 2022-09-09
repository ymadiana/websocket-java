package com.telflow.quotegenerator.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteraction;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotification;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotificationType;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotificationTypes;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionResponse;
import biz.dgit.schemas.telflow.cim.v3.ManageInventoryEntityNotification;
import biz.dgit.schemas.telflow.cim.v3.OperationResponse;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRole;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRolePartyInteractionRoleType;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRoles;
import biz.dgit.schemas.telflow.cim.v3.PartyRole;
import biz.dgit.schemas.telflow.cim.v3.QuoteSummary;

import com.telflow.fabric.test.FabricTestHelper;
import com.telflow.factory.common.helper.FabricHelper;

import java.io.IOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;

/**
 * Test ({@code NotificationListener}
 * @author Sandeep Vasani
 */
public class NotificationListenerTest {

    private FabricHelper mockFabricHelper;

    private ConsumerRecord<String, String> record;

    private NotificationListener listener;

    private BusinessInteraction biTemplate;

    @BeforeEach
    public void init() throws IOException {
        this.biTemplate = new BusinessInteraction()
            .withID("ORD123")
            .withBusinessInteractionInvolves(new PartyInteractionRoles()
                .withPartyInteractionRole(new PartyInteractionRole()
                .withInteractionRoleType(PartyInteractionRolePartyInteractionRoleType.COMMERCIALLY_RESPONSIBLE)
                .withPartyRole(new PartyRole().withID("PTR000000000012"))))
            .withQuote(new QuoteSummary().withVersion("1"));
        this.mockFabricHelper = mock(FabricHelper.class);
        doReturn(FabricTestHelper.toString(new OperationResponse())).
            when(this.mockFabricHelper).post(any(), eq("OnDemandNotification"), any());
        String response = FabricTestHelper.toString(new ManageBusinessInteractionResponse().
            withBusinessInteraction(this.biTemplate));
        doReturn(response).when(this.mockFabricHelper).post(any(), eq("GetBusinessInteraction"), any());
        doReturn(response).when(this.mockFabricHelper).post(any(), eq("UpdateBusinessInteraction"), any());
        this.record = mock(ConsumerRecord.class);
    }

    @Test
    public void BINotificationWithValidAction() throws IOException {
        BusinessInteractionNotification notification = new BusinessInteractionNotification()
            .withUser(new PartyRole().withID("PTR000000000012"))
            .withTypes(new BusinessInteractionNotificationTypes().withType(new BusinessInteractionNotificationType()
            .withCategory("Status").withType("Actions").withDescription("VALID_ACTION")))
            .withBusinessInteraction(this.biTemplate);
        doReturn(FabricTestHelper.toString(notification)).when(this.record).value();

        this.listener = new NotificationListener(this.mockFabricHelper, "VALID_ACTION", "templateId");
        this.listener.handleMessage(this.record);

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("OnDemandNotification"), any());
    }

    @Test
    public void BINotificationWithInvalidAction() throws IOException {
        BusinessInteractionNotification notification = new BusinessInteractionNotification()
                .withUser(new PartyRole().withID("PTR000000000012"))
            .withTypes(new BusinessInteractionNotificationTypes().withType(new BusinessInteractionNotificationType()
            .withCategory("Status").withType("Actions").withDescription("OTHER_ACTION")))
            .withBusinessInteraction(this.biTemplate);
        doReturn(FabricTestHelper.toString(notification)).when(this.record).value();

        this.listener = new NotificationListener(this.mockFabricHelper, "VALID_ACTION", "templateId");
        this.listener.handleMessage(this.record);

        verify(this.mockFabricHelper, times(0))
            .post(any(), eq("OnDemandNotification"), any());
    }

    @Test
    public void notBINotification() throws IOException {
        doReturn(FabricTestHelper.toString(new ManageInventoryEntityNotification())).when(this.record).value();

        this.listener = new NotificationListener(this.mockFabricHelper, "VALID_ACTION", "templateId");
        this.listener.handleMessage(this.record);

        verify(this.mockFabricHelper, times(0))
            .post(any(), eq("OnDemandNotification"), any());
    }

    @Test
    public void nullNotification() throws IOException {
        doReturn(null).when(this.record).value();

        this.listener = new NotificationListener(this.mockFabricHelper, "VALID_ACTION", "templateId");
        this.listener.handleMessage(this.record);

        verify(this.mockFabricHelper, times(0))
            .post(any(), eq("OnDemandNotification"), any());
    }
}
