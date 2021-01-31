package com.composum.platform.workflow.mail;

import javax.annotation.Nonnull;

/**
 * An exception that notifies that the email was still not sent after {@value com.composum.platform.workflow.mail.impl.EmailServiceImpl#MAXAGE_FUTURE} milliseconds expired.
 * This does not mean it'll never be sent, though.
 */
public class EmailStillNotSentException extends Exception {

    public EmailStillNotSentException(@Nonnull String message) {
        super(message);
    }

}
