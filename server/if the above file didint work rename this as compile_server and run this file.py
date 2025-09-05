from flask import Flask, request, jsonify
import subprocess
import tempfile
import os
import shutil

app = Flask(__name__)

def which(exe):
    """Find executable in PATH or fallback."""
    return shutil.which(exe) or exe

def tool_ok(tool, *args):
    """Check if a tool is available."""
    try:
        rc = subprocess.run([tool, *args], capture_output=True, text=True, timeout=8)
        return rc.returncode == 0 or rc.stdout or rc.stderr
    except Exception:
        return False

@app.post("/compile")
def compile_code():
    code = request.data.decode("utf-8", errors="replace")
    if not code.strip():
        return jsonify({"ok": False, "phase": "client", "stdout": "",
                        "stderr": "Empty source received.", "exitCode": -1}), 200

    kotlinc_bin = which("kotlinc.bat") if os.name == "nt" else which("kotlinc")
    java_bin = which("java")

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

            # Include stdlib explicitly on Windows if it exists
            stdlib_path = os.path.join(os.path.dirname(kotlinc_bin), "..", "lib", "kotlin-stdlib.jar")
            cp_option = ["-cp", stdlib_path] if os.path.exists(stdlib_path) else []

            # Compile Kotlin
            comp = subprocess.run(
                [kotlinc_bin, src_path, "-include-runtime", "-d", jar_path, "-jvm-target", "1.8", *cp_option],
                capture_output=True, text=True, timeout=30
            )

            if comp.returncode != 0:
                return jsonify({"ok": False, "phase": "compile", "stdout": comp.stdout,
                                "stderr": comp.stderr, "exitCode": comp.returncode}), 200

            # Run jar using Popen to avoid stdout blocking
            process = subprocess.Popen([java_bin, "-jar", jar_path],
                                       stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE,
                                       text=True)

            try:
                stdout, stderr = process.communicate(timeout=60)  # allow 60s max runtime
                exit_code = process.returncode
            except subprocess.TimeoutExpired:
                process.kill()
                stdout, stderr = process.communicate()
                return jsonify({"ok": False, "phase": "run", "stdout": stdout,
                                "stderr": "TimeoutExpired: Program took too long", "exitCode": -1}), 200

            return jsonify({"ok": exit_code == 0, "phase": "run", "stdout": stdout,
                            "stderr": stderr, "exitCode": exit_code}), 200

    except Exception as e:
        return jsonify({"ok": False, "phase": "server", "stdout": "",
                        "stderr": f"{type(e).__name__}: {e}", "exitCode": -1}), 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False, use_reloader=False)


