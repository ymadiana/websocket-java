package com.telflow.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint(value = "/server")
public class WebsocketServer {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketServer.class);
    
    private Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    public WebsocketServer() {
        
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WEBSOCKET OPEN");
        LOG.info(String.format("WebSocket opened: %s", session.getId()));
    }

    @OnMessage
    public void onMessage(String message) throws IOException {
        LOG.info(String.format("Message received: %s", message));
        broadcast(message);
    }

    @OnClose
    public void onClose(CloseReason reason, Session session) {
        System.out.println("WEBSOCKET CLOSE");
        LOG.info(String.format("Closing a WebSocket (%s) due to %s", session.getId(), reason.getReasonPhrase()));
    }

    @OnError
    public void onError(Session session, Throwable t) {
        LOG.info(String.format("Error in WebSocket session %s%n", session == null ? "null" : session.getId()), t);
    }
    
    private void broadcast(String message) {
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result ->  {
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException());
                }
            });
        });
    }

}
