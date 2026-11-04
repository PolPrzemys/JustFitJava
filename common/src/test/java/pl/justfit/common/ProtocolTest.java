package pl.justfit.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {

    @Test
    void requestSurvivesJsonRoundTrip() {
        Member member = new Member(0, "Jan Kowalski", "K-001", "2030-01-31");
        Request original = Request.of(Actions.ADD_MEMBER).with("member", member);

        String line = Json.toJson(original);
        Request parsed = Json.fromJson(line, Request.class);

        assertEquals(Actions.ADD_MEMBER, parsed.getAction());
        Member restored = parsed.get("member", Member.class);
        assertEquals("Jan Kowalski", restored.getName());
        assertEquals("K-001", restored.getMembershipNumber());
        assertEquals("2030-01-31", restored.getExpirationDate());
    }

    @Test
    void requestJsonIsSingleLineEvenWithNewlinesInValues() {
        Request request = Request.of(Actions.LOGIN).with("username", "a\nb");
        assertFalse(Json.toJson(request).contains("\n"));
    }

    @Test
    void missingKeyReturnsNull() {
        Request request = Request.of(Actions.PING);
        assertNull(request.getString("nope"));
    }

    @Test
    void responseCarriesErrorAndData() {
        Response error = Json.fromJson(Json.toJson(Response.error("zle")), Response.class);
        assertFalse(error.isOk());
        assertEquals("zle", error.getError());

        Response ok = Json.fromJson(Json.toJson(Response.ok(new Member(5, "Ala", "A-1", null))), Response.class);
        assertTrue(ok.isOk());
        assertEquals(5, ok.data(Member.class).getId());
    }

    @Test
    void memberStatusReflectsExpirationDate() {
        assertEquals("Brak karnetu", new Member(1, "A", "1", null).getStatus());
        assertEquals("Brak karnetu", new Member(1, "A", "1", "").getStatus());
        assertEquals("Aktywny", new Member(1, "A", "1", LocalDate.now().plusDays(1).toString()).getStatus());
        assertEquals("Wygasł", new Member(1, "A", "1", LocalDate.now().minusDays(1).toString()).getStatus());
        assertEquals("Błędna data", new Member(1, "A", "1", "wczoraj").getStatus());
    }

    @Test
    void invalidDateThrowsFromExpirationAsDate() {
        Member member = new Member(1, "A", "1", "31-01-2030");
        assertThrows(java.time.format.DateTimeParseException.class, member::expirationAsDate);
    }
}
