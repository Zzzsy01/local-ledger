export async function loadImages(files) {
  if (files.length !== 3) throw new Error('请一次选择三张图片。');
  const images = [];
  try {
    for (const [id, file] of files.entries()) {
      if (file.size > 20 * 1024 * 1024) throw new Error(`${file.name} 超过 20 MB，请换一张较小的截图。`);
      const bytes = new Uint8Array(await file.arrayBuffer());
      const jpg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
      const png = [137, 80, 78, 71, 13, 10, 26, 10].every((n, i) => bytes[i] === n);
      if (!jpg && !png) throw new Error(`${file.name} 不是有效的 JPG 或 PNG。`);
      const url = URL.createObjectURL(new Blob([bytes], { type: jpg ? 'image/jpeg' : 'image/png' }));
      const element = new Image();
      element.src = url;
      images.push({ id, name: file.name, bytes, url, element, format: jpg ? 'jpg' : 'png', role: null, ocr: null });
      await element.decode();
      Object.assign(images.at(-1), { width: element.naturalWidth, height: element.naturalHeight });
    }
    return images;
  } catch (error) {
    releaseImages(images);
    throw error;
  }
}

export function releaseImages(images) {
  images.forEach((image) => URL.revokeObjectURL(image.url));
}
