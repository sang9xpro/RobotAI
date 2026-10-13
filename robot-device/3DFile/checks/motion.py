import json
from pathlib import Path
from cadgen import read_scene, build123d as bd
from cadgen.geometry import overlap_volume
root=Path(__file__).resolve().parents[1]
scene=read_scene(root/'STEP/assembly.step')
move={'head_stem','magnetic_head','OPPO_Find_X8_envelope','camera_island_clearance_envelope','Qi_puck_56x7_envelope','servo_horn_envelope'}
leaves={o.label:o.shape() for o in scene.leaves() if not o.label.startswith('eye_')}
axis=bd.Axis((0,12,102),(1,0,0))
report={'units':'mm','axis':[0,12,102,1,0,0],'sampled_poses':[],'scope':'Rigid geometric interference only; cable flex, torque and thermal behavior require prototype tests.'}
for angle in [-15,-10,-5,0,5,10,15]:
    collisions=[]; distance=1e9; nearest=None
    for n in move:
        a=leaves[n].rotate(axis,angle)
        aa=a.bounding_box()
        for nb,b in leaves.items():
            if nb in move: continue
            bb=b.bounding_box()
            if any(min(getattr(aa.max,k),getattr(bb.max,k))-max(getattr(aa.min,k),getattr(bb.min,k))<0.02 for k in ('X','Y','Z')): continue
            v=sum(overlap_volume(sa,sb) for sa in a.solids() for sb in b.solids())
            if v>.1: collisions.append({'moving':n,'fixed':nb,'volume_mm3':round(v,3)})
    row={'angle_deg':angle,'collisions':collisions};report['sampled_poses'].append(row);print(json.dumps(row),flush=True)
(root/'docs/motion_checks.json').write_text(json.dumps(report,indent=2))

report['operating_limit_deg']=[-10,10]
report['passed_within_operating_limit']=all(not r['collisions'] for r in report['sampled_poses'] if abs(r['angle_deg'])<=10)
(root/'docs/motion_checks.json').write_text(json.dumps(report,indent=2))
if not report['passed_within_operating_limit']: raise SystemExit(1)
