package io.github.mangomaner.mangobot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.BotFiles;
import io.github.mangomaner.mangobot.service.BotFilesService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/image")
@Slf4j
public class ImageController {

    @Resource
    private BotFilesService botFilesService;

    @GetMapping("/list")
    @Operation(summary = "分页获取所有图片")
    public BaseResponse<IPage<BotFiles>> getImages(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "图片类型：image 或 meme") @RequestParam(required = false) String fileType) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BotFiles::getFileType, "image", "meme");
        if (fileType != null && !fileType.isEmpty()) {
            wrapper.eq(BotFiles::getFileType, fileType);
        }
        wrapper.orderByDesc(BotFiles::getCreateTime);
        
        Page<BotFiles> pageParam = new Page<>(page, pageSize);
        IPage<BotFiles> result = botFilesService.page(pageParam, wrapper);
        return ResultUtils.success(result);
    }

    @GetMapping("/fileId/{fileId}")
    @Operation(summary = "根据file_id获取图片信息")
    public BaseResponse<BotFiles> getImageByFileId(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileId, fileId);
        wrapper.in(BotFiles::getFileType, "image", "meme");
        BotFiles file = botFilesService.getOne(wrapper);
        return ResultUtils.success(file);
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "根据ID获取图片信息")
    public BaseResponse<BotFiles> getImageById(
            @Parameter(description = "数据库ID") @PathVariable Long id) {
        BotFiles file = botFilesService.getById(id);
        if (file != null && !"image".equals(file.getFileType()) && !"meme".equals(file.getFileType())) {
            return ResultUtils.error(400, "该文件不是图片类型");
        }
        return ResultUtils.success(file);
    }

    @GetMapping("/view/{fileId}")
    @Operation(summary = "根据file_id查看图片文件")
    public ResponseEntity<org.springframework.core.io.Resource> viewImage(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileId, fileId);
        wrapper.in(BotFiles::getFileType, "image", "meme");
        BotFiles file = botFilesService.getOne(wrapper);
        
        if (file == null || file.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            Path filePath = FileUtils.resolvePath(file.getFilePath());
            File imageFile = filePath.toFile();
            
            if (!imageFile.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            org.springframework.core.io.Resource resource = new FileSystemResource(imageFile);
            
            String contentType = "image/jpeg";
            if (file.getFileId().endsWith(".png")) {
                contentType = "image/png";
            } else if (file.getFileId().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (file.getFileId().endsWith(".webp")) {
                contentType = "image/webp";
            } else if (file.getFileId().endsWith(".jpg")) {
                contentType = "image/jpeg";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileId() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to view image: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "手动上传图片文件")
    public BaseResponse<String> uploadImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "图片类型：image 或 meme") @RequestParam(defaultValue = "image") String fileType,
            @Parameter(description = "图片描述") @RequestParam(required = false) String description) {
        if (file.isEmpty()) {
            return ResultUtils.error(400, "文件不能为空");
        }
        
        if (!"image".equals(fileType) && !"meme".equals(fileType)) {
            return ResultUtils.error(400, "fileType 必须是 image 或 meme");
        }
        
        try {
            String originalFilename = file.getOriginalFilename();
            String fileId = System.currentTimeMillis() + "_" + (originalFilename != null ? originalFilename : "image");
            
            String targetDir = "/data/" + fileType;
            String filePath = targetDir + "/" + fileId;
            
            Path targetPath = FileUtils.resolvePath(filePath);
            FileUtils.createParentDirectories(targetPath);
            file.transferTo(targetPath.toFile());
            
            BotFiles botFile = new BotFiles();
            botFile.setFileId(fileId);
            botFile.setFileType(fileType);
            botFile.setFilePath(filePath);
            botFile.setFileSize((int) file.getSize());
            botFile.setDescription(description);
            botFile.setCreateTime(System.currentTimeMillis());
            
            botFilesService.save(botFile);
            
            log.info("Uploaded image: fileId={}, fileType={}, filePath={}", fileId, fileType, filePath);
            return ResultUtils.success(fileId);
        } catch (Exception e) {
            log.error("Failed to upload image", e);
            return ResultUtils.error(500, "上传失败: " + e.getMessage());
        }
    }

    @PutMapping("/update")
    @Operation(summary = "更新图片信息")
    public BaseResponse<Boolean> updateImage(
            @Parameter(description = "数据库ID") @RequestParam Long id,
            @Parameter(description = "图片描述") @RequestParam(required = false) String description) {
        BotFiles file = botFilesService.getById(id);
        if (file == null) {
            return ResultUtils.error(404, "图片不存在");
        }
        
        if (!"image".equals(file.getFileType()) && !"meme".equals(file.getFileType())) {
            return ResultUtils.error(400, "该文件不是图片类型");
        }
        
        if (description != null) {
            file.setDescription(description);
        }
        
        boolean result = botFilesService.updateById(file);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/id/{id}")
    @Operation(summary = "根据ID删除图片")
    public BaseResponse<Boolean> deleteImageById(
            @Parameter(description = "数据库ID") @PathVariable Long id) {
        BotFiles file = botFilesService.getById(id);
        if (file == null) {
            return ResultUtils.error(404, "图片不存在");
        }
        
        if (!"image".equals(file.getFileType()) && !"meme".equals(file.getFileType())) {
            return ResultUtils.error(400, "该文件不是图片类型");
        }
        
        try {
            if (file.getFilePath() != null) {
                Path filePath = FileUtils.resolvePath(file.getFilePath());
                java.nio.file.Files.deleteIfExists(filePath);
            }
        } catch (Exception e) {
            log.error("Failed to delete file: filePath={}", file.getFilePath(), e);
        }
        
        boolean result = botFilesService.removeById(id);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/fileId/{fileId}")
    @Operation(summary = "根据file_id删除图片")
    public BaseResponse<Boolean> deleteImageByFileId(
            @Parameter(description = "文件ID") @PathVariable String fileId) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileId, fileId);
        wrapper.in(BotFiles::getFileType, "image", "meme");
        BotFiles file = botFilesService.getOne(wrapper);
        
        if (file == null) {
            return ResultUtils.error(404, "图片不存在");
        }
        
        try {
            if (file.getFilePath() != null) {
                Path filePath = FileUtils.resolvePath(file.getFilePath());
                java.nio.file.Files.deleteIfExists(filePath);
            }
        } catch (Exception e) {
            log.error("Failed to delete file: filePath={}", file.getFilePath(), e);
        }
        
        boolean result = botFilesService.remove(wrapper);
        return ResultUtils.success(result);
    }
}