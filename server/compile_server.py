from flask import Flask, request, jsonify
import subprocess, tempfile, os, shutil

app = Flask(__name__)

def which(exe): 
    # On Windows: kotlinc.bat; elsewhere: kotlinc
    return shutil.which(exe) or exe

def run(cmd, timeout=20, cwd=None):
    """Run a command, return (rc, stdout, stderr). Raises on timeout."""
    p = subprocess.run(
        cmd,
        cwd=cwd,
        capture_output=True,
        text=True,
        timeout=timeout,
        shell=False
    )
    return p.returncode, p.stdout, p.stderr

def tool_ok(tool, *args):
    try:
        rc, out, err = run([tool, *args], timeout=8)
        return rc == 0 or out or err  # prints a version is fine
    except Exception:
        return False

@app.post("/compile")
def compile_code():
    code = request.data.decode("utf-8", errors="replace")
    if not code.strip():
        return jsonify({"ok": False, "phase": "client", "stdout": "", 
                        "stderr": "Empty source received.", "exitCode": -1}), 200

    # Resolve tool names per OS
    kotlinc_bin = which("kotlinc.bat") if os.name == "nt" else which("kotlinc")
    java_bin    = which("java")

    # Pre-flight checks (clear error if tools missing)
    if not tool_ok(kotlinc_bin, "-version"):
        return jsonify({"ok": False, "phase": "client", "stdout": "", 
                        "stderr": "kotlinc not available. Install Kotlin compiler and add it to PATH.",
                        "exitCode": -1}), 200
    if not tool_ok(java_bin, "-version"):
        return jsonify({"ok": False, "phase": "client", "stdout": "", 
                        "stderr": "java not available. Install JDK 17+ and add it to PATH.",
                        "exitCode": -1}), 200

    try:
        with tempfile.TemporaryDirectory() as tmp:
            src_path = os.path.join(tmp, "Main.kt")
            jar_path = os.path.join(tmp, "app.jar")

            with open(src_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(code)

            # Compile to fat-jar (bundles Kotlin stdlib)
            comp_rc, comp_out, comp_err = run(
                [kotlinc_bin, src_path, "-include-runtime", "-d", jar_path, "-jvm-target", "1.8"],
                timeout=30
            )
            if comp_rc != 0:
                return jsonify({"ok": False, "phase": "compile", "stdout": comp_out,
                                "stderr": comp_err, "exitCode": comp_rc}), 200

            # Run the jar (limit runtime so infinite loops don’t hang)
            run_rc, run_out, run_err = run([java_bin, "-jar", jar_path], timeout=15)
            return jsonify({"ok": run_rc == 0, "phase": "run", "stdout": run_out,
                            "stderr": run_err, "exitCode": run_rc}), 200

    except subprocess.TimeoutExpired as te:
        return jsonify({"ok": False, "phase": "server", "stdout": "",
                        "stderr": f"TimeoutExpired: {te}", "exitCode": -1}), 200
    except Exception as e:
        return jsonify({"ok": False, "phase": "server", "stdout": "",
                        "stderr": f"{type(e).__name__}: {e}", "exitCode": -1}), 200

if __name__ == "__main__":
    # USB + adb reverse: keep localhost binding
    # (No firewall hassle; it tunnels via adb.)
    app.run(host="127.0.0.1", port=5000, debug=False, use_reloader=False)
