package com.study.cockpit.util;

import com.study.cockpit.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传文件安全校验单元测试：伪装脚本、路径穿越、伪造后缀应被拒绝。
 */
class FileGuardTest {

    private static final long MAX = 5 * 1024 * 1024;
    private static final String ALLOWED = "jpg,jpeg,png,webp";

    private byte[] fakePngScript() {
        // 内容是一段脚本，仅把文件名伪装成 .png
        return "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] realPng() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void shouldRejectEmptyFile() {
        assertThrows(BusinessException.class,
                () -> FileGuard.verify(new byte[0], "a.png", MAX, ALLOWED));
    }

    @Test
    void shouldRejectScriptRenamedAsPng() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileGuard.verify(fakePngScript(), "evil.png", MAX, ALLOWED));
        assertTrue(ex.getMessage().contains("图片") || ex.getMessage().contains("文件头"));
    }

    @Test
    void shouldRejectPathTraversalName() {
        assertThrows(BusinessException.class,
                () -> FileGuard.verify(fakePngScript(), "../../etc/passwd.png", MAX, ALLOWED));
    }

    @Test
    void shouldRejectExecutableDoubleSuffix() {
        assertThrows(BusinessException.class,
                () -> FileGuard.verify(fakePngScript(), "shell.jsp.png", MAX, ALLOWED));
    }

    @Test
    void shouldRejectDisallowedExtension() {
        assertThrows(BusinessException.class,
                () -> FileGuard.verify(fakePngScript(), "a.html", MAX, ALLOWED));
    }

    @Test
    void shouldAcceptRealPng() {
        assertDoesNotThrow(() -> {
            FileGuard.Verified v = FileGuard.verify(realPng(), "note.png", MAX, ALLOWED);
            assertEquals("png", v.ext());
        });
    }

    @Test
    void shouldRejectOversizeFile() {
        assertThrows(BusinessException.class,
                () -> FileGuard.verify(new byte[10], "a.png", 5, ALLOWED));
    }
}
