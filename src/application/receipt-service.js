import { ROLES, classify, findAmountRegion, parseRecognizedAmount, getAmountState, hasRequiredRoles, makeLayout } from '../domain/rules.js';
import { loadImages, releaseImages } from '../infrastructure/images.js';
import { createLocalOcr } from '../infrastructure/ocr.js';
import { createPdf } from '../infrastructure/pdf.js';

export function createReceiptService(onChange) {
  // Business records contain plain data; browser images and bytes stay in assets.
  let assets = [], images = [], layout = null, manualAmount = null;
  let busy = false, notice = '', status = '', progress = 0;

  function getState() {
    return {
      busy, notice, status, progress,
      amount: getAmountState(images, manualAmount),
      layout: layout?.map((page) => page.map((item) => ({ ...item }))) ?? null,
      images: images.map((image, index) => ({
        id: image.id, name: image.name, role: image.role, manualRole: image.manualRole,
        recognized: image.ocr !== null, url: assets[index].url, element: assets[index].element,
      })),
    };
  }
  const notify = () => onChange(getState());
  function updateLayout() { layout = hasRequiredRoles(images) ? makeLayout(images) : null; }
  function roleNotice() { notice = layout ? '' : '请为三张图片各选一种类型；每种类型需要一张。'; }

  async function selectFiles(files) {
    if (busy) return;
    if (files.length !== 3) { notice = '请一次选择三张图片。'; notify(); return; }
    busy = true; notice = ''; status = '正在读取图片…'; progress = 0; notify();
    let loaded = false;
    try {
      const next = await loadImages(files);
      releaseImages(assets); assets = next;
      images = assets.map(({ id, name, width, height }) => ({ id, name, width, height, role: null, manualRole: false, ocr: null }));
      manualAmount = null; layout = null; loaded = true; notify();
      let current = 0, amountPhase = false;
      const ocr = await createLocalOcr((ocrStatus, value) => {
        status = amountPhase ? '正在核对付款数字' : ocrStatus === 'recognizing text' ? `正在识别第 ${current + 1} 张图片` : '正在准备本地识别';
        progress = amountPhase ? 0.8 + value * 0.2 : ocrStatus === 'recognizing text' ? (current + value) / 3 * 0.8 : 0;
        notify();
      });
      try {
        for (current = 0; current < images.length; current++) {
          const image = images[current];
          image.ocr = await ocr.recognize(assets[current]);
          image.role = classify(image.ocr.text);
          updateLayout(); notify();
        }
        const amounts = images.map((image, index) => ({ image, asset: assets[index], region: findAmountRegion(image.role, image.ocr) })).filter(({ region }) => region);
        if (amounts.length) {
          amountPhase = true;
          await ocr.prepareAmounts();
          for (const { image, asset, region } of amounts) {
            image.ocr.amountText = await ocr.recognizeAmount(asset, region);
            image[image.role === 'taobao' ? 'taobaoCents' : 'paymentCents'] = parseRecognizedAmount(image.ocr.amountText, image.role);
            notify();
          }
        }
      } finally { await ocr.close(); }
      notice = layout ? '识别完成。请核对金额和预览后导出。' : '识别完成，请手动确认尚不明确的图片类型。';
    } catch (error) {
      console.error(error);
      notice = loaded ? '自动识别未完成。可以手动确认图片类型和金额后导出。' : error.message || '图片无法读取，请选择有效的 JPG 或 PNG。';
    } finally { busy = false; notify(); }
  }

  function setRole(id, role) {
    if (busy) return;
    const image = images.find((image) => image.id === id);
    if (!image || (role !== null && !ROLES.includes(role))) throw new Error('图片或类型无效。');
    image.role = role; image.manualRole = true;
    updateLayout(); roleNotice(); notify();
  }
  function setRoles(roles) {
    if (busy || images.length !== 3 || !Array.isArray(roles) || !hasRequiredRoles(roles.map((role) => ({ role })))) {
      throw new Error('需要已加载的三张图片和三种不同的类型。');
    }
    images.forEach((image, index) => { image.role = roles[index]; image.manualRole = true; });
    updateLayout(); roleNotice(); notify();
  }
  function setAmount(value) { manualAmount = value; notify(); }

  async function exportPdf(filename) {
    if (busy || !layout) return null;
    busy = true; status = '正在生成 PDF…'; notify();
    try {
      const bytes = await createPdf(assets, layout);
      const name = filename.trim().replace(/[\\/:*?"<>|]/g, '_').replace(/\.pdf$/i, '') || '票据整理';
      notice = `PDF 已生成，共 ${layout.length} 页。`;
      return { bytes, filename: `${name}.pdf` };
    } catch (error) {
      console.error(error); notice = 'PDF 生成失败，请重试导出。'; return null;
    } finally { busy = false; notify(); }
  }
  function dispose() {
    releaseImages(assets); assets = []; images = []; layout = null; manualAmount = null;
  }
  return { getState, selectFiles, setRole, setRoles, setAmount, exportPdf, dispose };
}
