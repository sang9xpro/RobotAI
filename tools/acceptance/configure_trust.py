#!/usr/bin/env python3
"""Add a macOS-verified network CA to the project-local Java truststore only."""
from pathlib import Path
import os, runpy, shutil, subprocess
ROOT=Path(__file__).resolve().parents[2]
leaf=ROOT/'.local/weather-chain-0.pem';root=ROOT/'.local/weather-chain-1.pem'
verify=subprocess.run(['security','verify-cert','-c',str(leaf),'-c',str(root),'-p','ssl','-s','api.open-meteo.com'],capture_output=True)
if verify.returncode: raise SystemExit('macOS does not trust this chain; refusing to import its CA.')
env=runpy.run_path(str(ROOT/'server-java/scripts/robotai-preflight.py'))['load_env']()
home=Path(env.get('JAVA_HOME',str(Path.home()/'Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home')))
store=ROOT/'.local/java-truststore'
if not store.exists(): shutil.copyfile(home/'lib/security/cacerts',store)
os.chmod(store,0o600)
keytool=home/'bin/keytool'
existing=subprocess.run([str(keytool),'-list','-alias','robotai-network-ca','-keystore',str(store),'-storepass','changeit'],capture_output=True)
if existing.returncode:
    added=subprocess.run([str(keytool),'-importcert','-noprompt','-alias','robotai-network-ca','-file',str(root),'-keystore',str(store),'-storepass','changeit'],capture_output=True)
    if added.returncode: raise SystemExit('Local CA import failed.')
print('macOS-verified CA added to project-local truststore; global JDK and Android trust unchanged.')
