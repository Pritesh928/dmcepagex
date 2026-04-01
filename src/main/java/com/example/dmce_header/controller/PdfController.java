package com.example.dmce_header.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin("*")
public class PdfController {

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processPdf(@RequestParam("pdf") MultipartFile pdfFile) throws IOException {

        PDDocument inputPdf = PDDocument.load(pdfFile.getInputStream());
        PDDocument outputPdf = new PDDocument();

        //Load images from response
        ClassPathResource headerRes = new ClassPathResource("header.png");
        ClassPathResource wmRes = new ClassPathResource("watermark.png");

        PDImageXObject headerImg = PDImageXObject.createFromByteArray(
                outputPdf, headerRes.getInputStream().readAllBytes(), "header"
        );

        PDImageXObject watermarkImg = PDImageXObject.createFromByteArray(
                outputPdf, wmRes.getInputStream().readAllBytes(), "watermark"
        );

        LayerUtility layerUtility = new LayerUtility(outputPdf);

        float headerHeight = 80;

        for (int i = 0; i < inputPdf.getNumberOfPages(); i++) {

            PDPage page = inputPdf.getPage(i);

            //size same as previous input(fix)
            PDRectangle pageSize = page.getMediaBox();
            PDPage newPage = new PDPage(pageSize);
            outputPdf.addPage(newPage);

            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();

            PDPageContentStream content = new PDPageContentStream(outputPdf, newPage);

            //Watermark content
            float wmWidth = pageWidth * 0.6f;
            float wmHeight = (watermarkImg.getHeight() * wmWidth) / watermarkImg.getWidth();

            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.12f); //watermark opacity adjustment 

            content.saveGraphicsState();
            content.setGraphicsStateParameters(gs);

            content.drawImage(
                    watermarkImg,
                    (pageWidth - wmWidth) / 2,
                    (pageHeight - wmHeight) / 2,
                    wmWidth,
                    wmHeight
            );

            content.restoreGraphicsState();

            // Header functions
            content.drawImage(
                    headerImg,
                    0,
                    pageHeight - headerHeight,
                    pageWidth,
                    headerHeight
            );

            //body of pdf as previous imput
            PDFormXObject form = layerUtility.importPageAsForm(inputPdf, i);

            content.saveGraphicsState();

            // content shift down function
            content.transform(Matrix.getTranslateInstance(0, -headerHeight));

            content.drawForm(form);

            content.restoreGraphicsState();

            content.close();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        outputPdf.save(baos);

        inputPdf.close();
        outputPdf.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=processed.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }
}