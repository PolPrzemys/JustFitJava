package pl.justfit.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.justfit.common.Actions;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.common.Trainer;
import pl.justfit.server.db.Database;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testy akcji zajęć przez RequestHandler, czyli tak, jak widzi je klient (bez gniazd). */
class ClassHandlerTest {

    private RequestHandler handler;

    @BeforeEach
    void setUp() throws SQLException {
        handler = new RequestHandler(Database.inMemory("class-handler-" + UUID.randomUUID()));
    }

    private void login() {
        assertTrue(handler.handle(Request.of(Actions.LOGIN)
                .with("username", "admin").with("password", "admin123")).isOk());
    }

    private String inTwoDays() {
        return LocalDateTime.now().plusDays(2).withNano(0).toString();
    }

    private Trainer addTrainer(String name) {
        Response response = handler.handle(Request.of(Actions.ADD_TRAINER).with("trainer", new Trainer(0, name)));
        assertTrue(response.isOk(), response.getError());
        return response.data(Trainer.class);
    }

    private Response addSession(String title, long trainerId, String room, int capacity, String start) {
        ClassSession session = new ClassSession(0, title, trainerId, null, room, start, capacity, 0);
        return handler.handle(Request.of(Actions.ADD_SESSION).with("session", session));
    }

    private Member addMember(String number) {
        Member member = new Member(0, "Członek " + number, number, LocalDate.now().plusYears(1).toString());
        Response response = handler.handle(Request.of(Actions.ADD_MEMBER).with("member", member));
        assertTrue(response.isOk(), response.getError());
        return response.data(Member.class);
    }

    private Response book(long sessionId, long memberId) {
        return handler.handle(Request.of(Actions.BOOK).with("sessionId", sessionId).with("memberId", memberId));
    }

    @Test
    void classActionsRequireLogin() {
        for (String action : new String[]{Actions.LIST_TRAINERS, Actions.LIST_SESSIONS, Actions.LIST_BOOKINGS,
                Actions.BOOK, Actions.ADD_SESSION}) {
            Response response = handler.handle(Request.of(action));
            assertFalse(response.isOk(), action);
            assertEquals("Wymagane logowanie", response.getError(), action);
        }
    }

    @Test
    void fullFlowAddBookListAndCancel() {
        login();
        Trainer trainer = addTrainer("Anna Trener");
        Response created = addSession("Joga", trainer.getId(), "Sala A", 5, inTwoDays());
        assertTrue(created.isOk(), created.getError());
        ClassSession session = created.data(ClassSession.class);
        assertEquals("Anna Trener", session.getTrainerName());

        Member member = addMember("A-1");
        Response booked = book(session.getId(), member.getId());
        assertTrue(booked.isOk(), booked.getError());
        Booking booking = booked.data(Booking.class);
        assertNotNull(booking);

        Response listed = handler.handle(Request.of(Actions.LIST_BOOKINGS).with("sessionId", session.getId()));
        assertEquals(1, listed.data(Booking[].class).length);

        ClassSession afterBooking = handler.handle(Request.of(Actions.LIST_SESSIONS)).data(ClassSession[].class)[0];
        assertEquals(1, afterBooking.getBooked());

        assertTrue(handler.handle(Request.of(Actions.CANCEL_BOOKING).with("bookingId", booking.getId())).isOk());
        ClassSession afterCancel = handler.handle(Request.of(Actions.LIST_SESSIONS)).data(ClassSession[].class)[0];
        assertEquals(0, afterCancel.getBooked());
    }

    @Test
    void sessionValidationRejectsBadData() {
        login();
        Trainer trainer = addTrainer("Anna Trener");
        long id = trainer.getId();

        assertEquals("Nazwa zajęć jest wymagana", addSession(" ", id, "Sala A", 5, inTwoDays()).getError());
        assertEquals("Sala jest wymagana", addSession("Joga", id, "", 5, inTwoDays()).getError());
        assertTrue(addSession("Joga", id, "Sala A", 0, inTwoDays()).getError().contains("Limit miejsc"));
        assertTrue(addSession("Joga", id, "Sala A", 501, inTwoDays()).getError().contains("Limit miejsc"));
        assertEquals("Termin zajęć jest wymagany", addSession("Joga", id, "Sala A", 5, null).getError());
        assertTrue(addSession("Joga", id, "Sala A", 5, "jutro").getError().contains("RRRR-MM-DDTGG:MM"));
        assertEquals("Termin zajęć nie może być w przeszłości",
                addSession("Joga", id, "Sala A", 5, LocalDateTime.now().minusDays(1).toString()).getError());
        assertEquals("Nie znaleziono trenera", addSession("Joga", 9999, "Sala A", 5, inTwoDays()).getError());
    }

    @Test
    void trainerNameIsRequired() {
        login();
        assertFalse(handler.handle(Request.of(Actions.ADD_TRAINER).with("trainer", new Trainer(0, " "))).isOk());
        assertFalse(handler.handle(Request.of(Actions.ADD_TRAINER)).isOk());
    }

    @Test
    void businessRuleMessagesReachTheClient() {
        login();
        Trainer trainer = addTrainer("Anna Trener");
        ClassSession session = addSession("Joga", trainer.getId(), "Sala A", 1, inTwoDays()).data(ClassSession.class);
        Member first = addMember("A-1");
        Member second = addMember("A-2");

        assertTrue(book(session.getId(), first.getId()).isOk());
        assertEquals("Członek jest już zapisany na te zajęcia", book(session.getId(), first.getId()).getError());
        assertEquals("Brak wolnych miejsc", book(session.getId(), second.getId()).getError());
        assertEquals("Wybierz zajęcia i członka",
                handler.handle(Request.of(Actions.BOOK).with("sessionId", session.getId())).getError());
    }

    @Test
    void trainerWithSessionsCannotBeDeletedButSessionAndThenTrainerCan() {
        login();
        Trainer trainer = addTrainer("Anna Trener");
        ClassSession session = addSession("Joga", trainer.getId(), "Sala A", 5, inTwoDays()).data(ClassSession.class);

        Response refused = handler.handle(Request.of(Actions.DELETE_TRAINER).with("id", trainer.getId()));
        assertFalse(refused.isOk());
        assertTrue(refused.getError().contains("zaplanowane zajęcia"));

        assertTrue(handler.handle(Request.of(Actions.DELETE_SESSION).with("id", session.getId())).isOk());
        assertTrue(handler.handle(Request.of(Actions.DELETE_TRAINER).with("id", trainer.getId())).isOk());
    }

    @Test
    void missingIdsAreReported() {
        login();
        assertFalse(handler.handle(Request.of(Actions.DELETE_TRAINER)).isOk());
        assertFalse(handler.handle(Request.of(Actions.DELETE_SESSION)).isOk());
        assertFalse(handler.handle(Request.of(Actions.CANCEL_BOOKING)).isOk());
        assertFalse(handler.handle(Request.of(Actions.LIST_BOOKINGS)).isOk());
        assertFalse(handler.handle(Request.of(Actions.DELETE_SESSION).with("id", 9999L)).isOk());
        assertFalse(handler.handle(Request.of(Actions.CANCEL_BOOKING).with("bookingId", 9999L)).isOk());
    }
}
