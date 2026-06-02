package com.emp.management.service;

import com.emp.management.entity.Certificate;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.CertificateRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Generates a professional Certificate of Completion PDF.
 *
 * Layout:
 *   [HEADER IMAGE  — contains branding + CERTIFICATE OF COMPLETION + AWARDED TO]
 *       {Employee Name}
 *       For Successfully Completing The Course
 *       {Course Name}
 *       Date of Completion: {date}
 *       Certificate No: {certNo}
 *   [FOOTER IMAGE  — contains Director signature + company details]
 *
 * Nothing from the header/footer images is duplicated in the rendered text.
 */
@Service
@RequiredArgsConstructor
public class CertificatePdfService {

    private final CertificateRepository certificateRepository;

    // Brand palette — aligned with the gold/dark-navy certificate imagery
    private static final DeviceRgb C_DARK    = new DeviceRgb( 24,  20,  38);
    private static final DeviceRgb C_GOLD    = new DeviceRgb(202, 167,  99);
    private static final DeviceRgb C_GOLD_DK = new DeviceRgb(150, 118,  40);
    private static final DeviceRgb C_GOLD_LT = new DeviceRgb(232, 203, 122);
    private static final DeviceRgb C_WHITE   = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb C_GRAY    = new DeviceRgb(100, 100, 100);
    private static final DeviceRgb C_LT_GRAY = new DeviceRgb(160, 160, 160);

    // ── Public entry points ────────────────────────────────────────────────

    public byte[] generateByCertNumber(String certNumber) throws IOException {
        Certificate cert = certificateRepository.findByCertificateNumber(certNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found: " + certNumber));
        return generate(cert);
    }

    public byte[] generateById(Long id) throws IOException {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found: " + id));
        return generate(cert);
    }

    // ── Internal pipeline ──────────────────────────────────────────────────

    private byte[] generate(Certificate cert) throws IOException {
        String empName = cert.getEmployee() != null
                ? cert.getEmployee().getFirstName() + " " + cert.getEmployee().getLastName() : "—";
        String course  = cert.getCourse()    != null ? cert.getCourse().getTitle() : "—";
        String certNo  = cert.getCertificateNumber();
        String date    = cert.getIssuedAt()  != null
                ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "—";
        return buildPdf(empName, course, date, certNo);
    }

    private byte[] buildPdf(String empName, String course, String date, String certNo)
            throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Landscape A4
        PageSize ps = PageSize.A4.rotate();
        float W  = ps.getWidth();    // 841.89 pt
        float H  = ps.getHeight();   // 595.28 pt
        float CX = W / 2f;

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            pdf.setDefaultPageSize(ps);
            PdfPage page = pdf.addNewPage();
            PdfCanvas cv = new PdfCanvas(page);

            // Fonts
            PdfFont fName   = PdfFontFactory.createFont(StandardFonts.TIMES_BOLDITALIC); // employee name
            PdfFont fCourse = PdfFontFactory.createFont(StandardFonts.TIMES_BOLDITALIC);       // course name
            PdfFont fBody   = PdfFontFactory.createFont(StandardFonts.HELVETICA);        // body labels
            PdfFont fBodyBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD); // semi-bold labels

            // ── Step 1: white base ───────────────────────────────────────
            cv.setFillColor(C_WHITE).rectangle(0, 0, W, H).fill();

            // ── Step 2: header image (top, full-width) ────────────────────
            //    Contains:  SAS KPO SERVICES branding
            //               CERTIFICATE OF COMPLETION
            //               AWARDED TO
            float headerH = placeImage(cv, "static/header_cert.png", W, H, true,  H * 0.48f);

            // ── Step 3: footer image (bottom, full-width) ─────────────────
            //    Contains:  Director signature
            //               Director name + company title
            //               Decorative branding elements
            float footerH = placeImage(cv, "static/footer_new.png",  W, H, false, H * 0.28f);

