package com.telflow.quotegenerator.listener;

import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotificationTypes;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionNotification;

import com.inomial.secore.kafka.MessageHandler;
import com.telflow.cim.converter.CimConverter;
import com.telflow.cim.converter.impl.CimConverterImpl;
import com.telflow.factory.common.helper.FabricHelper;
import com.telflow.quotegenerator.QuoteGenerationProcessor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.bind.JAXBException;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen for Kafka message and send them to the processor
 * @author Sandeep Vasani
 */
public class NotificationListener implements MessageHandler {

    private static final transient Logger LOG = LoggerFactory.getLogger(NotificationListener.class);

    private static CimConverter CIM_CONVERTER;

    private String transitionAction;

    private QuoteGenerationProcessor processor;

    static {
        try {
            CIM_CONVERTER = new CimConverterImpl(); 
        } catch (JAXBException e) {
            LOG.error("Failed to initialised CimConverter: ", e);
        }
    }

    public NotificationListener(FabricHelper helper, String transitionAction, String templateId) {
        this.transitionAction = transitionAction;
        this.processor = new QuoteGenerationProcessor(helper, templateId, CIM_CONVERTER);
    }

    @Override
    public void handleMessage(ConsumerRecord<String, String> record) {
        String message = record.value();

        if (StringUtils.isEmpty(message)) {
            return;
        }
        
        LOG.trace("Received kafka notification: headers: {} body: {}", record.headers(), message);

        try {
            ManageBusinessInteractionNotification notification = CIM_CONVERTER.unmarshal(message,
                    ManageBusinessInteractionNotification.class);
            
            if (!isGenerateQuoteAction(notification)) {
                return;
            }
            LOG.info("Received Generate Quote Notification: headers: {} body: {}", record.headers(), message);
            this.processor.process(notification);
        }  catch (IOException e) {
            LOG.error("Failed to generate quote: ", e);
        }
    }

    private boolean isGenerateQuoteAction(ManageBusinessInteractionNotification notification) {
        
        
        return Optional.ofNullable(notification).
            map(ManageBusinessInteractionNotification::getTypes).
            map(BusinessInteractionNotificationTypes::getType).
            map(List::stream).
            orElse(Stream.empty()).
            filter(type -> "Status".equals(type.getCategory())).
            filter(type -> "Actions".equals(type.getType())).
            filter(type -> type.getDescription().contains(this.transitionAction)).
            findFirst().orElse(null) != null;
    }

}
