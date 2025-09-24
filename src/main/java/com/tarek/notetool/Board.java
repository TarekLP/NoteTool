package com.tarek.notetool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a board, similar to Trello, that contains columns of notes.
 * The columns are based on the Note.Status enum.
 */
public class Board {

    private String name;
    private List<User> members;
    private final List<Column> columns;
    private final Map<UUID, Note> notes;

    /**
     * Constructs a new Board with a given name.
     * Initializes empty columns for each possible note status.
     * @param name The name of the board.
     */
    public Board(String name, List<User> members, boolean isNew) {
        this.name = name;
        this.members = new ArrayList<>(members);
        this.columns = new ArrayList<>();
        this.notes = new HashMap<>();

        if (isNew) {
            // Add default columns for a new board
            this.columns.add(new Column("To Do"));
            this.columns.add(new Column("In Progress"));
            this.columns.add(new Column("Done"));
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

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public void setColumns(List<Column> columns) {
        this.columns.clear();
        this.columns.addAll(columns);
    }

    /**
     * Internal method for setting the notes map directly, intended for use during deserialization.
     * This bypasses the logic in `addNote` to prevent side effects like creating duplicate references.
     * @param notes The map of notes to set.
     */
    void setNotesInternal(Map<UUID, Note> notes) {
        this.notes.putAll(notes);
    }

    /**
     * Adds a note to the board. The note is placed in the column
     * corresponding to its current status.
     * @param note The note to add.
     */
    public void addNote(Note note) {
        if (note != null && !notes.containsKey(note.getId())) {
            notes.put(note.getId(), note);
            // Add the note to its designated column's list of IDs
            findColumnById(note.getColumnId()).ifPresent(column -> {
                // Add to the end by default
                column.getNoteIds().add(note.getId());
            });
        }
    }

    /**
     * Finds a note by its ID across all columns.
     * @param noteId The UUID of the note to find.
     * @return An Optional containing the note if found, otherwise an empty Optional.
     */
    public Optional<Note> findNoteById(UUID noteId) {
        return Optional.ofNullable(notes.get(noteId));
    }

    /**
     * Moves a note to a different status column.
     * When no index is specified, the note is placed at the end of the target column.
     * @param noteId The ID of the note to move.
     * @param newColumnId The new status for the note.
     * @return true if the note was found and moved, false otherwise.
     */
    public boolean moveNote(UUID noteId, UUID newColumnId, int newIndex) {
        return findNoteById(noteId).map(note -> {
            UUID oldColumnId = note.getColumnId();

            // Remove from old column's noteId list
            findColumnById(oldColumnId).ifPresent(oldCol -> oldCol.getNoteIds().remove(noteId));

            // Add to new column's noteId list at the specified index
            findColumnById(newColumnId).ifPresent(newCol -> {
                List<UUID> noteIds = newCol.getNoteIds();
                if (newIndex >= 0 && newIndex <= noteIds.size()) {
                    noteIds.add(newIndex, noteId);
                } else {
                    noteIds.add(noteId); // Fallback to adding at the end
                }
            });

            // Update the note's own columnId
            if (!newColumnId.equals(oldColumnId)) {
                note.setColumnId(newColumnId);
            }

            return true;
        }).orElse(false);
    }

    public Optional<Column> findColumnById(UUID columnId) {
        return columns.stream().filter(c -> c.getId().equals(columnId)).findFirst();
    }

    /**
     * Removes a note from the board entirely.
     * @param noteId The ID of the note to remove.
     * @return true if the note was found and removed, false otherwise.
     */
    public boolean removeNote(UUID noteId) {
        if (notes.remove(noteId) != null) {
            // Also remove the ID from any column that contains it
            columns.forEach(column -> column.getNoteIds().remove(noteId));
            return true;
        }
        return false;
    }

    /**
     * Gets an unmodifiable list of all notes in a specific column.
     * @param status The status of the column.
     * @return An unmodifiable list of notes.
     */
    public List<Note> getNotesInColumn(UUID columnId) {
        return findColumnById(columnId)
                .map(column -> column.getNoteIds().stream()
                        .map(this::findNoteById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    /**
     * Gets an unmodifiable list of all notes on the board, across all columns.
     * @return An unmodifiable list of all notes.
     */
    public List<Note> getAllNotes() {
        return new ArrayList<>(notes.values());
    }

    /**
     * Sorts the notes within a specific column using the given comparator.
     * @param columnId The status of the column to sort.
     * @param comparator The comparator to define the sort order.
     */
    public void sortColumn(UUID columnId, Comparator<Note> comparator) {
        // This is now a UI concern, as the underlying list of all notes is not sorted per-column.
        // The MainViewController will get the notes for a column and sort them for display.
    }

    /**
     * Displays the board and its notes in a readable format to the console.
     */
    public void displayBoard() {
        System.out.println("========================================");
        System.out.println(" Board: " + this.name);
        System.out.println("========================================");
        columns.forEach(column -> {
            System.out.println("\n--- " + column.getName() + " ---");
            List<Note> notesInCol = getNotesInColumn(column.getId());
            if (notesInCol.isEmpty()) {
                System.out.println("(empty)");
            } else {
                notesInCol.forEach(note -> System.out.println("  - " + note.getTitle() + " (ID: " + note.getId().toString().substring(0, 8) + ")"));
            }
        });
        System.out.println("\n========================================");
    }
}