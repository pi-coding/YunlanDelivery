package com.fastdine.controller.admin;

import com.fastdine.constant.MessageConstant;
import com.fastdine.result.Result;
import com.fastdine.service.FileStorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;

/**
 * 通用接口：文件上传
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 文件上传
     * <p>
     * 返回**带域名的绝对地址**而不是 /upload/xxx 相对路径：
     * 管理端和后台同源，两种都能显示，但小程序里的 image 标签不认相对路径，
     * 存相对路径会导致新上传的菜品图在小程序里裂开。
     *
     * @param file
     * @return 文件访问地址
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());
        try {
            String path = fileStorageService.upload(file.getBytes(), file.getOriginalFilename());
            // 从当前请求推导出 http://host:port，再拼上 /upload/...
            String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
            return Result.success(url);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage());
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
