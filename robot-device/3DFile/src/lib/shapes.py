from __future__ import annotations
import math
from cadgen import build123d as bd, srgb
from . import params as p


def box(x,y,z,at=(0,0,0)):
    return bd.Pos(*at)*bd.Box(x,y,z,align=(bd.Align.CENTER,bd.Align.CENTER,bd.Align.MIN))

def cyl(r,h,at=(0,0,0)):
    return bd.Pos(*at)*bd.Cylinder(r,h,align=(bd.Align.CENTER,bd.Align.CENTER,bd.Align.MIN))

def rr(w,l,r,h,z=0):
    return bd.Pos(0,0,z)*bd.extrude(bd.RectangleRounded(w,l,r),amount=h)

def hole_x(d,length,at):
    return bd.Pos(*at)*bd.Rot(0,90,0)*bd.Cylinder(d/2,length,align=(bd.Align.CENTER,bd.Align.CENTER,bd.Align.MIN))

def hole_y(d,length,at):
    return bd.Pos(*at)*bd.Rot(90,0,0)*bd.Cylinder(d/2,length,align=(bd.Align.CENTER,bd.Align.CENTER,bd.Align.MIN))

def slot(length,width,depth,at=(0,0,0),angle=0):
    return bd.Pos(*at)*bd.Rot(0,0,angle)*bd.extrude(bd.SlotOverall(length,width),amount=depth)

def cut_all(s, tools):
    return s.cut(*tools) if tools else s

def finish(s,name,color=p.COLOR_CREAM):
    s.label=name
    s.color=srgb(color)
    return s

def base():
    s=rr(p.BODY_W,p.BODY_L,26,42,p.BASE_Z)
    bottom=[e for e in s.edges() if abs(e.bounding_box().min.Z-12)<1e-5 and abs(e.bounding_box().max.Z-12)<1e-5]
    s=bd.fillet(bottom,2)-rr(138,168,23,44,p.BASE_Z+3)
    # Lid inserts and removable electronics deck standoffs.
    for x,y in p.LID_SCREWS:
        s=s+cyl(6.4,42,(x,y,12))
    for x,y in p.DECK_SCREWS:
        s=s+cyl(5,32,(x,y,12))
    for x,y in p.CLIFFS:
        for dx in (-11,11):
            s=s+cyl(4.2,8,(x+dx,y,12))
    tools=[]
    for x,y in p.LID_SCREWS:
        tools.extend([cyl(1.7,44,(x,y,11)),cyl(3.3,3.1,(x,y,11.9))])
    for x,y in p.DECK_SCREWS:
        tools.append(cyl(2.1,7,(x,y,38)))
    for x,y in p.CLIFFS:
        tools.append(box(15,20,12,(x,y,11)))
        for dx in (-11,11): tools.append(cyl(1.7,10,(x+dx,y,11)))
    # Motor bracket screws, four for each motor, nuts accessible below floor.
    for side in (-1,1):
        for dx in (-8,8):
            for dy in (-11,11): tools.append(cyl(1.7,8,(side*56+dx,40+dy,10)))
        tools.append(hole_x(16,8,(side*69-4,40,31)))
        # Side fender fasteners, axes X.
        for y in (-14,12): tools.append(hole_x(3.4,12,(side*70-6,y,48)))
        # Idler axle screw head access and tension adjustment clearance.
        tools.append(hole_x(11,12,(side*70-6,-40,23)))
        # Idler tensioner fasteners, adjust along Y.
        for y in (-53,-27): tools.append(hole_x(3.4,12,(side*70-6,y,23)))
    # Battery straps and captive tray mount.
    for x in (-27,27):
        for y in (-54,-4): tools.append(box(12,3.2,8,(x,y,10)))
    for x in (-32,32):
        for y in (-50,-8): tools.append(cyl(1.7,8,(x,y,10)))
    # Front optical windows, isolated from printed wall by open air.
    for x in (-28,28):
        tools.append(hole_y(16,12,(x,-78,39)))
        for dx in (-12,12): tools.append(hole_y(3.4,12,(x+dx,-78,39)))
    # Rear replaceable I/O panel and its screws.
    tools.append(box(58,10,22,(0,85,22)))
    for x in (-33,33): tools.append(hole_y(3.4,12,(x,90,33)))
    return finish(cut_all(s,tools),'base')

