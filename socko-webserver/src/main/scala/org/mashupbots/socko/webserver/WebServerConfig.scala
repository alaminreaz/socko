//
// Copyright 2012 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.webserver

import java.io.File
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import com.typesafe.config.Config
import org.mashupbots.socko.utils.Logger
import com.typesafe.config.ConfigException
import org.mashupbots.socko.utils.WebLogFormat
import scala.collection.JavaConversions._

/**
 * Web server configuration
 *
 * The configuration can be optionally loaded from Akka's application.conf` file.
 *
 * The following example configuration file:
 * {{{
 *   akka-config-example {
 *     server-name=AkkaConfigExample
 *     hostname=localhost
 *     port=9000
 *
 *     # Optional web log. If not supplied, web server activity logging is turned off.
 *     web-log {
 *
 *       # Optional Web log format: Common (Default) or Extended
 *       format = Common
 *
 *       # Optional asynchronous log queue size. Defaults to 512 events in the queue
 *       # before new events are discarded.
 *       buffer-size = 512
 *     }
 *
 *     # Optional SSL. If not supplied, ssl is turned off.
 *     ssl {
 *
 *       # Path to key store (server cert.)
 *       key-store-file=/tmp/ks.dat
 *
 *       # Password to key store
 *       key-store-password=kspwd
 *
 *       # Optional path to trust store (client cert.)
 *       trust-store-file=/tmp/ts.dat
 *
 *       # Optional password to trust store
 *       trust-store-password=tspwd
 *     }
 *
 *     # Optional HTTP protocol configuration. If not supplied, defaults are used.
 *     http {
 *
 *       # Maximum size of HTTP request. Defaults to 4MB.
 *       max-length-in-mb=4
 *
 *       # Maximum length of the HTTP initial line. Defaults to 4096 bytes (4K).
 *       max-initial-line-length=4096
 *
 *       # Maximum size of HTTP headers. Defaults to 8192 bytes (8K).
 *       max-header-size-in-bytes=8192
 *
 *       # Maximum size of HTTP chunks. Defaults to 8192 bytes (8K).
 *       max-chunk-size-in-bytes=8192
 *
 *       # Flag to indicate if HTTP chunk requests should be aggregated and presented
 *       # as a single HTTP request. Defaults to true.
 *       aggregate-chunks=true
 *
 *       # Content under this size is not compressed. Defaults to 1024 bytes (1K).
 *       # Set to -1 to turn off compression; or 0 to compress all content.
 *       min-compressible-content-size-in-bytes=1024
 *       
 *       # Content over this size is not compressed. Defaults to 1MB
 *       max-compressible-content-size-in-bytes=60
 *       
 *       # Only content with the specified MIME type will be compressed
 *       compressible-content-types=[
 *         "text/plain", "text/html", "text/xml", "text/css",
 *         "application/xml", "application/xhtml+xml", "application/rss+xml",
 *         "application/json", "application/jsonml+json",
 *         "application/javascript", "application/x-javascript"]
 *     }
 *   }
 * }}}
 *
 * can be loaded as follows:
 * {{{
 *   object MyWebServerConfig extends ExtensionId[WebServerConfig] with ExtensionIdProvider {
 *     override def lookup = MyWebServerConfig
 *     override def createExtension(system: ExtendedActorSystem) =
 *       new WebServerConfig(system.settings.config, "akka-config-example")
 *   }
 *
 *   val myWebServerConfig = MyWebServerConfig(actorSystem)
 *   val webServer = new WebServer(myWebServerConfig, routes)
 *   webServer.start()
 * }}}
 *
 * @param serverName Human friendly name of this server. Defaults to `WebServer`.
 * @param hostname Hostname or IP address to bind. `0.0.0.0` will bind to all addresses.
 * 	You can also specify comma separated hostnames/ip address like `localhost,192.168.1.1`.
 *  Defaults to `localhost`.
 * @param port IP port number to bind to. Defaults to `8888`.
 * @param webLog Web server activity log configuration. If `None`, activity will not be
 *  logged. If supplied, activities will be asynchronously written to the logger.
 * @param ssl SSL protocol configuration. If `None`, then SSL will not be turned on.
 *  Defaults to `None`.
 * @param http HTTP protocol configuration. Defaults to an instance of
 *  [[org.mashupbots.socko.webserver.HttpConfig]] with default settings.
 */
