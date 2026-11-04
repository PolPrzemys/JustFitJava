package pl.justfit.server.db;

import pl.justfit.common.Member;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Operacje na tabeli członków. Daty są walidowane wyżej, w RequestHandler. */
public class MemberDao {

    private static final String UNIQUE_VIOLATION = "23505";

    private final Database database;

    public MemberDao(Database database) {
        this.database = database;
    }

    public Member add(Member member) throws SQLException, DuplicateMembershipNumberException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO members (name, membership_number, expiration_date) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            fill(ps, member);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                member.setId(keys.getLong(1));
            }
            return member;
        } catch (SQLException e) {
            throw translate(e, member);
        }
    }

    public List<Member> list() throws SQLException {
        List<Member> result = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, membership_number, expiration_date FROM members ORDER BY name, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date date = rs.getDate(4);
                result.add(new Member(rs.getLong(1), rs.getString(2), rs.getString(3),
                        date == null ? null : date.toLocalDate().toString()));
            }
        }
        return result;
    }

    /** @return false, gdy członek o podanym id nie istnieje */
    public boolean update(Member member) throws SQLException, DuplicateMembershipNumberException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE members SET name = ?, membership_number = ?, expiration_date = ? WHERE id = ?")) {
            fill(ps, member);
            ps.setLong(4, member.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate(e, member);
        }
    }

    /** @return false, gdy członek o podanym id nie istnieje */
    public boolean delete(long id) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private void fill(PreparedStatement ps, Member member) throws SQLException {
        ps.setString(1, member.getName());
        ps.setString(2, member.getMembershipNumber());
        if (member.getExpirationDate() == null || member.getExpirationDate().isBlank()) {
            ps.setNull(3, Types.DATE);
        } else {
            ps.setDate(3, Date.valueOf(member.getExpirationDate()));
        }
    }

    private SQLException translate(SQLException e, Member member) throws DuplicateMembershipNumberException {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            throw new DuplicateMembershipNumberException(member.getMembershipNumber(), e);
        }
        return e;
    }
}
