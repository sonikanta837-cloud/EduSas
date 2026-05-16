package com.emp.management.service;

import com.emp.management.entity.Certificate;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.CertificateRepository;
import com.itextpdf.io.font.constants.StandardFonts;
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

@Service
@RequiredArgsConstructor
public class CertificatePdfService {

    private final CertificateRepository certificateRepository;

    // Color palette matching SAS KPO logo aesthetic (dark navy + gold)
    private static final DeviceRgb C_DARK     = new DeviceRgb(28,  28,  46);
    private static final DeviceRgb C_GOLD     = new DeviceRgb(201, 168, 76);
    private static final DeviceRgb C_GOLD_LT  = new DeviceRgb(232, 203, 122);
    private static final DeviceRgb C_GOLD_DK  = new DeviceRgb(150, 118, 40);
    private static final DeviceRgb C_WHITE     = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb C_OFF_WHITE = new DeviceRgb(250, 248, 242);
    private static final DeviceRgb C_GRAY      = new DeviceRgb(110, 110, 110);
    private static final DeviceRgb C_LT_GRAY   = new DeviceRgb(175, 175, 175);

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

    private byte[] generate(Certificate cert) throws IOException {
        String empName = cert.getEmployee() != null
                ? cert.getEmployee().getFirstName() + " " + cert.getEmployee().getLastName() : "—";
        String course  = cert.getCourse() != null ? cert.getCourse().getTitle() : "—";
        String certNo  = cert.getCertificateNumber();
        String date    = cert.getIssuedAt() != null
                ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) : "—";
        return buildPdf(empName, course, date, certNo);
    }

    private byte[] buildPdf(String empName, String course, String date, String certNo) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PageSize ps = PageSize.A4.rotate();   // Landscape A4
        float W  = ps.getWidth();   // 841.89 pt
        float H  = ps.getHeight();  // 595.28 pt
        float CX = W / 2f;          // horizontal centre

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            pdf.setDefaultPageSize(ps);
            PdfPage page = pdf.addNewPage();
            PdfCanvas cv  = new PdfCanvas(page);

            PdfFont fHBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fHReg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont fTBold = PdfFontFactory.createFont(StandardFonts.TIMES_BOLD);
            PdfFont fTItal = PdfFontFactory.createFont(StandardFonts.TIMES_ITALIC);

            // ── 1. WHITE BASE + WARM OFF-WHITE INNER ────────────────────────
            cv.setFillColor(C_WHITE).rectangle(0, 0, W, H).fill();
            float bm = 10f;
            cv.setFillColor(C_OFF_WHITE).rectangle(bm, bm, W - 2*bm, H - 2*bm).fill();

            // ── 2. GOLD DOUBLE BORDER ─────────────────────────────────────
            cv.setStrokeColor(C_GOLD).setLineWidth(2.5f)
              .rectangle(bm, bm, W - 2*bm, H - 2*bm).stroke();
            float ib = bm + 7f;
            cv.setStrokeColor(C_GOLD_LT).setLineWidth(0.5f)
              .rectangle(ib, ib, W - 2*ib, H - 2*ib).stroke();

            // ── 3. DARK HEADER BAR ────────────────────────────────────────
            float hdrH = 120f;
            float hdrY = H - bm - hdrH;
            cv.setFillColor(C_DARK).rectangle(bm, hdrY, W - 2*bm, hdrH).fill();

            // Gold accent stripes below header
            cv.setFillColor(C_GOLD).rectangle(bm, hdrY - 0.5f, W - 2*bm, 4f).fill();
            cv.setFillColor(C_GOLD_LT).rectangle(bm, hdrY - 4f, W - 2*bm, 1.5f).fill();

            // ── 4. HEADER: LOGO + TEXT BLOCK (centred as a group) ────────
            float hdrMid = hdrY + hdrH / 2f;
            float logoSz = 72f;
            float gap    = 6f;
            // Approximate text block width so the group is centred
            float textBlockW = 230f;
            float groupW     = logoSz + gap + textBlockW;
            float groupLeft  = CX - groupW / 2f;

            // Logo
            float logoX = groupLeft;
            float logoY = hdrMid - logoSz / 2f + 10f;
            try {
                ClassPathResource res = new ClassPathResource("static/logo_with_white_background.png");
                byte[] logoBytes = res.getInputStream().readAllBytes();
                cv.addImageFittedIntoRectangle(
                        ImageDataFactory.create(logoBytes),
                        new Rectangle(logoX, logoY, logoSz, logoSz),
                        false);
            } catch (Exception ignored) {}

            // Text — centred within the text block
            float textCX = groupLeft + logoSz + gap + textBlockW / 2f;
            Canvas mc = new Canvas(cv, new Rectangle(0, 0, W, H));

            mc.showTextAligned(
                    new Paragraph("SAS KPO SERVICES")
                            .setFont(fHBold).setFontSize(26)
                            .setFontColor(C_GOLD).setMargin(0),
                    textCX, hdrMid + 9f, TextAlignment.CENTER);
            mc.showTextAligned(
                    new Paragraph("Partners in Business Extension")
                            .setFont(fHReg).setFontSize(11)
                            .setFontColor(C_LT_GRAY).setMargin(0),
                    textCX, hdrMid - 12f, TextAlignment.CENTER);

            // ── 7. CERTIFICATE TITLE ──────────────────────────────────────
            float titleY = hdrY - 46f;
            mc.showTextAligned(
                    new Paragraph("CERTIFICATE OF COMPLETION")
                            .setFont(fHBold).setFontSize(30)
                            .setFontColor(C_DARK).setMargin(0),
                    CX, titleY, TextAlignment.CENTER);

            // Gold double underline below title
            cv.setStrokeColor(C_GOLD).setLineWidth(2f)
              .moveTo(CX - 205f, titleY - 8f).lineTo(CX + 205f, titleY - 8f).stroke();
            cv.setStrokeColor(C_GOLD_LT).setLineWidth(0.5f)
              .moveTo(CX - 205f, titleY - 12f).lineTo(CX + 205f, titleY - 12f).stroke();

            // ── 8. "AWARDED TO" LABEL ────────────────────────────────────
            float awardY = titleY - 52f;
            mc.showTextAligned(
                    new Paragraph("This certificate is proudly presented to")
                            .setFont(fTItal).setFontSize(13)
                            .setFontColor(C_GRAY).setMargin(0),
                    CX, awardY, TextAlignment.CENTER);

            // ── 9. EMPLOYEE NAME ─────────────────────────────────────────
            float nameY = awardY - 62f;
            mc.showTextAligned(
                    new Paragraph(empName)
                            .setFont(fTBold).setFontSize(42)
                            .setFontColor(C_DARK).setMargin(0),
                    CX, nameY, TextAlignment.CENTER);

            // Gold underline below name
            cv.setStrokeColor(C_GOLD).setLineWidth(1.5f)
              .moveTo(CX - 220f, nameY - 8f).lineTo(CX + 220f, nameY - 8f).stroke();

            // ── 10. COMPLETION BODY TEXT ─────────────────────────────────
            float forY = nameY - 52f;
            mc.showTextAligned(
                    new Paragraph("For successfully completing the course")
                            .setFont(fHReg).setFontSize(13)
                            .setFontColor(C_GRAY).setMargin(0),
                    CX, forY, TextAlignment.CENTER);

            float courseY = forY - 34f;
            mc.showTextAligned(
                    new Paragraph("\" " + course + " \"")
                            .setFont(fTBold).setFontSize(17)
                            .setFontColor(C_GOLD_DK).setMargin(0),
                    CX, courseY, TextAlignment.CENTER);

            // ── 11. DATE ─────────────────────────────────────────────────
            float dateY = courseY - 34f;
            mc.showTextAligned(
                    new Paragraph("Completion Date:   " + date)
                            .setFont(fHReg).setFontSize(12)
                            .setFontColor(C_GRAY).setMargin(0),
                    CX, dateY, TextAlignment.CENTER);

            // ── 12. SHORT GOLD SEPARATOR ──────────────────────────────────
            float sepY = dateY - 36f;
            cv.setStrokeColor(C_GOLD_LT).setLineWidth(0.5f)
              .moveTo(CX - 90f, sepY).lineTo(CX + 90f, sepY).stroke();

            // ── 13. DIRECTOR NAME (centred, below date) ───────────────────
            float dirNameY = sepY - 26f;
            mc.showTextAligned(
                    new Paragraph("Abhishek Soni")
                            .setFont(fHBold).setFontSize(18)
                            .setFontColor(C_DARK).setMargin(0),
                    CX, dirNameY, TextAlignment.CENTER);
            mc.showTextAligned(
                    new Paragraph("Director, SAS KPO Services")
                            .setFont(fHReg).setFontSize(13)
                            .setFontColor(C_GRAY).setMargin(0),
                    CX, dirNameY - 24f, TextAlignment.CENTER);

            // ── 14. CERTIFICATE NUMBER (below director) ───────────────────
            mc.showTextAligned(
                    new Paragraph("Certificate No:  " + certNo)
                            .setFont(fHReg).setFontSize(10)
                            .setFontColor(C_LT_GRAY).setMargin(0),
                    CX, dirNameY - 46f, TextAlignment.CENTER);

            mc.close();

            // ── 15. CORNER ORNAMENTS ──────────────────────────────────────
            float ornLen = 22f;
            cv.setStrokeColor(C_GOLD).setLineWidth(2.2f);
            // Top-left
            cv.moveTo(ib, H - ib).lineTo(ib + ornLen, H - ib).stroke();
            cv.moveTo(ib, H - ib).lineTo(ib, H - ib - ornLen).stroke();
            // Top-right
            cv.moveTo(W - ib, H - ib).lineTo(W - ib - ornLen, H - ib).stroke();
            cv.moveTo(W - ib, H - ib).lineTo(W - ib, H - ib - ornLen).stroke();
            // Bottom-left
            cv.moveTo(ib, ib).lineTo(ib + ornLen, ib).stroke();
            cv.moveTo(ib, ib).lineTo(ib, ib + ornLen).stroke();
            // Bottom-right
            cv.moveTo(W - ib, ib).lineTo(W - ib - ornLen, ib).stroke();
            cv.moveTo(W - ib, ib).lineTo(W - ib, ib + ornLen).stroke();

            // ── 16. CIRCULAR VERIFIED SEAL ───────────────────────────────
            float sealX = bm + 108f, sealY = bm + 55f, sealR = 44f;
            cv.setFillColor(C_OFF_WHITE).circle(sealX, sealY, sealR).fill();
            cv.setStrokeColor(C_GOLD).setLineWidth(2f).circle(sealX, sealY, sealR).stroke();
            cv.setStrokeColor(C_GOLD_LT).setLineWidth(0.75f).circle(sealX, sealY, sealR - 6f).stroke();

            // Render seal text on main canvas to avoid clipping
            mc.showTextAligned(
                    new Paragraph("VERIFIED")
                            .setFont(fHBold).setFontSize(9)
                            .setFontColor(C_GOLD).setMargin(0),
                    sealX, sealY + 14f, TextAlignment.CENTER);
            mc.showTextAligned(
                    new Paragraph("- - -")
                            .setFont(fHBold).setFontSize(7)
                            .setFontColor(C_GOLD_LT).setMargin(0),
                    sealX, sealY + 3f, TextAlignment.CENTER);
            mc.showTextAligned(
                    new Paragraph("SAS KPO")
                            .setFont(fHBold).setFontSize(8)
                            .setFontColor(C_GOLD_DK).setMargin(0),
                    sealX, sealY - 10f, TextAlignment.CENTER);
            mc.showTextAligned(
                    new Paragraph("SERVICES")
                            .setFont(fHReg).setFontSize(6)
                            .setFontColor(C_GOLD_DK).setMargin(0),
                    sealX, sealY - 20f, TextAlignment.CENTER);
        }

        return out.toByteArray();
    }
}
