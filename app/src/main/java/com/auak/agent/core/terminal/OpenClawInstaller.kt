package com.auak.agent.core.terminal

import android.content.Context
import android.util.Log
import com.termux.shared.termux.TermuxConstants
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OpenClawInstaller(private val context: Context) {

    enum class State {
        NOT_CHECKED,
        CHECKING,
        NOT_INSTALLED,
        INSTALLING_NODE,
        INSTALLING_OPENCLAW,
        CONFIGURING,
        READY,
        RUNNING,
        ERROR
    }

    private val _state = MutableStateFlow(State.NOT_CHECKED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    private val home = TermuxConstants.TERMUX_HOME_DIR_PATH

    fun markReady() {
        _state.value = State.READY
        _statusMessage.value = "OpenClaw installed successfully!"
        Log.i(TAG, "markReady: state set to READY")
    }

    fun isNodeInstalled(): Boolean {
        val exists = File("$prefix/bin/node").exists()
        Log.d(TAG, "isNodeInstalled: $exists")
        return exists
    }

    fun isOpenClawInstalled(): Boolean {
        val entryFile = File("$prefix/lib/node_modules/openclaw/openclaw.mjs")
        val moduleDir = File("$prefix/lib/node_modules/openclaw/node_modules")
        val installed = entryFile.exists() && moduleDir.exists()
        Log.d(TAG, "isOpenClawInstalled: $installed (entry=${entryFile.exists()}, modules=${moduleDir.exists()})")
        return installed
    }

    fun isGatewayRunning(): Boolean {
        return try {
            val envp = arrayOf("LD_LIBRARY_PATH=$prefix/lib", "PATH=$prefix/bin:/system/bin")
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/sh", "-c",
                    "export LD_LIBRARY_PATH='$prefix/lib'; " +
                    "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18789/health 2>/dev/null"),
                envp
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val running = output == "200"
            Log.d(TAG, "isGatewayRunning: $running (http=$output)")
            running
        } catch (e: Exception) {
            Log.d(TAG, "isGatewayRunning: false (${e.message})")
            false
        }
    }

    suspend fun checkStatus() {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "checkStatus: start")
            _state.value = State.CHECKING
            _statusMessage.value = "Checking OpenClaw status..."

            if (!AuakClawTermuxInstaller.isInstalled()) {
                Log.i(TAG, "checkStatus: bootstrap not installed")
                _state.value = State.NOT_INSTALLED
                _statusMessage.value = "Bootstrap not installed"
                return@withContext
            }

            val gatewayRunning = isGatewayRunning()
            val openClawInstalled = isOpenClawInstalled()

            if (gatewayRunning && openClawInstalled) {
                Log.i(TAG, "checkStatus: gateway running")
                _state.value = State.RUNNING
                _statusMessage.value = "OpenClaw Gateway is running"
                return@withContext
            }

            if (gatewayRunning && !openClawInstalled) {
                Log.w(TAG, "checkStatus: stale gateway from previous install detected, files missing")
                _state.value = State.NOT_INSTALLED
                _statusMessage.value = "OpenClaw not installed (stale gateway on port)"
                return@withContext
            }

            if (openClawInstalled) {
                Log.i(TAG, "checkStatus: OpenClaw installed, gateway not running")
                _state.value = State.READY
                _statusMessage.value = "OpenClaw installed, gateway not running"
                return@withContext
            }

            Log.i(TAG, "checkStatus: OpenClaw not installed")
            _state.value = State.NOT_INSTALLED
            _statusMessage.value = "OpenClaw not installed"
        }
    }

    fun getInstallScript(): String {
        return buildString {
            appendLine("#!/system/bin/sh")

            appendLine("# AUAK OpenClaw offline installer")
            appendLine()

            appendLine("export PREFIX='$prefix'")
            appendLine("export HOME='$home'")
            appendLine("export PATH=\"\$PREFIX/bin:\$PREFIX/bin/applets:/system/bin\"")
            appendLine("export LD_LIBRARY_PATH=\"\$PREFIX/lib:\$PREFIX/libexec:\$LD_LIBRARY_PATH\"")
            appendLine("export TMPDIR=\"\$PREFIX/tmp\"")
            appendLine("export LANG='en_US.UTF-8'")
            appendLine("export SHARP_IGNORE_GLOBAL_LIBVIPS=1")
            appendLine()

            // ✅ 关键：dpkg 参数统一
            appendLine("DPKG_OPTS=\"--root=\$PREFIX --admindir=\$PREFIX/var/lib/dpkg --instdir=\$PREFIX --force-not-root --force-bad-path\"")
            appendLine()

            // ✅ 修复权限 + 初始化
            appendLine("mkdir -p \$PREFIX/var/lib/dpkg")
            appendLine("chmod -R 700 \$PREFIX")
            appendLine("touch \$PREFIX/var/lib/dpkg/status")
            appendLine()

            appendLine("echo '=== AUAK OpenClaw Installer (offline) ==='")
            appendLine("echo ''")

            // =====================
            appendLine("echo '>>> [1/6] Fixing broken deps...'")
            appendLine("dpkg \$DPKG_OPTS --remove --force-depends dpkg-scanpackages dpkg-perl 2>&1 || true")
            appendLine()

            // =====================
            appendLine("echo '>>> [2/6] Installing Node.js & tools from bundled packages...'")
            appendLine("DEBS_DIR=\$HOME/.auak agenty/debs")

            fun dpkgInstall(pkg: String) {
                appendLine("dpkg \$DPKG_OPTS -i \$DEBS_DIR/$pkg 2>&1")
            }

            dpkgInstall("c-ares_*.deb")
            dpkgInstall("libsqlite_*.deb")
            //以防没有ar命令
            appendLine("if ! command -v ar >/dev/null 2>&1; then")
            appendLine("  echo '[warn] ar not found, using busybox fallback'")
            appendLine("  alias ar='busybox ar'")
            appendLine("fi")

            appendLine("echo '  Installing Node.js (manual extract)...'")

            appendLine("NODE_DEB=\$(ls \$DEBS_DIR/nodejs-lts_*.deb)")
            appendLine("TMP_NODE=\$PREFIX/tmp/nodejs")
            appendLine("rm -rf \$TMP_NODE && mkdir -p \$TMP_NODE")

            appendLine("cd \$TMP_NODE")

            // 解 deb
            appendLine("ar x \$NODE_DEB")

            // 解 data
            appendLine("tar xf data.tar.*")

            // 拷贝文件（兼容两种结构）
            appendLine("if [ -d ./data ]; then cp -r ./data/* \$PREFIX/; else cp -r ./* \$PREFIX/; fi")

            appendLine("cd /")

            appendLine("echo \"  node version: \$(\$PREFIX/bin/node --version 2>&1)\"")

            dpkgInstall("npm_*.deb")
            appendLine("echo \"  node version: \$(\$PREFIX/bin/node --version 2>&1)\"")

            appendLine("echo '  Installing git...'")
            dpkgInstall("git_*.deb")
            appendLine("echo \"  git version: \$(\$PREFIX/bin/git --version 2>&1)\"")

            appendLine("echo '  Installing build tools (cmake, clang)...'")
            dpkgInstall("libarchive_*.deb")
            dpkgInstall("jsoncpp_*.deb")
            dpkgInstall("libuv_*.deb")
            dpkgInstall("rhash_*.deb")
            dpkgInstall("cmake_*.deb")
            appendLine("echo \"  cmake version: \$(\$PREFIX/bin/cmake --version 2>&1 | head -1)\"")

            dpkgInstall("libllvm_*.deb")
            dpkgInstall("ndk-sysroot_*.deb")
            dpkgInstall("libcompiler-rt_*.deb")
            dpkgInstall("lld_*.deb")
            dpkgInstall("llvm_*.deb")
            dpkgInstall("clang_*.deb")
            appendLine("echo \"  clang version: \$(\$PREFIX/bin/clang --version 2>&1 | head -1)\"")

            appendLine()

            // =====================
            appendLine("echo '>>> [3/6] Setting up Bionic compatibility...'")
            appendLine("cat > \$HOME/bionic-compat.js << 'COMPATEOF'")
            appendLine("const os = require('os');")
            appendLine("const origNet = os.networkInterfaces;")
            appendLine("os.networkInterfaces = function() {")
            appendLine("  try { const r = origNet.call(os); if (r && Object.keys(r).length > 0) return r; } catch(e) {}")
            appendLine("  return { lo: [{ address: '127.0.0.1', netmask: '255.0.0.0', family: 'IPv4', internal: true }] };")
            appendLine("};")
            appendLine("COMPATEOF")
            appendLine()

            // =====================
            appendLine("echo '>>> [4/6] Extracting OpenClaw from bundled archive...'")
            appendLine("OPENCLAW_ARCHIVE=\$HOME/.auak agenty/openclaw-node-modules.tar.xz")
            appendLine("OPENCLAW_DIR=\$PREFIX/lib/node_modules/openclaw")
            appendLine("rm -rf \$OPENCLAW_DIR 2>/dev/null")
            appendLine("mkdir -p \$OPENCLAW_DIR")
            appendLine("tar xf \$OPENCLAW_ARCHIVE -C \$OPENCLAW_DIR 2>&1 || { echo '[error] Failed to extract openclaw archive'; exit 1; }")
            appendLine()

            // =====================
            appendLine("echo '>>> [5/6] Configuring AUAK integration...'")
            appendLine("mkdir -p \$PREFIX/bin")

            appendLine("cat > \$PREFIX/bin/openclaw << 'OPENCLAWEOF'")
            appendLine("#!/system/bin/sh")
            appendLine("export PREFIX='$prefix'")
            appendLine("export HOME='$home'")
            appendLine("export LD_LIBRARY_PATH=\"\$PREFIX/lib\"")
            appendLine("export PATH=\"\$PREFIX/bin:/system/bin\"")
            appendLine("export NODE_OPTIONS=\"-r \$HOME/bionic-compat.js\"")
            appendLine("exec \"\$PREFIX/bin/node\" \"\$PREFIX/lib/node_modules/openclaw/openclaw.mjs\" \"\$@\"")
            appendLine("OPENCLAWEOF")
            appendLine("chmod 755 \$PREFIX/bin/openclaw")

            appendLine()

            // =====================
            appendLine("echo '>>> [6/6] Done'")
            appendLine("echo 'AUAK_OPENCLAW_SETUP_COMPLETE'")
        }
    }
    fun getStartGatewayScript(): String {
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("export PREFIX='$prefix'")
            appendLine("export HOME='$home'")
            appendLine("export LD_LIBRARY_PATH='$prefix/lib'")
            appendLine("export PATH='$prefix/bin:$prefix/bin/applets:/system/bin'")
            appendLine("export NODE_OPTIONS=\"-r \$HOME/bionic-compat.js\"")
            appendLine("export SHARP_IGNORE_GLOBAL_LIBVIPS=1")
            appendLine("echo 'Starting OpenClaw Gateway on :18789...'")
            appendLine("\$PREFIX/bin/node \$PREFIX/lib/node_modules/openclaw/openclaw.mjs gateway --allow-unconfigured --port 18789 --verbose &")
            appendLine("echo 'AUAK_GATEWAY_STARTED'")
        }
    }

    suspend fun runInstallInSession(session: TerminalSession) {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "runInstallInSession: starting OpenClaw install")
                _state.value = State.INSTALLING_NODE
                _statusMessage.value = "Installing Node.js and OpenClaw..."
                _errorMessage.value = null

                val script = getInstallScript()
                Log.d(TAG, "runInstallInSession: writing setup script (${script.length} chars)")
                writeAndExecuteScript(script, "setup_openclaw.sh")

                val cmd = "/system/bin/sh $home/.auak agenty/setup_openclaw.sh\n"
                Log.i(TAG, "runInstallInSession: sending command to session: ${cmd.trim()}")
                session.write(cmd)
            } catch (e: Exception) {
                _state.value = State.ERROR
                _errorMessage.value = e.message
                Log.e(TAG, "runInstallInSession: FAILED", e)
            }
        }
    }

    fun onInstallOutputLine(line: String) {
        Log.d(TAG, "onInstallOutputLine: $line")
        when {
            line.contains("[1/6]") -> {
                _state.value = State.INSTALLING_NODE
                _statusMessage.value = "Fixing broken deps..."
                Log.i(TAG, "Install step 1/6: fixing broken deps")
            }
            line.contains("[2/6]") -> {
                _state.value = State.INSTALLING_NODE
                _statusMessage.value = "Installing Node.js & tools..."
                Log.i(TAG, "Install step 2/6: installing Node.js & tools")
            }
            line.contains("[3/6]") -> {
                _state.value = State.INSTALLING_OPENCLAW
                _statusMessage.value = "Setting up Bionic compatibility..."
                Log.i(TAG, "Install step 3/6: bionic compat")
            }
            line.contains("[4/6]") -> {
                _state.value = State.INSTALLING_OPENCLAW
                _statusMessage.value = "Extracting OpenClaw (offline)..."
                Log.i(TAG, "Install step 4/6: extracting openclaw archive")
            }
            line.contains("[5/6]") -> {
                _state.value = State.CONFIGURING
                _statusMessage.value = "Configuring AUAK integration..."
                Log.i(TAG, "Install step 5/6: configuring")
            }
            line.contains("[6/6]") -> {
                _state.value = State.CONFIGURING
                _statusMessage.value = "Deploying AuakClaw workspace..."
                Log.i(TAG, "Install step 6/6: deploying workspace")
            }
            line.contains("AUAK_OPENCLAW_SETUP_COMPLETE") -> {
                _state.value = State.READY
                _statusMessage.value = "OpenClaw installed successfully!"
                Log.i(TAG, "Install COMPLETE!")
            }
            line.contains("[error]") -> {
                _state.value = State.ERROR
                _errorMessage.value = line
                Log.e(TAG, "Install ERROR: $line")
            }
        }
    }

    suspend fun startGateway(session: TerminalSession) {
        withContext(Dispatchers.IO) {
            val script = getStartGatewayScript()
            writeAndExecuteScript(script, "start_gateway.sh")
            session.write("/system/bin/sh $home/.auak agenty/start_gateway.sh\n")

            // Wait and verify
            delay(3000)
            if (isGatewayRunning()) {
                _state.value = State.RUNNING
                _statusMessage.value = "OpenClaw Gateway is running on :18789"
            }
        }
    }

    fun copyOpenClawArchiveFromAssets() {
        val destDir = File("$home/.auak agenty")
        destDir.mkdirs()
        val destFile = File(destDir, "openclaw-node-modules.tar.xz")
        if (destFile.exists()) {
            Log.i(TAG, "copyOpenClawArchiveFromAssets: archive already exists (${destFile.length()} bytes), skipping")
            return
        }
        try {
            context.assets.open("openclaw-node-modules.tar.xz").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "copyOpenClawArchiveFromAssets: copied to ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "copyOpenClawArchiveFromAssets: FAILED", e)
        }
    }

    fun copyMcpServerFromAssets() {
        val destDir = File("$home/.auak agenty")
        destDir.mkdirs()
        val destFile = File(destDir, "mcp-server.js")
        try {
            context.assets.open("auak agenty-mcp-server.js").use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setExecutable(true)
            Log.i(TAG, "copyMcpServerFromAssets: copied to ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "copyMcpServerFromAssets: FAILED", e)
        }
    }

    // Node Host disabled — reverted to MCP/curl mode for better upgrade compatibility
    // fun copyNodeHostFromAssets() {
    //     val destDir = File("$home/.auak agenty")
    //     destDir.mkdirs()
    //     val destFile = File(destDir, "node-host.js")
    //     try {
    //         context.assets.open("auak agenty-node-host.js").use { input ->
    //             destFile.outputStream().use { output ->
    //                 input.copyTo(output)
    //             }
    //         }
    //         destFile.setExecutable(true)
    //         Log.i(TAG, "copyNodeHostFromAssets: copied to ${destFile.absolutePath} (${destFile.length()} bytes)")
    //     } catch (e: Exception) {
    //         Log.e(TAG, "copyNodeHostFromAssets: FAILED", e)
    //     }
    // }

    fun copyDebsFromAssets() {
        val destDir = File("$home/.auak agenty/debs")
        destDir.mkdirs()
        try {
            val debFiles = context.assets.list("debs")?.toList() ?: emptyList()
            Log.i(TAG, "copyDebsFromAssets: found ${debFiles.size} deb files in assets")
            for (debName in debFiles) {
                if (!debName.endsWith(".deb")) continue
                try {
                    val destFile = File(destDir, debName)
                    context.assets.open("debs/$debName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "copyDebsFromAssets: copied $debName (${destFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "copyDebsFromAssets: FAILED for $debName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyDebsFromAssets: FAILED to list debs directory", e)
        }
    }

    fun copyWorkspaceFilesFromAssets() {
        val workspaceDir = File("$home/.openclaw/workspace")
        workspaceDir.mkdirs()
        try {
            copyAssetDir("auak agenty-workspace", workspaceDir, overwriteSkills = true)
            Log.i(TAG, "copyWorkspaceFilesFromAssets: done")
        } catch (e: Exception) {
            Log.e(TAG, "copyWorkspaceFilesFromAssets: FAILED", e)
        }
    }

    private fun copyAssetDir(assetPath: String, destDir: File, overwriteSkills: Boolean) {
        val entries = context.assets.list(assetPath) ?: return
        destDir.mkdirs()
        for (entry in entries) {
            val srcPath = "$assetPath/$entry"
            val destFile = File(destDir, entry)
            val subEntries = context.assets.list(srcPath)
            if (subEntries != null && subEntries.isNotEmpty()) {
                copyAssetDir(srcPath, destFile, overwriteSkills)
            } else {
                val isSkill = srcPath.contains("/skills/")
                val isauak agentyTemplate = entry.endsWith(".md") && !srcPath.contains("/memory/")
                val shouldWrite = !destFile.exists() || isSkill && overwriteSkills || isauak agentyTemplate
                if (shouldWrite) {
                    try {
                        context.assets.open(srcPath).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "copyAssetDir: copied $srcPath -> ${destFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "copyAssetDir: FAILED for $srcPath", e)
                    }
                } else {
                    Log.d(TAG, "copyAssetDir: skipped $srcPath (already exists)")
                }
            }
        }
    }

    fun getGatewayToken(): String? {
        val configFile = File("$home/.openclaw/openclaw.json")
        if (!configFile.exists()) return null
        return try {
            val json = JSONObject(configFile.readText())
            json.optJSONObject("gateway")?.optJSONObject("auth")?.optString("token", null)
        } catch (_: Exception) {
            null
        }
    }

    fun isModelConfigured(): Boolean {
        val configFile = File("$home/.openclaw/openclaw.json")
        if (!configFile.exists()) return false
        return try {
            val json = JSONObject(configFile.readText())
            val providers = json.optJSONObject("models")?.optJSONObject("providers")
            providers != null && providers.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    fun mergeOpenClawConfig(
        apiKey: String,
        baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3",
        modelId: String = "doubao-seed-2-0-pro-260215"
    ) {
        try {
            val configFile = File("$home/.openclaw/openclaw.json")
            val json = if (configFile.exists()) {
                try { JSONObject(configFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }
            configFile.parentFile?.mkdirs()

            mergeBaseConfig(json)

            val defaults = json.getOrPut("agents").getOrPut("defaults")
            defaults.put("workspace", "$home/.openclaw/workspace")
            defaults.remove("mcp")
            defaults.getOrPut("model").put("primary", "volcengine/$modelId")

            val modelsRoot = json.getOrPut("models")
            modelsRoot.put("mode", "merge")
            modelsRoot.remove("default")
            val providers = modelsRoot.getOrPut("providers")
            providers.remove("doubao")

            val volcModels = JSONArray()

            volcModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-pro-260215")
                put("name", "豆包Seed 2.0 Pro")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 256000)
                put("maxTokens", 128000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            volcModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-lite-260215")
                put("name", "豆包Seed 2.0 Lite")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 128000)
                put("maxTokens", 30000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            volcModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-mini-260215")
                put("name", "豆包Seed 2.0 Mini")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 256000)
                put("maxTokens", 30000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            providers.put("volcengine", JSONObject().apply {
                put("baseUrl", baseUrl.trimEnd('/'))
                put("apiKey", apiKey)
                put("api", "openai-completions")
                put("models", volcModels)
            })

            configFile.writeText(json.toString(2))
            Log.i(TAG, "mergeOpenClawConfig: merged volcengine provider (3 models, primary=$modelId)")
        } catch (e: Exception) {
            Log.e(TAG, "mergeOpenClawConfig: FAILED", e)
        }
    }

    fun mergeagentyAiConfig(accessToken: String) {
        try {
            val configFile = File("$home/.openclaw/openclaw.json")
            val json = if (configFile.exists()) {
                try { JSONObject(configFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }
            configFile.parentFile?.mkdirs()

            mergeBaseConfig(json)

            val defaults = json.getOrPut("agents").getOrPut("defaults")
            defaults.put("workspace", "$home/.openclaw/workspace")
            defaults.remove("mcp")
            defaults.getOrPut("model").put("primary", "agentyai/doubao-seed-2-0-pro-260215")

            val modelsRoot = json.getOrPut("models")
            modelsRoot.put("mode", "merge")
            modelsRoot.remove("default")
            val providers = modelsRoot.getOrPut("providers")
            providers.remove("doubao")
            providers.remove("volcengine")

            val agentyModels = JSONArray()

            agentyModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-pro-260215")
                put("name", "豆包Seed 2.0 Pro")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 256000)
                put("maxTokens", 128000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            agentyModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-lite-260215")
                put("name", "豆包Seed 2.0 Lite")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 128000)
                put("maxTokens", 30000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            agentyModels.put(JSONObject().apply {
                put("id", "doubao-seed-2-0-mini-260215")
                put("name", "豆包Seed 2.0 Mini")
                put("reasoning", true)
                put("input", JSONArray().put("text").put("image"))
                put("contextWindow", 256000)
                put("maxTokens", 30000)
                put("cost", JSONObject()
                    .put("input", 0).put("output", 0)
                    .put("cacheRead", 0).put("cacheWrite", 0))
                put("compat", JSONObject().apply {
                    put("supportsReasoningEffort", true)
                    put("maxTokensField", "max_tokens")
                })
            })

            providers.put("agentyai", JSONObject().apply {
                put("baseUrl", "https://agentyai.me/v1")
                put("apiKey", accessToken)
                put("api", "openai-completions")
                put("models", agentyModels)
            })

            configFile.writeText(json.toString(2))
            Log.i(TAG, "mergeagentyAiConfig: merged agentyai provider (baseUrl=https://agentyai.me/v1)")
        } catch (e: Exception) {
            Log.e(TAG, "mergeagentyAiConfig: FAILED", e)
        }
    }

    fun mergeMinimalConfig() {
        try {
            val configFile = File("$home/.openclaw/openclaw.json")
            val json = if (configFile.exists()) {
                try { JSONObject(configFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }
            configFile.parentFile?.mkdirs()

            mergeBaseConfig(json)

            val defaults = json.getOrPut("agents").getOrPut("defaults")
            defaults.put("workspace", "$home/.openclaw/workspace")
            defaults.remove("mcp")

            configFile.writeText(json.toString(2))
            Log.i(TAG, "mergeMinimalConfig: merged base config (no model provider)")
        } catch (e: Exception) {
            Log.e(TAG, "mergeMinimalConfig: FAILED", e)
        }
    }

    private fun mergeBaseConfig(json: JSONObject) {
        json.remove("identity")
        json.remove("mcp")

        if (json.has("models")) {
            json.getJSONObject("models").remove("default")
        }

        val gateway = json.getOrPut("gateway")
        gateway.put("mode", "local")
        gateway.remove("host")
        gateway.put("port", 18789)
        gateway.getOrPut("controlUi").put("enabled", true).put("basePath", "/openclaw")

        // Node Host allowCommands disabled — reverted to MCP/curl mode
        // val nodes = gateway.getOrPut("nodes")
        // val allowCommands = JSONArray()
        // val auak agentyCommands = arrayOf(...)
        // nodes.put("allowCommands", allowCommands)
    }

    private fun JSONObject.getOrPut(key: String): JSONObject {
        if (!has(key)) put(key, JSONObject())
        return getJSONObject(key)
    }

    private fun writeAndExecuteScript(content: String, filename: String) {
        val scriptDir = File("$home/.auak agenty")
        scriptDir.mkdirs()
        val scriptFile = File(scriptDir, filename)
        scriptFile.writeText(content)
        scriptFile.setExecutable(true)
    }

    companion object {
        private const val TAG = "OpenClawInstaller"
    }
}
