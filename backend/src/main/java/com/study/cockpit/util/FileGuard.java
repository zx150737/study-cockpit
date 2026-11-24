package com.study.cockpit.util;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件上传双重校验（安全要求 5 / 需求「堵死恶意脚本上传」）。
 *
 * 关卡：大小 -> 文件名安全（路径穿越/多重后缀/执行类后缀黑名单）-> 后缀白名单
 *      -> 文件头魔数与后缀一致性 -> 图片可解码性（JPEG/PNG）-> WEBP 结构校验。
 * 后缀与 Content-Type 均不作为信任依据，最终以文件头魔数判定真实类型。
 */
public final class FileGuard {

    /** 执行类 / 危险后缀：出现在文件名任意一段都直接拒绝（防 a.jsp.jpg） */
    private static final Set<String> EXEC_EXT = Set.of(
            "jsp", "jspx", "jspf", "php", "php3", "php4", "php5", "phtml", "pht",
            "asp", "aspx", "ashx", "asa", "cer", "exe", "dll", "so", "sh", "bat", "cmd",
            "com", "js", "mjs", "vbs", "ps1", "py", "pl", "cgi", "jar", "class",
            "html", "htm", "svg", "xml", "xhtml", "htaccess", "config", "ini");

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    /** 校验结果：规范化后的后缀与真实 MIME */
    public record Verified(String ext, String mimeType) {
    }

    private FileGuard() {
    }

    public static Verified verify(byte[] bytes, String originalName, long maxBytes, String allowedExtCsv) {
        if (bytes == null || bytes.length == 0) {
            throw reject("空文件");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "文件超过大小上限");
        }

        String name = originalName == null ? "" : originalName;
        // 文件名不得包含路径分隔符、父目录、控制字符
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.indexOf('\0') >= 0) {
            throw reject("文件名包含非法字符");
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("\\.");
        for (int i = 1; i < parts.length; i++) {
            if (EXEC_EXT.contains(parts[i])) {
                throw reject("文件名包含禁止的后缀");
            }
        }
        if (parts.length < 2) {
            throw reject("缺少文件后缀");
        }
        String ext = parts[parts.length - 1];
        if (!ALLOWED_EXT.contains(ext)) {
            throw reject("仅支持 jpg / png / webp 格式");
        }
        Set<String> configured = Arrays.stream(allowedExtCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (!configured.contains(ext)) {
            throw reject("该文件格式未在允许列表中");
        }

        // 文件头魔数（真实类型），必须与后缀一致
        if (isJpeg(bytes)) {
            if (!ext.equals("jpg") && !ext.equals("jpeg")) {
                throw reject("文件头与后缀不一致");
            }
            assertDecodable(bytes);
            return new Verified("jpg", "image/jpeg");
        }
        if (isPng(bytes)) {
            if (!ext.equals("png")) {
                throw reject("文件头与后缀不一致");
            }
            assertDecodable(bytes);
            return new Verified("png", "image/png");
        }
        if (isWebp(bytes)) {
            if (!ext.equals("webp")) {
                throw reject("文件头与后缀不一致");
            }
            // Java 原生 ImageIO 不支持 WEBP，改用 RIFF/WEBP 结构校验，不误杀合法 webp
            return new Verified("webp", "image/webp");
        }
        throw reject("文件内容不是合法图片（文件头校验失败）");
    }

    private static BusinessException reject(String message) {
        return new BusinessException(ResultCode.FILE_TYPE_REJECTED, message);
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        if (b.length < 8) {
            return false;
        }
        int[] magic = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < 8; i++) {
            if ((b[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] b) {
        if (b.length < 16) {
            return false;
        }
        String riff = new String(b, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(b, 8, 4, StandardCharsets.US_ASCII);
        String chunk = new String(b, 12, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp)
                && ("VP8 ".equals(chunk) || "VP8L".equals(chunk) || "VP8X".equals(chunk));
    }

    /** 必须能被真实解码为图片；同时限制尺寸，防图片炸弹与伪装脚本 */
    private static void assertDecodable(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > 20000 || image.getHeight() > 20000) {
                throw reject("图片内容无法解码或尺寸异常");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw reject("图片内容无法解码");
        }
    }
}
