import json, struct, zlib, os
A = 'src/main/resources/assets/taczbosses'

# ---------- textures (flat colours, replace with real art later) ----------
def png(path, w, h, px):
    raw = b''.join(b'\x00' + b''.join(bytes(px(x, y)) for x in range(w)) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
         + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)
base = [(84,98,58),(150,130,80),(70,72,76),(110,60,50),(40,50,90)]
for i, c in enumerate(base):
    def px(x, y, c=c):
        if 8 <= x < 16 and 11 <= y < 14: return (226,75,58,255)    # red visor strip on the head's front face
        if y < 16 and x < 32: return (c[0]//2+30, c[1]//2+30, c[2]//2+30, 255)
        return (c[0], c[1], c[2], 255)
    png(f'{A}/textures/entity/boss_{i+1}.png', 64, 64, px)
print('assets ok')
