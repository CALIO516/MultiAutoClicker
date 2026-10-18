#!/usr/bin/env python3
"""Offline curation/composition of CC0 licensed Pixel Frog and Pixel-Boy art.
Build-time only. Generates 200 uniquely animated 64x64 GIF assets.
"""
import argparse, json, math, random, re, zipfile
from pathlib import Path
from collections import Counter
from PIL import Image, ImageDraw, ImageFont, ImageChops

P=argparse.ArgumentParser();P.add_argument('--frog',required=True);P.add_argument('--ninja',required=True);P.add_argument('--output',required=True);A=P.parse_args()
FROG=Path(A.frog)/'PixelAdventure';NINJA=Path(A.ninja);OUT=Path(A.output);OUT.mkdir(parents=True,exist_ok=True)
SIZE=64;NF=8;MS=110
MONSTER={
'AngryPig':'멧돼지','Bat':'박쥐','Bee':'벌','BlueBird':'파랑새','Bunny':'토끼','Chameleon':'카멜레온','Chicken':'닭','Duck':'오리','FatBird':'큰 새','Ghost':'유령','Mushroom':'버섯','Plant':'식물 몬스터','Radish':'무 요정','Rino':'코뿔소','Rocks':'바위 몬스터','Skull':'해골','Slime':'슬라임','Snail':'달팽이','Trunk':'나무 정령','Turtle':'거북이'}
HERO={'Mask Dude':'가면 모험가','Ninja Frog':'닌자 개구리','Pink Man':'핑크 히어로','Virtual Guy':'사이버 히어로'}
VERB={'Idle':'대기','Run':'달리기','Walk':'걷기','Hit':'피격','Attack':'공격','Fall':'낙하','Jump':'점프','Double Jump':'더블 점프','Wall Jump':'벽 점프','Flying':'비행','Ground':'착지','Appear':'출현','Desappear':'사라짐','Ceiling In':'착지','Ceiling Out':'출발','Jump Anticipation':'점프 준비','Hit Wall':'돌진','Break':'파괴','On':'가동','Off':'정지','Blink':'눈깜박임'}
PAL=[('#111e38','#1f4062','#244464','#224a45','#56a887','#80d7c9'),('#21162d','#4e3053','#413358','#4a4559','#d1a6bd','#ffcd89'),('#0d2040','#1c4166','#213653','#204b58','#389eaf','#d4f9d0'),('#1a1e33','#363d60','#334b54','#416754','#70ae81','#d9f3ab'),('#10152d','#25294d','#283153','#413d62','#9470b8','#e3b0eb'),('#201927','#603f44','#543f50','#765d49','#d69a6a','#fae0ac'),('#142b3d','#206077','#256373','#27565e','#79b2ab','#f3e0aa'),('#171b2c','#263954','#384356','#495d62','#9bafb4','#fff0bf')]

def rgb(v):return tuple(bytes.fromhex(v.lstrip('#'))) if isinstance(v,str) else v
def mix(a,b,t):a=rgb(a);b=rgb(b);return tuple(round(q*(1-t)+w*t) for q,w in zip(a,b))
def valid_png(p):
 try:
  im=Image.open(p); return im.width>=10 and im.height>=8
 except Exception:return False

def frame_src(path):
 im=Image.open(path).convert('RGBA'); W,H=im.size
 match=re.search(r'\((\d+)x(\d+)\)',path.name)
 if match:fw,fh=map(int,match.groups())
 elif 'content/character/' in str(path):fw=fh=16
 elif 'content/weapon/' in str(path):fw=fh=16
 elif 'Fruits/' in str(path):fw=fh=32
 else:
  fw=32 if W>=32 and H>=20 else min(W,32)
  fh=32 if H>=32 and W>=20 else min(H,32)
 if not(1<=fw<=W and 1<=fh<=H):return []
 nx=W//fw;ny=H//fh
 # Horizontal sheets, vertical character layouts crop from row 0.
 if nx==1 and ny>=2: positions=[(0,i) for i in range(min(12,ny))]
 else:positions=[(i,0) for i in range(min(16,nx))]
 frames=[]
 for x,y in positions:
  piece=im.crop((x*fw,y*fh,x*fw+fw,y*fh+fh))
  if piece.getchannel('A').getbbox():frames.append(piece)
 if not frames:return []
 return frames

