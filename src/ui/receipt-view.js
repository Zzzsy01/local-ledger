import { ROLES, ROLE_LABELS, PAGE, formatMoney } from '../domain/rules.js';

const $ = (id) => document.getElementById(id);
const emptyPreview = $('preview').innerHTML;
let receipt, installPrompt = null, renderedImages = '', renderedPreview = '';

function message(text) { $('notice').textContent = text; $('notice').hidden = !text; }
function renderImages({ images, busy }) {
  const key = images.map((image) => image.url).join('|');
  if (key !== renderedImages) {
    renderedImages = key; $('images').replaceChildren();
    for (const image of images) {
      const card = document.createElement('div'); card.className = 'image-card';
      const thumb = document.createElement('img'); thumb.src = image.url; thumb.alt = `第 ${image.id + 1} 张图片缩略图`;
      const info = document.createElement('div');
      const name = document.createElement('p'); name.className = 'image-name'; name.textContent = image.name;
      info.append(name, document.createElement('small'));
      const select = document.createElement('select'); select.setAttribute('aria-label', `第 ${image.id + 1} 张图片类型`);
      select.append(new Option('请选择类型', ''));
      ROLES.forEach((role) => select.append(new Option(ROLE_LABELS[role], role)));
      select.addEventListener('change', () => receipt.setRole(image.id, select.value || null));
      card.append(thumb, info, select); $('images').append(card);
    }
  }
  Array.from($('images').children).forEach((card, index) => {
    const image = images[index], select = card.querySelector('select');
    select.value = image.role ?? ''; select.disabled = busy;
    card.querySelector('small').textContent = image.manualRole ? '已手动确认' : image.recognized ? image.role ? '已自动识别，可纠正' : '请确认图片类型' : '等待识别';
  });
}
function renderAmount(amount) {
  if ($('amount').value !== amount.value) $('amount').value = amount.value;
  $('amount').setCustomValidity(amount.invalid ? '请输入最多两位小数的非负金额。' : '');
  $('amount-note').textContent = amount.invalid ? '请输入有效金额，例如 115.56。' : {
    none: '金额只在这里显示，PDF 保留三图样式。',
    unknown: '未能确定淘宝实付款，请看截图填写。金额不会写入 PDF。',
    manual: '使用你填写的金额。金额不会写入 PDF。',
    recognized: '金额来自淘宝实付款，仅在这里显示。',
  }[amount.source];
  $('difference').hidden = !amount.differs;
  $('difference').textContent = amount.differs ? `支付账单显示 ¥${formatMoney(amount.paymentCents)}，与淘宝实付款不同，请核对两张截图。` : '';
}
function renderPreview({ layout, images }) {
  const key = JSON.stringify([layout, images.map((image) => image.url)]);
  if (key === renderedPreview) return;
  renderedPreview = key;
  if (!layout) { $('preview').innerHTML = emptyPreview; return; }
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
export function renderReceipt(state) {
  $('pick').disabled = state.busy;
  $('progress').parentElement.hidden = !state.busy;
  $('status').textContent = state.status; $('progress').value = state.progress;
  message(state.notice); renderImages(state); renderAmount(state.amount); renderPreview(state);
  $('export').disabled = state.busy || !state.layout;
  $('export-note').textContent = state.layout ? `将导出 ${state.layout.length} 页 PDF，保留全部三张图片。` : '确认三张图片的类型后即可导出。';
  $('pages').textContent = state.layout ? `${state.layout.length} 页 / A4` : '02 / 导出前确认';
}

export function mountReceiptView(service) {
  receipt = service;
  async function selectFiles(files) { await receipt.selectFiles([...files]); $('files').value = ''; }
  $('pick').addEventListener('click', () => $('files').click());
  $('files').addEventListener('change', (event) => selectFiles(event.target.files));
  $('pick').addEventListener('dragover', (event) => { event.preventDefault(); if (!$('pick').disabled) $('pick').classList.add('dragover'); });
  $('pick').addEventListener('dragleave', () => $('pick').classList.remove('dragover'));
  $('pick').addEventListener('drop', (event) => { event.preventDefault(); $('pick').classList.remove('dragover'); selectFiles(event.dataTransfer.files); });
  $('amount').addEventListener('input', () => receipt.setAmount($('amount').value));
  $('export').addEventListener('click', async () => {
    const result = await receipt.exportPdf($('filename').value); if (!result) return;
    const url = URL.createObjectURL(new Blob([result.bytes], { type: 'application/pdf' }));
    const a = document.createElement('a'); a.href = url; a.download = result.filename;
    document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000);
  });
  window.addEventListener('beforeinstallprompt', (event) => { event.preventDefault(); installPrompt = event; $('install').hidden = false; });
  $('install').addEventListener('click', async () => {
    if (!installPrompt) { message('在浏览器菜单中选择“安装应用”或“添加到主屏幕”。离线资源就绪后可断网使用。'); return; }
    await installPrompt.prompt(); await installPrompt.userChoice; installPrompt = null; $('install').hidden = true;
  });
  window.addEventListener('appinstalled', () => { $('install').hidden = true; });
  if (window.matchMedia('(display-mode: standalone)').matches) $('install').hidden = true;
  window.addEventListener('pagehide', () => receipt.dispose());
  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    navigator.serviceWorker.register(new URL('sw.js', document.baseURI)).then(() => navigator.serviceWorker.ready)
      .then(() => { $('offline-status').textContent = '离线资源已就绪 · 可添加到桌面'; })
      .catch((error) => { console.error(error); $('offline-status').textContent = '离线资源未就绪，请联网重新打开'; });
  } else $('offline-status').textContent = '本地运行';

  if (document.modelContext?.registerTool) {
    const cleanup = new AbortController();
    Promise.resolve(document.modelContext.registerTool({
      name: 'confirm_receipt_image_types', title: '确认票据图片类型',
      description: '为当前三张图片确认类型，更新与界面相同的排版预览。',
      inputSchema: { type: 'object', properties: { roles: { type: 'array', minItems: 3, maxItems: 3, items: { type: 'string', enum: ROLES } } }, required: ['roles'], additionalProperties: false },
      execute({ roles }) {
        receipt.setRoles(roles);
        const state = receipt.getState();
        return { roles: state.images.map((image) => image.role), pages: state.layout.length };
      },
    }, { signal: cleanup.signal })).catch(console.error);
    window.addEventListener('pagehide', () => cleanup.abort(), { once: true });
  }
  renderReceipt(receipt.getState());
}
