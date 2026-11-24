package com.study.cockpit.storage;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;
import com.study.cockpit.config.AppProperties;
import com.study.cockpit.util.WeekUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 本地文件存储实现（安全要求 5：限制存储目录、隔离资源）。
 * - 根目录来自环境变量 FILE_STORAGE_DIR，且不得位于 Web 静态资源目录（AppProperties 已校验）
 * - 目录 750 / 文件 640，运行用户非 root，绝不授予执行权限
 * - 文件名由后端 UUID 生成，路径形如 2026/11/<uuid>.jpg，不接受任何用户输入路径
 * - 写临时文件后原子移动，避免留下半成品
 * - 读取前 normalize + startsWith(root) 校验，堵死目录穿越
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements StorageService {

    private final AppProperties props;
    private Path root;

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(props.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        setPermissions(root, "rwxr-x---");
        log.info("图片存储根目录就绪: {}", root);
    }

    @Override
    public StoredFile store(byte[] content, String ext) {
        String relativePath = YearMonth.now(WeekUtils.ZONE).toString().replace('-', '/')
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = resolve(relativePath);
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            setPermissions(target.getParent(), "rwxr-x---");
            tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(tmp, content);
            setPermissions(tmp, "rw-r-----");
            // 不使用 REPLACE_EXISTING：UUID 不会冲突，避免覆盖已有文件
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredFile(relativePath, target.getFileName().toString());
        } catch (IOException e) {
            cleanupQuietly(tmp);
            log.error("图片落盘失败 relativePath={}", relativePath, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        // 归一化后必须仍在根目录内；绝对路径或含 .. 的越界路径一律拒绝
        if (!path.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "非法文件路径");
        }
        return path;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Path path = resolve(relativePath);
            Files.deleteIfExists(path);
            // 顺带清理空的 月/年 目录，不触碰根目录
            Path parent = path.getParent();
            while (parent != null && parent.startsWith(root) && !parent.equals(root)) {
                try {
                    Files.deleteIfExists(parent);
                } catch (IOException ignored) {
                    break; // 目录非空，属正常情况
                }
                parent = parent.getParent();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("图片文件删除失败 relativePath={}", relativePath, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件删除失败");
        }
    }

    private void setPermissions(Path path, String perms) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms));
        } catch (UnsupportedOperationException e) {
            // 非 POSIX 文件系统（如某些挂载卷）：跳过，由部署文档的目录权限配置兜底
            log.debug("文件系统不支持 POSIX 权限，跳过: {}", path);
        } catch (IOException e) {
            log.warn("设置文件权限失败: {}", path);
        }
    }

    private void cleanupQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响主流程
        }
    }
}
