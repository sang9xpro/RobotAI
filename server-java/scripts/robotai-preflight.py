#!/usr/bin/env python3
"""Read-only Docker/service checks. Never print credentials or container environments."""
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / ".local/backend.env"

def load_env():
    env = os.environ.copy()
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text().splitlines():
            if line and not line.startswith("#"):
                key, value = line.split("=", 1)
                env[key] = value
    return env

def command(args, env=None):
    result = subprocess.run(args, text=True, capture_output=True, env=env, timeout=30)
    if result.returncode:
        # Do not surface arbitrary stderr: container commands could echo configuration.
        raise RuntimeError(f"Command failed ({result.returncode}): {args[0]} {args[1]}")
    return result.stdout.strip()

def main():
    env = load_env()
    mysql = env.get("ROBOTAI_MYSQL_CONTAINER", "dev-mysql")
    redis = env.get("ROBOTAI_REDIS_CONTAINER", "dev-redis")
    containers = json.loads(command(["docker", "inspect", mysql, redis]))
    details = []
    for item in containers:
        details.append(dict(name=item["Name"].lstrip("/"), running=item["State"]["Running"],
                            image=item["Config"]["Image"], ports=item["NetworkSettings"]["Ports"]))
    mysql_env = env.copy()
    mysql_env["MYSQL_PWD"] = env["SPRING_DATASOURCE_PASSWORD"]
    query = "SELECT VERSION(); SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('xiaozhi','robotai_ken');"
    rows = command(["docker", "exec", "-e", "MYSQL_PWD", mysql, "mysql", "-uroot", "-N", "-e", query], mysql_env).splitlines()
    # Plain redis-cli produces NOAUTH as stdout with zero exit code; classify it explicitly.
    redis_env = env.copy()
    options = []
    if env.get("SPRING_DATA_REDIS_PASSWORD"):
        redis_env["REDISCLI_AUTH"] = env["SPRING_DATA_REDIS_PASSWORD"]
        options = ["-e", "REDISCLI_AUTH"]
    ping = command(["docker", "exec", *options, redis, "redis-cli", "--raw", "PING"], redis_env)
    info = {}
    if ping == "PONG":
        raw = command(["docker", "exec", *options, redis, "redis-cli", "--raw", "INFO", "server"], redis_env)
        info = dict(line.split(":", 1) for line in raw.splitlines() if ":" in line)
        counts = {}
        for database in [15, 14, 13]:
            count = command(["docker", "exec", *options, redis, "redis-cli", "-n", str(database), "--raw", "DBSIZE"], redis_env)
            counts[str(database)] = int(count)
        info = dict(version=info.get("redis_version"), ping="PONG", database_key_counts=counts,
                    authenticated=bool(options))
    else:
        info = dict(ping="AUTH_REQUIRED" if "NOAUTH" in ping else "FAILED")
    print(json.dumps(dict(containers=details, mysql=dict(version=rows[0],
        known_schemas=rows[1:], credentials_verified=True), redis=info), indent=2))

if __name__ == "__main__":
    main()
