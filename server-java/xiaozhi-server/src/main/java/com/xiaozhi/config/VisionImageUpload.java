package com.xiaozhi.config;

import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** Reads bounded image uploads in memory and derives the MIME type from the actual image format. */
final class VisionImageUpload {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;

    private VisionImageUpload() {}

    static Media read(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Chọn file ảnh không rỗng, tối đa 5 MB.");
        }
        byte[] data = file.getBytes();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("File ảnh không hợp lệ. Hỗ trợ PNG, JPG/JPEG và GIF.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    default -> throw new IllegalArgumentException("Định dạng ảnh chưa được hỗ trợ. Chọn PNG, JPG/JPEG hoặc GIF.");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IllegalArgumentException("Kích thước ảnh tối đa 4096 × 4096 pixel.");
                }
                return Media.builder().mimeType(MimeTypeUtils.parseMimeType(mime)).data(data).build();
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file ảnh. Hãy chọn ảnh hợp lệ.");
        }
    }
}
