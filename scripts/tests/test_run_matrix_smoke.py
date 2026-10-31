import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest


RUNNER = Path(__file__).resolve().parents[1] / "run_matrix_smoke.py"


class SmokeRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.project = Path(self.temporary.name)
        (self.project / "scripts").mkdir()
        if RUNNER.exists():
            shutil.copyfile(RUNNER, self.project / "scripts/run_matrix_smoke.py")
        self.bin = self.project / "bin"
        self.bin.mkdir()
        docker = self.bin / "docker"
        docker.write_text(
            "#!/usr/bin/env python3\nimport json, os, sys\nfrom pathlib import Path\n"
            "with Path(os.environ['DOCKER_CALLS']).open('a') as log: log.write(json.dumps(sys.argv[1:]) + '\\n')\n"
            "if sys.argv[1] == 'ps': print(os.environ.get('OWNED_CONTAINER', ''))\n"
            "if sys.argv[1] == 'logs': print('owned container diagnostic'); print('owned stderr diagnostic', file=sys.stderr)\n"
        )
        docker.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                                DOCKER_CALLS=str(self.project / "docker-calls.jsonl"))
        init_script = RUNNER.with_name("smoke-run.init.gradle.kts")
        if init_script.exists():
            shutil.copyfile(init_script, self.project / "scripts/smoke-run.init.gradle.kts")
        self.gradle = self.project / "gradlew"
        self.gradle.write_text(
            "#!/usr/bin/env python3\nimport sys\nprint('fixture output', flush=True)\nsys.exit(7)\n"
        )
        self.gradle.chmod(0o755)

    def run_runner(self, *arguments):
        return subprocess.run(
            [sys.executable, str(self.project / "scripts/run_matrix_smoke.py"), *arguments],
            cwd=self.project, env=self.environment,
            capture_output=True,
            text=True,
            timeout=15,
        )

    def report(self, result):
        paths = [line.split("=", 1)[1] for line in result.stdout.splitlines() if line.startswith("SMOKE_ARTIFACTS=")]
        self.assertEqual(1, len(paths), result.stdout + result.stderr)
        directory = Path(paths[0])
        return directory, json.loads((directory / "result.json").read_text())

    def test_preserves_failure_exit_code_and_captures_output(self):
        result = self.run_runner()
        self.assertEqual(7, result.returncode, result.stdout + result.stderr)
        directory, report = self.report(result)
        self.assertEqual(7, report["exit_code"])
        self.assertIn("fixture output", result.stdout)
        self.assertIn("fixture output", (directory / "gradle.log").read_text())

    def test_timeout_stops_the_command_and_its_descendant(self):
        self.gradle.write_text(
            "#!/usr/bin/env python3\n"
            "import subprocess, sys, time\n"
            "from pathlib import Path\n"
            "child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])\n"
            "Path('child.pid').write_text(str(child.pid))\n"
            "print('waiting for timeout', flush=True)\n"
            "time.sleep(60)\n"
        )
        result = self.run_runner("--timeout", "0.5")
        self.assertEqual(124, result.returncode, result.stdout + result.stderr)
        _, report = self.report(result)
        self.assertTrue(report["timed_out"])
        child = int((self.project / "child.pid").read_text())
        status = subprocess.run(["ps", "-p", str(child), "-o", "stat="], capture_output=True, text=True)
        self.assertTrue(not status.stdout.strip() or status.stdout.strip().startswith("Z"), status.stdout)

    def test_interruption_reports_cancellation_and_stops_the_command(self):
        self.gradle.write_text(
            "#!/usr/bin/env python3\nimport os, time\nfrom pathlib import Path\n"
            "Path('launcher.pid').write_text(str(os.getpid()))\ntime.sleep(60)\n"
        )
        runner = subprocess.Popen(
            [sys.executable, str(self.project / "scripts/run_matrix_smoke.py")],
            cwd=self.project, env=self.environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        launcher = None
        try:
            deadline = time.monotonic() + 5
            while not (self.project / "launcher.pid").exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            launcher = int((self.project / "launcher.pid").read_text())
            runner.send_signal(signal.SIGTERM)
            stdout, stderr = runner.communicate(timeout=8)
            result = subprocess.CompletedProcess(runner.args, runner.returncode, stdout, stderr)
            self.assertEqual(143, result.returncode, stdout + stderr)
            _, report = self.report(result)
            self.assertEqual("SIGTERM", report["interrupted_by"])
            status = subprocess.run(["ps", "-p", str(launcher), "-o", "stat="], capture_output=True, text=True)
            self.assertTrue(not status.stdout.strip() or status.stdout.strip().startswith("Z"), status.stdout)
        finally:
            if launcher is not None:
                try:
                    os.killpg(launcher, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            if runner.poll() is None:
                runner.kill()
                runner.communicate()

    def test_cleans_only_labeled_containers_and_run_owned_temporary_data(self):
        self.environment["OWNED_CONTAINER"] = "owned-container"
        self.gradle.write_text(
            "#!/usr/bin/env python3\nimport os\nfrom pathlib import Path\n"
            "directory = Path(os.environ['COMCORD_SMOKE_RUN_DIR']) / 'resources'\n"
            "directory.mkdir(exist_ok=True)\n(directory / 'disposable-data').write_text('test')\n"
        )
        result = self.run_runner()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        directory, report = self.report(result)
        self.assertFalse((directory / "resources").exists())
        calls = [json.loads(line) for line in (self.project / "docker-calls.jsonl").read_text().splitlines()]
        listings = [call for call in calls if call[0] == "ps"]
        self.assertTrue(listings)
        for call in listings:
            self.assertIn("label=dev.phillipslabs.comcord.smoke-run=" + report["run_id"], call)
        self.assertEqual([["rm", "--force", "owned-container"]], [call for call in calls if call[0] == "rm"])

    def test_timeout_retains_owned_container_diagnostics_before_cleanup(self):
        self.environment["OWNED_CONTAINER"] = "owned-container"
        self.gradle.write_text("#!/usr/bin/env python3\nimport time\ntime.sleep(60)\n")
        result = self.run_runner("--timeout", "0.3")
        self.assertEqual(124, result.returncode, result.stdout + result.stderr)
        directory, _ = self.report(result)
        self.assertIn("owned container diagnostic", (directory / "diagnostics/docker-owned-container.log").read_text())
        self.assertIn("owned stderr diagnostic", (directory / "diagnostics/docker-owned-container.log").read_text())

    def write_registered_worker(self, start_offset=0, require_startup_hook=False):
        self.environment["START_OFFSET"] = str(start_offset)
        self.environment["REQUIRE_STARTUP_HOOK"] = str(int(require_startup_hook))
        self.gradle.write_text(
            "#!/usr/bin/env python3\nimport os, subprocess, sys, json, time\n"
            "from pathlib import Path\nfrom datetime import datetime, timezone\n"
            "child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'], start_new_session=True)\n"
            "Path('child.pid').write_text(str(child.pid))\n"
            "start = subprocess.check_output(['ps', '-p', str(child.pid), '-o', 'lstart='], text=True, env=dict(os.environ, TZ='UTC', LC_ALL='C')).strip()\n"
            "epoch = int(datetime.strptime(start, '%a %b %d %H:%M:%S %Y').replace(tzinfo=timezone.utc).timestamp())\n"
            "if os.environ['REQUIRE_STARTUP_HOOK'] == '1' and '--init-script' not in sys.argv: time.sleep(60)\n"
            "if '--init-script' in sys.argv: assert Path(sys.argv[sys.argv.index('--init-script') + 1]).is_file()\n"
            "registry = Path(os.environ['COMCORD_SMOKE_RUN_DIR']) / 'jvms'\nregistry.mkdir()\n"
            "(registry / (str(child.pid) + '.json')).write_text(json.dumps(dict(pid=child.pid, started_at=epoch + int(os.environ['START_OFFSET']), run_id=os.environ['COMCORD_SMOKE_RUN_ID'])))\n"
            "time.sleep(60)\n"
        )

    def test_timeout_stops_a_detached_daemon_before_task_execution(self):
        self.write_registered_worker(require_startup_hook=True)
        child = None
        try:
            result = self.run_runner("--timeout", "0.6")
            self.assertEqual(124, result.returncode, result.stdout + result.stderr)
            child = int((self.project / "child.pid").read_text())
            status = subprocess.run(["ps", "-p", str(child), "-o", "stat="], capture_output=True, text=True)
            self.assertTrue(not status.stdout.strip() or status.stdout.strip().startswith("Z"), status.stdout)
        finally:
            if child is not None:
                try:
                    os.killpg(child, signal.SIGKILL)
                except ProcessLookupError:
                    pass

    def test_stale_pid_record_does_not_kill_another_process(self):
        self.write_registered_worker(start_offset=-100)
        child = None
        try:
            result = self.run_runner("--timeout", "0.6")
            self.assertEqual(124, result.returncode, result.stdout + result.stderr)
            child = int((self.project / "child.pid").read_text())
            status = subprocess.run(["ps", "-p", str(child), "-o", "stat="], capture_output=True, text=True)
            self.assertTrue(status.stdout.strip() and not status.stdout.strip().startswith("Z"), status.stdout)
        finally:
            if child is not None:
                try:
                    os.killpg(child, signal.SIGKILL)
                except ProcessLookupError:
                    pass

    def test_process_inspection_failure_still_cleans_resources_and_reports_failure(self):
        self.environment["OWNED_CONTAINER"] = "owned-container"
        ps = self.bin / "ps"
        ps.write_text("#!/bin/sh\nexit 2\n")
        ps.chmod(0o755)
        self.gradle.write_text("#!/usr/bin/env python3\nprint('completed')\n")
        result = self.run_runner()
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        directory, report = self.report(result)
        self.assertIn("Could not inspect owned workers", report["cleanup_errors"][0])
        self.assertFalse((directory / "resources").exists())
        calls = [json.loads(line) for line in (self.project / "docker-calls.jsonl").read_text().splitlines()]
        self.assertIn(["rm", "--force", "owned-container"], calls)

    def test_workflow_mode_is_forwarded_without_losing_explicit_filters(self):
        self.gradle.write_text(
            "#!/usr/bin/env python3\nimport json, sys\nfrom pathlib import Path\n"
            "Path('arguments.json').write_text(json.dumps(sys.argv[1:]))\n"
        )
        result = self.run_runner("--workflows-only", "--tests", "*AuthenticationSmokeTest")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        arguments = json.loads((self.project / "arguments.json").read_text())
        self.assertIn("-PmatrixWorkflowsOnly=true", arguments)
        self.assertEqual("*AuthenticationSmokeTest", arguments[arguments.index("--tests") + 1])


if __name__ == "__main__":
    unittest.main()
