package com.study.cockpit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;
import com.study.cockpit.config.AppProperties;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.entity.TaskImage;
import com.study.cockpit.mapper.TaskImageMapper;
import com.study.cockpit.security.ImageUrlSigner;
import com.study.cockpit.storage.StorageService;
import com.study.cockpit.util.FileGuard;
import com.study.cockpit.util.WeekUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片存储服务（需求 3，新增）。
 * - 上传：文件头 + 后缀双重校验、禁止执行类文件、UUID 重命名、目录隔离
 * - 有效期：upload_time 起算 15 天，expire_time 落库供定时任务扫描
 * - 访问：HMAC 临时地址 + Cookie JWT + 归属校验，仅本人可查看
 * - 到期：物理文件与数据库记录一并清除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskImageService {

    private static final int EXPIRE_DAYS = 15;
    private static final int CLEAN_BATCH = 500;

    private final TaskImageMapper imageMapper;
    private final TaskService taskService;
    private final StorageService storage;
    private final ImageUrlSigner signer;
    private final AppProperties props;

    public BizDTO.ImageVO upload(Long userId, Long taskId, MultipartFile file) {
        // 任务必须属于当前用户，防止把图片挂到他人任务上
        taskService.requireTask(userId, taskId);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_TYPE_REJECTED, "无法读取上传文件");
        }
        FileGuard.Verified verified = FileGuard.verify(
                bytes, file.getOriginalFilename(), props.getFileMaxSizeBytes(), props.getAllowedExt());

        StorageService.StoredFile stored = null;
        try {
            stored = storage.store(bytes, verified.ext());
            LocalDateTime now = WeekUtils.now();
            TaskImage image = new TaskImage();
            image.setUserId(userId);
            image.setTaskId(taskId);
            image.setOriginalName(safeOriginalName(file.getOriginalFilename()));
            image.setStoredName(stored.storedName());
            image.setRelativePath(stored.relativePath());
            image.setExt(verified.ext());
            image.setMimeType(verified.mimeType());
            image.setFileSize((long) bytes.length);
            image.setMagicVerified(1);
            image.setUploadTime(now);
            image.setExpireTime(now.plusDays(EXPIRE_DAYS));
            imageMapper.insert(image);
            return toVO(userId, image);
        } catch (BusinessException e) {
            cleanupQuietly(stored);
            throw e;
        } catch (Exception e) {
            cleanupQuietly(stored);
            log.error("图片上传失败 userId={} taskId={}", userId, taskId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "图片上传失败");
        }
    }

    public List<BizDTO.ImageVO> listByTask(Long userId, Long taskId) {
        taskService.requireTask(userId, taskId);
        List<TaskImage> images = imageMapper.selectList(Wrappers.<TaskImage>lambdaQuery()
                .eq(TaskImage::getUserId, userId)
                .eq(TaskImage::getTaskId, taskId)
                .orderByDesc(TaskImage::getId));
        List<BizDTO.ImageVO> result = new ArrayList<>();
        for (TaskImage image : images) {
            result.add(toVO(userId, image));
        }
        return result;
    }

    public void delete(Long userId, Long imageId) {
        TaskImage image = requireImage(userId, imageId);
        storage.delete(image.getRelativePath());
        imageMapper.deleteById(imageId);
    }

    /**
     * 图片字节流鉴权入口。
     * 校验顺序：签名（常量时间比较）-> 记录归属 -> 15 天有效期 -> 文件存在。
     * 任何一步失败统一 404，不区分"不存在"与"无权"，避免资源枚举。
     */
    public ImageContent loadForAccess(Long userId, Long imageId, Long exp, String sig) {
        if (exp == null || !signer.verify(imageId, userId, exp, sig)) {
            throw BusinessException.notFound();
        }
        TaskImage image = imageMapper.selectOne(Wrappers.<TaskImage>lambdaQuery()
                .eq(TaskImage::getId, imageId)
                .eq(TaskImage::getUserId, userId)
                .last("LIMIT 1"));
        if (image == null || image.getExpireTime() == null
                || !image.getExpireTime().isAfter(WeekUtils.now())) {
            throw BusinessException.notFound();
        }
        Path path = storage.resolve(image.getRelativePath());
        if (!Files.isRegularFile(path)) {
            // 文件已丢失但记录仍在：让清理任务下次处理，本次返回 404
            log.warn("图片文件缺失 imageId={} path={}", imageId, image.getRelativePath());
            throw BusinessException.notFound();
        }
        return new ImageContent(path, image.getMimeType(), image.getFileSize());
    }

    /**
     * 15 天到期清理（由 ImageExpireCleanTask 每日 03:00 调用）。
     * 分页扫描、先删文件再删记录；文件删除失败则保留记录留待下次重试，保证最终一致。
     */
    public int cleanExpired() {
        int total = 0;
        while (true) {
            List<TaskImage> batch = imageMapper.selectExpiredBatch(WeekUtils.now(), CLEAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            int deletedInBatch = 0;
            for (TaskImage image : batch) {
                try {
                    storage.delete(image.getRelativePath());
                } catch (Exception e) {
                    log.warn("过期图片文件删除失败，留待下次重试 imageId={}", image.getId());
                    continue;
                }
                imageMapper.deleteById(image.getId());
                deletedInBatch++;
                total++;
            }
            // 整批都失败或不足一批时退出，避免死循环
            if (deletedInBatch == 0 || batch.size() < CLEAN_BATCH) {
                break;
            }
        }
        if (total > 0) {
            log.info("过期图片清理完成，共删除 {} 条记录与文件", total);
        }
        return total;
    }

    private TaskImage requireImage(Long userId, Long imageId) {
        TaskImage image = imageMapper.selectOne(Wrappers.<TaskImage>lambdaQuery()
                .eq(TaskImage::getId, imageId)
                .eq(TaskImage::getUserId, userId)
                .last("LIMIT 1"));
        if (image == null) {
            throw BusinessException.notFound();
        }
        return image;
    }

    private BizDTO.ImageVO toVO(Long userId, TaskImage image) {
        long exp = signer.nextExpSeconds();
        String sig = signer.sign(image.getId(), userId, exp);
        BizDTO.ImageVO vo = new BizDTO.ImageVO();
        vo.setId(image.getId());
        vo.setTaskId(image.getTaskId());
        vo.setOriginalName(image.getOriginalName());
        vo.setTemporaryUrl("/api/images/" + image.getId() + "/raw?exp=" + exp + "&sig=" + sig);
        vo.setUploadTime(image.getUploadTime());
        vo.setExpireTime(image.getExpireTime());
        vo.setFileSize(image.getFileSize());
        vo.setMimeType(image.getMimeType());
        return vo;
    }

    /** 原始文件名只用于展示，截断并去掉换行，避免日志/CSV 注入 */
    private String safeOriginalName(String name) {
        if (name == null) {
            return "image";
        }
        String clean = name.replaceAll("[\\r\\n\\t]", "_").trim();
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    private void cleanupQuietly(StorageService.StoredFile stored) {
        if (stored != null) {
            try {
                storage.delete(stored.relativePath());
            } catch (Exception ignored) {
                // 补偿删除失败不影响错误返回
            }
        }
    }

    /** 鉴权通过后返回给控制器用于流式输出 */
    public record ImageContent(Path path, String mimeType, Long fileSize) {
    }
}
