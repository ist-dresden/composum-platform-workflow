package com.composum.platform.workflow.mail.mail;

import com.composum.platform.workflow.mail.EmailService;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressValidator;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        return EmailAddressValidator.isValid(emailAdress);
    }
}
