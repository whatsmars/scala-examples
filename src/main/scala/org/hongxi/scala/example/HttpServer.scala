package org.hongxi.scala.example

import java.io._
import java.net._
import java.util._

object HttpServer {

  val Encoding = "UTF-8"
  val WebRoot = new File(".")
  val DefaultFile = "index.html"
  val LineSep = System.getProperty("line.separator")

  case class Status(code: Int, text: String)

  def main(args: Array[String]) {
    val port = 8080
    val serverSocket = new ServerSocket(port)
    println(s"\nListening for connections on port ${port}...\n")

    val pool = concurrent.Executors.newCachedThreadPool()
    
    while(true)
      pool.execute(new HttpServer(serverSocket.accept()))
  }

}

class HttpServer(socket: Socket) extends Runnable {

  import HttpServer._ // import statics from companion object

  def run() {
    println(s"Connection opened. (${new Date()})")
    
    val source = io.Source.fromInputStream(socket.getInputStream(), Encoding)
    try {
      val line = source.getLines.next
      val tokens = new StringTokenizer(line)

      tokens.nextToken.toUpperCase match {
        case method @ ("GET" | "HEAD") =>
          val path = URLDecoder.decode(tokens.nextToken(), Encoding)
          sendFile(path, method)

        case method =>
          respondWithHtml(
            Status(501, "Not Implemented"),
            title = "501 Not Implemented",
            body = <H2>501 Not Implemented: { method } method</H2>
          )
          println(s"501 Not Implemented: ${method} method.")
      }
    } finally
      source.close()

  }

  def respond(status: Status, contentType: String = "text/html", content: Array[Byte]) {
    val out = new BufferedOutputStream(socket.getOutputStream())

    val header = s"""
      |HTTP/1.1 ${status.code} ${status.text}
      |Server: Scala HTTP Server 1.0
      |Date: ${new Date()}
      |Content-type: ${contentType}
      |Content-length: ${content.length}
    """.trim.stripMargin + LineSep + LineSep

    try {
      out.write(header.getBytes(Encoding))
      out.flush()

      out.write(content)
      out.flush()
    } finally {
      out.close()
    }
  }

  def respondWithHtml(status: Status, title: String, body: xml.NodeSeq) =
    respond(
      status = status,
      content = xml.Xhtml.toXhtml(
        <HTML>
          <HEAD><TITLE>{ title }</TITLE></HEAD>
            <BODY>
              { body }
            </BODY>
        </HTML>
      ).getBytes(Encoding)
    )

  def sendFile(path: String, method: String) = toFile(path) match {
    case Some(file) =>
      val content = if ("GET" == method) {
        val bytesOut = new ByteArrayOutputStream()
        sys.process.BasicIO.transferFully(new FileInputStream(file), bytesOut)
        bytesOut.toByteArray()
      } else
        Array.emptyByteArray
      val contentType = {
        val fileExt = file.getName.split('.').lastOption getOrElse ""
        getContentType(fileExt)
      }
      respond(
        Status(200, "OK"),
        contentType,
        content
      )
      println(s"File ${file.getPath} of type ${contentType} returned.")
    case None =>
      respondWithHtml(
        Status(404, "Not Found"),
        title = "404 File Not Found",
        body = <H2>404 File Not Found: {path}</H2>
      )
      println(s"404 File Not Found: ${path}")
  }

  def toFile(path: String): Option[File] =
    if (path contains "..") {
      respondWithHtml(
        Status(403, "Forbidden"),
        title = "403 Forbidden",
        body = <H2>403 Forbidden: .. in path not allowed: {path}</H2>
      )
      println(s"403 Forbidden: .. in path not allowed: ${path}")
      throw new SecurityException(s".. in path not allowed: ${path}")
    } else
      toFile(new File(WebRoot, path))

  def toFile(file: File, isRetry: Boolean = false): Option[File] =
    if (file.isDirectory && !isRetry)
      toFile(new File(file, DefaultFile), true)
    else if (file.isFile)
      Some(file)
    else
      None

  def getContentType(extension: String) = extension match {
    case "htm" | "html"  => "text/html"
    case "gif"           => "image/gif"
    case "jpg" | "jpeg"  => "image/jpeg"
    case "class" | "jar" => "applicaton/octet-stream"
    case _               => "text/plain"
  }

}