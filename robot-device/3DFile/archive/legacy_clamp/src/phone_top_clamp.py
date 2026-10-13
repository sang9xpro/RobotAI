from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/phone_top_clamp.step")
@stl(out="../tmp/cadgen_mesh/phone_top_clamp.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def phone_top_clamp():
    return print_shape("phone_top_clamp")

if __name__ == "__main__":
    phone_top_clamp()
