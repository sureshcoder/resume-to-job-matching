package in.sureshcoder.resumetojobmatching.jev;

public class JevApiException extends RuntimeException {

    private final int statusCode;

    public JevApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
