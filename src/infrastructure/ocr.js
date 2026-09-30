import { createWorker, PSM } from 'tesseract.js';

export async function createLocalOcr(onProgress) {
  const root = new URL('ocr/', document.baseURI).href;
  const worker = await createWorker(['eng', 'chi_sim'], 1, {
    workerPath: `${root}worker.min.js`,
    corePath: `${root}core/`,
    langPath: `${root}lang`,
    cacheMethod: 'none',
    logger: ({ status, progress }) => onProgress(status, progress),
  });
  await worker.setParameters({ tessedit_pageseg_mode: PSM.SPARSE_TEXT });
  return {
    async recognize(image) {
      const { data } = await worker.recognize(image.url, {}, { text: true, blocks: true });
      const lines = (data.blocks ?? []).flatMap((b) => b.paragraphs.flatMap((p) => p.lines));
      return { text: data.text, lines: lines.map(({ text, bbox, words }) => ({ text, bbox, words: words.map(({ text, bbox }) => ({ text, bbox })) })) };
    },
    async prepareAmounts() {
      await worker.reinitialize('eng', 1);
      await worker.setParameters({ tessedit_pageseg_mode: PSM.SINGLE_LINE, tessedit_char_whitelist: '0123456789.,-' });
    },
    async recognizeAmount(image, region) {
      const x = Math.max(0, region.x0 - 6), y = Math.max(0, region.y0 - 6);
      const width = Math.min(image.width - x, region.x1 - x + 6), height = Math.min(image.height - y, region.y1 - y + 6);
      const canvas = document.createElement('canvas'); canvas.width = Math.ceil(width * 3); canvas.height = Math.ceil(height * 3);
      const ctx = canvas.getContext('2d'); ctx.fillStyle = 'white'; ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(image.element, x, y, width, height, 0, 0, canvas.width, canvas.height);
      const { data } = await worker.recognize(canvas, {}, { text: true });
      return data.text.trim();
    },
    close: () => worker.terminate(),
  };
}
