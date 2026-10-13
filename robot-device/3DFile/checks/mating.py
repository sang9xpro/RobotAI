"""Probe saved assembly with screw-shank cylinders at intended common axes."""
import json,sys
from pathlib import Path
from cadgen import read_scene
from cadgen.geometry import overlap_volume
root=Path(__file__).resolve().parents[1];sys.path.insert(0,str(root/'src'))
from lib.shapes import cyl,hole_x,hole_y
from lib import params as p
leaves={o.label:o.shape() for o in read_scene(root/'STEP/assembly.step').leaves()}
rows=[]
def probe(label,names,shape):
    volumes={n:round(sum(overlap_volume(a,b) for a in leaves[n].solids() for b in shape.solids()),5) for n in names}
    rows.append({'joint':label,'obstruction_mm3':volumes,'passed':all(v<.01 for v in volumes.values())})
for x,y in p.LID_SCREWS: probe(f'lid screw {x},{y}',['base','lid'],cyl(1.5,47,(x,y,12)))
for x,y in p.DECK_SCREWS: probe(f'deck screw {x},{y}',['base','electronics_deck'],cyl(1.5,9,(x,y,38)))
for x,y in p.NECK_SCREWS: probe(f'neck screw {x},{y}',['lid','neck_mount'],cyl(1.5,13,(x,y,76)))
for side in ('left','right'):
    sign=-1 if side=='left' else 1
    for dx in (-8,8):
        for dy in (-11,11): probe(f'motor {side} {dx},{dy}',['base',f'motor_cradle_{side}',f'motor_cap_{side}'],cyl(1.5,30,(sign*56+dx,40+dy,12)))
    probe(f'idler axle {side}',[f'tensioner_{side}',f'idler_wheel_{side}',f'bearing_spacer_{side}'],hole_x(3,35,(-105 if sign<0 else 70,-40,23)))
probe('opposed neck pivot',['head_stem','neck_mount'],hole_x(3,15,(27,12,102)))
for x in (9,25): probe(f'magnetic mount screw {x}',['head_stem','magnetic_head'],hole_y(3,32,(x,22,111)))
result={'units':'mm','probe_diameter_mm':3,'rows':rows,'passed':all(r['passed'] for r in rows),'scope':'Saved nominal assembly screw passages only; excludes screw heads, nuts, tools and actual purchased tolerances.'}
(root/'docs/mating_checks.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
if not result['passed']: raise SystemExit(1)
