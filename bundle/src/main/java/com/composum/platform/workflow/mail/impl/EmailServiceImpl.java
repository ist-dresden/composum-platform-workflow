package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.commons.util.SlingThreadPoolExecutorService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.platform.workflow.mail.EmailServerConfigModel;
import com.composum.platform.workflow.mail.EmailService;
import com.composum.sling.core.BeanContext;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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

    protected volatile ExecutorService executorService;

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

    protected Email prepareEmail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfigResource) throws EmailSendingException {
        EmailServerConfigModel serverConfig = new EmailServerConfigModel();
        serverConfig.initialize(new BeanContext.Service(serverConfigResource.getResourceResolver()), serverConfigResource);
        Email email = emailBuilder.buildEmail(placeholderService);
        try {
            Authenticator authenticator =
                    StringUtils.isNotBlank(serverConfig.getCredentialId()) ?
                            credentialService.getMailAuthenticator(serverConfig.getCredentialId(), serverConfigResource.getResourceResolver()) : null;
            initFromServerConfig(email, serverConfig, authenticator);
        } catch (RepositoryException e) { // acl failure
            throw new EmailSendingException(e);
        }
        return email;
    }

    @Nonnull
    @Override
    public Future<String> sendMail(@Nonnull EmailBuilder emailBuilder, @Nonnull Resource serverConfig) throws EmailSendingException {
        verifyEnabled();
        Email email = prepareEmail(emailBuilder, serverConfig);
        return executorService.submit(() -> sendEmail(email));
    }

    protected String sendEmail(Email email) throws EmailSendingException {
        try {
            return email.send();
        } catch (EmailException e) {
            throw new EmailSendingException(e);
        }
    }

    protected void initFromServerConfig(@Nonnull Email email, @Nonnull EmailServerConfigModel serverConfig, Authenticator authenticator) throws EmailSendingException {
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
            throw new EmailSendingException("Email-server not enabled.");
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
        LOG.info("activated");
        this.config = theConfig;
        if (isEnabled()) {
            if (executorService == null) {
                this.executorService = new SlingThreadPoolExecutorService(threadPoolManager, THREADPOOL_NAME);
            }
        } else {
            shutdownExecutorService();
        }
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

    }

}
