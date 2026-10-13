from cadgen import build123d as bd, step
from lib.assembly_layout import models, laid_parts, hardware

@step(out='../STEP/internal_assembly.step')
def internal_assembly():
    m=models()
    parts=[s for s in laid_parts(m,False) if s.label not in ['neck_mount','servo_cap','head_stem','magnetic_head','neck_shroud','tower_side_panel_left','tower_side_panel_right','cable_clip','qi_carrier']]
    hw=[s for s in hardware() if not any(t in s.label for t in ('servo_HS','servo_horn','OPPO','eye_','camera_island','Qi_puck'))]
    return bd.Compound(children=parts+hw,label='MAM_internal_layout')

if __name__=='__main__':
    internal_assembly()
