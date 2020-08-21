package com.composum.platform.workflow.mail;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Allows sending emails, adress verification etc.
 */
public interface EmailService {

    /**
     * Checks whether this is a valid email address to send to.
     */
    boolean isValid(@Nullable String emailAdress);

    // FIXME(hps,21.08.20) temporary
    void sendMail(@Nonnull Email email) throws EmailException;

}
