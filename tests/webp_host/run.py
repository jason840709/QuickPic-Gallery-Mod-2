#!/usr/bin/env python3
"""Production WebP/JNI host tests. Android doubles do not verify Android codec behavior.
Synthetic WebP wrappers are independently decoded by Pillow/libwebp, not the doubles.
"""
from pathlib import Path
from io import BytesIO
import argparse, struct, subprocess, tempfile
from PIL import Image, ImageCms, features
ROOT = Path(__file__).resolve().parents[2]
def chunk(tag, payload):
    return tag + struct.pack('<I', len(payload)) + payload + bytes(len(payload) & 1)
def u24(n): return n.to_bytes(3, 'little')
def riff(chunks): return b'RIFF' + struct.pack('<I', len(chunks) + 4) + b'WEBP' + chunks
def frame(image, x, y, delay, flags, lossy=False):
    buf=BytesIO(); image.save(buf, format='WEBP', lossless=not lossy, quality=100, exact=True)
    encoded=buf.getvalue(); chunks=[]; p=12
    while p<len(encoded):
        size=int.from_bytes(encoded[p+4:p+8], 'little'); end=p+8+size+(size&1)
        if encoded[p:p+4] in (b'ALPH',b'VP8 ',b'VP8L'): chunks.append(encoded[p:end])
        p=end
    pixels=b''.join(struct.pack('<I',(a<<24)|(r<<16)|(g<<8)|b) for r,g,b,a in image.get_flattened_data())
    chunks.append(chunk(b'RAW ',struct.pack('<HH',*image.size)+pixels))
    chunks.append(chunk(b'odd!',b'abc'))
    header=u24(x//2)+u24(y//2)+u24(image.width-1)+u24(image.height-1)+u24(delay)+bytes([flags])
    return chunk(b'ANMF', header+b''.join(chunks))
def fixtures(folder):
    specifications=[(4,2,(255,0,0,255),0,0,11,2,False),
                    (2,2,(0,0,255,128),2,0,0,1,True),
                    (2,2,(255,255,0,128),0,0,10,2,False),
                    (2,2,(0,0,0,0),0,0,23,1,False),
                    (2,2,(0,0,0,0),2,0,33,2,False)]
    images=[]; frames=[]
    for w,h,color,x,y,d,f,lossy in specifications:
        image=Image.new('RGBA',(w,h),color); images.append(image)
        frames.append(frame(image,x,y,d,f,lossy))
    vp8x=chunk(b'VP8X',bytes([0x12,0,0,0])+u24(3)+u24(1))
    anim=chunk(b'ANIM',bytes([0x10,0x20,0x40,0x80])+struct.pack('<H',2))
    (folder/'animation.webp').write_bytes(riff(vp8x+anim+b''.join(frames)))
    (folder/'single.webp').write_bytes(riff(vp8x+anim+frames[0]))
    profile=ImageCms.ImageCmsProfile(ImageCms.createProfile('sRGB')).tobytes()
    (folder/'profile.icc').write_bytes(profile)
    (folder/'profile.webp').write_bytes(riff(chunk(b'VP8X',bytes([0x32,0,0,0])+u24(3)+u24(1))+chunk(b'ICCP',profile)+anim+frames[0]+frames[1]))
    partial=[frame(Image.new('RGBA',(2,2),(255,0,0,255)),0,0,20,2),frame(Image.new('RGBA',(2,2),(0,0,255,255)),2,0,20,2)]
    (folder/'partial.webp').write_bytes(riff(vp8x+chunk(b'ANIM',bytes([0x10,0x20,0x40,0x80,0,0]))+b''.join(partial)))
    out=BytesIO(); images[0].save(out,format='WEBP',lossless=True)
    (folder/'still.webp').write_bytes(out.getvalue())
    (folder/'still-large.webp').write_bytes(riff(out.getvalue()[12:]+chunk(b'JUNK',bytes(2*1024*1024))))
    return images

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--phase',default='all')
    ap.add_argument('--metadata',type=Path);args=ap.parse_args()
    assert features.check('webp'), 'Pillow/libwebp support is required'
    with tempfile.TemporaryDirectory(prefix='quickpic-webp-') as td:
        temp=Path(td); images=fixtures(temp); classes=temp/'classes'; classes.mkdir()
        sources=sorted((ROOT/'tests/webp_host').rglob('*.java'))
        sources += [ROOT/'src/com/alensw/jni/JniUtils.java']
        helper=ROOT/'src/com/alensw/jni/WebPAnimation.java'
        if helper.exists(): sources.append(helper)
        subprocess.run(['javac','--release','8','-Xlint:-options','-d',str(classes),*map(str,sources)],check=True)
        command=['java','-cp',str(classes),'com.alensw.jni.WebPHostTest',str(temp),args.phase]
        if args.metadata: command.append(str(args.metadata))
        subprocess.run(command,check=True)
        if args.phase in ('wrapper','all'):
            for index,image in enumerate(images):
                with Image.open(temp/f'wrapped-{index}.webp') as result:
                    decoded=result.convert('RGBA'); assert decoded.size==image.size
                    if index==1: # lossy VP8 RGB permits bounded color error; alpha stays exact.
                        for actual,expected in zip(decoded.get_flattened_data(),image.get_flattened_data()):
                            assert actual[3]==expected[3] and all(abs(actual[i]-expected[i])<=3 for i in range(3)),(actual,expected)
                    else: assert decoded.tobytes()==image.tobytes(),f'wrapper pixels {index}'
            for index in (0,1):
                with Image.open(temp/f'wrapped-profile-{index}.webp') as result:
                    assert result.info.get('icc_profile')==(temp/'profile.icc').read_bytes(), 'global ICC profile preserved'
                    assert result.size==images[index].size
            print('Pillow/libwebp: 5 standalone ANMF wrappers decoded with expected synthetic pixels; 2 ICC wrappers preserve profile')
if __name__=='__main__': main()
