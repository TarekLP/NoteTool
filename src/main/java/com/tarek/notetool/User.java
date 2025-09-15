package com.tarek.notetool;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a user in the system.
 * This is an immutable record, ensuring that user data cannot be changed after creation.
 * Equality is based solely on the user's unique ID.
 *
 * @param id   The unique identifier for the user.
 * @param name The display name of the user.
 */
public record User(UUID id, String name) {

    /**
     * Convenience constructor to create a new user with a random ID.
     * @param name The name of the user.
     */
    public User(String name) {
        this(UUID.randomUUID(), name);
    }

    @Override
    public String toString() {
        return name; // Important for ComboBox and ListView display
    }

    // The default record implementation of equals/hashCode considers all fields (id and name).
    // We override it here to maintain the original behavior of using only the ID for identity,
    // which is a common pattern for entities. This ensures that operations like list lookups work correctly.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}