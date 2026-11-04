package pl.justfit.common;

import com.google.gson.Gson;

/** Wspólna instancja Gson. Domyślnie zapisuje JSON w jednej linii, co pasuje do protokołu liniowego. */
public final class Json {

    private static final Gson GSON = new Gson();

    private Json() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
