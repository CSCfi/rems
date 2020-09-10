package rems;

/**
 * Thrown when there is a serious error in data e.g. missing an organization
 */
public class DataException extends Exception {

    public final Object data;

    public DataException(String message, Object data) {
        super(message);
        this.data = data;
    }
}
