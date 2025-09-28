package com.tarek.notetool;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A Java-based auto-updater that downloads the latest release ZIP from a GitHub
 * repository and extracts its contents into the current execution directory,
 * overwriting any existing files. It then relaunches the main application.
 */
public class AutoUpdater {

    // Configuration
    private static final String GITHUB_OWNER = "TarekLP";
    private static final String GITHUB_REPO = "NoteTool";
    private static final String GITHUB_API_URL = 
        "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";
    // The specific name of the release asset to download.
    private static final String TARGET_ASSET_NAME = "NoteToolDistrib.zip"; 
    // The name of the temporary file used to store the downloaded zip locally.
    private static final String DOWNLOAD_FILENAME = "latest_release.zip"; 
    // The name of the executable to launch after the update is complete.
    private static final String APP_EXECUTABLE_NAME = "NoteTool.exe";
    // --- Resilience Configuration ---
    private static final int MAX_DOWNLOAD_RETRIES = 3;
    private static final int RETRY_DELAY_SECONDS = 5;


    private record ReleaseInfo(String version, String downloadUrl) {}

    public static void main(String[] args) {
        System.out.println("--- NoteTool Auto-Updater ---");

        if (args.length == 0) {
            System.err.println("Error: This updater requires the current application version as a command-line argument.");
            return;
        }
        String currentVersion = args[0];
        System.out.println("Current version: " + currentVersion);

        try {
            // 1. Get the latest release download URL
            ReleaseInfo latestRelease = getLatestReleaseInfo();
            if (latestRelease == null) {
                System.err.println("Failed to find the latest release information from GitHub.");
                return;
            }
            System.out.println("Latest release found: Version " + latestRelease.version());

            // 2. Compare versions
            if (isNewer(latestRelease.version(), currentVersion)) {
                System.out.println("A new version is available. Starting download...");
            } else {
                System.out.println("You are already on the latest version. No update needed.");
                // Even if no update is needed, we still try to launch the app.
                relaunchApplication();
                return;
            }

            // 3. Download the ZIP file
            Path zipFilePath = downloadFile(latestRelease.downloadUrl(), DOWNLOAD_FILENAME);
            System.out.println("Download complete. Saved to: " + zipFilePath.toAbsolutePath());

            // 4. Extract the ZIP file (Overwriting existing files)
            extractZip(zipFilePath);
            System.out.println("Extraction complete. NoteTool updated successfully!");

            // 5. Clean up the downloaded ZIP file
            Files.delete(zipFilePath);
            System.out.println("Cleanup successful. Temporary file deleted.");

            // 6. Relaunch the main application
            relaunchApplication();

        } catch (Exception e) {
            System.err.println("An error occurred during the update process:");
            e.printStackTrace();
            // In case of error, still try to launch the app so the user isn't stuck.
            System.out.println("Attempting to launch the application anyway...");
            relaunchApplication();
        }
    }

    /**
     * Calls the GitHub API to find the download URL for the specific asset name.
     * @return The URL of the downloadable ZIP file, or null on failure.
     */
    private static ReleaseInfo getLatestReleaseInfo() throws IOException {
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        // GitHub API requires a User-Agent header
        connection.setRequestProperty("User-Agent", "NoteTool-Updater"); 

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            System.err.println("GitHub API check failed with HTTP status: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                System.err.println("This may be due to API rate limiting. Please try again later.");
            }
            // Read the error stream for more details from the API
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                System.err.println("API Response: " + errorReader.lines().collect(java.util.stream.Collectors.joining("\n")));
            }
            return null; // Return null to indicate failure
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            String jsonResponse = response.toString();
            // If the response is an empty array `[]`, there are no releases.
            if (jsonResponse.trim().equals("[]")) {
                System.err.println("No releases found on GitHub repository.");
                return null;
            }
            String json = jsonResponse;

            // Use regex for more robust parsing of the JSON response.
            // This is simpler than adding a full JSON library to a standalone updater.
            String version = extractJsonValue(json, "tag_name");
            String downloadUrl = null;

            // Find the correct asset's download URL
            Pattern assetsPattern = Pattern.compile("\\{\\s*\"url\":.*?\"browser_download_url\":\"(.*?)\".*?\"name\":\"" + Pattern.quote(TARGET_ASSET_NAME) + "\".*?\\}");
            Matcher assetsMatcher = assetsPattern.matcher(json);
            if (assetsMatcher.find()) {
                downloadUrl = assetsMatcher.group(1).replace("\\u002F", "/");
            }

