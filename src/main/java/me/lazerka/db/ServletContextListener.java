package me.lazerka.db;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author Dzmitry Lazerka
 */
public class ServletContextListener implements javax.servlet.ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(ServletContextListener.class);

    private static final String INJECTOR_NAME = Injector.class.getName();

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        logger.trace("contextInitialized");

        // Redirect java.util.logging through SLF4J.
        // Doesn't work in GAE, cause java.util.logging.LogManager is restricted.
        //SLF4JBridgeHandler.removeHandlersForRootLogger();
        //SLF4JBridgeHandler.install();

        ServletContext servletContext = servletContextEvent.getServletContext();

        Injector injector = Guice.createInjector(new DbModule());
        servletContext.setAttribute(INJECTOR_NAME, injector);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        logger.trace("contextDestroyed");
        // App Engine does not currently invoke this method.
        ServletContext servletContext = servletContextEvent.getServletContext();
        servletContext.removeAttribute(INJECTOR_NAME);
    }
}
