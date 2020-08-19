package com.composum.platform.workflow.mail;

import com.composum.platform.workflow.mail.mail.EmailServiceImpl;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link EmailService}.
 */
public class EmailServiceImplTest {

    @Rule
    public final ErrorCollectorAlwaysPrintingFailures ec = new ErrorCollectorAlwaysPrintingFailures();

    protected EmailServiceImpl service;

    @Before
    public void setUp() {
        service = new EmailServiceImpl();
    }

    @Test
    public void isValid() {
        ec.checkThat(service.isValid("bla@blu.example.net"), is(true));
        ec.checkThat(service.isValid("broken"), is(false));
        ec.checkThat(service.isValid(""), is(false));
        ec.checkThat(service.isValid(null), is(false));
    }

}
