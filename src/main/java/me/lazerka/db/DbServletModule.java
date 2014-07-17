package me.lazerka.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Scopes;
import com.googlecode.objectify.ObjectifyFilter;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Dzmitry Lazerka
 */
public class DbServletModule extends JerseyServletModule {
    @Override
    protected void configureServlets() {
        // Objectify requires this while using Async+Caching
        // until https://code.google.com/p/googleappengine/issues/detail?id=4271 gets fixed.
        bind(ObjectifyFilter.class).asEagerSingleton();
        filter("/*").through(ObjectifyFilter.class);

        // Route all requests through GuiceContainer.
        serve("/*").with(GuiceContainer.class, getJerseyParams());

        // Handle "application/json" by Jackson.
        bind(JacksonJsonProvider.class).asEagerSingleton();

        ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
        bind(ObjectMapper.class).toInstance(objectMapper);

        bind(DBResource.class).in(Scopes.NO_SCOPE);
    }

    private Map<String, String> getJerseyParams() {
        Map<String, String> params = new LinkedHashMap<>();

        // Read somewhere that it's needed for GAE.
        params.put("com.sun.jersey.config.feature.DisableWADL", "true");

        // This makes use of custom Auth+filters using OAuth2.
        // Commented because using GAE default authentication.
        // params.put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, AuthFilterFactory.class.getName());

        //params.put("com.sun.jersey.spi.container.ContainerRequestFilters", "com.sun.jersey.api.container.filter.LoggingFilter");
        //params.put("com.sun.jersey.spi.container.ContainerResponseFilters", "com.sun.jersey.api.container.filter.LoggingFilter");
        //params.put("com.sun.jersey.config.feature.logging.DisableEntitylogging", "true");
        //params.put("com.sun.jersey.config.feature.Trace", "true");
        return params;
    }

}
