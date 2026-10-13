from cadgen import build123d as bd, step, glb
from lib.assembly_layout import models, laid_parts, hardware

@step(out='../STEP/assembly.step')
@glb(out='../GLB/assembly.glb',mesh_tolerance=0.0007)
def assembly():
    m=models()
    return bd.Compound(children=laid_parts(m)+hardware(),label='MAM_Find_X8_R4_compact_tower')

if __name__=='__main__':
    assembly()
