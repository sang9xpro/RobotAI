from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/front_sensor_carrier.step")
@stl(out="../tmp/cadgen_mesh/front_sensor_carrier.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def front_sensor_carrier():
    return print_shape("front_sensor_carrier")

if __name__ == "__main__":
    front_sensor_carrier()
