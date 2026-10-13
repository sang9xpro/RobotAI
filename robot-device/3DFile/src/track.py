from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/track.step")
@stl(out="../tmp/cadgen_mesh/track.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def track():
    return print_shape("track")

if __name__ == "__main__":
    track()