def lid():
    # Car-like stance: long low nose (-Y), short high tail (+Y).
    outer=bd.loft([bd.Pos(0,y,0)*(bd.Plane.XY.offset(z)*bd.RectangleRounded(w,l,r)) for z,w,l,r,y in [(54,144,174,26,0),(60,143.8,172,27,1),(71,138,156,28,7),(79,128,136,27,12),(84,118,124,24,14)]])
    inner=bd.loft([bd.Pos(0,y,0)*(bd.Plane.XY.offset(z)*bd.RectangleRounded(w,l,r)) for z,w,l,r,y in [(53,138,168,23,0),(60,137.8,166,24,1),(69,133,151,25,6),(77,121,131,24,12),(79,112,118,21,14)]])
    s=(outer & box(144,174,31,(0,0,54)))-inner
    for x,y in p.LID_SCREWS:
        s=s+cyl(6,7,(x,y,54))
        if x: s=s+box(12,12,7,((x+ (70 if x>0 else -70))/2,y,54))
        else: s=s+box(12,10,7,(0,80 if y>0 else -80,54))
    # Reinforcing ribs join roof around the neck load path.
    for x in (-44,38): s=s+box(8,62,8,(x,12,76))
    tools=[]
    for x,y in p.LID_SCREWS:
        tools.append(cyl(2.1,7.2,(x,y,53.9)))
    for x,y in p.NECK_SCREWS: tools.append(cyl(1.7,15,(x,y,74)))
    tools.append(box(14,10,20,(-20,30,72)))
    # Cooling slots, away from phone hinge footprint.
    for x in (-30,-20,-10,0,10,20,30): tools.append(slot(15,3.2,25,(x,59,68),90))
    # Touch-pad pockets retain 2 mm roof thickness for capacitive touch.
    for x in (-36,36): tools.append(box(17,17,4,(x,-35,78)))
    return finish(cut_all(s,tools),'lid')

def battery_tray():
    s=rr(78,50,4,3)+rr(78,50,4,24)-rr(72.6,44.6,2,26,3)
    for x in (-27,27):
        for y in (-23.5,23.5): s=s-box(12,5,4,(x,y,6))
    for x in (-32,32):
        for y in (-21,21): s=s-cyl(1.7,5,(x,y,-1))
    return finish(s,'battery_tray',p.COLOR_GREEN)

def electronics_deck():
    s=rr(130,151,8,3)
    tools=[]
    for x,y in p.DECK_SCREWS: tools.append(cyl(1.7,5,(x,y,-1)))
    for x,y in p.LID_SCREWS: tools.append(cyl(6.5,5,(x,y,-1)))
    # Slotted grid for nylon standoffs / straps; no guessed board hole pattern.
    for x in (-48,-32,-16,0,16,32,48):
        for y in (-60,-42,-24,-6,12,30,48,66): tools.append(slot(12,3.4,5,(x,y,-1)))
    return finish(cut_all(s,tools),'electronics_deck',p.COLOR_GREEN)

def motor_cradle():
    s=rr(24,28,3,23)
    s=s-box(34,12.6,15,(0,0,10.7))
    for x in (-8,8):
        for y in (-11,11): s=s-cyl(1.7,26,(x,y,-1))
    return finish(s,'motor_cradle',p.COLOR_GREEN)

def motor_cap():
    s=rr(24,28,3,3)
    # 1.0 mm foam strip between this pad and motor avoids rigid overclamping.
    s=s+box(22,10,1.5,(0,0,-1.5))
    for x in (-8,8):
        for y in (-11,11): s=s-cyl(1.7,7,(x,y,-2))
    return finish(s,'motor_cap',p.COLOR_GREEN)

