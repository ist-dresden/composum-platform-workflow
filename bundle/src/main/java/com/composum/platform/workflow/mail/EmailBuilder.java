package com.composum.platform.workflow.mail;

import com.composum.platform.commons.content.service.PlaceholderService;
import com.composum.platform.commons.util.FileResourceDataSource;
import com.composum.sling.clientlibs.handle.FileHandle;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.*;
import java.util.stream.Collectors;

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
    /**
     * Attachments that are not contained in the template.
     */
    protected final List<Pair<String, DataSource>> additionalAttachments = new ArrayList<>();

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
            boolean haveAttachments = !attachments.isEmpty() || !additionalAttachments.isEmpty();
            if (isNotBlank(html)) {
                HtmlEmail htmlEmail = new HtmlEmail();
                if (isNotBlank(body)) {
                    htmlEmail.setTextMsg(body);
                }
                htmlEmail.setHtmlMsg(html);
                email = htmlEmail;
            } else if (haveAttachments) {
                email = new MultiPartEmail();
            } else {
                email = new SimpleEmail();
                email.setMsg(body);
            }
            if (haveAttachments) {
                MultiPartEmail multipartMail = (MultiPartEmail) email;
                for (FileHandle fileHandle : attachments) {
                    attach(fileHandle, multipartMail);
                }
                for (Pair<String, DataSource> additionalAttachment : additionalAttachments) {
                    ((MultiPartEmail) email).attach(additionalAttachment.getRight(), additionalAttachment.getLeft(), null);
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
        DataSource ds = new FileResourceDataSource(fileHandle);
        email.attach(ds, fileHandle.getName(), null);
    }

    protected List<FileHandle> retrieveAttachments() {
        List<FileHandle> res = new ArrayList<>();
        if (template != null) {
            for (Resource child : template.getChildren()) {
                if (ResourceUtil.isFile(child)) {
                    FileHandle fileHandle = new FileHandle(child);
                    if (fileHandle.isValid()) {
                        res.add(fileHandle);
                    } else {
                        LOG.error("Invalid file: {}", child.getPath());
                    }
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

    /**
     * Add an attachment that's not contained in the template.
     *
     * @param name the name of the attachment
     * @param data a {@link DataSource} with the data, possibly a {@link FileResourceDataSource}.
     */
    public EmailBuilder addAttachment(String name, DataSource data) {
        additionalAttachments.add(Pair.of(name, data));
        return this;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder
                .append("template", template)
                .append("overridingProperties", mapToString(overridingProperties))
                .append("placeholders", mapToString(placeholders));
        if (isNotEmpty(additionalAttachments)) {
            builder.append("addAttachments",
                    additionalAttachments.stream().map(Pair::getLeft).collect(Collectors.joining(",")));
        }
        return builder.toString();
    }

    protected static String mapToString(Map<String, Object> map) {
        if (map == null) return "null";
        return "{" + map.entrySet().stream()
                .map((entry) -> {
                    if (entry.getValue().getClass().isArray()) {
                        return entry.getKey() + "=" + StringUtils.abbreviateMiddle(
                                String.valueOf(Arrays.asList((Object[]) entry.getValue())), "...", 120);
                    } else {
                        return entry.getKey() + "=" + StringUtils.abbreviate(String.valueOf(entry.getValue()), 120);
                    }
                })
                .map(Objects::toString)
                .collect(Collectors.joining(", ")) + "}";
    }

    /**
     * Some data we want to have in our logs to be able to identify an email. We try to exclude private data.
     */
    public String describeForLogging() {
        StringBuilder buf = new StringBuilder("{");
        String from = StringUtils.defaultIfBlank(combinedProperties.get(PROP_FROM, String.class), "");
        buf.append(" from=").append(from.trim().hashCode());
        String[] tos = combinedProperties.get(PROP_TO, String[].class);
        if (tos != null) {
            int num = 0;
            for (String to : tos) {
                if (++num > 3) {
                    buf.append(" ...(").append(tos.length).append(")");
                    break;
                }
                buf.append(" to=").append(
                        StringUtils.defaultIfBlank(combinedProperties.get(PROP_FROM, String.class), "")
                                .trim().hashCode()
                );
            }
        }
        if (template != null) {
            buf.append(" tmpl=").append(template.getPath());
        }
        buf.append(" subj=").append(combinedProperties.get(PROP_SUBJECT, String.class));
        buf.append(" }");
        return buf.toString();
    }

}
