package com.tarek.notetool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages a collection of boards. This is the top-level container for the application state.
 */
public class NoteManager {

    private final Map<String, Board> boards;
    private final Deque<UUID> recentNoteIds;
    private User currentUser;
    private final Set<String> allTags;
    private final List<String> galleryImagePaths;
    private transient boolean isDirty = false;
    private transient Set<Path> loadedBoardFiles = new HashSet<>();

    private static final int MAX_RECENT_NOTES = 10;

    public static class NoteBoardPair {
        public final Note note;
        public final Board board;

        public NoteBoardPair(Note note, Board board) {
            this.note = note;
            this.board = board;
        }
    }

    public NoteManager() {
        this.boards = new HashMap<>();
        this.recentNoteIds = new LinkedList<>();
        this.currentUser = new User("Default User");
        this.allTags = new HashSet<>();
        this.galleryImagePaths = new ArrayList<>();
    }

    public void markAsDirty() {
        this.isDirty = true;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        markAsDirty();
    }

    public Set<String> getAllTags() {
        return allTags;
    }

    public void setAllTags(Set<String> tags) {
        this.allTags.clear();
        this.allTags.addAll(tags);
        markAsDirty();
    }

    /**
     * Gets an unmodifiable list of all image paths in the global gallery.
     * @return An unmodifiable list of image paths.
     */
    public List<String> getGalleryImagePaths() {
        return Collections.unmodifiableList(galleryImagePaths);
    }

    /**
     * Adds a new image path to the global gallery.
     * @param imagePath The file name of the image to add.
     */
    public void addGalleryImagePath(String imagePath) {
        if (imagePath != null && !imagePath.trim().isEmpty() && !this.galleryImagePaths.contains(imagePath)) {
            this.galleryImagePaths.add(imagePath);
            markAsDirty();
        }
    }

    public boolean removeGalleryImagePath(String imagePath) {
        if (this.galleryImagePaths.remove(imagePath)) {
            markAsDirty();
            return true;
        }
        return false;
    }

    /**
     * Creates a new board and adds it to the manager.
     * @param boardName The name of the new board.
     * @return The newly created Board.
     * @throws IllegalArgumentException if the board name is invalid or a board with that name already exists.
     */
    public Board createBoard(String boardName, List<User> members) {
        if (boardName == null || boardName.trim().isEmpty()) {
            throw new IllegalArgumentException("Board name cannot be null or empty.");
        }
        if (boards.containsKey(boardName)) {
            throw new IllegalArgumentException("A board with the name '" + boardName + "' already exists.");
        }
        Board newBoard = new Board(boardName, members, true);
        boards.put(boardName, newBoard);
        markAsDirty();
        return newBoard;
    }

    /**
     * Retrieves a board by its name.
     * @param boardName The name of the board to retrieve.
     * @return An Optional containing the board if found, otherwise an empty Optional.
     */
    public Optional<Board> getBoard(String boardName) {
        return Optional.ofNullable(boards.get(boardName));
    }

    /**
     * Removes a board from the manager.
     * @param boardName The name of the board to remove.
     * @return true if the board was found and removed, false otherwise.
     */
    public boolean removeBoard(String boardName) {
        Board removedBoard = boards.remove(boardName);
        if (removedBoard != null) {
            // Clean up any references to notes from the deleted board in the recent notes list.
            Set<UUID> notesFromRemovedBoard = removedBoard.getAllNotes().stream()
                    .map(Note::getId)
                    .collect(Collectors.toSet());
            recentNoteIds.removeAll(notesFromRemovedBoard);
            markAsDirty();
            return true;
        }
        return false;
    }