def wheel_common(radius):
    s=cyl(radius,26)
    s=s+cyl(radius+1.5,2)+cyl(radius+1.5,2,(0,0,24))
    # Track's central guide rib runs inside this groove.
    s=s-(cyl(radius+2,3.8,(0,0,11.1))-cyl(radius-3,3.8,(0,0,11.1)))
    return s

def drive_wheel():
    s=wheel_common(26)+cyl(7.5,7,(0,0,-6))
    # D shaft: diameter clearance 3.15; flat at 1.05 from axis.
    bore=cyl(1.575,34,(0,0,-7)) & box(6,5,36,(0,-1.45,-8))
    s=s-bore
    # M3 radial grub screw on flat, pilot to be tapped after printing.
    s=s-hole_y(2.5,10,(0,8,-2))
    return finish(s,'drive_wheel',p.COLOR_GREEN)

def idler_wheel():
    s=wheel_common(18)-cyl(3.2,28,(0,0,-1))
    s=s-cyl(5.1,4.3,(0,0,-0.01))-cyl(5.1,4.3,(0,0,21.8))
    return finish(s,'idler_wheel',p.COLOR_GREEN)

def bearing_spacer():
    return finish(cyl(3,17.6)-cyl(1.7,19,(0,0,-.5)),'bearing_spacer',p.COLOR_DARK)

def tensioner():
    s=rr(36,22,3,3)
    for x in (-13,13): s=s-slot(8,3.4,5,(x,0,-1))
    s=s-cyl(1.7,5,(0,0,-1))
    return finish(s,'tensioner',p.COLOR_GREEN)

def belt_profile(offset=0):
    # X follows robot Y; profile Y follows world Z minus 28.
    c1=(-40,-5); c2=(40,3); r1=18+offset; r2=26+offset
    dx=80.;dy=8.;d=math.hypot(dx,dy);ux=dx/d;uy=dy/d
    q=(r1-r2)/d;t=math.sqrt(1-q*q)
    normals=[(ux*q-uy*t,uy*q+ux*t),(ux*q+uy*t,uy*q-ux*t)]
    n,m=normals
    pts=[(c1[0]+r1*n[0],c1[1]+r1*n[1]),(c2[0]+r2*n[0],c2[1]+r2*n[1]),(c2[0]+r2*m[0],c2[1]+r2*m[1]),(c1[0]+r1*m[0],c1[1]+r1*m[1])]
    face=bd.Pos(*c1)*bd.Circle(r1)+bd.Pos(*c2)*bd.Circle(r2)+bd.Polygon(*pts[::-1],align=None)
    return face, normals

def capsule(offset,thickness):
    return bd.extrude(belt_profile(offset)[0],amount=thickness,dir=(0,0,1))

def track():
    inner=-.3;outer=inner+p.TRACK_SKIN
    s=capsule(outer,p.TRACK_WIDTH)-capsule(inner,p.TRACK_WIDTH+1)
    s=s+bd.Pos(0,0,(p.TRACK_WIDTH-2.8)/2)*(capsule(inner+.15,2.8)-capsule(inner-2,2.8))
    normals=belt_profile(outer)[1]
    for nx,ny in normals:
        a=(-40+(18+outer)*nx,-5+(18+outer)*ny);b=(40+(26+outer)*nx,3+(26+outer)*ny)
        for i in range(1,14):
            t=i/14;x=a[0]*(1-t)+b[0]*t;y=a[1]*(1-t)+b[1]*t
            s=s+bd.Pos(x+.55*nx,y+.55*ny,0)*bd.Rot(0,0,math.degrees(math.atan2(ny,nx)))*box(2.4,3,p.TRACK_WIDTH)
    top=math.atan2(normals[0][1],normals[0][0]);bottom=math.atan2(normals[1][1],normals[1][0])
    for cx,cy,r,start,end in [(40,3,26+outer,top,bottom),(-40,-5,18+outer,bottom,top-2*math.pi)]:
        count=max(2,round(abs(end-start)*r/6))
        for i in range(count):
            a=start+(end-start)*(i+.5)/count
            s=s+bd.Pos(cx+(r+.55)*math.cos(a),cy+(r+.55)*math.sin(a),0)*bd.Rot(0,0,math.degrees(a))*box(2.4,3,p.TRACK_WIDTH)
    return finish(s,'track_tpu',p.COLOR_DARK)

