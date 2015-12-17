package scala.generator

object KafkaCaseClasses extends KafkaCaseClasses 
trait KafkaCaseClasses extends ExtendedCaseClasses {

  override def filenamePostfix = "KafkaModels"
  override def manualExtendsClasses = Seq("KafkaMessage")
  override def packageNamespace(ssd: ScalaService) = ssd.namespaces.base + ".kafka.models"

  override def modelFilter(model: ScalaModel):Boolean =
    model.model.attributes.find(attr => attr.name == "kafka_class").isDefined

  override def generateBody(ssd: ScalaService,  model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    val kafkaDataKey = model.model.attributes.find(attr => attr.name == "kafka_class") map {attr =>
      (attr.value \ "data_key").get.as[String]
    }
  s""" {
  def key = ${kafkaDataKey.get}
}
"""
  }

  override def additionalClasses = """

trait KafkaMessage {
  def key: String
}
"""

}

