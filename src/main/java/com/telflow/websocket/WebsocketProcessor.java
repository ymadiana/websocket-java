package com.telflow.websocket;

import java.io.IOException;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Websocket client
 */
public class WebsocketProcessor {

    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketProcessor.class);
    
    private final WebsocketServer server;
    
    /**
     * 
     */
    public WebsocketProcessor(WebsocketServer server) {
        this.server = server;
    }
    
    /**
     * @param message Notification message
     * @throws IOException
     * @throws URISyntaxException 
     * @throws InterruptedException 
     */
    public void process(String message) 
            throws IOException, URISyntaxException, InterruptedException {
        this.server.onMessage(message);

        Thread.sleep(1000);
    }

}
