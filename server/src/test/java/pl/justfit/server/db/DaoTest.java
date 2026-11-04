package pl.justfit.server.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.justfit.common.Member;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoTest {

    private Database database;
    private MemberDao members;
    private UserDao users;

    @BeforeEach
    void setUp() throws SQLException {
        database = Database.inMemory("dao-" + UUID.randomUUID());
        members = new MemberDao(database);
        users = new UserDao(database);
    }

    @Test
    void addAssignsIdAndListReturnsMember() throws Exception {
        Member saved = members.add(new Member(0, "Jan Kowalski", "K-001", "2030-05-01"));
        assertNotEquals(0, saved.getId());

        List<Member> all = members.list();
        assertEquals(1, all.size());
        assertEquals("Jan Kowalski", all.get(0).getName());
        assertEquals("2030-05-01", all.get(0).getExpirationDate());
    }

    @Test
    void memberWithoutExpirationDateIsStoredAsNull() throws Exception {
        members.add(new Member(0, "Ala", "A-1", null));
        assertNull(members.list().get(0).getExpirationDate());
    }

    @Test
    void duplicateMembershipNumberIsRejected() throws Exception {
        members.add(new Member(0, "Jan", "K-001", null));
        assertThrows(DuplicateMembershipNumberException.class,
                () -> members.add(new Member(0, "Ktoś inny", "K-001", null)));
        assertEquals(1, members.list().size());
    }

    @Test
    void updateChangesFieldsAndReportsMissingMember() throws Exception {
        Member saved = members.add(new Member(0, "Jan", "K-001", null));
        saved.setName("Jan Nowak");
        saved.setExpirationDate("2031-01-01");
        assertTrue(members.update(saved));

        Member loaded = members.list().get(0);
        assertEquals("Jan Nowak", loaded.getName());
        assertEquals("2031-01-01", loaded.getExpirationDate());

        assertFalse(members.update(new Member(9999, "Nikt", "X-1", null)));
    }

    @Test
    void deleteRemovesMemberAndReportsMissingMember() throws Exception {
        Member saved = members.add(new Member(0, "Jan", "K-001", null));
        assertTrue(members.delete(saved.getId()));
        assertTrue(members.list().isEmpty());
        assertFalse(members.delete(saved.getId()));
    }

    @Test
    void defaultAdminCanAuthenticate() throws Exception {
        assertTrue(users.authenticate("admin", "admin123"));
    }

    @Test
    void wrongPasswordAndUnknownUserAreRejected() throws Exception {
        assertFalse(users.authenticate("admin", "zle"));
        assertFalse(users.authenticate("nikt", "admin123"));
        assertFalse(users.authenticate(null, null));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        users.create("recepcja", "tajne-haslo");

        try (var c = database.connection();
             var ps = c.prepareStatement("SELECT password_hash FROM app_users WHERE username = 'recepcja'");
             var rs = ps.executeQuery()) {
            assertTrue(rs.next());
            String stored = rs.getString(1);
            assertNotEquals("tajne-haslo", stored);
            assertTrue(stored.startsWith("$2"), "oczekiwano hasha BCrypt, jest: " + stored);
        }
        assertTrue(users.authenticate("recepcja", "tajne-haslo"));
        assertFalse(users.authenticate("recepcja", "TAJNE-HASLO"));
    }
}