    /**
     * Duplicates a board and all its notes.
     * @param originalBoardName The name of the board to duplicate.
     * @return The newly created board.
     * @throws IllegalArgumentException if the original board does not exist.
     */
    public Board duplicateBoard(String originalBoardName) {
        Board originalBoard = getBoard(originalBoardName)
                .orElseThrow(() -> new IllegalArgumentException("Board '" + originalBoardName + "' not found."));

        // Find a unique name for the new board
        String newBoardName = originalBoardName + " (Copy)";
        int copyIndex = 2;
        while (boards.containsKey(newBoardName)) {
            newBoardName = originalBoardName + " (Copy " + copyIndex++ + ")";
        }

        // Create the new board with the same members
        Board newBoard = new Board(newBoardName, originalBoard.getMembers(), false);

        // Duplicate all notes from the original board to the new one
        for (Note originalNote : originalBoard.getAllNotes()) {
            Note newNote = originalNote.duplicate();
            newBoard.addNote(newNote);
        }

        boards.put(newBoardName, newBoard);
        markAsDirty();
        return newBoard;
    }

    /**
     * Finds all notes that have a given image file as a reference.
     * @param imageFileName The name of the image file in the gallery.
     * @return A list of notes that use the image.
     */
    public List<Note> getNotesUsingImage(String imageFileName) {
        if (imageFileName == null || imageFileName.isBlank()) {
            return Collections.emptyList();
        }
        List<Note> usingNotes = new ArrayList<>();
        for (Board board : boards.values()) {
            for (Note note : board.getAllNotes()) {
                if (note.getReferenceImagePaths().contains(imageFileName)) {
                    usingNotes.add(note);
                }
            }
        }
        return usingNotes;
    }

    /**
     * Gets an unmodifiable set of all board names.
     * @return An unmodifiable set of board names.
     */
    public Set<String> getBoardNames() {
        return Collections.unmodifiableSet(boards.keySet());
    }

    /**
     * A convenience method to find a note across all boards.
     * This could be slow if there are many boards and notes.
     * @param noteId The ID of the note to find.
     * @return An Optional containing the note if found, otherwise an empty Optional.
     */
    public Optional<Note> findNoteAcrossAllBoards(UUID noteId) {
        for (Board board : boards.values()) {
            Optional<Note> note = board.findNoteById(noteId);
            if (note.isPresent()) {
                return note;
            }
        }
        return Optional.empty();
    }

    public Optional<NoteBoardPair> findNoteAndBoard(UUID noteId) {
        for (Board board : boards.values()) {
            Optional<Note> noteOpt = board.findNoteById(noteId);
            if (noteOpt.isPresent()) {
                return Optional.of(new NoteBoardPair(noteOpt.get(), board));
            }
        }
        return Optional.empty();
    }

    /**
     * Searches for notes across all boards based on a query string.
     * The search is case-insensitive and checks note titles and content.
     * @param query The search term.
     * @return A map where each key is a matching Note and the value is the Board it belongs to.
     */
    public Map<Note, Board> searchAllNotes(String query) {
        Map<Note, Board> results = new HashMap<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        String lowerCaseQuery = query.toLowerCase();

        for (Board board : boards.values()) {
            for (Note note : board.getAllNotes()) {
                if (note.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                    note.getContent().toLowerCase().contains(lowerCaseQuery)) {
                    results.put(note, board);
                }
            }
        }
        return results;
    }

    public void recordNoteAccess(UUID noteId) {
        if (noteId == null) {
            return;
        }
        // Remove if it exists to move it to the front
        recentNoteIds.remove(noteId);
        // Add to the front
        recentNoteIds.addFirst(noteId);
        // Trim the list if it's too long
        while (recentNoteIds.size() > MAX_RECENT_NOTES) {
            recentNoteIds.removeLast();
        }
        markAsDirty();
    }

    public List<NoteBoardPair> getRecentNotes() {
        List<NoteBoardPair> recentNotes = new ArrayList<>();
        for (UUID noteId : recentNoteIds) {
            findNoteAndBoard(noteId).ifPresent(recentNotes::add);
        }
        return recentNotes;
    }

