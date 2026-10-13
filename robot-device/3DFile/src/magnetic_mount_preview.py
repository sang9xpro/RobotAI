from cadgen import build123d as bd, step
from lib.assembly_layout import models, laid_parts, hardware

@step(out='../STEP/magnetic_mount_preview.step')
def magnetic_mount_preview():
    parts=laid_parts(models())
    parts += [p for p in hardware() if p.label not in ['OPPO_Find_X8_envelope','camera_island_clearance_envelope'] and not p.label.startswith('eye_')]
    return bd.Compound(children=parts,label='MAM_magnetic_mount_without_phone')

if __name__=='__main__': magnetic_mount_preview()
