export const ROLES = ['invoice', 'taobao', 'payment'];
export const ROLE_LABELS = { invoice: '发票', taobao: '淘宝订单 / 付款', payment: '微信 / 支付宝付款' };

const compact = (text) => text.normalize('NFKC').replace(/\s/g, '');

export function classify(text) {
  // ponytail: keyword rules cover these screenshot types; extend markers when a real page format changes.
  const t = compact(text);
  const scores = {
    invoice: [/电子发票|增值税/.test(t), /发票号码|发票代码/.test(t), /价税合计|纳税人识别号/.test(t)].filter(Boolean).length,
    taobao: [/订单信息|订单编号/.test(t), /实付款|实付金额/.test(t), /收货信息|再买一单|确认收货/.test(t)].filter(Boolean).length,
    payment: [/账单详情|账单服务/.test(t), /交易单号|商户单号/.test(t), /支付时间|交易时间/.test(t), /当前状态|付款方式|收款方/.test(t)].filter(Boolean).length,
  };
  const ranked = Object.entries(scores).sort((a, b) => b[1] - a[1]);
  return ranked[0][1] >= 2 && ranked[0][1] > ranked[1][1] ? ranked[0][0] : null;
}

export function parseMoney(value) {
  const t = value.normalize('NFKC').trim().replace(/^[¥￥]\s*/, '').replace(/,/g, '');
  const m = /^(\d{1,9})(?:\.(\d{1,2}))?$/.exec(t);
  return m ? Number(m[1]) * 100 + Number((m[2] ?? '').padEnd(2, '0')) : null;
}

export function formatMoney(cents) {
  return cents == null ? '' : `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}

export function getAmountState(images, manualAmount) {
  const taobao = images.find((image) => image.role === 'taobao');
  const paymentCents = images.find((image) => image.role === 'payment')?.paymentCents ?? null;
  const manual = manualAmount !== null;
  const cents = manual ? parseMoney(manualAmount) : taobao?.taobaoCents ?? null;
  return {
    value: manual ? manualAmount : formatMoney(cents),
    invalid: manual && manualAmount.trim() !== '' && cents === null,
    source: taobao && cents === null ? 'unknown' : manual ? 'manual' : taobao ? 'recognized' : 'none',
    paymentCents,
    differs: cents !== null && paymentCents !== null && cents !== paymentCents,
  };
}

export function hasRequiredRoles(images) {
  return images.length === 3 && ROLES.every((role) => images.filter((image) => image.role === role).length === 1);
}

export function parseRecognizedAmount(text, role) {
  const value = compact(text);
  // Taobao's dropdown chevron can be recognized as a trailing dash. Bills use a leading debit sign.
  const match = (role === 'taobao' ? /^(\d{1,9}(?:\.\d{1,2})?)-?$/ : /^-?(\d{1,9}(?:\.\d{1,2})?)$/).exec(value);
  return match && !/^0\d/.test(match[1]) ? parseMoney(match[1]) : null;
}

export function findAmountRegion(role, ocr) {
  const lines = ocr.lines ?? [];
  if (role === 'payment') {
    const amounts = lines.filter((line) => /^[-−–][¥￥Y]?[\d.,]+$/.test(compact(line.text)) && line.bbox);
    return amounts.length === 1 ? amounts[0].bbox : null;
  }
  if (role !== 'taobao') return null;
  const labels = lines.filter((line) => /实付款|实付金额|实付(?!价)/.test(compact(line.text)) && line.bbox);
  if (labels.length !== 1) return null;
  const label = labels[0], h = label.bbox.y1 - label.bbox.y0, cy = (label.bbox.y0 + label.bbox.y1) / 2;
  const row = lines.filter((line) => line !== label && line.bbox && /\d/.test(line.text) &&
    line.bbox.x0 > label.bbox.x1 && Math.abs((line.bbox.y0 + line.bbox.y1) / 2 - cy) <= h * 0.8);
  const amount = row.sort((a, b) => b.bbox.x1 - a.bbox.x1)[0];
  if (amount) return amount.bbox;
  const words = (label.words ?? []).filter((word) => /\d/.test(word.text) && word.bbox);
  return words.sort((a, b) => b.bbox.x1 - a.bbox.x1)[0]?.bbox ?? null;
}

export const PAGE = { width: 595.32, height: 841.92, x: 90, y: 72, widthInside: 415.32, heightInside: 697.92, gap: 8, shotWidth: 185, shotHeight: 410.69 };

export function makeLayout(images) {
  if (!hasRequiredRoles(images)) {
    throw new Error('请分别确认一张发票、一张淘宝截图和一张支付截图。');
  }
  if (images.some((i) => !Number.isFinite(i.width) || !Number.isFinite(i.height) || i.width <= 0 || i.height <= 0)) {
    throw new Error('图片尺寸无效。');
  }
  const ordered = ROLES.map((role) => images.find((i) => i.role === role));
  const size = (image, w) => ({ imageId: image.id, width: w, height: image.height * w / image.width });
  let invoice = size(ordered[0], PAGE.widthInside);
  const shots = ordered.slice(1).map((i) => {
    const full = size(i, PAGE.shotWidth);
    return full.height > PAGE.shotHeight && PAGE.shotHeight / full.height >= 0.95 ? size(i, PAGE.shotHeight * i.width / i.height) : full;
  });
  const rowHeight = Math.max(...shots.map((i) => i.height));
  const invoiceRoom = PAGE.heightInside - rowHeight - PAGE.gap;
  // ponytail: allow up to 5% size reduction to match normal template images; longer invoices get a page of their own.
  if (invoice.height > invoiceRoom && invoiceRoom / invoice.height >= 0.95) invoice = size(ordered[0], invoice.width * invoiceRoom / invoice.height);
  const placeRow = (top) => shots.map((s, index) => ({ ...s, x: PAGE.x + index * PAGE.shotWidth, y: top + rowHeight - s.height }));
  if (invoice.height + PAGE.gap + rowHeight <= PAGE.heightInside) {
    return [[{ ...invoice, x: PAGE.x, y: PAGE.y }, ...placeRow(PAGE.y + invoice.height + PAGE.gap)]];
  }
  // Match the retained template at full width; long invoices get their own page.
  const fitPage = (image, width) => {
    const scale = Math.min(width / image.width, PAGE.heightInside / image.height);
    return { imageId: image.id, x: PAGE.x, y: PAGE.y, width: image.width * scale, height: image.height * scale };
  };
  const pages = [[fitPage(ordered[0], PAGE.widthInside)]];
  if (rowHeight <= PAGE.heightInside) pages.push(placeRow(PAGE.y));
  else ordered.slice(1).forEach((image) => pages.push([fitPage(image, PAGE.widthInside)]));
  return pages;
}
