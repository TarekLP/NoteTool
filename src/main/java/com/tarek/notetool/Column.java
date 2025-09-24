package com.tarek.notetool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Column {
    private final UUID id;
    private String name;
    private final List<UUID> noteIds;

    public Column(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.noteIds = new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<UUID> getNoteIds() {
        return noteIds;
    }

    @Override
    public String toString() {
        return name;
    }
}