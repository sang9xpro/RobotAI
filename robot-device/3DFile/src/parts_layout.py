from cadgen import build123d as bd, step
from lib.assembly_layout import models

@step(out='../STEP/parts_layout.step')
def parts_layout():
    m=models(); parts=[]
    for i,(n,s) in enumerate(m.items()):
        s=s.moved(bd.Location(((i%5)*210,(i//5)*210,0)))
        s.label=n
        parts.append(s)
    return bd.Compound(children=parts,label='print_parts_catalog_NOT_single_print_plate')

if __name__=='__main__':
    parts_layout()
