from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/lid.step")
@stl(out="../tmp/cadgen_mesh/lid.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def lid():
    return print_shape("lid")

if __name__ == "__main__":
    lid()
