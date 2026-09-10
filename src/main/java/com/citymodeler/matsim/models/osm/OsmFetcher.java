package com.citymodeler.matsim.models.osm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Downloads OSM PBF data from Geofabrik for a given boundary polygon.
 * Caches downloaded files locally to avoid re-downloading.
 */
public final class OsmFetcher {

    /** Progress callback: (stepDescription, fractionComplete) where fraction is 0.0-1.0 or -1 for indeterminate. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String step, double fraction);
    }

    private final HttpClient httpClient;
    private final Path cacheDir;

    public OsmFetcher(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public OsmFetcher() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "osm-cache"));
    }

    /**
     * Resolves the best Geofabrik region for the boundary, downloads the PBF,
     * and returns the local file path.
     *
     * @param boundary      the import boundary polygon
     * @param progress      progress callback (may be null)
     * @return local path to the downloaded PBF file
     */
    public Path fetchForBoundary(OsmBoundary boundary, ProgressListener progress) throws IOException {
        notify(progress, "Resolving region...", 0.0);
        Optional<GeofabrikRegions.Region> regionOpt = GeofabrikRegions.resolveSmallest(boundary);
        if (regionOpt.isEmpty()) {
            throw new IOException("No known Geofabrik region covers the given boundary");
        }
        GeofabrikRegions.Region region = regionOpt.get();
        System.out.println("[OsmFetcher] Resolved region: " + region.name());

        // Check cache
        Path cachedFile = cacheDir.resolve(region.baseName() + ".osm.pbf");
        if (Files.exists(cachedFile) && Files.size(cachedFile) > 1024) {
            notify(progress, "Using cached: " + region.baseName(), 1.0);
            return cachedFile;
        }

        notify(progress, "Downloading " + region.name() + "...", 0.0);
        Files.createDirectories(cacheDir);

        Path tempFile = cacheDir.resolve(region.baseName() + ".osm.pbf.part");
        downloadWithProgress(region.url(), tempFile, progress);

        Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
        notify(progress, "Download complete: " + region.baseName(), 1.0);
        return cachedFile;
    }

    /**
     * Downloads from a specific URL.
     */
    public Path fetchFromUrl(String url, Path outputFile, ProgressListener progress) throws IOException {
        notify(progress, "Downloading...", 0.0);
        Files.createDirectories(outputFile.getParent());
        Path tempFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        downloadWithProgress(url, tempFile, progress);
        Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        notify(progress, "Download complete", 1.0);
        return outputFile;
    }

    private void downloadWithProgress(String url, Path dest, ProgressListener progress) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
            }

            long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            try (InputStream in = response.body()) {
                byte[] buffer = new byte[8192];
                long bytesRead = 0;
                int lastPercent = -1;

                try (var out = Files.newOutputStream(dest)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        bytesRead += read;
                        if (totalBytes > 0 && progress != null) {
                            int percent = (int) (bytesRead * 100 / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                progress.onProgress(
                                        String.format("Downloading... %d%% (%d/%d MB)",
                                                percent, bytesRead / 1048576, totalBytes / 1048576),
                                percent / 100.0);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private static void notify(ProgressListener progress, String step, double fraction) {
        if (progress != null) {
            progress.onProgress(step, fraction);
        }
    }
}
