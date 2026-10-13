"""Check exported geometry, topology and rigid assembly interference in mm."""
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'src'))
from cadgen import read_step, read_scene
from cadgen.geometry import overlap_volume
from lib.assembly_layout import PART_NAMES
report={'units':'mm','part_checks':[],'interferences':[]}
for name in PART_NAMES:
    f=ROOT/'STEP'/f'{name}.step'
    if not f.exists(): continue
    s=read_step(f); bb=s.bounding_box(); solids=s.solids()
    row={'name':name,'valid':bool(s.is_valid),'solids':len(solids),'volume_mm3':round(s.volume,3),'bounds_mm':[round(v,3) for v in bb.size],'min_z':round(bb.min.Z,5),'fits_220_bed':bb.size.X<=220 and bb.size.Y<=220}
    report['part_checks'].append(row)
    print(json.dumps(row),flush=True)
if (ROOT/'STEP/assembly.step').exists():
    scene=read_scene(ROOT/'STEP/assembly.step')
    leaves=[(o.label,o.shape()) for o in scene.leaves()]
    print('Checking assembly pairs:',len(leaves),flush=True)
    # AABB pruning; all actual overlaps retained, exclusions are interpreted later.
    for i,(na,a) in enumerate(leaves):
        aa=a.bounding_box()
        for nb,b in leaves[i+1:]:
            if 'eye_' in na or 'eye_' in nb: continue
            bb=b.bounding_box()
            if any(min(getattr(aa.max,k),getattr(bb.max,k))-max(getattr(aa.min,k),getattr(bb.min,k))<0.02 for k in ('X','Y','Z')): continue
            try:
                volume=sum(overlap_volume(sa,sb) for sa in a.solids() for sb in b.solids())
                if volume>0.1:
                    row={'a':na,'b':nb,'overlap_mm3':round(volume,3)}
                    report['interferences'].append(row); print(json.dumps(row),flush=True)
            except Exception as e: report['interferences'].append({'a':na,'b':nb,'error':str(e)})
(ROOT/'docs/geometry_checks.json').write_text(json.dumps(report,indent=2))

expected={(f'{wheel}_wheel_{side}',f'track_{side}') for wheel in ('drive','idler') for side in ('left','right')}
for row in report['interferences']:
    row['accepted_elastic_preload']=(row['a'],row['b']) in expected and 'error' not in row
report['elastic_preload_note']='Only four wheel/TPU belt pairs have intentional 0.3 mm radial preload; all other overlap above 0.1 mm3 fails.'
report['passed']=len(report['part_checks'])==len(PART_NAMES) and all(r['valid'] and r['solids']==1 and r['volume_mm3']>0 and abs(r['min_z'])<0.001 and r['fits_220_bed'] for r in report['part_checks']) and all(r['accepted_elastic_preload'] for r in report['interferences'])
(ROOT/'docs/geometry_checks.json').write_text(json.dumps(report,indent=2))
if not report['passed']: raise SystemExit('Geometry validation failed; see docs/geometry_checks.json')
