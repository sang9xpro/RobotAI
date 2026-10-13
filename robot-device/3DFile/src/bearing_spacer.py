from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/bearing_spacer.step")
@stl(out="../tmp/cadgen_mesh/bearing_spacer.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def bearing_spacer():
    return print_shape("bearing_spacer")

if __name__ == "__main__":
    bearing_spacer()
