package com.shop.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;

public class StreamMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;

    public StreamMultipartFile(String name, Path path, String contentType) {
        this.name = name;
        this.path = path;
        this.originalFilename = path.getFileName().toString();
        this.contentType = contentType;
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return getSize() == 0; }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}