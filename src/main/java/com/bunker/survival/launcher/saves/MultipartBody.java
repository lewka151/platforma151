package com.bunker.survival.launcher.saves;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Streams multipart/form-data without buffering the file in RAM. */
final class MultipartBody {
    private MultipartBody() {
    }

    static String newBoundary() {
        return "----Platforma151" + UUID.randomUUID().toString().replace("-", "");
    }

    static HttpRequest.BodyPublisher ofFileUpload(Map<String, String> fields, String fileField, Path file,
                                                  String filename, String contentType, String boundary) throws IOException {
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String value = e.getValue() != null ? e.getValue() : "";
                parts.add(HttpRequest.BodyPublishers.ofString(
                        "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n"
                                + value + "\r\n",
                        StandardCharsets.UTF_8));
            }
        }
        String safeName = filename != null ? filename.replace("\"", "") : "save.zip";
        String type = contentType != null ? contentType : "application/zip";
        parts.add(HttpRequest.BodyPublishers.ofString(
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + safeName + "\"\r\n"
                        + "Content-Type: " + type + "\r\n\r\n",
                StandardCharsets.UTF_8));
        parts.add(HttpRequest.BodyPublishers.ofFile(file));
        parts.add(HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n", StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
    }
}
