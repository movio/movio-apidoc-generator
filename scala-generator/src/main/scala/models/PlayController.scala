package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
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
    val play2Json = Play2Json(ssd).generate()

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
        val bodyParam = operation.body.map(_ => "request.body")
        val argNameList = (
          Seq(Some("request"), bodyParam).flatten ++
            operation.parameters.map(_.name)
        ).mkString(", ")

        val bodyParse = operation.body.map(o => s"(BodyParsers.parse.json[${o.name}])").getOrElse("")

        // If we're posting a big collection - just return the size
        val returnSizeIfCollection = operation.body.map(_.datatype match {
                                                          case _: Container => ".size"
                                                          case _ => ""
                                                        }).getOrElse("")

        // Use in service
        val resultType = operation.resultType

        s"""
def ${operation.name}(${argList}) = Action.async${bodyParse} {  request =>
  service.${method}(${argNameList}).map(_ match {
    case Success(result) =>
      Ok(Json.toJson(result${returnSizeIfCollection}))
    case Failure(ex) =>
      InternalServerError(Json.toJson(Error("500", ex.toString)))
  })
}"""
        }.mkString("\n")


      val source = s"""$header
package controllers

import javax.inject.Inject
import javax.inject.Singleton

import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.util.{Success, Failure}

import services.${serviceName}

class ${resourceName} @Singleton @Inject() (service: ${serviceName}) extends Controller {
  import ${ssd.namespaces.models}._
  import ${ssd.namespaces.models}.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  ${resourceFunctions.indent(2)}
}
"""
      File(resourceName + ".scala", Some("controllers"), source)
    }
  }
}
