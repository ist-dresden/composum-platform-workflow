package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.commons.util.ScheduledExecutorServiceFromExecutorService;
import com.composum.platform.commons.util.SlingThreadPoolExecutorService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.platform.workflow.mail.EmailServerConfigModel;
import com.composum.platform.workflow.mail.EmailService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.Resource;
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
import java.util.concurrent.*;

import static com.composum.platform.workflow.mail.EmailServerConfigModel.*;

/**
 * Implementation of {@link EmailService} using Simple Java Mail.
 */
@Component(property = {
        Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Service"
})
@Designate(ocd = EmailServiceImpl.Config.class)
public class EmailServiceImpl implements EmailService {

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
    protected CredentialService credentialService;

    @Reference
    protected ThreadPoolManager threadPoolManager;

    @Reference
    protected PlaceholderService placeholderService;

    protected volatile ScheduledExecutorService scheduledExecutorService;

    protected volatile Config config;

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

    protected Email prepareEmail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfigResource, String loggingId) throws EmailSendingException {
        EmailServerConfigModel serverConfig = new EmailServerConfigModel();
        serverConfig.initialize(new BeanContext.Service(serverConfigResource.getResourceResolver()), serverConfigResource);
        Email email = emailBuilder.buildEmail(placeholderService);
        try {
            Authenticator authenticator =
                    StringUtils.isNotBlank(serverConfig.getCredentialId()) ?
                            credentialService.getMailAuthenticator(serverConfig.getCredentialId(), serverConfigResource.getResourceResolver()) : null;
            initFromServerConfig(email, serverConfig, authenticator, loggingId);
            email.buildMimeMessage();
        } catch (RepositoryException | EmailException e) { // acl failure or failure building the message
            throw new EmailSendingException(e);
        }
        return email;
    }

    @Nonnull
    @Override
    public Future<String> sendMail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyEnabled();
        String loggingId = makeLoggingId(emailBuilder, serverConfig);
        Email email = prepareEmail(emailBuilder, serverConfig, loggingId);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queueing {} : {}", loggingId, emailBuilder.toString());
        }
        EmailTask task = new EmailTask(email, loggingId);
        task.currentTry = scheduledExecutorService.submit(task::trySending);
        Future<String> future = task.resultFuture;
        try { // That might throw up on immediate errors with the email in some cases, but doesn't hold us up much.
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
    protected String makeLoggingId(EmailBuilder emailBuilder, Resource serverConfig) {
        String id = RandomStringUtils.randomAlphanumeric(10);
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

    @Nonnull
    @Override
    public String sendMailImmediately(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyEnabled();
        String loggingId = makeLoggingId(emailBuilder, serverConfig);
        Email email = prepareEmail(emailBuilder, serverConfig, loggingId);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending with {} : {}", loggingId, emailBuilder.toString());
        }
        return sendEmail(email, loggingId);
    }

    protected String sendEmail(Email email, String loggingId) throws EmailSendingException {
        try {
            String messageId = email.sendMimeMessage();
            LOG.info("Sent email {} : {}", loggingId, messageId);
            return messageId;
        } catch (EmailException | RuntimeException e) {
            throw new EmailSendingException(e);
        }
    }

    protected void initFromServerConfig(@Nonnull Email email, @Nonnull EmailServerConfigModel serverConfig, Authenticator authenticator, String loggingId) throws EmailSendingException {
        verifyEnabled();
        if (serverConfig == null) {
            throw new IllegalArgumentException("No email server configuration given");
        }
        if (config.connectionTimeout() != 0) {
            email.setSocketConnectionTimeout(config.connectionTimeout());
        }
        if (config.socketTimeout() != 0) {
            email.setSocketTimeout(config.socketTimeout());
        }
        if (!serverConfig.getEnabled()) {
            LOG.warn("Trying to send email with disabled server {}", serverConfig.getPath());
            throw new EmailSendingException("Email-server not enabled: " + serverConfig.getPath());
        }
        email.setHostName(serverConfig.getHost());
        String connectionType = serverConfig.getConnectionType();
        if (VALUE_SMTP.equals(connectionType)) {
            email.setSmtpPort(serverConfig.getPort());
        } else if (VALUE_STARTTLS.equals(connectionType)) {
            email.setSmtpPort(serverConfig.getPort());
            email.setStartTLSRequired(true);
        } else if (VALUE_SMTPS.equals(connectionType)) {
            email.setSslSmtpPort(String.valueOf(serverConfig.getPort()));
            email.setSSLOnConnect(true);
        }
        if (authenticator != null) {
            email.setAuthenticator(authenticator);
        }
        LOG.debug("Using serverCfg for {} : {}", loggingId, serverConfig.toString());
    }

    protected void verifyEnabled() throws EmailSendingException {
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
    protected void activate(Config theConfig) {
        this.config = theConfig;
        if (isEnabled()) {
            if (scheduledExecutorService == null) {
                this.scheduledExecutorService =
                        new ScheduledExecutorServiceFromExecutorService(
                                new SlingThreadPoolExecutorService(threadPoolManager, THREADPOOL_NAME));
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
        ScheduledExecutorService oldExecutorService = this.scheduledExecutorService;
        this.scheduledExecutorService = null;
        if (oldExecutorService != null) {
            oldExecutorService.shutdown();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (scheduledExecutorService != null) {
                LOG.error("Bug: ExecutorService is still not shutdown in finalize! " + scheduledExecutorService);
            }
            shutdownExecutorService();
        } finally {
            super.finalize();
        }
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

    }

    /**
     * Task for doing several sending retries.
     */
    protected class EmailTask {

        protected final Email email;
        protected final CompletableFuture<String> resultFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (currentTry != null) {
                    currentTry.cancel(mayInterruptIfRunning);
                }
                return super.cancel(mayInterruptIfRunning);
            }
        };
        protected volatile int retry = 0;
        protected volatile Future<?> currentTry;
        /**
         * Something that can be used to uniquely identify the mail for the logfile.
         */
        protected final String loggingId;

        public EmailTask(Email email, String loggingId) {
            this.email = email;
            this.loggingId = loggingId;
        }

        public void trySending() {
            retry = retry + 1;
            try {
                String messageId = sendEmail(email, loggingId);
                resultFuture.complete(messageId);
            } catch (EmailSendingException e) {
                Config conf = config;
                if (conf.enabled() && e.isRetryable()) {
                    long delay = retryTime(conf);
                    LOG.info("Try {} failed for {} because of {}, retry delay {}", retry, loggingId, e.toString(), delay);
                    if (conf.retries() > retry) {
                        try {
                            scheduledExecutorService.schedule(this::trySending, delay, TimeUnit.SECONDS);
                        } catch (RuntimeException | Error e2) {
                            resultFuture.completeExceptionally(e2);
                        }
                    } else {
                        LOG.info("Giving up after {} retries for {}", retry, loggingId);
                        resultFuture.completeExceptionally(
                                new EmailSendingException("Giving up after " + retry + " retries.", e));
                    }
                } else {
                    resultFuture.completeExceptionally(e);
                }
            } catch (RuntimeException | Error e) {
                resultFuture.completeExceptionally(e);
            }
        }

        protected long retryTime(Config conf) {
            return (long) Math.pow(2, retry - 1) * conf.retryTime();
        }

    }

}
