package pl.justfit.common;

/** Zapis członka na zajęcia. Imię i numer karnetu dołącza serwer, żeby klient nie musiał dopytywać. */
public class Booking {

    private long id;
    private long sessionId;
    private long memberId;
    private String memberName;
    private String membershipNumber;

    public Booking() {
    }

    public Booking(long id, long sessionId, long memberId, String memberName, String membershipNumber) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.membershipNumber = membershipNumber;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMembershipNumber() {
        return membershipNumber;
    }

    public void setMembershipNumber(String membershipNumber) {
        this.membershipNumber = membershipNumber;
    }
}
