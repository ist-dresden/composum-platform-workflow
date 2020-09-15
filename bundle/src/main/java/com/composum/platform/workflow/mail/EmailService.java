package com.composum.platform.workflow.mail;

import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Future;

/**
 * Allows sending emails, adress verification etc.
 */
public interface EmailService {

    /**
     * Returns true if the email service is enabled.
     */
    boolean isEnabled();

    /**
     * Checks whether this is a valid email address to send to.
     */
    boolean isValid(@Nullable String emailAdress);

    /**
     * Sends an email asynchronously. If you need it done synchronously, please use {@link #sendMailImmediately(EmailBuilder, Resource)}.
     *
     * @param email        the content of the email
     * @param serverConfig the resource containing the configuration of the email server
     * @return a future with the message-ID. Caution: this is not 100% reliable - the email might be processed on a different host after a retry.
     */
    @Nonnull
    Future<String> sendMail(@Nonnull EmailBuilder email, @Nonnull Resource serverConfig) throws EmailSendingException;

    /**
     * Sends an email immediately, returning only when it's sent successfully, or an exception occurred.
     * No retries are done.
     *
     * @param email        the content of the email
     * @param serverConfig the resource containing the configuration of the email server
     * @return the message-ID
     */
    @Nonnull
    String sendMailImmediately(@Nonnull EmailBuilder email, @Nonnull Resource serverConfig) throws EmailSendingException;

}
