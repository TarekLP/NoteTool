package com.tarek.notetool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class to check for the latest version of the application from GitHub.
 */
public class UpdateChecker {

    private static final String GITHUB_OWNER = "TarekLP";
    private static final String GITHUB_REPO = "NoteTool";
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";

    /**
     * Fetches the latest release version tag from GitHub.
     * @return An Optional containing the version tag (e.g., "v1.4.0") if successful, otherwise empty.
     */
    public static Optional<String> getLatestVersionTag() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "NoteTool-Update-Checker");

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
                return Optional.empty();
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
                    System.out.println("No releases found on GitHub repository.");
                    return Optional.empty();
                }

                Pattern pattern = Pattern.compile("\"tag_name\":\\s*\"(.*?)\"");
                Matcher matcher = pattern.matcher(jsonResponse);
                return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
            }
        } catch (IOException e) {
            System.err.println("Could not connect to GitHub to check for updates: " + e.getMessage());
            return Optional.empty();
        }
    }
}