package pl.justfit.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Żądanie klienta: nazwa akcji i dowolne dane w obiekcie JSON. */
public class Request {

    private String action;
    private JsonObject data = new JsonObject();

    public Request() {
    }

    public static Request of(String action) {
        Request request = new Request();
        request.action = action;
        return request;
    }

    public Request with(String key, Object value) {
        data.add(key, Json.gson().toJsonTree(value));
        return this;
    }

    public String getAction() {
        return action;
    }

    public boolean has(String key) {
        return data != null && data.has(key) && !data.get(key).isJsonNull();
    }

    public <T> T get(String key, Class<T> type) {
        if (!has(key)) {
            return null;
        }
        JsonElement element = data.get(key);
        return Json.gson().fromJson(element, type);
    }

    public String getString(String key) {
        return get(key, String.class);
    }
}
