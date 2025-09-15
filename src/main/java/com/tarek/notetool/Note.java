package com.tarek.notetool;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Note {

    // Enum for the status of the note (e.g., To Do, In Progress, Done)
    public enum Status {
        TODO,
        IN_PROGRESS,
        DONE,
        ARCHIVED
    }

    // Enum for the priority level of the note
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public static class Goal {
        private String description;
        private boolean completed;

        public Goal(String description) {
            this.description = description;
            this.completed = false;
        }

        public Goal(Goal original) {
            this.description = original.description;
            this.completed = original.completed;
        }

        public String getDescription() {
            return description;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    public static class Comment {
        private final String text;
        private final User author;
        private final LocalDateTime timestamp;

        public Comment(String text, User author) {
            this.text = text;
            this.author = author;
            this.timestamp = LocalDateTime.now();
        }

        public String getText() { return text; }
        public User getAuthor() { return author; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    private final UUID id;
    private String title;
    private String content;
    private Status status;
    private Priority priority;
    private LocalDateTime creationDate;
    private LocalDateTime lastModifiedDate;
    private LocalDateTime dueDate;
    private List<User> assignees;
    private List<Comment> comments;
    private List<Goal> goals; // A list of sub-tasks or goals
    private Set<String> tags; // A set of labels for categorization

    /**
     * Constructor for a new Note.
     * @param title The title of the note.
     * @param content The main content of the note.
     */
    public Note(String title, String content) {
        this.id = UUID.randomUUID(); // Assign a unique ID
        this.title = title;
        this.content = content;
        this.status = Status.TODO; // Default status
        this.priority = Priority.MEDIUM; // Default priority
        this.creationDate = LocalDateTime.now();
        this.lastModifiedDate = LocalDateTime.now();
        this.assignees = new ArrayList<>();
        this.comments = new ArrayList<>();
        this.goals = new ArrayList<>();
        this.tags = new HashSet<>();
    }

    /**
     * A true copy constructor that creates an identical copy of a note,
     * including its ID and timestamps. Used for creating an editable snapshot.
     * @param original The note to duplicate.
     */
    public Note(Note original) {
        this.id = original.id;
        this.title = original.title;
        this.content = original.content;
        this.status = original.status;
        this.priority = original.priority;
        this.creationDate = original.creationDate;
        this.lastModifiedDate = original.lastModifiedDate;
        this.dueDate = original.dueDate;
        this.assignees = new ArrayList<>(original.assignees);
        this.comments = new ArrayList<>(original.comments);
        this.goals = new ArrayList<>();
        for (Goal originalGoal : original.goals) {
            this.goals.add(new Goal(originalGoal));
        }
        this.tags = new HashSet<>(original.tags);
    }

    /**
     * Creates a duplicate of this note with a new ID and current timestamps.
     * This is used for the "Duplicate Note" feature.
     * @return A new Note instance that is a functional duplicate of the original.
     */
    public Note duplicate() {
        // Use the main constructor to get a new ID and timestamps
        Note newNote = new Note(this.title, this.content);

        // Copy the relevant properties from the original
        newNote.setStatus(this.status);
        newNote.setPriority(this.priority);
        newNote.setDueDate(this.dueDate);
        newNote.setAssignees(this.assignees); // setAssignees already creates a new list
        newNote.setTags(this.tags); // setTags already creates a new set

        // Deep copy goals
        List<Goal> newGoals = new ArrayList<>();
        for (Goal g : this.goals) {
            newGoals.add(new Goal(g));
        }
        newNote.setGoals(newGoals);

        // Comments are intentionally not copied for a duplication action.
        return newNote;
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        updateLastModified();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        updateLastModified();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        updateLastModified();
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
        updateLastModified();
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
        updateLastModified();
    }

    public List<User> getAssignees() {
        return assignees;
    }

    public void setAssignees(List<User> assignees) {
        this.assignees = new ArrayList<>(assignees);
        updateLastModified();
    }

    public List<Comment> getComments() {
        return Collections.unmodifiableList(comments);
    }

    public void setComments(List<Comment> comments) {
        this.comments = new ArrayList<>(comments);
        updateLastModified();
    }

    public List<Goal> getGoals() {
        return Collections.unmodifiableList(goals);
    }

    public void setGoals(List<Goal> goals) {
        // Create a mutable copy to ensure the internal list cannot be modified externally
        this.goals = new ArrayList<>(goals);
        updateLastModified();
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void setTags(Set<String> tags) {
        // Create a mutable copy to ensure the internal set cannot be modified externally
        this.tags = new HashSet<>(tags);
        updateLastModified();
    }

    // --- Helper Methods ---

    /**
     * Updates this note's fields from another note instance.
     * This is used to commit changes from an edited copy back to the original object.
     * The creation date and ID are not changed.
     * @param source The note to copy data from.
     * @throws IllegalArgumentException if the source note has a different ID.
     */
    public void updateFrom(Note source) {
        if (!this.id.equals(source.id)) {
            throw new IllegalArgumentException("Cannot update from a note with a different ID.");
        }
        this.title = source.title;
        this.content = source.content;
        this.status = source.status;
        this.priority = source.priority;
        this.lastModifiedDate = source.lastModifiedDate;
        this.dueDate = source.dueDate;
        this.assignees = new ArrayList<>(source.assignees);
        this.comments = new ArrayList<>(source.comments);
        this.goals = source.goals.stream().map(Goal::new).collect(Collectors.toList());
        this.tags = new HashSet<>(source.tags);
    }

    private void updateLastModified() {
        this.lastModifiedDate = LocalDateTime.now();
    }

    public void addGoal(String goal) {
        if (goal != null && !goal.trim().isEmpty()) {
            this.goals.add(new Goal(goal));
            updateLastModified();
        }
    }

    public void addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            // Only update if the tag was actually added (it wasn't a duplicate)
            if (this.tags.add(tag.toLowerCase())) {
                updateLastModified();
            }
        }
    }

    public void removeTag(String tag) {
        if (tag != null) {
            // Check if removal was successful to decide whether to update the modified date
            // The tag argument is guaranteed to be lowercase from the UI, so no need to convert.
            if (this.tags.remove(tag)) {
                updateLastModified();
            }
        }
    }

    /**
     * Checks if the note's due date has passed.
     * @return true if the note is overdue, false otherwise.
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate);
    }

    @Override
    public String toString() {
        return "Note{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", dueDate=" + dueDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Note note = (Note) o;
        return Objects.equals(id, note.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
