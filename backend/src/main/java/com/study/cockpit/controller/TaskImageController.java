package com.study.cockpit.controller;

import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.TaskImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * 图片上传 / 列表 / 删除 / 鉴权访问（需求 3 + 安全要求 5/10）。
 * 目录不对外暴露，字节流只能经 /api/images/{id}/raw 返回，且需通过签名与归属校验。
 */
@RestController
@RequiredArgsConstructor
public class TaskImageController {

    private final TaskImageService imageService;

    @PostMapping("/api/tasks/{taskId}/images")
    public Result<BizDTO.ImageVO> upload(@PathVariable Long taskId,
                                         @RequestParam("file") MultipartFile file) {
        return Result.ok(imageService.upload(SecurityUtils.getUserId(), taskId, file));
    }

    /** 每次请求都会刷新临时访问地址，前端刷新页面后需重新拉取列表 */
    @GetMapping("/api/tasks/{taskId}/images")
    public Result<List<BizDTO.ImageVO>> list(@PathVariable Long taskId) {
        return Result.ok(imageService.listByTask(SecurityUtils.getUserId(), taskId));
    }

    @DeleteMapping("/api/images/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        imageService.delete(SecurityUtils.getUserId(), id);
        return Result.ok();
    }

    /**
     * 图片字节流。&lt;img src&gt; 会自动携带 HttpOnly Cookie，配合 URL 上的 HMAC 签名完成双重鉴权。
     * 签名无效 / 过期 / 非本人 / 文件缺失 一律 404（由 BusinessException 统一处理）。
     */
    @GetMapping("/api/images/{id}/raw")
    public void raw(@PathVariable Long id,
                    @RequestParam("exp") Long exp,
                    @RequestParam("sig") String sig,
                    jakarta.servlet.http.HttpServletResponse response) throws IOException {
        TaskImageService.ImageContent content =
                imageService.loadForAccess(SecurityUtils.getUserId(), id, exp, sig);
        response.setContentType(content.mimeType());
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "inline");
        response.setHeader("Cache-Control", "private, no-store");
        long size = content.fileSize() == null ? Files.size(content.path()) : content.fileSize();
        response.setContentLengthLong(size);
        Files.copy(content.path(), response.getOutputStream());
    }
}
