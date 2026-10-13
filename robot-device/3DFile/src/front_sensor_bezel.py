from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/front_sensor_bezel.step")
@stl(out="../tmp/cadgen_mesh/front_sensor_bezel.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def front_sensor_bezel():
    return print_shape("front_sensor_bezel")

if __name__ == "__main__":
    front_sensor_bezel()
