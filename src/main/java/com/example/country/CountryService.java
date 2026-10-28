package com.example.country;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class CountryService {
    private final Map<Long, Country> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Country create(Country country) {
        long id = sequence.incrementAndGet();
        Country saved = withId(id, country);
        store.put(id, saved);
        return saved;
    }

    public List<Country> findAll() {
        return store.values().stream().sorted(Comparator.comparing(Country::id)).toList();
    }

    public Country findById(long id) {
        Country country = store.get(id);
        if (country == null) {
            throw new CountryNotFoundException(id);
        }
        return country;
    }

    public Country update(long id, Country country) {
        Country updated = store.computeIfPresent(id, (key, old) -> withId(id, country));
        if (updated == null) {
            throw new CountryNotFoundException(id);
        }
        return updated;
    }

    public void delete(long id) {
        if (store.remove(id) == null) {
            throw new CountryNotFoundException(id);
        }
    }

    private static Country withId(long id, Country c) {
        return new Country(id, c.name(), c.capital(), c.population());
    }
}
