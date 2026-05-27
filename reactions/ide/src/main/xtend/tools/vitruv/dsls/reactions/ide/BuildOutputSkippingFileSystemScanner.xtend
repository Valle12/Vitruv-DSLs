package tools.vitruv.dsls.reactions.ide

import com.google.inject.Singleton
import java.io.File
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.util.IAcceptor
import org.eclipse.xtext.util.IFileSystemScanner

/**
 * File-system scanner that skips common build-output and VCS folders when walking
 * the LSP's workspace. Without this filter the reactions LSP indexes both the
 * sources under {@code src/main/reactions/} and Maven's {@code target/classes/}
 * copies of the same files, which yields a {@code duplicate_type} error on every
 * synthetic JVM type the reactions inferrer produces.
 */
@Singleton
class BuildOutputSkippingFileSystemScanner extends IFileSystemScanner.JavaIoFileSystemScanner {

	static val SKIPPED_DIRECTORIES = #{
		"target",       // Maven
		"build",        // Gradle
		"bin",          // Eclipse output
		"out",          // IntelliJ output
		"node_modules", // npm
		".git",
		".gradle",
		".idea",
		".vscode",
		".settings"
	}

	override scanRec(File file, IAcceptor<URI> acceptor) {
		if (file.directory && SKIPPED_DIRECTORIES.contains(file.name)) {
			return
		}
		super.scanRec(file, acceptor)
	}

}
