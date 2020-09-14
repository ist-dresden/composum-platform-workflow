package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.commons.util.SlingThreadPoolExecutorService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.platform.workflow.mail.EmailServerConfigModel;
import com.composum.platform.workflow.mail.EmailService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.*;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;

import static com.composum.platform.workflow.mail.EmailServerConfigModel.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Implementation of {@link EmailService} using Simple Java Mail.
 */
@Component(
        service = {EmailService.class, Runnable.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Service",
                "scheduler.period=5",
                "scheduler.concurrent=false",
                "scheduler.threadPool=" + EmailServiceImpl.THREADPOOL_NAME
        })
// FIXME(hps,09.09.20) Reduce call frequency!
@Designate(ocd = EmailServiceImpl.Config.class)
public class EmailServiceImpl implements EmailService, Runnable {

    /**
     * Path for server configurations, mail templates, etc. This is not enforced, just a suggestion.
     */
    @SuppressWarnings("unused")
    public static final String PATH_CONFIG = "/var/composum/platform/mail";

    /**
     * Name of the threadpool, and thus prefix for the thread names.
     */
    public static final String THREADPOOL_NAME = "EmailSrv";

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Reference
    protected ResourceResolverFactory resolverFactory;

    @Reference
    protected CredentialService credentialService;

    @Reference
    protected ThreadPoolManager threadPoolManager;

    @Reference
    protected PlaceholderService placeholderService;

    protected volatile ExecutorService executorService;

    protected volatile Config config;

    protected Random random = new SecureRandom();

    protected final String serviceId = RandomStringUtils.random(8, 0, 0, true, true, null, random);

    @Override
    public boolean isValid(@Nullable String emailAdress) {
        if (StringUtils.isBlank(emailAdress)) return false;
        try {
            new SimpleEmail().setFrom(emailAdress);
            return true;
        } catch (EmailException e) {
            return false;
        }
    }

