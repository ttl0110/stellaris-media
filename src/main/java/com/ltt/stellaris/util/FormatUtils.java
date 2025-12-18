package com.ltt.stellaris.util;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for formatting file sizes and dates
 */
public final class FormatUtils {

    private static final String[] SIZE_UNITS = { "B", "KB", "MB", "GB", "TB" };
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.##");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private FormatUtils() {
        // Utility class, no instantiation
    }

    /**
     * Format file size in bytes to human readable format
     * 
     * @param bytes Size in bytes
     * @return Formatted string like "1.5 GB"
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < SIZE_UNITS.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return SIZE_FORMAT.format(size) + " " + SIZE_UNITS[unitIndex];
    }

    /**
     * Format date/time for display
     * 
     * @param instant Instant to format
     * @return Formatted date string like "2024-01-15 14:30"
     */
    public static String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_FORMATTER.format(instant);
    }

    /**
     * Get file extension from filename
     * 
     * @param filename File name
     * @return Extension without dot, or empty string if none
     */
    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Get filename without extension
     * 
     * @param filename File name
     * @return Filename without extension
     */
    public static String getNameWithoutExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0) {
            return filename;
        }
        return filename.substring(0, lastDot);
    }

    /**
     * Sanitize path to prevent directory traversal attacks
     * 
     * @param path Path to sanitize
     * @return Sanitized path
     */
    public static String sanitizePath(String path) {
        if (path == null) {
            return "";
        }
        // Remove any parent directory traversal attempts
        return path.replace("..", "")
                .replace("//", "/")
                .replace("\\\\", "/")
                .replace("\\", "/")
                .replaceAll("^/+", "");
    }
}
