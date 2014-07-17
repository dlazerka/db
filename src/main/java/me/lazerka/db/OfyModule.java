package me.lazerka.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.impl.translate.opt.joda.JodaTimeTranslators;

/**
 * @author Dzmitry Lazerka
 */
public class OfyModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(OfyModule.class);

    @Override
    protected void configure() {
        logger.trace("setUpObjectify");
        ObjectifyFactory factory = ObjectifyService.factory();
        JodaTimeTranslators.add(factory);

        // Here go entities
        //factory.register(Someclass.class);
    }

    @Provides
    private Objectify provideOfy() {
        return ObjectifyService.ofy();
    }

}
