package rems;

/**
 * Thrown when e.g. an sql transaction conflict occurs.
 */
public class TryAgainException extends Exception {

    public TryAgainException() {
    }

    public TryAgainException(String message) {
        super(message);
    }

    public TryAgainException(String message, Throwable cause) {
        super(message, cause);
    }

    public TryAgainException(Throwable cause) {
        super(cause);
    }
}
