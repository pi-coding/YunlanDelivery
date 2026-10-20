package com.fastdine.service;

/**
 * 文件存储服务。当前实现为本地磁盘，后续如需切换阿里云 OSS / MinIO，
 * 只需新增一个实现类替换本接口即可，上层代码不用改动。
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param content          文件字节内容
     * @param originalFilename 原始文件名（用于取扩展名）
     * @return 可直接访问的文件 URL（相对路径）
     */
    String upload(byte[] content, String originalFilename);
}
