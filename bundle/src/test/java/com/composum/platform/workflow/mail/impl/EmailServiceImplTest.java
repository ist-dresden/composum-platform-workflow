package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.content.service.PlaceholderServiceImpl;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import com.composum.sling.platform.staging.query.Query;
import com.composum.sling.platform.staging.query.QueryBuilder;
import com.composum.sling.platform.staging.query.impl.QueryBuilderAdapterFactory;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import com.composum.sling.platform.testing.testutil.JcrTestUtils;
import com.google.common.base.Function;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.InternetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmailServiceImpl}.
 */
public class EmailServiceImplTest {

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImplTest.class);

    protected static final String PATH_SERVERCFG = "/conf/some/servercfg";
    protected static final String PATH_INVALIDSERVERCFG = "/conf/some/invalidserver";

    @Rule
    public final ErrorCollectorAlwaysPrintingFailures ec = new ErrorCollectorAlwaysPrintingFailures().onFailure(this::printJcr);

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

        // the service resolver is closed after each use. Since it seems difficult to create another resolver,
        // we just prevent the closing.
        ResourceResolver serviceResolver = Mockito.spy(context.resourceResolver());
        Mockito.doNothing().when(serviceResolver).close();
        when(resourceResolverFactory.getServiceResourceResolver(any())).thenReturn(serviceResolver);

        when(config.enabled()).thenReturn(true);
        when(config.connectionTimeout()).thenReturn(1000);
        when(config.socketTimeout()).thenReturn(1000);
        when(threadPoolManager.get(anyString())).thenReturn(threadPool);
        doAnswer((invocation) -> {
            threadPool.shutdownNow();
            return null;
        }).when(threadPoolManager).release(threadPool);
        service.activate(config);

        context.registerAdapter(ResourceResolver.class, QueryBuilder.class,
                (Function) (resolver) ->
                        new QueryBuilderAdapterFactory().getAdapter(resolver, QueryBuilder.class));

        LOG.info("Current time: {}", System.currentTimeMillis());

    }

    protected void printJcr() {
        try {
            Thread.sleep(500); // wait for logging messages to be written
            System.out.flush();
            System.err.flush();
        } catch (InterruptedException e) { // haha.
            throw new RuntimeException(e);
        }
        JcrTestUtils.printResourceRecursivelyAsJson(context.resourceResolver().getResource("/conf"));
        JcrTestUtils.printResourceRecursivelyAsJson(context.resourceResolver().getResource("/var"));
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

    @Test // (timeout = 2000)
    public void noConnection() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        Throwable exception = null;
        try {
            Future<String> future = service.sendMail(email, invalidServerConfigResource);
            future.get(3000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            exception = e.getCause();
        } catch (EmailSendingException e) {
            LOG.info("Expected EmailSendingException; now checking cause.", e);
            exception = e;
        }
        ec.checkThat(exception, instanceOf(EmailSendingException.class));
        if (exception instanceof EmailSendingException) {
            EmailSendingException e = (EmailSendingException) exception;
            Throwable cause = e.getRootCause();
            boolean ok = (cause instanceof SocketException) || (cause instanceof SocketTimeoutException);
            if (!ok) {
                throw exception;
            }
        }
    }

    @Test // (timeout = 12000)
    public void retries() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        when(config.retries()).thenReturn(2);
        when(config.retryTime()).thenReturn(1);
        long begin = System.currentTimeMillis();
        Future<String> future = service.sendMail(email, invalidServerConfigResource);
        for (int i = 0; i < 10 && !future.isDone(); ++i) {
            Thread.sleep(1000);
            service.run();
        }
        try {
            future.get(20000, TimeUnit.MILLISECONDS);
            fail("Failure expected");
        } catch (ExecutionException e) {
            ec.checkThat(e.getCause().getMessage(), containsString("Giving up after 2 retries for"));
            long time = System.currentTimeMillis() - begin;
            ec.checkThat("" + time, time >= 4000, is(true));
            ec.checkThat("" + time, time < 10000, is(true));
            ec.checkThat(future.isDone(), is(true));
        }
    }

    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

    /**
     * Just for development purposes to try out queries.
     */
    @Test
    public void checkQuery() throws Exception {
        ResourceResolver resolver = context.resourceResolver();

        // create an element in the mail queue
        Email email = new SimpleEmail();
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setMsg("This is a test impl ... :-)");
        email.setTo(Collections.singletonList(new InternetAddress("somethingelse@example.net")));
        email.setHostName("192.0.2.1");
        QueuedEmail queuedEmail = new QueuedEmail("234u298j9sdij9", email, "/somecfg", null);
        queuedEmail.setNextTry(System.currentTimeMillis());
        queuedEmail.create(context.resourceResolver());
        resolver.commit();

        Query query = resolver.adaptTo(QueryBuilder.class).createQuery();
        query.path(QueuedEmail.PATH_MAILQUEUE);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis() + 1000000);
        query.condition(
                query.conditionBuilder().property(QueuedEmail.PROP_NEXTTRY).lt().val(calendar)
        );

        List<Resource> pendingMails = new ArrayList<>();
        query.execute().forEach(pendingMails::add);
        ec.checkThat(pendingMails.size(), is(1));

        calendar.setTimeInMillis(System.currentTimeMillis() - 1000000);
        pendingMails.clear();
        query.execute().forEach(pendingMails::add);
        ec.checkThat(pendingMails.size(), is(0));
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
