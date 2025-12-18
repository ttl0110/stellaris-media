package com.ltt.stellaris.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTO representing a file or folder item for UI display
 */
@Data
@Builder
public class FileItem {

    /** Display name of the file/folder */
    private String name;

    /** Relative path from library root */
    private String path;

    /** Full absolute path (internal use only, not exposed to UI) */
    private String absolutePath;

    /** True if this is a directory */
    private boolean directory;

    /** File size in bytes (0 for directories) */
    private long size;

    /** Formatted file size for display (e.g., "1.5 GB") */
    private String sizeFormatted;

    /** Last modified timestamp */
    private Instant lastModified;

    /** Formatted last modified for display */
    private String lastModifiedFormatted;

    /** File type enum */
    private FileType fileType;

    /** File extension (without dot) */
    private String extension;

    /** Icon class (Boxicons) */
    private String icon;

    /** MIME type for streaming */
    private String mimeType;

    /** Thumbnail URL if available */
    private String thumbnailUrl;

    /** Number of items inside (for folders) */
    private Integer itemCount;

    /** Parent library name */
    private String libraryName;

    /**
     * Get the URL-safe encoded path for use in URLs
     */
    public String getEncodedPath() {
        if (path == null)
            return "";
        return path.replace("\\", "/");
    }

    /**
     * Check if the file is streamable (video/audio)
     */
    public boolean isStreamable() {
        return fileType != null && fileType.isStreamable();
    }

    /**
     * Check if the file can be previewed
     */
    public boolean isPreviewable() {
        return fileType != null && fileType.isPreviewable();
    }

    /**
     * Check if this is a video file
     */
    public boolean isVideo() {
        return fileType == FileType.VIDEO;
    }

    /**
     * Check if this is an audio file
     */
    public boolean isAudio() {
        return fileType == FileType.AUDIO;
    }

    /**
     * Check if this is an image file
     */
    public boolean isImage() {
        return fileType == FileType.IMAGE;
    }
}
