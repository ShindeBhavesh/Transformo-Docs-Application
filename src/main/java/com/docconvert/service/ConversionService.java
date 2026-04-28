package com.docconvert.service;

import com.docconvert.dto.FileDTOs.*;
import com.docconvert.dto.MetadataDTOs.FileMetadata;
import com.docconvert.entity.*;
import com.docconvert.repository.*;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.usermodel.PictureData.PictureType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversionService {

    private final ConversionHistoryRepository historyRepository;
    private final OcrResultRepository ocrResultRepository;
    private final UserFileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final MetadataService metadataService;

    @Value("${tesseract.data.path:tessdata}")
    private String tesseractDataPath;

    @Transactional
    public long clearUserHistory(User user) {
        long count = historyRepository.countByUserId(user.getId());
        historyRepository.deleteByUserId(user.getId());
        return count;
    }

    // ==================== WORD TO PDF ====================
    @Transactional
    public ConversionResponse wordToPdf(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "WORD_TO_PDF", "docx", "pdf");

        try {
            byte[] pdfBytes;
            String filePath = sourceFile.getFilePath();

            if (filePath.toLowerCase().endsWith(".docx")) {
                pdfBytes = convertDocxToPdfImproved(filePath);
            } else if (filePath.toLowerCase().endsWith(".doc")) {
                pdfBytes = convertDocToPdf(filePath);
            } else {
                throw new RuntimeException("Unsupported Word format. Supported: .doc, .docx");
            }

            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.(docx?|DOCX?)$", ".pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(pdfBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertDocxToPdfImproved(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(50, 50, 50, 50);

            // Process paragraphs with styling
            for (XWPFParagraph para : document.getParagraphs()) {
                if (para.getText().trim().isEmpty()) {
                    doc.add(new Paragraph("\n"));
                    continue;
                }

                Paragraph pdfPara = new Paragraph();

                for (XWPFRun run : para.getRuns()) {
                    String text = run.getText(0);
                    if (text != null) {
                        Text pdfText = new Text(text);

                        // Apply styling
                        if (run.isBold()) {
                            pdfText.setBold();
                        }
                        if (run.isItalic()) {
                            pdfText.setItalic();
                        }
                        if (run.getUnderline() != UnderlinePatterns.NONE) {
                            pdfText.setUnderline();
                        }
                        if (run.getFontSizeAsDouble() != null) {
                            pdfText.setFontSize(run.getFontSizeAsDouble().floatValue());
                        }

                        pdfPara.add(pdfText);
                    }
                }

                // Apply paragraph alignment
                switch (para.getAlignment()) {
                    case CENTER -> pdfPara.setTextAlignment(TextAlignment.CENTER);
                    case RIGHT -> pdfPara.setTextAlignment(TextAlignment.RIGHT);
                    case BOTH -> pdfPara.setTextAlignment(TextAlignment.JUSTIFIED);
                    default -> pdfPara.setTextAlignment(TextAlignment.LEFT);
                }

                doc.add(pdfPara);
            }

            // Process tables
            for (XWPFTable table : document.getTables()) {
                int numCols = table.getRow(0).getTableCells().size();
                Table pdfTable = new Table(UnitValue.createPercentArray(numCols)).useAllAvailableWidth();
                pdfTable.setBorder(new SolidBorder(ColorConstants.BLACK, 1));

                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        Cell pdfCell = new Cell();
                        pdfCell.add(new Paragraph(cell.getText()));
                        pdfCell.setBorder(new SolidBorder(ColorConstants.BLACK, 0.5f));
                        pdfCell.setPadding(5);
                        pdfTable.addCell(pdfCell);
                    }
                }
                doc.add(pdfTable);
                doc.add(new Paragraph("\n"));
            }

            // Process images
            for (XWPFPictureData pictureData : document.getAllPictures()) {
                try {
                    Image img = new Image(ImageDataFactory.create(pictureData.getData()));
                    img.setAutoScale(true);
                    img.setMaxWidth(500);
                    doc.add(img);
                } catch (Exception e) {
                    log.warn("Could not add image to PDF: {}", e.getMessage());
                }
            }

            doc.close();
            return baos.toByteArray();
        }
    }

    private byte[] convertDocToPdf(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(50, 50, 50, 50);

            for (String paragraph : text.split("\r\n|\r|\n")) {
                if (!paragraph.trim().isEmpty()) {
                    doc.add(new Paragraph(paragraph));
                }
            }

            doc.close();
            extractor.close();
            return baos.toByteArray();
        }
    }

    // ==================== PDF TO WORD ====================
    @Transactional
    public ConversionResponse pdfToWord(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "PDF_TO_WORD", "pdf", "docx");

        try {
            byte[] docxBytes = convertPdfToDocxImproved(sourceFile.getFilePath());
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", ".docx");
            UserFile convertedFile = fileStorageService.storeConvertedFile(docxBytes, outputFileName,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertPdfToDocxImproved(String filePath) throws Exception {
        try (PDDocument pdfDocument = Loader.loadPDF(new File(filePath));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            XWPFDocument document = new XWPFDocument();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdfDocument);

            for (int i = 0; i < pdfDocument.getNumberOfPages(); i++) {
                // Add page header
                XWPFParagraph pageHeader = document.createParagraph();
                pageHeader.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun headerRun = pageHeader.createRun();
                headerRun.setText("--- Page " + (i + 1) + " ---");
                headerRun.setBold(true);
                headerRun.setFontSize(10);
                headerRun.setColor("808080");

                // Extract text from this page
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(pdfDocument);

                // Split into paragraphs and add to document
                String[] paragraphs = pageText.split("\n\n");
                for (String paraText : paragraphs) {
                    if (!paraText.trim().isEmpty()) {
                        XWPFParagraph para = document.createParagraph();
                        XWPFRun run = para.createRun();
                        run.setText(paraText.trim());
                    }
                }

                // Try to extract and add images from the page
                try {
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, 72);
                    // Only add if page has minimal text (likely image-based)
                    if (pageText.trim().length() < 50) {
                        ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                        ImageIO.write(pageImage, "PNG", imgBaos);

                        XWPFParagraph imgPara = document.createParagraph();
                        XWPFRun imgRun = imgPara.createRun();
                        imgRun.addPicture(new ByteArrayInputStream(imgBaos.toByteArray()),
                                XWPFDocument.PICTURE_TYPE_PNG, "page_" + (i + 1) + ".png",
                                Units.toEMU(500), Units.toEMU(700));
                    }
                } catch (Exception e) {
                    log.warn("Could not add page image: {}", e.getMessage());
                }

                // Add page break between pages (except last)
                if (i < pdfDocument.getNumberOfPages() - 1) {
                    XWPFParagraph breakPara = document.createParagraph();
                    breakPara.setPageBreak(true);
                }
            }

            document.write(baos);
            document.close();
            return baos.toByteArray();
        }
    }

    // ==================== PDF COMPRESSION (FIXED) ====================
    @Transactional
    public ConversionResponse compressPdf(UserFile sourceFile, User user, String compressionLevel) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "COMPRESS_PDF", "pdf", "pdf");

        try {
            byte[] compressedBytes = compressPdfFileImproved(sourceFile.getFilePath(), compressionLevel);
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "_compressed.pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(compressedBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] compressPdfFileImproved(String filePath, String level) throws Exception {
        // Compression settings based on level
        float imageQuality;
        int imageDpi;

        switch (level.toLowerCase()) {
            case "high":
                imageQuality = 0.3f;
                imageDpi = 72;
                break;
            case "low":
                imageQuality = 0.8f;
                imageDpi = 200;
                break;
            case "medium":
            default:
                imageQuality = 0.5f;
                imageDpi = 150;
                break;
        }

        try (PDDocument document = Loader.loadPDF(new File(filePath));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDFRenderer renderer = new PDFRenderer(document);

            // Create new compressed PDF
            PdfWriter writer = new PdfWriter(baos, new WriterProperties()
                    .setCompressionLevel(9)
                    .setFullCompressionMode(true));
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();

                // Render page as image
                BufferedImage pageImage = renderer.renderImageWithDPI(i, imageDpi, ImageType.RGB);

                // Compress the image
                ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();

                // Use Thumbnailator for better compression
                Thumbnails.of(pageImage)
                        .scale(1.0)
                        .outputQuality(imageQuality)
                        .outputFormat("jpg")
                        .toOutputStream(imgBaos);

                // Add compressed image to new PDF
                Image img = new Image(ImageDataFactory.create(imgBaos.toByteArray()));

                // Set page size based on original
                PageSize pageSize = new PageSize(mediaBox.getWidth(), mediaBox.getHeight());
                pdfDoc.addNewPage(pageSize);

                img.setFixedPosition(i + 1, 0, 0);
                img.setWidth(pageSize.getWidth());
                img.setHeight(pageSize.getHeight());
                doc.add(img);
            }

            doc.close();

            byte[] result = baos.toByteArray();

            // If compressed file is larger, try alternative compression
            File originalFile = new File(filePath);
            if (result.length >= originalFile.length()) {
                log.info("Image-based compression not effective, using stream compression only");
                return compressPdfStreams(filePath);
            }

            return result;
        }
    }

    private byte[] compressPdfStreams(String filePath) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(filePath);
            PdfWriter writer = new PdfWriter(baos, new WriterProperties()
                    .setCompressionLevel(9)
                    .setFullCompressionMode(true));

            PdfDocument pdfDoc = new PdfDocument(reader, writer);
            pdfDoc.close();

            return baos.toByteArray();
        }
    }

    // ==================== EXCEL TO PDF (IMPROVED) ====================
    @Transactional
    public ConversionResponse excelToPdf(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "EXCEL_TO_PDF", "xlsx", "pdf");

        try {
            byte[] pdfBytes = convertExcelToPdfImproved(sourceFile.getFilePath());
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.(xlsx?|XLSX?)$", ".pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(pdfBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertExcelToPdfImproved(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc, PageSize.A4.rotate()); // Landscape for Excel
            doc.setMargins(30, 30, 30, 30);

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);

                // Sheet title
                doc.add(new Paragraph("Sheet: " + sheet.getSheetName())
                        .setBold()
                        .setFontSize(14)
                        .setMarginBottom(10));

                if (sheet.getPhysicalNumberOfRows() == 0) {
                    doc.add(new Paragraph("(Empty sheet)").setItalic());
                    continue;
                }

                // Determine number of columns
                int maxCols = 0;
                for (Row row : sheet) {
                    if (row.getLastCellNum() > maxCols) {
                        maxCols = row.getLastCellNum();
                    }
                }

                if (maxCols == 0) continue;

                // Create table
                Table table = new Table(UnitValue.createPercentArray(maxCols)).useAllAvailableWidth();
                table.setFontSize(8);

                // Process merged regions
                List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();

                for (Row row : sheet) {
                    for (int colIdx = 0; colIdx < maxCols; colIdx++) {
                        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIdx);

                        // Check if this cell is part of a merged region
                        boolean isPartOfMerge = false;
                        for (CellRangeAddress region : mergedRegions) {
                            if (region.isInRange(row.getRowNum(), colIdx) &&
                                    !(region.getFirstRow() == row.getRowNum() && region.getFirstColumn() == colIdx)) {
                                isPartOfMerge = true;
                                break;
                            }
                        }

                        if (isPartOfMerge) continue;

                        Cell pdfCell = new Cell();
                        String cellValue = getCellValueAsString(cell);
                        pdfCell.add(new Paragraph(cellValue));

                        // Apply cell styling
                        if (cell != null) {
                            CellStyle style = cell.getCellStyle();
                            if (style != null) {
                                // Background color
                                short bgColor = style.getFillForegroundColor();
                                if (bgColor != IndexedColors.AUTOMATIC.getIndex()) {
                                    pdfCell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                                }
                            }
                        }

                        pdfCell.setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
                        pdfCell.setPadding(3);
                        table.addCell(pdfCell);
                    }
                }

                doc.add(table);

                // Page break between sheets (except last)
                if (sheetIndex < workbook.getNumberOfSheets() - 1) {
                    doc.add(new AreaBreak());
                }
            }

            doc.close();
            return baos.toByteArray();
        }
    }

    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";

        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toString();
                    }
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        yield String.valueOf((long) value);
                    }
                    yield String.valueOf(value);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        try {
                            yield cell.getStringCellValue();
                        } catch (Exception e2) {
                            yield cell.getCellFormula();
                        }
                    }
                }
                case BLANK -> "";
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== PPT TO PDF (IMPROVED) ====================
    @Transactional
    public ConversionResponse pptToPdf(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "PPT_TO_PDF", "pptx", "pdf");

        try {
            byte[] pdfBytes = convertPptToPdfImproved(sourceFile.getFilePath());
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.(pptx?|PPTX?)$", ".pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(pdfBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertPptToPdfImproved(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            java.awt.Dimension pageSize = ppt.getPageSize();

            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);

            // Set page size based on slide dimensions
            PageSize pdfPageSize = new PageSize(pageSize.width, pageSize.height);
            Document doc = new Document(pdfDoc, pdfPageSize);
            doc.setMargins(0, 0, 0, 0);

            List<XSLFSlide> slides = ppt.getSlides();

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);

                // Render slide as image
                BufferedImage slideImage = new BufferedImage(
                        pageSize.width, pageSize.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = slideImage.createGraphics();

                // Set rendering hints for better quality
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // White background
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, pageSize.width, pageSize.height);

                // Draw the slide
                slide.draw(graphics);
                graphics.dispose();

                // Convert to bytes
                ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                ImageIO.write(slideImage, "PNG", imgBaos);

                // Add to PDF
                Image img = new Image(ImageDataFactory.create(imgBaos.toByteArray()));
                img.setFixedPosition(0, 0);
                img.setWidth(pdfPageSize.getWidth());
                img.setHeight(pdfPageSize.getHeight());

                if (i > 0) {
                    pdfDoc.addNewPage(pdfPageSize);
                }
                doc.add(img);
            }

            doc.close();
            return baos.toByteArray();
        }
    }

    // ==================== JPG TO PDF ====================
    @Transactional
    public ConversionResponse jpgToPdf(List<UserFile> sourceFiles, User user) throws Exception {
        UserFile firstFile = sourceFiles.get(0);
        ConversionHistory history = createHistory(user, firstFile, "JPG_TO_PDF", "jpg", "pdf");

        try {
            byte[] pdfBytes = convertImagesToPdfImproved(sourceFiles);
            String outputFileName = "images_" + System.currentTimeMillis() + ".pdf";
            UserFile convertedFile = fileStorageService.storeConvertedFile(pdfBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, firstFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertImagesToPdfImproved(List<UserFile> imageFiles) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);

            for (UserFile imageFile : imageFiles) {
                BufferedImage bufferedImage = ImageIO.read(new File(imageFile.getFilePath()));

                if (bufferedImage == null) {
                    log.warn("Could not read image: {}", imageFile.getOriginalName());
                    continue;
                }

                // Create page size based on image dimensions
                float width = bufferedImage.getWidth();
                float height = bufferedImage.getHeight();

                // Scale to fit within reasonable bounds while maintaining aspect ratio
                float maxWidth = 595; // A4 width in points
                float maxHeight = 842; // A4 height in points

                if (width > maxWidth || height > maxHeight) {
                    float scale = Math.min(maxWidth / width, maxHeight / height);
                    width *= scale;
                    height *= scale;
                }

                PageSize pageSize = new PageSize(width + 50, height + 50);
                pdfDoc.addNewPage(pageSize);

                Image image = new Image(ImageDataFactory.create(imageFile.getFilePath()));
                image.setAutoScale(true);
                image.setMaxWidth(width);
                image.setMaxHeight(height);

                doc.add(image);
            }

            doc.close();
            return baos.toByteArray();
        }
    }

    // ==================== PDF OCR ====================
    @Transactional
    public OcrResponse performOcr(UserFile sourceFile, User user) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            String extractedText;
            double confidence = 0.0;

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tesseractDataPath);
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(1);
            tesseract.setOcrEngineMode(1);

            if (sourceFile.getMimeType() != null && sourceFile.getMimeType().startsWith("image/")) {
                BufferedImage image = ImageIO.read(new File(sourceFile.getFilePath()));
                extractedText = tesseract.doOCR(image);
            } else {
                try (PDDocument document = Loader.loadPDF(new File(sourceFile.getFilePath()))) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    extractedText = stripper.getText(document);

                    if (extractedText.trim().length() < 50) {
                        log.info("PDF appears to be image-based, performing OCR");
                        extractedText = ocrPdfPages(document, tesseract);
                    }
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;

            OcrResult ocrResult = OcrResult.builder()
                    .user(user)
                    .sourceFile(sourceFile)
                    .extractedText(extractedText)
                    .confidence(confidence)
                    .language("eng")
                    .processingTimeMs(processingTime)
                    .build();

            ocrResultRepository.save(ocrResult);

            ConversionHistory history = createHistory(user, sourceFile, "PDF_OCR", "pdf", "txt");
            history.setStatus(ConversionHistory.ConversionStatus.COMPLETED);
            history.setCompletedAt(LocalDateTime.now());
            historyRepository.save(history);

            return OcrResponse.builder()
                    .id(ocrResult.getId())
                    .extractedText(extractedText)
                    .confidence(confidence)
                    .language("eng")
                    .processingTimeMs(processingTime)
                    .sourceFileName(sourceFile.getOriginalName())
                    .build();

        } catch (TesseractException e) {
            log.error("OCR failed: {}", e.getMessage());
            throw new RuntimeException("OCR processing failed: " + e.getMessage(), e);
        }
    }

    private String ocrPdfPages(PDDocument document, Tesseract tesseract) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 300, ImageType.RGB);
            String pageText = tesseract.doOCR(image);
            text.append("--- Page ").append(i + 1).append(" ---\n");
            text.append(pageText).append("\n\n");
        }

        return text.toString();
    }

    // ==================== MERGE PDFs ====================
    @Transactional
    public ConversionResponse mergePdfs(List<UserFile> sourceFiles, User user) throws Exception {
        UserFile firstFile = sourceFiles.get(0);
        ConversionHistory history = createHistory(user, firstFile, "MERGE_PDF", "pdf", "pdf");

        try {
            byte[] mergedBytes = mergePdfFiles(sourceFiles);
            String outputFileName = "merged_" + System.currentTimeMillis() + ".pdf";
            UserFile convertedFile = fileStorageService.storeConvertedFile(mergedBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, firstFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] mergePdfFiles(List<UserFile> files) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument mergedPdf = new PdfDocument(writer);

            for (UserFile file : files) {
                try (PdfDocument sourcePdf = new PdfDocument(new PdfReader(file.getFilePath()))) {
                    sourcePdf.copyPagesTo(1, sourcePdf.getNumberOfPages(), mergedPdf);
                }
            }

            mergedPdf.close();
            return baos.toByteArray();
        }
    }

    // ==================== SPLIT PDF ====================
    @Transactional
    public ConversionResponse splitPdf(UserFile sourceFile, User user, String pageRange) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "SPLIT_PDF", "pdf", "pdf");

        try {
            List<Integer> pages = parsePageRange(pageRange);
            byte[] splitBytes = splitPdfFile(sourceFile.getFilePath(), pages);
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "_split.pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(splitBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] splitPdfFile(String filePath, List<Integer> pages) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(filePath);
            PdfDocument sourcePdf = new PdfDocument(reader);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument newPdf = new PdfDocument(writer);

            int totalPages = sourcePdf.getNumberOfPages();
            for (int page : pages) {
                if (page > 0 && page <= totalPages) {
                    sourcePdf.copyPagesTo(page, page, newPdf);
                }
            }

            newPdf.close();
            sourcePdf.close();
            return baos.toByteArray();
        }
    }

    // ==================== DELETE PDF PAGES ====================
    @Transactional
    public ConversionResponse deletePdfPages(UserFile sourceFile, User user, String pageRange) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "DELETE_PAGES", "pdf", "pdf");

        try {
            List<Integer> pagesToDelete = parsePageRange(pageRange);
            byte[] resultBytes = deletePagesFromPdf(sourceFile.getFilePath(), pagesToDelete);
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "_edited.pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(resultBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] deletePagesFromPdf(String filePath, List<Integer> pagesToDelete) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(filePath);
            PdfDocument sourcePdf = new PdfDocument(reader);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument newPdf = new PdfDocument(writer);

            Set<Integer> deleteSet = new HashSet<>(pagesToDelete);
            int totalPages = sourcePdf.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                if (!deleteSet.contains(i)) {
                    sourcePdf.copyPagesTo(i, i, newPdf);
                }
            }

            if (newPdf.getNumberOfPages() == 0) {
                throw new RuntimeException("Cannot delete all pages from PDF");
            }

            newPdf.close();
            sourcePdf.close();
            return baos.toByteArray();
        }
    }

    // ==================== EXTRACT PDF PAGES ====================
    @Transactional
    public ConversionResponse extractPdfPages(UserFile sourceFile, User user, String pageRange) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "EXTRACT_PAGES", "pdf", "pdf");

        try {
            List<Integer> pages = parsePageRange(pageRange);
            byte[] extractedBytes = splitPdfFile(sourceFile.getFilePath(), pages);
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "_extracted.pdf");
            UserFile convertedFile = fileStorageService.storeConvertedFile(extractedBytes, outputFileName, "application/pdf", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    // ==================== PDF TO PPT ====================
    @Transactional
    public ConversionResponse pdfToPpt(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "PDF_TO_PPT", "pdf", "pptx");

        try {
            byte[] pptBytes = convertPdfToPptImproved(sourceFile.getFilePath());
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", ".pptx");
            UserFile convertedFile = fileStorageService.storeConvertedFile(pptBytes, outputFileName,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertPdfToPptImproved(String filePath) throws Exception {
        try (PDDocument pdfDocument = Loader.loadPDF(new File(filePath));
             XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDFRenderer renderer = new PDFRenderer(pdfDocument);

            // Set slide size to match PDF page size
            if (pdfDocument.getNumberOfPages() > 0) {
                PDRectangle pageSize = pdfDocument.getPage(0).getMediaBox();
                ppt.setPageSize(new java.awt.Dimension(
                        (int) pageSize.getWidth(),
                        (int) pageSize.getHeight()));
            }

            for (int i = 0; i < pdfDocument.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", imgBaos);

                XSLFSlide slide = ppt.createSlide();
                XSLFPictureData pictureData = ppt.addPicture(imgBaos.toByteArray(), PictureType.PNG);
                XSLFPictureShape picture = slide.createPicture(pictureData);

                // Fill the entire slide
                java.awt.Dimension slideSize = ppt.getPageSize();
                picture.setAnchor(new java.awt.Rectangle(0, 0, slideSize.width, slideSize.height));
            }

            ppt.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== PDF TO JPG (FIXED - returns all pages as ZIP) ====================
    @Transactional
    public ConversionResponse pdfToJpg(UserFile sourceFile, User user) throws Exception {
        ConversionHistory history = createHistory(user, sourceFile, "PDF_TO_JPG", "pdf", "zip");

        try {
            byte[] zipBytes = convertPdfToImagesZip(sourceFile);
            String outputFileName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "_images.zip");
            UserFile convertedFile = fileStorageService.storeConvertedFile(zipBytes, outputFileName, "application/zip", user);

            return completeConversionWithMetadata(history, convertedFile, sourceFile);
        } catch (Exception e) {
            failConversion(history, e.getMessage());
            throw e;
        }
    }

    private byte[] convertPdfToImagesZip(UserFile sourceFile) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(sourceFile.getFilePath()));
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(baos)) {

            PDFRenderer renderer = new PDFRenderer(document);
            String baseName = sourceFile.getOriginalName().replaceAll("\\.pdf$", "");

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150, ImageType.RGB);

                ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                ImageIO.write(image, "JPEG", imgBaos);

                String entryName = String.format("%s_page_%d.jpg", baseName, i + 1);
                ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                entry.setSize(imgBaos.size());

                zipOut.putArchiveEntry(entry);
                zipOut.write(imgBaos.toByteArray());
                zipOut.closeArchiveEntry();
            }

            zipOut.finish();
            return baos.toByteArray();
        }
    }

    // ==================== HELPER METHODS ====================

    public List<ConversionResponse> getConversionHistory(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return historyRepository.findByUserIdOrderByStartedAtDesc(user.getId())
                .stream()
                .map(h -> ConversionResponse.builder()
                        .id(h.getId())
                        .conversionType(h.getConversionType())
                        .sourceFileName(h.getSourceFileName())
                        .convertedFileName(h.getConvertedFileName())
                        .sourceFileSize(h.getSourceFileSize())
                        .convertedFileSize(h.getConvertedFileSize())
                        .status(h.getStatus().name())
                        .startedAt(h.getStartedAt().format(formatter))
                        .completedAt(h.getCompletedAt() != null ? h.getCompletedAt().format(formatter) : null)
                        .build())
                .collect(Collectors.toList());
    }

    public DashboardStats getStats(User user) {
        long conversions = historyRepository.countByUserId(user.getId());
        long ocrScans = ocrResultRepository.countByUserId(user.getId());

        return DashboardStats.builder()
                .totalConversions(conversions)
                .ocrScans(ocrScans)
                .build();
    }

    private ConversionHistory createHistory(User user, UserFile sourceFile, String type, String sourceFormat, String targetFormat) {
        ConversionHistory history = ConversionHistory.builder()
                .user(user)
                .sourceFile(sourceFile)
                .conversionType(type)
                .sourceFormat(sourceFormat)
                .targetFormat(targetFormat)
                .sourceFileName(sourceFile.getOriginalName())
                .sourceFileSize(sourceFile.getFileSize())
                .status(ConversionHistory.ConversionStatus.PROCESSING)
                .build();
        return historyRepository.save(history);
    }

    private ConversionResponse completeConversion(ConversionHistory history, UserFile convertedFile) {
        history.setConvertedFile(convertedFile);
        history.setConvertedFileName(convertedFile.getOriginalName());
        history.setConvertedFileSize(convertedFile.getFileSize());
        history.setStatus(ConversionHistory.ConversionStatus.COMPLETED);
        history.setCompletedAt(LocalDateTime.now());
        historyRepository.save(history);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return ConversionResponse.builder()
                .id(history.getId())
                .conversionType(history.getConversionType())
                .sourceFileName(history.getSourceFileName())
                .convertedFileName(convertedFile.getOriginalName())
                .sourceFileSize(history.getSourceFileSize())
                .convertedFileSize(convertedFile.getFileSize())
                .status("COMPLETED")
                .downloadUrl("/api/files/download/" + convertedFile.getId())
                .startedAt(history.getStartedAt().format(formatter))
                .completedAt(history.getCompletedAt().format(formatter))
                .build();
    }

    private ConversionResponse completeConversionWithMetadata(ConversionHistory history, UserFile convertedFile, UserFile sourceFile) {
        ConversionResponse response = completeConversion(history, convertedFile);

        // Extract and log metadata
        try {
            FileMetadata sourceMetadata = metadataService.extractMetadata(sourceFile);
            FileMetadata convertedMetadata = metadataService.extractMetadata(convertedFile);
            log.info("Conversion completed. Source: {} ({} pages), Converted: {} ({} bytes)",
                    sourceFile.getOriginalName(),
                    sourceMetadata.getPageCount(),
                    convertedFile.getOriginalName(),
                    convertedFile.getFileSize());
        } catch (Exception e) {
            log.warn("Could not extract metadata: {}", e.getMessage());
        }

        return response;
    }

    private void failConversion(ConversionHistory history, String errorMessage) {
        history.setStatus(ConversionHistory.ConversionStatus.FAILED);
        history.setErrorMessage(errorMessage);
        history.setCompletedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    private List<Integer> parsePageRange(String range) {
        List<Integer> pages = new ArrayList<>();
        if (range == null || range.isEmpty()) {
            pages.add(1);
            return pages;
        }

        String[] parts = range.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] rangeParts = part.split("-");
                int start = Integer.parseInt(rangeParts[0].trim());
                int end = Integer.parseInt(rangeParts[1].trim());
                for (int i = start; i <= end; i++) {
                    pages.add(i);
                }
            } else {
                pages.add(Integer.parseInt(part));
            }
        }

        return pages.stream().distinct().sorted().collect(Collectors.toList());
    }
}