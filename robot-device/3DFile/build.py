"""Rebuild MAM CAD and optional checks/previews. Run from 3DFile."""
from pathlib import Path
import os, subprocess, sys, json, argparse
root=Path(__file__).resolve().parent
os.chdir(root)
(root/'tmp').mkdir(exist_ok=True)
env=os.environ.copy();env['CADGEN_CACHE_DIR']=str(root/'.cadgen-cache');env['CADGEN_DAEMON']='0'
p=argparse.ArgumentParser();p.add_argument('--review',action='store_true');p.add_argument('--checks',action='store_true');args=p.parse_args()
for model in ['assembly','internal_assembly','parts_layout','magnetic_mount_preview']:
    with open(root/'tmp'/f'{model}_build.log','w') as f:
        result=subprocess.run([sys.executable,f'src/{model}.py','--json'],env=env,stdout=f,stderr=subprocess.STDOUT)
    print(model,'OK' if result.returncode==0 else 'FAILED',flush=True)
    if result.returncode: sys.exit(result.returncode)
# cadgen 0.7.6 mesh exporter produced boundary/nonmanifold edges on valid BReps.
# Retessellate the exact same saved STEP with OCCT for the printable deliverables.
from cadgen import read_step
import build123d as bd
import trimesh
os.environ['CADGEN_CACHE_DIR']=env['CADGEN_CACHE_DIR']
sys.path.insert(0,str(root/'src'))
from lib.assembly_layout import PART_NAMES
for name in PART_NAMES:
    shape=read_step(root/'STEP'/f'{name}.step')
    out=root/'STL'/f'{name}.stl'
    bd.export_stl(shape,out,tolerance=0.04,angular_tolerance=0.15)
    mesh=trimesh.load(out,force='mesh')
    if not mesh.is_watertight or len(shape.solids())!=1:
        raise RuntimeError(f'Invalid print mesh: {name}')
    else: print('print STL',name,'OK',flush=True)
if args.checks:
    for check in ['validate','mating','motion']:
        subprocess.run([sys.executable,f'checks/{check}.py'],env=env,check=True)
if args.review:
    jobs=[]
    for f in sorted((root/'STEP').glob('*.step')):
        outputs=[{'path':str(root/'previews'/f'{f.stem}.png'),'camera':'iso'}]
        if f.stem=='assembly':
            outputs=[{'path':str(root/'previews'/'assembly.png'),'camera':{'direction':[1,-1,0.7]}},{'path':str(root/'previews'/'assembly_rear.png'),'camera':{'direction':[-1,1,.65]}}]
        jobs.append({'input':str(f),'outputs':outputs,'output':{'sizeProfile':'simple','padding':.08},'display':{'mode':'solid','edges':{'enabled':False}}})
    (root/'tmp'/'review_job.json').write_text(json.dumps({'jobs':jobs},indent=2))
    subprocess.run([sys.executable,'-m','cadgen.cli','step','snapshot','--job','tmp/review_job.json'],env=env,check=True)
