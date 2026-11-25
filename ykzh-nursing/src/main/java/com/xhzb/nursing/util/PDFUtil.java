package com.xhzb.nursing.util;

// PDFBox 3.x：使用 Loader 加载文档，IO 相关的 RandomAccessReadBuffer 位于 pdfbox-io 模块
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 文本提取工具
 * <p>
 * 说明：原先放在 ykzh-quartz 模块，但调用方在 ykzh-nursing，
 * 而 nursing 并不依赖 quartz，导致编译期“找不到符号 PDFUtil”。已迁移到本模块。
 * PDFBox 3.x 由 spring-ai-pdf-document-reader 传递引入，无需额外声明依赖。
 */
public class PDFUtil {

    public static String pdfToString(InputStream inputStream) {

        // PDDocument 和 InputStream 均实现 AutoCloseable，交给 try-with-resources 自动关闭
        try (InputStream in = inputStream;
             PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {

            // 创建一个PDFTextStripper实例来提取文本
            PDFTextStripper pdfStripper = new PDFTextStripper();

            // 从PDF文档中提取文本
            return pdfStripper.getText(document);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
