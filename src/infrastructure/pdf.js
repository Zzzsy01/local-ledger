import { PDFDocument } from 'pdf-lib';
import { PAGE } from '../domain/rules.js';

export async function createPdf(images, layout) {
  const pdf = await PDFDocument.create();
  pdf.setTitle('票据整理');
  const embedded = new Map();
  for (const image of images) {
    embedded.set(image.id, await (image.format === 'jpg' ? pdf.embedJpg(image.bytes) : pdf.embedPng(image.bytes)));
  }
  for (const placements of layout) {
    const page = pdf.addPage([PAGE.width, PAGE.height]);
    for (const item of placements) {
      page.drawImage(embedded.get(item.imageId), { x: item.x, y: PAGE.height - item.y - item.height, width: item.width, height: item.height });
    }
  }
  return pdf.save();
}
