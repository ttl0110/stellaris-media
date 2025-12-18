package com.ltt.stellaris.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "stellaris")
public class StellarisProperties {

    private List<Library> libraries = new ArrayList<>();
    private Thumbnail thumbnail = new Thumbnail();
    private Ui ui = new Ui();

    @Data
    public static class Library {
        private String name;
        private String path;
        private String icon = "bx-folder";
    }

    @Data
    public static class Thumbnail {
        private boolean enabled = true;
        private String cachePath;
        private boolean videoThumbnailEnabled = true;
        private String ffmpegPath = "ffmpeg";
    }

    @Data
    public static class Ui {
        private int itemsPerPage = 50;
        private String defaultView = "grid";
    }
}
