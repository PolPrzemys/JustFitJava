package pl.justfit.server.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Member;
import pl.justfit.common.Trainer;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassesDaoTest {

    private MemberDao members;
    private TrainerDao trainers;
    private SessionDao sessions;
    private BookingDao bookings;
    private Trainer trainer;

    @BeforeEach
    void setUp() throws Exception {
        Database database = Database.inMemory("classes-" + UUID.randomUUID());
        members = new MemberDao(database);
        trainers = new TrainerDao(database);
        sessions = new SessionDao(database);
        bookings = new BookingDao(database);
        trainer = trainers.add(new Trainer(0, "Anna Trener"));
    }

    private ClassSession newSession(int capacity, LocalDateTime start) throws Exception {
        return sessions.add(new ClassSession(0, "Joga", trainer.getId(), null, "Sala A", start.toString(), capacity, 0));
    }

    private LocalDateTime inTwoDays() {
        return LocalDateTime.now().plusDays(2).withNano(0);
    }

    private Member newMember(String number, LocalDate expiration) throws Exception {
        return members.add(new Member(0, "Członek " + number, number, expiration == null ? null : expiration.toString()));
    }

    private Member activeMember(String number) throws Exception {
        return newMember(number, LocalDate.now().plusYears(1));
    }

    private String refusalOf(long sessionId, long memberId) {
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> bookings.book(sessionId, memberId));
        return e.getMessage();
    }

    // ---------- trenerzy ----------

    @Test
    void trainersAreAddedAndListedByName() throws Exception {
        trainers.add(new Trainer(0, "Aaron Pierwszy"));
        List<Trainer> all = trainers.list();
        assertEquals(2, all.size());
        assertEquals("Aaron Pierwszy", all.get(0).getName());
        assertNotEquals(0, all.get(0).getId());
    }

    @Test
    void trainerWithSessionsCannotBeDeleted() throws Exception {
        newSession(10, inTwoDays());
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> trainers.delete(trainer.getId()));
        assertTrue(e.getMessage().contains("zaplanowane zajęcia"));
        assertEquals(1, trainers.list().size());
    }

    @Test
    void trainerWithoutSessionsIsDeletedAndMissingOneIsReported() throws Exception {
        assertTrue(trainers.delete(trainer.getId()));
        assertTrue(trainers.list().isEmpty());
        assertFalse(trainers.delete(trainer.getId()));
    }

    // ---------- zajęcia ----------

    @Test
    void sessionWithUnknownTrainerIsRefused() {
        ClassSession session = new ClassSession(0, "Joga", 9999, null, "Sala A", inTwoDays().toString(), 10, 0);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> sessions.add(session));
        assertEquals("Nie znaleziono trenera", e.getMessage());
    }

    @Test
    void sessionListShowsTrainerNameAndBookedCount() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        assertEquals("Anna Trener", session.getTrainerName());
        bookings.book(session.getId(), activeMember("A-1").getId());
        bookings.book(session.getId(), activeMember("A-2").getId());

        ClassSession listed = sessions.list().get(0);
        assertEquals("Anna Trener", listed.getTrainerName());
        assertEquals(2, listed.getBooked());
        assertEquals(8, listed.getFreeSpots());
        assertFalse(listed.isFull());
        assertEquals(session.startAsDateTime(), listed.startAsDateTime());
    }

    @Test
    void sessionsAreListedInChronologicalOrder() throws Exception {
        ClassSession later = newSession(10, inTwoDays().plusDays(3));
        ClassSession sooner = newSession(10, inTwoDays());
        List<ClassSession> all = sessions.list();
        assertEquals(sooner.getId(), all.get(0).getId());
        assertEquals(later.getId(), all.get(1).getId());
    }

    // ---------- zapisy: reguły ----------

    @Test
    void bookingSucceedsAndIsListedWithMemberData() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        Member member = activeMember("A-1");

        Booking booking = bookings.book(session.getId(), member.getId());
        assertNotEquals(0, booking.getId());
        assertEquals("Członek A-1", booking.getMemberName());

        List<Booking> list = bookings.listForSession(session.getId());
        assertEquals(1, list.size());
        assertEquals("A-1", list.get(0).getMembershipNumber());
    }

    @Test
    void sameMemberCannotBookTwice() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        Member member = activeMember("A-1");
        bookings.book(session.getId(), member.getId());
        assertEquals("Członek jest już zapisany na te zajęcia", refusalOf(session.getId(), member.getId()));
    }

    @Test
    void fullSessionRefusesFurtherBookings() throws Exception {
        ClassSession session = newSession(1, inTwoDays());
        bookings.book(session.getId(), activeMember("A-1").getId());
        assertEquals("Brak wolnych miejsc", refusalOf(session.getId(), activeMember("A-2").getId()));
        assertTrue(sessions.list().get(0).isFull());
    }

    @Test
    void pastSessionCannotBeBooked() throws Exception {
        ClassSession past = newSession(10, LocalDateTime.now().minusHours(2).withNano(0));
        assertEquals("Te zajęcia już się odbyły", refusalOf(past.getId(), activeMember("A-1").getId()));
    }

    @Test
    void membershipMustBeValidOnTheDayOfTheClass() throws Exception {
        LocalDateTime start = inTwoDays();
        ClassSession session = newSession(10, start);

        Member expiredBefore = newMember("E-1", start.toLocalDate().minusDays(1));
        Member expiresThatDay = newMember("E-2", start.toLocalDate());
        Member noMembership = newMember("E-3", null);

        String message = "Karnet członka nie jest ważny w dniu zajęć";
        assertEquals(message, refusalOf(session.getId(), expiredBefore.getId()));
        assertEquals(message, refusalOf(session.getId(), noMembership.getId()));
        assertEquals(expiresThatDay.getId(), bookings.book(session.getId(), expiresThatDay.getId()).getMemberId());
    }

    @Test
    void unknownSessionOrMemberIsRefused() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        Member member = activeMember("A-1");
        assertEquals("Nie znaleziono zajęć", refusalOf(9999, member.getId()));
        assertEquals("Nie znaleziono członka", refusalOf(session.getId(), 9999));
    }

    // ---------- anulowanie i kaskady ----------

    @Test
    void cancelFreesTheSpotAndMissingBookingIsReported() throws Exception {
        ClassSession session = newSession(1, inTwoDays());
        Member first = activeMember("A-1");
        Booking booking = bookings.book(session.getId(), first.getId());

        assertTrue(bookings.cancel(booking.getId()));
        assertFalse(bookings.cancel(booking.getId()));
        // miejsce znowu wolne, więc zapis innej osoby przechodzi
        bookings.book(session.getId(), activeMember("A-2").getId());
    }

    @Test
    void deletingSessionRemovesItsBookings() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        bookings.book(session.getId(), activeMember("A-1").getId());

        assertTrue(sessions.delete(session.getId()));
        assertTrue(bookings.listForSession(session.getId()).isEmpty());
        assertFalse(sessions.delete(session.getId()));
    }

    @Test
    void deletingMemberRemovesTheirBookings() throws Exception {
        ClassSession session = newSession(10, inTwoDays());
        Member member = activeMember("A-1");
        bookings.book(session.getId(), member.getId());

        assertTrue(members.delete(member.getId()));
        assertTrue(bookings.listForSession(session.getId()).isEmpty());
        assertEquals(0, sessions.list().get(0).getBooked());
    }

    // ---------- współbieżność ----------

    @Test
    void manyThreadsRacingForLastSpotsNeverOverbook() throws Exception {
        int capacity = 3;
        int contenders = 12;
        ClassSession session = newSession(capacity, inTwoDays());
        List<Member> candidates = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            candidates.add(activeMember("R-" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Member member : candidates) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    bookings.book(session.getId(), member.getId());
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        executor.shutdown();

        assertEquals(capacity, succeeded, "dokładnie tyle zapisów, ile jest miejsc");
        assertEquals(capacity, bookings.listForSession(session.getId()).size());
    }
}
