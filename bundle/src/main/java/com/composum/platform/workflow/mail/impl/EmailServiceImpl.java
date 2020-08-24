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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
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
public class EmailServiceImpl implements EmailService {

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
        try {
            initFromServerConfig(email, serverConfig);
        } catch (RepositoryException e) { // acl failure
            throw new EmailException(e);
        }
        String id = email.send();
        return id;
    }

    protected void initFromServerConfig(@Nonnull Email email, @Nonnull Resource serverConfig) throws RepositoryException, EmailException {
        if (serverConfig == null) {
            throw new IllegalArgumentException("No email server configuration given");
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

}
