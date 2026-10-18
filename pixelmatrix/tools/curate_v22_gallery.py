#!/usr/bin/env python3
"""Create the 10×20 diverse offline v2.2 gallery. Uses previously generated CC0 pixel sprites.
Custom 32px hand-drawn pixel motifs are produced at native LED resolution and
nearest-neighbour scaled to 64px master artwork. No external runtime dependencies.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from collections import defaultdict, Counter
import json, math, random, zipfile, re
BASE=Path('app/src/main/assets')
G=BASE/'gallery'; G.mkdir(parents=True,exist_ok=True)
prior=json.loads((BASE/'gallery-catalog.json').read_text(encoding='utf-8'))['samples']
OUT=G/'v22'
OUT.mkdir(exist_ok=True)
CATS=[('hero','게임 캐릭터'),('monster','몬스터·RPG'),('item','아이템·보물'),('cafe','카페·디저트'),('sign','매장·안내'),('scenery','게임 풍경'),('tech','테크·전자기기'),('daily','생활 사물'),('vehicle','교통·공사'),('nature','날씨·자연')]
TITLES={
'cafe':['커피 원두','캡슐 커피','카페 라떼','치즈 케이크','딸기 타르트','글레이즈 도넛','핫 초콜릿','디저트 접시','바게트','에스프레소 샷'],
'sign':['NEW 신상품','HOT 인기','MENU 메뉴','ORDER 주문','READY 준비','TAKE 포장','FREE 무료','REST 휴식','STOP 정지','SHOP 매장'],
'scenery':['해변 일출','달빛 설산','벚꽃 언덕','바다 등대','숲속 오두막','산속 폭포','왕국의 성','별빛 호수','네온 골목','픽셀 도시 야경','사막 오아시스','구름 위 하늘','눈 내리는 마을','가을 숲','해저 산호','농장 풍차','비 내리는 거리','던전 입구','열차역 플랫폼','석양 바다'],
'tech':['레트로 컴퓨터','노트북','스마트폰','기계식 키보드','게임 콘솔','아케이드 머신','CRT 텔레비전','카세트 테이프','플로피 디스크','헤드폰','카메라','와이파이 공유기','데이터 서버','건전지','스마트워치','로봇','게임패드','프린터','스피커','레코드 턴테이블'],
'daily':['책 더미','스탠드 조명','우산','가죽 가방','선물 상자','고양이 인형','초록 화분','벽시계','탁상 알람','양초','열쇠','자물쇠','쇼핑백','의자','소파','거울','편지 봉투','안경','뜨개 모자','운동화'],
'vehicle':['시티 택시','시내버스','빨간 스포츠카','지하철','고속열차','비행기','로켓','자전거','오토바이','전동 킥보드','범선','픽업 트럭','소방차','건설 굴삭기','불도저','공사 표지','안전 콘','신호등','타워 크레인','이사 트럭'],
'nature':['맑은 태양','구름 사이 햇살','봄비','천둥 번개','함박눈','무지개','초승달','별똥별','소나무','단풍나무','벚꽃','빨간 튤립','버섯','바다 파도','열대어','나비','밤하늘 별','해바라기','낙엽 바람','산속 캠프파이어']
}
# Baked 3x5 pixel type. Use real bitmaps, never text rasterization in LED graphics.
FONT={'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],'C':['011','100','100','100','011'],'D':['110','101','101','101','110'],'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],'G':['011','100','101','101','011'],'H':['101','101','111','101','101'],'I':['111','010','010','010','111'],'J':['001','001','001','101','010'],'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],'M':['101','111','111','101','101'],'N':['101','111','111','111','101'],'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],'Q':['010','101','101','111','011'],'R':['110','101','110','101','101'],'S':['011','100','010','001','110'],'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],'W':['101','101','111','111','101'],'X':['101','101','010','101','101'],'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],'0':['111','101','101','101','111'],'2':['110','001','010','100','111'],'4':['101','101','111','001','001'],' ':['000']*5}
PAL=[('#10172e','#203652','#35546d','#e8ae71','#e5d4ba','#ffefb8'),('#16172e','#383459','#54517b','#ee8a79','#dbbdd6','#faf0c5'),('#122439','#21526c','#266e83','#64c2b0','#a4e4d3','#fbecb9'),('#1c2330','#39504b','#536950','#d0a36c','#e4c49a','#fff0c4'),('#141b32','#32425f','#4e5079','#85a5c9','#df9f98','#ffe4a9')]
def box(d,b,fill,outline=None,width=1):d.rectangle(b,fill=fill,outline=outline,width=width)
def ellipse(d,b,fill,outline=None,width=1):d.ellipse(b,fill=fill,outline=outline,width=width)
def poly(d,p,fill,outline=None):d.polygon(p,fill=fill);d.line(p+[p[0]],fill=outline,width=1) if outline else None
def ln(d,pts,fill,width=1):d.line(pts,fill=fill,width=width)
def glyph(d,word,cy,col='#fbdd98'):
 word=word.upper();scale=1 if len(word)>4 else 2
 w=(len(word)*4-1)*scale
 if w>26:scale=1;w=len(word)*4-1
 xx=(32-w)//2;yy=cy-(5*scale)//2
 for i,ch in enumerate(word):
  for j,row in enumerate(FONT.get(ch,FONT[' '])):
   for k,p in enumerate(row):
    if p=='1':box(d,(xx+(4*i+k)*scale,yy+j*scale,xx+(4*i+k+1)*scale-1,yy+(j+1)*scale-1),col)
def background(idx,phase,cat):
 p=PAL[(idx//3+len(cat))%len(PAL)];im=Image.new('RGB',(32,32),p[0]);d=ImageDraw.Draw(im)
 for y in range(32):
  color=p[0] if y<9 else (p[1] if y<22 else p[2]);ln(d,((0,y),(31,y)),color)
 # Layered but quiet backdrop, leaving center clean.
 seed=random.Random(idx*719+len(cat)*311)
 for t in range(12):
  x=seed.randrange(32);y=seed.randrange(23)
  if t%3: d.point((x,y),fill=p[2] if t%2 else p[1])
 box(d,(0,27,31,31),p[1]);ln(d,((0,27),(31,27)),p[2],1)
 for x in range(2,31,6):box(d,(x+(idx%3),29,x+2+(idx%3),29),p[2])
 return im,d,p

def create_custom(cat,index,phase):
 # Draw on a native 32x32 canvas. Local variants control object pose and useful animation.
 im,d,p=background(index,phase,cat);dark='#101320';outline='#1c2534';metal='#889cb2';light='#f3eacb';gold='#f5bc62';red='#e7676b';cyan='#62d4d3';blue='#438ec3';green='#6ebb85';wood='#975d4d'
 k=index;off=(1 if phase in (2,3) else 0)
 if cat=='sign':
  words=['NEW','HOT','MENU','ORDER','READY','TAKE','FREE','REST','STOP','SHOP'];word=words[k];
  box(d,(3,6,28,26),dark,metal,1);box(d,(5,8,26,24),p[1],p[2]);ln(d,((10,2),(10,7)),metal);ln(d,((22,2),(22,7)),metal)
  glyph(d,word,16, gold if (k+phase)%8<5 else cyan);box(d,(4,25,27,26),p[2]);
  for x in (6,25):d.point((x,10),fill=light)
 elif cat=='cafe':
  if k in (0,1):
   box(d,(10,7,22,22),dark,metal);box(d,(11,8,21,12),metal);box(d,(13,14,19,21),wood);ellipse(d,(14,16,17,19),gold)
   if k==1:box(d,(11,10,21,13),cyan);box(d,(15,15,17,18),gold)
   else:
    for i in range(4):ellipse(d,(12+i*2,15+(i%2),14+i*2,17+(i%2)),wood)
  elif k in (2,6,7):
   poly(d,[(9,10),(23,10),(21,24),(11,24)],metal,dark);ellipse(d,(8,8,24,14),wood,light)
   box(d,(11,12,21,14),gold);ln(d,((23,16),(27,16),(27,21),(22,21)),light,2)
  elif k in (3,4,5):
   ellipse(d,(6,21,26,26),metal);ellipse(d,(10,10,22,22),gold,wood,2)
   if k==3:poly(d,[(10,17),(16,8),(23,17)],'#f6d6a3',wood);ellipse(d,(15,10,19,13),red)
   elif k==4:
    for i in range(3):ellipse(d,(11+i*3,12,14+i*3,15),red)
   else:ellipse(d,(13,13,19,18),'#fce9c7');d.point((14,14),fill=cyan)
  elif k==8:
   ellipse(d,(7,10,25,24),gold,wood,2);poly(d,[(9,12),(16,7),(24,14),(21,23),(12,22)],gold,wood);ln(d,((11,18),(21,13)),light,2)
  else:
   box(d,(7,12,25,24),metal,dark,2);ellipse(d,(6,10,26,14),wood,light);ln(d,((22,16),(28,15),(28,21),(23,22)),gold,2)
  # real steam movement
  if k in (0,1,2,6,7,9):
   for i in range(2):
    x=13+i*6+((phase+i)%3==0);y=7-((phase*2+i)%5)
    ln(d,((x,y+2),(x+1,y),(x,y-2)),light)
 elif cat=='tech':
  if k in (0,1,6,12,17):
   box(d,(5,7,27,23),dark,metal,2);box(d,(8,10,24,19),blue);box(d,(9,11,22,12),cyan);box(d,(10,15,20,16),p[2]);box(d,(13,24,18,26),metal)
   if k in (1,17):poly(d,[(3,23),(27,23),(30,26),(6,26)],metal,dark)
   if k==12:box(d,(11,11,22,19),dark);box(d,(13,13,20,15),cyan)
  elif k in (2,14,11):
   box(d,(10,4,22,26),dark,metal,2);box(d,(12,7,20,22),blue);box(d,(14,9,18,12),cyan);box(d,(14,24,18,24),light)
   if k==11:ln(d,((7,15),(3,12)),cyan);ln(d,((25,15),(29,12)),cyan)
  elif k in (3,4,9,15,16,19):
   box(d,(6,13,26,23),dark,metal,2)
   if k in (4,16):ellipse(d,(8,16,12,20),cyan);ellipse(d,(20,16,24,20),red);box(d,(14,17,18,18),light)
   elif k in (9,19):ellipse(d,(9,9,22,22),blue,dark,2);ellipse(d,(12,12,19,18),cyan);ln(d,((8,14),(8,21),(11,24)),metal,2)
   elif k==15:box(d,(12,7,21,20),cyan,dark);ellipse(d,(13,9,20,14),light)
   else:
    for a in range(8):box(d,(8+(a%4)*4,15+(a//4)*4,10+(a%4)*4,16+(a//4)*4),metal)
  elif k in (5,10,18):
   box(d,(7,7,25,25),dark,metal,2);box(d,(10,10,22,18),blue);box(d,(12,12,20,15),cyan);ellipse(d,(13,20,19,23),red)
  else:
   box(d,(7,9,25,23),metal,dark,2);box(d,(10,11,22,19),p[2]);box(d,(12,13,20,15),cyan)
  if phase%4==0:box(d,(18,11,21,12),light)
 elif cat=='daily':
  if k in (0,16):
   box(d,(8,9,24,24),wood,dark,2);box(d,(10,10,21,12),light);box(d,(10,15,21,16),gold);box(d,(11,19,19,20),metal)
  elif k in (1,9):
   box(d,(14,9,18,25),wood,dark,1);ellipse(d,(9,4,23,13),gold,dark,2);ellipse(d,(13,8,19,13),light)
   if k==9:poly(d,[(12,9),(16,3+off),(20,9)],red,gold)
  elif k in (2,19):
   poly(d,[(5,16),(16,5),(27,16)],blue,dark);ln(d,((16,16),(16,25),(20,26)),wood,2)
   if k==19:box(d,(9,8,23,14),red,dark)
  elif k in (3,12):
   box(d,(7,10,25,25),wood,dark,2);box(d,(12,9,19,11),gold);box(d,(13,15,19,17),metal)
  elif k in (4,14):
   box(d,(6,13,26,23),wood,dark,2);box(d,(7,12,13,19),red);box(d,(16,12,25,19),gold)
  elif k in (5,6,8,10,11):
   ellipse(d,(7,12,25,25),green,dark,2);box(d,(11,19,21,25),wood)
   if k in (5,8):ellipse(d,(11,7,22,18),metal,dark);ellipse(d,(13,11,15,13),cyan);ellipse(d,(18,11,20,13),cyan)
   if k==11:box(d,(12,12,20,22),gold,dark)
  elif k in (7,17):
   ellipse(d,(7,7,25,25),light,dark,2);ellipse(d,(11,11,21,21),metal);ln(d,((16,16),(16,11)),dark,2);ln(d,((16,16),(21,17)),dark,1)
  else:
   box(d,(7,11,25,24),metal,dark,2);box(d,(11,8,21,12),gold);box(d,(11,17,21,20),light)
  if phase in (0,4):d.point((8,9),fill=light)
 elif cat=='vehicle':
  if k in (0,1,2,3,4,11,12,19):
   base=[gold,red,red,metal,blue,wood,red,cyan][[0,1,2,3,4,11,12,19].index(k)]
   box(d,(4,14,28,24),base,dark,2);poly(d,[(9,14),(12,9),(22,9),(25,14)],base,dark);box(d,(12,12,21,14),cyan)
   ellipse(d,(8,21,13,26),dark,metal);ellipse(d,(20,21,25,26),dark,metal)
   if k in (3,4):box(d,(6,16,9,21),light);box(d,(23,16,26,21),light)
  elif k in (5,6,10):
   poly(d,[(15,4),(24,20),(16,25),(8,20)],metal,dark);box(d,(13,10,18,18),blue);poly(d,[(8,22),(16,29),(24,22)],red,dark)
  elif k in (7,8,9):
   ellipse(d,(4,19,13,27),dark,metal,2);ellipse(d,(21,19,29,27),dark,metal,2)
   ln(d,((8,23),(17,15),(25,23),(8,23)),gold,2);ln(d,((17,15),(17,11)),metal,2)
  elif k in (13,14,18):
   box(d,(7,13,25,24),gold,dark,2);box(d,(18,5,21,16),metal);poly(d,[(19,6),(27,8),(28,13)],gold,dark);ellipse(d,(9,22,14,27),dark,metal)
  elif k in (15,16):
   poly(d,[(8,7),(24,7),(27,24),(5,24)],gold,dark);glyph(d,'!',16,dark) if False else box(d,(15,11,17,19),dark)
  else:
   box(d,(10,5,21,26),dark,metal,2);box(d,(12,8,19,12),red if phase%4<2 else green);box(d,(12,16,19,19),gold)
  if phase%4==0:ln(d,((27,20),(30,19)),light,1)
 elif cat=='nature':
  if k in (0,1,6,7,16):
   ellipse(d,(9,7,23,21),gold if k!=6 else light,dark,2)
   for a in range(8):
    th=a*math.pi/4;xx=16+round(math.cos(th)*12);yy=14+round(math.sin(th)*11)
    if 1<=xx<=29 and 0<=yy<=25: d.point((xx,yy),fill=gold)
   if k in (1,16):ellipse(d,(15+off,12,28+off,20),metal,p[2])
  elif k in (2,3,4,9,18):
   ellipse(d,(5,10,17,19),light);ellipse(d,(12,6,25,20),metal);box(d,(7,14,25,20),metal)
   for i in range(4):
    x=8+i*5;y=21+(phase+i*3)%6
    ln(d,((x,y),(x-1,y+3)),cyan if k!=4 else light)
   if k==3:poly(d,[(16,17),(12,23),(18,21),(14,28),(23,18)],gold,dark)
  elif k in (5,13,14):
   for i,c in enumerate((red,gold,green,cyan,blue)):d.arc((5+i,5+i,27-i,27-i),180,360,fill=c,width=2)
   if k==13:box(d,(2,25,29,28),blue)
  elif k in (8,10,11,17):
   box(d,(14,17,18,28),wood,dark);ellipse(d,(6,8,27,22),green,dark,2)
   for i in range(6):d.point((9+i*3,12+(i%3)*2),fill=gold if k==11 else '#a5d56d')
   if k==17:ellipse(d,(11,6,23,16),gold)
  elif k in (12,15):
   ellipse(d,(8,10,24,23),red,dark,2);box(d,(13,21,18,26),wood)
  elif k==19:
   poly(d,[(9,26),(13,12),(18,12),(24,26)],gold,dark);ellipse(d,(12,7,20,17),red,gold,2)
  else:
   poly(d,[(6,23),(14,10),(18,17),(23,7),(29,24)],metal,dark);box(d,(1,24,30,27),blue)
 elif cat=='scenery':
  # 20 distinct little environments grouped by recognizable foreground motifs.
  horizon=16+index%4
  if k in (0,7,15,19):
   box(d,(0,horizon,31,30),blue);ln(d,((0,horizon),(31,horizon)),cyan,1)
   for a in range(4):ln(d,((a*9+(phase%2),horizon+3+a%3),(a*9+5+(phase%2),horizon+3+a%3)),light)
   if k==15:poly(d,[(8,21),(15,12),(21,21)],wood,dark)
   ellipse(d,(21,5,29,13),gold)
  elif k in (1,10,13):
   poly(d,[(0,22),(12,7),(18,18),(25,5),(31,19),(31,29),(0,29)],metal,dark)
   poly(d,[(9,11),(12,7),(16,13)],light)
  elif k in (2,3,4,6,11,12,18):
   poly(d,[(5,23),(5,14),(14,14),(14,9),(20,9),(20,14),(27,14),(27,24)],wood,dark)
   poly(d,[(2,15),(16,6),(30,15)],red,dark)
   box(d,(13,18,18,24),dark,gold);box(d,(7,16,10,19),gold);box(d,(21,16,24,19),gold)
   if k==3:ln(d,((29,25),(29,6)),light,2)
   if k==12:for_x=0
  elif k in (5,16):
   poly(d,[(0,22),(8,11),(14,19),(24,8),(31,18),(31,30),(0,30)],green,dark)
   box(d,(15,9,21,27),cyan);box(d,(17,14,19,28),light)
  elif k in (8,9,14):
   for i in range(5):
    x=i*7-1;h=10+(i*3+k)%8
    box(d,(x,h,x+6,27),p[2],dark,1)
    for yy in range(h+3,24,5):box(d,(x+2,yy,x+3,yy+1),gold if phase%4<2 else cyan)
  else:
   box(d,(9,9,24,27),wood,dark,2);poly(d,[(7,10),(17,4),(26,10)],gold,dark)
   box(d,(14,15,19,25),dark)
  if phase%4==0:d.point((3,5),fill=light)
 else: raise ValueError(cat)
 # A tiny animated glint keeps 8 discrete frames even in still-life templates.
 d.point(((4+phase*3)%29+1,3+(phase%2)),fill=light if phase%2 else cyan)
 return im.resize((64,64),Image.Resampling.NEAREST)

def export(frames,path):
 # source quality preserved, don't let Pillow optimize away meaningful animation frames
 assert len(frames)==8
 frames[0].save(path,save_all=True,append_images=frames[1:],duration=[115]*8,loop=0,disposal=2,optimize=False)
 with Image.open(path) as gif:
  assert gif.size==(64,64) and gif.n_frames==8,(path,gif.size,gif.n_frames)

selected=[]
def add_from_prior(cat,objs):
 for p in objs:
  selected.append((cat,p['name'],G/p['file'],p['source']))

# Hand-pick source variety rather than 20 frames of one sprite.
heroes=[x for x in prior if x['category'] in ('hero','heroaction','ninja')]
monsters=[x for x in prior if x['category'] in ('enemy','fly','ghost')]
items=[x for x in prior if x['category'] in ('item','fruit','trap')]
def diverse(xs,n):
 buckets=defaultdict(list)
 for x in xs:buckets[x['name'].split(' · ')[0]].append(x)
 out=[];i=0
 while len(out)<n and any(buckets.values()):
  for v in list(buckets.values()):
   if v and len(out)<n:out.append(v.pop(0))
  i+=1
  if i>30:break
 assert len(out)==n
 return out
add_from_prior('hero',diverse(heroes,20))
add_from_prior('monster',diverse(monsters,20))
add_from_prior('item',diverse(items,20))
add_from_prior('cafe',[x for x in prior if x['category']=='cafe'][:10])
for i,name in enumerate(TITLES['cafe']):selected.append(('cafe',name,i,'Original'))
add_from_prior('sign',[x for x in prior if x['category']=='sign'][:10])
for i,name in enumerate(TITLES['sign']):selected.append(('sign',name,i,'Original'))
for cat in ['scenery','tech','daily','vehicle','nature']:
 for i,name in enumerate(TITLES[cat]):selected.append((cat,name,i,'Original'))
assert len(selected)==200
itemsOut=[]
for ix,(cat,name,src,source) in enumerate(selected,1):
 namefile=f'pixel_{ix:03d}.gif';target=OUT/namefile
 if isinstance(src,Path):
  with Image.open(src) as im:
   frames=[]
   for p in range(8):
    im.seek(p);frames.append(im.convert('RGB').copy())
 else:frames=[create_custom(cat,src,p) for p in range(8)]
 export(frames,target)
 itemsOut.append({'id':f'pixel_{ix:03d}','name':name,'category':cat,'categoryName':dict(CATS)[cat],'file':'v22/'+namefile,'frames':8,'durationMs':920,'resolution':'64x64','source':source})
for old in G.glob('premium_*.gif'):old.unlink()
counts=Counter(x['category'] for x in itemsOut)
assert len(counts)==10 and all(counts[k]==20 for k,_ in CATS),counts
catalog={'categories':[{'id':k,'name':v,'count':20} for k,v in CATS],'samples':itemsOut}
(BASE/'gallery-catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
html=BASE/'index.html';text=html.read_text('utf-8');line='const GALLERY_CATALOG='+json.dumps(catalog,ensure_ascii=False,separators=(',',':'))+';'
text,n=re.subn(r'^const GALLERY_CATALOG=.*;$',lambda m:line,text,flags=re.M)
assert n==1
html.write_text(text,encoding='utf-8')
with zipfile.ZipFile(Path('PixelMatrix_Studio_v2.2_GIF_200.zip'),'w',compression=zipfile.ZIP_DEFLATED) as z:
 for i in itemsOut:z.write(G/i['file'],arcname=f"{i['category']}/{i['file'].split('/')[-1]}")
 z.write(BASE/'gallery-catalog.json','gallery-catalog.json')
 z.write(BASE/'ASSET_LICENSES.md','ASSET_LICENSES.md')
print(json.dumps({'total':len(itemsOut),'categories':dict(counts),'zip_bytes':Path('PixelMatrix_Studio_v2.2_GIF_200.zip').stat().st_size},ensure_ascii=False))