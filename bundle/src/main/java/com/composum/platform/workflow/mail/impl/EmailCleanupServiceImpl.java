package com.composum.platform.workflow.mail.impl;

import com.composum.sling.core.util.ResourceUtil;
import com.composum.sling.core.util.SlingResourceUtil;
import com.composum.sling.platform.staging.query.Query;
import com.composum.sling.platform.staging.query.QueryBuilder;
import org.apache.sling.api.resource.*;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Calendar;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.SECONDS;


@Component(
        service = {Runnable.class, EmailCleanupService.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Cleanup Service",
                "scheduler.expression=* 0 * * * ?",
                "scheduler.runOn=SINGLE"
        }
)
@Designate(ocd = EmailCleanupServiceImpl.Config.class)
public class EmailCleanupServiceImpl implements Runnable, EmailCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailCleanupServiceImpl.class);

    protected volatile Config config;

    @Reference
    protected ResourceResolverFactory resolverFactory;

    /**
     * Used to simulate scheduler.concurrent=false .
     */
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void run() {
        LOG.debug("running cleanup");
        boolean locked = false;
        try {
            locked = lock.writeLock().tryLock(1, SECONDS);
            if (locked) {
                cleanupFolder(QueuedEmail.PATH_MAILQUEUE_FAILED, config.keepFailedSeconds());
                cleanupFolder(QueuedEmail.PATH_MAILQUEUE_SENT, config.keepSuccessfulSeconds());
            } else {
                LOG.info("Skipping cleanup - old cleanup is still running???");
            }
        } catch (InterruptedException e) {
            LOG.error("" + e, e);
            // can't rethrow; since interruption flag is cleared:
            Thread.currentThread().interrupt();
        } finally {
            if (locked) {
                lock.writeLock().unlock();
            }
            LOG.debug("cleanup done");
        }
    }

    protected void cleanupFolder(String cleanupPath, int keepSeconds) {
        Calendar cuttime = Calendar.getInstance();
        long cutTimeMillis = cuttime.getTimeInMillis() - keepSeconds;
        if (cutTimeMillis < 0) { // use a large keep time to switch off cleaning
            return;
        }
        cuttime.setTimeInMillis(cutTimeMillis);
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(null)) {
            Query query = resolver.adaptTo(QueryBuilder.class).createQuery();
            query.path(cleanupPath).condition(
                    query.conditionBuilder().property(ResourceUtil.PROP_LAST_MODIFIED).lt().val(Calendar.getInstance())
            );
            StringBuilder buf = new StringBuilder(cleanupPath).append(":\n");
            int count = 0;
            for (Resource resource : query.execute()) {
                resolver.delete(resource);
                buf.append(SlingResourceUtil.relativePath(cleanupPath, resource.getPath())).append(" ");
                count++;
            }
            if (count > 0) {
                LOG.info("Cleaned up {} in {}", count, buf);
            } else {
                LOG.debug("Nothing to clean up in {}", cleanupPath);
            }
            resolver.commit();
        } catch (LoginException | RuntimeException | PersistenceException e) {
            LOG.error("Problem cleaning up {} keeptime {}", cleanupPath, keepSeconds, e);
        }
    }

    @Activate
    @Modified
    protected void activate(@Nonnull Config theConfig) {
        this.config = theConfig;
        LOG.info("activated");
    }

    @Deactivate
    protected void deactivate() {
        LOG.info("deactivated");
    }

    @Override
    public boolean keepFailedEmails() {
        return config.keepFailedSeconds() > 0;
    }

    @Override
    public boolean keepDeliveredEmails() {
        return config.keepSuccessfulSeconds() > 0;
    }

    @ObjectClassDefinition(
            name = "Composum Workflow Email Cleanup Service"
    )
    @interface Config {

        @AttributeDefinition(name = "Keep failed time",
                description = "Number of seconds to keep emails that failed emails around. " +
                        "If 0 we delete them immediately. If cleanup is not wanted, use a large number like 253370764800.")
        int keepFailedSeconds() default 86400 * 7; // 1 week

        @AttributeDefinition(name = "Keep successful time",
                description = "Number of seconds to keep emails that have been successfully around. " +
                        "If 0 we delete them immediately. If cleanup is not wanted, use a large number like 253370764800.")
        int keepSuccessfulSeconds() default 86400 * 7; // 1 week

    }

}