case class WebServerConfig(
  serverName: String = "WebServer",
  hostname: String = "localhost",
  port: Int = 8888,
  webLog: Option[WebLogConfig] = None,
  ssl: Option[SslConfig] = None,
  http: HttpConfig = HttpConfig()) extends Extension {

  /**
   * Read configuration from AKKA's `application.conf`
   */
  def this(config: Config, prefix: String) = this(
    config.getString(prefix + ".server-name"),
    config.getString(prefix + ".hostname"),
    config.getInt(prefix + ".port"),
    WebServerConfig.getOptionalWebLogConfig(config, prefix + ".web-log"),
    WebServerConfig.getOptionalSslConfig(config, prefix + ".ssl"),
    WebServerConfig.getHttpConfig(config, prefix + ".http"))

  /**
   * Validate current configuration settings. Throws an exception if configuration has errors.
   */
  def validate() = {
    if (serverName == null || serverName.isEmpty) {
      throw new IllegalArgumentException("server name must be specified")
    }

    if (hostname == null || hostname.isEmpty) {
      throw new IllegalArgumentException("hostname must be specified")
    }
    if (port <= 0) {
      throw new IllegalArgumentException("port must be specified and > 0")
    }

    if (ssl.isDefined) {
      if (ssl.get.keyStoreFile == null) {
        throw new IllegalArgumentException("key store file must be specified")
      }
      if (!ssl.get.keyStoreFile.exists) {
        throw new IllegalArgumentException("key store file does not exist")
      }
      if (!ssl.get.keyStoreFile.isFile) {
        throw new IllegalArgumentException("key store file is not a file")
      }
      if (ssl.get.keyStorePassword == null || ssl.get.keyStorePassword == "") {
        throw new IllegalArgumentException("key store password must be specified")
      }

      if (ssl.get.trustStoreFile.isDefined) {
        if (ssl.get.trustStoreFile == null || ssl.get.trustStoreFile.get == null) {
          throw new IllegalArgumentException("trust store file must be specified")
        }
        if (!ssl.get.trustStoreFile.get.exists) {
          throw new IllegalArgumentException("trust store file does not exist")
        }
        if (!ssl.get.trustStoreFile.get.isFile) {
          throw new IllegalArgumentException("trust store file is not a file")
        }
        if (ssl.get.trustStorePassword == null ||
          ssl.get.trustStorePassword.isEmpty ||
          ssl.get.trustStorePassword.get == null ||
          ssl.get.trustStorePassword.get == "") {
          throw new IllegalArgumentException("trust store password must be specified")
        }
      }
    }

    if (http == null) {
      throw new IllegalArgumentException("HTTP configuration must be specified")
    }
    if (http.maxLengthInMB <= 0) {
      throw new IllegalArgumentException("HTTP configuration, maximum length in MB, must be specified and > 0")
    }
    if (http.maxInitialLineLength <= 0) {
      throw new IllegalArgumentException("HTTP configuration, maximum initial line length, must be > 0")
    }
    if (http.maxHeaderSizeInBytes < 0) {
      throw new IllegalArgumentException("HTTP configuration, maximum header size, must be > 0")
    }
    if (http.maxChunkSizeInBytes < 0) {
      throw new IllegalArgumentException("HTTP configuration, maximum chunk size, must be > 0")
    }

  }
}

/**
 * SSL Configuration
 *
 * @param keyStoreFile Path to server private key store file (server certificates)
 * @param keyStorePassword Password to access server private key store file.
 * @param trustStoreFile Path to key store file for trusted remote public keys (client certificates).
 * 	This is optional.
 * @param trustStorePassword Password to access the key store for trusted remote public keys (client certificates).
 * 	This is optional.
 */
