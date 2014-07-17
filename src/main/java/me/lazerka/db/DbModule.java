package me.lazerka.db;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class DbModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new DbServletModule());

        install(new OfyModule());

        bindGaeServices();
    }

    private void bindGaeServices() {
        bind(BlobstoreService.class).toInstance(BlobstoreServiceFactory.getBlobstoreService());
        bind(MemcacheService.class).toInstance(MemcacheServiceFactory.getMemcacheService());
        bind(UserService.class).toInstance(UserServiceFactory.getUserService());
    }

    @Provides
    @Named("now")
    private DateTime now() {
        return DateTime.now(DateTimeZone.UTC);
    }

}
