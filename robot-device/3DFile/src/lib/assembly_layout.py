"""Rigid placements of exported print-oriented child models. Units mm."""
from __future__ import annotations
from cadgen import build123d as bd, srgb
from . import params as p
from .shapes import box, cyl, hole_x, rr, finish, tower_mirror
import importlib

PART_NAMES=['base','lid','battery_tray','electronics_deck','motor_cradle','motor_cap','drive_wheel','idler_wheel','bearing_spacer','tensioner','track','fender','sensor_carrier','front_sensor_carrier','front_sensor_bezel','io_panel','io_panel_blank','neck_mount','servo_cap','head_stem','magnetic_head','neck_shroud','tower_side_panel','cable_clip','calibration_coupon']

def models():
    return {n:getattr(importlib.import_module(n),n)() for n in PART_NAMES}

def original(s,n):
    if n=='base': return bd.Pos(0,0,12)*s
    if n=='lid': return bd.Pos(0,0,84)*bd.Rot(180,0,0)*s
    if n=='motor_cap': return bd.Pos(0,0,3)*bd.Rot(180,0,0)*s
    if n=='fender': return bd.Pos(0,0,-4)*s
    if n=='drive_wheel': return bd.Pos(0,0,-6)*s
    if n=='neck_mount': return bd.Pos(0,0,84)*s
    if n=='head_stem': return bd.Pos(0,0,93.5)*s
    if n=='tower_side_panel': return bd.Pos(0,0,94)*s
    if n=='neck_shroud': return bd.Pos(0,0,89)*s
    if n=='head_yoke': return bd.Pos(32,0,0)*bd.Rot(0,-90,0)*s
    return s

def laid_parts(m,include_lid=True):
    items=[]
    def add(n,loc=None,label=None):
        s=original(m[n],n)
        if loc is not None: s=loc*s
        s.label=label or n
        items.append(s)
    add('base')
    if include_lid: add('lid')
    add('battery_tray',bd.Pos(0,-29,15))
    add('electronics_deck',bd.Pos(0,0,44))
    for side in (-1,1):
        tag='left' if side<0 else 'right'
        add('motor_cradle',bd.Pos(side*56,40,15),f'motor_cradle_{tag}')
        add('motor_cap',bd.Pos(side*56,40,38),f'motor_cap_{tag}')
        rot=bd.Rot(0,side*90,0)
        add('drive_wheel',bd.Pos(side*76,40,31)*rot,f'drive_wheel_{tag}')
        add('idler_wheel',bd.Pos(side*76,-40,23)*rot,f'idler_wheel_{tag}')
        add('bearing_spacer',bd.Pos(side*80.2,-40,23)*rot,f'bearing_spacer_{tag}')
        # local (X,Y,Z) maps to world (Y,Z,X), then mirror side by 180 about YZ.
        orient=bd.Rot(0,0,90)*bd.Rot(90,0,0)
        if side<0: orient=bd.Rot(0,0,90)*bd.Rot(-90,0,0)
        # Left rotation reverses local X; racetrack is symmetric front/rear.
        if side<0:
            for n,xx in [('track',78.3),('fender',76)]:
                part=original(m[n],n).mirror(bd.Plane.XZ)
                part=bd.Pos(-xx,0,28)*orient*part;part.label=f'{n}_{tag}';items.append(part)
        else:
            add('track',bd.Pos(78.3,0,28)*orient,f'track_{tag}')
        if side>0: add('fender',bd.Pos(76,0,28)*orient,f'fender_{tag}')
        add('tensioner',bd.Pos(side*72,-40,23)*orient,f'tensioner_{tag}')
    for i,(x,y) in enumerate(p.CLIFFS):
        add('sensor_carrier',bd.Pos(x,y,20),f'cliff_carrier_{i+1}')
    for i,x in enumerate((-28,28)):
        # Sensors point forward; rails stay inside, rear of PCB faces into chassis.
        add('front_sensor_carrier',bd.Pos(x,-84,39)*bd.Rot(-90,0,0),f'front_carrier_{i+1}')
        add('front_sensor_bezel',bd.Pos(x,-84.5,39)*bd.Rot(90,0,0),f'front_bezel_{i+1}')
    add('io_panel',bd.Pos(0,87,33)*bd.Rot(-90,0,0))
    add('neck_mount')
    add('servo_cap',bd.Pos(-17,26,112))
    add('head_stem')
    add('magnetic_head',bd.Pos(17,-3,p.PHONE_Z)*bd.Rot(90,0,0))
    add('neck_shroud')
    add('tower_side_panel',label='tower_side_panel_left')
    panel=tower_mirror(original(m['tower_side_panel'],'tower_side_panel'));panel.label='tower_side_panel_right';items.append(panel)
    add('cable_clip',bd.Pos(16,30,89))
    return items

