const esbuild = require("esbuild");
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const isProduction = process.argv.includes("--production");
const isWatch = process.argv.includes("--watch");

const REPO_ROOT = path.resolve(__dirname, "..", "..");
const JAR_NAME = "tools.vitruv.dsls.reactions.ide.jar";
const JAR_SRC = path.join(REPO_ROOT, "reactions", "ide", "target", JAR_NAME);
const JAR_DEST = path.join(__dirname, JAR_NAME);

function buildAndCopyLspJar() {
	const mvn = path.join(REPO_ROOT, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
	console.log(`[lsp-jar] Building ${JAR_NAME} via Maven...`);
	const result = spawnSync(
		mvn,
		["-pl", "reactions/ide", "-am", "package", "-DskipTests", "-q"],
		{ cwd: REPO_ROOT, stdio: "inherit", shell: true }
	);
	if (result.status !== 0) {
		throw new Error(`Maven build failed (exit code ${result.status})`);
	}
	if (!fs.existsSync(JAR_SRC)) {
		throw new Error(`Expected JAR not found at ${JAR_SRC}`);
	}
	fs.copyFileSync(JAR_SRC, JAR_DEST);
	console.log(`[lsp-jar] Copied to ${path.relative(REPO_ROOT, JAR_DEST)}`);
}

async function main() {
	buildAndCopyLspJar();

	const ctx = await esbuild.context({
		entryPoints: ["src/extension.ts"],
		bundle: true,
		format: "cjs",
		minify: isProduction,
		sourcemap: !isProduction,
		sourcesContent: false,
		platform: "node",
		outfile: "dist/extension.js",
		external: ["vscode"],
	});
	if (isWatch) {
		await ctx.watch();
	} else {
		await ctx.rebuild();
		await ctx.dispose();
	}
}

main().catch((e) => {
	console.error(e);
	process.exit(1);
});
