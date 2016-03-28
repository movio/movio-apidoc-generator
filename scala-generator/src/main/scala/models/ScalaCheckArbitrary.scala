package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator._
import generator.ServiceFileNames

object ScalaCheckArbitrary extends ScalaCheckArbitrary
trait ScalaCheckArbitrary extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = {
    ScalaCaseClasses.modelsWithTooManyFieldsErrors(form.service) match {
      case Nil    => Right(generateCode(form = form, addBindables = true, addHeader = true))
      case errors => Left(errors)
    }
  }

  def generateCode(form: InvocationForm, addBindables: Boolean, addHeader: Boolean): Seq[File] = {
    val ssd = ScalaService(form.service)

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    def getFieldName(field: ScalaField): String =
      ScalaUtil.quoteNameIfKeyword(snakeToCamelCase(field.name))

    def getFieldArbitrary(field: ScalaField): String = {
      val name = getFieldName(field)
      val tpe =
        CaseClassUtil.getScalaProps(field.field) match {
          case Some(props) ⇒
            val rootedType = "_root_." + props.`class`.get
            if (field.required)
              rootedType
            else
              s"_root_.scala.Option[${rootedType}]"
          case None ⇒
            field.datatype.name
        }
      s"${name} <- arbitrary[${tpe}]"
    }

    def generateCaseClass(model: ScalaModel): String =
      s"""implicit val arb${model.name}: Arbitrary[${model.qualifiedName}] = Arbitrary {
         |  for {
         |${model.fields.map(getFieldArbitrary).mkString("\n").indent(4)}
         |  } yield ${model.qualifiedName}.apply(${model.fields.map(getFieldName).mkString(", ")})
         |}
         |""".stripMargin

    def generateEnum(enum: ScalaEnum): String =
      s"""implicit val arb${enum.name}: Arbitrary[${enum.qualifiedName}] =
         |  Arbitrary(Gen.oneOf(${enum.qualifiedName}.all))
         |""".stripMargin

    val models = ssd.models.map(generateCaseClass)
    val enums = ssd.enums.map(generateEnum)

    val source = s"""$header
package ${ssd.namespaces.models} {
  package object arbitrary {
    import org.scalacheck.Arbitrary
    import org.scalacheck.Arbitrary.arbitrary
    import org.scalacheck.Gen
    import movio.cinema.test.StandardArbitraries._

${enums.mkString("\n\n").indent(4)}

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
        "Arbitrary",
        source,
        Some("Scala")
      )
    )
  }
}
