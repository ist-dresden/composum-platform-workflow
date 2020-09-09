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
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.*;

import static com.composum.platform.workflow.mail.EmailServerConfigModel.*;

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

    @Nonnull
    @Override
    public Future<String> sendMail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyEnabled();
        String loggingId = makeLoggingId(emailBuilder, serverConfig);
        Email email = emailBuilder.buildEmail(placeholderService);
        initializeEmailWithServerConfig(email, serverConfig, loggingId);
        try {
            email.buildMimeMessage();
        } catch (EmailException e) {
            throw new EmailSendingException(e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queueing {} : {}", loggingId, emailBuilder.toString());
        }
        EmailTask task = new EmailTask(email, serverConfig.getPath(), loggingId);
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
        Email email = emailBuilder.buildEmail(placeholderService);
        initializeEmailWithServerConfig(email, serverConfig, loggingId);
        try {
            email.buildMimeMessage();
        } catch (EmailException e) {
            throw new EmailSendingException(e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending with {} : {}", loggingId, emailBuilder.toString());
        }
        return sendEmail(email.getMimeMessage(), loggingId);
    }

    protected String sendEmail(MimeMessage message, String loggingId) throws EmailSendingException {
        try {
            Transport.send(message);
            String messageId = message.getMessageID();
            LOG.info("Sent email {} : {}", loggingId, messageId);
            return messageId;
        } catch (RuntimeException | MessagingException e) {
            throw new EmailSendingException(e);
        }
    }

    /**
     * Initializes the email session within the email with server configuration.
     */
    protected void initializeEmailWithServerConfig(@Nonnull Email email, @Nonnull Resource serverConfigResource, String loggingId) throws EmailSendingException {
        verifyEnabled();
        EmailServerConfigModel serverConfig = new EmailServerConfigModel();
        serverConfig.initialize(new BeanContext.Service(serverConfigResource.getResourceResolver()), serverConfigResource);
        Authenticator authenticator;
        try {
            authenticator = StringUtils.isNotBlank(serverConfig.getCredentialId()) ?
                    credentialService.getMailAuthenticator(serverConfig.getCredentialId(), serverConfigResource.getResourceResolver()) : null;
        } catch (RepositoryException e) { // acl failure
            throw new EmailSendingException(e);
        }
        if (serverConfig == null) {
            throw new IllegalArgumentException("No email server configuration given");
        }
        email.setDebug(config.debugInteractions());
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

    protected void verifyEnabled() throws EmailSendingException {
        if (!isEnabled()) {
            throw new EmailSendingException("Email service is not enabled.");
        }
    }

    protected InputStream serialize(Email email, String loggingId) throws EmailSendingException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            MimeMessage mimeMessage = email.getMimeMessage();
            if (mimeMessage == null) {
                email.buildMimeMessage();
                mimeMessage = email.getMimeMessage();
            }
            mimeMessage.writeTo(bos);
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (EmailException | IOException | MessagingException e) {
            LOG.error("Could not serialize email {}", loggingId, e);
            throw new EmailSendingException(e);
        }
    }

    protected MimeMessage deserialize(InputStream inputStream, Resource serverConfigResource, String loggingId)
            throws EmailSendingException, MessagingException, EmailException {
        SimpleEmail emailForSession = new SimpleEmail();
        initializeEmailWithServerConfig(emailForSession, serverConfigResource, loggingId);
        Session mailSession = emailForSession.getMailSession();
        MimeMessage mimeMessage = new MimeMessage(mailSession, inputStream);
        return mimeMessage;
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

    /**
     * Checks whether there are more emails to run.
     */
    @Override
    public void run() {
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

        protected final MimeMessage mimeMessage;
        protected final CompletableFuture<String> resultFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (currentTry != null) {
                    currentTry.cancel(mayInterruptIfRunning);
                }
                return super.cancel(mayInterruptIfRunning);
            }
        };
        protected final String serverConfigPath;
        protected volatile int retry = 0;
        protected volatile Future<?> currentTry;
        /**
         * Something that can be used to uniquely identify the mail for the logfile.
         */
        protected final String loggingId;

        public EmailTask(@Nonnull Email email, @Nonnull String serverConfigPath, @Nonnull String loggingId) throws EmailSendingException {
            this.loggingId = loggingId;
            this.serverConfigPath = serverConfigPath;
            MimeMessage msg = email.getMimeMessage();
            if (msg == null) {
                try {
                    email.buildMimeMessage();
                } catch (EmailException e) {
                    throw new EmailSendingException(e);
                }
                msg = email.getMimeMessage();
            }
            mimeMessage = msg;
        }

        public void trySending() {
            retry = retry + 1;
            try {
                String messageId = sendEmail(mimeMessage, loggingId);
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
