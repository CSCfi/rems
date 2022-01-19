package rems;

/**
 * Thrown when a request is invalid, because of a "too large" request.
 */
public class PayloadTooLargeException extends Exception {

    public PayloadTooLargeException() {
    }

    public PayloadTooLargeException(String message) {
        super(message);
    }

    public PayloadTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PayloadTooLargeException(Throwable cause) {
        super(cause);
    }
}
