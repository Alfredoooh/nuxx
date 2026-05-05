// ProjectManager.kt
package com.xcode.app.editor

import android.content.Context
import org.json.JSONObject

data class LocalProject(
    val name: String,
    val type: String,
    val files: MutableMap<String, String>
)

object ProjectManager {

    private const val PREF_KEY = "nx_local_project"

    val templates: LinkedHashMap<String, Pair<String, Map<String, String>>> = linkedMapOf(
        "blank" to Pair("Em branco", mapOf(
            "README.md" to "# \${name}\n\nDescrição do projeto.\n"
        )),
        "html" to Pair("HTML / CSS / JS", mapOf(
            "index.html" to "<!DOCTYPE html>\n<html lang=\"pt\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>Documento</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n\n<h1>Ola, Mundo!</h1>\n\n<script src=\"script.js\"></script>\n</body>\n</html>\n",
            "style.css" to "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}\nbody{font-family:sans-serif;background:#fff;color:#333;padding:24px;}\nh1{font-size:2rem;color:#0e7af0;}\n",
            "script.js" to "'use strict';\n\nconsole.log('Ola, mundo!');\n"
        )),
        "flutter" to Pair("Flutter", mapOf(
            "pubspec.yaml" to "name: my_app\ndescription: A new Flutter project.\nversion: 1.0.0+1\nenvironment:\n  sdk: \">=3.0.0 <4.0.0\"\ndependencies:\n  flutter:\n    sdk: flutter\nflutter:\n  uses-material-design: true\n",
            "lib/main.dart" to "import 'package:flutter/material.dart';\n\nvoid main() => runApp(const MyApp());\n\nclass MyApp extends StatelessWidget {\n  const MyApp({super.key});\n  @override\n  Widget build(BuildContext context) {\n    return MaterialApp(\n      title: 'Flutter Demo',\n      theme: ThemeData(colorSchemeSeed: Colors.blue),\n      home: const MyHomePage(),\n    );\n  }\n}\n\nclass MyHomePage extends StatelessWidget {\n  const MyHomePage({super.key});\n  @override\n  Widget build(BuildContext context) {\n    return Scaffold(\n      appBar: AppBar(title: const Text('Home')),\n      body: const Center(child: Text('Ola, Flutter!')),\n    );\n  }\n}\n",
            ".gitignore" to ".dart_tool/\nbuild/\n*.g.dart\n"
        )),
        "node" to Pair("Node.js", mapOf(
            "package.json" to "{\n  \"name\": \"my-app\",\n  \"version\": \"1.0.0\",\n  \"description\": \"\",\n  \"main\": \"index.js\",\n  \"scripts\": {\n    \"start\": \"node index.js\",\n    \"dev\": \"nodemon index.js\"\n  }\n}\n",
            "index.js" to "const http = require('http');\n\nconst PORT = process.env.PORT || 3000;\n\nconst server = http.createServer((req, res) => {\n  res.writeHead(200, { 'Content-Type': 'text/plain' });\n  res.end('Hello, World!\\n');\n});\n\nserver.listen(PORT, () => {\n  console.log(`Servidor em http://localhost:\${PORT}`);\n});\n",
            ".gitignore" to "node_modules/\n.env\ndist/\n"
        )),
        "python" to Pair("Python", mapOf(
            "main.py" to "def main():\n    print(\"Ola, Mundo!\")\n\n\nif __name__ == \"__main__\":\n    main()\n",
            "requirements.txt" to "# Dependencias\n",
            ".gitignore" to "__pycache__/\n*.pyc\n*.pyo\n.env\nvenv/\n.venv/\ndist/\nbuild/\n"
        )),
        "react" to Pair("React (Vite)", mapOf(
            "index.html" to "<!DOCTYPE html>\n<html lang=\"pt\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>React App</title>\n</head>\n<body>\n<div id=\"root\"></div>\n<script type=\"module\" src=\"/src/main.jsx\"></script>\n</body>\n</html>\n",
            "src/main.jsx" to "import React from 'react'\nimport { createRoot } from 'react-dom/client'\nimport App from './App.jsx'\nimport './index.css'\n\ncreateRoot(document.getElementById('root')).render(\n  <React.StrictMode>\n    <App />\n  </React.StrictMode>\n)\n",
            "src/App.jsx" to "export default function App() {\n  return (\n    <div>\n      <h1>Hello, World!</h1>\n    </div>\n  )\n}\n",
            "src/index.css" to "* { box-sizing: border-box; margin: 0; padding: 0; }\nbody { font-family: sans-serif; }\n",
            "package.json" to "{\n  \"name\": \"react-app\",\n  \"version\": \"0.0.0\",\n  \"scripts\": {\n    \"dev\": \"vite\",\n    \"build\": \"vite build\"\n  },\n  \"dependencies\": {\n    \"react\": \"^18.0.0\",\n    \"react-dom\": \"^18.0.0\"\n  },\n  \"devDependencies\": {\n    \"vite\": \"^5.0.0\",\n    \"@vitejs/plugin-react\": \"^4.0.0\"\n  }\n}\n"
        ))
    )

    fun save(ctx: Context, project: LocalProject) {
        val filesJson = JSONObject()
        project.files.forEach { (k, v) -> filesJson.put(k, v) }
        val json = JSONObject().apply {
            put("name", project.name)
            put("type", project.type)
            put("files", filesJson)
        }
        ctx.getSharedPreferences("xcode", Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, json.toString()).apply()
    }

    fun load(ctx: Context): LocalProject? {
        val raw = ctx.getSharedPreferences("xcode", Context.MODE_PRIVATE)
            .getString(PREF_KEY, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val filesJson = json.getJSONObject("files")
            val files = mutableMapOf<String, String>()
            filesJson.keys().forEach { k -> files[k] = filesJson.getString(k) }
            LocalProject(
                name = json.getString("name"),
                type = json.getString("type"),
                files = files
            )
        } catch (e: Exception) { null }
    }

    fun createFromTemplate(name: String, type: String): LocalProject {
        val (_, templateFiles) = templates[type] ?: templates["blank"]!!
        return LocalProject(
            name = name,
            type = type,
            files = templateFiles.mapValues { (_, v) ->
                v.replace("\${name}", name)
            }.toMutableMap()
        )
    }
}