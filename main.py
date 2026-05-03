from flask import Flask, jsonify, request
import os
import subprocess

app = Flask(__name__)

REPO_URL = "https://github.com/Alfredoooh/nuxx"
REPO_DIR = "/tmp/nuxx"

def clone_or_pull():
    if os.path.exists(REPO_DIR):
        subprocess.run(["git", "-C", REPO_DIR, "pull"], check=True)
    else:
        subprocess.run(["git", "clone", REPO_URL, REPO_DIR], check=True)

clone_or_pull()

@app.route("/files")
def list_files():
    result = []
    for root, dirs, files in os.walk(REPO_DIR):
        dirs[:] = [d for d in dirs if d != ".git"]
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, REPO_DIR)
            result.append(rel)
    return jsonify(result)

@app.route("/file")
def get_file():
    path = request.args.get("path")
    if not path:
        return jsonify({"error": "path required"}), 400
    full = os.path.join(REPO_DIR, path)
    if not os.path.exists(full):
        return jsonify({"error": "not found"}), 404
    with open(full, "r", errors="replace") as f:
        content = f.read()
    return jsonify({"path": path, "content": content})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=10000)