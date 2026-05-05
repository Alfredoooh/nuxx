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

    val templates: Map<String, Pair<String, Map<String, String>>> = mapOf(
        "blank" to Pair("Em branco", mapOf(
            "README.md" to "# Novo Projeto\n\nDescrição do projeto.\n"
        )),
        "html" to Pair("HTML / CSS / JS", mapOf(
            "index.html" to "<!DOCTYPE html>\n<html lang=\"pt\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>Documento</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n\n<h1>Olá, Mundo!</h1>\n\n<script src=\"script.js\"></script>\n</body>\n</html>\n",
            "style.css" to "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}\nbody{font-family:sans-serif;background:#fff;color:#333;padding:24px;}\nh1{font-size:2rem;color:#0e7af0;}\n",
            "script.js" to "// JavaScript\nconsole.log('Olá, mundo!');\n"
        )),
        "flutter" to Pair("Flutter", mapOf(
            "pubspec.yaml" to "name: my_app\ndescription: A new Flutter project.\nversion: 1.0.0+1\nenvironment:\n  sdk: \">=3.0.0 <4.0.0\"\ndependencies:\n  flutter:\n    sdk: flutter\nflutter:\n  uses-material-design: true\n",
            "lib/main.dart" to "import 'package:flutter/material.dart';\n\nvoid main() => runApp(const MyApp());\n\nclass MyApp extends StatelessWidget {\n  const MyApp({super.key});\n  @override\n  Widget build(BuildContext context) {\n    return MaterialApp(\n      title: 'Flutter Demo',\n      theme: ThemeData(colorSchemeSeed: Colors.blue),\n      home: const MyHomePage(),\n    );\n  }\n}\n\nclass MyHomePage extends StatelessWidget {\n  const MyHomePage({super.key});\n  @override\n  Widget build(BuildContext context) {\n    return Scaffold(\n      appBar: AppBar(title: const Text('Home')),\n      body: const Center(child: Text('Olá, Flutter!')),\n    );\n  }\n}\n"
        )),
        "node" to Pair("Node.js", mapOf(
            "package.json" to "{\n  \"name\": \"my-app\",\n  \"version\": \"1.0.0\",\n  \"main\": \"index.js\",\n  \"scripts\": { \"start\": \"node index.js\" }\n}\n",
            "index.js" to "const http = require('http');\n\nconst server = http.createServer((req, res) => {\n  res.writeHead(200, {'Content-Type': 'text/plain'});\n  res.end('Hello, World!\\n');\n});\n\nserver.listen(3000, () => console.log('http://localhost:3000'));\n",
            ".gitignore" to "node_modules/\n.env\n"
        )),
        "python" to Pair("Python", mapOf(
            "main.py" to "def main():\n    print(\"Olá, Mundo!\")\n\nif __name__ == \"__main__\":\n    main()\n",
            "requirements.txt" to "# Dependências\n",
            ".gitignore" to "__pycache__/\n*.pyc\n.env\nvenv/\n"
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
            files = templateFiles.toMutableMap()
        )
    }
}