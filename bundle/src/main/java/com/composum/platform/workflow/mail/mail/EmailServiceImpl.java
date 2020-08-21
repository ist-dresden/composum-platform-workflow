package com.composum.platform.workflow.mail.mail;

import com.composum.platform.workflow.mail.EmailService;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of {@link EmailService} using Simple Java Mail.
 */
@Component(property = {
        Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Service"
})
public class EmailServiceImpl implements EmailService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public boolean isValid(@Nullable String emailAdress) {
        throw new UnsupportedOperationException("Not implemented yet."); // FIXME hps 20.08.20 not implemented
    }

    @Override
    public void sendMail(@Nonnull Email email) throws EmailException {
        email.setHostName("smtp.web.de");
        email.setSmtpPort(587);
        email.setAuthenticator(new DefaultAuthenticator("hstoerr", "Sitajo-9"));
        email.setStartTLSRequired(true);
        String id = email.send();
    }
}
