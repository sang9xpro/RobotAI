"""Prepare project-local Vulkan shader tools and pinned headers (macOS ARM64)."""
from pathlib import Path
import os, subprocess
root = Path(__file__).resolve().parents[1]
work = root / '.work'; work.mkdir(exist_ok=True)
conda = os.environ.get('CONDA_EXE', 'conda')
def run(*args): subprocess.run([str(a) for a in args], check=True)
if not (work/'vulkan-host/bin/glslc').exists():
    run(conda, 'create', '-y', '-p', work/'vulkan-host', '-c', 'conda-forge', 'shaderc=2026.4', 'cmake=4.4.3')
for name, remote, ref in [
    ('Vulkan-Headers','https://github.com/KhronosGroup/Vulkan-Headers.git','vulkan-sdk-1.4.328.1'),
    ('SPIRV-Headers','https://github.com/KhronosGroup/SPIRV-Headers.git','cb42dec3830d3ac67fa449ecdc0c0f73d5e74498')]:
    path = work/name
    if not path.exists():
        run('git', 'init', path); run('git', '-C', path, 'remote', 'add', 'origin', remote)
        run('git', '-C', path, 'fetch', '--depth', '1', 'origin', ref)
        run('git', '-C', path, 'checkout', '--detach', 'FETCH_HEAD')
cmake = work/'vulkan-host/bin/cmake'
run(cmake, '-S', work/'SPIRV-Headers', '-B', work/'spirv-headers-build', f'-DCMAKE_INSTALL_PREFIX={work / "vulkan-deps"}')
run(cmake, '--install', work/'spirv-headers-build')
print('Vulkan build dependencies ready:', work)
