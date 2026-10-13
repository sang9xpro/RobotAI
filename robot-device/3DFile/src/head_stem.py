from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/head_stem.step")
@stl(out="../tmp/cadgen_mesh/head_stem.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def head_stem():
    return print_shape("head_stem")

if __name__ == "__main__":
    head_stem()
