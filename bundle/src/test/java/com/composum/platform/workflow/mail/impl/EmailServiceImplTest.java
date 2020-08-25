package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.credentials.CredentialService;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Before;
import org.junit.Ignore;
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
    protected Resource invalidServerConfigResource;

    @Mock
    protected CredentialService credentialService;

    @InjectMocks
    protected EmailServiceImpl service = new EmailServiceImpl();

    @Before
    public void setUp() throws RepositoryException {
        MockitoAnnotations.initMocks(this);
        when(credentialService.getMailAuthenticator(anyString(), any())).thenReturn(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("apikey", getPassword());
                    }
                }
        );
        when(serverConfigResource.getValueMap()).thenReturn(
                new ValueMapDecorator(Map.of(
                        "enabled", true,
                        "type", "STARTTLS",
                        "host", "smtp.sendgrid.net",
                        "port", 587,
                        "credentialId", "/some/thing"
                ))
        );
        when(invalidServerConfigResource.getValueMap()).thenReturn(
                new ValueMapDecorator(Map.of(
                        "enabled", true,
                        "type", "STARTTLS",
                        "host", "192.0.2.1", // invalid IP
                        "port", 587,
                        "credentialId", "/some/thing"
                ))
        );
    }

    /**
     * Hook for personal tests with a real mail server.
     */
    protected String getPassword() {
        return "somepassword";
    }

    @Test(expected = EmailException.class)
    public void sendInvalidMail() throws EmailException {
        Email email = new SimpleEmail();
        email.setFrom("wrong");
        email.setSubject("TestMail");
        email.setMsg("This is a test impl ... :-)");
        email.addTo("broken_address");
        service.sendMail(email, serverConfigResource);
    }

    /**
     * Caution: this test takes a looong time - it
     */
    @Test(expected = EmailException.class, timeout = 65000)
    @Ignore
    public void noconnection() throws EmailException {
        Email email = new SimpleEmail();
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setMsg("This is a test impl ... :-)");
        email.addTo("somethingelse@example.net");
        service.sendMail(email, invalidServerConfigResource);
    }


    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

}
