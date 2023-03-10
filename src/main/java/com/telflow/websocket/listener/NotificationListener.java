package com.telflow.websocket.listener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inomial.secore.kafka.MessageHandler;
import com.telflow.cim.converter.CimConverter;
import com.telflow.cim.converter.impl.CimConverterImpl;
import com.telflow.websocket.WebsocketProcessor;
import com.telflow.websocket.WebsocketServer;

import biz.dgit.schemas.telflow.cim.v3.BusinessInteraction;
import biz.dgit.schemas.telflow.cim.v3.BusinessInteractionNotificationTypes;
import biz.dgit.schemas.telflow.cim.v3.ManageBusinessInteractionNotification;

/**
 * Listen for Kafka message and send them to the processor
 * @author Sandeep Vasani
 */
public class NotificationListener implements MessageHandler {

    private static final transient Logger LOG = LoggerFactory.getLogger(NotificationListener.class);

    private static final CimConverter CIM_CONVERTER = new CimConverterImpl();

    private WebsocketProcessor processor;

    public NotificationListener(WebsocketServer server) {
        this.processor = new WebsocketProcessor(server);
    }

    @Override
    public void handleMessage(ConsumerRecord<String, String> record) {
        String message = record.value();

        if (StringUtils.isEmpty(message)) {
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Received kafka notification: headers: {} body: {}", record.headers(), message);
        }

        try {
            ManageBusinessInteractionNotification notification = CIM_CONVERTER.unmarshal(message,
                    ManageBusinessInteractionNotification.class);

            if (!isUpdateAction(notification)) {
                return;
            }
            
            String biid = extractBiid(notification);
            
            LOG.info("Received Update BI Notification: headers: {} body: {}", record.headers(), message);
            this.processor.process(biid);
        }  catch (IOException | URISyntaxException | InterruptedException e) {
            LOG.error("Failed to send message: ", e);
        }
    }

    private boolean isUpdateAction(ManageBusinessInteractionNotification notification) {


        return Optional.ofNullable(notification).
            map(ManageBusinessInteractionNotification::getTypes).
            map(BusinessInteractionNotificationTypes::getType).
            map(List::stream).
            orElse(Stream.empty()).
            filter(type -> "Action".equals(type.getCategory())).
            filter(type -> "Update".equals(type.getType())).
            findFirst().orElse(null) != null;
    }
    
    private String extractBiid(ManageBusinessInteractionNotification notification) {
        return Optional.ofNullable(notification).
            map(ManageBusinessInteractionNotification::getBusinessInteraction).
            map(BusinessInteraction::getID).
            orElse(null);
    }

}
