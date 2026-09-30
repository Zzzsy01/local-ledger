import test from 'node:test';
import assert from 'node:assert/strict';
import { classify, parseMoney, parseRecognizedAmount, formatMoney, findAmountRegion, makeLayout, PAGE } from '../src/rules.js';

test('bill mentioning Taobao remains a payment bill', () => {
  assert.equal(classify('淘宝平台商户 支付时间 当前状态 支付成功 交易单号 商户单号'), 'payment');
  assert.equal(classify('交易成功 实付款 订单信息 收货信息 再买一单'), 'taobao');
  assert.equal(classify('电子发票 发票号码 纳税人识别号 价税合计'), 'invoice');
  assert.equal(classify('淘宝 商品 12.14'), null);
});

test('real paid amount beats product price and discount, and ambiguity stays empty', () => {
  assert.equal(parseMoney('￥1,061.05'), 106105);
  assert.equal(parseMoney('12.345'), null);
  assert.equal(parseMoney('-12.14'), null);
  assert.equal(formatMoney(1214), '12.14');
  assert.equal(parseRecognizedAmount('63.8-\n', 'taobao'), 6380);
  assert.equal(parseRecognizedAmount('-63.80\n', 'payment'), 6380);
  assert.equal(parseRecognizedAmount('-63.80', 'taobao'), null);
  assert.equal(parseRecognizedAmount('63.8.0', 'taobao'), null);
  assert.equal(parseRecognizedAmount('03.8', 'taobao'), null);
  assert.equal(parseRecognizedAmount('YD4.71', 'taobao'), null);
  const region = { x0: 900, x1: 1150, y0: 1570, y1: 1630 };
  const lines = [
    { text: '实付价 ¥4.07', bbox: { x0: 300, x1: 800, y0: 1300, y1: 1345 } },
    { text: '实付款 共减¥3', bbox: { x0: 50, x1: 600, y0: 1580, y1: 1630 } },
    { text: 'YD4.71', bbox: region },
  ];
  assert.equal(findAmountRegion('taobao', { lines }), region);
  assert.equal(findAmountRegion('taobao', { lines: [lines[0]] }), null);
  assert.equal(findAmountRegion('taobao', { lines: [...lines, { ...lines[1] }] }), null);
  assert.equal(findAmountRegion('taobao', { lines: [{ text: '实付款 ¥54.71', bbox: region, words: [{ text: '¥54.71', bbox: region }] }] }), region);
  const bill = [{ text: '-54.71', bbox: region }, { text: '2026-06-10 14:17:10', bbox: region }];
  assert.equal(findAmountRegion('payment', { lines: bill }), region);
  assert.equal(findAmountRegion('payment', { lines: [...bill, { text: '-19.47', bbox: region }] }), null);
  assert.equal(findAmountRegion('payment', { lines: [bill[1]] }), null);
});

test('shuffled images reproduce the template, long invoice separates, all images fit', () => {
  const images = [
    { id: 2, role: 'payment', width: 1080, height: 2400 },
    { id: 0, role: 'invoice', width: 1800, height: 1200 },
    { id: 1, role: 'taobao', width: 1080, height: 2400 },
  ];
  assert.equal(makeLayout(images).length, 1);
  const actual = images.map((i) => i.role === 'invoice' ? { ...i, width: 1260, height: 861 } : { ...i, width: 1260, height: 2800 });
  assert.equal(makeLayout(actual).length, 1);
  images[1].height = 2600;
  const pages = makeLayout(images);
  assert.equal(pages.length, 2);
  assert.deepEqual(pages.flat().map((i) => i.imageId), [0, 1, 2]);
  for (const item of pages.flat()) {
    assert.ok(item.x >= PAGE.x && item.y >= PAGE.y);
    assert.ok(item.x + item.width <= PAGE.width - PAGE.x + 0.001);
    assert.ok(item.y + item.height <= PAGE.height - PAGE.y + 0.001);
    const image = images.find((i) => i.id === item.imageId);
    assert.ok(Math.abs(item.width / item.height - image.width / image.height) < 1e-8);
  }
  images[0].role = 'taobao';
  assert.throws(() => makeLayout(images), /分别确认/);
});
