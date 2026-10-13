from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/sensor_carrier.step")
@stl(out="../tmp/cadgen_mesh/sensor_carrier.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def sensor_carrier():
    return print_shape("sensor_carrier")

if __name__ == "__main__":
    sensor_carrier()
