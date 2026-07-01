package io.github.limuqy.mc.backup.database.csv;

import com.opencsv.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenCSV 读写封装，统一配置 Reader/Writer
 * <p>
 * 所有 CSV 读写必须通过此类，禁止在 CsvMetadataStore 外直接使用 CSVReader/CSVWriter
 */
public class CsvTableIO {

    // 固定表头定义
    private static final String[] VERSIONS_HEADER = {
        "id", "version_name", "timestamp", "description", "file_count", "total_size", "is_manual", "status"
    };

    private static final String[] BLOBS_HEADER = {
        "blob_key", "file_path", "file_hash", "file_size", "is_chunk", "state", "compressed_size"
    };

    private static final String[] FILE_INFO_HEADER = {
        "id", "version_id", "file_path", "file_hash", "file_size", "is_chunk", "blob_key"
    };

    /**
     * 获取表头
     */
    public static String[] headerFor(String table) {
        switch (table) {
            case "versions":
                return VERSIONS_HEADER;
            case "blobs":
                return BLOBS_HEADER;
            case "file_info":
                return FILE_INFO_HEADER;
            default:
                throw new IllegalArgumentException("未知表名: " + table);
        }
    }

    /**
     * 读取 CSV 文件所有数据行（跳过表头）
     *
     * @param csvPath    CSV 文件路径
     * @param hasHeader  是否有表头行
     * @return 数据行列表，每行为 String[]
     */
    public static List<String[]> readAll(Path csvPath, boolean hasHeader) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(csvPath)) {
            return rows;
        }
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVReader csvReader = createReader(reader)) {
            if (hasHeader) {
                csvReader.readNext(); // 跳过表头
            }
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 原子写入 CSV 文件（先写 .tmp 再 ATOMIC_MOVE）
     *
     * @param csvPath  目标文件路径
     * @param header   表头
     * @param rows     数据行
     */
    public static void writeAll(Path csvPath, String[] header, List<String[]> rows) throws IOException {
        Path tmpPath = csvPath.resolveSibling(csvPath.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8);
             ICSVWriter csvWriter = createWriter(writer)) {
            if (header != null) {
                csvWriter.writeNext(header);
            }
            for (String[] row : rows) {
                csvWriter.writeNext(row);
            }
        }
        Files.move(tmpPath, csvPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 创建 CSVReader（标准配置）
     */
    private static CSVReader createReader(Reader reader) {
        return new CSVReaderBuilder(reader)
            .withCSVParser(new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withIgnoreLeadingWhiteSpace(true)
                .build())
            .build();
    }

    /**
     * 创建 ICSVWriter（RFC 4180 标准配置）
     */
    private static ICSVWriter createWriter(Writer writer) {
        return new CSVWriterBuilder(writer)
            .withParser(new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build())
            .build();
    }
}
