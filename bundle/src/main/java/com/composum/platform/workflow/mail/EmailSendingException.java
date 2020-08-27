package com.composum.platform.workflow.mail;

import org.apache.commons.mail.EmailException;

/**
 * Something went wrong when trying to send an email.<p>
 * This is our own wrapper to, say, an commons-email EmailExeption to hide the actual library that's used.
 */
public class EmailSendingException extends Exception {

    public EmailSendingException(String message) {
        super(message);
    }

    public EmailSendingException(String message, Exception exception) {
        super(message, exception);
    }

    public EmailSendingException(Exception exception) {
        super(exception);
    }
}
