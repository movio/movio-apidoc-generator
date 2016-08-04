package scala.generator

import lib.Text._

case class ScalaEnums(
  ssd: ScalaService,
  enum: ScalaEnum
) {

  private[this] val unions = ssd.unionsForEnum(enum)

  def build(): String = {
    import lib.Text._
    Seq(
      enum.description.map { desc => ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"sealed trait ${enum.name}" + ScalaUtil.extendsClause(unions.map(_.name)).map(s => s" $s").getOrElse(""),
      s"object ${enum.name} {",
      buildValues().indent(2),
      s"}"
    ).mkString("\n\n")
  }

  /**
    * Returns the implicits for json serialization. Note that if the
    * enum is part of a union type, we do NOT generate a writer for
    * the enum as the implicit would interfere.
    */
  def buildJson(): String = {
    unions.find(!_.discriminator.isEmpty) match {
      case None => buildJsonNoDiscriminator()
      case Some(_) => buildJsonWithDiscriminator()
    }
  }

  /**
    * If this enum is part of a union type with discrimator, we allow
    * the enum to serialize the type. The reader must be able to
    * handle an object where we pull the value out of the JSON
    * Object. Note that we do not need to actually read the
    * discriminator - we just need to hydrate the enum.
    */
  private def buildJsonWithDiscriminator(): String = {
    Seq(
      s"implicit val jsonReads${ssd.name}${enum.name} = new play.api.libs.json.Reads[${enum.qualifiedName}] {",
      Seq(
        s"def reads(js: play.api.libs.json.JsValue): play.api.libs.json.JsResult[${enum.qualifiedName}] = {",
        Seq(
          "js match {",
          Seq(
            s"case v: play.api.libs.json.JsString => play.api.libs.json.JsSuccess(${enum.qualifiedName}(v.value))",
            "case _ => {",
            Seq(
              """(js \ "value").validate[String] match {""",
              Seq(
                s"case play.api.libs.json.JsSuccess(v, _) => play.api.libs.json.JsSuccess(${enum.qualifiedName}(v))",
                "case err: play.api.libs.json.JsError => err"
              ).mkString("\n").indent(2),
              "}"
            ).mkString("\n").indent(2),
            "}"
          ).mkString("\n").indent(2),
          "}"
        ).mkString("\n").indent(2),
        "}"
      ).mkString("\n").indent(2),
      "}"
    ).mkString("\n")
  }

  private def buildJsonNoDiscriminator(): String = {
    Seq(
      s"implicit val jsonReads${ssd.name}${enum.name} = __.read[String].map(${enum.name}.apply)",
      s"implicit val jsonWrites${ssd.name}${enum.name} = new Writes[${enum.name}] {",
      s"  def writes(x: ${enum.name}) = JsString(x.toString)",
      "}"
    ).mkString("\n")
  }

  private def buildValues(): String = {
    enum.values.map { value =>
      Seq(
        value.description.map { desc => ScalaUtil.textToComment(desc) },
        Some(s"""case object ${value.name} extends ${enum.name} { override def toString = "${value.originalName}" }""")
      ).flatten.mkString("\n")
    }.mkString("\n") + "\n" +
    s"""
/**
 * all returns a list of all the valid, known values. We use
 * lower case to avoid collisions with the camel cased values
 * above.
 */
""" +
    s"val all = Seq(" + enum.values.map(_.name).mkString(", ") + ")\n\n" +
    s"private[this]\n" +
    s"val byName = all.map(x => x.toString.toLowerCase -> x).toMap\n\n" +
    s"""def apply(value: String): ${enum.name} = fromString(value).getOrElse(throw new IllegalArgumentException(s"$$value is not a valid ${enum.name}."))\n\n""" +
    s"def fromString(value: String): _root_.scala.Option[${enum.name}] = byName.get(value.toLowerCase)\n\n"
  }

}
