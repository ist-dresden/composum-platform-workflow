package com.composum.platform.workflow.mail.impl;

import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;

/**
 * Model for an email that is persisted in the JCR to make it resistent to
 */
class QueuedEmail {

    private static final Logger LOG = LoggerFactory.getLogger(QueuedEmail.class);

    /**
     * Location where the queued mails are saved. Access should be allowed only for the service user.
     */
    public static final String PATH_MAILQUEUE = "/var/composum/platform/mail/queue";

    /**
     * Location where the queued mails that exceeded the number of retries are moved. Access should be allowed only for the service user.
     */
    public static final String PATH_MAILQUEUE_FAILED = "/var/composum/platform/mail/queue-failed";

    /**
     * Location where delivered mails are saved, if so configured. Access should be allowed only for the service user.
     */
    public static final String PATH_MAILQUEUE_SENT = "/var/composum/platform/mail/queue-sent";

    public static final String PROP_LOGGINGID = "loggingId";
    public static final String PROP_EMAIL = "email";
    public static final String PROP_SERVERCONFIG = "serverConfig";
    public static final String PROP_CREDENTIALKEY = "credentialKey";
    public static final String PROP_RETRY = "retry";
    public static final String PROP_NEXTTRY = "nextTry";
    public static final String PROP_QUEUED_AT = "queuedAt";
    public static final String PROP_STATE = "state";

    public static final String STATE_SENDING = "sending";
    /**
     * Value for {@link #PROP_STATE} that says the mail was successfully sent.
     */
    public static final String STATE_SENT = "sent";
    /**
     * Value for {@link #PROP_STATE} that says that sending the mail failed, e.g. because the relay wasn't available
     * or the email content being broken.
     */
    public static final String STATE_FAILED = "failed";
    /**
     * Value for {@link #PROP_STATE} that says that the mail is in the queue of the corresponding cluster.
     */
    public static final String STATE_RESERVED = "reserved";
    /**
     * Value for {@link #PROP_STATE} that says that the mail sending was aborted by a {@link java.util.concurrent.Future#cancel(boolean)} on the future returned by {@link com.composum.platform.workflow.mail.EmailService#sendMail(Tenant, EmailBuilder, Resource)}.
     */
    public static final String STATE_CANCELLED = "cancelled";
    /**
     * Value for {@link #PROP_STATE} that says that sending the mail failed due to some internal error that should be investigated.
     */
    public static final String STATE_INTERNAL_ERROR = "internalError";

    /**
     * A special value for {@link #getNextTry()} that says "never retry". Prevents it being found by the
     * queries for {@link #PROP_NEXTTRY}. (1.1.9999)
     */
    public static final long NEXTTRY_NEVER = 253370764800000L;
    /** The {@value ResourceUtil#PROP_PRIMARY_TYPE} we use for queue entries. */
    public static final String PRIMARYTYPE = ResourceUtil.TYPE_UNSTRUCTURED;

    protected final String path;
    protected final String loggingId;
    protected final byte[] messageBytes;
    protected final String serverConfigPath;
    protected String credentialToken;
    protected final Long modified;
    protected int retry;
    protected long nextTry;
    protected String queuedAt;
    protected String state;

    /**
     * Read data from resource.
     */
    public QueuedEmail(@Nonnull Resource resource) throws EmailSendingException {
        path = resource.getPath();
        ValueMap vm = resource.getValueMap();
        loggingId = vm.get(PROP_LOGGINGID, String.class);
        try (InputStream mimeStream = vm.get(PROP_EMAIL, InputStream.class)) {
            messageBytes = IOUtils.toByteArray(mimeStream);
        } catch (IOException ioException) {
            throw new EmailSendingException("Error reading message for " + loggingId, ioException);
        }
        serverConfigPath = vm.get(PROP_SERVERCONFIG, String.class);
        credentialToken = vm.get(PROP_CREDENTIALKEY, String.class);
        queuedAt = vm.get(PROP_QUEUED_AT, String.class);
        retry = vm.get(PROP_RETRY, 0);
        nextTry = vm.get(PROP_NEXTTRY, System.currentTimeMillis());
        modified = vm.get(ResourceUtil.PROP_LAST_MODIFIED, Long.class);
        state = vm.get(PROP_STATE, String.class);
    }

    public QueuedEmail(@Nonnull String loggingId, @Nonnull Email email, @Nonnull String serverConfigPath, @Nullable String credentialToken, @Nonnull String folder) throws EmailSendingException {
        this.loggingId = loggingId;
        this.serverConfigPath = serverConfigPath;
        this.credentialToken = credentialToken;
        this.path = PATH_MAILQUEUE + "/" + folder + loggingId;
        MimeMessage msg = email.getMimeMessage();
        if (msg == null) {
            try {
                email.buildMimeMessage();
            } catch (EmailException e) {
                throw new EmailSendingException("Touble building mime message for " + loggingId, e);
            }
            msg = email.getMimeMessage();
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            msg.writeTo(bo);
        } catch (IOException | MessagingException e) {
            throw new EmailSendingException("Error writing message " + loggingId, e);
        }
        messageBytes = bo.toByteArray();
        this.modified = null;
    }

    protected MimeMessage deserializeEmail(@Nonnull InputStream inputStream, @Nonnull Session mailSession) throws EmailSendingException {
        try {
            MimeMessage mimeMessage = new MimeMessage(mailSession, inputStream);
            return mimeMessage;
        } catch (MessagingException e) {
            LOG.warn("Trouble deserializing email {}", path, e);
            throw new EmailSendingException(e);
        }
    }

