import { createWorker } from 'tesseract.js';
import * as pdfjsLib from 'pdfjs-dist';

pdfjsLib.GlobalWorkerOptions.workerSrc = `${process.env.PUBLIC_URL}/pdf.worker.min.mjs`;

/**
 * Renders PDF pages to canvas and runs Tesseract OCR in the browser.
 * Works for image-based, scanned, and photo-of-CV PDFs with no backend dependency.
 * @param {File} file - The PDF file object
 * @param {Function} onProgress - Optional callback(0-100) for OCR progress
 * @returns {Promise<string>} Raw extracted text
 */
export async function ocrPdfFile(file, onProgress) {
  const arrayBuffer = await file.arrayBuffer();
  const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;

  const worker = await createWorker('eng', 1, {
    logger: m => {
      if (onProgress && m.status === 'recognizing text') {
        onProgress(Math.round(m.progress * 100));
      }
    },
  });

  let fullText = '';
  const numPages = Math.min(pdf.numPages, 2);

  for (let i = 1; i <= numPages; i++) {
    const page = await pdf.getPage(i);
    const viewport = page.getViewport({ scale: 2.5 });

    const canvas = document.createElement('canvas');
    canvas.width = viewport.width;
    canvas.height = viewport.height;
    const ctx = canvas.getContext('2d');
    await page.render({ canvasContext: ctx, viewport }).promise;

    const { data: { text } } = await worker.recognize(canvas);
    console.log(`[OCR] Page ${i} text:\n`, text);
    fullText += text + '\n';
  }

  await worker.terminate();
  return fullText;
}
