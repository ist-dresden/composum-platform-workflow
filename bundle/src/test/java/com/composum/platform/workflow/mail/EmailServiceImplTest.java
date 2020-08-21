package com.composum.platform.workflow.mail;

import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.mail.EmailServiceImpl;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jcr.RepositoryException;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmailService}.
 */
public class EmailServiceImplTest {

    @Rule
    public final ErrorCollectorAlwaysPrintingFailures ec = new ErrorCollectorAlwaysPrintingFailures();

    @Mock
    protected Resource serverConfigResource;

    @Mock
    protected CredentialService credentialService;

    @InjectMocks
    protected EmailServiceImpl service;

    @Before
    public void setUp() throws RepositoryException {
        MockitoAnnotations.initMocks(this);
        when(credentialService.getMailAuthenticator(anyString(), any(ResourceResolver.class))).thenReturn(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("apikey", "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");
                    }
                }
        );
        when(serverConfigResource.getValueMap()).thenReturn(
                new ValueMapDecorator(Map.of(
                        "host", "smtp.sendgrid.net",
                        "port", 587
                ))
        );
        service = new EmailServiceImpl();
    }

    @Test
    public void sendAMail() throws EmailException {
        Email email = new SimpleEmail();
        email.setFrom("yu4cheem@techno.ms");
        email.setSubject("TestMail");
        email.setMsg("This is a test mail ... :-)");
        email.addTo("hps@ist-software.com");
        service.sendMail(email, serverConfigResource);
    }

    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

}
