package com.example.videoprocessor.service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Classname: FileService
 * Package: com.example.videoprocessor.service
 * Description:
 *
 * @Author: No_Ripple(吴波)
 * @Creat： - 15:56
 * @Version: v1.0
 */
public class FileService {
    private final EncryptService encryptService;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor
            (10, 10, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));

    public FileService(EncryptService encryptService) {
        this.encryptService = encryptService;
    }

    public void read(String filePath, int chunkSize) throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r");
             FileChannel channel = file.getChannel();) {
            long size = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);

            for (long offset= 0; offset < size; offset += chunkSize) {
                int byteToRead = (int) Math.min(chunkSize, size - offset); // 计算当前块大小
                buffer.clear(); // 清空缓冲区
                channel.read(buffer, offset); // 从文件中读取数据到缓冲区
                buffer.flip(); // 准备读取缓冲区中的数据
                // 读取缓冲区中的数据
                byte[] chunk = new byte[byteToRead];
                buffer.get(chunk);

                // 提交到线程池处理当前块的数据
                executor.submit(() -> {
                    try {
                        byte[] compressedData = GzipCompress(chunk);
                        byte[] encryptedChunk = encryptService.encryptBytes(compressedData);
                    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                             IllegalBlockSizeException | BadPaddingException e) {
                        throw new RuntimeException(e);
                    }
                    return encryptService;
                });
                System.out.println("Chunk: " + new String(chunk));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void save(){

    }

    /**
     * 使用 Gzip 压缩数据
     * @param data 压缩前数据
     * @return 压缩后的数据
     */
    public byte[] GzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(data);
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * @param compressedData 压缩后数据
     * @return 解压后数据
     */
    public byte[] GzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
        }
        return byteArrayOutputStream.toByteArray();
    }
}