case class SslConfig(
  keyStoreFile: File,
  keyStorePassword: String,
  trustStoreFile: Option[File],
  trustStorePassword: Option[String]) {

  /**
   * Read configuration from AKKA's `application.conf`
   */
  def this(config: Config, prefix: String) = this(
    new File(config.getString(prefix + ".key-store-file")),
    config.getString(prefix + ".key-store-password"),
    WebServerConfig.getOptionalFile(config, prefix + ".trust-store-file"),
    WebServerConfig.getOptionalString(config, prefix + ".trust-store-password"))

}

/**
 * HTTP protocol handling configuration
 * 
 * HTTP compression parameters only applies to HTTP request and responses and not web sockets.
 *
 * @param maxLengthInMB Maximum size of HTTP request in megabytes. Defaults to 4MB.
 * @param maxInitialLineLength Maximum size the initial line. Defaults to 4096 characters.
 * @param maxHeaderSizeInBytes Maximum size of HTTP headers. Defaults to 8192 bytes.
 * @param maxChunkSizeInBytes Maximum size of HTTP chunks. Defaults to 8192 bytes.
 * @param aggreateChunks Flag to indicate if we want to aggregate chunks. If `false`, your processor actors must be
 *  able to handle `HttpChunkProcessingContext`
 * @param minCompressibleContentSizeInBytes Minimum number of bytes before HTTP content will be compressed if requested
 *   by the client. Set to `-1` to turn off compression for all files; `0` to make all content compressible.
 * @param maxCompressibleContentSizeInBytes Maximum number of bytes before HTTP content will be not be compressed if
 *   requested by the client. Defaults to 1MB otherwise too much CPU maybe taken up for compression.
 * @param compressibleContentTypes List of MIME types of that can be compressed. If not supplied, defaults to
 *   HTML, CSS, JSON, XML and Javascript files.
 */
case class HttpConfig(
  maxLengthInMB: Int = 4,
  maxInitialLineLength: Int = 4096,
  maxHeaderSizeInBytes: Int = 8192,
  maxChunkSizeInBytes: Int = 8192,
  aggreateChunks: Boolean = true,
  minCompressibleContentSizeInBytes: Int = 1024,
  maxCompressibleContentSizeInBytes: Int = (1 * 1024 * 1024),
  compressibleContentTypes: List[String] = WebServerConfig.defaultCompressibleContentTypes) {

  val maxLengthInBytes = maxLengthInMB * 1024 * 1024

  /**
   * Read configuration from AKKA's `application.conf`. Supply default values to use if setting not present
   */
  def this(config: Config, prefix: String) = this(
    WebServerConfig.getInt(config, prefix + ".max-length-in-mb", 4),
    WebServerConfig.getInt(config, prefix + ".max-initial-line-length", 4096),
    WebServerConfig.getInt(config, prefix + ".max-header-size-in-bytes", 8192),
    WebServerConfig.getInt(config, prefix + ".max-chunk-size-in-bytes", 8192),
    WebServerConfig.getBoolean(config, prefix + ".aggregate-chunks", true),
    WebServerConfig.getInt(config, prefix + ".min-compressible-content-size-in-bytes", 1024),
    WebServerConfig.getInt(config, prefix + ".max-compressible-content-size-in-bytes", 1 * 1024 * 1024),
    WebServerConfig.getCompressibleContentTypes(config, prefix + ".compressible-content-types"))
}

/**
 * Configuration for web server activity logs.
 *
 * The events are queued before being asynchronously written to the logger.
 *
 * @param format Format of the web log
 * @param bufferSize Number of events to queue before new events are discarded. This prevents a slow writer
 *   causing the queue the grow until web server to run out of memory.
 */
