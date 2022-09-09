package com.telflow.quotegenerator;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteraction;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotification;
import biz.dgit.schemas.telflow.cim.v3.Fault;
import biz.dgit.schemas.telflow.cim.v3.LifecycleState;
import biz.dgit.schemas.telflow.cim.v3.LifecycleStateLifecycleStateType;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionRequest;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionResponse;
import biz.dgit.schemas.telflow.cim.v3.ManageEntityNotification;
import biz.dgit.schemas.telflow.cim.v3.NotificationProperty;
import biz.dgit.schemas.telflow.cim.v3.OperationResponse;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRole;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRolePartyInteractionRoleType;
import biz.dgit.schemas.telflow.cim.v3.PartyInteractionRoles;
import biz.dgit.schemas.telflow.cim.v3.PartyRole;
import biz.dgit.schemas.telflow.cim.v3.PartyRolePartyRoleType;
import biz.dgit.schemas.telflow.cim.v3.QuoteSummary;

import com.telflow.cim.converter.CimConverter;
import com.telflow.cim.converter.impl.CimConverterImpl;
import com.telflow.fabric.test.FabricTestHelper;
import com.telflow.factory.common.helper.FabricHelper;

import java.io.IOException;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test class for the Quote Generation processor.
 */
public class QuoteGenerationProcessorTest {

    private static CimConverter CIM_CONVERTER;

    static {
        try {
            CIM_CONVERTER = new CimConverterImpl(); 
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    private BusinessInteraction biTemplate;
    
    private BusinessInteraction biTemplateNoVersion;

    private FabricHelper mockFabricHelper;

    @BeforeEach
    public void init() throws IOException {
        this.biTemplate = new BusinessInteraction()
            .withID("ORD123")
            .withBusinessInteractionInvolves(new PartyInteractionRoles()
                .withPartyInteractionRole(new PartyInteractionRole()
                .withInteractionRoleType(PartyInteractionRolePartyInteractionRoleType.COMMERCIALLY_RESPONSIBLE)
                .withPartyRole(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM))))
            .withQuote(new QuoteSummary().withVersion("1"));
        
        this.biTemplateNoVersion = new BusinessInteraction()
                .withID("ORD123")
                .withBusinessInteractionInvolves(new PartyInteractionRoles()
                    .withPartyInteractionRole(new PartyInteractionRole()
                    .withInteractionRoleType(PartyInteractionRolePartyInteractionRoleType.COMMERCIALLY_RESPONSIBLE)
                    .withPartyRole(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM))))
                .withQuote(new QuoteSummary());

        this.mockFabricHelper = mock(FabricHelper.class);

        mockFabricSuccess();
    }

    @Test
    public void testNotifyRequest() throws IOException {
        BusinessInteractionNotification notification =
            new BusinessInteractionNotification().withBusinessInteraction(this.biTemplate)
                    .withUser(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM));

        QuoteGenerationProcessor processor = new QuoteGenerationProcessor(
            this.mockFabricHelper, "myTemplate", CIM_CONVERTER);
        processor.process(notification);

        ArgumentCaptor<String> notificationArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(this.mockFabricHelper, times(1))
            .post(notificationArgumentCaptor.capture(), eq("OnDemandNotification"), any());

