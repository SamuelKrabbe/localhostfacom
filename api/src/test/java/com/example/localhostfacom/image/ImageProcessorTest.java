package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private byte[] image(int width, int height, int type, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void encodesAnOpaqueSourceAsJpeg() throws Exception {
        ProcessedImage result = processor.process(image(200, 100, BufferedImage.TYPE_INT_RGB, "png"), 1024);

        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.width()).isEqualTo(200);
        assertThat(result.height()).isEqualTo(100);
    }

    /** Re-encoding a transparent upload as JPEG would flatten it onto a black background. */
    @Test
    void keepsATransparentSourceAsPng() throws Exception {
        ProcessedImage result = processor.process(image(120, 120, BufferedImage.TYPE_INT_ARGB, "png"), 1024);

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void scalesDownWhileKeepingTheAspectRatio() throws Exception {
        ProcessedImage result = processor.process(image(2000, 1000, BufferedImage.TYPE_INT_RGB, "jpg"), 1024);

        assertThat(result.width()).isEqualTo(1024);
        assertThat(result.height()).isEqualTo(512);
    }

    @Test
    void leavesASmallImageAtItsOriginalSize() throws Exception {
        ProcessedImage result = processor.process(image(300, 200, BufferedImage.TYPE_INT_RGB, "jpg"), 1024);

        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(200);
    }

    @Test
    void producesTheSameHashForIdenticalBytes() throws Exception {
        byte[] source = image(50, 50, BufferedImage.TYPE_INT_RGB, "png");

        assertThat(processor.process(source, 1024).hash())
                .isEqualTo(processor.process(source, 1024).hash())
                .hasSize(64);
    }

    @Test
    void rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> processor.process("not an image".getBytes(), 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsAnImageWhoseDeclaredDimensionsAreAbsurd() throws Exception {
        // 9000 exceeds maxSourceDim, so it is refused from the header alone,
        // before anything is decoded into memory.
        assertThatThrownBy(() -> processor.process(image(9000, 10, BufferedImage.TYPE_INT_RGB, "png"), 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("too large");
    }
}