    /**
     * Saves the entire current state of the NoteManager to a single file for export.
     * @param filePath The path to the file where the state will be saved.
     * @throws IOException if an I/O error occurs while writing to the file.
     */
    public void saveToFile(String filePath) throws IOException {
        // Use a Gson instance configured for exporting the entire object graph.
        Gson exportGson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();

        try (Writer writer = new FileWriter(filePath)) {
            exportGson.toJson(this, writer);
            // Note: We don't reset the dirty flag for an export operation.
        }
    }

    /**
     * Loads a NoteManager state from a single file for import, creating a new manager instance.
     * @param filePath The path to the file from which to load the state.
     * @return The loaded NoteManager instance.
     * @throws IOException if an I/O error occurs while reading from the file.
     */
    public static NoteManager loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) { // Check for empty file
            throw new IOException("Data file not found or is empty, a new one will be created.");
        }

        // Use a Gson instance specifically for importing, which requires all deserializers.
        Gson importGson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(Board.class, new BoardDeserializer())
                .registerTypeAdapter(NoteManager.class, new NoteManagerDeserializer())
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();

        try (Reader reader = new FileReader(filePath)) {
            NoteManager manager = importGson.fromJson(reader, NoteManager.class);
            return manager != null ? manager : new NoteManager();
        } catch (Exception e) {
            // Catch broader exceptions during JSON parsing to prevent application crash on corrupt file
            throw new IOException("Failed to parse data file. It might be corrupted. " + e.getMessage(), e);
        }
    }

    /**
     * Exports a single board to a JSON file.
     * @param boardName The name of the board to export.
     * @param file The file to save the board to.
     * @throws IOException if the board doesn't exist or a save error occurs.
     */
    public void exportBoard(String boardName, File file) throws IOException {
        Board board = getBoard(boardName)
                .orElseThrow(() -> new IOException("Board '" + boardName + "' not found."));

        Gson gson = getGson();
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(board, writer);
        }
    }

    /**
     * Imports a single board from a JSON file.
     * If a board with the same name already exists, it will be renamed.
     * @param file The file to import the board from.
     * @throws IOException if a read or parse error occurs.
     */
    public void importBoard(File file) throws IOException {
        Gson gson = getGson();
        try (Reader reader = new FileReader(file)) {
            Board importedBoard = gson.fromJson(reader, Board.class);
            if (importedBoard == null || importedBoard.getName() == null) {
                throw new IOException("The file does not contain a valid board.");
            }

            String boardName = importedBoard.getName();
            int copyIndex = 1;
            while (boards.containsKey(boardName)) {
                boardName = importedBoard.getName() + " (Import " + copyIndex++ + ")";
            }
            importedBoard.setName(boardName);
            boards.put(boardName, importedBoard);
            markAsDirty();
        }
    }

    /**
     * Saves the current state of the NoteManager to a directory for persistence.
     * Preferences are saved in 'preferences.json'.
     * Each board is saved as a separate '[board-name].json' file in a 'boards' subdirectory.
     * @param dataDirectory The path to the directory where the state will be saved.
     * @throws IOException if an I/O error occurs while writing to the files.
     */
    public void saveToDirectory(Path dataDirectory) throws IOException {
        Gson gson = getGson();

        // 1. Save settings (currentUser, allTags, recentNoteIds) to preferences.json
        Path settingsFile = dataDirectory.resolve("preferences.json");
        NoteManagerSettings settings = new NoteManagerSettings();
        settings.currentUser = this.currentUser;
        settings.allTags = this.allTags;
        settings.recentNoteIds = this.recentNoteIds;
        settings.galleryImagePaths = this.galleryImagePaths;

        try (Writer writer = new FileWriter(settingsFile.toFile())) {
            gson.toJson(settings, writer);
        }

        // 2. Save each board to its own file in a 'boards' subdirectory
        Path boardsDir = dataDirectory.resolve("boards");
        if (!Files.exists(boardsDir)) {
            Files.createDirectories(boardsDir);
        }

        Set<Path> savedBoardFiles = new HashSet<>();
        for (Board board : boards.values()) {
            // Sanitize board name to create a valid filename.
            String fileName = board.getName().replaceAll("[^a-zA-Z0-9.\\-]", "_") + ".json";
            Path boardFile = boardsDir.resolve(fileName);
            savedBoardFiles.add(boardFile);
            try (Writer writer = new FileWriter(boardFile.toFile())) {
                gson.toJson(board, writer);
            }
        }

        // 3. Delete obsolete board files that were loaded but are no longer present.
        for (Path oldFile : loadedBoardFiles) {
            if (!savedBoardFiles.contains(oldFile)) {
                try {
                    Files.deleteIfExists(oldFile);
                    System.out.println("Deleted obsolete board file: " + oldFile);
                } catch (IOException e) {
                    System.err.println("Failed to delete obsolete board file: " + oldFile);
                }
            }
        }

        this.isDirty = false; // Reset dirty flag on successful save
    }

    /**
     * Loads a NoteManager state from a directory structure for persistence.
     * @param dataDirectory The path to the directory from which to load the state.
     * @return The loaded NoteManager instance.
     * @throws IOException if a critical I/O error occurs.
     */
    public static NoteManager loadFromDirectory(Path dataDirectory) throws IOException {
        Gson gson = getGson();
        NoteManager manager = new NoteManager();

        // 1. Load settings from preferences.json
        Path settingsFile = dataDirectory.resolve("preferences.json");
        if (Files.exists(settingsFile)) {
            try (Reader reader = new FileReader(settingsFile.toFile())) {
                NoteManagerSettings settings = gson.fromJson(reader, NoteManagerSettings.class);
                if (settings != null) {
                    if (settings.currentUser != null) manager.setCurrentUser(settings.currentUser);
                    if (settings.allTags != null) manager.allTags.addAll(settings.allTags);
                    if (settings.recentNoteIds != null) manager.recentNoteIds.addAll(settings.recentNoteIds);
                    if (settings.galleryImagePaths != null) manager.galleryImagePaths.addAll(settings.galleryImagePaths);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse preferences.json, using defaults. " + e.getMessage());
            }
        }

        // 2. Load boards from 'boards' subdirectory
        Path boardsDir = dataDirectory.resolve("boards");
        if (Files.exists(boardsDir) && Files.isDirectory(boardsDir)) {
            try (Stream<Path> stream = Files.list(boardsDir)) {
                stream.filter(file -> file.toString().endsWith(".json"))
                        .forEach(boardFile -> {
                            try (Reader reader = new FileReader(boardFile.toFile())) {
                                Board board = gson.fromJson(reader, Board.class);
                                manager.loadedBoardFiles.add(boardFile); // Track loaded files
                                if (board != null && board.getName() != null) {
                                    manager.boards.put(board.getName(), board);
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to load or parse board file: " + boardFile + ". " + e.getMessage());
                            }
                        });
            }
        }
        return manager;
    }

    // --- GSON Configuration ---

    private static Gson getGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(Board.class, new BoardDeserializer())
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .create();
    }

    /**
     * Custom adapter to properly serialize and deserialize LocalDateTime objects.
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            out.value(value != null ? value.toString() : null);
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString());
        }
    }

    /**
     * Custom deserializer for Board to correctly initialize the final 'columns' field as an EnumMap.
     */
    private static class BoardDeserializer implements JsonDeserializer<Board> {
        @Override
        public Board deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            String name = jsonObject.get("name").getAsString();
            Type userListType = new TypeToken<List<User>>() {}.getType();
            List<User> members = context.deserialize(jsonObject.get("members"), userListType);

            Board board = new Board(name, members != null ? members : new ArrayList<>(), false);

            // --- NEW: Robust Deserialization to handle old and new formats ---
            if (jsonObject.has("columns") && jsonObject.get("columns").isJsonArray()) {
                // This is the NEW format with a List<Column>
                Type columnListType = new TypeToken<List<Column>>() {}.getType();
                List<Column> deserializedColumns = context.deserialize(jsonObject.get("columns"), columnListType);
                if (deserializedColumns != null) {
                    board.setColumns(deserializedColumns);
                }

                Type noteMapType = new TypeToken<Map<UUID, Note>>() {}.getType();
                Map<UUID, Note> deserializedNotes = context.deserialize(jsonObject.get("notes"), noteMapType);
                if (deserializedNotes != null) {
                    // Directly populate the board's internal map to avoid the side effects of addNote(),
                    // which would add duplicate note IDs to the already-deserialized columns.
                    board.setNotesInternal(deserializedNotes);
                }
            } else if (jsonObject.has("columns") && jsonObject.get("columns").isJsonObject()) {
                // This is the OLD format with an EnumMap<Status, List<Note>>
                // We will migrate it on the fly.
                System.out.println("Migrating old board format for: " + name);

                // Manually create columns based on old statuses
                Column todoCol = new Column("To Do");
                Column inProgressCol = new Column("In Progress");
                Column doneCol = new Column("Done");
                Column archivedCol = new Column("Archived");
                board.setColumns(List.of(todoCol, inProgressCol, doneCol, archivedCol));

                // Deserialize the old structure
                Type oldColumnMapType = new TypeToken<Map<String, List<Note>>>() {}.getType();
                Map<String, List<Note>> oldColumns = context.deserialize(jsonObject.get("columns"), oldColumnMapType);

                // A helper map to link old status names to new column IDs
                Map<String, UUID> statusToColId = Map.of(
                        "TODO", todoCol.getId(),
                        "IN_PROGRESS", inProgressCol.getId(),
                        "DONE", doneCol.getId(),
                        "ARCHIVED", archivedCol.getId()
                );

                // Add all notes from the old structure to the new board structure
                oldColumns.forEach((status, notes) -> notes.forEach(note -> {
                    note.setColumnId(statusToColId.get(status));
                    board.addNote(note);
                }));
            }
            return board;
        }
    }

    /**
     * Custom deserializer for NoteManager to correctly initialize final collection fields.
     */
    private static class NoteManagerDeserializer implements JsonDeserializer<NoteManager> {
        @Override
        public NoteManager deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            NoteManager manager = new NoteManager();
            JsonObject jsonObject = json.getAsJsonObject();

            if (jsonObject.has("boards")) {
                Type boardMapType = new TypeToken<Map<String, Board>>() {}.getType();
                Map<String, Board> deserializedBoards = context.deserialize(jsonObject.get("boards"), boardMapType);
                if (deserializedBoards != null) {
                    manager.boards.putAll(deserializedBoards);
                }
            }

            if (jsonObject.has("recentNoteIds")) {
                Type uuidListType = new TypeToken<List<UUID>>() {}.getType();
                List<UUID> deserializedIds = context.deserialize(jsonObject.get("recentNoteIds"), uuidListType);
                if (deserializedIds != null) {
                    manager.recentNoteIds.addAll(deserializedIds);
                }
            }

            if (jsonObject.has("allTags")) {
                Type stringSetType = new TypeToken<Set<String>>() {}.getType();
                Set<String> deserializedTags = context.deserialize(jsonObject.get("allTags"), stringSetType);
                if (deserializedTags != null) {
                    manager.allTags.addAll(deserializedTags);
                }
            }

            if (jsonObject.has("currentUser")) {
                User user = context.deserialize(jsonObject.get("currentUser"), User.class);
                if (user != null) {
                    manager.setCurrentUser(user);
                }
            }

            return manager;
        }
    }

    // This class is a simple container for serializing/deserializing settings.
    private static class NoteManagerSettings {
        User currentUser;
        Set<String> allTags;
        Deque<UUID> recentNoteIds;
        List<String> galleryImagePaths;
    }
}