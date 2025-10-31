package home.work.exceptions;

public class ExceptionProcessor {
    public static boolean isUniqueConstraintViolation(Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null) return false;
        // H2: "Unique index or primary key violation"
        return msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("violation");
    }
}
