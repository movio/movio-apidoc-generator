package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{Namespaces, ScalaClientMethodGenerator, ScalaService, ScalaClientCommon}
import scala.generator.{ScalaCaseClasses, ScalaClientMethodConfig, ScalaClientMethodConfigs}
import generator.ServiceFileNames

case class PlayFrameworkVersion(
  name: String,
  config: ScalaClientMethodConfig,
  requestHolderClass: String,
  authSchemeClass: String,
  supportsHttpPatch: Boolean
)

object Play24ClientGenerator extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = {

    val config = PlayFrameworkVersion(
      name = "2.4.x",
      config = ScalaClientMethodConfigs.Play24(Namespaces.quote(form.service.namespace)),
      requestHolderClass = "play.api.libs.ws.WSRequest",
      authSchemeClass = "play.api.libs.ws.WSAuthScheme",
      supportsHttpPatch = true
    )

    Play2ClientGenerator.invoke(config, form)
  }
}

object Play2ClientGenerator {

  def invoke(
    version: PlayFrameworkVersion,
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Play2ClientGenerator(version, form).invoke()
  }

}

case class Play2ClientGenerator(
  version: PlayFrameworkVersion,
  form: InvocationForm
) {

  private[this] val ssd = new ScalaService(form.service)

  def invoke(): Either[Seq[String], Seq[File]] = {
    ScalaCaseClasses.modelsWithTooManyFieldsErrors(form.service) match {
      case Nil => Right(generateCode())
      case errors => Left(errors)
    }
  }

  private def generateCode(): Seq[File] = {
    val source = ApidocComments(form.service.version, form.userAgent).toJavaString + "\n" +
      Seq(
        Play2Models.generateCode(form, addBindables = true, addHeader = false).map(_.contents).mkString("\n\n"),
        client()
      ).mkString("\n\n")

    Seq(ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, "Client", source, Some("Scala")))
  }

  private def client(): String = {

    val methodGenerator = ScalaClientMethodGenerator(version.config, ssd)

    val patchMethod = version.supportsHttpPatch match {
      case true => """_logRequest("PATCH", _requestHolder(path).withQueryString(queryParameters:_*)).patch(body.getOrElse(play.api.libs.json.Json.obj()))"""
      case false => s"""sys.error("PATCH method is not supported in Play Framework Version ${version.name}")"""
    }

    val headers = Headers(form)
    val headerString = headers.scala.
      map { case (name, value) => s""""$name" -> ${value}""" }.
      mkString(".withHeaders(\n        ", ",\n        ", "") + "\n      ).withHeaders(defaultHeaders : _*)"

    s"""package ${ssd.namespaces.base} {

${headers.objectConstants.indent(2)}

${ScalaClientCommon.clientSignature(version.config).indent(2)} {
${JsonImports(form.service).mkString("\n").indent(4)}

    private[this] val logger = play.api.Logger("${ssd.namespaces.base}.Client")

    logger.info(s"Initializing ${ssd.namespaces.base}.Client for url $$apiUrl")

    val client = play.api.Play.maybeApplication match {
      case Some(_) => // Don't need a client when in a play app
        logger.trace("Play app found - using that it to configure play client")
        None
      case None =>
        autoClose match {
          case true => // Don't need a client, we'll create one for each request
            logger.trace("Auto close set - will create a new connection for each request")
            None
          case false =>
            logger.trace("Auto close not set - creating a new client - will need to be closed manually")
            val builder = new com.ning.http.client.AsyncHttpClientConfig.Builder()
            Some(new play.api.libs.ws.ning.NingWSClient(builder.build()))
        }
    }

${methodGenerator.accessors().indent(4)}

${methodGenerator.objects().indent(4)}

    def _requestHolder(path: String): ${version.requestHolderClass} = {
      val url:play.api.libs.ws.WSRequest = play.api.Play.maybeApplication match {
        case Some(app) => // We have a running Play App use built in client for url
          import play.api.Play.current
          play.api.libs.ws.WS.url(apiUrl + path)
        case None =>
          val builder = new com.ning.http.client.AsyncHttpClientConfig.Builder()
          client match {
            case Some(c) => // using existing client
              c.url(apiUrl + path)
            case None => // we need to create a new client for each request
              val c = new play.api.libs.ws.ning.NingWSClient(builder.build())
              c.url(apiUrl + path)
          }
      }

      val holder = url$headerString

      auth.fold(holder) {
        case Authorization.Basic(username, password) => {
          holder.withAuth(username, password.getOrElse(""), ${version.authSchemeClass}.BASIC)
        }
        case a => sys.error("Invalid authorization scheme[" + a.getClass + "]")
      }
    }

    def _logRequest(method: String, req: ${version.requestHolderClass})(implicit ec: scala.concurrent.ExecutionContext): ${version.requestHolderClass} = {
      val queryComponents = for {
        (name, values) <- req.queryString
        value <- values
      } yield s"$$name=$$value"
      val url = s"$${req.url}$${queryComponents.mkString("?", "&", "")}"
      auth.fold(logger.info(s"curl -X $$method $$url")) { _ =>
        logger.info(s"curl -X $$method -u '[REDACTED]:' $$url")
      }
      req
    }

    def _executeRequest(
      method: String,
      path: String,
      queryParameters: Seq[(String, String)] = Seq.empty,
      body: Option[play.api.libs.json.JsValue] = None
    )(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[${version.config.responseClass}] = {
      val result = method.toUpperCase match {
        case "GET" => {
          _logRequest("GET", _requestHolder(path).withQueryString(queryParameters:_*)).get()
        }
        case "POST" => {
          _logRequest("POST", _requestHolder(path).withQueryString(queryParameters:_*).withHeaders("Content-Type" -> "application/json; charset=UTF-8")).post(body.getOrElse(play.api.libs.json.Json.obj()))
        }
        case "PUT" => {
          _logRequest("PUT", _requestHolder(path).withQueryString(queryParameters:_*).withHeaders("Content-Type" -> "application/json; charset=UTF-8")).put(body.getOrElse(play.api.libs.json.Json.obj()))
        }
        case "PATCH" => {
          $patchMethod
        }
        case "DELETE" => {
          _logRequest("DELETE", _requestHolder(path).withQueryString(queryParameters:_*)).delete()
        }
         case "HEAD" => {
          _logRequest("HEAD", _requestHolder(path).withQueryString(queryParameters:_*)).head()
        }
         case "OPTIONS" => {
          _logRequest("OPTIONS", _requestHolder(path).withQueryString(queryParameters:_*)).options()
        }
        case _ => {
          _logRequest(method, _requestHolder(path).withQueryString(queryParameters:_*))
          sys.error("Unsupported method[%s]".format(method))
        }
      }
      // Close connection if needed
      result.onComplete {
        case _ => // 
          client match {
            case Some(c) =>
              if (autoClose) {
                logger.trace("Auto closing client connection")
                c.close
              }
            case _ => // No client - don't need to close
          }
      }
      result
    }

    def close: Unit = {
      client match {
        case Some(c) => 
          if (! autoClose)
            c.close
          else
            throw new RuntimeException("Connection set to autoClose - do not call close")
        case None =>
          throw new RuntimeException("Connection managed by the running Play App - do not call close")
      }
    }

  }

${ScalaClientCommon(version.config).indent(2)}

${methodGenerator.traitsAndErrors().indent(2)}

}"""
  }

}
