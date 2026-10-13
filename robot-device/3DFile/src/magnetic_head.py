from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/magnetic_head.step")
@stl(out="../tmp/cadgen_mesh/magnetic_head.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def magnetic_head():
    return print_shape("magnetic_head")

if __name__ == "__main__":
    magnetic_head()