def background(cat,idx,phase):
 seed=idx*43+73;rr=random.Random(seed);palette=PAL[(idx//11+sum(map(ord,cat)))%len(PAL)]
 sky0,sky1,mtn,front,leaf,glow=map(rgb,palette)
 canvas=Image.new('RGB',(SIZE,SIZE));d=ImageDraw.Draw(canvas)
 # Stepped colour bands + subtle dither clusters
 for y in range(SIZE):
  if y<16: base=sky0
  elif y<31:base=mix(sky0,sky1,.50)
  elif y<48:base=sky1
  elif y<54:base=mtn
  else:base=front
  d.line((0,y,63,y),fill=base)
 for k in range(34):
  x,y=rr.randrange(64),rr.randrange(45);color=mix(sky0,sky1,.30 if k%2 else .63)
  if k%4!=0:d.point((x,y),fill=color)
 for k in range(7):
  x=rr.randrange(64); y=rr.randrange(7,39)
  if (k+phase//2)%4==0:
   d.line((x-1,y,x+1,y),fill=glow,width=1);d.line((x,y-1,x,y+1),fill=glow,width=1)
  else:d.point((x,y),fill=mix(glow,sky1,.5))
 # Background silhouettes provide depth without visual noise
 town=('cafe','sign','city','ninja','item','trap')
 if cat in town:
  for k in range(7):
   x= k*11-3;y=rr.randrange(30,47)
   d.rectangle((x,y,x+rr.randrange(9,15),51),fill=mix(mtn,sky1,.45))
   if k%2: d.rectangle((x+3,y+4,x+4,y+6),fill=mix(glow,sky1,.55))
 else:
  d.polygon([(0,49),(0,39),(8,44),(19,32),(27,41),(38,34),(49,42),(59,31),(64,43),(64,52)],fill=mix(mtn,sky1,.6))
  d.polygon([(0,48),(9,40),(16,43),(26,39),(39,46),(50,38),(64,47),(64,55),(0,55)],fill=mtn)
 # Ground shadow/tiles
 d.rectangle((0,53,63,63),fill=front)
 d.line((0,52,63,52),fill=mix(leaf,front,.42),width=2)
 for j in range(19):
  x=rr.randrange(64);y=rr.randrange(55,64)
  d.line((x,y,x+rr.randrange(1,4),y),fill=mix(leaf,front,.35 if j%2 else .57))
 return canvas,palette

def decorate(im,cat,idx,phase,palette):
 d=ImageDraw.Draw(im)
 glow=rgb(palette[5]);wave=(phase%4);rng=random.Random(idx+11)
 if cat in ('hero','enemy','ninja'):
  for k in range(3):
   x=(idx*19+k*23+phase*(k%2))%61+1;y=(idx*13+k*17)%34+7
   if (phase+k)%3==0:d.line((x-1,y,x+1,y),fill=glow);d.line((x,y-1,x,y+1),fill=glow)
 elif cat in ('cafe','sign'):
  for k in range(4):
   x=(idx*5+k*17)%59+2;y=11+(k*8)%28
   if (k+phase)%4==0:d.line((x-1,y,x+1,y),fill=glow)
 return im

def compose(path,cat,idx):
 sheets=frame_src(path)
 if not sheets:return None
 # Analyze alpha union to maintain stable anchor and avoid frame-to-frame jitter.
 boxes=[a.getchannel('A').getbbox() for a in sheets]
 ub=(min(b[0] for b in boxes),min(b[1] for b in boxes),max(b[2] for b in boxes),max(b[3] for b in boxes))
 fw,fh=sheets[0].size
 bound_w=ub[2]-ub[0];bound_h=ub[3]-ub[1]
 if bound_w<3 or bound_h<3:return None
 targetw=48 if cat!='ninja' else 39;targeth=43 if cat!='ninja' else 39
 scale=min(targetw/bound_w,targeth/bound_h,2.5)
 if bound_w>50:scale=min(scale,1.0)
 iw=max(1,round(bound_w*scale));ih=max(1,round(bound_h*scale))
 images=[]
 for phase in range(8):
  frame=sheets[round(phase*(len(sheets)-1)/7)] if len(sheets)>1 else sheets[0]
  sprite=frame.crop(ub).resize((iw,ih),Image.Resampling.NEAREST)
  if not sprite.getchannel('A').getbbox():sprite=sheets[0].crop(ub).resize((iw,ih),Image.Resampling.NEAREST)
  base,palette=background(cat,idx,phase)
  layer=Image.new('RGBA',(64,64),(0,0,0,0))
  lx=(64-iw)//2;ly=(53-ih)+(0 if 'Flying' not in path.name else -9)
  # Ground shadow
  dr=ImageDraw.Draw(layer)
  if cat!='ninja':dr.ellipse((max(2,lx+4),52,min(61,lx+iw-2),56),fill=(4,12,22,130))
  layer.alpha_composite(sprite,(lx,max(0,ly)))
  base=Image.alpha_composite(base.convert('RGBA'),layer).convert('RGB')
  images.append(decorate(base,cat,idx,phase,palette))
 return images

# Pixel-perfect 5x7 sign lettering, measured in advance with strict fit; no clipping.
FONT={
'A':['01110','10001','10001','11111','10001','10001','10001'],'B':['11110','10001','10001','11110','10001','10001','11110'],
'C':['01111','10000','10000','10000','10000','10000','01111'],'D':['11110','10001','10001','10001','10001','10001','11110'],
'E':['11111','10000','10000','11110','10000','10000','11111'],'F':['11111','10000','10000','11110','10000','10000','10000'],
'G':['01111','10000','10000','10111','10001','10001','01111'],'H':['10001','10001','10001','11111','10001','10001','10001'],
'I':['11111','00100','00100','00100','00100','00100','11111'],'J':['00111','00010','00010','00010','10010','10010','01100'],
'K':['10001','10010','10100','11000','10100','10010','10001'],'L':['10000','10000','10000','10000','10000','10000','11111'],
'M':['10001','11011','10101','10101','10001','10001','10001'],'N':['10001','11001','10101','10011','10001','10001','10001'],
'O':['01110','10001','10001','10001','10001','10001','01110'],'P':['11110','10001','10001','11110','10000','10000','10000'],
'Q':['01110','10001','10001','10001','10101','10010','01101'],'R':['11110','10001','10001','11110','10100','10010','10001'],
'S':['01111','10000','10000','01110','00001','00001','11110'],'T':['11111','00100','00100','00100','00100','00100','00100'],
'U':['10001','10001','10001','10001','10001','10001','01110'],'V':['10001','10001','10001','10001','10001','01010','00100'],
'W':['10001','10001','10001','10101','10101','10101','01010'],'X':['10001','10001','01010','00100','01010','10001','10001'],
'Y':['10001','10001','01010','00100','00100','00100','00100'],'Z':['11111','00001','00010','00100','01000','10000','11111'],
'0':['01110','10011','10101','10101','11001','10001','01110'],'1':['00100','01100','00100','00100','00100','00100','01110'],
'2':['01110','10001','00001','00010','00100','01000','11111'],'4':['10010','10010','10010','11111','00010','00010','00010'],
' ':['00000']*7
}

def glyph_text(d,word,cx,cy,color):
 available=54
 scale=2 if (len(word)*6-1)*2<=available else 1
 width=(len(word)*6-1)*scale
 x0=cx-width//2; y0=cy-7*scale//2
 for k,ch in enumerate(word):
  g=FONT.get(ch,FONT[' ']);bx=x0+k*6*scale
  for y,line in enumerate(g):
   for x,on in enumerate(line):
    if on=='1':d.rectangle((bx+x*scale,y0+y*scale,bx+(x+1)*scale-1,y0+(y+1)*scale-1),fill=color)
 return width

def coffee(idx):
 words=['에스프레소 머신','라떼 한 잔','원두 그라인더','드립 포트','테이크아웃 컵','빵 굽는 오븐','크루아상','핸드드립','머그잔','카페 네온']
 images=[]
 for t in range(8):
  im,pal=background('cafe',idx+180,t);d=ImageDraw.Draw(im)
  # Service counter with dark walnut wood + coherent highlights
  d.rectangle((5,46,58,55),fill='#3b2e38');d.line((5,46,58,46),fill='#b98865',width=2);d.line((5,54,58,54),fill='#5a4752')
  if idx in (0,2,5):
   d.rectangle((15,17,48,47),fill='#161c2d');d.rectangle((17,17,46,20),fill='#7b879d');d.rectangle((18,21,44,42),fill='#536c78')
   d.rectangle((24,25,38,36),fill='#251c28');d.rectangle((26,27,36,30),fill='#ffcd8a');d.rectangle((24,38,40,41),fill='#d7e4f2')
   if idx==2:
    d.ellipse((22,20,39,29),fill='#5f4550',outline='#d2a77d',width=2)
    for s in range(8):d.point(((28+s*3)%36+1,22+(s%4)),fill='#e7b576')
   if idx==5:
    d.ellipse((21,25,43,39),fill='#bf7a45');d.arc((21,25,43,39),10,165,fill='#ffe2a1',width=2)
  elif idx in (1,4,8):
   d.polygon([(18,23),(46,23),(42,45),(22,45)],fill='#d9e4e9');d.line((19,24,45,24),fill='#ffffff',width=2)
   d.ellipse((18,19,46,28),fill='#a16a46',outline='#fff0c6',width=2)
   d.arc((45,28,56,41),270,100,fill='#e7edf5',width=3)
   d.line((25,23,40,23),fill='#faeac9',width=2)
  elif idx in (3,7):
   d.polygon([(16,33),(33,19),(46,33),(36,45),(18,45)],fill='#bdc8d0');d.polygon([(16,33),(33,19),(46,33)],fill='#e4eef4');d.ellipse((30,21,36,27),fill='#6d778d')
   d.line((42,32,52,28,55,32),fill='#f9daa6',width=2)
  elif idx==6:
   d.polygon([(12,40),(18,28),(29,20),(39,22),(50,38),(47,45),(14,45)],fill='#cf8653');d.arc((17,24,44,42),182,341,fill='#f8d393',width=3);d.line((15,42,45,42),fill='#8d442d',width=3)
  else:
   glyph_text(d,'CAFE',32,29,'#f5d6a3' if t%4 else '#81ebd4')
   d.rectangle((8,12,56,47),outline='#bf9468',width=2)
  # Steam animation
  if idx in (0,1,3,4,7,8):
   for i in range(3):
    y=15-i*4-(t+i)%4;x=26+i*5+(1 if (t+i)%3==0 else 0)
    d.line((x,y+3,x+1,y,x+2,y-2),fill='#dee9e3',width=1)
  images.append(im)
 return words[idx],images

def signage(idx):
 labels=['OPEN','CLOSED','SALE','EXIT','WIFI','WC','24H','PARK','INFO','PICKUP']
 words=['영업 중 OPEN','영업 종료 CLOSED','세일 SALE','출구 EXIT','무료 WIFI','화장실 WC','24시간 24H','주차 PARK','안내 INFO','픽업 PICKUP']
 code=labels[idx];frames=[]
 for t in range(8):
  im,pal=background('sign',idx+190,t);d=ImageDraw.Draw(im)
  d.rectangle((3,7,60,48),fill='#152337',outline='#465f77',width=2)
  d.rectangle((7,11,56,44),fill='#1c2d43',outline='#6792aa',width=1)
  d.rectangle((9,13,54,42),fill='#1c2b40')
  # Visible hanging hooks and accurate pixel typography
  d.line((16,3,16,10),fill='#b7c8d6',width=2);d.line((47,3,47,10),fill='#b7c8d6',width=2)
  glow='#ffce72' if idx in (0,2,6,9) else '#81e7de'
  if idx==1:glow='#a8c0e0'
  glyph_text(d,code,32,28,glow if t not in (4,5) else mix(glow,'#2a4253',.25))
  d.rectangle((7,45,57,48),fill='#243448');d.line((10,48,54,48),fill='#3c6780')
  if t%4==0:d.point((10,16),fill='#d9fcff');d.point((53,16),fill='#d9fcff')
  frames.append(im)
 return words[idx],frames

def choose():
 def collect(root,segment,limit,skip=None):
  paths=sorted((root/segment).rglob('*.png'))
  paths=[x for x in paths if x.is_file() and not (skip and skip(x)) and valid_png(x)]
  return paths[:limit]
 heroes=collect(FROG,'Main Characters',28,lambda x:x.parent==FROG/'Main Characters')
 enemies=collect(FROG,'Enemies',80,lambda x: any(s in x.name.lower() for s in ('pieces','particles','bullet.','leafs')))
 items=collect(FROG,'Items',22,lambda x:'Collected' in x.name)
 traps=collect(FROG,'Traps',38,lambda x:'Particle' in x.name or x.name.lower()=='chain.png')
 ninja=collect(NINJA,'content/character',8,lambda x:'Shadow' in x.name)+collect(NINJA,'content/weapon',12,lambda x:'in_hand' in x.name)
 # Discard sprite sheets lacking meaningful foreground after examining first cell.
 def good(a):
  try:
   b=frame_src(a)
   if not b:return False
   box=b[0].getchannel('A').getbbox()
   return bool(box and (box[2]-box[0]>=5) and (box[3]-box[1]>=4))
  except Exception:return False
 sets={"hero":([x for x in heroes if good(x)],28),"enemy":([x for x in enemies if good(x)],80),"item":([x for x in items if good(x)],22),"trap":([x for x in traps if good(x)],38),"ninja":([x for x in ninja if good(x)],12)}
 specs=[]
 for k,(l,target) in sets.items():
  # Fill to target from pool if curations excluded some.
  allpaths=[]
  sections={'hero':['Main Characters'],'enemy':['Enemies'],'item':['Items'],'trap':['Traps'],'ninja':['content/character','content/weapon','content/destroyable']}
  root=NINJA if k=='ninja' else FROG
  for sec in sections[k]:allpaths.extend(sorted((root/sec).rglob('*.png')))
  for p in allpaths:
   if len(l)>=target:break
   if p not in l and good(p):l.append(p)
  if len(l)<target:raise RuntimeError(f'Not enough usable CC0 {k} assets {len(l)}<{target}')
  specs.extend((k,x) for x in l[:target])
 if len(specs)!=180:raise RuntimeError('source count '+str(len(specs)))
 return specs

def classify(key,path):
 name=path.stem.split(' (')[0].replace('  ',' ').strip()
 if key=='hero':
  who=HERO.get(path.parent.name,path.parent.name); verb=VERB.get(name,name)
  return who+' · '+verb, ('heroaction' if 'Jump' in name or name in ('Run','Fall') else 'hero')
 if key=='enemy':
  who=MONSTER.get(path.parent.name,path.parent.name); verb=VERB.get(name,name)
  cat='fly' if path.parent.name in ('Bat','Bee','BlueBird','FatBird') else ('ghost' if path.parent.name in ('Ghost','Skull','Rocks') else 'enemy')
  return who+' · '+verb,cat
 if key=='trap':
  tr={'Arrow':'화살','Blocks':'바위 블록','Falling Platforms':'추락 발판','Fan':'환풍기','Fire':'불꽃','Platforms':'움직이는 플랫폼','Rock Head':'바위 얼굴','Sand Mud Ice':'얼음·모래','Saw':'톱날','Spike Head':'가시 머리','Spiked Ball':'철구','Spikes':'가시','Trampoline':'점프 패드'}
  return tr.get(path.parent.name,path.parent.name)+' · '+VERB.get(name,name),'trap'
 if key=='item':
  if 'Fruits' in str(path):return {'Apple':'사과','Bananas':'바나나','Cherries':'체리','Kiwi':'키위','Melon':'멜론','Orange':'오렌지','Pineapple':'파인애플','Strawberry':'딸기'}.get(name,name),'fruit'
  return '보물 '+path.parent.name+' · '+VERB.get(name,name),'item'
 if key=='ninja':
  value=path.parent.name
  return {'ninja_blue':'푸른 닌자','samurai_blue':'푸른 사무라이','samurai_green':'녹색 사무라이','pig':'마을 돼지'}.get(value,value.replace('_',' ').title())+' · 16비트','ninja'
 return name,'other'

def export_gif(frames,target):
 assert len(frames)==8 and all(a.size==(64,64) for a in frames)
 quant=[im.quantize(colors=128,method=Image.Quantize.FASTOCTREE) for im in frames]
 quant[0].save(target,save_all=True,append_images=quant[1:],optimize=False,duration=[MS]*8,loop=0,disposal=2)
 with Image.open(target) as gif:
  if gif.n_frames!=8:raise RuntimeError(f'GIF frames lost {target}: {gif.n_frames}')
  uniq={gif.seek(i) or gif.convert('RGB').tobytes() for i in range(8)}
  if len(uniq)<3:raise RuntimeError(f'Not enough animation variation in {target}: {len(uniq)}')

specs=choose();categories={};items=[];names=set();sources=Counter()
for idx in range(200):
 if idx<180:
  k,source=specs[idx]; title,cat=classify(k,source); frames=compose(source,k,idx)
  if not frames:raise RuntimeError(f'Cannot decode {source}')
  sources['Pixel Frog' if k!='ninja' else 'Pixel-Boy & AAA']+=1
 elif idx<190:
  title,frames=coffee(idx-180);cat='cafe';sources['Original compositions']+=1
 else:
  title,frames=signage(idx-190);cat='sign';sources['Original compositions']+=1
 if title in names:title+=f' {idx+1}'
 names.add(title)
 f=f'premium_{idx+1:03d}.gif'; export_gif(frames,OUT/f)
 catname={'hero':'게임 히어로','heroaction':'히어로 액션','enemy':'숲과 필드 몬스터','fly':'비행 몬스터','ghost':'던전 몬스터','item':'게임 아이템','fruit':'파워업 과일','trap':'액션 함정·장치','ninja':'판타지 RPG','cafe':'카페·베이커리','sign':'매장·안내 표지'}[cat]
 categories[cat]=catname
 items.append({'id':f'premium_{idx+1:03d}','name':title,'category':cat,'categoryName':catname,'file':f,'frames':8,'durationMs':880,'resolution':'64x64','source': 'Pixel Frog / CC0' if idx<180 and k!='ninja' else ('Pixel-Boy & AAA / CC0' if idx<180 else 'Original')})
listcats=[{'id':key,'name':val,'count':sum(1 for x in items if x['category']==key)} for key,val in categories.items()]
(OUT.parent/'gallery-catalog.json').write_text(json.dumps({'categories':listcats,'samples':items},ensure_ascii=False,indent=2),encoding='utf-8')
(OUT.parent/'ASSET_LICENSES.md').write_text('PixelMatrix Studio 2.1 - CC0 Asset Credits\n\nSource 1: Pixel Frog, Pixel Adventure - https://pixelfrog-assets.itch.io/pixel-adventure-1 (Creative Commons Zero 1.0)\nSource mirror used for automated extraction: https://github.com/marpor/PixelAdventure\n\nSource 2: Pixel-Boy & AAA, Ninja Adventure - https://pixel-boy.itch.io/ninja-adventure-asset-pack (Creative Commons Zero 1.0)\nSource: https://github.com/pixel-boy/NinjaAdventure\n\nImages are remixed into original animated scenes (backgrounds, motion accents). Original cafe and signage artwork belongs to this project. No copyrighted Sonic, Kirby, or other protected game-character sprites are included.\n',encoding='utf-8')
print(json.dumps({'items':len(items),'categories':len(listcats),'sources':dict(sources),'assets_bytes':sum(p.stat().st_size for p in OUT.glob('*.gif'))},ensure_ascii=False))
# Embed the generated catalog in the offline WebView; it must match bundled assets.
html=OUT.parent/'index.html'
if not html.is_file():raise RuntimeError('Missing app editor HTML '+str(html))
original=html.read_text(encoding='utf-8')
new_catalog='const GALLERY_CATALOG='+json.dumps({'categories':listcats,'samples':items},ensure_ascii=False,separators=(',',':'))+';'
matched=False
lines=[]
for line in original.splitlines():
 if line.startswith('const GALLERY_CATALOG='):
  lines.append(new_catalog);matched=True
 else:lines.append(line)
if not matched:raise RuntimeError('No embedded catalog to update')
html.write_text('\n'.join(lines)+'\n',encoding='utf-8')
# Create one ready-to-use gallery ZIP with categories and license notice.
zipout=OUT.parent.parent.parent.parent.parent/'PixelMatrix_Studio_Premium_200_GIF.zip'
with zipfile.ZipFile(zipout,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for it in items:z.write(OUT/it['file'],f"{it['categoryName']}/{it['file']}")
 z.write(OUT.parent/'gallery-catalog.json','gallery-catalog.json')
 z.write(OUT.parent/'ASSET_LICENSES.md','ASSET_LICENSES.md')
print('generated_zip',zipout,zipout.stat().st_size)