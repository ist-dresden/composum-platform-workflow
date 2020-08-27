package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.content.service.PlaceholderServiceImpl;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.BeanContext;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import javax.jcr.RepositoryException;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

    @Mock
    protected ThreadPoolManager threadPoolManager;

    @Mock
    protected ThreadPool threadPool;

    @Mock
    protected EmailServiceImpl.Config config;

    @Spy
    protected PlaceholderService placeholderService = new PlaceholderServiceImpl();

    @InjectMocks
    protected EmailServiceImpl service = new EmailServiceImpl();

    protected BeanContext beanContext = new BeanContext.Map();

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
        when(config.enabled()).thenReturn(true);
        when(config.connectionTimeout()).thenReturn(1000);
        when(config.socketTimeout()).thenReturn(1000);
        when(threadPoolManager.get(anyString())).thenReturn(threadPool);
        when(threadPool.submit(any(Callable.class))).thenAnswer(
                (invocation) -> {
                    CompletableFuture future = new CompletableFuture();
                    try {
                        Callable callable = invocation.getArgument(0, Callable.class);
                        future.complete(callable.call());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                    return future;
                }
        );
        service.activate(config);
    }

    @After
    public void tearDown() {
        service.deactivate();
    }

    /**
     * Hook for personal tests with a real mail server.
     */
    protected String getPassword() {
        return "somepassword";
    }

    @Test(expected = EmailSendingException.class, timeout = 100)
    public void sendInvalidMail() throws EmailSendingException {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("wrong");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("broken_address");
        service.sendMail(email, serverConfigResource);
    }

    @Test(expected = EmailSendingException.class, timeout = 2000)
    public void noConnection() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        try {
            service.sendMail(email, invalidServerConfigResource).get(3000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

}
