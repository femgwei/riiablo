"""Offline replay experiment against retained private-desktop Hurricane snapshots."""
import argparse
from collections import Counter,defaultdict
import json
import math
from pathlib import Path
import struct


def direction(x,y,tx,ty):
    dx,dy=tx-x,ty-y
    swap=abs(dx)>abs(dy)
    small,big=sorted((abs(dx),abs(dy)))
    tangent=127*small//big if big else 0
    length=math.sqrt(127**2+tangent**2)
    vx,vy=int(4096*tangent/length),int(4096*127/length)
    angle=min(7,int(math.atan(tangent/127)*32/math.pi))
    if swap: vx,vy=vy,vx;angle=(-1-angle)&15
    if dy<0:vy=-vy;angle=(-1-angle)&31
    if dx>=0:angle=(-1-angle)&63
    else:vx=-vx
    return vx,vy,(angle+8)&63


def poses(cx,cy,lx,ly,speed,life):
    x,y=((cx+lx)<<16)+32768,((cy+ly)<<16)+32768
    dx,dy,d=direction(x,y,x+(ly<<16),y-(lx<<16))
    vx,vy=(dx*speed)>>8,(dy*speed)>>8
    result=[(x,y,vx,vy,d)]
    for tick in range(life):
        if tick%2==0:
            ix,iy=x>>16,y>>16
            tx,ty=ix+iy-cy,iy-ix+cx
            dx,dy,d=direction(x,y,(tx<<16)+32768,(ty<<16)+32768)
            vx,vy=(dx*speed)>>8,(dy*speed)>>8
        x+=vx;y+=vy
        result.append((x,y,vx,vy,d))
    return result


def inspect(directory):
    units=defaultdict(list)
    for line in (directory/'missiles.jsonl').open(encoding='utf8'):
        row=json.loads(line);units[row['unit_id']].append(row)
    starts=Counter();compared=Counter();errors=Counter();examples=[]
    for rows in units.values():
        first=rows[0];age=first['total_frames']-first['current_frame'];starts[age]+=1
        if age!=0:continue
        cx,cy=struct.unpack_from('<2i',bytes.fromhex(first['missile_data_hex']),40)
        lx,ly=(first['x_fixed']>>16)-cx,(first['y_fixed']>>16)-cy
        model=poses(cx,cy,lx,ly,(20 if first['class_id']==581 else 18)*256*75//100,first['total_frames'])
        for s in rows:
            age=s['total_frames']-s['current_frame'];p=model[age]
            observed=(s['x_fixed'],s['y_fixed'],*s['velocity'],s['direction'])
            for n,(a,b) in enumerate(zip(p,observed)):
                compared[n]+=1
                if a!=b:
                    errors[n]+=1
                    if len(examples)<8:examples.append(dict(unit=s['unit_id'],age=age,field=n,expected=a,observed=b))
    print(json.dumps(dict(birth_ages=starts,compared=compared,errors=errors,examples=examples),indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=Path)
    inspect(p.parse_args().directory)