def fender():
    ring=(capsule(7,26)-capsule(4,27)) & box(175,40,28,(0,25,0))
    for x in (-14,12):
        tab=box(8,13,6,(x,24,-4))-cyl(1.7,9,(x,20,-5))
        ring=ring+tab
    return finish(ring,'fender',p.COLOR_GREEN)

def sensor_carrier():
    s=rr(29,26,7,2.4)
    s=s-box(11,16,4,(0,0,-.5))
    # Two side rails locate a 13 x 18 mm Pololu board, leave optics open.
    for x in (-8.2,8.2): s=s+box(2.4,20,3,(x,0,2.4))
    for x in (-11,11): s=s-cyl(1.7,7,(x,0,-1))
    return finish(s,'sensor_carrier',p.COLOR_GREEN)

def front_sensor_carrier():
    s=rr(32,26,3,2.4)-box(11,16,4,(0,0,-.5))
    for x in (-8.2,8.2): s=s+box(2.4,20,3,(x,0,2.4))
    for x in (-12,12): s=s-cyl(1.7,7,(x,0,-1))
    return finish(s,'front_sensor_carrier',p.COLOR_GREEN)

def front_sensor_bezel():
    # Slip-fit stem into front shell; use a thin bead of removable adhesive.
    s=cyl(7.8,2.5)+cyl(10,1.6,(0,0,2.5))
    s=s-cyl(6,6,(0,0,-1))
    return finish(s,'front_sensor_bezel',p.COLOR_DARK)

def io_panel():
    s=rr(74,31,4,3)
    for x in (-33,33): s=s-cyl(1.7,5,(x,0,-1))
    # Panel-mount DC barrel jack and slide switch; removable blank is also supplied.
    s=s-cyl(4.1,5,(-19,0,-1))
    s=s-box(14,8,5,(7,0,-1))
    for x in (-3,17): s=s-cyl(1.1,5,(x,0,-1))
    s=s-cyl(3.1,5,(27,0,-1))
    return finish(s,'io_panel',p.COLOR_GREEN)

def io_panel_blank():
    s=rr(74,31,4,3)
    for x in (-33,33): s=s-cyl(1.7,5,(x,0,-1))
    return finish(s,'io_panel_blank',p.COLOR_GREEN)

def neck_mount():
    s=bd.Pos(-3,20,84)*rr(100,88,22,5)
    s=s+bd.Pos(-17,23.5,89)*rr(44,58,10,23)
    s=s-box(32.8,33.2,25,(-15,20,93.3))
    s=s-box(14,47,19,(-4.5,20,93.1))
    s=s-hole_x(18,12,(-2,12,102))
    s=s-box(8,9,10,(-36,30,96))
    s=s+box(8,20,17,(33.5,12,89))+hole_x(9,8,(29.5,12,106))
    s=s-hole_x(3.4,12,(27,12,102))
    for x,y in p.NECK_SCREWS: s=s-cyl(1.7,7,(x,y,83))
    for x,y in [(-35,4),(-35,48),(1,48)]: s=s-cyl(2.1,8,(x,y,105))
    s=s-box(12,10,8,(-20,30,83))
    s=s-box(12,30,20,(1,0,100))
    for x in (-18,18):
        s=s+box(8,10,10,(x,49,89))
        s=s-hole_y(1.8,12,(x,55,94))
    return finish(s,'neck_mount',p.COLOR_GREEN)

