package com.ltt.stellaris.controller;

import com.ltt.stellaris.config.StellarisProperties;
import com.ltt.stellaris.model.BrowseResult;
import com.ltt.stellaris.model.FileItem;
import com.ltt.stellaris.service.FileService;
import com.ltt.stellaris.util.MimeTypeUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for file browsing and operations
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final StellarisProperties properties;

    /**
     * Browse files in a library
     */
    @GetMapping("/browse/{library}/**")
    public String browse(
            @PathVariable("library") String library,
            @RequestParam(value = "sort", defaultValue = "name") String sort,
            @RequestParam(value = "asc", defaultValue = "true") boolean asc,
            @RequestParam(value = "view", defaultValue = "grid") String view,
            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
            HttpServletRequest request,
            Model model) {

        // Extract path from request URI
        String fullPath = request.getRequestURI();
        String prefix = "/browse/" + library;
        String relativePath = fullPath.length() > prefix.length()
                ? fullPath.substring(prefix.length() + 1)
                : "";

        BrowseResult result = fileService.browse(library, relativePath, sort, asc);

        model.addAttribute("result", result);
        model.addAttribute("library", library);
        model.addAttribute("currentSort", sort);
        model.addAttribute("sortAsc", asc);
        model.addAttribute("currentView", view);
        model.addAttribute("defaultView", properties.getUi().getDefaultView());

        // If HTMX request, return only the file grid fragment
        if ("true".equals(htmxRequest)) {
            return "fragments/file-grid :: fileGrid";
        }

        return "browse";
    }

    /**
     * Browse root of a library
     */
    @GetMapping("/browse/{library}")
    public String browseRoot(
            @PathVariable("library") String library,
            @RequestParam(value = "sort", defaultValue = "name") String sort,
            @RequestParam(value = "asc", defaultValue = "true") boolean asc,
            @RequestParam(value = "view", defaultValue = "grid") String view,
            Model model) {

        BrowseResult result = fileService.browse(library, "", sort, asc);

        model.addAttribute("result", result);
        model.addAttribute("library", library);
        model.addAttribute("currentSort", sort);
        model.addAttribute("sortAsc", asc);
        model.addAttribute("currentView", view);
        model.addAttribute("defaultView", properties.getUi().getDefaultView());

        return "browse";
    }

    /**
     * Download a file
     */
    @GetMapping("/download/{library}/**")
    public ResponseEntity<Resource> download(
            @PathVariable("library") String library,
            HttpServletRequest request) {

        String fullPath = request.getRequestURI();
        String prefix = "/download/" + library;
        String relativePath = fullPath.length() > prefix.length()
                ? fullPath.substring(prefix.length() + 1)
                : "";

        try {
            Path filePath = fileService.getFilePath(library, relativePath);
            FileItem fileInfo = fileService.getFileInfo(library, relativePath);

            Resource resource = new FileSystemResource(filePath);
            String contentType = MimeTypeUtils.getMimeType(filePath);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileInfo.getName() + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);
        } catch (Exception e) {
            log.error("Download failed: {}", relativePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Download a folder as ZIP
     */
    @GetMapping("/download-zip/{library}/**")
    public void downloadZip(
            @PathVariable("library") String library,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String fullPath = request.getRequestURI();
        String prefix = "/download-zip/" + library;
        String relativePath = fullPath.length() > prefix.length()
                ? fullPath.substring(prefix.length() + 1)
                : "";

        FileItem folderInfo = fileService.getFileInfo(library, relativePath);

        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + folderInfo.getName() + ".zip\"");

        try (OutputStream out = response.getOutputStream()) {
            fileService.zipFolder(library, relativePath, out);
        }
    }

    /**
     * Create a new folder
     */
    @PostMapping("/api/folder")
    @ResponseBody
    public Map<String, Object> createFolder(
            @RequestParam("library") String library,
            @RequestParam("path") String path,
            @RequestParam("name") String name) {

        Map<String, Object> result = new HashMap<>();
        try {
            fileService.createFolder(library, path, name);
            result.put("success", true);
            result.put("message", "Folder created successfully");
        } catch (Exception e) {
            log.error("Create folder failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Delete a file or folder
     */
    @DeleteMapping("/api/file")
    @ResponseBody
    public Map<String, Object> delete(
            @RequestParam("library") String library,
            @RequestParam("path") String path) {

        Map<String, Object> result = new HashMap<>();
        try {
            fileService.delete(library, path);
            result.put("success", true);
            result.put("message", "Deleted successfully");
        } catch (Exception e) {
            log.error("Delete failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Rename a file or folder
     */
    @PutMapping("/api/rename")
    @ResponseBody
    public Map<String, Object> rename(
            @RequestParam("library") String library,
            @RequestParam("path") String path,
            @RequestParam("newName") String newName) {

        Map<String, Object> result = new HashMap<>();
        try {
            fileService.rename(library, path, newName);
            result.put("success", true);
            result.put("message", "Renamed successfully");
        } catch (Exception e) {
            log.error("Rename failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Move a file or folder
     */
    @PutMapping("/api/move")
    @ResponseBody
    public Map<String, Object> move(
            @RequestParam("library") String library,
            @RequestParam("source") String source,
            @RequestParam("dest") String dest) {

        Map<String, Object> result = new HashMap<>();
        try {
            fileService.move(library, source, dest);
            result.put("success", true);
            result.put("message", "Moved successfully");
        } catch (Exception e) {
            log.error("Move failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Copy a file or folder
     */
    @PostMapping("/api/copy")
    @ResponseBody
    public Map<String, Object> copy(
            @RequestParam("library") String library,
            @RequestParam("source") String source,
            @RequestParam("dest") String dest) {

        Map<String, Object> result = new HashMap<>();
        try {
            fileService.copy(library, source, dest);
            result.put("success", true);
            result.put("message", "Copied successfully");
        } catch (Exception e) {
            log.error("Copy failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Upload files
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public Map<String, Object> upload(
            @RequestParam("library") String library,
            @RequestParam("path") String path,
            @RequestParam("files") MultipartFile[] files) {

        Map<String, Object> result = new HashMap<>();
        int successCount = 0;

        for (MultipartFile file : files) {
            try {
                fileService.saveUploadedFile(library, path, file.getOriginalFilename(),
                        file.getInputStream());
                successCount++;
            } catch (Exception e) {
                log.error("Upload failed for: {}", file.getOriginalFilename(), e);
            }
        }

        result.put("success", successCount > 0);
        result.put("message", successCount + " of " + files.length + " files uploaded");
        result.put("uploaded", successCount);
        return result;
    }

    /**
     * Get file info as JSON
     */
    @GetMapping("/api/file/{library}/**")
    @ResponseBody
    public FileItem getFileInfo(
            @PathVariable("library") String library,
            HttpServletRequest request) {

        String fullPath = request.getRequestURI();
        String prefix = "/api/file/" + library;
        String relativePath = fullPath.length() > prefix.length()
                ? fullPath.substring(prefix.length() + 1)
                : "";

        return fileService.getFileInfo(library, relativePath);
    }
}