            // ── Step 4: white fill for content zone ───────────────────────
            float contentTop    = H - headerH;   // just below header image
            float contentBottom = footerH;        // just above footer image
            cv.setFillColor(C_WHITE)
              .rectangle(0, contentBottom, W, contentTop - contentBottom)
              .fill();

            // ── Step 5: dynamic certificate content ───────────────────────
            //    Everything below is centred; positions are derived from
            //    actual contentTop / contentBottom so the layout adapts
            //    regardless of how the images scale.

            float span = contentTop - contentBottom;   // usable vertical space

            Canvas mc = new Canvas(cv, new Rectangle(0, 0, W, H));

            // — Employee Name ————————————————————————————————————————————
            float nameFontSize = empName.length() > 35 ? 30f
                               : empName.length() > 22 ? 34f
                               : 38f;
            float nameY = contentTop - span * 0.35f;
            mc.showTextAligned(
                    new Paragraph(empName)
                            .setFont(fName).setFontSize(nameFontSize)
                            .setFontColor(C_GOLD).setMargin(0),
                    CX, nameY, TextAlignment.CENTER);

            // — "For Successfully Completing The Course" —————————————————
            float forY = nameY - nameFontSize * 0.30f - 18f;
            mc.showTextAligned(
                    new Paragraph("For Successfully Completing The Course")
                            .setFont(fBodyBold).setFontSize(14f)
                            .setFontColor(C_DARK).setMargin(0),
                    CX, forY, TextAlignment.CENTER);

            // — Course Name ——————————————————————————————————————————————
            float courseFontSize = course.length() > 50 ? 15f
                                 : course.length() > 35 ? 18f
                                 : 22f;
            float courseY = forY - 32f;
            mc.showTextAligned(
                    new Paragraph(course)
                            .setFont(fCourse).setFontSize(courseFontSize)
                            .setFontColor(C_DARK).setMargin(0),
                    CX, courseY, TextAlignment.CENTER);

            // Light separator below course
            float sep2Y = courseY - 20f;
            cv.setStrokeColor(C_GOLD_LT).setLineWidth(0.4f)
              .moveTo(CX - 130f, sep2Y).lineTo(CX + 130f, sep2Y).stroke();

            // — Date of Completion ————————————————————————————————————————
            float dateY = sep2Y - 20f;
            mc.showTextAligned(
                    new Paragraph("Date of Completion:     " + date)
                            .setFont(fBody).setFontSize(13f)
                            .setFontColor(new DeviceRgb(0, 0, 0)).setMargin(0),
                    CX, dateY, TextAlignment.CENTER);

            // — Certificate Number ————————————————————————————————————————
            float certNoY = dateY - 22f;
            mc.showTextAligned(
                    new Paragraph("Certificate No:     " + certNo)
                            .setFont(fBody).setFontSize(12f)
                            .setFontColor(new DeviceRgb(0, 0, 0)).setMargin(0),
                    CX, certNoY, TextAlignment.CENTER);

            mc.close();
        }

        return out.toByteArray();
    }

    // ── Utility: load + place image, returns actual height used ───────────

    /**
     * Loads an image from the classpath and renders it full-width on the page.
     *
     * @param atTop  true  → image anchored to top   (y = H - imageH)
     *               false → image anchored to bottom (y = 0)
     * @param maxH   maximum height cap (prevents images from consuming too much space)
     * @return actual rendered height in points
     */
    private float placeImage(PdfCanvas cv, String classpathResource,
                              float W, float H, boolean atTop, float maxH) {
        try {
            byte[] bytes = new ClassPathResource(classpathResource)
                    .getInputStream().readAllBytes();
            ImageData img = ImageDataFactory.create(bytes);

            // Maintain natural aspect ratio
            float h = W * img.getHeight() / img.getWidth();
            if (h > maxH) h = maxH;

            float y = atTop ? H - h : 0f;
            cv.addImageFittedIntoRectangle(img, new Rectangle(0, y, W, h), false);
            return h;
        } catch (Exception ignored) {
            return 0f;   // image is optional — layout degrades gracefully
        }
    }
}