def servo_cap():
    s=rr(44,52,7,3)
    for x,y in [(-18,-22),(-18,22),(18,22)]: s=s-cyl(1.7,5,(x,y,-1))
    s=s-box(14,34,5,(18,-14,-1))
    return finish(s,'servo_cap',p.COLOR_GREEN)

def head_stem():
    s=hole_x(17,23,(6,12,102))
    s=s+bd.Pos(17,4,102)*rr(24,14,5,20)
    s=s-box(4,30,30,(4,12,93))
    for z in (-6,6): s=s-hole_x(2.1,7,(4,12,102+z))
    for x in (9,25): s=s-hole_y(3.4,32,(x,24,111))
    s=s-hole_x(3.4,25,(5,12,102))
    return finish(s,'head_stem',p.COLOR_GREEN)

def magnetic_head():
    s=cyl(31,9)+box(26,20,4,(0,-34,0))
    s=s-cyl(28.4,8,(0,0,2))-cyl(22,4,(0,0,-1))
    for x in (-8,8): s=s-cyl(1.7,6,(x,-34,-1))
    s=s-box(9,12,5,(0,-28,4))
    return finish(s,'magnetic_head',p.COLOR_GREEN)

def tower_envelopes():
    sections=[(89,90.4,70,14),(108,88.4,68,14),(116,86.4,64,13),(122,74.4,50,16)]
    outer=bd.Pos(-1,24,0)*bd.loft([bd.Plane.XY.offset(z)*bd.RectangleRounded(w,l,r) for z,w,l,r in sections])
    inner=bd.Pos(-1,24,0)*bd.loft([bd.Plane.XY.offset(z)*bd.RectangleRounded(w,l,r) for z,w,l,r in [(88,82,64,11),(108,80,62,11),(114,78,60,10),(119,66,44,13)]])
    skin_inner=bd.Pos(-1,24,0)*bd.loft([bd.Plane.XY.offset(z)*bd.RectangleRounded(w-3.6,l-3.6,r-1.8) for z,w,l,r in sections])
    return outer,inner,outer-skin_inner

def tower_panel_mask(clearance=0):
    return bd.Pos(-48,24,104)*bd.Rot(0,90,0)*bd.extrude(bd.RectangleRounded(20+2*clearance,34+2*clearance,5+clearance),amount=13)

def tower_mirror(s):
    return s.mirror(bd.Plane(origin=(-1,0,0),x_dir=(0,1,0),z_dir=(1,0,0)))

def tower_side_panel():
    outer,inner,skin=tower_envelopes()
    return finish(skin & tower_panel_mask(),'tower_side_panel',p.COLOR_CREAM)

def neck_shroud():
    outer,inner,skin=tower_envelopes();s=outer-inner
    s=s-box(31,40,32,(17,-3,96))
    s=s-box(100,70,30,(0,-32.5,100))
    for x in (-18,18): s=s-hole_y(2.4,12,(x,62,94))
    pocket=skin & tower_panel_mask(.3)
    s=s-pocket-tower_mirror(pocket)
    return finish(s,'neck_shroud',p.COLOR_GREEN)

def head_yoke():
    # Hinge center is (0,0,0); arms placed between servo and opposite pillar.
    s=box(40,14,6,(12,0,13))
    for x in (-6,30):
        arm=box(4,16,15,(x,0,0))+hole_x(9,4,(x-2,0,0))
        arm=arm-hole_x(3.4,6,(x-3,0,0))
        s=s+arm
    # Mount commercial 24T servo horn to left arm; central screw remains accessible.
    for z in (-6,6): s=s-hole_x(2.1,7,(-10,0,z))
    # Bridge toward back of phone frame. Two screws normal to phone back.
    s=s+box(36,24,8,(0,-7,12))
    for x in (-12,12): s=s-hole_y(3.4,28,(x,7,16))
    return finish(s,'head_yoke',p.COLOR_GREEN)

