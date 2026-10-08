#!/usr/bin/env python3
"""Local B1 lifecycle. Secrets stay in ignored .local/backend.env, not argv/log output."""
import argparse
import json
import os
import runpy
import signal
import socket
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "server-java"
LOCAL = ROOT / ".local"
shared = runpy.run_path(str(Path(__file__).with_name("robotai-preflight.py")))

def mysql(query):
    env = shared["load_env"]()
    env["MYSQL_PWD"] = env["SPRING_DATASOURCE_PASSWORD"]
    return shared["command"](["docker", "exec", "-e", "MYSQL_PWD", env.get("ROBOTAI_MYSQL_CONTAINER", "dev-mysql"),
        "mysql", "-uroot", "-N", "-e", query], env)

def alive(module):
    pidfile = LOCAL / f"{module}.pid"
    if not pidfile.exists():
        return False
    try:
        pid = int(pidfile.read_text())
        args = subprocess.check_output(["ps", "-p", str(pid), "-o", "command="], text=True)
        return str(BACKEND / module / "target") in args
    except (ProcessLookupError, ValueError, subprocess.CalledProcessError):
        return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["init-db", "start", "status", "stop", "database-status"])
    parser.add_argument("--module", choices=["xiaozhi-server", "xiaozhi-dialogue", "all"], default="all")
    args = parser.parse_args()
    if args.action == "init-db":
        # Refuse Flyway's initial DROP/CREATE on a pre-existing unrelated schema.
        tables = mysql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='robotai_ken';")
        if int(tables) and not (LOCAL / "schema-owned").exists():
            raise SystemExit("robotai_ken already contains tables and is not owned by this setup; refusing migration.")
        mysql("CREATE DATABASE IF NOT EXISTS robotai_ken CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        (LOCAL / "schema-owned").write_text("robotai_ken\n")
        print("Dedicated schema robotai_ken ready.")
        return
    if args.action == "database-status":
        tables = mysql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='robotai_ken';")
        print(f"Tables: {tables}")
        print("Migration version/success:\n" + mysql("SELECT version,success FROM robotai_ken.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"))
        # Only provider metadata, no endpoints, passwords, API keys or messages.
        print("LLM provider metadata (type/provider/default/enabled):\n" + mysql("SELECT configType,provider,isDefault,state FROM robotai_ken.sys_config WHERE configType='llm';"))
        return
    modules = ["xiaozhi-server", "xiaozhi-dialogue"] if args.module == "all" else [args.module]
    for module in modules:
        port = 8091 if module == "xiaozhi-server" else 8092
        pidfile = LOCAL / f"{module}.pid"
        if args.action == "start":
            if alive(module):
                print(f"{module}: already running")
                continue
            if not (LOCAL / "schema-owned").exists():
                raise SystemExit("Run init-db first.")
            jars = list((BACKEND / module / "target").glob("*-exec.jar"))
            if not jars:
                jars = list((BACKEND / module / "target").glob("*.jar"))
            if len(jars) != 1:
                raise SystemExit(f"Build {module} first.")
            with socket.socket() as probe:
                if probe.connect_ex(("127.0.0.1", port)) == 0:
                    raise SystemExit(f"Port {port} is occupied; refusing to replace another service.")
            env = shared["load_env"]()
            # Dialogue YAML has a legacy literal default; override it through Spring's env binding.
            env["SA_TOKEN_JWT_SECRET_KEY"] = env["JWT_SECRET_KEY"]
            java_home = env.get("JAVA_HOME", str(Path.home() / "Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home"))
            java_args = []
            truststore = LOCAL / "java-truststore"
            if truststore.is_file():
                java_args = ["-Djavax.net.ssl.trustStore=" + str(truststore),
                             "-Djavax.net.ssl.trustStorePassword=changeit"]
            env["SERVER_ADDRESS"] = "127.0.0.1"
            (LOCAL / "runtime").mkdir(exist_ok=True)
            logfile = LOCAL / f"{module}.log"
            with logfile.open("a") as out:
                os.chmod(logfile, 0o600)
                process = subprocess.Popen([str(Path(java_home) / "bin/java"), *java_args, "-Xmx1024m", "-jar", str(jars[0])],
                    cwd=BACKEND, env=env, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
            pidfile.write_text(str(process.pid))
            print(f"{module}: started PID {process.pid}, port {port}; startup is asynchronous")
        elif args.action == "stop":
            if alive(module):
                os.kill(int(pidfile.read_text()), signal.SIGTERM)
                print(f"{module}: SIGTERM sent")
            else:
                print(f"{module}: not running")
        else:
            with socket.socket() as probe:
                open_port = probe.connect_ex(("127.0.0.1", port)) == 0
            print(f"{module}: process={alive(module)}, tcp={open_port}, http://127.0.0.1:{port}")
            if module == "xiaozhi-server" and open_port:
                try:
                    with urllib.request.urlopen(f"http://127.0.0.1:{port}/v3/api-docs", timeout=5) as response:
                        doc = json.load(response)
                        print(f"OpenAPI: {doc.get('openapi')}, paths={len(doc.get('paths',{}))}")
                except urllib.error.HTTPError as exc:
                    print(f"OpenAPI HTTP {exc.code}; not proof of management authentication readiness")
                except Exception as exc:
                    print(f"OpenAPI probe: {type(exc).__name__}")

if __name__ == "__main__":
    main()