        verify(this.mockFabricHelper, times(2))
            .post(any(), eq("GetBusinessInteraction"), any());

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("UpdateBusinessInteraction"), any());

        ManageEntityNotification notifyRequest = FabricTestHelper.convertTo(
            ManageEntityNotification.class, notificationArgumentCaptor.getValue());
        assertThat(notifyRequest.getUser().getType(), is(PartyRolePartyRoleType.SYSTEM));
        List<NotificationProperty> propertyList = notifyRequest.getProperties().getProperty();
        assertThat(propertyList.get(0).getKey(), is("draftProperty"));
        assertThat(propertyList.get(0).getValue(), is("false"));

        assertThat(propertyList.get(1).getKey(), is("fileName"));
        assertThat(propertyList.get(1).getValue(), is("ORD123 Quote v2.pdf"));

        assertThat(propertyList.get(2).getKey(), is("businessInteractionId"));
        assertThat(propertyList.get(2).getValue(), is("ORD123"));

        assertThat(notifyRequest.getTemplate().getID(), is("myTemplate"));
    }
    
    @Test
    public void testNotifyRequestNoVersion() throws IOException {
        BusinessInteractionNotification notification =
            new BusinessInteractionNotification().withBusinessInteraction(this.biTemplateNoVersion)
                    .withUser(new PartyRole().withType(PartyRolePartyRoleType.SYSTEM));

        QuoteGenerationProcessor processor = new QuoteGenerationProcessor(
            this.mockFabricHelper, "myTemplate", CIM_CONVERTER);
        processor.process(notification);

        ArgumentCaptor<String> notificationArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(this.mockFabricHelper, times(1))
            .post(notificationArgumentCaptor.capture(), eq("OnDemandNotification"), any());

        verify(this.mockFabricHelper, times(2))
            .post(any(), eq("GetBusinessInteraction"), any());

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("UpdateBusinessInteraction"), any());

        ManageEntityNotification notifyRequest = FabricTestHelper.convertTo(
            ManageEntityNotification.class, notificationArgumentCaptor.getValue());
        assertThat(notifyRequest.getUser().getType(), is(PartyRolePartyRoleType.SYSTEM));
        List<NotificationProperty> propertyList = notifyRequest.getProperties().getProperty();
        assertThat(propertyList.get(0).getKey(), is("draftProperty"));
        assertThat(propertyList.get(0).getValue(), is("false"));

        assertThat(propertyList.get(1).getKey(), is("fileName"));
        assertThat(propertyList.get(1).getValue(), is("ORD123 Quote v1.pdf"));

        assertThat(propertyList.get(2).getKey(), is("businessInteractionId"));
        assertThat(propertyList.get(2).getValue(), is("ORD123"));

        assertThat(notifyRequest.getTemplate().getID(), is("myTemplate"));
    }

    @Test
    public void testNotifyRequest_Failure() throws IOException {
        mockNotifyFailure();

        BusinessInteractionNotification notification =
            new BusinessInteractionNotification().withBusinessInteraction(this.biTemplate)
                    .withUser(new PartyRole().withID("PTR000000000012"));

        QuoteGenerationProcessor processor = new QuoteGenerationProcessor(
            this.mockFabricHelper, "myTemplate", CIM_CONVERTER);
        processor.process(notification);

        ArgumentCaptor<String> fabricArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("OnDemandNotification"), any());

        verify(this.mockFabricHelper, times(2))
            .post(any(), eq("GetBusinessInteraction"), any());

        verify(this.mockFabricHelper, times(2))
            .post(fabricArgumentCaptor.capture(), eq("UpdateBusinessInteraction"), any());

        ManageBusinessInteractionRequest fabricRequest = FabricTestHelper.convertTo(
            ManageBusinessInteractionRequest.class, fabricArgumentCaptor.getValue());
        assertNotNull(fabricRequest.getBusinessInteraction().getNotes());
        assertThat(fabricRequest.getBusinessInteraction().getNotes().getNote().size(), is(1));
        assertThat(fabricRequest.getBusinessInteraction().getNotes().getNote().get(0).getDescription(),
            is("Failed to generate quote: Notify returned error code 500 with message: Internal Server Error"));
    }

    @Test
    public void testNotifyRequest_NoResponse() throws IOException {
        mockNotifyException();

        BusinessInteractionNotification notification =
            new BusinessInteractionNotification().withBusinessInteraction(this.biTemplate)
                    .withUser(new PartyRole().withID("PTR000000000012"));

        QuoteGenerationProcessor processor = new QuoteGenerationProcessor(
            this.mockFabricHelper, "myTemplate", CIM_CONVERTER);
        processor.process(notification);

        ArgumentCaptor<String> fabricArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("OnDemandNotification"), any());

        verify(this.mockFabricHelper, times(2))
            .post(any(), eq("GetBusinessInteraction"), any());

        verify(this.mockFabricHelper, times(2))
            .post(fabricArgumentCaptor.capture(), eq("UpdateBusinessInteraction"), any());

        ManageBusinessInteractionRequest fabricRequest = FabricTestHelper.convertTo(
            ManageBusinessInteractionRequest.class, fabricArgumentCaptor.getValue());
        assertNotNull(fabricRequest.getBusinessInteraction().getNotes());
        assertThat(fabricRequest.getBusinessInteraction().getNotes().getNote().size(), is(1));
        assertThat(fabricRequest.getBusinessInteraction().getNotes().getNote().get(0).getDescription(),
            is("Failed to generate quote: Error"));
    }

    @Test
    public void testNotifyRequest_NoBiUpdateAtEndState() throws IOException {
        this.biTemplate = this.biTemplate
            .withInteractionStatus(new LifecycleState().withType(LifecycleStateLifecycleStateType.END_SUCCESS));
        mockFabricSuccess();

        BusinessInteractionNotification notification =
            new BusinessInteractionNotification().withBusinessInteraction(this.biTemplate)
                    .withUser(new PartyRole().withID("PTR000000000012"));

        QuoteGenerationProcessor processor = new QuoteGenerationProcessor(
            this.mockFabricHelper, "myTemplate", CIM_CONVERTER);
        processor.process(notification);

        verify(this.mockFabricHelper, times(1))
            .post(any(), eq("OnDemandNotification"), any());

        verify(this.mockFabricHelper, times(2))
            .post(any(), eq("GetBusinessInteraction"), any());

        verify(this.mockFabricHelper, times(0))
            .post(any(), eq("UpdateBusinessInteraction"), any());
    }

    private void mockFabricSuccess() throws IOException {
        String response = FabricTestHelper.toString(new ManageBusinessInteractionResponse().
            withBusinessInteraction(this.biTemplate));
        doReturn(response).when(this.mockFabricHelper).post(any(), eq("GetBusinessInteraction"), any());
        doReturn(response).when(this.mockFabricHelper).post(any(), eq("UpdateBusinessInteraction"), any());
        doReturn(FabricTestHelper.toString(new OperationResponse())).
            when(this.mockFabricHelper).post(any(), eq("OnDemandNotification"), any());
    }

    private void mockNotifyFailure() throws IOException {
        String response = FabricTestHelper.toString(new OperationResponse().withException(
            new Fault().withCode("500").withPublicMessage("Internal Server Error")));
        doReturn(response).when(this.mockFabricHelper).post(any(), eq("OnDemandNotification"), any());
    }

    private void mockNotifyException() throws IOException {
        doThrow(new IOException("Error")).when(this.mockFabricHelper).post(any(), eq("OnDemandNotification"),
                any());
    }
}
