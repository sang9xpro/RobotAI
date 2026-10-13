from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/cable_clip.step")
@stl(out="../tmp/cadgen_mesh/cable_clip.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def cable_clip():
    return print_shape("cable_clip")

if __name__ == "__main__":
    cable_clip()
