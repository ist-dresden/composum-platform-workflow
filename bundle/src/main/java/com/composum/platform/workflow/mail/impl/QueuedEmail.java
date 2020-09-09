package com.composum.platform.workflow.mail.impl;

import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Model for an email that is persisted in the JCR to make it resistent to
 */
class QueuedEmail {

    private static final Logger LOG = LoggerFactory.getLogger(QueuedEmail.class);

    /**
     * Location where the queued mails are saved. Access should be allowed only for the service user.
     */
    public static final String PATH_MAILQUEUE = "/var/composum/platform/mail/queue";

    public static final String PROP_LOGGINGID = "loggingId";
    public static final String PROP_EMAIL = "email";
    public static final String PROP_SERVERCONFIG = "serverConfig";

    protected String path;
    protected final String loggingId;
    protected final MimeMessage mimeMessage;
    protected final String serverConfigPath;

    /**
     * Read data from resource.
     */
    public QueuedEmail(@Nonnull Resource resource) throws EmailSendingException {
        path = resource.getPath();
        ValueMap vm = resource.getValueMap();
        loggingId = vm.get(PROP_LOGGINGID, String.class);
        mimeMessage = deserializeEmail(vm.get(PROP_EMAIL, InputStream.class));
        serverConfigPath = vm.get(PROP_SERVERCONFIG, String.class);
    }

    public QueuedEmail(@Nonnull String loggingId, @Nonnull Email email, @Nonnull String serverConfigPath) throws EmailSendingException {
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

    protected MimeMessage deserializeEmail(@Nonnull InputStream inputStream) throws EmailSendingException {
        SimpleEmail emailForSession = new SimpleEmail();
        emailForSession.setHostName("192.0.2.1"); // invalid IP, not actually used
        try {
            Session mailSession = emailForSession.getMailSession();
            MimeMessage mimeMessage = new MimeMessage(mailSession, inputStream);
            return mimeMessage;
        } catch (MessagingException | EmailException e) {
            LOG.warn("Trouble deserializing email {}", path, e);
            throw new EmailSendingException(e);
        }
    }

    public void save(@Nonnull ResourceResolver resolver, @Nonnull String path) throws EmailSendingException {
        try {
            Resource resource = ResourceUtil.getOrCreateResource(resolver, path, ResourceUtil.TYPE_SLING_FOLDER + "/" + ResourceUtil.TYPE_UNSTRUCTURED);
            ModifiableValueMap vm = resource.adaptTo(ModifiableValueMap.class);
            vm.put(PROP_LOGGINGID, loggingId);
            vm.put(PROP_SERVERCONFIG, serverConfigPath);
            vm.put(PROP_EMAIL, serializeEmail());
        } catch (EmailSendingException | RepositoryException e) {
            LOG.error("Could not write to {}", path, e);
            throw new EmailSendingException(e);
        }
    }

    protected InputStream serializeEmail() throws EmailSendingException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            mimeMessage.writeTo(bos);
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException | MessagingException e) {
            LOG.error("Could not serialize email {}", loggingId, e);
            throw new EmailSendingException(e);
        }
    }

    public String getLoggingId() {
        return loggingId;
    }

    public MimeMessage getMimeMessage() {
        return mimeMessage;
    }

    public String getServerConfigPath() {
        return serverConfigPath;
    }
}
