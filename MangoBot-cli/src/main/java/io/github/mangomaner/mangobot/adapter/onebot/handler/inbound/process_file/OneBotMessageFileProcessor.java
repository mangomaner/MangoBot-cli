package io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.process_file;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.*;
import io.github.mangomaner.mangobot.module.agent.workspace.ImageParseService;
import io.github.mangomaner.mangobot.module.file.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OneBotMessageFileProcessor {

    private final BotFilesService botFilesService;
    private final ImageParseService imageParseService;

    public void processReceivedFiles(List<OneBotMessageSegment> segments) {
        for (OneBotMessageSegment segment : segments) {
            if (segment instanceof FileSegment fileSegment) {
                processFileSegment(fileSegment);
            } else if (segment instanceof ImageSegment imageSegment) {
                processImageSegment(imageSegment);
            } else if (segment instanceof VideoSegment videoSegment) {
                processVideoSegment(videoSegment);
            } else if (segment instanceof RecordSegment recordSegment) {
                processRecordSegment(recordSegment);
            }
        }
    }

    private void processFileSegment(FileSegment segment) {
        FileSegment.FileData data = segment.getData();
        AddFileRequest request = new AddFileRequest();
        request.setFileId(data.getFileId());
        request.setFileType("file");
        request.setUrl(data.getUrl());
        request.setFileSize(Integer.parseInt(data.getFileSize()));
        request.setDescription(data.getFile());
        botFilesService.addFile(request);
    }

    private void processImageSegment(ImageSegment segment) {
        ImageSegment.ImageData data = segment.getData();

        int subType = data.getSubType() != null ? data.getSubType() : 0;
        String url = data.getUrl();
        String fileId = data.getFile();

        if (botFilesService.getFileByFileId(fileId) != null) {
            return;
        }

        String fileType;
        switch (subType) {
            case 1, 11 -> fileType = "meme";
            default -> fileType = "image";
        }

        if (isLocalFilePath(fileId)) {
            saveLocalImageFile(fileId, fileType, subType);
            return;
        }

        AddFileRequest request = new AddFileRequest();
        request.setFileId(fileId);
        request.setUrl(url);
        request.setSubType(subType);
        request.setFileSize(data.getFileSize() == null ? null : Integer.parseInt(data.getFileSize()));
        request.setFileType(fileType);

        String targetDir = switch (subType) {
            case 1, 11 -> "data/meme";
            default -> "data/image";
        };

        try {
            String fileName = extractFileName(fileId);
            String filePath = targetDir + "/" + fileName;
            Path targetPath = FileUtils.resolvePath(filePath);
            FileUtils.downloadFile(url, targetPath);
            request.setFilePath(filePath);
            log.info("Downloaded image: fileId={}, subType={}, url={}, filePath={}", fileId, subType, url, filePath);
        } catch (Exception e) {
            log.error("Failed to download image: fileId={}, subType={}, url={}", fileId, subType, url, e);
        }

        String description = null;
        if (url != null && !url.isEmpty()) {
            if (subType == 1 || subType == 11) {
                description = imageParseService.parseMeme(url);
            } else {
                description = imageParseService.parseImage(url);
            }

            if (description != null) {
                log.info("Parsed image description: fileId={}, description={}", fileId, description);
            }
        }
        request.setDescription(description);

        botFilesService.addFile(request);
    }

    private void processVideoSegment(VideoSegment segment) {
        VideoSegment.VideoData data = segment.getData();
        AddFileRequest request = new AddFileRequest();
        request.setFileId(data.getFile());
        request.setFileType("video");
        request.setUrl(data.getUrl());
        botFilesService.addFile(request);
    }

    private void processRecordSegment(RecordSegment segment) {
        RecordSegment.RecordData data = segment.getData();
        AddFileRequest request = new AddFileRequest();
        request.setFileId(data.getFile());
        request.setFileType("record");
        request.setUrl(data.getUrl());
        botFilesService.addFile(request);
    }

    private boolean isLocalFilePath(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return false;
        }
        return fileId.contains(":/") || fileId.contains(":\\") ||
               fileId.startsWith("/") || fileId.startsWith("\\\\");
    }

    private void saveLocalImageFile(String filePath, String fileType, int subType) {
        try {
            String fileName = extractFileName(filePath);
            String relativePath = extractRelativePath(filePath);

            if (botFilesService.getFileByFileId(fileName) != null) {
                return;
            }

            AddFileRequest request = new AddFileRequest();
            request.setFileId(fileName);
            request.setFileType(fileType);
            request.setFilePath(relativePath);
            request.setSubType(subType);

            Path localPath = FileUtils.resolvePath(relativePath);
            if (Files.exists(localPath)) {
                request.setFileSize((int) Files.size(localPath));
            }

            botFilesService.addFile(request);
            log.info("Saved local image file: fileId={}, filePath={}, fileType={}", fileName, relativePath, fileType);
        } catch (Exception e) {
            log.warn("Failed to save local image file: {}", filePath, e);
        }
    }

    private String extractRelativePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        Path baseDir = FileUtils.getBaseDirectory();
        Path absolutePath;

        try {
            if (java.nio.file.Paths.get(filePath).isAbsolute()) {
                absolutePath = java.nio.file.Paths.get(filePath);
            } else {
                absolutePath = baseDir.resolve(filePath);
            }

            if (absolutePath.startsWith(baseDir)) {
                return baseDir.relativize(absolutePath).toString().replace('\\', '/');
            }
        } catch (Exception e) {
            log.debug("Failed to extract relative path: {}", filePath);
        }

        return filePath.replace('\\', '/');
    }

    private String extractFileName(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return "unknown";
        }
        int lastSeparator = Math.max(
            fileId.lastIndexOf('/'),
            fileId.lastIndexOf('\\')
        );
        if (lastSeparator >= 0 && lastSeparator < fileId.length() - 1) {
            return fileId.substring(lastSeparator + 1);
        }
        return fileId;
    }
}
