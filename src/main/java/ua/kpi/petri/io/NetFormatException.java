package ua.kpi.petri.io;

public class NetFormatException extends RuntimeException {

    public NetFormatException(String message) {
        super(message);
    }

    public NetFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
