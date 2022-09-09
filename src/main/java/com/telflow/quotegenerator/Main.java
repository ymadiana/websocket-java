package com.telflow.quotegenerator;

import com.inomial.secore.kafka.MessageConsumer;
import com.inomial.secore.scope.Scope;
import com.telflow.factory.common.exception.InitialisationException;
import com.telflow.factory.common.helper.FabricHelper;
import com.telflow.factory.configuration.management.ConsulManager;
import com.telflow.quotegenerator.listener.NotificationListener;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Main entry point to Quote Generator
 * 
 * @author Sandeep Vasani
 */
public class Main {

    private static transient Logger LOG;

    private static final String APP_NAME = "telflow-quote-generator";

    private static MessageConsumer MESSAGE_CONSUMER;

    private static FabricHelper FABRIC_HELPER;

    private Main() {
        // do nothing
    }

    /**
     * The main entry point for the application.
     * 
     * @param args Any command line arguments that have been passed.
     */
    public static void main(String[] args) {
        // Force libraries that use j.u.l. to use the slf4j handler:
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LOG = LoggerFactory.getLogger(Main.class);

        LOG.info("Starting Quote generator");
        try {
            initConsul();
            
            setupQuoteGenerator();
            LOG.info("Quote generator started");
        } catch (Exception | Error ie) {
            LOG.error("Failed to initialise application, exiting.", ie);
            LOG.error("Properties: {}", System.getProperties());
            LOG.error("Env: {}", System.getenv());
            System.exit(1);
        }
    }

    private static void initConsul() {
        try {
            String consulEndpoint = System.getenv("CONSUL_SERVER");
            LOG.info("Consul Endpoint: {}", consulEndpoint);
            URL url = new URL(consulEndpoint);
            Map<String, String> defaults = getDefaults();
            ConsulManager.init(url, APP_NAME, defaults);
            ConsulManager.addRegisteredObject("taskNotifier", Main::setupQuoteGenerator);
        } catch (MalformedURLException mue) {
            LOG.error("Failed to initialise Consul: ", mue);
            throw new InitialisationException("Failed to initialise Consul", mue);
        }
    }

    private static void initFabricHelper() {
        String endpoint = String.format("%s://%s:%s/api/",
            ConsulManager.getAppKey(ConsulKeys.ENV_FABRIC_PROTOCOL),
            ConsulManager.getAppKey(ConsulKeys.ENV_FABRIC_HOST),
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_PORT));
        LOG.info("Fabric: {}", endpoint);
        FABRIC_HELPER = new FabricHelper(
            endpoint,
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_USER),
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_PASSWORD)
        );
    }

    private static void setupQuoteGenerator() {
        initFabricHelper();

        // Kafka configuration
        Map<String, Object> cp = new HashMap<>();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ConsulManager.getEnvKey(ConsulKeys.ENV_KAFKA_ENDPOINT));
        cp.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        if (MESSAGE_CONSUMER != null) {
            MESSAGE_CONSUMER.shutdown();
        }
        MESSAGE_CONSUMER = new MessageConsumer(cp);

        NotificationListener listener = new NotificationListener(FABRIC_HELPER,
            ConsulManager.getAppKey(ConsulKeys.APP_TRANSITION_ACTION),
            ConsulManager.getAppKey(ConsulKeys.APP_NOTIFY_TEMPLATE));
        MESSAGE_CONSUMER.addMessageHandler(ConsulManager.getAppKey(ConsulKeys.APP_INBOX_TOPIC), listener, Scope.NONE);
        MESSAGE_CONSUMER.start(APP_NAME);

        LOG.trace("Initialised a consumer for showcase quote generator: ID: {}, Name: {}",
            MESSAGE_CONSUMER.getId(), MESSAGE_CONSUMER.getName());
    }

    private static Map<String, String> getDefaults() {
        ConsulManager.setAppName(APP_NAME);
        Map<String, String> defaultValues = new HashMap<>();
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.APP_INBOX_TOPIC), "telflow.notification");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_PROTOCOL), "http");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_HOST), "localhost");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_PORT), "9797");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_FABRIC_USER), "");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_FABRIC_PASSWORD), "");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_KAFKA_ENDPOINT), "kafka:9092");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.APP_TRANSITION_ACTION), "generateQuote");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.APP_NOTIFY_TEMPLATE), "PDF Attach Artefact Template");
        return defaultValues;
    }

}