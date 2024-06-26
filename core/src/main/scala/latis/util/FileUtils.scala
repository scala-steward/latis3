package latis.util

import java.net.URI
import java.net.URL
import java.nio.file.*

import scala.util.Properties

/**
 * Defines utility functions for working with files.
 */
object FileUtils {
  //TODO: rename, IOUtils? UriUtils? keep Path stuff here?

  /**
   * Optionally returns an absolute path for the given path.
   * If the given path is relative, this looks in the classpath
   * and current working directory.
   */
  def resolvePath(path: Path): Option[Path] =
    if (path.isAbsolute) {
      Some(path)
    } else
      getClass.getResource("/" + path) match {
        case url: URL =>
          url.getProtocol match {
            case "file" =>
              Some(Paths.get(url.toURI))
            case "jar" =>
              val Array(fsUri, file) = url.toString.split("!")
              val fs                 = getOrCreateFileSystem(URI.create(fsUri))
              Some(fs.getPath(file))
            case p =>
              val msg = s"Not able to resolve path for unknown protocol $p"
              throw new RuntimeException(msg)
          }
        case null =>
          // Not found in classpath, try the current working directory
          val fpath = Paths.get(Properties.userDir, path.toString)
          // Make sure it exists since this is our last attempt
          if (fpath.toFile.exists) Some(fpath)
          else None
      }

  def resolvePath(path: String): Option[Path] =
    resolvePath(Paths.get(path))

  /**
   * Provides a FileSystem for a given URI.
   *
   * Since using FileSystems.newFileSystem may throw a
   * FileSystemAlreadyExistsException, this makes sure
   * we create it only once.
   */
  private def getOrCreateFileSystem(uri: URI): FileSystem =
    try {
      FileSystems.getFileSystem(uri)
    } catch {
      case _: FileSystemNotFoundException =>
        val env = new java.util.HashMap[String, String]()
        FileSystems.newFileSystem(uri, env)
    }

  /**
   * Returns the file extension given its path.
   * This simply returns everything after the last ".".
   */
  def getExtension(path: String): Option[String] = path.split("\\.").toList match {
    case _ :: ss => ss.lastOption
    case _       => None
  }

}
