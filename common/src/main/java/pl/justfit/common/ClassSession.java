package pl.justfit.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Zajęcia grupowe: tytuł, trener, sala, termin i limit miejsc.
 * Termin jest przesyłany jako tekst ISO (RRRR-MM-DDTGG:MM).
 * Pola trainerName i booked wypełnia serwer przy odczycie z bazy.
 */
public class ClassSession {

    private long id;
    private String title;
    private long trainerId;
    private String trainerName;
    private String room;
    private String startTime;
    private int capacity;
    private int booked;

    public ClassSession() {
    }

    public ClassSession(long id, String title, long trainerId, String trainerName,
                        String room, String startTime, int capacity, int booked) {
        this.id = id;
        this.title = title;
        this.trainerId = trainerId;
        this.trainerName = trainerName;
        this.room = room;
        this.startTime = startTime;
        this.capacity = capacity;
        this.booked = booked;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTrainerId() {
        return trainerId;
    }

    public void setTrainerId(long trainerId) {
        this.trainerId = trainerId;
    }

    public String getTrainerName() {
        return trainerName;
    }

    public void setTrainerName(String trainerName) {
        this.trainerName = trainerName;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBooked() {
        return booked;
    }

    public void setBooked(int booked) {
        this.booked = booked;
    }

    /** Zwraca termin jako obiekt daty. Rzuca wyjątek dla niepoprawnego formatu. */
    public LocalDateTime startAsDateTime() throws DateTimeParseException {
        return LocalDateTime.parse(startTime);
    }

    public int getFreeSpots() {
        return capacity - booked;
    }

    public boolean isFull() {
        return booked >= capacity;
    }
}
