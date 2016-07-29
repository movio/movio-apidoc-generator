package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.{Attribute, ResponseCodeInt}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil}
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import play.api.libs.json.JsString

object PlayController extends PlayController
trait PlayController extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    ssd.resources.map{ resource: ScalaResource =>
      val resourceName = resource.plural + "Controller"
      val serviceName = resource.plural + "Service"

      val resourceFunctions = resource.operations.map { operation: ScalaOperation =>
        val method = operation.method.toString.toLowerCase
        val argList = ScalaUtil.fieldsToArgList(operation.parameters.map(_.definition())).getOrElse("")

        // Only include request.body if body present
        val bodyParam = operation.body.map(_ => "body.get")
        val argNameList = (
          Seq(Some("request"), bodyParam).flatten ++
            operation.parameters.map(_.name)
        ).mkString(", ")

        val bodyParse = operation.body.map(o => s"(play.api.mvc.BodyParsers.parse.json)").getOrElse("")

        // If we're posting a big collection - just return the size
        val returnSizeIfCollection = operation.body.map(_.datatype match {
                                                          case _: Container => ".size"
                                                          case _ => ""
                                                        }).getOrElse("")

        // Use the first successful status code in the defined responses, otherwise default to 200
        val successStatusCode = operation.responses
          .filter(_.isSuccess)
          .map(_.code)
          .collectFirst { case ResponseCodeInt(code) ⇒ code }
          .getOrElse(200)

        // Use in service
        val resultType = operation.resultType

        val block = s"""
service.${method}(${argNameList}).map{_ match {
  case scala.util.Success(result) =>
    Status($successStatusCode)(Json.toJson(result${returnSizeIfCollection}))
  case scala.util.Failure(ex) =>
    errorResponse(tenant, ex, msg => Error("500", msg))
}}"""

        operation.body match {
          case Some(body) => s"""
def ${operation.name}(${argList}) = play.api.mvc.Action.async(play.api.mvc.BodyParsers.parse.json) {  request =>
  request.body.validate[${body.name}] match {
    case errors: JsError =>
      errorResponse(tenant, errors, msg => Error("400", msg))
    case body: JsSuccess[${body.name}] =>${block.indent(6)}
  }
}"""
        case None => s"""
def ${operation.name}(${argList}) = play.api.mvc.Action.async {  request =>${block.indent(2)}
}"""
        }
      }.mkString("\n")


      val source = s"""$header
package controllers

import javax.inject.Inject
import javax.inject.Singleton

import play.api.libs.json._
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.Future

import services.${serviceName}

class ${resourceName} @Singleton @Inject() (service: ${serviceName}) extends play.api.mvc.Controller {
  import ${ssd.namespaces.models}._
  import ${ssd.namespaces.models}.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val logger = Logger(this.getClass)

  ${resourceFunctions.indent(2)}

  private def errorResponse[A: Writes](tenant: String, errors: JsError, create: String => A): Future[play.api.mvc.Result] = {
    logger.warn(s"[$$tenant] Error parsing the payload: [$$errors]")
    val msg = errors.errors.flatMap(node => {
      val nodeName = node._1.path.map(_.toString + ": ").mkString
      val message = node._2.map(_.message).mkString
      s"$$nodeName$$message"
    }).mkString
    scala.concurrent.Future(BadRequest(Json.toJson(create(msg))))
  }

  private def errorResponse[A: Writes](tenant: String, ex: Throwable, create: String => A): play.api.mvc.Result = {
    logger.error(s"[$$tenant] Error processing request", ex)
    InternalServerError(Json.toJson(create(ex.getMessage)))
  }

}
"""
      File(resourceName + ".scala", Some("controllers"), source)
    }
  }
}
