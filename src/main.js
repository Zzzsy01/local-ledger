import './style.css';
import { processFiles } from './flow.js';
import { releaseImages } from './images.js';
import { ROLES, ROLE_LABELS, PAGE, makeLayout, parseMoney, formatMoney } from './rules.js';
import { createPdf } from './pdf.js';

const $ = (id) => document.getElementById(id);
let images = [], busy = false, manualAmount = null, installPrompt = null;
const emptyPreview = $('preview').innerHTML;

function message(text) { $('notice').textContent = text; $('notice').hidden = !text; }
function setBusy(value) {
  busy = value;
  $('pick').disabled = value;
  $('progress').parentElement.hidden = !value;
  document.querySelectorAll('.image-card select').forEach((select) => { select.disabled = value; });
  renderPreview();
}
function updateAmount() {
  const taobao = images.find((image) => image.role === 'taobao');
  const payment = images.find((image) => image.role === 'payment');
  const cents = manualAmount !== null ? parseMoney(manualAmount) : taobao?.taobaoCents ?? null;
  if (manualAmount === null) $('amount').value = formatMoney(cents);
  const invalid = manualAmount !== null && manualAmount.trim() !== '' && cents === null;
  $('amount').setCustomValidity(invalid ? '请输入最多两位小数的非负金额。' : '');
  $('amount-note').textContent = invalid ? '请输入有效金额，例如 115.56。' :
    taobao && cents === null ? '未能确定淘宝实付款，请看截图填写。金额不会写入 PDF。' :
    manualAmount !== null ? '使用你填写的金额。金额不会写入 PDF。' : '金额来自淘宝实付款，仅在这里显示。';
  const other = payment?.paymentCents;
  const differs = cents !== null && other != null && cents !== other;
  $('difference').hidden = !differs;
  $('difference').textContent = differs ? `支付账单显示 ¥${formatMoney(other)}，与淘宝实付款不同，请核对两张截图。` : '';
}
function renderImages() {
  $('images').replaceChildren();
  for (const image of images) {
    const card = document.createElement('div'); card.className = 'image-card';
    const thumb = document.createElement('img'); thumb.src = image.url; thumb.alt = `第 ${image.id + 1} 张图片缩略图`;
    const info = document.createElement('div');
    const name = document.createElement('p'); name.className = 'image-name'; name.textContent = image.name;
    const note = document.createElement('small'); note.textContent = image.ocr ? (image.role ? '已自动识别，可纠正' : '请确认图片类型') : '等待识别';
    info.append(name, note);
    const select = document.createElement('select'); select.setAttribute('aria-label', `第 ${image.id + 1} 张图片类型`); select.disabled = busy;
    select.append(new Option('请选择类型', ''));
    ROLES.forEach((role) => select.append(new Option(ROLE_LABELS[role], role)));
    select.value = image.role ?? '';
    select.addEventListener('change', () => { image.role = select.value || null; note.textContent = '已手动确认'; message(''); updateAmount(); renderPreview(); });
    card.append(thumb, info, select); $('images').append(card);
  }
}
function currentLayout() {
  return images.length === 3 && ROLES.every((role) => images.filter((image) => image.role === role).length === 1) ? makeLayout(images) : null;
}
function renderPreview() {
  const layout = currentLayout();
  $('export').disabled = busy || !layout;
  $('export-note').textContent = layout ? `将导出 ${layout.length} 页 PDF，保留全部三张图片。` : '确认三张图片的类型后即可导出。';
  $('pages').textContent = layout ? `${layout.length} 页 / A4` : '02 / 导出前确认';
  if (!layout) {
    $('preview').innerHTML = emptyPreview;
    if (images.length && !busy) message('请为三张图片各选一种类型；每种类型需要一张。');
    return;
  }
  $('preview').replaceChildren();
  for (const [index, placements] of layout.entries()) {
    const canvas = document.createElement('canvas');
    canvas.className = 'preview-page'; canvas.width = Math.round(PAGE.width * 1.5); canvas.height = Math.round(PAGE.height * 1.5);
    canvas.setAttribute('role', 'img'); canvas.setAttribute('aria-label', `PDF 第 ${index + 1} 页预览`);
    const ctx = canvas.getContext('2d'); ctx.scale(canvas.width / PAGE.width, canvas.height / PAGE.height);
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, PAGE.width, PAGE.height);
    placements.forEach((item) => ctx.drawImage(images.find((image) => image.id === item.imageId).element, item.x, item.y, item.width, item.height));
    $('preview').append(canvas);
  }
}
async function selectFiles(files) {
  if (busy) return;
  if (files.length !== 3) { message('请一次选择三张图片。'); return; }
  let loadedNewImages = false;
  message(''); setBusy(true);
  $('status').textContent = '正在读取图片…'; $('progress').value = 0;
  try {
    await processFiles([...files], {
      onImages: (loaded) => { releaseImages(images); images = loaded; loadedNewImages = true; manualAmount = null; $('amount').value = ''; renderImages(); updateAmount(); },
      onProgress: (text, progress) => { $('status').textContent = text; $('progress').value = progress; },
      onImage: () => { renderImages(); updateAmount(); renderPreview(); },
    });
    message(currentLayout() ? '识别完成。请核对金额和预览后导出。' : '识别完成，请手动确认尚不明确的图片类型。');
  } catch (error) {
    console.error(error);
    message(loadedNewImages ? '自动识别未完成。可以手动确认图片类型和金额后导出。' : error.message || '图片无法读取，请选择有效的 JPG 或 PNG。');
  } finally {
    setBusy(false); renderImages(); updateAmount(); $('files').value = '';
  }
}
$('pick').addEventListener('click', () => $('files').click());
$('files').addEventListener('change', (event) => selectFiles(event.target.files));
$('pick').addEventListener('dragover', (event) => { event.preventDefault(); if (!busy) $('pick').classList.add('dragover'); });
$('pick').addEventListener('dragleave', () => $('pick').classList.remove('dragover'));
$('pick').addEventListener('drop', (event) => { event.preventDefault(); $('pick').classList.remove('dragover'); selectFiles(event.dataTransfer.files); });
$('amount').addEventListener('input', () => { manualAmount = $('amount').value; updateAmount(); });
$('export').addEventListener('click', async () => {
  const layout = currentLayout(); if (!layout || busy) return;
  setBusy(true); $('status').textContent = '正在生成 PDF…';
  try {
    const bytes = await createPdf(images, layout);
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
    const a = document.createElement('a'); a.href = url;
    const filename = $('filename').value.trim().replace(/[\\/:*?"<>|]/g, '_').replace(/\.pdf$/i, '') || '票据整理';
    a.download = `${filename}.pdf`; document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
    message(`PDF 已生成，共 ${layout.length} 页。`);
  } catch (error) { console.error(error); message('PDF 生成失败，请重试导出。'); }
  finally { setBusy(false); }
});

window.addEventListener('beforeinstallprompt', (event) => { event.preventDefault(); installPrompt = event; $('install').hidden = false; });
$('install').addEventListener('click', async () => {
  if (!installPrompt) { message('在浏览器菜单中选择“安装应用”或“添加到主屏幕”。离线资源就绪后可断网使用。'); return; }
  await installPrompt.prompt(); await installPrompt.userChoice; installPrompt = null; $('install').hidden = true;
});
window.addEventListener('appinstalled', () => { $('install').hidden = true; });
if (window.matchMedia('(display-mode: standalone)').matches) $('install').hidden = true;
window.addEventListener('pagehide', () => releaseImages(images));
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  navigator.serviceWorker.register(new URL('sw.js', document.baseURI)).then(() => navigator.serviceWorker.ready)
    .then(() => { $('offline-status').textContent = '离线资源已就绪 · 可添加到桌面'; })
    .catch((error) => { console.error(error); $('offline-status').textContent = '离线资源未就绪，请联网重新打开'; });
} else $('offline-status').textContent = '本地运行';

// Another way to perform the existing role correction, when the browser supports WebMCP.
if (document.modelContext?.registerTool) {
  const context = document.modelContext;
  const cleanup = new AbortController();
  Promise.resolve(context.registerTool({
    name: 'confirm_receipt_image_types', title: '确认票据图片类型',
    description: '为当前三张图片确认类型，更新与界面相同的排版预览。',
    inputSchema: { type: 'object', properties: { roles: { type: 'array', minItems: 3, maxItems: 3, items: { type: 'string', enum: ROLES } } }, required: ['roles'], additionalProperties: false },
    execute({ roles }) {
      if (busy || images.length !== 3 || !Array.isArray(roles) || roles.length !== 3 || !ROLES.every((role) => roles.filter((r) => r === role).length === 1)) throw new Error('需要已加载的三张图片和三种不同的类型。');
      images.forEach((image, index) => { image.role = roles[index]; });
      renderImages(); updateAmount(); renderPreview();
      return { roles, pages: currentLayout().length };
    },
  }, { signal: cleanup.signal })).catch(console.error);
  window.addEventListener('pagehide', () => cleanup.abort(), { once: true });
}
