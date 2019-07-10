package rems;

/**
 * Thrown when a request is invalid.
 */
public class InvalidRequestException extends Exception {

    public InvalidRequestException() {
    }

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidRequestException(Throwable cause) {
        super(cause);
    }
}
