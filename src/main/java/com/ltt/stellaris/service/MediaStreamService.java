package com.ltt.stellaris.service;

import com.ltt.stellaris.config.StellarisProperties;
import com.ltt.stellaris.util.MimeTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for media streaming and thumbnail generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStreamService {

    private final StellarisProperties properties;
    private final FileService fileService;

    private Path thumbnailCachePath;

    @PostConstruct
    public void init() {
        String cachePath = properties.getThumbnail().getCachePath();
        if (cachePath != null) {
            thumbnailCachePath = Paths.get(cachePath.replace("${user.home}", System.getProperty("user.home")));
            try {
                Files.createDirectories(thumbnailCachePath);
                log.info("Thumbnail cache directory: {}", thumbnailCachePath);
            } catch (IOException e) {
                log.error("Failed to create thumbnail cache directory", e);
            }
        }
    }

    /**
     * Stream a video or audio file with range support
     * Properly handles HTTP Range requests for seeking and mobile browser
     * compatibility. Uses chunked responses for HDD optimization.
     */
    public ResponseEntity<Resource> streamMedia(String libraryName, String relativePath, String rangeHeader) {
        // Max chunk size - 5MB chunks for better HDD streaming
        final long MAX_CHUNK_SIZE = 5 * 1024 * 1024;

        try {
            Path filePath = fileService.getFilePath(libraryName, relativePath);
            long fileSize = Files.size(filePath);
            String mimeType = MimeTypeUtils.getMimeType(filePath);

            // No range header - return full file with Accept-Ranges header
            if (rangeHeader == null || rangeHeader.isEmpty()) {
                Resource resource = new FileSystemResource(filePath);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, mimeType)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                        .body(resource);
            }

            // Parse range header: "bytes=start-end" or "bytes=start-" or "bytes=-suffix"
            long start = 0;
            long end = fileSize - 1;
            boolean openEndedRange = false;

            String rangeSpec = rangeHeader.replace("bytes=", "").trim();

            // Handle multiple ranges (take only first one for simplicity)
            if (rangeSpec.contains(",")) {
                rangeSpec = rangeSpec.split(",")[0].trim();
            }

            if (rangeSpec.startsWith("-")) {
                // Suffix range: "-500" means last 500 bytes
                long suffixLength = Long.parseLong(rangeSpec.substring(1));
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else if (rangeSpec.endsWith("-")) {
                // Open-ended range: "500-" means from byte 500 to end
                start = Long.parseLong(rangeSpec.substring(0, rangeSpec.length() - 1));
                openEndedRange = true;
                // For HDD optimization: limit initial chunk size
                end = Math.min(start + MAX_CHUNK_SIZE - 1, fileSize - 1);
            } else if (rangeSpec.contains("-")) {
                // Full range: "500-999"
                String[] parts = rangeSpec.split("-");
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
            }

            // Validate range
            if (start < 0 || start > end || start >= fileSize) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }

            // Clamp end to file size
            if (end >= fileSize) {
                end = fileSize - 1;
            }

            long contentLength = end - start + 1;
            String contentRange = String.format("bytes %d-%d/%d", start, end, fileSize);

            log.debug("Streaming {}: range={}-{}, length={}, total={}, openEnded={}",
                    relativePath, start, end, contentLength, fileSize, openEndedRange);

            // Use InputStreamResource for proper streaming
            InputStream inputStream = new SeekableInputStream(filePath.toFile(), start, contentLength);
            Resource resource = new org.springframework.core.io.InputStreamResource(inputStream);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, mimeType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_RANGE, contentRange)
                    // Add cache headers for better mobile performance
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);

        } catch (NumberFormatException e) {
            log.warn("Invalid range header format: {}", rangeHeader);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error streaming media: {}", relativePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get image preview (resized thumbnail)
     */
    public ResponseEntity<Resource> getImagePreview(String libraryName, String relativePath, int maxWidth,
            int maxHeight) {
        try {
            Path imagePath = fileService.getFilePath(libraryName, relativePath);

            // For small images, return original
            if (maxWidth <= 0 && maxHeight <= 0) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.getMimeType(imagePath))
                        .body(new FileSystemResource(imagePath));
            }

            // Check if thumbnail exists in cache
            String cacheKey = generateCacheKey(imagePath.toString(), maxWidth, maxHeight);
            Path cachedThumbnail = thumbnailCachePath.resolve(cacheKey + ".jpg");

            if (!Files.exists(cachedThumbnail)) {
                // Generate thumbnail
                Files.createDirectories(cachedThumbnail.getParent());
                Thumbnails.of(imagePath.toFile())
                        .size(maxWidth > 0 ? maxWidth : 300, maxHeight > 0 ? maxHeight : 300)
                        .outputFormat("jpg")
                        .outputQuality(0.8)
                        .toFile(cachedThumbnail.toFile());
                log.debug("Generated thumbnail: {}", cachedThumbnail);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(new FileSystemResource(cachedThumbnail));

        } catch (Exception e) {
            log.error("Error generating image preview: {}", relativePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get video thumbnail using FFmpeg
     */
    public ResponseEntity<Resource> getVideoThumbnail(String libraryName, String relativePath) {
        if (!properties.getThumbnail().isVideoThumbnailEnabled()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path videoPath = fileService.getFilePath(libraryName, relativePath);

            // Check cache
            String cacheKey = generateCacheKey(videoPath.toString(), 320, 180);
            Path cachedThumbnail = thumbnailCachePath.resolve("video").resolve(cacheKey + ".jpg");

            if (!Files.exists(cachedThumbnail)) {
                Files.createDirectories(cachedThumbnail.getParent());

                // Use FFmpeg to extract frame at 10% of video duration
                String ffmpegPath = properties.getThumbnail().getFfmpegPath();
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath,
                        "-i", videoPath.toString(),
                        "-ss", "00:00:05", // Seek to 5 seconds
                        "-vframes", "1",
                        "-vf", "scale=320:-1",
                        "-y",
                        cachedThumbnail.toString());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                // Read output to prevent blocking
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (reader.readLine() != null) {
                        // Consume output
                    }
                }

                boolean completed = process.waitFor(30, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    log.warn("FFmpeg timeout for: {}", videoPath);
                    return ResponseEntity.notFound().build();
                }

                if (!Files.exists(cachedThumbnail)) {
                    log.warn("FFmpeg failed to generate thumbnail for: {}", videoPath);
                    return ResponseEntity.notFound().build();
                }

                log.debug("Generated video thumbnail: {}", cachedThumbnail);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(new FileSystemResource(cachedThumbnail));

        } catch (Exception e) {
            log.error("Error generating video thumbnail: {}", relativePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get subtitle file for a video
     */
    public ResponseEntity<Resource> getSubtitle(String libraryName, String videoPath) {
        Optional<Path> subtitlePath = fileService.findSubtitle(libraryName, videoPath);

        if (subtitlePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path path = subtitlePath.get();
        String mimeType = MimeTypeUtils.getMimeType(path);

        // Convert SRT to VTT if needed (browsers prefer VTT)
        if (path.toString().toLowerCase().endsWith(".srt")) {
            try {
                String vttContent = convertSrtToVtt(Files.readString(path));
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/vtt")
                        .body(new ByteArrayResource(vttContent.getBytes()));
            } catch (IOException e) {
                log.error("Error converting SRT to VTT", e);
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mimeType)
                .body(new FileSystemResource(path));
    }

    /**
     * Generate cache key for thumbnails
     */
    private String generateCacheKey(String path, int width, int height) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = path + "_" + width + "_" + height;
            byte[] hash = md.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf((path + "_" + width + "_" + height).hashCode());
        }
    }

    /**
     * Convert SRT subtitle format to WebVTT
     */
    private String convertSrtToVtt(String srtContent) {
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");

        // Simple conversion: replace comma with dot in timestamps
        String[] blocks = srtContent.split("\n\n");
        for (String block : blocks) {
            String[] lines = block.split("\n");
            if (lines.length >= 3) {
                // Skip sequence number, keep timestamp and text
                String timestamp = lines[1].replace(",", ".");
                StringBuilder text = new StringBuilder();
                for (int i = 2; i < lines.length; i++) {
                    text.append(lines[i]).append("\n");
                }
                vtt.append(timestamp).append("\n").append(text).append("\n");
            }
        }

        return vtt.toString();
    }

    /**
     * InputStream backed by RandomAccessFile for reliable seeking in large files.
     * Uses internal buffering for better HDD performance.
     * Unlike InputStream.skip(), RandomAccessFile.seek() is guaranteed to position
     * correctly.
     */
    private static class SeekableInputStream extends InputStream {
        private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer for HDD optimization

        private final RandomAccessFile raf;
        private long remaining;
        private final byte[] buffer;
        private int bufferPos;
        private int bufferLimit;

        public SeekableInputStream(File file, long start, long length) throws IOException {
            this.raf = new RandomAccessFile(file, "r");
            this.raf.seek(start); // RandomAccessFile.seek is reliable for any file size
            this.remaining = length;
            this.buffer = new byte[BUFFER_SIZE];
            this.bufferPos = 0;
            this.bufferLimit = 0;
        }

        private int fillBuffer() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(BUFFER_SIZE, remaining);
            int bytesRead = raf.read(buffer, 0, toRead);
            if (bytesRead > 0) {
                bufferPos = 0;
                bufferLimit = bytesRead;
            }
            return bytesRead;
        }

        @Override
        public int read() throws IOException {
            if (bufferPos >= bufferLimit) {
                if (fillBuffer() <= 0) {
                    return -1;
                }
            }
            remaining--;
            return buffer[bufferPos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0 && bufferPos >= bufferLimit) {
                return -1;
            }

            int totalRead = 0;
            while (len > 0 && (bufferPos < bufferLimit || remaining > 0)) {
                // Refill buffer if empty
                if (bufferPos >= bufferLimit) {
                    if (fillBuffer() <= 0) {
                        break;
                    }
                }

                // Copy from buffer
                int available = bufferLimit - bufferPos;
                int toCopy = Math.min(len, available);
                System.arraycopy(buffer, bufferPos, b, off, toCopy);
                bufferPos += toCopy;
                off += toCopy;
                len -= toCopy;
                totalRead += toCopy;
            }

            return totalRead > 0 ? totalRead : -1;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(remaining + (bufferLimit - bufferPos), Integer.MAX_VALUE);
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }

        @Override
        public long skip(long n) throws IOException {
            long toSkip = Math.min(n, remaining + (bufferLimit - bufferPos));

            // First, skip from buffer
            int bufferAvailable = bufferLimit - bufferPos;
            if (toSkip <= bufferAvailable) {
                bufferPos += (int) toSkip;
                return toSkip;
            }

            // Skip remaining buffer and seek in file
            long skippedFromBuffer = bufferAvailable;
            long remainingToSkip = toSkip - skippedFromBuffer;

            raf.seek(raf.getFilePointer() + remainingToSkip);
            remaining -= remainingToSkip;
            bufferPos = 0;
            bufferLimit = 0;

            return toSkip;
        }
    }

    /**
     * ByteArrayResource for in-memory content
     */
    private static class ByteArrayResource extends org.springframework.core.io.ByteArrayResource {
        public ByteArrayResource(byte[] byteArray) {
            super(byteArray);
        }
    }
}
