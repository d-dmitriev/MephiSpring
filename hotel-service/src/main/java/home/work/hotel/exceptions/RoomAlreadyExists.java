package home.work.hotel.exceptions;

public class RoomAlreadyExists extends RuntimeException {
    public RoomAlreadyExists(String message) {
        super(message);
    }
}
