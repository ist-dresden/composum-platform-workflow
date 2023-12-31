package com.composum.platform.workflow.mail.impl;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.content.service.PlaceholderServiceImpl;
import com.composum.platform.commons.credentials.CredentialService;
import com.composum.platform.commons.credentials.impl.CredentialServiceImpl;
import com.composum.platform.workflow.mail.EmailBuilder;
import com.composum.platform.workflow.mail.EmailSendingException;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import com.composum.sling.platform.staging.query.Query;
import com.composum.sling.platform.staging.query.QueryBuilder;
import com.composum.sling.platform.staging.query.impl.QueryBuilderAdapterFactory;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import com.composum.sling.platform.testing.testutil.JcrTestUtils;
import com.composum.sling.platform.testing.testutil.junitcategory.SlowTest;
import com.composum.sling.platform.testing.testutil.junitcategory.TimingSensitive;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolConfig;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.apache.sling.tenant.Tenant;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

    @Spy
    protected CredentialService credentialService = new CredentialServiceImpl() {
        @Override
        public String sendMail(@Nonnull MimeMessage message, @Nullable String credentialIdOrToken, @Nullable ResourceResolver aclCheckResolver) throws RepositoryException, IOException, MessagingException {
            Transport.send(message, getEmailRelayUser(), getEmailRelayPassword());
            return message.getMessageID();
        }

        @Override
        public String getAccessToken(@Nonnull String credentialId, @Nullable ResourceResolver aclCheckResolver, @Nonnull String type) throws RepositoryException, IllegalArgumentException, IllegalStateException {
            return "the token";
        }
    };

    @Mock
    protected ThreadPoolManager threadPoolManager;

    @Spy
    protected TestingThreadPool threadPool = new TestingThreadPool();

    @Mock
    protected EmailServiceImpl.Config config;

    @Mock
    protected EmailCleanupService emailCleanupService;

    @Spy
    protected PlaceholderService placeholderService = new PlaceholderServiceImpl();

    @InjectMocks
    protected EmailServiceImpl service = new EmailServiceImpl();

    protected BeanContext beanContext = new BeanContext.Map();

    @Mock
    protected Tenant tenant;

    @Before
    public void setUp() throws RepositoryException, LoginException {
        MockitoAnnotations.initMocks(this);
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
        when(config.debugInteractions()).thenReturn(true);
        when(threadPoolManager.get(anyString())).thenReturn(threadPool);
        doAnswer((invocation) -> {
            threadPool.shutdownNow();
            return null;
        }).when(threadPoolManager).release(threadPool);
        service.activate(config);

        when(tenant.getId()).thenReturn("thetenant");

        when(emailCleanupService.keepDeliveredEmails()).thenReturn(true);
        when(emailCleanupService.keepFailedEmails()).thenReturn(true);

        context.registerAdapter(ResourceResolver.class, QueryBuilder.class,
                (Function) (resolver) ->
                        new QueryBuilderAdapterFactory().getAdapter(resolver, QueryBuilder.class));

        LOG.info("Test start time: {} = {}", System.currentTimeMillis(), new Date());

    }

    protected PasswordAuthentication getAuthenticatorForRelay() {
        return new PasswordAuthentication("apikey", getEmailRelayPassword());
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
        LOG.info("Test stop time: {} = {}", System.currentTimeMillis(), new Date());
        service.deactivate();
        if (!threadPool.isShutdown()) {
            threadPool.shutdownNow();
            fail("Threadpool was not shut down!");
        }
    }

    /**
     * Hook for personal tests with a real mail server.
     */
    protected String getEmailRelayUser() {
        return "someuser";
    }

    /**
     * Hook for personal tests with a real mail server.
     */
    protected String getEmailRelayPassword() {
        return "somepassword";
    }

    @Test(expected = EmailSendingException.class, timeout = 100)
    public void sendInvalidMail() throws EmailSendingException {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("wrong");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("broken_address");
        service.sendMail(tenant, email, serverConfigResource);
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
            Future<String> future = service.sendMail(tenant, email, invalidServerConfigResource);
            future.get(3000, MILLISECONDS);
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
    @Category({SlowTest.class, TimingSensitive.class})
    public void retries() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        when(config.retries()).thenReturn(2);
        when(config.retryTime()).thenReturn(1);
        long begin = System.currentTimeMillis();
        Future<String> future = service.sendMail(tenant, email, invalidServerConfigResource);
        for (int i = 0; i < 25 && !future.isDone(); ++i) {
            Thread.sleep(500);
            service.run();
        }
        try {
            future.get(20000, MILLISECONDS);
            fail("Failure expected");
        } catch (ExecutionException e) {
            ec.checkThat(e.getCause().getMessage(), containsString("Giving up after 2 retries for"));
            long time = System.currentTimeMillis() - begin;
            ec.checkThat("" + time, time >= 4000, is(true));
            ec.checkThat("" + time, time < 15000, is(true));
            ec.checkThat(future.isDone(), is(true));
        }
    }

    @Test // (timeout = 2000)
    public void cancelingImmediately() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        when(config.retries()).thenReturn(2);
        when(config.retryTime()).thenReturn(1);
        Future<String> future = service.sendMail(null, email, invalidServerConfigResource);
        future.cancel(true);
        LOG.info("Canceled.");
        ec.checkFailsWith(future::get, instanceOf(CancellationException.class));
        Thread.sleep(1500); // await failed connect attempt in the background
        // If there is no network it's possible that the first try was done before we got to cancel the future.
        // in that case we have to offer a retry. If the network was there, the future is cancelled before
        // the first delivery attempt is finished.
        boolean emailInFailedQueue = false;
        for (int i = 0; i < 4 && !emailInFailedQueue; ++i) {
            Thread.sleep(500);
            service.run();
            Resource failedQueue = context.resourceResolver().getResource(QueuedEmail.PATH_MAILQUEUE_FAILED);
            emailInFailedQueue = failedQueue != null && IteratorUtils.toArray(failedQueue.listChildren()).length == 1;
        }
        LOG.info("Check time: {} = {}", System.currentTimeMillis(), new Date());
        ec.checkThat(emailInFailedQueue, is(true));
    }


    @Test // (timeout = 2000)
    @Category({SlowTest.class, TimingSensitive.class})
    public void canceling() throws Throwable {
        EmailBuilder email = new EmailBuilder(beanContext, null);
        email.setFrom("something@example.net");
        email.setSubject("TestMail");
        email.setBody("This is a test impl ... :-)");
        email.setTo("somethingelse@example.net");
        when(config.retries()).thenReturn(3);
        when(config.retryTime()).thenReturn(1);
        Future<String> future = service.sendMail(tenant, email, invalidServerConfigResource);
        Thread.sleep(1500); // wait until first attempt is done and email is in retry
        future.cancel(false);
        LOG.info("Canceled.");
        ec.checkFailsWith(() -> future.get(1, MILLISECONDS), instanceOf(CancellationException.class));
        boolean emailInFailedQueue = false;
        for (int i = 0; i < 4 && !emailInFailedQueue; ++i) {
            Thread.sleep(500);
            service.run();
            Resource failedQueue = context.resourceResolver().getResource(QueuedEmail.PATH_MAILQUEUE_FAILED);
            emailInFailedQueue = failedQueue != null && IteratorUtils.toArray(failedQueue.listChildren()).length == 1;
        }
        LOG.info("Check time: {} = {}", System.currentTimeMillis(), new Date());
        ec.checkThat(emailInFailedQueue, is(true));
    }

    @Test(expected = CancellationException.class)
    public void checkFutureCancelingBehaviour() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            future.cancel(true);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Cancel should not throw up.");
        }
        future.get();
        // Interesting: this throws a CancellationException created at the cancel call - the stacktrace when calling the get is lost!
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
        QueuedEmail queuedEmail = new QueuedEmail("234u298j9sdij9", email, "/somecfg", null, "");
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

    /**
     * Tests what happens when email sending is interrupted with {@link Thread#interrupt()}.
     * It seems the email process is not interrupted.
     */
    // @Test
    public void checkEmailInterruptionBehavior() throws Exception {
        final Email email = new SimpleEmail();
        email.setFrom("something@nothing.example.net");
        email.setSubject("TestMail");
        email.setMsg("This email just hangs since it has an invalid host (illegal IP).");
        email.setTo(Collections.singletonList(new InternetAddress("somethingelse@nothing.example.net")));
        email.setHostName("192.0.2.1");
        email.setSocketTimeout(5000);
        email.setSocketConnectionTimeout(5000);
        CompletableFuture<String> future = new CompletableFuture<>();
        SynchronousQueue<String> queue = new SynchronousQueue<>();
        Thread t = new Thread("mailer") {
            @Override
            public void run() {
                try {
                    queue.put("wait for me");
                    future.complete(email.send());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            }
        };
        t.start();
        queue.poll(1, TimeUnit.SECONDS);
        Thread.sleep(1000); // now the thread should hang in the sending process.
        Exception stacktrace = new Exception("Stacktrace, not thrown");
        stacktrace.setStackTrace(t.getStackTrace());
        stacktrace.printStackTrace();
        t.interrupt();
        future.get(15, TimeUnit.SECONDS);
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
