package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.content.service.PlaceholderServiceImpl;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmailService}.
 */
public class EmailServiceImplTest {

    protected static final String PATH_SERVERCFG = "/some/servercfg";
    protected static final String PATH_INVALIDSERVERCFG = "/some/invalidserver";

    @Rule
    public final ErrorCollectorAlwaysPrintingFailures ec = new ErrorCollectorAlwaysPrintingFailures();

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    protected Resource serverConfigResource;

    protected Resource invalidServerConfigResource;

    @Mock
    protected ResourceResolverFactory resourceResolverFactory;

    @Mock
    protected CredentialService credentialService;

    @Mock
    protected ThreadPoolManager threadPoolManager;

    @Spy
    protected TestingThreadPool threadPool = new TestingThreadPool();

    @Mock
    protected EmailServiceImpl.Config config;

    @Spy
    protected PlaceholderService placeholderService = new PlaceholderServiceImpl();

    @InjectMocks
    protected EmailServiceImpl service = new EmailServiceImpl();

    protected BeanContext beanContext = new BeanContext.Map();

    @Before
    public void setUp() throws RepositoryException, LoginException {
        MockitoAnnotations.initMocks(this);
        when(credentialService.getMailAuthenticator(anyString(), any())).thenReturn(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("apikey", getPassword());
                    }
                }
        );
        serverConfigResource = context.build().resource(PATH_SERVERCFG, ResourceUtil.PROP_PRIMARY_TYPE, ResourceUtil.TYPE_UNSTRUCTURED,
                "enabled", true,
                "connectionType", "STARTTLS",
                "host", "smtp.sendgrid.net",
                "port", 587,
                "credentialId", "/some/thing").commit().getCurrentParent();
        invalidServerConfigResource = context.build().resource(PATH_INVALIDSERVERCFG, ResourceUtil.PROP_PRIMARY_TYPE, ResourceUtil.TYPE_UNSTRUCTURED,
                "enabled", true,
                "connectionType", "STARTTLS",
                "host", "192.0.2.1", // invalid IP
                "port", 587,
                "credentialId", "/some/thing").commit().getCurrentParent();
        when(resourceResolverFactory.getServiceResourceResolver(any())).then((args) ->
                context.resourceResolver().clone(null)
        );
        when(config.enabled()).thenReturn(true);
        when(config.connectionTimeout()).thenReturn(1000);
        when(config.socketTimeout()).thenReturn(1000);
        when(threadPoolManager.get(anyString())).thenReturn(threadPool);
        doAnswer((invocation) -> {
            threadPool.shutdownNow();
            return null;
        }).when(threadPoolManager).release(threadPool);
        service.activate(config);
    }

    @After
    public void tearDown() {
        service.deactivate();
        if (!threadPool.isShutdown()) {
            threadPool.shutdownNow();
            fail("Threadpool was not shut down!");
        }
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

    @Test(timeout = 2000)
    public void noConnection() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        Throwable exception = null;
        try {
            service.sendMail(email, invalidServerConfigResource).get(3000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            exception = e.getCause();
        } catch (EmailSendingException e) {
            exception = e;
        }
        ec.checkThat(exception, instanceOf(EmailSendingException.class));
        if (exception instanceof EmailSendingException) {
            EmailSendingException e = (EmailSendingException) exception;
            Throwable cause = e.getRootCause();
            ec.checkThat(cause.getClass().getName(), cause, anyOf(instanceOf(SocketException.class), instanceOf(SocketTimeoutException.class)));
        }
    }

    @Test(timeout = 12000)
    public void retries() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        when(config.retries()).thenReturn(3);
        when(config.retryTime()).thenReturn(1);
        long begin = System.currentTimeMillis();
        Future<String> future = service.sendMail(email, invalidServerConfigResource);
        try {
            future.get(20000, TimeUnit.MILLISECONDS);
            fail("Failure expected");
        } catch (ExecutionException e) {
            ec.checkThat(e.getCause().getMessage(), is("Giving up after 3 retries."));
            long time = System.currentTimeMillis() - begin;
            ec.checkThat("" + time, time >= 4000, is(true));
            ec.checkThat("" + time, time < 10000, is(true));
            ec.checkThat(future.isDone(), is(true));
        }
    }

    // FIXME(hps,01.09.20) sinnvolles logging im EmailService

    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

    protected static class TestingThreadPool extends ThreadPoolExecutor implements ThreadPool {

        public TestingThreadPool() {
            super(2, 5, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
        }

        @Override
        public String getName() {
            return "testing threadpool";
        }

        @Override
        public ThreadPoolConfig getConfiguration() {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

    }

}
