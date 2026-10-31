#!/usr/bin/env python3
"""Run the repository's isolated Matrix integration tests and retain their output."""

import argparse
from datetime import datetime, timezone
import json
import math
import os
import signal
import shutil
from pathlib import Path
import subprocess
import threading
import time
import uuid


class RunInterrupted(Exception):
    def __init__(self, signum):
        self.signum = signum


def interrupt(signum, _frame):
    raise RunInterrupted(signum)


def stream_output(process, path):
    with path.open("w") as output:
        for line in process.stdout:
            print(line, end="", flush=True)
            output.write(line)
            output.flush()


def terminate_group(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        pass
    # Also kill descendants which ignored SIGTERM after the launcher exited.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def docker(*arguments, include_stderr=False):
    result = subprocess.run(
        ["docker", *arguments], capture_output=True, text=True, timeout=10, check=True,
    )
    return (result.stdout + result.stderr if include_stderr else result.stdout).strip()


def process_identity(pid):
    """An OS start time prevents a stale registry PID from identifying another process."""
    if pid <= 1:
        return None
    result = subprocess.run(
        ["ps", "-p", str(pid), "-o", "lstart=,stat=,comm="],
        capture_output=True, text=True, timeout=2,
        env=dict(os.environ, TZ="UTC", LC_ALL="C"),
    )
    columns = result.stdout.strip().split(None, 6)
    if result.returncode or len(columns) < 7 or columns[5].startswith("Z"):
        return None
    started = int(datetime.strptime(" ".join(columns[:5]), "%a %b %d %H:%M:%S %Y").replace(tzinfo=timezone.utc).timestamp())
    return {"pid": pid, "started_at": started, "command": columns[6]}


def owned_workers(artifacts, run_id):
    owned = {}
    for path in (artifacts / "jvms").glob("*.json"):
        try:
            recorded = json.loads(path.read_text())
            actual = process_identity(int(recorded["pid"]))
            if actual and recorded["run_id"] == run_id and recorded["started_at"] == actual["started_at"]:
                owned[actual["pid"]] = actual
        except (OSError, ValueError, KeyError, subprocess.SubprocessError):
            continue
    # Single-use Gradle daemons can detach; their test workers are still their descendants.
    listing = subprocess.run(["ps", "-eo", "pid=,ppid="], capture_output=True, text=True, timeout=2, check=True)
    family = [tuple(map(int, line.split())) for line in listing.stdout.splitlines() if len(line.split()) == 2]
    changed = True
    while changed:
        changed = False
        for pid, parent in family:
            if parent in owned and pid not in owned:
                identity = process_identity(pid)
                if identity:
                    owned[pid] = identity
                    changed = True
    return list(owned.values())


def still_owned(identity):
    current = process_identity(identity["pid"])
    return current is not None and current["started_at"] == identity["started_at"]


def terminate_workers(workers):
    for sig in (signal.SIGTERM, signal.SIGKILL):
        for worker in workers:
            if still_owned(worker):
                try:
                    os.kill(worker["pid"], sig)
                except ProcessLookupError:
                    pass
        if sig == signal.SIGTERM:
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline and any(still_owned(worker) for worker in workers):
                time.sleep(0.05)


def capture_diagnostics(artifacts, run_id, workers):
    destination = artifacts / "diagnostics"
    destination.mkdir(exist_ok=True)
    (destination / "processes.json").write_text(json.dumps(workers, indent=2) + "\n")
    jcmd = shutil.which("jcmd")
    for worker in workers:
        if jcmd and Path(worker["command"]).name == "java" and still_owned(worker):
            try:
                dump = subprocess.run([jcmd, str(worker["pid"]), "Thread.print"], capture_output=True, text=True, timeout=5)
                (destination / f"jvm-{worker['pid']}.log").write_text(dump.stdout + dump.stderr)
            except (OSError, subprocess.SubprocessError) as failure:
                (destination / f"jvm-{worker['pid']}.log").write_text(str(failure) + "\n")
    try:
        containers = docker("ps", "-aq", "--filter", f"label=dev.phillipslabs.comcord.smoke-run={run_id}").split()
        for container in containers:
            logs = docker("logs", "--tail", "200", container, include_stderr=True)
            (destination / f"docker-{container}.log").write_text(logs + "\n")
    except (OSError, subprocess.SubprocessError) as failure:
        (destination / "capture-error.txt").write_text(str(failure) + "\n")


def cleanup_resources(artifacts, run_id):
    errors = []
    try:
        containers = docker("ps", "-aq", "--filter", f"label=dev.phillipslabs.comcord.smoke-run={run_id}").split()
        if containers:
            docker("rm", "--force", *containers)
    except (OSError, subprocess.SubprocessError) as failure:
        errors.append(f"Could not clean labeled containers: {failure}")
    try:
        shutil.rmtree(artifacts / "resources")
    except FileNotFoundError:
        pass
    except OSError as failure:
        errors.append(f"Could not remove temporary resources: {failure}")
    return errors


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        epilog="Exit: Gradle status; 124 on deadline; 128+signal on interruption; 1 on setup/cleanup failure. "
               "Logs, result.json and failure diagnostics are kept under build/reports/matrix-smoke/.",
    )
    parser.add_argument("--workflows-only", action="store_true", help="Run production workflows, excluding opt-in adoption gate fixtures")
    parser.add_argument("--tests", action="append", default=[], help="Gradle test filter; may be repeated")
    parser.add_argument("--timeout", type=float, default=600, help="Outer deadline in seconds (default: 600)")
    arguments = parser.parse_args()
    if os.name != "posix" or not math.isfinite(arguments.timeout) or arguments.timeout <= 0:
        parser.error("Requires macOS/Linux and a positive timeout")
    project = Path(__file__).resolve().parent.parent
    run_id = uuid.uuid4().hex
    artifacts = project / "build/reports/matrix-smoke" / run_id
    artifacts.mkdir(parents=True)
    print(f"SMOKE_ARTIFACTS={artifacts}", flush=True)
    resources = artifacts / "resources"
    resources.mkdir()
    environment = dict(os.environ, COMCORD_SMOKE_RUN_ID=run_id, COMCORD_SMOKE_RUN_DIR=str(artifacts))
    command = [
        str(project / "gradlew"), "--no-daemon", "--console=plain",
        "--init-script", str(project / "scripts/smoke-run.init.gradle.kts"),
        ":composeApp:matrixSmokeTest",
    ]
    if arguments.workflows_only:
        command.append("-PmatrixWorkflowsOnly=true")
    for pattern in arguments.tests:
        command.extend(["--tests", pattern])
    process = None
    output = None
    timed_out = False
    interrupted_by = None
    previous = {sig: signal.signal(sig, interrupt) for sig in (signal.SIGINT, signal.SIGTERM)}
    try:
        docker("info", "--format", "{{.ServerVersion}}")
        process = subprocess.Popen(
            command, cwd=project, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, start_new_session=True, env=environment,
        )
        output = threading.Thread(target=stream_output, args=(process, artifacts / "gradle.log"), daemon=True)
        output.start()
        exit_code = process.wait(timeout=arguments.timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        exit_code = 124
    except (OSError, subprocess.CalledProcessError) as failure:
        print(f"Smoke runner could not start: {failure}", flush=True)
        exit_code = 1
    except RunInterrupted as interruption:
        interrupted_by = signal.Signals(interruption.signum).name
        exit_code = 128 + interruption.signum
    finally:
        for sig in previous:
            signal.signal(sig, signal.SIG_IGN)
        cleanup_errors = []
        workers = []
        try:
            workers = owned_workers(artifacts, run_id)
            if exit_code != 0:
                capture_diagnostics(artifacts, run_id, workers)
        except (OSError, ValueError, subprocess.SubprocessError) as failure:
            cleanup_errors.append(f"Could not inspect owned workers: {failure}")
        try:
            if process is not None:
                terminate_group(process)
            terminate_workers(workers)
        except (OSError, subprocess.SubprocessError) as failure:
            cleanup_errors.append(f"Could not stop owned workers: {failure}")
        if output is not None:
            output.join(timeout=3)
        cleanup_errors.extend(cleanup_resources(artifacts, run_id))
        for error in cleanup_errors:
            print(error, flush=True)
        if cleanup_errors and exit_code == 0:
            exit_code = 1
        for sig, handler in previous.items():
            signal.signal(sig, handler)
    (artifacts / "result.json").write_text(
        json.dumps({"run_id": run_id, "exit_code": exit_code, "timed_out": timed_out,
                    "interrupted_by": interrupted_by, "cleanup_errors": cleanup_errors}, indent=2) + "\n"
    )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
