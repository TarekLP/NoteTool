package com.tarek.notetool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a board, similar to Trello, that contains columns of notes.
 * The columns are based on the Note.Status enum.
 */
public class Board {

    private String name;
    private List<User> members;

    // An EnumMap is a highly efficient Map implementation for use with enum keys.
    private final Map<Note.Status, List<Note>> columns;

    /**
     * Constructs a new Board with a given name.
     * Initializes empty columns for each possible note status.
     * @param name The name of the board.
     */
    public Board(String name, List<User> members) {
        this.name = name;
        this.members = new ArrayList<>(members);
        this.columns = new EnumMap<>(Note.Status.class);
        for (Note.Status status : Note.Status.values()) {
            columns.put(status, new ArrayList<>());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<User> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /**
     * Adds a note to the board. The note is placed in the column
     * corresponding to its current status.
     * @param note The note to add.
     */
    public void addNote(Note note) {
        if (note != null) {
            // Ensure the note isn't already on the board in another column
            removeNote(note.getId());
            columns.get(note.getStatus()).add(note);
        }
    }

    /**
     * Finds a note by its ID across all columns.
     * @param noteId The UUID of the note to find.
     * @return An Optional containing the note if found, otherwise an empty Optional.
     */
    public Optional<Note> findNoteById(UUID noteId) {
        for (List<Note> notesInColumn : columns.values()) {
            for (Note note : notesInColumn) {
                if (note.getId().equals(noteId)) {
                    return Optional.of(note);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Moves a note to a different status column.
     * When no index is specified, the note is placed at the end of the target column.
     * @param noteId The ID of the note to move.
     * @param newStatus The new status for the note.
     * @return true if the note was found and moved, false otherwise.
     */
    public boolean moveNote(UUID noteId, Note.Status newStatus) {
        List<Note> newList = columns.get(newStatus);
        if (newList == null) return false;

        return moveNote(noteId, newStatus, newList.size());
    }

    /**
     * Moves a note to a new status and/or a new position within a column.
     * @param noteId The ID of the note to move.
     * @param newStatus The new status for the note.
     * @param newIndex The new index for the note within the target column's list.
     * @return true if the note was found and moved, false otherwise.
     */
    public boolean moveNote(UUID noteId, Note.Status newStatus, int newIndex) {
        return findNoteById(noteId).map(note -> {
            Note.Status oldStatus = note.getStatus();
            List<Note> oldList = columns.get(oldStatus);
            List<Note> newList = columns.get(newStatus);

            if (oldList == null || newList == null) return false;

            oldList.remove(note);
            note.setStatus(newStatus);

            if (newIndex >= 0 && newIndex <= newList.size()) {
                newList.add(newIndex, note);
            } else {
                newList.add(note); // Fallback: add to the end
            }
            return true;
        }).orElse(false);
    }

    /**
     * Removes a note from the board entirely.
     * @param noteId The ID of the note to remove.
     * @return true if the note was found and removed, false otherwise.
     */
    public boolean removeNote(UUID noteId) {
        return findNoteById(noteId).map(note ->
            columns.get(note.getStatus()).remove(note)
        ).orElse(false);
    }

    /**
     * Gets an unmodifiable list of all notes in a specific column.
     * @param status The status of the column.
     * @return An unmodifiable list of notes.
     */
    public List<Note> getNotesInColumn(Note.Status status) {
        return Collections.unmodifiableList(columns.get(status));
    }

    /**
     * Gets an unmodifiable list of all notes on the board, across all columns.
     * @return An unmodifiable list of all notes.
     */
    public List<Note> getAllNotes() {
        List<Note> allNotes = new ArrayList<>();
        for (List<Note> notesInColumn : columns.values()) {
            allNotes.addAll(notesInColumn);
        }
        return Collections.unmodifiableList(allNotes);
    }

    /**
     * Sorts the notes within a specific column using the given comparator.
     * @param status The status of the column to sort.
     * @param comparator The comparator to define the sort order.
     */
    public void sortColumn(Note.Status status, Comparator<Note> comparator) {
        List<Note> notesInColumn = columns.get(status);
        if (notesInColumn != null) {
            notesInColumn.sort(comparator);
        }
    }

    /**
     * Displays the board and its notes in a readable format to the console.
     */
    public void displayBoard() {
        System.out.println("========================================");
        System.out.println(" Board: " + this.name);
        System.out.println("========================================");
        columns.forEach((status, notes) -> {
            System.out.println("\n--- " + status + " ---");
            if (notes.isEmpty()) {
                System.out.println("(empty)");
            } else {
                notes.forEach(note -> System.out.println("  - " + note.getTitle() + " (ID: " + note.getId().toString().substring(0, 8) + ")"));
            }
        });
        System.out.println("\n========================================");
    }
}