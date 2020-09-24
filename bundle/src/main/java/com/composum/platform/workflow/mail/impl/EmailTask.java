package com.composum.platform.workflow.mail.impl;

import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.platform.workflow.mail.EmailServerConfigModel;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.mail.Session;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.composum.platform.workflow.mail.impl.QueuedEmail.*;
import static com.composum.sling.core.util.SlingResourceUtil.appendPaths;
import static com.composum.sling.core.util.SlingResourceUtil.relativePath;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Task for doing several sending retries.
 */
public class EmailTask {

    private static final Logger LOG = LoggerFactory.getLogger(EmailTask.class);

    @Nonnull
    private final EmailServiceImpl emailService;

    protected final CompletableFuture<String> resultFuture = new CompletableFuture<>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            LOG.info("Cancelling from outside: {}", loggingId);
            if (currentTry != null) {
                currentTry.cancel(mayInterruptIfRunning);
            }
            try {
                return super.cancel(mayInterruptIfRunning);
            } finally { // move to failed.
                // FIXME(hps,22.09.20) might be dangerous if the mail is currently in work :-(
                archiveQueuedEmail(null, false, STATE_CANCELLED);
            }
        }
    };

    protected volatile long lastRun;

    protected volatile Future<?> currentTry;

    /**
     * Something that can be used to uniquely identify the mail for the logfile.
     */
    protected final String loggingId;

    protected final String queuedEmailPath;

    public EmailTask(@Nonnull EmailServiceImpl emailService, @Nonnull Email email, @Nonnull String serverConfigPath, @Nonnull String loggingId, @Nullable String credentialToken, @Nonnull String folder) throws EmailSendingException {
        this.emailService = emailService;
        this.loggingId = loggingId;
        QueuedEmail queuedEmail = new QueuedEmail(loggingId, email, serverConfigPath, credentialToken, folder);
        queuedEmailPath = queuedEmail.getPath();
        queuedEmail.setNextTry(System.currentTimeMillis() + MILLISECONDS.convert(emailService.config.retryTime(), SECONDS));
        queuedEmail.setQueuedAt(emailService.serviceId);
        lastRun = System.currentTimeMillis();
        try (ResourceResolver serviceResolver = emailService.getServiceResolver()) {
            queuedEmail.create(serviceResolver);
            serviceResolver.commit();
        } catch (PersistenceException e) {
            LOG.error("Could not persist queued email at {}", queuedEmailPath, e);
            throw new EmailSendingException("Could not persist queued email", e);
        }
    }

    public EmailTask(EmailServiceImpl emailService, @Nonnull QueuedEmail queuedEmail) {
        this.emailService = emailService;
        queuedEmailPath = queuedEmail.path;
        loggingId = queuedEmail.getLoggingId();
        lastRun = System.currentTimeMillis();
    }

    public void trySending() {
        LOG.debug(">>>trySending {}", loggingId);
        QueuedEmail queuedEmail = null;
        try {
            emailService.verifyServiceIsEnabled();
            try (ResourceResolver serviceResolver = emailService.getServiceResolver()) {
                Resource queuedEmailResource = serviceResolver.getResource(queuedEmailPath);
                if (queuedEmailResource != null) {
                    queuedEmail = new QueuedEmail(queuedEmailResource);
                    if (emailService.serviceId.equals(queuedEmail.getQueuedAt())) {
                        queuedEmail.setState(STATE_SENDING);
                        queuedEmail.setNextTry(NEXTTRY_NEVER); // not automatically selected anymore
                        queuedEmail.update(serviceResolver); // make sure we can write to it by setting last modified time
                        serviceResolver.commit();
                        serviceResolver.refresh();

                        Email emailForSession = new SimpleEmail();
                        Resource serverConfigResource = serviceResolver.getResource(queuedEmail.getServerConfigPath());
                        if (serverConfigResource == null) {
                            LOG.error("Service resolver could not read server config {} for {}", queuedEmail.getServerConfigPath(), queuedEmail.getLoggingId());
                            throw new EmailSendingException("Service resolver could not read server configuration");
                        }
                        EmailServerConfigModel serverConfig = new EmailServerConfigModel(serverConfigResource);
                        emailService.initializeEmailWithServerConfig(emailForSession, serverConfig, queuedEmail.getLoggingId());
                        Session session = emailForSession.getMailSession();

                        LOG.info("Processing {} retry {}", queuedEmail.getRetry(), loggingId);
                        emailService.verifyServiceIsEnabled(); // don't send during #deactivate
                        if (!resultFuture.isDone()) { // checks for outside events that abort us
                            String messageId = emailService.sendEmail(queuedEmail.getMimeMessage(session), loggingId, queuedEmail.getCredentialToken());
                            resultFuture.complete(messageId);
                            // preventively clear thread interruption status since it's crucial that the rest is done and we are almost done, anyway.
                            Thread.interrupted();
                            archiveQueuedEmail(serviceResolver, true, STATE_SENT);
                        } else if (resultFuture.isCancelled()) { // special case of done. Move to failed.
                            LOG.info("Was cancelled from outside - archiving to failed.");
                            archiveQueuedEmail(serviceResolver, false, STATE_CANCELLED);
                        } else { // safety check, but that must not happen.
                            LOG.error("Bug: Future already completed? Not sending {}", loggingId);
                            archiveQueuedEmail(serviceResolver, false, STATE_INTERNAL_ERROR); // nothing sensible to do.
                        }

                    } else {
                        LOG.info("Ignoring since queued for different server - race condition {}", loggingId);
                    }
                } else {
                    LOG.error("Queued email is gone for unknown reason (possibly processed by other cluster server) for {}", loggingId);
                    resultFuture.completeExceptionally(new EmailSendingException("Queued email is gone for some reason."));
                }
            } catch (PersistenceException e) {
                LOG.error("Bug: trouble changing queued email {}", loggingId, e);
                throw new EmailSendingException("Bug: trouble changing queued email.");
            } catch (EmailException | RuntimeException e) {
                String logid = queuedEmail != null ? queuedEmail.getLoggingId() : queuedEmailPath;
                throw new EmailSendingException("Exception sending " + logid, e);
            }
        } catch (EmailSendingException e) {
            EmailServiceImpl.Config conf = emailService.config;
            boolean retry;
            if (conf == null || !conf.enabled()) {
                LOG.info("Emailservice disabled in the meantime -> aborting {} , might be retried later, though.", loggingId);
                resultFuture.completeExceptionally(new EmailSendingException("Emailservice disabled -> abort. Might be retried later, though."));
                retry = true;
            } else if (resultFuture.isCancelled()) {
                LOG.info("Was cancelled from outside - archiving to failed.");
                retry = false;
            } else if (e.isRetryable() && !resultFuture.isDone()) {
                LOG.info("Try {} failed for {} because of {}", queuedEmail.getRetry(), loggingId, e.toString());
                if (queuedEmail.getRetry() >= conf.retries()) {
                    resultFuture.completeExceptionally(
                            new EmailSendingException("Giving up after " + queuedEmail.getRetry() + " retries for " + loggingId, e));
                    LOG.info("Giving up after {} retries for {}", queuedEmail.getRetry(), loggingId, e);
                    retry = false;
                } else {
                    retry = true;
                }
            } else {
                resultFuture.completeExceptionally(e);
                LOG.warn("Non-retryable error, giving up: try {} failed for {}", (queuedEmail != null ? queuedEmail.getRetry() : -1), loggingId, e);
                retry = false;
            }
            if (retry) {
                prepareForRetry();
            } else {
                archiveQueuedEmail(null, false, STATE_FAILED);
            }
        } catch (RuntimeException e) { // Bug.
            LOG.warn("Unexpected exception - aborting {}", loggingId, e);
            resultFuture.completeExceptionally(e);
            archiveQueuedEmail(null, false, STATE_INTERNAL_ERROR);
            throw e;
        } catch (Error e) {
            LOG.warn("Unexpected error - aborting {}", loggingId, e);
            resultFuture.completeExceptionally(e);
            // archiveQueuedEmail(null, false); we omit that deliberately because Errors are serious and should be investigated
            throw e;
        } finally {
            if (resultFuture.isDone()) {
                emailService.inProcess.remove(queuedEmailPath);
            }
            lastRun = System.currentTimeMillis();
            LOG.debug("<<<trySending {}", loggingId);
        }
    }

    protected void prepareForRetry() {
        try (ResourceResolver resolver = emailService.getServiceResolver()) {
            Resource queuedResource = resolver.getResource(queuedEmailPath);
            if (queuedResource != null) {
                QueuedEmail queuedEmail = new QueuedEmail(queuedResource);
                queuedEmail.setState(null);
                queuedEmail.setRetry(queuedEmail.getRetry() + 1);
                queuedEmail.setNextTry(System.currentTimeMillis() + emailService.retryTime(queuedEmail.getRetry()));
                queuedEmail.update(resolver);
                resolver.commit();
            } else {
                LOG.error("Bug? Pending email disappeared. {}", queuedEmailPath);
            }
        } catch (EmailSendingException | RuntimeException | PersistenceException e) {
            LOG.error("Error preparing for retry {}", queuedEmailPath, e);
        }
    }

    /**
     * Moves the queued email to the archival places or deletes it, depending on configuration.
     */
    protected void archiveQueuedEmail(@Nullable ResourceResolver serviceResolver, boolean success, @Nonnull String state) {
        ResourceResolver resolver = null;
        try {
            resolver = serviceResolver != null ? serviceResolver : emailService.getServiceResolver();
            Resource res = resolver.getResource(queuedEmailPath);
            if (res != null) {
                boolean keepMail = success ? emailService.cleanupService.keepDeliveredEmails() : emailService.cleanupService.keepFailedEmails();
                String basepath = success ? PATH_MAILQUEUE_SENT : PATH_MAILQUEUE_FAILED;
                String newLocation = appendPaths(basepath, relativePath(QueuedEmail.PATH_MAILQUEUE, queuedEmailPath));

                if (keepMail) {
                    QueuedEmail queuedEmail = new QueuedEmail(res);
                    queuedEmail.setState(state);
                    queuedEmail.setNextTry(NEXTTRY_NEVER);
                    if (success) {
                        queuedEmail.setCredentialToken(null); // this is sensitive data and won't be needed again.
                    }
                    queuedEmail.update(resolver);
                    resolver.commit();
                    resolver.refresh();

                    String dir = ResourceUtil.getParent(newLocation);
                    if (!StringUtils.startsWith(dir, basepath)) {
                        throw new IllegalArgumentException("Something's wrong with archive location " + newLocation + " for " + loggingId);
                    }
                    ResourceUtil.getOrCreateResource(resolver, dir);
                    LOG.info("Archiving {} to {}", loggingId, newLocation);
                    resolver.move(queuedEmailPath, dir);
                } else {
                    LOG.info("Removing from email queue: {} for {}", queuedEmailPath, loggingId);
                    resolver.delete(res);
                }
                resolver.commit();
                resolver.refresh();
            } else {
                LOG.warn("Bug: queued email not present anymore for {}", loggingId);
            }
        } catch (EmailSendingException | PersistenceException | RepositoryException e) {
            LOG.error("Exception removing queued email for {} at {}", loggingId, queuedEmailPath, e);
            resultFuture.completeExceptionally(e);
        } finally {
            if (serviceResolver == null && resolver != null) { // serviceResolver should not be closed here.
                resolver.close();
            }
        }
    }

}
