package com.fastdine.service.impl;

import com.fastdine.constant.MessageConstant;
import com.fastdine.exception.BaseException;
import com.fastdine.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘存储实现：文件按 yyyy/MM/dd 分目录存放，
 * 通过 WebMvcConfiguration 中映射的 /upload/** 对外访问。
 */
@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final List<String> ALLOWED_EXT =
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /**
     * 本地存储根目录，默认项目根下的 upload 目录
     */
    @Value("${fastdine.upload.path:upload}")
    private String uploadPath;

    /**
     * 访问 URL 前缀
     */
    @Value("${fastdine.upload.url-prefix:/upload}")
    private String urlPrefix;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(uploadPath).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("本地文件存储目录：{}，访问前缀：{}", root, urlPrefix);
    }

    @Override
    public String upload(byte[] content, String originalFilename) {
        //1、扩展名白名单
        String ext = extensionOf(originalFilename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BaseException(MessageConstant.UPLOAD_FAILED + "：仅支持 " + ALLOWED_EXT);
        }

        //2、文件头校验：扩展名可以随便改，只有魔数能说明它到底是什么
        String actualType = detectImageType(content);
        if (actualType == null) {
            throw new BaseException(MessageConstant.UPLOAD_FAILED + "：文件内容不是有效的图片");
        }
        if (!matchExtension(ext, actualType)) {
            throw new BaseException(MessageConstant.UPLOAD_FAILED + "：文件扩展名与内容类型不一致");
        }

        //3、按日期分目录，避免单目录文件过多
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path dir = root.resolve(datePath);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), content);
        } catch (IOException e) {
            log.error("文件写入失败：{}", e.getMessage());
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }

        return urlPrefix + "/" + datePath + "/" + fileName;
    }

    /**
     * 通过文件头魔数识别图片类型，无法识别时返回 null
     *
     * @param content 文件字节
     * @return jpg / png / gif / bmp / webp
     */
    private String detectImageType(byte[] content) {
        if (content == null || content.length < 12) {
            return null;
        }
        //JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        //PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && content[4] == 0x0D && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A) {
            return "png";
        }
        //GIF: GIF8
        if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
            return "gif";
        }
        //BMP: BM
        if (content[0] == 'B' && content[1] == 'M') {
            return "bmp";
        }
        //WEBP: RIFF....WEBP
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * 判断扩展名与真实类型是否匹配（jpg 与 jpeg 视为同一种）
     */
    private boolean matchExtension(String ext, String actualType) {
        if ("jpg".equals(actualType)) {
            return "jpg".equals(ext) || "jpeg".equals(ext);
        }
        return actualType.equals(ext);
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
