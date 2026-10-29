package com.campusverse.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;

@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());
    private static volatile boolean initialized = false;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("CampusVerse Lite starting up. Initializing database schema...");
        try {
            DatabaseManager.initSchema();
            initialized = true;
            LOGGER.info("Database initialized successfully.");
        } catch (Exception e) {
            LOGGER.severe("CampusVerse Lite failed to initialize database schema: " + e.getMessage());
            throw new RuntimeException("Database initialization error", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("CampusVerse Lite shutting down. Closing database connections...");
        if (initialized) {
            try (java.sql.Connection conn = DatabaseManager.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("SHUTDOWN");
            } catch (Exception e) {
                LOGGER.warning("Database shutdown notice: " + e.getMessage());
            }
        }
        LOGGER.info("Database closed successfully.");
    }
}

