package com.ltt.stellaris.controller;

import com.ltt.stellaris.model.FileItem;
import com.ltt.stellaris.service.FileService;
import com.ltt.stellaris.service.MediaStreamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for media streaming and playback
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class StreamController {

    private final MediaStreamService mediaStreamService;
    private final FileService fileService;

    /**
     * Stream video or audio
     */
    @GetMapping("/stream/{library}/**")
    public ResponseEntity<Resource> stream(
            @PathVariable("library") String library,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletRequest request) {

        String relativePath = extractPath(request, "/stream/" + library);
        return mediaStreamService.streamMedia(library, relativePath, rangeHeader);
    }

    /**
     * Video player page
     */
    @GetMapping("/player/{library}/**")
    public String player(
            @PathVariable("library") String library,
            HttpServletRequest request,
            Model model) {

        String relativePath = extractPath(request, "/player/" + library);
        FileItem fileInfo = fileService.getFileInfo(library, relativePath);

        // Get files in same directory for next/previous navigation
        String parentPath = relativePath.contains("/")
                ? relativePath.substring(0, relativePath.lastIndexOf('/'))
                : "";

        model.addAttribute("file", fileInfo);
        model.addAttribute("library", library);
        model.addAttribute("streamUrl", "/stream/" + library + "/" + relativePath);
        model.addAttribute("subtitleUrl", "/subtitle/" + library + "/" + relativePath);

        return "player";
    }

    /**
     * Image gallery page
     */
    @GetMapping("/gallery/{library}/**")
    public String gallery(
            @PathVariable("library") String library,
            HttpServletRequest request,
            Model model) {

        String relativePath = extractPath(request, "/gallery/" + library);
        FileItem fileInfo = fileService.getFileInfo(library, relativePath);

        model.addAttribute("file", fileInfo);
        model.addAttribute("library", library);
        model.addAttribute("imageUrl", "/preview/image/" + library + "/" + relativePath + "?w=1920&h=1080");
        model.addAttribute("originalUrl", "/download/" + library + "/" + relativePath);

        return "gallery";
    }

    /**
     * Image preview (resized)
     */
    @GetMapping("/preview/image/{library}/**")
    public ResponseEntity<Resource> imagePreview(
            @PathVariable("library") String library,
            @RequestParam(value = "w", defaultValue = "300") int width,
            @RequestParam(value = "h", defaultValue = "300") int height,
            HttpServletRequest request) {

        String relativePath = extractPath(request, "/preview/image/" + library);
        return mediaStreamService.getImagePreview(library, relativePath, width, height);
    }

    /**
     * Video thumbnail
     */
    @GetMapping("/thumbnail/video/{library}/**")
    public ResponseEntity<Resource> videoThumbnail(
            @PathVariable("library") String library,
            HttpServletRequest request) {

        String relativePath = extractPath(request, "/thumbnail/video/" + library);
        return mediaStreamService.getVideoThumbnail(library, relativePath);
    }

    /**
     * Get subtitle for video
     */
    @GetMapping("/subtitle/{library}/**")
    public ResponseEntity<Resource> subtitle(
            @PathVariable("library") String library,
            HttpServletRequest request) {

        String relativePath = extractPath(request, "/subtitle/" + library);
        return mediaStreamService.getSubtitle(library, relativePath);
    }

    /**
     * Extract relative path from request URI
     */
    private String extractPath(HttpServletRequest request, String prefix) {
        String fullPath = request.getRequestURI();
        return fullPath.length() > prefix.length()
                ? fullPath.substring(prefix.length() + 1)
                : "";
    }
}
