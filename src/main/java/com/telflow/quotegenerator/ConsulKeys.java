package com.telflow.quotegenerator;

/**
 * Consul Keys used by Quote Generator
 * 
 * @author Sandeep Vasani
 */
public class ConsulKeys {

    /**
     * the inbox topic
     */
    public static final String APP_INBOX_TOPIC = "/inbox";

    /**
     * The protocol for fabric to use
     */
    public static final String ENV_FABRIC_PROTOCOL = "/fabric/protocol";
    
    /**
     * The fabric host
     */
    public static final String ENV_FABRIC_HOST = "/fabric/host";
    
    /**
     * The port for fabric.
     */
    public static final String ENV_FABRIC_PORT = "/fabric/port";

    /**
     * fabric user
     */
    public static final String ENV_FABRIC_USER = "/fabric/esbHttpUser";

    /**
     * fabric password
     */
    public static final String ENV_FABRIC_PASSWORD = "/fabric/secure/esbHttpPassword";

    /**
     * The endpoint for kafka. Used by anything that requires kafka access
     */
    public static final String ENV_KAFKA_ENDPOINT = "/kafka/kafkaEndpoint";

    /**
     * Transition Action for which to generate Quote
     */
    public static final String APP_TRANSITION_ACTION = "/transitionAction";

    /**
     * Notification Template to generate Quote
     */
    public static final String APP_NOTIFY_TEMPLATE = "/notifyTemplate";

    private ConsulKeys() {
        // do nothing
    }
}
