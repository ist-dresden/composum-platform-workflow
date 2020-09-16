package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.commons.util.SlingThreadPoolExecutorService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.platform.workflow.mail.EmailServerConfigModel;
import com.composum.platform.workflow.mail.EmailService;
import com.composum.sling.platform.staging.query.Query;
import com.composum.sling.platform.staging.query.QueryBuilder;
import com.google.common.collect.MapMaker;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.composum.platform.workflow.mail.EmailServerConfigModel.*;
import static com.composum.platform.workflow.mail.impl.QueuedEmail.PROP_NEXTTRY;
import static java.util.concurrent.TimeUnit.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Implementation of {@link EmailService} using Commons-Email.
 * The queue of unsent emails is kept persistently in the JCR at {@link QueuedEmail#PATH_MAILQUEUE} to ensure they
 * are sent even if the server crashes. Since we might work in a cluster on the same JCR, there is a protocol that prevents
 * that one queue entry is processed by two nodes simultaneously:
 * <ol>
 *     <li>First we set {@link QueuedEmail#getNextTry()} to the next try and mark it at {@link QueuedEmail#getQueuedAt()}
 *     with our {@link #serviceId} as reserved for ourselves. These entries are not yet put to the {@link #executorService} but are kept in {@link #reservedQueueEntries}.</li>
 *     <li>Only on the next {@link #run()} these entries are put to the {@link #executorService}. For each queue entry, when it's {@link EmailTask#trySending()} is called,
 *     we only do something if the {@link QueuedEmail#getQueuedAt()} is still our {@link #serviceId}.
 *     Otherwise we assume that another cluster node pulled it to himself in a race condition.</li>
 * </ol>
 * // FIXME(hps,15.09.20) How to ensure that it's processed preferredly at this node to make sure the futures get the right value?
 */
@Component(
        service = {EmailService.class, Runnable.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Service",
                "scheduler.expression=" + EmailServiceImpl.TIMERTICK_SCHEDULE,
                "scheduler.threadPool=" + EmailServiceImpl.THREADPOOL_NAME,
                // "scheduler.concurrent=false", // that should be here, but strangely the job isn't sheduled anymore.
                // "scheduler.runOn=SINGLE" // perhaps. Would avoid traps by parallel processing somewhat
        },
        immediate = true
)
@Designate(ocd = EmailServiceImpl.Config.class)
public class EmailServiceImpl implements EmailService, Runnable {

    /**
     * Path for server configurations, mail templates, etc. This is not enforced, just a suggestion.
     */
    @SuppressWarnings("unused")
    public static final String PATH_CONFIG = "/var/composum/platform/mail";

    /**
     * Schedule at which {@link #run()} is called to collect pending mails.
     * If changing, please remember to change {@link #TIMERTICK_LENGTH_MS}, too!
     */
    // FIXME(hps,09.09.20) Reduce call frequency later!
    protected static final String TIMERTICK_SCHEDULE = "0/10 * * * * ?";

    /**
     * Time in milliseconds between two calls of {@link #TIMERTICK_SCHEDULE}.
     */
    protected static final long TIMERTICK_LENGTH_MS = 10000;

    /**
     * The maximum time we keep futures waiting for the sent email around.
     */
    protected static final long MAXAGE_FUTURE = MILLISECONDS.convert(10, MINUTES);

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

    /**
     * Used to simulate scheduler.concurrent=false .
     */
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Maps the path in the queue to tasks that are currently queued or ready to be reused - mostly for updating the returned future.
     */
    protected final Map<String, EmailTask> inProcess = new MapMaker().concurrencyLevel(1).weakValues().makeMap();

    /**
     * We keep here all queue entries which we have reserved for ourselves for one {@link #run()} timertick
     * to prevent parallel sending of the emails from several cluster nodes.
     * Access synchronized with {@link #lock}.
     */
    protected final List<String> reservedQueueEntries = new ArrayList<>();

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
        inProcess.put(task.queuedEmailPath, task);
        Future<String> future = task.resultFuture;
        try {
            // Try to signal immediate errors with the email in some cases. This but shouldn't hold us up much.
            future.get(50, MILLISECONDS);
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
     * Initializes the email session within the email with the server configuration.
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
        LOG.info("Cron called");
        if (lock.writeLock().tryLock()) {
            try (ResourceResolver resolver = getServiceResolver()) {
                triggerProcessingOfReservedEntries(resolver);
                retrievePendingMails(resolver);
            } catch (EmailSendingException | InterruptedException | RuntimeException e) {
                LOG.error("Trouble managing queue", e);
            } finally {
                lock.writeLock().unlock();
                LOG.info("Cron done");
            }
            cleanupInProcess();
        } else {
            LOG.info("Cron currently running - skipping call");
        }
    }

    /**
     * Remove entries that are not in the executor and are too old.
     */
    protected void cleanupInProcess() {
        List<EmailTask> tasks = inProcess.values().stream().collect(Collectors.toList());
        for (EmailTask task : tasks) {
            Future<?> future = task.currentTry;
            if (future == null || future.isDone()) { // not in the executor
                if (task.lastRun < System.currentTimeMillis() - MAXAGE_FUTURE) {
                    EmailTask removed = inProcess.remove(task.queuedEmailPath);
                    future = removed.currentTry;
                    if (future != null && !future.isDone()) { // recheck since we might have concurrent modification
                        inProcess.put(removed.queuedEmailPath, removed);
                    } else {
                        LOG.info("Removing from internal queue since too old: {}", task.loggingId);
                    }
                }
            }
        }
    }

    protected void retrievePendingMails(ResourceResolver resolver) throws InterruptedException {
        // Now reserve new entries ready to retry
        Thread.sleep(RandomUtils.nextInt(0, 200)); // try to avoid races in a cluster
        List<Resource> pendingMails = queryPendingMails(resolver);
        Collections.shuffle(pendingMails);
        List<Resource> processList = new ArrayList<>();

        // immediately do things for which we have a future laying around.
        for (Iterator<Resource> it = pendingMails.iterator(); it.hasNext(); ) {
            Resource pendingMail = it.next();
            String pendingServiceId = pendingMail.getValueMap().get(QueuedEmail.PROP_QUEUED_AT, String.class);
            if (serviceId.equals(pendingServiceId) && inProcess.containsKey(pendingMail.getPath())) {
                processList.add(pendingMail);
                it.remove();
            }
        }

        // mails that have been touched by other cluster members we process only if they are at least two timer ticks
        // old, so that they are preferredly processed by their own cluster member.
        // we also try not to starve others for work, so we take only a couple of them.
        int maxothercount = pendingMails.size() / 3;
        long otherServerStealTime = System.currentTimeMillis() - 2 * TIMERTICK_LENGTH_MS;
        for (Resource pendingMail : pendingMails) {
            if (maxothercount >= 0) {
                String pendingServiceId = pendingMail.getValueMap().get(QueuedEmail.PROP_QUEUED_AT, String.class);
                Long nextTry = pendingMail.getValueMap().get(PROP_NEXTTRY, System.currentTimeMillis());
                if (serviceId.equals(pendingServiceId) || nextTry < otherServerStealTime) {
                    processList.add(pendingMail);
                    --maxothercount;
                }
            }
        }

        // now reserve what we have collected.
        for (Resource pendingMail : processList) {
            reserve(pendingMail);
        }
    }

    protected void triggerProcessingOfReservedEntries(ResourceResolver resolver) throws EmailSendingException {
        // First trigger processing of already reserved entries
        for (String reservedQueueEntryPath : reservedQueueEntries) {
            Resource reservedQueueEntry = resolver.getResource(reservedQueueEntryPath);
            if (null != reservedQueueEntry) {
                EmailTask oldTask = inProcess.get(reservedQueueEntryPath);
                if (oldTask == null) {
                    QueuedEmail queuedEmail = new QueuedEmail(reservedQueueEntry);
                    LOG.debug("Requeueing {} : {}", queuedEmail.getLoggingId(), reservedQueueEntryPath);
                    EmailTask task = new EmailTask(queuedEmail);
                    task.currentTry = executorService.submit(task::trySending);
                    inProcess.put(task.queuedEmailPath, task);
                } else if (oldTask.currentTry.isDone()) {
                    LOG.debug("Requeueing still present {} : {}", oldTask.loggingId, oldTask.queuedEmailPath);
                    oldTask.currentTry = executorService.submit(oldTask::trySending);
                }
            }
        }
        reservedQueueEntries.clear();
    }

    /**
     * Mark the queue entry that we are going to process it really soon now.
     */
    protected void reserve(Resource pendingMail) {
        String path = pendingMail.getPath();
        try (ResourceResolver resolver = getServiceResolver()) {
            QueuedEmail.reserve(resolver.getResource(path), serviceId, this::retryTime);
            resolver.commit();
            reservedQueueEntries.add(path);
        } catch (EmailSendingException | RuntimeException | PersistenceException e) {
            LOG.error("Trouble reserving {}", path, e);
        }
    }

    @Nonnull
    protected List<Resource> queryPendingMails(ResourceResolver resolver) {
        Query query = resolver.adaptTo(QueryBuilder.class).createQuery();
        query.path(QueuedEmail.PATH_MAILQUEUE).condition(
                query.conditionBuilder().property(PROP_NEXTTRY).lt().val(Calendar.getInstance())
        );
        List<Resource> pendingMails = new ArrayList<>();
        query.execute().forEach(pendingMails::add);
        return pendingMails;
    }

    /**
     * The time delay for the next retry in milliseconds.
     */
    protected long retryTime(int retry) {
        return (long) Math.pow(2, retry - 1) * MILLISECONDS.convert(config.retryTime(), SECONDS);
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

        protected volatile long lastRun;

        protected volatile Future<?> currentTry;

        /**
         * Something that can be used to uniquely identify the mail for the logfile.
         */
        protected final String loggingId;

        protected final String queuedEmailPath;

        public EmailTask(@Nonnull Email email, @Nonnull String serverConfigPath, @Nonnull String loggingId, @Nullable String credentialToken) throws EmailSendingException {
            this.loggingId = loggingId;
            QueuedEmail queuedEmail = new QueuedEmail(loggingId, email, serverConfigPath, credentialToken);
            queuedEmailPath = queuedEmail.getPath(); // FIXME(hps,10.09.20) tenant?
            queuedEmail.setNextTry(System.currentTimeMillis() + MILLISECONDS.convert(config.retryTime(), SECONDS));
            queuedEmail.setQueuedAt(serviceId);
            lastRun = System.currentTimeMillis();
            try (ResourceResolver serviceResolver = getServiceResolver()) {
                queuedEmail.save(serviceResolver);
                serviceResolver.commit();
            } catch (PersistenceException e) {
                LOG.error("Could not persist queued email at {}", queuedEmailPath, e);
                throw new EmailSendingException("Could not persist queued email", e);
            }
        }

        public EmailTask(@Nonnull QueuedEmail queuedEmail) {
            queuedEmailPath = queuedEmail.path;
            loggingId = queuedEmail.getLoggingId();
            lastRun = System.currentTimeMillis();
        }

        public void trySending() {
            LOG.debug(">>>trySending {}", loggingId);
            QueuedEmail queuedEmail = null;
            try {
                verifyServiceIsEnabled();
                try (ResourceResolver serviceResolver = getServiceResolver()) {
                    Resource queuedEmailResource = serviceResolver.getResource(queuedEmailPath);
                    if (queuedEmailResource != null) {
                        queuedEmail = new QueuedEmail(queuedEmailResource);
                        if (serviceId.equals(queuedEmail.getQueuedAt())) {
                            queuedEmail.save(serviceResolver); // make sure we can write to it by setting last modified time
                            serviceResolver.commit();

                            Email emailForSession = new SimpleEmail();
                            Resource serverConfigResource = serviceResolver.getResource(queuedEmail.getServerConfigPath());
                            if (serverConfigResource == null) {
                                LOG.error("Service resolver could not read server config {} for {}", queuedEmail.getServerConfigPath(), queuedEmail.getLoggingId());
                                throw new EmailSendingException("Service resolver could not read server configuration");
                            }
                            initializeEmailWithServerConfig(emailForSession, serverConfigResource, queuedEmail.getLoggingId(), queuedEmail.getCredentialToken());
                            Session session = emailForSession.getMailSession();

                            LOG.info("Processing {} retry {}", queuedEmail.getRetry(), loggingId);
                            String messageId = sendEmail(queuedEmail.getMimeMessage(session), loggingId);

                            removeQueuedEmail(serviceResolver);
                            resultFuture.complete(messageId);
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
                } catch (EmailException e) {
                    String logid = queuedEmail != null ? queuedEmail.getLoggingId() : queuedEmailPath;
                    throw new EmailSendingException("Exception sending " + logid, e);
                }
            } catch (EmailSendingException e) {
                Config conf = config;
                if (conf == null || !conf.enabled()) {
                    LOG.info("Emailservice disabled -> aborting {} , might be retried later, though.", loggingId);
                    resultFuture.completeExceptionally(new EmailSendingException("Emailservice disabled -> abort. Might be retried later, though."));
                } else if (e.isRetryable()) {
                    LOG.info("Try {} failed for {}", queuedEmail.getRetry(), loggingId, e);
                    if (queuedEmail.getRetry() >= conf.retries()) {
                        resultFuture.completeExceptionally(
                                new EmailSendingException("Giving up after " + queuedEmail.getRetry() + " retries for " + loggingId, e));
                        LOG.info("Giving up after {} retries for {}", queuedEmail.getRetry(), loggingId, e);
                    } else {
                        // FIXME(hps,14.09.20) What about the result future?
                        // nothing to do - we just wait until this is reintroduced into the queue
                    }
                } else {
                    resultFuture.completeExceptionally(e);
                    LOG.warn("Non-retryable error, giving up: try {} failed for {}", (queuedEmail != null ? queuedEmail.getRetry() : -1), loggingId, e);
                    removeQueuedEmail(null);
                }
            } catch (RuntimeException | Error e) {
                LOG.warn("Unexpected exception", e);
                resultFuture.completeExceptionally(e);
            } finally {
                if (resultFuture.isDone()) {
                    inProcess.remove(queuedEmailPath);
                }
                lastRun = System.currentTimeMillis();
                LOG.debug("<<<trySending {}", loggingId);
            }
        }

        protected void removeQueuedEmail(ResourceResolver serviceResolver) {
            ResourceResolver resolver = null;
            try {
                resolver = serviceResolver != null ? serviceResolver : getServiceResolver();
                Resource res = resolver.getResource(queuedEmailPath);
                if (res != null) {
                    LOG.info("Removing from email queue: {} for {}", queuedEmailPath, loggingId);

                    // FIXME(hps,15.09.20) actually delete
                    QueuedEmail eml = new QueuedEmail(res);
                    Calendar calendar = Calendar.getInstance();
                    eml.setNextTry(System.currentTimeMillis() + MILLISECONDS.convert(1000, DAYS));
                    eml.save(resolver);

                    // resolver.delete(res);
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

    }

    /**
     * Returned future from a EmailTask - it references the task to prevent it from being garbage collected.
     * (The task is retained in {@link #inProcess} until collected.)
     */
    protected static class EmailTaskFuture<V> implements Future<V> {

        protected final Future<V> delegate;

        /**
         * This keeps a reference to the EmailTask so that it is not deleted as long as this future is in use.
         */
        protected final EmailTask emailTask;

        public EmailTaskFuture(Future<V> delegate, EmailTask emailTask) {
            this.delegate = delegate;
            this.emailTask = emailTask;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public V get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }

}
