import * as vscode from "vscode";
import { ExtensionContext } from "vscode";

import * as lsp from "./lsp";

export async function activate(context: ExtensionContext) {
	try {
		await lsp.activate(context);
	} catch (e) {
		const msg = e instanceof Error ? `${e.message}\n${e.stack ?? ""}` : String(e);
		const out = vscode.window.createOutputChannel("Reactions");
		context.subscriptions.push(out);
		out.appendLine("[reactions] language client failed to start:");
		out.appendLine(msg);
		out.show(true);
		vscode.window.showErrorMessage(
			`Reactions language server failed to start. See Output → "Reactions" for details.`
		);
	}
}

export function deactivate() {}
