import json, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
out=ROOT/'docs/dfam'; out.mkdir(exist_ok=True)
summary=[]
for f in sorted((ROOT/'STL').glob('*.stl')):
    cmd=[sys.executable,str(ROOT/'checks/dfam_tool.py'),'measure',str(f),'--samples','2500','--angle-limit','45']
    r=subprocess.run(cmd,capture_output=True,text=True)
    (out/(f.stem+'.json')).write_text(r.stdout or json.dumps({'error':r.stderr}))
    try:
        d=json.loads(r.stdout); w=d.get('wall_thickness',{}); mesh=d.get('mesh',{})
        item={'part':f.stem,'exit_code':r.returncode,'watertight':mesh.get('watertight'),'body_count':mesh.get('body_count'),'p05_mm':w.get('p05_mm'),'min_mm':w.get('min_mm'),'support_area_pct':d.get('overhangs',{}).get('down_facing_area_below_limit_pct'),'partial':d.get('partial',False)}
        if item['support_area_pct'] and item['support_area_pct']>0:
            orient=subprocess.run([sys.executable,str(ROOT/'checks/dfam_tool.py'),'orientations',str(f),'--angle-limit','45'],capture_output=True,text=True)
            (out/(f.stem+'_orientations.json')).write_text(orient.stdout)
    except Exception as e: item={'part':f.stem,'error':str(e),'stderr':r.stderr[-1000:]}
    summary.append(item);print(json.dumps(item),flush=True)
(out/'summary.json').write_text(json.dumps(summary,indent=2))
