package pl.justfit.common;

import com.google.gson.JsonElement;

import java.lang.reflect.Type;

/** Odpowiedź serwera: flaga powodzenia, komunikat błędu albo dane. */
public class Response {

    private boolean ok;
    private String error;
    private JsonElement data;

    public Response() {
    }

    public static Response ok() {
        Response response = new Response();
        response.ok = true;
        return response;
    }

    public static Response ok(Object data) {
        Response response = ok();
        response.data = Json.gson().toJsonTree(data);
        return response;
    }

    public static Response error(String message) {
        Response response = new Response();
        response.ok = false;
        response.error = message;
        return response;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public <T> T data(Class<T> type) {
        return data == null ? null : Json.gson().fromJson(data, type);
    }

    public <T> T data(Type type) {
        return data == null ? null : Json.gson().fromJson(data, type);
    }
}