def hardware():
    hw=[]
    def add(s,label,color):
        hw.append(finish(s,label,color))
    # Simplified supplier envelopes, explicitly NOT printing parts.
    for side in (-1,1):
        add(box(26,12,10,(side*56,40,26)),f'N20_body_{side}','#B29D69')
        shaft=cyl(1.5,9) & box(6,5,11,(0,-1.5,-1))
        add(bd.Pos(side*69,40,31)*bd.Rot(0,side*90,0)*shaft,f'N20_shaft_{side}','#9DA2A0')
        for ax in (side*76.2,side*97.8):
            bearing=hole_x(10,4,(ax if side>0 else ax-4,-40,23))-hole_x(3,6,(ax-1 if side>0 else ax-5,-40,23))
            add(bearing,f'bearing_623_{side}_{ax}','#AAB1B3')
    add(box(70,41,20,(0,-29,18)),'battery_2S_envelope','#405E92')
    add(box(31,32.4,16.8,(-15,20,93.6))+box(13,46,16.8,(-4.5,20,93.6)),'servo_HS225MG_envelope','#3C434C')
    # A conservative servo-horn envelope; use the supplied 24T horn, not a printed spline.
    add(hole_x(8,2,(4,12,102))-hole_x(1.5,4,(3,12,102)),'servo_horn_envelope','#CED3D0')
    for name,w,l,h,x,y in [('ESP32_board',34,64,16,-37,-37),('driver',20,25,8,-9,-45),('regulator_motor',43,21,13,28,-56),('regulator_servo',21,43,13,44,5),('regulator_logic',43,21,13,-27,59)]:
        add(box(w,l,h,(x,y,51)),name+'_envelope','#2F7B66')
    for i,(x,y) in enumerate(p.CLIFFS): add(box(13,18,2,(x,y,22.5)),f'cliff_board_{i}','#2F7B66')
    for i,x in enumerate((-28,28)):
        add(bd.Pos(x,-81.5,39)*bd.Rot(-90,0,0)*box(13,18,2),f'front_board_{i}','#2F7B66')
    phone=rr(p.PHONE_L,p.PHONE_H,9,p.PHONE_T)
    phone_pose=bd.Pos(0,p.PHONE_Y,p.PHONE_Z)*bd.Rot(90,0,0)*bd.Pos(0,0,5)
    add(phone_pose*phone,'OPPO_Find_X8_envelope','#20272B')
    add(phone_pose*cyl(30,4,(-49,0,-4)),'camera_island_clearance_envelope','#454D51')
    add(bd.Pos(0,p.PHONE_Y,p.PHONE_Z)*bd.Rot(90,0,0)*cyl(28,7,(17,0,-2)),'Qi_puck_56x7_envelope','#D0D3D3')
    # Screen eye graphics are presentation geometry only, not part of phone clearance model.
    for x in (-25,25):
        eye=bd.Pos(x,0,p.PHONE_T+.05)*bd.extrude(bd.Ellipse(10,14),amount=.1)
        add(phone_pose*eye,f'eye_{x}','#9DE8CB')
    return hw
