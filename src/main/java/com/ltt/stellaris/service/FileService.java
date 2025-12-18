package com.ltt.stellaris.service;

import com.ltt.stellaris.config.StellarisProperties;
import com.ltt.stellaris.model.BrowseResult;
import com.ltt.stellaris.model.FileItem;
import com.ltt.stellaris.model.FileType;
import com.ltt.stellaris.util.FormatUtils;
import com.ltt.stellaris.util.MimeTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for file system operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final StellarisProperties properties;

    /**
     * Get configured library by name
     */
    public Optional<StellarisProperties.Library> getLibrary(String libraryName) {
        return properties.getLibraries().stream()
                .filter(lib -> lib.getName().equalsIgnoreCase(libraryName))
                .findFirst();
    }

    /**
     * Get all configured libraries as FileItems for home page
     */
    public List<FileItem> getLibraries() {
        return properties.getLibraries().stream()
                .map(lib -> FileItem.builder()
                        .name(lib.getName())
                        .path(lib.getName())
                        .directory(true)
                        .fileType(FileType.FOLDER)
                        .icon(lib.getIcon())
                        .libraryName(lib.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Browse files in a library at the given path
     */
    public BrowseResult browse(String libraryName, String relativePath, String sortBy, boolean ascending) {
        StellarisProperties.Library library = getLibrary(libraryName)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryName));

        Path basePath = Paths.get(library.getPath());
        String sanitizedPath = FormatUtils.sanitizePath(relativePath);
        Path targetPath = sanitizedPath.isEmpty() ? basePath : basePath.resolve(sanitizedPath);

        // Security check: ensure path is within library
        if (!targetPath.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: path outside library");
        }

        if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
            throw new IllegalArgumentException("Directory not found: " + relativePath);
        }

        List<FileItem> items = listDirectory(targetPath, libraryName, sanitizedPath);

        // Sort items
        sortItems(items, sortBy, ascending);

        // Build breadcrumbs
        List<BrowseResult.BreadcrumbItem> breadcrumbs = buildBreadcrumbs(libraryName, sanitizedPath);

        // Calculate statistics
        int folderCount = (int) items.stream().filter(FileItem::isDirectory).count();
        int fileCount = items.size() - folderCount;
        long totalSize = items.stream().filter(i -> !i.isDirectory()).mapToLong(FileItem::getSize).sum();

        // Calculate parent path
        String parentPath = null;
        if (!sanitizedPath.isEmpty()) {
            int lastSlash = sanitizedPath.lastIndexOf('/');
            parentPath = lastSlash > 0 ? sanitizedPath.substring(0, lastSlash) : "";
        }

        return BrowseResult.builder()
                .libraryName(libraryName)
                .currentPath(sanitizedPath)
                .parentPath(parentPath)
                .breadcrumbs(breadcrumbs)
                .items(items)
                .totalItems(items.size())
                .folderCount(folderCount)
                .fileCount(fileCount)
                .totalSize(totalSize)
                .totalSizeFormatted(FormatUtils.formatFileSize(totalSize))
                .build();
    }

    /**
     * List directory contents
     */
    private List<FileItem> listDirectory(Path directory, String libraryName, String relativePath) {
        List<FileItem> items = new ArrayList<>();

        try (Stream<Path> stream = Files.list(directory)) {
            stream.forEach(path -> {
                try {
                    FileItem item = createFileItem(path, libraryName, relativePath);
                    items.add(item);
                } catch (Exception e) {
                    log.warn("Failed to read file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to list directory: {}", directory, e);
        }

        return items;
    }

    /**
     * Create FileItem from Path
     */
    private FileItem createFileItem(Path path, String libraryName, String parentPath) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        String fileName = path.getFileName().toString();
        boolean isDirectory = Files.isDirectory(path);

        FileType fileType = isDirectory ? FileType.FOLDER : FileType.fromFilename(fileName);
        String itemPath = parentPath.isEmpty() ? fileName : parentPath + "/" + fileName;

        FileItem.FileItemBuilder builder = FileItem.builder()
                .name(fileName)
                .path(itemPath)
                .absolutePath(path.toAbsolutePath().toString())
                .directory(isDirectory)
                .lastModified(attrs.lastModifiedTime().toInstant())
                .lastModifiedFormatted(FormatUtils.formatDateTime(attrs.lastModifiedTime().toInstant()))
                .fileType(fileType)
                .icon(fileType.getIcon())
                .libraryName(libraryName);

        if (isDirectory) {
            // Count items in folder
            try (Stream<Path> stream = Files.list(path)) {
                builder.itemCount((int) stream.count());
            } catch (IOException e) {
                builder.itemCount(0);
            }
        } else {
            builder.size(attrs.size())
                    .sizeFormatted(FormatUtils.formatFileSize(attrs.size()))
                    .extension(FormatUtils.getExtension(fileName))
                    .mimeType(MimeTypeUtils.getMimeType(path));

            // Set thumbnail URL for images
            if (fileType == FileType.IMAGE) {
                builder.thumbnailUrl("/preview/image/" + libraryName + "/" + itemPath);
            }
        }

        return builder.build();
    }

    /**
     * Build breadcrumb navigation items
     */
    private List<BrowseResult.BreadcrumbItem> buildBreadcrumbs(String libraryName, String path) {
        List<BrowseResult.BreadcrumbItem> breadcrumbs = new ArrayList<>();

        // Add library root
        breadcrumbs.add(BrowseResult.BreadcrumbItem.builder()
                .name(libraryName)
                .path("")
                .current(path.isEmpty())
                .build());

        if (!path.isEmpty()) {
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    if (currentPath.length() > 0) {
                        currentPath.append("/");
                    }
                    currentPath.append(parts[i]);

                    breadcrumbs.add(BrowseResult.BreadcrumbItem.builder()
                            .name(parts[i])
                            .path(currentPath.toString())
                            .current(i == parts.length - 1)
                            .build());
                }
            }
        }

        return breadcrumbs;
    }

    /**
     * Sort file items
     */
    private void sortItems(List<FileItem> items, String sortBy, boolean ascending) {
        Comparator<FileItem> comparator = switch (sortBy != null ? sortBy.toLowerCase() : "name") {
            case "size" -> Comparator.comparingLong(FileItem::getSize);
            case "date", "modified" -> Comparator.comparing(FileItem::getLastModified,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "type" -> Comparator.comparing(item -> item.getFileType().name());
            default -> Comparator.comparing(FileItem::getName, String.CASE_INSENSITIVE_ORDER);
        };

        // Directories first, then apply sort
        Comparator<FileItem> finalComparator = Comparator
                .comparing(FileItem::isDirectory).reversed()
                .thenComparing(ascending ? comparator : comparator.reversed());

        items.sort(finalComparator);
    }

    /**
     * Get file info for a specific file
     */
    public FileItem getFileInfo(String libraryName, String relativePath) {
        StellarisProperties.Library library = getLibrary(libraryName)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryName));

        Path basePath = Paths.get(library.getPath());
        String sanitizedPath = FormatUtils.sanitizePath(relativePath);
        Path filePath = basePath.resolve(sanitizedPath);

        // Security check
        if (!filePath.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: path outside library");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }

        String parentPath = sanitizedPath.contains("/")
                ? sanitizedPath.substring(0, sanitizedPath.lastIndexOf('/'))
                : "";

        try {
            return createFileItem(filePath, libraryName, parentPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file info", e);
        }
    }

    /**
     * Get the actual Path for a file
     */
    public Path getFilePath(String libraryName, String relativePath) {
        StellarisProperties.Library library = getLibrary(libraryName)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryName));

        Path basePath = Paths.get(library.getPath());
        String sanitizedPath = FormatUtils.sanitizePath(relativePath);
        Path filePath = basePath.resolve(sanitizedPath);

        // Security check
        if (!filePath.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: path outside library");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }

        return filePath;
    }

    /**
     * Create a new folder
     */
    public void createFolder(String libraryName, String parentPath, String folderName) throws IOException {
        StellarisProperties.Library library = getLibrary(libraryName)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryName));

        Path basePath = Paths.get(library.getPath());
        String sanitizedParent = FormatUtils.sanitizePath(parentPath);
        Path targetPath = sanitizedParent.isEmpty()
                ? basePath.resolve(folderName)
                : basePath.resolve(sanitizedParent).resolve(folderName);

        // Security check
        if (!targetPath.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: path outside library");
        }

        Files.createDirectories(targetPath);
        log.info("Created folder: {}", targetPath);
    }

    /**
     * Delete a file or folder
     */
    public void delete(String libraryName, String relativePath) throws IOException {
        Path path = getFilePath(libraryName, relativePath);

        if (Files.isDirectory(path)) {
            // Delete directory recursively
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.error("Failed to delete: {}", p, e);
                        }
                    });
        } else {
            Files.delete(path);
        }
        log.info("Deleted: {}", path);
    }

    /**
     * Rename a file or folder
     */
    public void rename(String libraryName, String relativePath, String newName) throws IOException {
        Path path = getFilePath(libraryName, relativePath);
        Path newPath = path.resolveSibling(newName);

        // Security check for new name
        if (!newPath.normalize().getParent().equals(path.normalize().getParent())) {
            throw new SecurityException("Invalid new name");
        }

        Files.move(path, newPath);
        log.info("Renamed {} to {}", path, newPath);
    }

    /**
     * Move a file or folder
     */
    public void move(String libraryName, String sourcePath, String destPath) throws IOException {
        Path source = getFilePath(libraryName, sourcePath);

        StellarisProperties.Library library = getLibrary(libraryName).orElseThrow();
        Path basePath = Paths.get(library.getPath());
        String sanitizedDest = FormatUtils.sanitizePath(destPath);
        Path dest = sanitizedDest.isEmpty() ? basePath : basePath.resolve(sanitizedDest);

        // Security check
        if (!dest.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: destination outside library");
        }

        Path target = dest.resolve(source.getFileName());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved {} to {}", source, target);
    }

    /**
     * Copy a file or folder
     */
    public void copy(String libraryName, String sourcePath, String destPath) throws IOException {
        Path source = getFilePath(libraryName, sourcePath);

        StellarisProperties.Library library = getLibrary(libraryName).orElseThrow();
        Path basePath = Paths.get(library.getPath());
        String sanitizedDest = FormatUtils.sanitizePath(destPath);
        Path dest = sanitizedDest.isEmpty() ? basePath : basePath.resolve(sanitizedDest);

        // Security check
        if (!dest.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: destination outside library");
        }

        Path target = dest.resolve(source.getFileName());

        if (Files.isDirectory(source)) {
            // Copy directory recursively
            Files.walk(source).forEach(s -> {
                try {
                    Path d = target.resolve(source.relativize(s));
                    if (Files.isDirectory(s)) {
                        Files.createDirectories(d);
                    } else {
                        Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("Failed to copy: {}", s, e);
                }
            });
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Copied {} to {}", source, target);
    }

    /**
     * Create ZIP stream for a folder
     */
    public void zipFolder(String libraryName, String relativePath, OutputStream outputStream) throws IOException {
        Path folderPath = getFilePath(libraryName, relativePath);

        if (!Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException("Not a directory: " + relativePath);
        }

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            String baseName = folderPath.getFileName().toString();

            Files.walk(folderPath).forEach(path -> {
                if (!Files.isDirectory(path)) {
                    try {
                        String entryName = baseName + "/" + folderPath.relativize(path).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        log.error("Failed to add to zip: {}", path, e);
                    }
                }
            });
        }
    }

    /**
     * Save uploaded file
     */
    public void saveUploadedFile(String libraryName, String relativePath, String fileName,
            InputStream inputStream) throws IOException {
        StellarisProperties.Library library = getLibrary(libraryName)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryName));

        Path basePath = Paths.get(library.getPath());
        String sanitizedPath = FormatUtils.sanitizePath(relativePath);
        Path targetDir = sanitizedPath.isEmpty() ? basePath : basePath.resolve(sanitizedPath);
        Path targetFile = targetDir.resolve(fileName);

        // Security check
        if (!targetFile.normalize().startsWith(basePath.normalize())) {
            throw new SecurityException("Access denied: path outside library");
        }

        Files.createDirectories(targetDir);
        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved uploaded file: {}", targetFile);
    }

    /**
     * Find subtitle file for a video
     */
    public Optional<Path> findSubtitle(String libraryName, String videoPath) {
        Path video = getFilePath(libraryName, videoPath);
        String baseName = FormatUtils.getNameWithoutExtension(video.getFileName().toString());
        Path directory = video.getParent();

        // Look for subtitle files with same base name
        String[] subtitleExtensions = { "vtt", "srt", "ass", "ssa" };

        for (String ext : subtitleExtensions) {
            Path subtitlePath = directory.resolve(baseName + "." + ext);
            if (Files.exists(subtitlePath)) {
                return Optional.of(subtitlePath);
            }
        }

        return Optional.empty();
    }
}
