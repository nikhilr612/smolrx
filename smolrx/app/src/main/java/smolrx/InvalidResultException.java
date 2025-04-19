package smolrx;

public class InvalidResultException extends RuntimeException {
    public InvalidResultException(String message) {
        super(message);
    }
}