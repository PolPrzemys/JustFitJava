package pl.justfit.client;

import com.google.gson.reflect.TypeToken;
import pl.justfit.common.Actions;
import pl.justfit.common.Booking;
import pl.justfit.common.ClassSession;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.common.Trainer;

import java.io.IOException;
import java.util.List;

/** Typowane wywołania serwera. GUI korzysta z tej klasy i nie zna formatu protokołu. */
public class ClubService implements AutoCloseable {

    private final ServerConnection connection;

    public ClubService(ServerConnection connection) {
        this.connection = connection;
    }

    public void login(String username, String password) throws IOException, ServiceException {
        call(Request.of(Actions.LOGIN).with("username", username).with("password", password));
    }

    // ---------- członkowie ----------

    public List<Member> listMembers() throws IOException, ServiceException {
        Response response = call(Request.of(Actions.LIST_MEMBERS));
        List<Member> members = response.data(new TypeToken<List<Member>>() { }.getType());
        return members == null ? List.of() : members;
    }

    public Member addMember(Member member) throws IOException, ServiceException {
        return call(Request.of(Actions.ADD_MEMBER).with("member", member)).data(Member.class);
    }

    public Member updateMember(Member member) throws IOException, ServiceException {
        return call(Request.of(Actions.UPDATE_MEMBER).with("member", member)).data(Member.class);
    }

    public void deleteMember(long id) throws IOException, ServiceException {
        call(Request.of(Actions.DELETE_MEMBER).with("id", id));
    }

    // ---------- trenerzy ----------

    public List<Trainer> listTrainers() throws IOException, ServiceException {
        Response response = call(Request.of(Actions.LIST_TRAINERS));
        List<Trainer> trainers = response.data(new TypeToken<List<Trainer>>() { }.getType());
        return trainers == null ? List.of() : trainers;
    }

    public Trainer addTrainer(Trainer trainer) throws IOException, ServiceException {
        return call(Request.of(Actions.ADD_TRAINER).with("trainer", trainer)).data(Trainer.class);
    }

    public void deleteTrainer(long id) throws IOException, ServiceException {
        call(Request.of(Actions.DELETE_TRAINER).with("id", id));
    }

    // ---------- zajęcia ----------

    public List<ClassSession> listSessions() throws IOException, ServiceException {
        Response response = call(Request.of(Actions.LIST_SESSIONS));
        List<ClassSession> sessions = response.data(new TypeToken<List<ClassSession>>() { }.getType());
        return sessions == null ? List.of() : sessions;
    }

    public ClassSession addSession(ClassSession session) throws IOException, ServiceException {
        return call(Request.of(Actions.ADD_SESSION).with("session", session)).data(ClassSession.class);
    }

    public void deleteSession(long id) throws IOException, ServiceException {
        call(Request.of(Actions.DELETE_SESSION).with("id", id));
    }

    // ---------- zapisy ----------

    public List<Booking> listBookings(long sessionId) throws IOException, ServiceException {
        Response response = call(Request.of(Actions.LIST_BOOKINGS).with("sessionId", sessionId));
        List<Booking> bookings = response.data(new TypeToken<List<Booking>>() { }.getType());
        return bookings == null ? List.of() : bookings;
    }

    public Booking book(long sessionId, long memberId) throws IOException, ServiceException {
        return call(Request.of(Actions.BOOK).with("sessionId", sessionId).with("memberId", memberId))
                .data(Booking.class);
    }

    public void cancelBooking(long bookingId) throws IOException, ServiceException {
        call(Request.of(Actions.CANCEL_BOOKING).with("bookingId", bookingId));
    }

    private Response call(Request request) throws IOException, ServiceException {
        Response response = connection.send(request);
        if (!response.isOk()) {
            throw new ServiceException(response.getError());
        }
        return response;
    }

    @Override
    public void close() {
        connection.close();
    }
}
