package com.composum.platform.workflow.mail;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.util.FileHandleDataSource;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.sling.clientlibs.handle.FileHandle;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.mail.*;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.CompositeValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Class collecting the data for an email from email templates (resources) and explicit setters.
 * The texts can contain placeholders of the form ${placeholder} which are replaced using
 * {@link PlaceholderService#applyPlaceholders(BeanContext, String, Map)} - see there.
 */
public class EmailBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EmailBuilder.class);

    public static final String PROP_SUBJECT = "subject";
    public static final String PROP_TO = "to";
    public static final String PROP_FROM = "from";
    public static final String PROP_CC = "cc";
    public static final String PROP_BCC = "bcc";
    public static final String PROP_REPLYTO = "replyTo";
    public static final String PROP_BOUNCE_ADDRESS = "bounceAddress";
    public static final String PROP_BODY = "body";
    /**
     * Optional HTML body for the mail.
     */
    public static final String PROP_HTML = "html";

    protected final BeanContext beanContext;
    protected final Resource template;
    protected final Map<String, Object> placeholders = new LinkedHashMap<>();
    protected final Map<String, Object> overridingProperties = new LinkedHashMap<>();
    protected final ValueMap combinedProperties;

    public EmailBuilder(@Nonnull BeanContext context, @Nullable Resource template) {
        this.template = template;
        this.beanContext = context;
        if (template != null) {
            combinedProperties = new CompositeValueMap(
                    new ValueMapDecorator(overridingProperties),
                    template.getValueMap()
            );
        } else {
            combinedProperties = new ValueMapDecorator(overridingProperties);
        }
    }

    @Nonnull
    public Email buildEmail(@Nonnull PlaceholderService placeholderService) throws EmailSendingException {
        List<FileHandle> attachments = retrieveAttachments();
        try {
            Email email;
            String body = replacePlaceholders(combinedProperties.get(PROP_BODY, String.class), placeholderService);
            String html = replacePlaceholders(combinedProperties.get(PROP_HTML, String.class), placeholderService);
            if (isNotBlank(html)) {
                HtmlEmail htmlEmail = new HtmlEmail();
                if (isNotBlank(body)) {
                    htmlEmail.setTextMsg(body);
                }
                htmlEmail.setHtmlMsg(html);
                email = htmlEmail;
            } else if (!attachments.isEmpty()) {
                email = new MultiPartEmail();
            } else {
                    email = new SimpleEmail();
                    email.setMsg(body);
            }
            if (!attachments.isEmpty()) {
                MultiPartEmail multipartMail = (MultiPartEmail) email;
                for (FileHandle fileHandle : attachments) {
                    attach(fileHandle, multipartMail);
                }
            }
            email.setSubject(replacePlaceholders(combinedProperties.get(PROP_SUBJECT, String.class), placeholderService));
            email.setFrom(combinedProperties.get(PROP_FROM, String.class));
            List<InternetAddress> tos = adressesFrom(combinedProperties.get(PROP_TO, String[].class));
            if (isNotEmpty(tos)) {
                email.setTo(tos);
            }
            List<InternetAddress> replyTos = adressesFrom(combinedProperties.get(PROP_REPLYTO, String[].class));
            if (isNotEmpty(replyTos)) {
                email.setReplyTo(replyTos);
            }
            String bounceAddress = combinedProperties.get(PROP_BOUNCE_ADDRESS, String.class);
            if (isNotBlank(bounceAddress)) {
                email.setBounceAddress(bounceAddress);
            }
            List<InternetAddress> ccs = adressesFrom(combinedProperties.get(PROP_CC, String[].class));
            if (isNotEmpty(ccs)) {
                email.setCc(ccs);
            }
            List<InternetAddress> bccs = adressesFrom(combinedProperties.get(PROP_BCC, String[].class));
            if (isNotEmpty(bccs)) {
                email.setBcc(bccs);
            }
            return email;
        } catch (EmailSendingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailSendingException(e);
        }
    }

    protected void attach(FileHandle fileHandle, MultiPartEmail email) throws EmailException {
        DataSource ds = new FileHandleDataSource(fileHandle);
        email.attach(ds, fileHandle.getName(), null);
    }

    protected List<FileHandle> retrieveAttachments() {
        List<FileHandle> res = new ArrayList<>();
        if (template != null) {
            for (Resource child : template.getChildren()) {
                if (ResourceUtil.isFile(child)) {
                    res.add(new FileHandle(child));
                }
            }
        }
        return res;
    }

    protected List<InternetAddress> adressesFrom(String[] values) throws EmailSendingException {
        List<InternetAddress> adrs = new ArrayList<>();
        if (null != values && values.length > 0) {
            for (String value : values) {
                try {
                    adrs.add(new InternetAddress(value));
                } catch (AddressException e) {
                    LOG.warn("Could not convert adress {} for {}", value, template, e);
                    throw new EmailSendingException("Could not convert adress for " + template, e);
                }
            }
        }
        return adrs;
    }

    protected String replacePlaceholders(String prop, PlaceholderService placeholderService) {
        return isNotBlank(prop) ? placeholderService.applyPlaceholders(beanContext, prop, placeholders) : prop;
    }

    public EmailBuilder addPlaceholder(@Nonnull String placeholder, @Nonnull Object value) {
        placeholders.put(placeholder, value);
        return this;
    }

    public EmailBuilder addPlaceholders(@Nonnull Map<String, Object> placeholders) {
        placeholders.putAll(placeholders);
        return this;
    }

    public EmailBuilder setSubject(@Nonnull String subject) {
        overridingProperties.put(PROP_SUBJECT, subject);
        return this;
    }

    public EmailBuilder setFrom(@Nonnull String from) {
        overridingProperties.put(PROP_FROM, from);
        return this;
    }

    public EmailBuilder setTo(@Nonnull String... to) {
        overridingProperties.put(PROP_TO, to);
        return this;
    }

    public EmailBuilder setCC(@Nonnull String... cc) {
        overridingProperties.put(PROP_CC, cc);
        return this;
    }

    public EmailBuilder setBCC(@Nonnull String... bcc) {
        overridingProperties.put(PROP_BCC, bcc);
        return this;
    }

    public EmailBuilder setReplyTo(@Nonnull String... replyTo) {
        overridingProperties.put(PROP_REPLYTO, replyTo);
        return this;
    }

    public EmailBuilder setBounceAddress(@Nonnull String bounceAddress) {
        overridingProperties.put(PROP_BOUNCE_ADDRESS, bounceAddress);
        return this;
    }

    public EmailBuilder setBody(@Nonnull String body) {
        overridingProperties.put(PROP_BODY, body);
        return this;
    }

    public EmailBuilder setHTML(@Nonnull String html) {
        overridingProperties.put(PROP_HTML, html);
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("template", template)
                .append("overridingProperties", overridingProperties)
                .append("placeholders", placeholders)
                .toString();
    }
}
