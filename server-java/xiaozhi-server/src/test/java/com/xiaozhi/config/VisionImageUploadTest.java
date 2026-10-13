package com.xiaozhi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionImageUploadTest {
    @Test
    void supportedImagesPreserveBytesAndDeriveMimeTypeFromContent() throws Exception {
        for (String format : new String[]{"png", "jpeg", "gif"}) {
            byte[] bytes = ConfigControllerTest.imageBytes(format, 2, 2);
            var media = VisionImageUpload.read(new MockMultipartFile("image", "wrong-name.txt", "text/plain", bytes));
            assertThat(media.getDataAsByteArray()).containsExactly(bytes);
            assertThat(media.getMimeType().toString()).isEqualTo("image/" + format);
        }
    }

    @Test
    void emptyOversizedAndMalformedUploadsAreRejected() throws Exception {
        var oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn((long) VisionImageUpload.MAX_BYTES + 1);
        assertThatThrownBy(() -> VisionImageUpload.read(oversized)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5 MB");
        verify(oversized, never()).getBytes();
        for (byte[] bytes : new byte[][]{new byte[0], new byte[]{1, 2, 3}}) {
            assertThatThrownBy(() -> VisionImageUpload.read(new MockMultipartFile("image", "fake.png", "image/png", bytes)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unsupportedFormatsAndExcessiveDimensionsAreRejected() throws Exception {
        byte[] bmp = ConfigControllerTest.imageBytes("bmp", 2, 2);
        assertThatThrownBy(() -> VisionImageUpload.read(new MockMultipartFile("image", bmp)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Định dạng ảnh");
        byte[] wide = ConfigControllerTest.imageBytes("png", 4097, 1);
        assertThatThrownBy(() -> VisionImageUpload.read(new MockMultipartFile("image", wide)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4096");
    }
}
