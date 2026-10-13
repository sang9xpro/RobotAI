from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/motor_cradle.step")
@stl(out="../tmp/cadgen_mesh/motor_cradle.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def motor_cradle():
    return print_shape("motor_cradle")

if __name__ == "__main__":
    motor_cradle()
