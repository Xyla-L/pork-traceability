"""
生成「模拟上报」用的演示凭证照片。

为什么要有它：演示设备上报时，接种凭证照片是必填的，但演示环境没有真机、也没有真实照片。
直接让用户随手选一张本地照片，会出现「把别人的真实照片塞进演示库」的问题。
所以预置一张一眼就能看出是合成演示图的图片（醒目 DEMO 水印 + 明确声明），
点「填充示例值」时自动上传并回填 fileId；文件服务按 sha256 去重，反复点也不会堆文件。

用法：D:/python/python.exe logs/make-demo-voucher.py
输出：web-admin/public/demo/vaccine-voucher.png
"""
import os
import struct
import zlib

W, H = 640, 480

FONT = {
    'A': ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    'C': ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    'D': ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'H': ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'K': ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'M': ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    'N': ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'V': ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    'Y': ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    '0': ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    '2': ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    '6': ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    ' ': ["00000", "00000", "00000", "00000", "00000", "00000", "00000"],
    '-': ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
}

canvas = bytearray(W * H * 3)


def fill(color):
    r, g, b = color
    for i in range(W * H):
        canvas[i * 3] = r
        canvas[i * 3 + 1] = g
        canvas[i * 3 + 2] = b


def rect(x0, y0, x1, y1, color):
    r, g, b = color
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(W, x1), min(H, y1)
    for y in range(y0, y1):
        base = y * W * 3
        for x in range(x0, x1):
            i = base + x * 3
            canvas[i] = r
            canvas[i + 1] = g
            canvas[i + 2] = b


def text(s, x, y, scale, color):
    """按 5x7 点阵逐像素放大绘制；不依赖任何字体文件"""
    r, g, b = color
    cx = x
    for ch in s.upper():
        glyph = FONT.get(ch, FONT[' '])
        for row, bits in enumerate(glyph):
            for col, bit in enumerate(bits):
                if bit == '1':
                    px0 = cx + col * scale
                    py0 = y + row * scale
                    for dy in range(scale):
                        py = py0 + dy
                        if py >= H:
                            continue
                        base = py * W * 3
                        for dx in range(scale):
                            px = px0 + dx
                            if px >= W:
                                continue
                            i = base + px * 3
                            canvas[i] = r
                            canvas[i + 1] = g
                            canvas[i + 2] = b
        cx += 6 * scale


def text_width(s, scale):
    return len(s) * 6 * scale


def center_text(s, y, scale, color):
    text(s, max(0, (W - text_width(s, scale)) // 2), y, scale, color)


# 底：浅灰白
fill((245, 247, 250))

# 顶部深蓝标题条
rect(0, 0, W, 64, (44, 62, 80))
center_text('PORK TRACE SYSTEM', 22, 3, (236, 240, 241))

# 主体：演示声明（黑字，最大号）
center_text('DEMO VOUCHER', 110, 6, (44, 62, 80))
center_text('VACCINE PHOTO', 168, 4, (52, 73, 94))

# 中间：一个简化的疫苗瓶 + 注射器示意图（纯几何图形，避免任何真实场景照片）
# 瓶身
rect(232, 250, 300, 350, (200, 214, 229))
rect(238, 258, 294, 344, (236, 244, 250))
# 瓶口
rect(252, 236, 280, 252, (120, 144, 156))
# 瓶内液面
rect(238, 310, 294, 344, (133, 193, 233))
# 标签
rect(240, 268, 292, 300, (255, 255, 255))
text('VAC', 246, 276, 2, (231, 76, 60))
# 注射器针筒
rect(330, 268, 440, 296, (236, 240, 241))
rect(330, 268, 440, 274, (189, 195, 199))
rect(360, 276, 412, 288, (133, 193, 233))
# 针头
rect(440, 278, 500, 284, (127, 140, 141))

# 醒目警示横幅
rect(0, 372, W, 412, (241, 196, 15))
center_text('NOT A REAL RECORD', 380, 3, (146, 43, 0))

# 底部说明
center_text('SAMPLE DATA FOR DEMONSTRATION ONLY - 2026', 436, 1, (127, 140, 141))

# 斜向 DEMO 水印：只画稀疏网格点，肉眼可见但不遮挡内容
for y in range(70, 370, 4):
    for x in range(0, W, 4):
        if (x + y) % 84 < 46:
            i = (y * W + x) * 3
            canvas[i] = min(255, canvas[i] + 26)
            canvas[i + 1] = min(255, canvas[i + 1] + 26)
            canvas[i + 2] = min(255, canvas[i + 2] + 26)

raw = b''.join(b'\x00' + bytes(canvas[y * W * 3:(y + 1) * W * 3]) for y in range(H))


def chunk(tag, data):
    body = tag + data
    return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)


ihdr = struct.pack('>IIBBBBB', W, H, 8, 2, 0, 0, 0)
png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', ihdr)
       + chunk(b'IDAT', zlib.compress(raw, 9))
       + chunk(b'IEND', b''))

out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   'web-admin', 'public', 'demo', 'vaccine-voucher.png')
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'wb') as f:
    f.write(png)
print('written', out, len(png), 'bytes')
