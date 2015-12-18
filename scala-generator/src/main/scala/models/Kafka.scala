package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.generator.CodeGenerator

object Kafka extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm
  ): Seq[File] = {
    KafkaConsumer.generateCode(form) ++
      KafkaProducer.generateCode(form)
  }
}
