package com.composum.platform.workflow.mail.mail;

import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.EmailService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.Resource;
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

    public static final String PROP_SERVER_HOST = "host";
    public static final String PROP_SERVER_PORT = "port";
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
        email.setHostName("smtp.web.de");
        email.setSmtpPort(587);
        email.setAuthenticator(new DefaultAuthenticator("hstoerr", "Sitajo-9"));
        email.setStartTLSRequired(true);
        String id = email.send();
        return id;
    }

    protected void initFromServerConfig(@Nonnull Email email, @Nonnull Resource serverConfig) throws RepositoryException {
        if (serverConfig == null) {
            throw new IllegalArgumentException("No email server configuration given");
        }
        @NotNull ValueMap vm = serverConfig.getValueMap();
        email.setHostName(vm.get(PROP_SERVER_HOST, String.class));
        // FIXME(hps,21.08.20) variants in configuration (ssl / starttls)
        email.setSmtpPort(vm.get(PROP_SERVER_PORT, Integer.class));
        email.setStartTLSRequired(true);
        String credentialId = vm.get(PROP_CREDENTIALID, String.class);
        if (StringUtils.isNotBlank(credentialId)) {
            Authenticator authenticator = credentialService.getMailAuthenticator(credentialId, serverConfig.getResourceResolver());
            email.setAuthenticator(authenticator);
        }
    }

}
