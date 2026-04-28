package com.docconvert.service;

import com.docconvert.dto.MetadataDTOs.*;
import com.docconvert.entity.User;
import com.docconvert.entity.UserFile;
import com.docconvert.repository.UserFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final UserFileRepository fileRepository;

    public FileMetadata extractMetadata(UserFile userFile) {
        String mimeType = userFile.getMimeType();
        String filePath = userFile.getFilePath();

        FileMetadata.FileMetadataBuilder builder = FileMetadata.builder()
                .fileId(userFile.getId())
                .fileName(userFile.getOriginalName())
                .fileSize(userFile.getFileSize())
                .formattedSize(formatFileSize(userFile.getFileSize()))
                .mimeType(mimeType)
                .fileType(userFile.getFileType());

        try {
            if (mimeType == null) {
                mimeType = detectMimeType(filePath);
            }

            if (mimeType.equals("application/pdf")) {
                return extractPdfMetadata(filePath, builder);
            } else if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) {
                return extractWordMetadata(filePath, builder);
            } else if (mimeType.contains("spreadsheetml") || mimeType.contains("ms-excel")) {
                return extractExcelMetadata(filePath, builder);
            } else if (mimeType.contains("presentationml") || mimeType.contains("ms-powerpoint")) {
                return extractPptMetadata(filePath, builder);
            } else if (mimeType.startsWith("image/")) {
                return extractImageMetadata(filePath, builder);
            } else {
                return extractBasicMetadata(filePath, builder);
            }
        } catch (Exception e) {
            log.error("Error extracting metadata for file {}: {}", userFile.getOriginalName(), e.getMessage());
            return builder.build();
        }
    }

    public FileMetadata extractMetadata(Long fileId, User user) {
        UserFile userFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!userFile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return extractMetadata(userFile);
    }

    public List<FileMetadata> extractMetadataForMultipleFiles(List<Long> fileIds, User user) {
        List<FileMetadata> metadataList = new ArrayList<>();
        for (Long fileId : fileIds) {
            try {
                metadataList.add(extractMetadata(fileId, user));
            } catch (Exception e) {
                log.error("Failed to extract metadata for file {}: {}", fileId, e.getMessage());
            }
        }
        return metadataList;
    }

    private FileMetadata extractPdfMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDDocumentInformation info = document.getDocumentInformation();

            builder.pageCount(document.getNumberOfPages())
                    .title(info.getTitle())
                    .author(info.getAuthor())
                    .subject(info.getSubject())
                    .keywords(info.getKeywords())
                    .creator(info.getCreator())
                    .producer(info.getProducer())
                    .isEncrypted(document.isEncrypted());

            // Creation and modification dates
            if (info.getCreationDate() != null) {
                builder.creationDate(formatDate(info.getCreationDate().getTime()));
            }
            if (info.getModificationDate() != null) {
                builder.modificationDate(formatDate(info.getModificationDate().getTime()));
            }

            // PDF version
            builder.pdfVersion(String.valueOf(document.getVersion()));

            // Page size (from first page)
            if (document.getNumberOfPages() > 0) {
                PDPage firstPage = document.getPage(0);
                PDRectangle mediaBox = firstPage.getMediaBox();
                builder.pageSize(String.format("%.2f x %.2f pts (%.2f x %.2f in)",
                        mediaBox.getWidth(), mediaBox.getHeight(),
                        mediaBox.getWidth() / 72, mediaBox.getHeight() / 72));
            }

            // Word count (approximate)
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String[] words = text.split("\\s+");
            builder.wordCount(words.length)
                    .characterCount(text.length());

            // Custom properties
            Map<String, String> customProps = new HashMap<>();
            for (String key : info.getMetadataKeys()) {
                String value = info.getCustomMetadataValue(key);
                if (value != null) {
                    customProps.put(key, value);
                }
            }
            if (!customProps.isEmpty()) {
                builder.customProperties(customProps);
            }

        } catch (Exception e) {
            log.error("Error extracting PDF metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private FileMetadata extractWordMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            if (filePath.toLowerCase().endsWith(".docx")) {
                XWPFDocument document = new XWPFDocument(fis);
                POIXMLProperties props = document.getProperties();
                POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();

                builder.title(coreProps.getTitle())
                        .author(coreProps.getCreator())
                        .subject(coreProps.getSubject())
                        .keywords(coreProps.getKeywords())
                        .creator(coreProps.getCreator());

                if (coreProps.getCreated() != null) {
                    builder.creationDate(formatDate(coreProps.getCreated()));
                }
                if (coreProps.getModified() != null) {
                    builder.modificationDate(formatDate(coreProps.getModified()));
                }

                // Count paragraphs and words
                int paragraphCount = 0;
                int wordCount = 0;
                int charCount = 0;

                for (XWPFParagraph para : document.getParagraphs()) {
                    paragraphCount++;
                    String text = para.getText();
                    if (text != null && !text.isEmpty()) {
                        wordCount += text.split("\\s+").length;
                        charCount += text.length();
                    }
                }

                builder.paragraphCount(paragraphCount)
                        .wordCount(wordCount)
                        .characterCount(charCount)
                        .pageCount(document.getProperties().getExtendedProperties()
                                .getUnderlyingProperties().getPages());

                document.close();
            }
        } catch (Exception e) {
            log.error("Error extracting Word metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private FileMetadata extractExcelMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            if (workbook instanceof XSSFWorkbook) {
                XSSFWorkbook xssfWorkbook = (XSSFWorkbook) workbook;
                POIXMLProperties props = xssfWorkbook.getProperties();
                POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();

                builder.title(coreProps.getTitle())
                        .author(coreProps.getCreator())
                        .subject(coreProps.getSubject())
                        .keywords(coreProps.getKeywords());

                if (coreProps.getCreated() != null) {
                    builder.creationDate(formatDate(coreProps.getCreated()));
                }
                if (coreProps.getModified() != null) {
                    builder.modificationDate(formatDate(coreProps.getModified()));
                }
            }

            // Sheet information
            int sheetCount = workbook.getNumberOfSheets();
            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < sheetCount; i++) {
                sheetNames.add(workbook.getSheetName(i));
            }

            builder.sheetCount(sheetCount)
                    .sheetNames(sheetNames);

        } catch (Exception e) {
            log.error("Error extracting Excel metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private FileMetadata extractPptMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try (FileInputStream fis = new FileInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            POIXMLProperties props = ppt.getProperties();
            POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();

            builder.title(coreProps.getTitle())
                    .author(coreProps.getCreator())
                    .subject(coreProps.getSubject())
                    .keywords(coreProps.getKeywords());

            if (coreProps.getCreated() != null) {
                builder.creationDate(formatDate(coreProps.getCreated()));
            }
            if (coreProps.getModified() != null) {
                builder.modificationDate(formatDate(coreProps.getModified()));
            }

            // Slide count
            builder.slideCount(ppt.getSlides().size());

            // Check for notes
            boolean hasNotes = false;
            for (XSLFSlide slide : ppt.getSlides()) {
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    hasNotes = true;
                    break;
                }
            }
            builder.hasNotes(hasNotes);

            // Page size
            java.awt.Dimension pageSize = ppt.getPageSize();
            builder.pageSize(String.format("%d x %d pixels", pageSize.width, pageSize.height));

        } catch (Exception e) {
            log.error("Error extracting PPT metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private FileMetadata extractImageMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try {
            File imageFile = new File(filePath);
            BufferedImage image = ImageIO.read(imageFile);

            if (image != null) {
                builder.width(image.getWidth())
                        .height(image.getHeight());

                // Color space
                int colorType = image.getType();
                String colorSpace = switch (colorType) {
                    case BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_3BYTE_BGR -> "RGB";
                    case BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_4BYTE_ABGR -> "RGBA";
                    case BufferedImage.TYPE_BYTE_GRAY -> "Grayscale";
                    case BufferedImage.TYPE_BYTE_BINARY -> "Binary";
                    default -> "Unknown";
                };
                builder.colorSpace(colorSpace);
            }

            // Try to get DPI and other metadata from ImageIO
            try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.setInput(iis);

                    IIOMetadata metadata = reader.getImageMetadata(0);
                    if (metadata != null) {
                        String[] formatNames = metadata.getMetadataFormatNames();
                        builder.compression(reader.getFormatName());
                    }
                    reader.dispose();
                }
            }

        } catch (Exception e) {
            log.error("Error extracting image metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private FileMetadata extractBasicMetadata(String filePath, FileMetadata.FileMetadataBuilder builder) {
        try {
            File file = new File(filePath);
            builder.modificationDate(formatDate(new Date(file.lastModified())));
        } catch (Exception e) {
            log.error("Error extracting basic metadata: {}", e.getMessage());
        }
        return builder.build();
    }

    private String detectMimeType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tiff", "tif" -> "image/tiff";
            default -> "application/octet-stream";
        };
    }

    private String formatDate(Date date) {
        if (date == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 Bytes";
        String[] sizes = {"Bytes", "KB", "MB", "GB", "TB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, i), sizes[i]);
    }
}