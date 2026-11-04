package pl.justfit.client;

import com.google.gson.reflect.TypeToken;
import pl.justfit.common.Actions;
import pl.justfit.common.Member;
import pl.justfit.common.Request;
import pl.justfit.common.Response;

import java.io.IOException;
import java.util.List;

/** Typowane wywołania serwera. GUI korzysta z tej klasy i nie zna formatu protokołu. */
public class MemberService implements AutoCloseable {

    private final ServerConnection connection;

    public MemberService(ServerConnection connection) {
        this.connection = connection;
    }

    public void login(String username, String password) throws IOException, ServiceException {
        call(Request.of(Actions.LOGIN).with("username", username).with("password", password));
    }

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