case class WebLogConfig(
  format: WebLogFormat.Type = WebLogFormat.Common,
  bufferSize: Int = 512) {

  /**
   * Read configuration from AKKA's `application.conf`
   */
  def this(config: Config, prefix: String) = this(
    WebLogFormat.withName(config.getString(prefix + ".format")),
    WebServerConfig.getInt(config, prefix + ".buffer-size", 512))
}

/**
 * Methods for reading configuration from Akka.
 */
object WebServerConfig extends Logger {

  /**
   * Returns an optional file configuration value. It is assumed that the value of the configuration name is the full
   * path to a file or directory.
   */
  def getOptionalFile(config: Config, name: String): Option[File] = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        None
      } else {
        Some(new File(v))
      }
    } catch {
      case _ => None
    }
  }

  /**
   * Returns an optional string configuration value
   */
  def getOptionalString(config: Config, name: String): Option[String] = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        None
      } else {
        Some(v)
      }
    } catch {
      case _ => None
    }
  }

  /**
   * Returns the specified setting as an integer. If setting not specified, then the default is returned.
   */
  def getInt(config: Config, name: String, defaultValue: Int): Int = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        defaultValue
      } else {
        config.getInt(name)
      }
    } catch {
      case _ => defaultValue
    }
  }

  /**
   * Returns the specified setting as a boolean. If setting not specified, then the default is returned.
   */
  def getBoolean(config: Config, name: String, defaultValue: Boolean): Boolean = {
    try {
      val v = config.getString(name)
      if (v == null || v == "") {
        defaultValue
      } else {
        config.getBoolean(name)
      }
    } catch {
      case _ => defaultValue
    }
  }

  /**
   * Returns the defined `HttpConfig`. If not defined, then the default `HttpConfig` is returned.
   */
  def getHttpConfig(config: Config, name: String): HttpConfig = {
    try {
      val v = config.getConfig(name)
      if (v == null) {
        new HttpConfig()
      } else {
        new HttpConfig(config, name)
      }
    } catch {
      case ex: ConfigException.Missing => {
        new HttpConfig()
      }
      case ex => {
        log.error("Error parsing HTTPConfig. Defaults will be used.", ex)
        new HttpConfig()
      }
    }
  }

  /**
   * Returns the defined `SslConfig`. If not defined, `None` is returned.
   */
  def getOptionalSslConfig(config: Config, name: String): Option[SslConfig] = {
    try {
      val v = config.getConfig(name)
      if (v == null) {
        None
      } else {
        Some(new SslConfig(config, name))
      }
    } catch {
      case ex: ConfigException.Missing => {
        None
      }
      case ex => {
        log.error("Error parsing SSL config. SSL is turned off.", ex)
        None
      }
    }
  }

  /**
   * Returns the activity log setting
   */
  def getOptionalWebLogConfig(config: Config, name: String): Option[WebLogConfig] = {
    try {
      val v = config.getConfig(name)
      if (v == null) {
        None
      } else {
        Some(new WebLogConfig(config, name))
      }
    } catch {
      case ex: ConfigException.Missing => {
        None
      }
      case ex => {
        log.error("Error parsing WebLogConfig config. Web server activity logging is turned off.", ex)
        None
      }
    }
  }

  val defaultCompressibleContentTypes: List[String] =
    "text/plain" :: "text/html" :: "text/xml" :: "text/css" ::
      "application/xml" :: "application/xhtml+xml" :: "application/rss+xml" ::
      "application/json" :: "application/jsonml+json" ::
      "application/javascript" :: "application/x-javascript" ::
      Nil

  /**
   * Returns an optional file configuration value. It is assumed that the value of the configuration name is the full
   * path to a file or directory.
   */
  def getCompressibleContentTypes(config: Config, name: String): List[String] = {
    try {
      val v = config.getStringList(name).toList
      if (v == null || v.isEmpty) {
        defaultCompressibleContentTypes
      } else {
        v
      }
    } catch {
      case _ => defaultCompressibleContentTypes
    }
  }
}

