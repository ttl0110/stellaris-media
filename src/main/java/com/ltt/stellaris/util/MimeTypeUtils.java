package com.ltt.stellaris.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for MIME type detection
 */
public final class MimeTypeUtils {

    private MimeTypeUtils() {
        // Utility class
    }

    /**
     * Get MIME type for a file
     * 
     * @param path File path
     * @return MIME type string
     */
    public static String getMimeType(Path path) {
        try {
            String mimeType = Files.probeContentType(path);
            if (mimeType != null) {
                return mimeType;
            }
        } catch (IOException ignored) {
            // Fall through to extension-based detection
        }

        return getMimeTypeFromExtension(path.getFileName().toString());
    }

    /**
     * Get MIME type based on file extension
     */
    public static String getMimeTypeFromExtension(String filename) {
        String ext = FormatUtils.getExtension(filename).toLowerCase();

        return switch (ext) {
            // Video
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "avi" -> "video/x-msvideo";
            case "mov" -> "video/quicktime";
            case "wmv" -> "video/x-ms-wmv";
            case "flv" -> "video/x-flv";
            case "m4v" -> "video/x-m4v";
            case "mpeg", "mpg" -> "video/mpeg";
            case "3gp" -> "video/3gpp";

            // Audio
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "wma" -> "audio/x-ms-wma";
            case "m4a" -> "audio/mp4";
            case "opus" -> "audio/opus";

            // Image
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "tiff", "tif" -> "image/tiff";

            // Documents
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "rtf" -> "application/rtf";

            // Archives
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            case "tar" -> "application/x-tar";
            case "gz" -> "application/gzip";

            // Code
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "java", "py", "sh", "bat", "md", "yml", "yaml" -> "text/plain";

            // Subtitles
            case "srt" -> "application/x-subrip";
            case "vtt" -> "text/vtt";
            case "ass", "ssa" -> "text/x-ssa";

            default -> "application/octet-stream";
        };
    }

    /**
     * Check if MIME type is streamable in browser
     */
    public static boolean isStreamable(String mimeType) {
        if (mimeType == null)
            return false;
        return mimeType.startsWith("video/") || mimeType.startsWith("audio/");
    }

    /**
     * Check if MIME type is an image
     */
    public static boolean isImage(String mimeType) {
        if (mimeType == null)
            return false;
        return mimeType.startsWith("image/");
    }
}
