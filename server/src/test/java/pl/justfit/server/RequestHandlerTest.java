package pl.justfit.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.justfit.common.Actions;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;
import pl.justfit.server.db.Database;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestHandlerTest {

    private Database database;
    private RequestHandler handler;

    @BeforeEach
    void setUp() throws SQLException {
        database = Database.inMemory("handler-" + UUID.randomUUID());
        handler = new RequestHandler(database);
    }

    private Response login() {
        return handler.handle(Request.of(Actions.LOGIN).with("username", "admin").with("password", "admin123"));
    }

    private Response add(String name, String number, String date) {
        return handler.handle(Request.of(Actions.ADD_MEMBER).with("member", new Member(0, name, number, date)));
    }

    @Test
    void pingWorksWithoutLogin() {
        assertTrue(handler.handle(Request.of(Actions.PING)).isOk());
    }

    @Test
    void actionsRequireLogin() {
        Response response = handler.handle(Request.of(Actions.LIST_MEMBERS));
        assertFalse(response.isOk());
        assertEquals("Wymagane logowanie", response.getError());
    }

    @Test
    void loginWithWrongPasswordFails() {
        Response response = handler.handle(Request.of(Actions.LOGIN).with("username", "admin").with("password", "zle"));
        assertFalse(response.isOk());
        assertFalse(handler.isAuthenticated());
    }

    @Test
    void loginWithoutCredentialsFails() {
        assertFalse(handler.handle(Request.of(Actions.LOGIN)).isOk());
    }

    @Test
    void loggedInUserCanAddListUpdateAndDelete() {
        assertTrue(login().isOk());

        Response added = add("Jan Kowalski", "K-001", "2030-01-01");
        assertTrue(added.isOk());
        Member saved = added.data(Member.class);
        assertNotNull(saved);

        saved.setName("Jan Nowak");
        assertTrue(handler.handle(Request.of(Actions.UPDATE_MEMBER).with("member", saved)).isOk());

        Response list = handler.handle(Request.of(Actions.LIST_MEMBERS));
        assertTrue(list.isOk());
        assertTrue(list.data(Member[].class)[0].getName().equals("Jan Nowak"));

        assertTrue(handler.handle(Request.of(Actions.DELETE_MEMBER).with("id", saved.getId())).isOk());
        assertEquals(0, handler.handle(Request.of(Actions.LIST_MEMBERS)).data(Member[].class).length);
    }

    @Test
    void validationRejectsBadMembers() {
        login();
        assertFalse(add("", "K-001", null).isOk());
        assertFalse(add("Jan", " ", null).isOk());
        Response badDate = add("Jan", "K-001", "31-12-2030");
        assertFalse(badDate.isOk());
        assertTrue(badDate.getError().contains("RRRR-MM-DD"));
    }

    @Test
    void duplicateMembershipNumberGivesReadableError() {
        login();
        assertTrue(add("Jan", "K-001", null).isOk());
        Response duplicate = add("Ktoś inny", "K-001", null);
        assertFalse(duplicate.isOk());
        assertEquals("Ten numer karnetu jest już używany", duplicate.getError());
    }

    @Test
    void updateAndDeleteOfMissingMemberReportError() {
        login();
        assertFalse(handler.handle(Request.of(Actions.UPDATE_MEMBER)
                .with("member", new Member(9999, "Nikt", "X-1", null))).isOk());
        assertFalse(handler.handle(Request.of(Actions.DELETE_MEMBER).with("id", 9999L)).isOk());
        assertFalse(handler.handle(Request.of(Actions.DELETE_MEMBER)).isOk());
    }

    @Test
    void unknownActionAndEmptyRequestAreRejected() {
        login();
        assertFalse(handler.handle(Request.of("LATAJ")).isOk());
        assertFalse(handler.handle(null).isOk());
        assertFalse(handler.handle(new Request()).isOk());
    }

    @Test
    void eachHandlerKeepsItsOwnLoginState() {
        login();
        RequestHandler other = new RequestHandler(database);
        assertTrue(handler.isAuthenticated());
        assertFalse(other.isAuthenticated());
    }
}