    /**
     * Reserves the queued email for the given service id.
     */
    public static void reserve(@Nullable Resource resource, @Nonnull String serviceId, @Nonnull Function<Integer, Long> retryTime) {
        if (resource != null) {
            ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
            put(mvm, PROP_STATE, STATE_RESERVED);
            put(mvm, ResourceUtil.JCR_LASTMODIFIED, Calendar.getInstance());
            put(mvm, PROP_QUEUED_AT, serviceId);
            int retry = mvm.get(PROP_RETRY, 0);
            long now = System.currentTimeMillis();
            Calendar nextTryCalendar = Calendar.getInstance();
            long delay = retryTime.apply(retry);
            nextTryCalendar.setTimeInMillis(nextTryCalendar.getTimeInMillis() + delay);
            put(mvm, PROP_NEXTTRY, nextTryCalendar);
            LOG.info("Reserving {} - retry {} time {}", resource.getPath(), retry, nextTryCalendar.getTime());
        }
    }

    public void create(@Nonnull ResourceResolver resolver) throws EmailSendingException {
        try {
            Resource resource = ResourceUtil.getOrCreateResource(resolver, path, ResourceUtil.TYPE_SLING_FOLDER + "/" + PRIMARYTYPE);
            writeToResource(resource);
        } catch (RepositoryException e) {
            LOG.error("Could not write to {}", path, e);
            throw new EmailSendingException(e);
        }
    }

    public void update(@Nonnull ResourceResolver resolver) throws EmailSendingException {
        try {
            resolver.refresh();
            Resource resource = resolver.getResource(path);
            if (resource != null) {
                writeToResource(resource);
            } else {
                LOG.error("Bug? Queued email vanished: {}", path);
                throw new EmailSendingException("Queued email vanished: " + path);
            }
        } catch (RuntimeException e) {
            LOG.error("Could not write to {}", path, e);
            throw new EmailSendingException(e);
        }
    }

    protected void writeToResource(Resource resource) {
        ModifiableValueMap vm = resource.adaptTo(ModifiableValueMap.class);
        put(vm, ResourceUtil.PROP_MIXINTYPES, new String[]{ResourceUtil.MIX_CREATED, ResourceUtil.MIX_LAST_MODIFIED});
        put(vm, PROP_LOGGINGID, loggingId);
        put(vm, PROP_SERVERCONFIG, serverConfigPath);
        put(vm, PROP_CREDENTIALKEY, credentialToken);
        put(vm, PROP_EMAIL, new ByteArrayInputStream(messageBytes));
        put(vm, PROP_RETRY, retry);
        Calendar nextTryCalendar = Calendar.getInstance();
        nextTryCalendar.setTimeInMillis(nextTry);
        put(vm, PROP_NEXTTRY, nextTryCalendar);
        put(vm, ResourceUtil.JCR_LASTMODIFIED, Calendar.getInstance());
        put(vm, PROP_QUEUED_AT, queuedAt);
        put(vm, PROP_STATE, state);
        if (LOG.isDebugEnabled()) {
            LOG.info("Saved: " + toString());
        }
    }

    protected static void put(@Nonnull ModifiableValueMap vm, @Nonnull String key, @Nullable Object value) {
        if (value == null) {
            vm.remove(key);
        } else {
            vm.put(key, value);
        }
    }

    public String getLoggingId() {
        return loggingId;
    }

    public MimeMessage getMimeMessage(@Nonnull Session session) throws EmailSendingException {
        return deserializeEmail(new ByteArrayInputStream(messageBytes), session);
    }

    public String getServerConfigPath() {
        return serverConfigPath;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public void setCredentialToken(String credentialToken) {
        this.credentialToken = credentialToken;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }

    /**
     * The time for the next try sending the email, as in {@link System#currentTimeMillis()}.
     */
    public long getNextTry() {
        return nextTry;
    }

    /**
     * The time for the next try sending the email, as in {@link System#currentTimeMillis()}.
     */
    public void setNextTry(long nextTry) {
        this.nextTry = nextTry;
    }

    public String getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(String queuedAt) {
        this.queuedAt = queuedAt;
    }

    /**
     * The time of the last modification, as in {@link System#currentTimeMillis()}.
     */
    public Long getModified() {
        return modified;
    }

    /**
     * The path where the queued email is saved.
     */
    public String getPath() {
        return path;
    }

    /**
     * The state of the mail, if applicable. One of null,
     * {@link #STATE_RESERVED}, {@link #STATE_SENDING}, {@link #STATE_SENT}, {@link #STATE_FAILED}.
     */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        if (loggingId != null) {
            builder.append("loggingId", loggingId);
        }
        if (path != null) {
            builder.append("path", path);
        }
        if (state != null) {
            builder.append("state", state);
        }
        if (serverConfigPath != null) {
            builder.append("serverConfigPath", serverConfigPath);
        }
        builder.append("retry", retry);
        builder.append("nextTry", new Date(nextTry));
        if (modified != null) {
            builder.append("modified", modified);
        }
        if (queuedAt != null) {
            builder.append("queuedAt", queuedAt);
        }
        if (credentialToken != null) {
            builder.append("credentialToken", "(omitted)");
        }
        if (messageBytes != null) {
            builder.append("messageBytes", "(omitted)");
        }
        return builder.toString();
    }
}
