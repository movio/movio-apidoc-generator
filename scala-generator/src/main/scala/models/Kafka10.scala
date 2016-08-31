package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.generator.CodeGenerator

object Kafka10 extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm
  ): Seq[File] = {
    Kafka10Consumer.generateCode(form) ++
      Kafka10Producer.generateCode(form)
  }
}
