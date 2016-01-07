package scala.models

import scala.generator.{ ScalaModel, ScalaService }
import scala.generator.ScalaUtil

object CaseClassUtil {

  def getInstanceName(model: ScalaModel, id: Int): String = {
    val name = model.originalName + "_entity"
    ScalaUtil.toVariable(name) + id
  }

  def getInstanceBatchName(model: ScalaModel): String =
    ScalaUtil.toVariable(model.originalName + "_entities")

  def generateInstance(model: ScalaModel, id: Int): String = ???

  def getModelByName(specName: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.filter(_.originalName == specName).headOption
}

