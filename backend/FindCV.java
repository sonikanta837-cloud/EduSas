import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.file.*;

public class FindCV {
    public static void main(String[] args) throws Exception {
        File dir = new File("uploads/resumes");
        File[] pdfs = dir.listFiles((d, n) -> n.endsWith(".pdf"));
        if (pdfs == null) { System.out.println("No PDFs"); return; }
        java.util.Arrays.sort(pdfs, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        PDFTextStripper s = new PDFTextStripper();
        s.setSortByPosition(true);
        for (int i = 0; i < Math.min(10, pdfs.length); i++) {
            try (PDDocument doc = Loader.loadPDF(pdfs[i])) {
                String t = s.getText(doc);
                if (t.toLowerCase().contains("aayushi") || t.toLowerCase().contains("bhawsar")) {
                    System.out.println("=== FOUND: " + pdfs[i].getName() + " ===");
                    System.out.println(t);
                    return;
                }
            } catch(Exception e) {}
        }
        System.out.println("Not found in recent 10 files");
    }
}
