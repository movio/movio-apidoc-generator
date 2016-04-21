package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames

// Copied from Play2Models
object Play2JsonStandalone extends Play2JsonStandalone
trait Play2JsonStandalone extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    ScalaCaseClasses.modelsWithTooManyFieldsErrors(form.service) match {
      case Nil => Right(generateCode(form = form, addBindables = true, addHeader = true))
      case errors => Left(errors)
    }
  }

  def generateCode(
    form: InvocationForm,
    addBindables: Boolean,
    addHeader: Boolean
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val source = s"""$header
package ${ssd.namespaces.models} {

  package object json {
    import play.api.libs.json.__
    import play.api.libs.json.JsString
    import play.api.libs.json.Writes
    import play.api.libs.functional.syntax._
${JsonImports(form.service).mkString("\n").indent(4)}

    private[${ssd.namespaces.last}] implicit val jsonReadsUUID = __.read[String].map(java.util.UUID.fromString)

    private[${ssd.namespaces.last}] implicit val jsonWritesUUID = new Writes[java.util.UUID] {
      def writes(x: java.util.UUID) = JsString(x.toString)
    }

    private[${ssd.namespaces.last}] implicit val jsonReadsJodaDateTime = __.read[String].map { str =>
      import org.joda.time.format.ISODateTimeFormat.dateTimeParser
      dateTimeParser.parseDateTime(str)
    }

    private[${ssd.namespaces.last}] implicit val jsonWritesJodaDateTime = new Writes[org.joda.time.DateTime] {
      def writes(x: org.joda.time.DateTime) = {
        import org.joda.time.format.ISODateTimeFormat.dateTime
        val str = dateTime.print(x)
        JsString(str)
      }
    }

    private[${ssd.namespaces.last}] implicit val jsonReadsJodaLocalDateTime = __.read[String].map { str =>
      import org.joda.time.format.ISODateTimeFormat.dateTimeParser
      dateTimeParser.parseLocalDateTime(str)
    }

    private[${ssd.namespaces.last}] implicit val jsonWritesJodaLocalDateTime = new Writes[org.joda.time.LocalDateTime] {
      def writes(x: org.joda.time.LocalDateTime) = {
        import org.joda.time.format.ISODateTimeFormat.dateTime
        val str = dateTime.print(x)
        JsString(str)
      }
    }

    private[${ssd.namespaces.last}] implicit val jsonReadsJodaDateTimeZone = __.read[String].map { str =>
      org.joda.time.DateTimeZone.forID(str)
    }

    private[${ssd.namespaces.last}] implicit val jsonWritesJodaDateTimeZone = new Writes[org.joda.time.DateTimeZone] {
      def writes(x: org.joda.time.DateTimeZone) = JsString(x.getID)
    }

${Seq(enumJson, play2Json).filter(!_.isEmpty).mkString("\n\n").indent(4)}
  }
}
"""

    Seq(ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, "Json", source, Some("Scala")))
  }
}
