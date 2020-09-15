package com.composum.platform.workflow.mail;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

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
     * We treat {@link SocketTimeoutException} and {@link SocketException} as retryable, as they can occur on
     * network failures. We also treat {@link UnknownHostException}s as retryable, since they can also occur on
     * temporary network failures, and might even be correctable if it was caused by a wrong email server configuration.
     */
    public boolean isRetryable() {
        Throwable rootCause = getRootCause();
        return rootCause instanceof SocketTimeoutException || rootCause instanceof SocketException
                || rootCause instanceof UnknownHostException;
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
