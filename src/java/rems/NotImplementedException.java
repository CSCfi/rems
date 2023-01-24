package rems;

/**
 * Thrown when a request is invalid, because request method or target has not been implemented.
 */
public class NotImplementedException extends Exception {

    public NotImplementedException() {
    }

    public NotImplementedException(String message) {
        super(message);
    }

    public NotImplementedException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotImplementedException(Throwable cause) {
        super(cause);
    }
}