    @Nonnull
    @Override
    public String sendMailImmediately(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyServiceIsEnabled();
        String loggingId = makeLoggingId(emailBuilder, serverConfig);
        Email email = emailBuilder.buildEmail(placeholderService);
        initializeEmailWithServerConfig(email, serverConfig, loggingId, null);
        try {
            email.buildMimeMessage();
        } catch (EmailException e) {
            throw new EmailSendingException("Could not build mime message for " + loggingId, e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending with {} : {}", loggingId, emailBuilder.toString());
        }
        return sendEmail(email.getMimeMessage(), loggingId);
    }


    @Nonnull
    @Override
    public Future<String> sendMail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyServiceIsEnabled();
        String loggingId = makeLoggingId(emailBuilder, serverConfig);
        String credentialToken = getCredentialToken(serverConfig, loggingId);
        Email email = emailBuilder.buildEmail(placeholderService);
        initializeEmailWithServerConfig(email, serverConfig, loggingId, credentialToken);
        try {
            email.buildMimeMessage();
        } catch (EmailException e) {
            throw new EmailSendingException("Could not build mime message for " + loggingId, e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queueing {} : {}", loggingId, emailBuilder.toString());
        }
        EmailTask task = new EmailTask(email, serverConfig.getPath(), loggingId, credentialToken);
        task.currentTry = executorService.submit(task::trySending);
        Future<String> future = task.resultFuture;
        try {
            // Try to signal immediate errors with the email in some cases. This but shouldn't hold us up much.
            future.get(50, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw wrapAndThrowException(e.getCause());
        } catch (TimeoutException | InterruptedException e) { //
            LOG.debug("Ignored for {} : {} ", loggingId, e.toString());
        }
        return future;
    }

    /**
     * Creates an id that is put into all logging messages to ensure we can identify all messages belonging to an email, which would be impossible otherwise since it's run in several threads.
     */
    @Nonnull
    protected String makeLoggingId(EmailBuilder emailBuilder, Resource serverConfig) {
        String id = RandomStringUtils.random(10, 0, 0, true, true, null, random);
        LOG.info("Assigning logId {} to {} with servercfg {}", id, emailBuilder.describeForLogging(), serverConfig.getPath());
        return id;
    }

    protected EmailSendingException wrapAndThrowException(Throwable exception) throws EmailSendingException {
        if (exception instanceof EmailSendingException) {
            return (EmailSendingException) exception;
        } else if (exception instanceof Error) {
            throw (Error) exception;
        } else {
            throw new EmailSendingException((Exception) exception);
        }
    }

    protected String sendEmail(MimeMessage message, String loggingId) throws EmailSendingException {
        try {
            Transport.send(message);
            String messageId = message.getMessageID();
            LOG.info("Sent email {} : {}", loggingId, messageId);
            return messageId;
        } catch (RuntimeException | MessagingException e) {
            LOG.warn("Sent email  failed {}", loggingId, e);
            throw new EmailSendingException(e);
        }
    }

    /**
     * Creates the credential token to access credentials in retries. Caution: works only with the serverConfig from the users resolver, not the service resolver, since it is used for authorization check.
     */
    protected String getCredentialToken(@Nonnull Resource serverConfigResource, @Nonnull String loggingId) throws EmailSendingException {
        EmailServerConfigModel serverConfig = new EmailServerConfigModel(serverConfigResource);
        ResourceResolver userResolver = serverConfigResource.getResourceResolver();
        String token = null;
        if (isNotBlank(serverConfig.getCredentialId())) {
            try {
                token = credentialService.getAccessToken(serverConfig.getCredentialId(), userResolver, CredentialService.TYPE_EMAIL);
            } catch (RepositoryException e) {
                throw new EmailSendingException("Could not get access token for email relay for " + loggingId, e);
            }
        }
        return token;
    }

    /**
     * Initializes the email session within the email with server configuration.
     */
    protected void initializeEmailWithServerConfig(@Nonnull Email email, @Nonnull Resource serverConfigResource, @Nonnull String loggingId, @Nullable String credentialToken) throws EmailSendingException {
        verifyServiceIsEnabled();
        EmailServerConfigModel serverConfig = new EmailServerConfigModel(serverConfigResource);
        Authenticator authenticator;
        try {
            String idOrToken = isNotBlank(credentialToken) ? credentialToken : serverConfig.getCredentialId();
            authenticator = isNotBlank(idOrToken) ?
                    credentialService.getMailAuthenticator(idOrToken, serverConfigResource.getResourceResolver()) : null;
        } catch (RepositoryException e) { // acl failure
            throw new EmailSendingException("Trouble creating credential token for email service for " + loggingId, e);
        }
        if (serverConfig == null) {
            throw new IllegalArgumentException("No email server configuration given for " + loggingId);
        }
        email.setDebug(config.debugInteractions());
        if (config.connectionTimeout() != 0) {
            email.setSocketConnectionTimeout(config.connectionTimeout());
        }
        if (config.socketTimeout() != 0) {
            email.setSocketTimeout(config.socketTimeout());
        }
        if (!serverConfig.getEnabled()) {
            LOG.warn("Trying to send email with disabled server {} for {}", loggingId, serverConfig.getPath());
            throw new EmailSendingException("Email-server not enabled: " + serverConfig.getPath());
        }
        email.setHostName(serverConfig.getHost());
        String connectionType = serverConfig.getConnectionType();
        if (VALUE_SMTP.equalsIgnoreCase(connectionType)) {
            email.setSmtpPort(serverConfig.getPort());
        } else if (VALUE_STARTTLS.equalsIgnoreCase(connectionType)) {
            email.setSmtpPort(serverConfig.getPort());
            email.setStartTLSRequired(true);
        } else if (VALUE_SMTPS.equalsIgnoreCase(connectionType)) {
            email.setSslSmtpPort(String.valueOf(serverConfig.getPort()));
            email.setSSLOnConnect(true);
        } else {
            LOG.warn("Unknown connection type {} at {}", connectionType, serverConfig.getPath());
        }
        if (authenticator != null) {
            email.setAuthenticator(authenticator);
        }
        LOG.debug("Using serverCfg for {} : {}", loggingId, serverConfig.toString());
    }

    protected void verifyServiceIsEnabled() throws EmailSendingException {
        if (!isEnabled()) {
            throw new EmailSendingException("Email service is not enabled.");
        }
    }

    @Override
    public boolean isEnabled() {
        Config mycfg = config;
        return mycfg != null && mycfg.enabled();
    }

    @Activate
    @Modified
    protected void activate(@Nonnull Config theConfig) {
        this.config = Objects.requireNonNull(theConfig); // FIXME(hps,11.09.20) wrong
        if (isEnabled()) {
            if (executorService == null) {
                this.executorService = new SlingThreadPoolExecutorService(threadPoolManager, THREADPOOL_NAME);
            }
        } else {
            shutdownExecutorService();
        }
        LOG.info("activated - enabled: {}", isEnabled());
    }

    @Deactivate
    protected void deactivate() {
        LOG.info("deactivated");
        this.config = null;
        shutdownExecutorService();
    }

    protected void shutdownExecutorService() {
        ExecutorService oldExecutorService = this.executorService;
        this.executorService = null;
        if (oldExecutorService != null) {
            oldExecutorService.shutdown();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (executorService != null) {
                LOG.error("Bug: ExecutorService is still not shutdown in finalize! " + executorService);
            }
            shutdownExecutorService();
        } finally {
            super.finalize();
        }
    }

    protected ResourceResolver getServiceResolver() throws EmailSendingException {
        try {
            return resolverFactory.getServiceResourceResolver(null);
        } catch (LoginException e) {
            LOG.error("Configuration error: cannot get service resolver", e);
            throw new EmailSendingException(e);
        }
    }

    /**
     * Checks whether there are more emails to run.
     */
    @Override
    public void run() {
        // FIXME(hps,10.09.20) implement
        LOG.debug("Cron called.");
    }

    @ObjectClassDefinition(
            name = "Composum Workflow Email Service"
    )
    @interface Config {

        @AttributeDefinition(name = "enabled", required = true, description =
                "The on/off switch for the mail service. By default off, since it needs to be configured.")
        boolean enabled() default false;

        @AttributeDefinition( // commons-email default 60 seconds
                name = "Connection Timeout in milliseconds"
        )
        int connectionTimeout() default 10000;

        @AttributeDefinition( // commons-email default 60 seconds
                name = "Socket I/O timeout in milliseconds"
        )
        int socketTimeout() default 10000;

        @AttributeDefinition(name = "Retries", required = true, description =
                "The number of retries when we have connection failure to the mail relay. It starts with 5 minutes, " +
                        "doubling the interval each time. That is, 10 at 300 seconds 1st retry is about 2 days.")
        int retries() default 10;

        @AttributeDefinition(name = "1st retry", required = true, description =
                "Time in seconds after which the first retry for retryable failures is started.")
        int retryTime() default 300;

        @AttributeDefinition(name = "Debug", required = true, description =
                "If set to true, interactions with the email relay are printed to stdout.")
        boolean debugInteractions() default false;

    }

    /**
     * Task for doing several sending retries.
     */
    protected class EmailTask {

        protected final CompletableFuture<String> resultFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (currentTry != null) {
                    currentTry.cancel(mayInterruptIfRunning);
                }
                return super.cancel(mayInterruptIfRunning);
            }
        };
        protected volatile Future<?> currentTry;

        /**
         * Something that can be used to uniquely identify the mail for the logfile.
         */
        protected final String loggingId;

        protected final String queuedEmailPath;

        public EmailTask(@Nonnull Email email, @Nonnull String serverConfigPath, @Nonnull String loggingId, @Nullable String credentialToken) throws EmailSendingException {
            this.loggingId = loggingId;
            queuedEmailPath = QueuedEmail.PATH_MAILQUEUE + "/" + loggingId; // FIXME(hps,10.09.20) tenant?
            QueuedEmail queuedEmail = new QueuedEmail(loggingId, email, serverConfigPath, credentialToken);
            queuedEmail.setNextTry(System.currentTimeMillis() + config.retryTime());
            queuedEmail.setQueuedAt(serviceId);
            try (ResourceResolver serviceResolver = getServiceResolver()) {
                queuedEmail.save(serviceResolver, queuedEmailPath);
                serviceResolver.commit();
            } catch (PersistenceException e) {
                LOG.error("Could not persist queued email at {}", queuedEmailPath, e);
                throw new EmailSendingException("Could not persist queued email", e);
            }
        }

        public void trySending() {
            QueuedEmail queuedEmail = null;
            try {
                // FIXME(hps,11.09.20) use precautions

                verifyServiceIsEnabled();
                try (ResourceResolver serviceResolver = getServiceResolver()) {
                    Resource queuedEmailResource = serviceResolver.getResource(queuedEmailPath);
                    if (queuedEmailResource != null) {
                        queuedEmail = new QueuedEmail(queuedEmailResource);
                        queuedEmail.setQueuedAt(serviceId);
                        queuedEmail.setRetry(queuedEmail.getRetry() + 1);
                        long delay = retryTime(config, queuedEmail.getRetry());
                        queuedEmail.setNextTry(System.currentTimeMillis() + delay);
                        queuedEmail.save(serviceResolver, queuedEmailPath);
                        serviceResolver.commit();

                        Email emailForSession = new SimpleEmail();
                        Resource serverConfigResource = serviceResolver.getResource(queuedEmail.getServerConfigPath());
                        if (serverConfigResource == null) {
                            LOG.error("Service resolver could not read server config {} for {}", queuedEmail.getServerConfigPath(), queuedEmail.getLoggingId());
                            throw new EmailSendingException("Service resolver could not read server configuration");
                        }
                        initializeEmailWithServerConfig(emailForSession, serverConfigResource, queuedEmail.getLoggingId(), queuedEmail.getCredentialToken());
                        Session session = emailForSession.getMailSession();

                        LOG.info("Processing {} retry {}, next retry delay {}", queuedEmail.getRetry() - 1, loggingId, delay);
                        String messageId = sendEmail(queuedEmail.getMimeMessage(session), loggingId);

                        removeQueuedEmail(serviceResolver);
                        resultFuture.complete(messageId);
                    } else {
                        LOG.error("Queued email is gone for unknown reason for {}", loggingId);
                        resultFuture.completeExceptionally(new EmailSendingException("Queued email is gone for some reason.") );
                    }
                } catch (PersistenceException e) {
                    LOG.error("Bug: trouble changing queued email {}", loggingId, e);
                    throw new EmailSendingException("Bug: trouble changing queued email.");
                } catch (EmailException e) {
                    String logid = queuedEmail != null ? queuedEmail.getLoggingId() : queuedEmailPath;
                    throw new EmailSendingException("Exception sending " + logid, e);
                }
            } catch (EmailSendingException e) {
                Config conf = config;
                if (conf.enabled() && e.isRetryable()) {
                    // FIXME(hps,11.09.20) IMPLEMENT RETRY
                    removeQueuedEmail(null);
                    resultFuture.completeExceptionally(e);
                    LOG.info("Try {} failed for {}", queuedEmail.getRetry(), loggingId, e);
//                    if (conf.retries() > retry) {
//                        try {
//                            scheduledExecutorService.schedule(this::trySending, delay, TimeUnit.SECONDS);
//                        } catch (RuntimeException | Error e2) {
//                            resultFuture.completeExceptionally(e2);
//                        }
//                    } else {
//                        LOG.info("Giving up after {} retries for {}", retry, loggingId);
//                        resultFuture.completeExceptionally(
//                                new EmailSendingException("Giving up after " + retry + " retries for " + loggingId, e));
//                    }
                } else {
                    removeQueuedEmail(null);
                    resultFuture.completeExceptionally(e);
                }
            } catch (RuntimeException | Error e) {
                LOG.warn("Unexpected exception", e);
                resultFuture.completeExceptionally(e);
            }
        }

        protected void removeQueuedEmail(ResourceResolver serviceResolver) {
            ResourceResolver resolver = null;
            try {
                resolver = serviceResolver != null ? serviceResolver : getServiceResolver();
                Resource res = resolver.getResource(queuedEmailPath);
                if (res != null) {
                    // FIXME(hps,11.09.20) remove queued email . Set resultFuture on exceptions.
                    QueuedEmail queuedEmail = new QueuedEmail(res);
                    queuedEmail.setNextTry(Long.MAX_VALUE);
                    queuedEmail.save(resolver, queuedEmailPath);
                    resolver.commit();
                } else {
                    LOG.warn("Bug: queued email not present anymore for {}", loggingId);
                }
            } catch (EmailSendingException | PersistenceException e) {
                LOG.error("Exception removing queued email for {} at {}", loggingId, queuedEmailPath, e);
                resultFuture.completeExceptionally(e);
            } finally {
                if (serviceResolver == null && resolver != null) { // serviceResolver should not be closed here.
                    resolver.close();
                }
            }
        }

        protected long retryTime(Config conf, int retry) {
            return (long) Math.pow(2, retry - 1) * conf.retryTime();
        }

    }

}
