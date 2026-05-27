/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as path from "path";

import { ExtensionContext, workspace } from "vscode";
import {
	LanguageClient,
	LanguageClientOptions,
	ServerOptions,
	TransportKind,
} from "vscode-languageclient/node";

const LSP_JAR = "tools.vitruv.dsls.reactions.ide.jar";
// kept in sync with the shade plugin's <mainClass> in reactions/ide/pom.xml
const LSP_MAIN_CLASS = "tools.vitruv.dsls.reactions.ide.ReactionsServerLauncher";

function buildJavaArgs(extraJars: string[]): string[] {
	if (extraJars.length === 0) {
		return ["-jar", LSP_JAR, "-log", "-trace"];
	}
	const classpath = [LSP_JAR, ...extraJars].join(path.delimiter);
	return ["-cp", classpath, LSP_MAIN_CLASS, "-log", "-trace"];
}

export async function activate(context: ExtensionContext) {
	const additionalJars = workspace
		.getConfiguration("reactions")
		.get<string[]>("additionalMetamodelJars", []);

	const xtextServerOptions: ServerOptions = {
		command: "java",
		transport: TransportKind.stdio,
		args: buildJavaArgs(additionalJars),
		options: {
			cwd: context.extensionPath,
		},
	};

	const clientOptions: LanguageClientOptions = {
		documentSelector: [{ scheme: "file", language: "reaction" }],
	};

	const client = new LanguageClient(
		"reactions-lsp",
		"Reactions Language Server",
		xtextServerOptions,
		clientOptions
	);

	await client.start();
	context.subscriptions.push(client);

	return client;
}
