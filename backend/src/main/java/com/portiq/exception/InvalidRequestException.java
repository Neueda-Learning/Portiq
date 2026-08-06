package com.portiq.exception;

/**
 * A request that is syntactically fine but asks for something that cannot be honoured - a blank
 * ticker, an out-of-range parameter. Maps to 400 with the message shown to the caller verbatim,
 * so the message must be written for a human to read.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