def phone_frame():
    s=rr(172,90,8,4)-rr(149,67,5,6,-1)
    # Clearance for Find X8 camera island; conservative envelope, not measured lens CAD.
    s=s-cyl(32,6,(-49,0,-1))
    s=s+box(68,13,4,(0,-33,0))
    for x in (-12,12): s=s-cyl(1.7,7,(x,-32,-1))
    # Join mounting holes to the lower bar with a small central mounting bridge.
    s=s+box(34,17,4,(0,-33.5,0))
    for x in (-12,12): s=s-cyl(1.7,7,(x,-32,-1))
    for x in (-61,61):
        s=s+box(16,4,12,(x,-39.5,4))
        s=s+box(16,7,2.5,(x,-38,14))
    for x in (-80,80): s=s-slot(11,3.4,7,(x,39,-1),90)
    # Qi carrier connects at two slotted points on right edge (no metal behind coil).
    for y in (-18.5,25.5): s=s-slot(8,3.4,7,(80,y,-1),90)
    s=s-cyl(32,6,(-49,0,-1))
    return finish(s,'phone_frame',p.COLOR_CREAM)

def phone_top_clamp():
    s=rr(172,8,3,4)
    for x in (-80,80): s=s-cyl(1.7,6,(x,0,-1))
    for x in (-61,61):
        s=s+box(16,4,8,(x,-2.5,4))
        s=s+box(16,7,2.5,(x,-3.5,10))
    return finish(s,'phone_top_clamp',p.COLOR_GREEN)

def qi_carrier():
    # 56 mm puck recess, mounting disk left open on contact side; removable clamps.
    s=cyl(31,9)-cyl(28.4,8,(0,0,2))
    s=s-cyl(22,4,(0,0,-1))
    for y in (-22,22):
        s=s+box(53,8,3,(38,y,0))
        s=s-slot(16,3.4,6,(63,y,-1))
    s=s-box(10,8,12,(0,-29,-1))
    # Clear the phone frame lower cross-brace while preserving rear puck support.
    s=s-box(74,20,7,(-17,-38.5,4.6))
    s=s-box(68,8,7,(0,33.5,4.6))
    for y in (-22,22):
        s=s+cyl(4,2,(63,y,3))
        s=s-slot(16,3.4,8,(63,y,-1))
    s=s-cyl(28.4,8,(0,0,2))
    return finish(s,'qi_carrier',p.COLOR_GREEN)

def cable_clip():
    s=rr(18,12,3,3)-cyl(1.7,5,(-5,0,-1))
    s=s+box(8,12,4,(4,0,3))-box(10,5,2,(4,0,3))
    return finish(s,'cable_clip',p.COLOR_GREEN)

def calibration_coupon():
    s=rr(80,35,3,4)
    for i,d in enumerate((3.0,3.1,3.2,3.4,4.0,4.2,4.4,10.0,10.2)):
        x=-32+(i%5)*16; y=-8+(i//5)*16
        s=s-cyl(d/2,6,(x,y,-1))
    return finish(s,'calibration_coupon',p.COLOR_CREAM)

FACTORIES={n:globals()[n] for n in ['base','lid','battery_tray','electronics_deck','motor_cradle','motor_cap','drive_wheel','idler_wheel','bearing_spacer','tensioner','track','fender','sensor_carrier','front_sensor_carrier','front_sensor_bezel','io_panel','io_panel_blank','neck_mount','servo_cap','head_stem','magnetic_head','neck_shroud','tower_side_panel','cable_clip','calibration_coupon']}

def print_shape(name):
    s=FACTORIES[name]()
    if name=='lid': s=s.rotate(bd.Axis.X,180)
    if name=='motor_cap': s=s.rotate(bd.Axis.X,180)
    if name=='head_yoke': s=s.rotate(bd.Axis.Y,90)
    # Lay the fender on its flat side; track already has the correct side down.
    bb=s.bounding_box()
    return s.moved(bd.Location((0,0,-bb.min.Z)))
