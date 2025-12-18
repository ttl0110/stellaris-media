package com.ltt.stellaris.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for browse page response containing file listing and navigation info
 */
@Data
@Builder
public class BrowseResult {

    /** Current library name */
    private String libraryName;

    /** Current path relative to library root */
    private String currentPath;

    /** Parent path for navigation (null if at library root) */
    private String parentPath;

    /** Breadcrumb items for navigation */
    private List<BreadcrumbItem> breadcrumbs;

    /** List of files and folders in current directory */
    private List<FileItem> items;

    /** Total number of items */
    private int totalItems;

    /** Number of folders */
    private int folderCount;

    /** Number of files */
    private int fileCount;

    /** Total size of all files */
    private long totalSize;

    /** Formatted total size */
    private String totalSizeFormatted;

    @Data
    @Builder
    public static class BreadcrumbItem {
        private String name;
        private String path;
        private boolean current;
    }
}
