package com.composum.platform.workflow.mail;

import com.composum.platform.models.simple.AbstractLoadedSlingBean;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import javax.annotation.Nonnull;

/**
 * Model that reads the email server configuration data from a resource.
 */
public class EmailServerConfigModel extends AbstractLoadedSlingBean {

    /**
     * Whether the server is enabled.
     */
    public static final String PROP_SERVER_ENABLED = "enabled";
    /**
     * Kind of server: SMTP, SMTPS, STARTTLS.
     */
    public static final String PROP_SERVER_TYPE = "connectionType";
    /**
     * Value {@value #VALUE_SMTP} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_SMTP = "SMTP";
    /**
     * Value {@value #VALUE_SMTPS} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_SMTPS = "SMTPS";
    /**
     * Value {@value #VALUE_STARTTLS} for {@link #PROP_SERVER_TYPE}.
     */
    public static final String VALUE_STARTTLS = "STARTTLS";
    /**
     * SMTP server / relay hostname.
     */
    public static final String PROP_SERVER_HOST = "host";
    /**
     * SMTP server / relay port.
     */
    public static final String PROP_SERVER_PORT = "port";
    /**
     * Optional credential ID for the SMTP server / relay used with the {@link CredentialService#getMailAuthenticator(String, ResourceResolver)}.
     */
    public static final String PROP_CREDENTIALID = "credentialId";

    protected Boolean enabled;
    protected String host;
    protected Integer port;
    protected String connectionType;
    protected String credentialId;

    public EmailServerConfigModel() {
        // empty - initialization is later with initialize
    }

    public EmailServerConfigModel(@Nonnull Resource resource) {
        initialize(new BeanContext.Service(resource.getResourceResolver()), resource);
    }

    @Override
    public void initialize(BeanContext beanContext, @Nonnull Resource resource) {
        super.initialize(beanContext, resource);
        ValueMap vm = resource.getValueMap();
        enabled = vm.get(PROP_SERVER_ENABLED, Boolean.class);
        host = vm.get(PROP_SERVER_HOST, String.class);
        port = vm.get(PROP_SERVER_PORT, Integer.class);
        connectionType = vm.get(PROP_SERVER_TYPE, String.class);
        credentialId = vm.get(PROP_CREDENTIALID, String.class);
    }

    /**
     * Whether this email server configuration is enabled.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * The credentials for contacting the server.
     */
    public String getCredentialId() {
        return credentialId;
    }

    /**
     * One of {@value #VALUE_SMTP}, {@value #VALUE_SMTPS} or {@value #VALUE_STARTTLS}.
     */
    public String getConnectionType() {
        return connectionType;
    }


    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        if (path != null) {
            builder.append("path", path);
        }
        if (Boolean.TRUE.equals(enabled)) {
            builder.append("enabled", enabled);
        }
        if (host != null) {
            builder.append("host", host);
        }
        if (port != null) {
            builder.append("port", port);
        }
        if (connectionType != null) {
            builder.append("connectionType", connectionType);
        }
        if (credentialId != null) {
            builder.append("credentialId", credentialId);
        }
        return builder.toString();
    }
}
