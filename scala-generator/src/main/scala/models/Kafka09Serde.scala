package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.ScalaCaseClasses
import scala.generator.ScalaDatatype
import scala.generator.ScalaField
import scala.generator.ScalaPrimitive
import scala.generator.ScalaService
import generator.ServiceFileNames

// Copied from Play2Models
object Kafka09Serde extends Kafka09Serde
trait Kafka09Serde extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = {
    ScalaCaseClasses.modelsWithTooManyFieldsErrors(form.service) match {
      case Nil    => Right(generateCode(form = form, addBindables = true, addHeader = true))
      case errors => Left(errors)
    }
  }

  def generateCode(form: InvocationForm, addBindables: Boolean, addHeader: Boolean): Seq[File] = {
    val ssd = ScalaService(form.service)

    val header = if (addHeader) {
      val formattingDirective = "// format: OFF"
      val commentHeader = ApidocComments(form.service.version, form.userAgent).toJavaString
      s"$formattingDirective\n\n$commentHeader\n"
    } else {
      ""
    }

    def generateSerdeClasses(simpleName: String, fullName: String): String = {
      s"""class ${simpleName}Serializer extends Serializer[${fullName}] {
         |  override def configure(configs: JMap[String, _], isKey: Boolean): Unit = {}
         |  override def close(): Unit = {}
         |  override def serialize(topic: String, obj: ${fullName}): Array[Byte] = {
         |    Json.toJson(obj).toString.getBytes(StandardCharsets.UTF_8)
         |  }
         |}
         |class ${simpleName}Deserializer extends Deserializer[${fullName}] {
         |  override def configure(configs: JMap[String, _], isKey: Boolean): Unit = {}
         |  override def close(): Unit = {}
         |  override def deserialize(topic: String, bytes: Array[Byte]): ${fullName} = {
         |    Try(Json.parse(bytes).as[${fullName}]) match {
         |      case Success(obj) ⇒ obj
         |      case Failure(ex) ⇒ throw new SerializationException("Failed to parse ${simpleName}", ex)
         |    }
         |  }
         |}
      """.stripMargin
    }

    val models = ssd.models map { model ⇒
      generateSerdeClasses(model.name, model.qualifiedName)
    }

    val source = s"""$header
package ${ssd.namespaces.models} {
  package object serde {
    import org.apache.kafka.common.serialization.Serializer
    import org.apache.kafka.common.serialization.Deserializer
    import org.apache.kafka.common.errors.SerializationException
    import play.api.libs.json.Json
    import ${ssd.namespaces.models}.json._
    import java.nio.charset.StandardCharsets
    import java.util.{Map ⇒ JMap}
    import scala.util.Failure
    import scala.util.Success
    import scala.util.Try

${models.mkString("\n\n").indent(4)}
  }
}
"""
    Seq(
      ServiceFileNames.toFile(
        form.service.namespace,
        form.service.organization.key,
        form.service.application.key,
        form.service.version,
        "Serde",
        source,
        Some("Scala")
      )
    )
  }
}
