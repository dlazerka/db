package me.lazerka.db;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bypasses _ah/* requests except some.
 *
 * @author Dzmitry Lazerka
 */
public class BypassGuiceFilter extends com.google.inject.servlet.GuiceFilter {
    private final static Logger logger = LoggerFactory.getLogger(BypassGuiceFilter.class);

    private final Pattern PATTERN = Pattern.compile("/_ah/.*");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            logger.trace("Got request: {} {}", req.getMethod(), req.getRequestURI());
        } else {
            logger.warn("Got non-HTTP request (length={})", request.getContentLength());
        }
        HttpServletRequest req = (HttpServletRequest) request;

        // Break the chain for dev server except warmup (must be handled by app).
        String requestURI = req.getRequestURI();
        boolean isAhRequest = PATTERN.matcher(requestURI).matches();

        // Here's the main thing.
        boolean shouldGuiceHandle = requestURI.equals("/_ah/warmup") || requestURI.startsWith("/_ah/upload/");

        if (isAhRequest && !shouldGuiceHandle) {
            logger.trace("Bypassing Guice filter: {}", requestURI);
            chain.doFilter(request, response);
            return ;
        } else if (isAhRequest) {
            logger.info("Not bypassing Guice filter: {}", requestURI);
        }

        super.doFilter(request, response, chain);
    }
}
