package com.study.cockpit.storage;

import java.nio.file.Path;

/**
 * 文件存储抽象（需求：本地目录实现，预留对象存储扩展位）。
 * 本期只提供 LocalFileStorageServiceImpl；后续接入 OSS/MinIO 时新增实现类，
 * 把 Bean 换成 @Primary 即可，业务层无需改动。
 */
public interface StorageService {

    /** 落盘并返回相对路径与 UUID 文件名 */
    StoredFile store(byte[] content, String ext);

    /** 把相对路径解析为存储根下的绝对路径；越界（路径穿越）直接拒绝 */
    Path resolve(String relativePath);

    /** 删除物理文件（不存在时视为成功，保证清理任务幂等） */
    void delete(String relativePath);

    /** 存储结果 */
    record StoredFile(String relativePath, String storedName) {
    }
}
