from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/base.step")
@stl(out="../tmp/cadgen_mesh/base.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def base():
    return print_shape("base")

if __name__ == "__main__":
    base()
