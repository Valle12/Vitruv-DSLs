package tools.vitruv.dsls.reactions.ide

import com.google.inject.AbstractModule
import com.google.inject.util.Modules
import org.eclipse.xtext.ide.server.ServerLauncher
import org.eclipse.xtext.ide.server.ServerModule
import org.eclipse.xtext.util.IFileSystemScanner

/**
 * Entry point for the reactions language server.
 *
 * Binds the default scanner to our custom one
 *
 * Binding the scanner in {@link ReactionsLanguageIdeModule} is not sufficient because
 * the workspace scan uses the server-level injector, not the per-language one.
 */
class ReactionsServerLauncher {
	/**
	 * Starts the language server with the file system scanner that skips build output.
	 *
	 * @param args the arguments the Xtext server launcher understands
	 */
	def static void main(String[] args) {
		val overridden = Modules.override(new ServerModule()).with(new AbstractModule() {
			override configure() {
				bind(IFileSystemScanner).to(BuildOutputSkippingFileSystemScanner)
			}
		})
		ServerLauncher.launch("Reactions LSP", args, overridden)
	}
}
