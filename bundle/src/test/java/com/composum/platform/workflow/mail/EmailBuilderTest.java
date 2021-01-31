package com.composum.platform.workflow.mail;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.content.service.PlaceholderServiceImpl;
import com.composum.sling.core.BeanContext;
import com.composum.sling.platform.testing.testutil.ErrorCollectorAlwaysPrintingFailures;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ModifiableValueMapDecorator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmailBuilder}.
 */
public class EmailBuilderTest {

    @Rule
    public final ErrorCollectorAlwaysPrintingFailures ec = new ErrorCollectorAlwaysPrintingFailures();

    @Mock
    protected Resource templateResource;

    protected ModifiableValueMap templateMap = new ModifiableValueMapDecorator(new LinkedHashMap<>());

    protected PlaceholderService placeholderService;

    @Before
    public void setUp() {
        placeholderService = new PlaceholderServiceImpl();
        MockitoAnnotations.initMocks(this);
        when(templateResource.getValueMap()).thenReturn(templateMap);
    }

    @Test
    public void simpleEmail() throws Exception {
        EmailBuilder builder = new EmailBuilder(new BeanContext.Map(), null);
        builder.setFrom("from@somewhere.net");
        builder.setTo("to1@example.net", "to2@example.net");
        builder.setCC("cc@example.net");
        builder.setBCC("bcc@example.net");
        builder.setReplyTo("reply@example.net");
        builder.setSubject("Hallo ${name}!");
        builder.setBody("Good morning, Mr. ${surname}!\nHow are you, ${name}?");
        builder.addPlaceholder("surname", "Meier");
        builder.addPlaceholder("name", "Franz");
        Email mail = builder.buildEmail(placeholderService);
        ec.checkThat(normalizedMessage(mail), is("Date: (deleted)\n" +
                "From: from@somewhere.net\n" +
                "Reply-To: reply@example.net\n" +
                "To: to1@example.net, to2@example.net\n" +
                "Cc: cc@example.net\n" +
                "Bcc: bcc@example.net\n" +
                "Message-ID: (deleted)\n" +
                "Subject: Hallo Franz!\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: text/plain; charset=us-ascii\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                "\n" +
                "Good morning, Mr. Meier!\n" +
                "How are you, Franz?"));
    }

    @Test
    public void simpleEmailCombiningTest() throws Exception {
        EmailBuilder builder = new EmailBuilder(new BeanContext.Map(), templateResource);
        templateMap.put("from", "from@example.net");
        templateMap.put("subject", "overridden subject ${X}");
        templateMap.put("body", "The body ${Y;%+10.4f}"); // format see https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Formatter.html
        builder.setSubject("overriding subject ${X}");
        builder.setTo("to@example.net");
        builder.addPlaceholder("X", "xxx");
        builder.addPlaceholder("Y", Math.E);
        Email mail = builder.buildEmail(placeholderService);
        ec.checkThat(normalizedMessage(mail), is("Date: (deleted)\n" +
                "From: from@example.net\n" +
                "To: to@example.net\n" +
                "Message-ID: (deleted)\n" +
                "Subject: overriding subject xxx\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: text/plain; charset=us-ascii\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                "\n" +
                "The body    +2,7183"));
    }

    protected String normalizedMessage(Email email) throws IOException, MessagingException, EmailException {
        email.setHostName("127.0.0.1");
        email.buildMimeMessage();
        MimeMessage msg = email.getMimeMessage();
        OutputStream buf = new ByteArrayOutputStream();
        msg.writeTo(buf);
        // replace variable things
        return buf.toString()
                .replaceAll("(?m)^Date:.*$", "Date: (deleted)")
                .replaceAll("(?m)^Message-ID:.*$", "Message-ID: (deleted)")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("Part_[0-9._]+", "(partid)");
    }

    @Test
    public void htmlEmail() throws Exception {
        EmailBuilder builder = new EmailBuilder(new BeanContext.Map(), null);
        builder.setFrom("from@somewhere.net");
        builder.setTo("to@example.net");
        builder.setSubject("Hallo ${name}!");
        builder.setBody("Good morning, Mr. ${name}!");
        builder.setHTML("<P>Good morning, Mr. <EM>${name}</EM>!</P>");
        builder.addPlaceholder("name", "Franz");
        Email mail = builder.buildEmail(placeholderService);
        ec.checkThat(normalizedMessage(mail), is("Date: (deleted)\n" +
                "From: from@somewhere.net\n" +
                "To: to@example.net\n" +
                "Message-ID: (deleted)\n" +
                "Subject: Hallo Franz!\n" +
                "MIME-Version: 1.0\n" +
                "Content-Type: multipart/alternative; \n" +
                "\tboundary=\"----=_(partid)\"\n" +
                "\n" +
                "------=_(partid)\n" +
                "Content-Type: text/plain; charset=us-ascii\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                "\n" +
                "Good morning, Mr. Franz!\n" +
                "------=_(partid)\n" +
                "Content-Type: text/html; charset=us-ascii\n" +
                "Content-Transfer-Encoding: 7bit\n" +
                "\n" +
                "<P>Good morning, Mr. <EM>Franz</EM>!</P>\n" +
                "------=_(partid)--\n"));
    }



}
