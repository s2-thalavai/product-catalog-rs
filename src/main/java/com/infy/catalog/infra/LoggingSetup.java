package com.infy.catalog.infra;

import org.slf4j.bridge.SLF4JBridgeHandler;

/** Jersey, HK2 and Grizzly log through java.util.logging; route that into SLF4J/Logback. */
public final class LoggingSetup {

    private LoggingSetup() {
    }

    public static void configure() {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }
}
