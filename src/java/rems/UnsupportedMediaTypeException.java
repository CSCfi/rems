package rems;

/**
 * Thrown when a request is invalid, because of an unsupported type.
 */
public class UnsupportedMediaTypeException extends Exception {

    public UnsupportedMediaTypeException() {
    }

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }

    public UnsupportedMediaTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedMediaTypeException(Throwable cause) {
        super(cause);
    }
}
