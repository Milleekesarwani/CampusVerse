package com.campusverse.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;

@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("CampusVerse Lite starting up. Initializing database schema...");
        DatabaseManager.initSchema();
        LOGGER.info("Database initialized successfully.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("CampusVerse Lite shutting down.");
    }
}

