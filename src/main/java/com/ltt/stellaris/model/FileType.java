package com.ltt.stellaris.model;

/**
 * Enum representing different file types for UI display and handling
 */
public enum FileType {
    FOLDER("folder", "bx-folder", null),
    VIDEO("video", "bx-film",
            new String[] { "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpeg", "mpg", "3gp" }),
    AUDIO("audio", "bx-music", new String[] { "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "opus" }),
    IMAGE("image", "bx-image",
            new String[] { "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif" }),
    DOCUMENT("document", "bx-file",
            new String[] { "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt" }),
    ARCHIVE("archive", "bx-archive", new String[] { "zip", "rar", "7z", "tar", "gz", "bz2", "xz" }),
    CODE("code", "bx-code",
            new String[] { "java", "py", "js", "ts", "html", "css", "json", "xml", "yml", "yaml", "md", "sh", "bat" }),
    OTHER("other", "bx-file-blank", null);

    private final String category;
    private final String icon;
    private final String[] extensions;

    FileType(String category, String icon, String[] extensions) {
        this.category = category;
        this.icon = icon;
        this.extensions = extensions;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public String[] getExtensions() {
        return extensions;
    }

    /**
     * Determine file type based on extension
     */
    public static FileType fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return OTHER;
        }
        String ext = extension.toLowerCase();
        for (FileType type : values()) {
            if (type.extensions != null) {
                for (String e : type.extensions) {
                    if (e.equals(ext)) {
                        return type;
                    }
                }
            }
        }
        return OTHER;
    }

    /**
     * Determine file type from filename
     */
    public static FileType fromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return OTHER;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return fromExtension(ext);
    }

    /**
     * Check if this type is streamable in browser
     */
    public boolean isStreamable() {
        return this == VIDEO || this == AUDIO;
    }

    /**
     * Check if this type can be previewed inline
     */
    public boolean isPreviewable() {
        return this == IMAGE || this == VIDEO || this == AUDIO || this == DOCUMENT;
    }
}
