import { loadImages } from './images.js';
import { createLocalOcr } from './ocr.js';
import { classify, findAmountRegion, parseRecognizedAmount } from './rules.js';

export async function processFiles(files, { onImages, onProgress, onImage }) {
  const images = await loadImages(files);
  onImages(images);
  let current = 0, amountPhase = false;
  const ocr = await createLocalOcr((status, progress) => {
    const label = amountPhase ? '正在核对付款数字' : status === 'recognizing text' ? `正在识别第 ${current + 1} 张图片` : '正在准备本地识别';
    onProgress(label, amountPhase ? 0.8 + progress * 0.2 : status === 'recognizing text' ? (current + progress) / 3 * 0.8 : 0);
  });
  try {
    for (current = 0; current < images.length; current++) {
      const image = images[current];
      image.ocr = await ocr.recognize(image);
      image.role = classify(image.ocr.text);
      onImage(image);
    }
    const amounts = images.map((image) => ({ image, region: findAmountRegion(image.role, image.ocr) })).filter(({ region }) => region);
    if (amounts.length) {
      amountPhase = true;
      await ocr.prepareAmounts();
      for (const { image, region } of amounts) {
        image.ocr.amountText = await ocr.recognizeAmount(image, region);
        image[image.role === 'taobao' ? 'taobaoCents' : 'paymentCents'] = parseRecognizedAmount(image.ocr.amountText, image.role);
        onImage(image);
      }
    }
  } finally {
    await ocr.close();
  }
  return images;
}
