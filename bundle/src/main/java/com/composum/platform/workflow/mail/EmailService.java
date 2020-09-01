package com.composum.platform.workflow.mail;

import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
     * Sends an email asynchronously. If you need it done synchronously, just use {@link Future#get(long, TimeUnit)}
     * on the result. Caution: if the server crashes before it's sent or if there is a connection to the mail relay,
     * this loses the email.
     *
     * @param email        the content of the email
     * @param serverConfig the resource containing the configuration of the email server
     * @return a future with the message-ID
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
