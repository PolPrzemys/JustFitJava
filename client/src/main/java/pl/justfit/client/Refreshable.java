package pl.justfit.client;

/** Zakładka, która potrafi ponownie pobrać swoje dane z serwera (wywoływane przy wejściu na zakładkę). */
public interface Refreshable {

    void refresh();
}
