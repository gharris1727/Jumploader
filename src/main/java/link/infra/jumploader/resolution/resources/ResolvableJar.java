package link.infra.jumploader.resolution.resources;

import link.infra.jumploader.DownloadWorkerManager;
import link.infra.jumploader.Jumploader;
import link.infra.jumploader.launch.arguments.ParsedArguments;
import link.infra.jumploader.resolution.EnvironmentDiscoverer;
import link.infra.jumploader.resolution.InvalidHashException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

public abstract class ResolvableJar {
	protected transient final EnvironmentDiscoverer.JarStorageLocation jarStorage;
	public ResolvableJar(EnvironmentDiscoverer.JarStorageLocation jarStorage) {
		this.jarStorage = jarStorage;
	}

	protected static URL pathToURL(Path path) {
		try {
			return path.toUri().toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException("Failed to load JAR, malformed URL", e);
		}
	}

	protected static URI resolveMavenPath(URI baseURL, String mavenPath) {
		String[] mavenPathSplit = mavenPath.split(":");
		if (mavenPathSplit.length != 3 && mavenPathSplit.length != 4) {
			throw new RuntimeException("Invalid maven path: " + mavenPath);
		}
		String classifierPart = mavenPathSplit.length == 3 ? "" : "-" + mavenPathSplit[3];
		return baseURL.resolve(
			String.join("/", mavenPathSplit[0].split("\\.")) + "/" + // Group ID
			mavenPathSplit[1] + "/" + // Artifact ID
			mavenPathSplit[2] + "/" + // Version
			mavenPathSplit[1] + "-" + mavenPathSplit[2] + classifierPart + ".jar"
		);
	}

	protected static void downloadFile(DownloadWorkerManager.TaskStatus status, URL downloadURI, Path destPath, Function<InputStream, InputStream> bytesTransformer) throws IOException {
		Files.createDirectories(destPath.getParent());

		URLConnection conn = downloadURI.openConnection();
		conn.setRequestProperty("User-Agent", Jumploader.USER_AGENT);
		conn.setRequestProperty("Accept", "application/octet-stream");

		int contentLength = conn.getContentLength();
		Path destPathTemp = destPath.resolveSibling(destPath.getFileName() + ".tmp");
		try (InputStream res = bytesTransformer.apply(conn.getInputStream());
			BytesReportingInputStream bris = new BytesReportingInputStream(res, status, contentLength)) {
			Files.copy(bris, destPathTemp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(destPathTemp, destPath);
		} catch (InvalidHashException e) {
			Files.deleteIfExists(destPath);
			Files.deleteIfExists(destPathTemp);
			throw e;
		}
	}

	public abstract URL resolveLocal() throws FileNotFoundException;
	public abstract URL resolveRemote(DownloadWorkerManager.TaskStatus status, ParsedArguments args) throws Exception;

	public abstract String toHumanString();
}