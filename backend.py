#!/usr/bin/env python3
"""Start the prepared local RobotAI backend with one command."""
import argparse
import json
import runpy
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / ".local"
JAVA = ROOT / "server-java/scripts/robotai-local.py"
GATEWAY = ROOT / "services/llm-gateway/local.py"
WHISPER = ROOT / "services/phowhisper/local.py"
shared = runpy.run_path(str(ROOT / "server-java/scripts/robotai-preflight.py"))


def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def manager(path, action, *args):
    run(sys.executable, path, action, *args)


def wait_for(name, check, timeout=120):
    print(f"Chờ {name} sẵn sàng...", flush=True)
    deadline = time.monotonic() + timeout
    while True:
        try:
            if check():
                print(f"{name}: sẵn sàng", flush=True)
                return
        except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError(f"{name} chưa sẵn sàng sau {timeout}s. Xem log trong .local/.")
        time.sleep(1)


def http_ready(url, headers=None, expected=None):
    request = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(request, timeout=3) as response:
        if expected:
            return response.status == 200 and json.load(response).get("status") == expected
        return response.status == 200


def tcp_ready(port):
    with socket.create_connection(("127.0.0.1", port), timeout=3):
        return True


def management_ready():
    # OpsEndpointAccessConfig requires login for API docs and Actuator.
    # A 401 proves HTTP is responding, not authenticated application health.
    try:
        return http_ready("http://127.0.0.1:8091/v3/api-docs")
    except urllib.error.HTTPError as exc:
        return exc.code == 401


def start():
    if not (LOCAL / "backend.env").is_file() or not (LOCAL / "schema-owned").is_file():
        raise RuntimeError("Cần cấu hình .local/backend.env và khởi tạo database bằng robotai-local.py init-db trước.")
    env = shared["load_env"]()
    # Reuse the configured containers, including their existing volumes and passwords.
    mysql = env.get("ROBOTAI_MYSQL_CONTAINER", "dev-mysql")
    redis = env.get("ROBOTAI_REDIS_CONTAINER", "dev-redis")
    run("docker", "start", mysql, redis)

    def database_ready():
        mysql_env = dict(env, MYSQL_PWD=env["SPRING_DATASOURCE_PASSWORD"])
        result = shared["command"]([
            "docker", "exec", "-e", "MYSQL_PWD", mysql,
            "mysql", "-uroot", "-N", "robotai_ken", "-e", "SELECT 1;"], mysql_env)
        redis_env = env.copy()
        options = []
        if env.get("SPRING_DATA_REDIS_PASSWORD"):
            redis_env["REDISCLI_AUTH"] = env["SPRING_DATA_REDIS_PASSWORD"]
            options = ["-e", "REDISCLI_AUTH"]
        ping = shared["command"](["docker", "exec", *options, redis, "redis-cli", "--raw", "PING"], redis_env)
        return result == "1" and ping == "PONG"

    wait_for("MySQL / Redis", database_ready)
    manager(GATEWAY, "start")
    gateway = json.loads((LOCAL / "llm-gateway.json").read_text())
    wait_for("LLM gateway", lambda: http_ready(
        f"http://127.0.0.1:{gateway['port']}/health",
        {"Authorization": "Bearer " + gateway["token"]}))
    whisper = runpy.run_path(str(WHISPER))
    if not whisper["running"]():
        manager(WHISPER, "start")
    config = whisper["config"]()
    wait_for("PhoWhisper", lambda: bool(whisper["running"]()) and whisper["health"](config))
    java = runpy.run_path(str(JAVA))
    for module, port in [
        ("xiaozhi-server", 8091),
        ("xiaozhi-dialogue", 8092),
    ]:
        manager(JAVA, "start", "--module", module)
        # Dialogue has no HTTP health endpoint; verify its managed process and listener.
        wait_for(module, lambda: java["alive"](module) and (
            management_ready() if port == 8091 else tcp_ready(port)))
    print("\nBackend đã khởi chạy. API: http://127.0.0.1:8091")
    print("WebSocket: ws://127.0.0.1:8092/ws/xiaozhi/v1/")
    print("Log: .local/llm-gateway.log, phowhisper.log, xiaozhi-server.log, xiaozhi-dialogue.log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", nargs="?", default="start", choices=["start", "status", "stop"])
    args = parser.parse_args()
    if args.action == "start":
        start()
    else:
        for path in [JAVA, WHISPER, GATEWAY]:
            manager(path, args.action)
        if args.action == "stop":
            print("Đã gửi yêu cầu dừng backend. MySQL/Redis vẫn chạy vì có thể được dùng chung.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"Không hoàn tất: {exc}", file=sys.stderr)
        print("Các dịch vụ đã khởi chạy vẫn chạy; sửa lỗi rồi chạy lại python3 backend.py.", file=sys.stderr)
        sys.exit(1)
