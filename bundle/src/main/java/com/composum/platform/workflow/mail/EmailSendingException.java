package com.composum.platform.workflow.mail;

import java.net.SocketException;
import java.net.SocketTimeoutException;

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

    /**
     * Returns true if it makes sense to repeat the sending since this might be a temporary error.
     * Currently we check whether it is an {@link java.net.SocketTimeoutException} wrapped somewhere in the
     * exception, which could indicate a temporary network failure.
     */
    public boolean isRetryable() {
        Throwable rootCause = getRootCause();
        return rootCause instanceof SocketTimeoutException || rootCause instanceof SocketException;
    }

    /**
     * Returns the innermost exception, which usually startet the problem.[
     */
    public Throwable getRootCause() {
        Throwable rootCause = this;
        Throwable cause = getCause();
        while (cause != null && cause != cause.getCause()) {
            rootCause = cause;
            cause = cause.getCause();
        }
        return rootCause;
    }

}
