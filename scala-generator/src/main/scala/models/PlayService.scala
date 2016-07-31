package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil }
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import play.api.libs.json.JsString

object PlayService extends PlayService
trait PlayService extends CodeGenerator {
  import CaseClassUtil._
  import KafkaUtil._

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
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val kafkaModels = getKafkaModels(ssd)

    ssd.resources.map { resource: ScalaResource ⇒
      val resourceName = resource.plural
      val serviceName = resource.plural + "Service"

      // Find KafkaProducer that contians the model for $resourceName
      val kafkaProducers = kafkaModels.flatMap(_.model.attributes.map(attr ⇒ {
        (attr.value \ "data_type").as[JsString].value
      }))
      val resourceBodies = resource.operations.flatMap(_.body.map(_.body.`type`))

      val producerMap = kafkaProducers.intersect(resourceBodies).map(t => t -> ScalaUtil.toClassName(t)).toMap

      val producers = producerMap.values.map(p ⇒ s"val kafka${p}Producer = new Kafka${p}Producer(config)").mkString("\n")

      val resourceFunctions = resource.operations.map { operation: ScalaOperation ⇒
        val method = operation.method.toString.toLowerCase
        val parameters = operation.parameters

        val resultType = operation.resultType

        val bodyType = operation.body.map(_.name).getOrElse(resultType)

        val firstParamName = parameters.map(_.name).headOption.getOrElse("")

        val dataArg = operation.body.map(b => s"""data: ${b.name}""")

        val additionalArgs = Seq(Some("request: Request[T]"), dataArg).flatten
        val argList = ScalaUtil.fieldsToArgList(additionalArgs ++ (parameters.map(_.definition()))).mkString(", ")

        val argNameList = (Seq("request.body", "request") ++ operation.parameters.map(_.name)).mkString(", ")
        // Log request parameters
        val paramLogging = operation.parameters.map{ p ⇒ s"${p.name}: $$${p.name}" }.mkString(", ")

        val producerName = operation.body.map(_.body.`type`)
          .map(_.replaceAll("[\\[\\]]", ""))
          .map(clazz => s"kafka${ScalaUtil.toClassName(clazz)}Producer")
          .getOrElse("???")


        val bodyScala = method.toLowerCase match {
          case "post" | "put" => s"""${producerName}.send(data, ${firstParamName})"""
          case "get" =>
            // Create a default Case Class
            ssd.models.filter(_.qualifiedName == operation.resultType).headOption match {
              case Some(model) =>
                val caseClass = generateInstance(model, 1, ssd)
                s"Try { ${caseClass.indent(6)} }"
              case None =>
                "Try { Unit }"
            }
          case _ => "???"
        }

        val logging = method.toLowerCase match {
          case "post" | "put" =>
            operation.body.map(
              _.datatype match {
                case _: Container =>
                  s"""logger.debug(s"[$paramLogging] Producing a batch of [$${data.size}] $resourceName messages")"""
                case _ =>
                  s"""logger.debug(s"[$paramLogging] Producing a single $resourceName message: [$${data}]")"""
              }
            ).getOrElse("")

          case _ =>
            ""
        }


        s"""
def ${method}[T](${argList}): Future[Try[${bodyType}]] = {
  Future {
    ${logging}
    ${bodyScala}
  }
}"""
      }.mkString("\n")

      val source = s"""$header
package services

import scala.concurrent.Future
import scala.util.Try
import javax.inject.Inject

import com.typesafe.config.Config

import play.api.mvc.Request
import play.api.Logger

class ${serviceName} @Inject() (config: Config) {
  import ${ssd.namespaces.models}._
  import ${ssd.namespaces.base}.kafka._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  ${producers}

  private val logger = Logger(this.getClass)

  ${resourceFunctions.indent(2)}
}
"""
      File(serviceName + ".scala", Some("services"), source)
    }
  }
}