            if (version == null || downloadUrl == null) {
                System.err.println("Could not parse version tag or download URL from GitHub API response.");
                return null;
            }

            return new ReleaseInfo(version, downloadUrl);
        } finally {
            connection.disconnect();
        }
    }

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Downloads a file from a given URL and saves it to a specified file path.
     * @param fileUrl The URL to download from.
     * @param fileName The name of the file to save.
     * @return The Path object of the saved file.
     */
    private static Path downloadFile(String fileUrl, String fileName) throws IOException {
        Path targetPath = Paths.get(fileName);

        for (int attempt = 1; attempt <= MAX_DOWNLOAD_RETRIES; attempt++) {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Follow redirects and set User-Agent header
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "NoteTool-Updater"); 

                long fileSize = connection.getContentLengthLong();
                System.out.println("\nDownloading asset: " + fileUrl);
                System.out.println("File size: " + (fileSize > 0 ? (fileSize / 1024) + " KB" : "Unknown"));

                try (InputStream in = connection.getInputStream()) {
                    try (OutputStream out = Files.newOutputStream(targetPath)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalBytesRead = 0;
                        int lastPercentage = -1;

                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            if (fileSize > 0) {
                                int percentage = (int) ((totalBytesRead * 100) / fileSize);
                                if (percentage > lastPercentage) {
                                    System.out.print("\rDownloading... " + percentage + "%");
                                    lastPercentage = percentage;
                                }
                            }
                        }
                        System.out.println("\rDownloading... 100%");
                    }
                    // If we reach here, the download was successful.
                    return targetPath;
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                System.err.println("\nDownload attempt " + attempt + " failed: " + e.getMessage());
                // Clean up the partially downloaded file before retrying
                Files.deleteIfExists(targetPath);

                if (attempt < MAX_DOWNLOAD_RETRIES) {
                    try {
                        System.out.println("Retrying in " + RETRY_DELAY_SECONDS + " seconds...");
                        Thread.sleep(RETRY_DELAY_SECONDS * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                        throw new IOException("Update process was interrupted.", ie);
                    }
                } else {
                    throw new IOException("Failed to download file after " + MAX_DOWNLOAD_RETRIES + " attempts.", e);
                }
            }
        }
        // This line should not be reachable, but is required by the compiler.
        throw new IOException("Download failed unexpectedly.");
    }

    /**
     * Extracts the contents of a ZIP file to the current directory, overwriting files.
     * @param zipFilePath The Path object of the ZIP file.
     */
    private static void extractZip(Path zipFilePath) throws IOException {
        Path targetDir = Paths.get("."); // Current directory
        
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                
                // Security check: prevent Zip Slip vulnerability
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry attempted to write outside of the target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    // Create directory structure if necessary
                    Files.createDirectories(entryPath);
                } else {
                    // Ensure parent directories exist
                    Files.createDirectories(entryPath.getParent());
                    
                    // Write the file content, explicitly OVERWRITING the existing file
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    /**
     * Relaunches the main application executable.
     */
    private static void relaunchApplication() {
        System.out.println("Relaunching " + APP_EXECUTABLE_NAME + "...");
        try {
            Path executablePath = Paths.get(APP_EXECUTABLE_NAME);
            if (Files.exists(executablePath)) {
                new ProcessBuilder(executablePath.toAbsolutePath().toString()).start();
                System.out.println("Application relaunched successfully.");
            } else {
                System.err.println("Error: Could not find application executable to relaunch: " + APP_EXECUTABLE_NAME);
            }
        } catch (IOException e) {
            System.err.println("Failed to relaunch the application:");
            e.printStackTrace();
        }
    }

    /**
     * Compares two version strings (e.g., "1.3.0", "v1.4.0").
     * @param v1 The first version string.
     * @param v2 The second version string.
     * @return true if v1 is newer than v2, false otherwise.
     */
    private static boolean isNewer(String v1, String v2) {
        // Remove leading 'v' and split by dots
        String[] parts1 = v1.toLowerCase().replace("v", "").split("\\.");
        String[] parts2 = v2.toLowerCase().replace("v", "").split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            double part1 = i < parts1.length ? Double.parseDouble(parts1[i]) : 0;
            double part2 = i < parts2.length ? Double.parseDouble(parts2[i]) : 0;

            if (part1 > part2) {
                return true;
            }
            if (part1 < part2) {
                return false;
            }
        }
        // Versions are identical
        return false;
    }
}
