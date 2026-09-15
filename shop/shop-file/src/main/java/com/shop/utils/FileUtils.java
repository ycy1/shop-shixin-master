package com.shop.utils;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author xxj
 * @title FileUtils
 * @date 2025/7/19 11:59
 * @description TODO
 */
@Slf4j
public class FileUtils {

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final long TB = GB * 1024;

    /**
     * 将字节数转换为易读的文件大小字符串
     * @param size 文件大小（字节）
     * @return 格式化的字符串（如"1.23 MB"）
     */
    public static String convertFileSize(long size) {
        if (size < KB) {
            return size + " B";
        } else if (size < MB) {
            return String.format("%.2f KB", (double) size / KB);
        } else if (size < GB) {
            return String.format("%.2f MB", (double) size / MB);
        } else if (size < TB) {
            return String.format("%.2f GB", (double) size / GB);
        } else {
            return String.format("%.2f TB", (double) size / TB);
        }
    }

    /**
     * 压缩文件并返回压缩后的文件
     * @param multipartFile 文件
     * @return 压缩后的文件
     */
    public static File compressFile(MultipartFile multipartFile) {
        log.info("libwebp：图片压缩start,大小:{}", FileUtils.convertFileSize(multipartFile.getSize()));
        File compressFile = null;
        String outputPath = System.getProperty("user.dir") + File.separator + "libwebp";
        try {
            String filePath = outputPath + File.separator + multipartFile.getOriginalFilename();
            // 创建临时文件
            File file = new File(filePath);
            // 将 MultipartFile 内容传输到文件
            multipartFile.transferTo(file);

            String LIBWEBP_HOME = System.getenv("LIBWEBP_HOME");
            String cmd = "cwebp.exe -q 50 -mt " + filePath + " -o " + outputPath + File.separator + "compressFile.webp";
            // 创建一个ProcessBuilder实例
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", LIBWEBP_HOME + cmd); // 使用dir命令列出当前目录的内容
            if(!IpUtil.getOS().equals("win")){
                cmd = "cwebp -q 50 -mt " + filePath + " -o " + outputPath + File.separator + "compressFile.webp";
                builder = new ProcessBuilder("bash", "-c", cmd);
            }

            builder.redirectErrorStream(true); // 将错误输出和标准输出合并
            // 启动进程
            Process process = builder.start();

            // 读取命令的输出（如果有的话）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("libwebp：{}", line);
            }
            // 等待进程结束
            boolean finished = process.waitFor(60, TimeUnit.SECONDS); // 等待60秒
            int i = process.exitValue();
            if (finished && i == 0) {
                compressFile = new File(outputPath + File.separator + "compressFile.webp");
                log.info("libwebp：图片压缩end,大小:{}", FileUtils.convertFileSize(compressFile.length()));
            } else {
                log.error("libwebp：图片压缩失败");
            }
            FileUtil.del(file);
        } catch (Exception e) {
            log.error("文件压缩异常,{}", e.getMessage());
        }
        return compressFile;
    }

    /**
     * 解压webp文件并返回解压后的png文件列表
     * @param compressFile 压缩文件
     * @return 解压后的文件
     */
    public static File uncompressFile(File compressFile){
        log.info("libwebp：图片解压缩start,大小:{}", FileUtils.convertFileSize(compressFile.length()));
        String outputPath = System.getProperty("user.dir") + File.separator + "libwebp";

        try {
            String filePath = outputPath + File.separator + compressFile.getName();
            // 创建临时文件
            File file = new File(filePath);
            // 将 compressFile 内容传输到文件 file
            FileUtil.writeBytes(FileUtil.readBytes(compressFile), file);

            // dwebp C:\Users\Lenovo\Desktop\test\222.webp -o C:\Users\Lenovo\Desktop\test\222.png
            String LIBWEBP_HOME = System.getenv("LIBWEBP_HOME");
            String cmd = "dwebp.exe " + filePath + " -o " + outputPath + File.separator + "uncompressFile.png";
            // 创建一个ProcessBuilder实例
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", LIBWEBP_HOME + cmd); // 使用dir命令列出当前目录的内容
            if(!IpUtil.getOS().equals("win")){
                cmd = "cwebp " + filePath + " -o " + outputPath + File.separator + "uncompressFile.png";
                builder = new ProcessBuilder("bash", "-c", cmd);
            }

            builder.redirectErrorStream(true); // 将错误输出和标准输出合并
            // 启动进程
            Process process = builder.start();

            // 读取命令的输出（如果有的话）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("libwebp：{}", line);
            }
            // 等待进程结束
            int exitCode = process.waitFor();
            FileUtil.del(file);
//            System.out.println("Exited with code: " + exitCode);
//            System.exit(0);

        } catch (Exception e) {
            log.error("文件解压缩异常,{}", e.getMessage());
        }
        File uncompressFile = new File(outputPath + File.separator + "uncompressFile.png");
        log.info("libwebp：图片解压缩end,大小:{}", FileUtils.convertFileSize(uncompressFile.length()));
        return uncompressFile;
    }

    /**
     * 解压ZIP文件并返回解压后的文件列表
     * @param zipFile ZIP文件
     * @return 解压后的文件列表
     * @throws IOException 如果解压过程中出现错误
     */
    public static List<MultipartFile> unzipToFiles(MultipartFile zipFile) throws IOException {
        List<MultipartFile> extractedFiles = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream(), Charset.forName("GBK"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
//                    System.out.println(entry.getName());
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;

                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                    MultipartFile multipartFile = new MockMultipartFile(
                            entry.getName(),          // 文件名
                            entry.getName(),           // 原始文件名
                            null,                      // 内容类型(可为null)
                            bos.toByteArray()           // 文件内容
                    );
                    extractedFiles.add(multipartFile);
                }
            }
        }catch (IOException e){
            throw new IOException("解压文件失败："+ e.getMessage());
        }finally {
            zipFile.getInputStream().close();
        }

        return extractedFiles;
    }


    /**
     * 压缩多个文件并返回压缩后的字节数组
     * @param dataMap 文件名和字节数组的映射
     * @return 压缩后的字节数组
     * @throws IOException 如果压缩过程中出现错误
     */
    public static byte[] compressMultiple(Map<String, byte[]> dataMap) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : dataMap.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }catch (IOException e){
            log.error("压缩文件失败：{}", e.getMessage());
        }finally {
            try {
                bos.flush();
                bos.close();
            }catch (IOException e){
                log.error("关闭流异常：{}", e.getMessage());
            }
        }
        return bos.toByteArray();
    }


    /**
     * 将图片 URL 转换为 File
     * @param imageUrl 图片 URL
     * @return File 对象
     */
    public static File urlToFile(String imageUrl) {
        if(StringUtils.isEmpty(imageUrl)) return null;
        File tempFile = null;
        try {
            log.info("图片URL：{}", imageUrl);
            // 1. 创建 URL 对象并打开连接
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // 设置超时时间（防止网络问题导致卡死）
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");

            // 2. 获取文件扩展名
            String fileName = getFileNameFromUrl(imageUrl);
            String extension = getFileExtension(fileName);

            // 3. 创建临时文件（使用 .tmp 后缀，下载完成后重命名为正确扩展名）
            tempFile = File.createTempFile("image_", extension);
            // 确保程序退出时自动删除
            tempFile.deleteOnExit();

            // 4. 下载图片并写入临时文件
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }catch (Exception e){
            log.error("图片URL转换异常：{}", e.getMessage());
        }
        return tempFile;
    }

    public static boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 1. MIME 快速过滤
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return false;
        }

        // 2. 后缀过滤
        String name = file.getOriginalFilename();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        boolean extOk = lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp");
        if (!extOk) {
            return false;
        }

        return true;
    }

    public static void main(String[] args) throws Exception {
//        File file = urlToFile("http://182.92.85.80/group1/M00/00/07/tlxVUGqIRaqAYydCAAAkUuxDWME124.png");
//        System.out.println(file.getName());
        String fileNameFromUrl = getFileNameFromUrl("http://182.92.85.80/group1/M00/00/07/tlxVUGqIRaqAYydCAAAkUuxDWME124.png");
        System.out.println(fileNameFromUrl.substring(0, fileNameFromUrl.lastIndexOf('.')));
    }


    /**
     * 从 URL 中提取文件名
     */
    public static String getFileNameFromUrl(String imageUrl) {
        String[] segments = imageUrl.split("/");
        String lastSegment = segments[segments.length - 1];
        // 处理可能带参数的情况，如 image.jpg?token=xxx
        int queryIndex = lastSegment.indexOf('?');
        if (queryIndex != -1) {
            lastSegment = lastSegment.substring(0, queryIndex);
        }
        return lastSegment;
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".jpg"; // 默认扩展名
        }
        int dotIndex = fileName.lastIndexOf('.');
        return fileName.substring(dotIndex);
    }



}
