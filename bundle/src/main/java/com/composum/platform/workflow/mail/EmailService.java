package com.composum.platform.workflow.mail;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Future;

/**
 * Allows sending emails, adress verification etc.
 */
public interface EmailService {

    /**
     * Checks whether this is a valid email address to send to.
     */
    boolean isValid(@Nullable String emailAdress);

    // FIXME(hps,21.08.20) temporary
    @Nullable
    String sendMailImmediately(@Nonnull Email email, @Nonnull Resource serverConfig) throws EmailException;

    // FIXME(hps,21.08.20) temporary
    @Nullable
    Future<String> sendMail(@Nonnull Email email, @Nonnull Resource serverConfig) throws EmailException;

    /** Returns true if the email service is enabled. */
    boolean isEnabled();

}
