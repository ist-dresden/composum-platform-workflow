package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.EmailService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
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
     * Whether the server is enabled.
     */
    public static final String PROP_SERVER_ENABLED = "enabled";
    /**
     * Kind of server: SMTP, SMTPS, STARTTLS.
     */
    public static final String PROP_SERVER_TYPE = "connectionType";
    /**
     * Value {@value #VALUE_SMTP} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_SMTP = "SMTP";
    /**
     * Value {@value #VALUE_SMTPS} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_SMTPS = "SMTPS";
    /**
     * Value {@value #VALUE_STARTTLS} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_STARTTLS = "STARTTLS";
    /**
     * SMTP server / relay hostname.
     */
    public static final String PROP_SERVER_HOST = "host";
    /**
     * SMTP server / relay port.
     */
    public static final String PROP_SERVER_PORT = "port";
    /**
     * Optional credential ID for the SMTP server / relay used with the {@link CredentialService#getMailAuthenticator(String, ResourceResolver)}.
     */
    public static final String PROP_CREDENTIALID = "credentialId";

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Reference
    protected CredentialService credentialService;

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

    @Override
    public String sendMail(@Nonnull Email email, @Nonnull Resource serverConfig) throws EmailException {
        verifyEnabled();
        try {
            initFromServerConfig(email, serverConfig);
        } catch (RepositoryException e) { // acl failure
            throw new EmailException(e);
        }
        String id = email.send();
        return id;
    }

    protected void initFromServerConfig(@Nonnull Email email, @Nonnull Resource serverConfig) throws RepositoryException, EmailException {
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
        @NotNull ValueMap vm = serverConfig.getValueMap();
        Boolean enabled = vm.get(PROP_SERVER_ENABLED, Boolean.class);
        if (!Boolean.TRUE.equals(enabled)) {
            LOG.warn("Trying to send email with disabled server {}", serverConfig.getPath());
            ;
            throw new EmailException("Email-server not enabled.");
        }
        email.setHostName(vm.get(PROP_SERVER_HOST, String.class));
        String type = vm.get(PROP_SERVER_TYPE, String.class);
        if (VALUE_SMTP.equals(type)) {
            email.setSmtpPort(vm.get(PROP_SERVER_PORT, Integer.class));
        } else if (VALUE_STARTTLS.equals(type)) {
            email.setSmtpPort(vm.get(PROP_SERVER_PORT, Integer.class));
            email.setStartTLSRequired(true);
        } else if (VALUE_SMTPS.equals(type)) {
            email.setSslSmtpPort(vm.get(PROP_SERVER_PORT, String.class));
            email.setSSLOnConnect(true);
        }
        String credentialId = vm.get(PROP_CREDENTIALID, String.class);
        if (StringUtils.isNotBlank(credentialId)) {
            Authenticator authenticator = credentialService.getMailAuthenticator(credentialId, serverConfig.getResourceResolver());
            email.setAuthenticator(authenticator);
        }
    }

    protected void verifyEnabled() throws EmailException {
        if (!isEnabled()) {
            throw new EmailException("Email service is not enabled.");
        }
    }

    @Override
    public boolean isEnabled() {
        Config mycfg = config;
        return mycfg != null && mycfg.enabled();
    }

    @Activate
    @Modified
    @Deactivate
    protected void modifyConfig(Config config) {
        this.config = config;
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
